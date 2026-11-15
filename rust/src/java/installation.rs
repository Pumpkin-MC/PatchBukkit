//! Detection and validation of the host Java installation.
//!
//! PatchBukkit requires Java 25 or newer both to run Paperclip on first start
//! and to host the embedded HotSpot JVM (`patchbukkit.jar` and Paper 26.3+
//! compile to class file version 69.0). Checking this upfront avoids
//! downloading Paper only to fail with a missing `java` binary or a cryptic
//! `UnsupportedClassVersionError` inside JNI.

use std::{
    io::ErrorKind,
    path::{Path, PathBuf},
    process::Command,
};

use anyhow::{Context, Result, bail};

/// Minimum Java feature/specification version required by PatchBukkit and Paper 26.3+.
pub const MIN_JAVA_VERSION: u32 = 25;

/// Validated Java runtime on the host system.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct JavaInstallation {
    /// Parsed major specification version (e.g. `25`, `27`).
    pub major: u32,
    /// Full runtime version string (e.g. `"25.0.1"`).
    pub version: String,
    /// Path to the `java` executable.
    pub java_bin: PathBuf,
    /// Resolved `java.home` directory reported by the runtime, if available.
    pub java_home: Option<PathBuf>,
    /// Path to `libjvm` inside `java_home`, if found.
    pub libjvm_path: Option<PathBuf>,
}

/// Locates `java` via `JAVA_HOME` or `PATH` and verifies that it is installed,
/// runnable, and at least Java [`MIN_JAVA_VERSION`].
pub fn check_java_installation() -> Result<JavaInstallation> {
    let (java_bin, invalid_java_home) =
        resolve_java_binary(std::env::var_os("JAVA_HOME").as_deref());
    probe_java_binary(&java_bin, invalid_java_home.as_deref())
}

const fn java_exe_name() -> &'static str {
    if cfg!(windows) { "java.exe" } else { "java" }
}

const fn jvm_dyn_lib_name() -> &'static str {
    if cfg!(target_os = "windows") {
        "jvm.dll"
    } else if cfg!(target_os = "macos") {
        "libjvm.dylib"
    } else {
        "libjvm.so"
    }
}

/// Resolves the `java` executable to invoke, returning `(candidate_binary, invalid_java_home)`.
fn resolve_java_binary(java_home_env: Option<&std::ffi::OsStr>) -> (PathBuf, Option<PathBuf>) {
    let exe = java_exe_name();
    if let Some(home) = java_home_env
        && !home.is_empty()
    {
        let home_path = PathBuf::from(home);
        let candidate = home_path.join("bin").join(exe);
        if candidate.is_file() {
            return (candidate, None);
        }
        tracing::warn!(
            "JAVA_HOME is set to '{}', but '{}' does not exist; falling back to PATH",
            home_path.display(),
            candidate.display()
        );
        return (PathBuf::from(exe), Some(home_path));
    }
    (PathBuf::from(exe), None)
}

fn probe_java_binary(
    java_bin: &Path,
    invalid_java_home: Option<&Path>,
) -> Result<JavaInstallation> {
    let exe = java_exe_name();
    let output = match Command::new(java_bin)
        .args(["-XshowSettings:properties", "-version"])
        .output()
    {
        Ok(out) if out.status.success() => out,
        Ok(out) => {
            // Fall back to plain `-version` in case a non-HotSpot JVM rejects `-XshowSettings`.
            if let Ok(fallback) = Command::new(java_bin).arg("-version").output()
                && fallback.status.success()
            {
                fallback
            } else {
                let details = combined_output(&out.stdout, &out.stderr);
                let details = details.trim();
                if details.is_empty() {
                    bail!(
                        "Failed to run `{}` ({}). PatchBukkit requires Java {MIN_JAVA_VERSION} or newer.",
                        java_bin.display(),
                        out.status
                    );
                }
                bail!(
                    "Failed to run `{}` ({}): {details}\nPatchBukkit requires Java {MIN_JAVA_VERSION} or newer.",
                    java_bin.display(),
                    out.status
                );
            }
        }
        Err(err) if err.kind() == ErrorKind::NotFound => {
            if let Some(bad_home) = invalid_java_home {
                bail!(
                    "Java is not installed or could not be found (`JAVA_HOME` is set to '{}', where `bin/{exe}` does not exist, and `{}` was not found in `PATH`). PatchBukkit requires Java {MIN_JAVA_VERSION} or newer.",
                    bad_home.display(),
                    java_bin.display()
                );
            }
            bail!(
                "Java is not installed or could not be found (`{}` was not found in `JAVA_HOME` or `PATH`). PatchBukkit requires Java {MIN_JAVA_VERSION} or newer.",
                java_bin.display()
            );
        }
        Err(err) => {
            return Err(err).with_context(|| {
                format!(
                    "Failed to execute `{}` (PatchBukkit requires Java {MIN_JAVA_VERSION} or newer)",
                    java_bin.display()
                )
            });
        }
    };

    let text = combined_output(&output.stdout, &output.stderr);
    let parsed = parse_java_output(&text);

    let Some(major) = parsed.major else {
        bail!(
            "Could not determine Java version from `{}` output. PatchBukkit requires Java {MIN_JAVA_VERSION} or newer.",
            java_bin.display()
        );
    };

    let version = parsed.version.unwrap_or_else(|| major.to_string());
    let resolved_bin = parsed
        .java_home
        .as_deref()
        .map(|home| home.join("bin").join(exe))
        .filter(|p| p.is_file())
        .unwrap_or_else(|| java_bin.to_path_buf());

    if major < MIN_JAVA_VERSION {
        let location = parsed.java_home.as_deref().unwrap_or(&resolved_bin);
        bail!(
            "Unsupported Java version: found Java {major} ({version}) at '{}', but PatchBukkit requires Java {MIN_JAVA_VERSION} or newer.",
            location.display()
        );
    }

    let libjvm_path = parsed.java_home.as_deref().and_then(find_libjvm);

    Ok(JavaInstallation {
        major,
        version,
        java_bin: resolved_bin,
        java_home: parsed.java_home,
        libjvm_path,
    })
}

