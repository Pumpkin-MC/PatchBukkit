//! Runtime acquisition of the Paper server.
//!
//! PatchBukkit runs the real CraftBukkit / NMS classes, but must never
//! redistribute Mojang code. Instead we download the Paperclip jar from
//! PaperMC at runtime, let it patch the vanilla server locally
//! (`-Dpaperclip.patchonly=true`), and put the resulting jars on the JVM
//! classpath. This is the same model Paperclip itself uses.

use std::{
    fs,
    io::{Read, Write},
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, anyhow, bail};
use serde::Deserialize;
use sha2::{Digest, Sha256};

use crate::config::patchbukkit::PaperConfig;

const FILL_API: &str = "https://fill.papermc.io/v3/projects/paper";
const USER_AGENT: &str = concat!(
    "PatchBukkit/",
    env!("CARGO_PKG_VERSION"),
    " (https://github.com/Pumpkin-MC/PatchBukkit)"
);
const INSTALL_MARKER: &str = ".patchbukkit-installed";

#[derive(Deserialize)]
struct BuildInfo {
    id: u32,
    downloads: Downloads,
}

#[derive(Deserialize)]
struct Downloads {
    #[serde(rename = "server:default")]
    server: Download,
}

#[derive(Deserialize)]
struct Download {
    name: String,
    checksums: Checksums,
    url: String,
}

#[derive(Deserialize)]
struct Checksums {
    sha256: String,
}

/// Ensures the patched Paper server for the configured version/build exists
/// in `cache_dir` and returns all jars that must be on the classpath
/// (the patched server jar first, then its libraries).
pub fn ensure_paper_server(cache_dir: &Path, config: &PaperConfig) -> Result<Vec<PathBuf>> {
    fs::create_dir_all(cache_dir)
        .with_context(|| format!("Failed to create Paper cache dir {}", cache_dir.display()))?;

    let marker_path = cache_dir.join(INSTALL_MARKER);
    let wanted_marker = format!("{}-{}", config.version, config.build);

    let installed = fs::read_to_string(&marker_path).is_ok_and(|m| m.trim() == wanted_marker)
        && server_jar(cache_dir, &config.version).is_some();

    if !installed {
        if !config.auto_download {
            bail!(
                "Paper {} build {} is not installed in {} and `paper.auto-download` is disabled",
                config.version,
                config.build,
                cache_dir.display()
            );
        }
        install(cache_dir, config)?;
        fs::write(&marker_path, &wanted_marker)?;
    }

    collect_classpath(cache_dir, &config.version)
}

fn install(cache_dir: &Path, config: &PaperConfig) -> Result<()> {
    tracing::info!(
        "Downloading Paper {} build {} (first start only)...",
        config.version,
        config.build
    );

    let info_url = format!(
        "{FILL_API}/versions/{}/builds/{}",
        config.version, config.build
    );
    let info: BuildInfo = ureq::get(&info_url)
        .header("User-Agent", USER_AGENT)
        .call()
        .with_context(|| format!("Failed to query {info_url}"))?
        .body_mut()
        .read_json()
        .context("Failed to parse Paper build info")?;

    let download = &info.downloads.server;
    let paperclip = cache_dir.join(&download.name);
    if !file_matches_sha256(&paperclip, &download.checksums.sha256) {
        download_verified(&download.url, &paperclip, &download.checksums.sha256)?;
    }

    // Remove output of a previously installed build so stale jars don't end up on the classpath.
    let _ = fs::remove_dir_all(cache_dir.join("versions"));
    let _ = fs::remove_dir_all(cache_dir.join("libraries"));

    tracing::info!("Patching Paper build {} locally with Paperclip...", info.id);
    let java = java_binary();
    let output = Command::new(&java)
        .current_dir(cache_dir)
        .arg("-Dpaperclip.patchonly=true")
        .arg("-jar")
        .arg(&paperclip)
        .output()
        .with_context(|| format!("Failed to run Paperclip with `{}`", java.display()))?;

    if !output.status.success() {
        bail!(
            "Paperclip failed ({}):\n{}{}",
            output.status,
            String::from_utf8_lossy(&output.stdout),
            String::from_utf8_lossy(&output.stderr)
        );
    }

    if server_jar(cache_dir, &config.version).is_none() {
        bail!(
            "Paperclip finished but no patched server jar was found in {}",
            cache_dir.join("versions").display()
        );
    }

    tracing::info!("Paper {} build {} installed", config.version, info.id);
    Ok(())
}