fn combined_output(stdout: &[u8], stderr: &[u8]) -> String {
    let err = String::from_utf8_lossy(stderr);
    let out = String::from_utf8_lossy(stdout);
    if out.is_empty() {
        err.into_owned()
    } else if err.is_empty() {
        out.into_owned()
    } else {
        format!("{err}\n{out}")
    }
}

#[derive(Debug, Default, PartialEq, Eq)]
struct ParsedJavaOutput {
    major: Option<u32>,
    version: Option<String>,
    java_home: Option<PathBuf>,
}

fn parse_java_output(text: &str) -> ParsedJavaOutput {
    let mut spec_version: Option<String> = None;
    let mut prop_version: Option<String> = None;
    let mut banner_version: Option<String> = None;
    let mut java_home: Option<PathBuf> = None;

    for line in text.lines() {
        let trimmed = line.trim();
        if let Some((key, value)) = trimmed.split_once('=') {
            let key = key.trim();
            let value = value.trim();
            if !value.is_empty() {
                match key {
                    "java.specification.version" => {
                        spec_version.get_or_insert_with(|| value.to_string());
                    }
                    "java.version" => {
                        prop_version.get_or_insert_with(|| value.to_string());
                    }
                    "java.home" => {
                        java_home.get_or_insert_with(|| PathBuf::from(value));
                    }
                    _ => {}
                }
            }
        }

        if banner_version.is_none()
            && let Some(ver) = extract_banner_version(trimmed)
        {
            banner_version = Some(ver);
        }
    }

    let major = spec_version
        .as_deref()
        .and_then(parse_java_major)
        .or_else(|| prop_version.as_deref().and_then(parse_java_major))
        .or_else(|| banner_version.as_deref().and_then(parse_java_major));

    let version = prop_version.or(banner_version).or(spec_version);

    ParsedJavaOutput {
        major,
        version,
        java_home,
    }
}

/// Extracts a version token from standard `java -version` / `java --version` banner lines,
/// e.g. `openjdk version "25.0.1" 2025-10-21` or `java version "1.8.0_412"`.
fn extract_banner_version(line: &str) -> Option<String> {
    if let Some((_, rest)) = line.split_once("version \"")
        && let Some((ver, _)) = rest.split_once('"')
        && parse_java_major(ver).is_some()
    {
        return Some(ver.to_string());
    }
    let mut parts = line.split_whitespace();
    let first = parts.next()?;
    if matches!(first, "openjdk" | "java") {
        let second = parts.next()?.trim_matches('"');
        if parse_java_major(second).is_some() {
            return Some(second.to_string());
        }
    }
    None
}

/// Parses a Java version string (`"25"`, `"25.0.1"`, `"25-ea"`, `"1.8.0_412"`) into its major feature number.
fn parse_java_major(version: &str) -> Option<u32> {
    let trimmed = version.trim().trim_matches('"');
    let normalized = trimmed.strip_prefix("1.").unwrap_or(trimmed);
    let digit_len = normalized.bytes().take_while(u8::is_ascii_digit).count();
    if digit_len == 0 {
        return None;
    }
    normalized[..digit_len].parse::<u32>().ok()
}