/// The patched server jar Paperclip writes to `versions/<mc>/paper-<mc>.jar`.
fn server_jar(cache_dir: &Path, version: &str) -> Option<PathBuf> {
    let dir = cache_dir.join("versions").join(version);
    fs::read_dir(dir)
        .ok()?
        .filter_map(std::result::Result::ok)
        .map(|e| e.path())
        .find(|p| p.extension().is_some_and(|ext| ext == "jar"))
}

fn collect_classpath(cache_dir: &Path, version: &str) -> Result<Vec<PathBuf>> {
    let server = server_jar(cache_dir, version)
        .ok_or_else(|| anyhow!("Patched Paper server jar is missing"))?;

    let mut libraries: Vec<PathBuf> = walkdir::WalkDir::new(cache_dir.join("libraries"))
        .into_iter()
        .filter_map(std::result::Result::ok)
        .filter(|e| e.file_type().is_file())
        .map(walkdir::DirEntry::into_path)
        .filter(|p| p.extension().is_some_and(|ext| ext == "jar"))
        .collect();
    libraries.sort();

    let mut classpath = Vec::with_capacity(libraries.len() + 1);
    classpath.push(server);
    classpath.extend(libraries);
    Ok(classpath)
}

fn java_binary() -> PathBuf {
    let exe = if cfg!(windows) { "java.exe" } else { "java" };
    std::env::var_os("JAVA_HOME")
        .map(|home| PathBuf::from(home).join("bin").join(exe))
        .filter(|p| p.exists())
        .unwrap_or_else(|| PathBuf::from(exe))
}

fn file_matches_sha256(path: &Path, expected: &str) -> bool {
    let Ok(mut file) = fs::File::open(path) else {
        return false;
    };
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        match file.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => hasher.update(&buf[..n]),
            Err(_) => return false,
        }
    }
    hex(&hasher.finalize()).eq_ignore_ascii_case(expected)
}

fn download_verified(url: &str, dest: &Path, expected_sha256: &str) -> Result<()> {
    let tmp = dest.with_extension("jar.downloading");
    let mut response = ureq::get(url)
        .header("User-Agent", USER_AGENT)
        .call()
        .with_context(|| format!("Failed to download {url}"))?;

    let mut reader = response
        .body_mut()
        .with_config()
        .limit(512 * 1024 * 1024)
        .reader();
    let mut out = fs::File::create(&tmp)?;
    let mut hasher = Sha256::new();
    let mut buf = [0u8; 64 * 1024];
    loop {
        let n = reader.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
        out.write_all(&buf[..n])?;
    }
    out.flush()?;
    drop(out);

    let actual = hex(&hasher.finalize());
    if !actual.eq_ignore_ascii_case(expected_sha256) {
        let _ = fs::remove_file(&tmp);
        bail!("SHA-256 mismatch for {url}: expected {expected_sha256}, got {actual}");
    }

    fs::rename(&tmp, dest)?;
    Ok(())
}

fn hex(bytes: &[u8]) -> String {
    use std::fmt::Write as _;
    bytes
        .iter()
        .fold(String::with_capacity(bytes.len() * 2), |mut s, b| {
            let _ = write!(s, "{b:02x}");
            s
        })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn collect_classpath_puts_server_first() {
        let dir = tempfile::tempdir().unwrap();
        let versions = dir.path().join("versions").join("26.3");
        let libs = dir.path().join("libraries").join("com").join("example");
        fs::create_dir_all(&versions).unwrap();
        fs::create_dir_all(&libs).unwrap();
        fs::write(versions.join("paper-26.3.jar"), b"").unwrap();
        fs::write(libs.join("b.jar"), b"").unwrap();
        fs::write(libs.join("a.jar"), b"").unwrap();
        fs::write(libs.join("notes.txt"), b"").unwrap();

        let cp = collect_classpath(dir.path(), "26.3").unwrap();
        assert_eq!(cp.len(), 3);
        assert!(cp[0].ends_with("versions/26.3/paper-26.3.jar"));
        assert!(cp[1].ends_with("a.jar"));
        assert!(cp[2].ends_with("b.jar"));
    }

    #[test]
    fn sha256_check() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("f");
        fs::write(&file, b"abc").unwrap();
        assert!(file_matches_sha256(
            &file,
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        ));
        assert!(!file_matches_sha256(&file, "00"));
        assert!(!file_matches_sha256(&dir.path().join("missing"), "00"));
    }
}