fn find_libjvm(java_home: &Path) -> Option<PathBuf> {
    let lib_name = jvm_dyn_lib_name();
    let fast_candidates = [
        java_home.join("lib").join("server").join(lib_name),
        java_home.join("bin").join("server").join(lib_name),
        java_home.join("lib").join("client").join(lib_name),
        java_home.join("bin").join("client").join(lib_name),
    ];
    for candidate in fast_candidates {
        if candidate.is_file() {
            return Some(candidate);
        }
    }

    walkdir::WalkDir::new(java_home)
        .into_iter()
        .filter_map(std::result::Result::ok)
        .filter(|e| e.file_type().is_file())
        .map(walkdir::DirEntry::into_path)
        .find(|p| p.file_name().and_then(|n| n.to_str()) == Some(lib_name))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_java_major_versions() {
        assert_eq!(parse_java_major("25"), Some(25));
        assert_eq!(parse_java_major("25.0.1"), Some(25));
        assert_eq!(parse_java_major("25-ea"), Some(25));
        assert_eq!(parse_java_major("27.0.1.0.3"), Some(27));
        assert_eq!(parse_java_major("21.0.4"), Some(21));
        assert_eq!(parse_java_major("17"), Some(17));
        assert_eq!(parse_java_major("1.8"), Some(8));
        assert_eq!(parse_java_major("1.8.0_412"), Some(8));
        assert_eq!(parse_java_major(""), None);
        assert_eq!(parse_java_major("invalid"), None);
    }

    #[test]
    fn parses_xshowsettings_output() {
        let output = "\
Picked up JAVA_TOOL_OPTIONS: -Xmx512m
Property settings:
    file.encoding = UTF-8
    java.home = /usr/lib/jvm/java-25-openjdk
    java.runtime.version = 25.0.1+9
    java.specification.version = 25
    java.version = 25.0.1

openjdk version \"25.0.1\" 2025-10-21
OpenJDK Runtime Environment (build 25.0.1+9)
OpenJDK 64-Bit Server VM (build 25.0.1+9, mixed mode, sharing)
";
        let parsed = parse_java_output(output);
        assert_eq!(parsed.major, Some(25));
        assert_eq!(parsed.version.as_deref(), Some("25.0.1"));
        assert_eq!(
            parsed.java_home.as_deref(),
            Some(Path::new("/usr/lib/jvm/java-25-openjdk"))
        );
    }

    #[test]
    fn parses_legacy_banner_only_output() {
        let output = "\
java version \"1.8.0_412\"
Java(TM) SE Runtime Environment (build 1.8.0_412-b08)
Java HotSpot(TM) 64-Bit Server VM (build 25.412-b08, mixed mode)
";
        let parsed = parse_java_output(output);
        assert_eq!(parsed.major, Some(8));
        assert_eq!(parsed.version.as_deref(), Some("1.8.0_412"));
        assert_eq!(parsed.java_home, None);
    }

    #[test]
    fn reports_clear_error_when_java_missing() {
        let missing = Path::new("/nonexistent/patchbukkit/bin/java");
        let err = probe_java_binary(missing, None).unwrap_err().to_string();
        assert!(
            err.contains("Java is not installed or could not be found"),
            "{err}"
        );
        assert!(err.contains("Java 25 or newer"), "{err}");
    }

    #[test]
    fn reports_invalid_java_home_when_fallback_also_missing() {
        let missing = Path::new("/nonexistent/patchbukkit/bin/java");
        let bad_home = Path::new("/wrong/java_home");
        let err = probe_java_binary(missing, Some(bad_home))
            .unwrap_err()
            .to_string();
        assert!(err.contains("/wrong/java_home"), "{err}");
        assert!(err.contains("Java 25 or newer"), "{err}");
    }

    #[cfg(unix)]
    #[test]
    fn rejects_outdated_java_version() {
        use std::os::unix::fs::PermissionsExt;

        let dir = tempfile::tempdir().unwrap();
        let fake_java = dir.path().join("java");
        std::fs::write(
            &fake_java,
            "#!/bin/sh\necho '    java.specification.version = 21' >&2\necho '    java.version = 21.0.5' >&2\necho '    java.home = /opt/jdk-21' >&2\n",
        )
        .unwrap();
        std::fs::set_permissions(&fake_java, std::fs::Permissions::from_mode(0o755)).unwrap();

        let err = probe_java_binary(&fake_java, None).unwrap_err().to_string();
        assert!(
            err.contains("Unsupported Java version: found Java 21 (21.0.5)"),
            "{err}"
        );
        assert!(err.contains("Java 25 or newer"), "{err}");
    }

    #[test]
    fn detects_host_java_installation() {
        let info = check_java_installation().expect("host should have Java 25+ installed");
        assert!(info.major >= MIN_JAVA_VERSION);
        assert!(info.java_home.is_some());
        assert!(info.libjvm_path.is_some());
    }
}
