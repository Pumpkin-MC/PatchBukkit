use std::{
    fs,
    path::{Path, PathBuf},
};

use pumpkin::plugin::Context;

pub struct PatchBukkitDirectories {
    pub base: PathBuf,
    pub plugins: PathBuf,
    pub plugin_updates: PathBuf,
    pub jassets: PathBuf,
    /// Cache for the Paper server that is downloaded and patched at runtime
    pub paper: PathBuf,
    /// Working directory of the headless Paper server (its configs and shadow level)
    pub paper_runtime: PathBuf,
}

pub fn get_base_directory(server: &Context) -> Result<PathBuf, String> {
    let data_folder = std::path::absolute(server.get_data_folder())
        .map_err(|_| "Failed to get absolute directory from relative")?;
    let server_root = data_folder
        .parent()
        .ok_or("Failed to determine server root from PatchBukkit data folder")?;
    Ok(server_root.join("patchbukkit"))
}

pub fn setup_directories(
    server: &Context,
    plugins_dir_name: &str,
) -> Result<PatchBukkitDirectories, String> {
    let data_folder = std::path::absolute(server.get_data_folder())
        .map_err(|_| "Failed to get absolute directory from relative")?;
    let server_root = data_folder
        .parent()
        .ok_or("Failed to determine server root from PatchBukkit data folder")?;
    let base = server_root.join("patchbukkit");

    let plugins = if Path::new(plugins_dir_name).is_absolute() {
        PathBuf::from(plugins_dir_name)
    } else if plugins_dir_name == "patchbukkit-plugins" {
        base.join("patchbukkit-plugins")
    } else {
        server_root.join(plugins_dir_name)
    };

    let plugin_updates = plugins.join("update");
    let jassets = base.join("jassets");
    let paper = base.join("cache").join("paper");
    let paper_runtime = base.join("paper-runtime");

    fs::create_dir_all(&jassets)
        .map_err(|err| format!("Failed to create jassets folder: {err:?}"))?;

    fs::create_dir_all(&plugins)
        .map_err(|err| format!("Failed to create plugins folder: {err:?}"))?;

    let patchbukkit_jar_dest = jassets.join("patchbukkit.jar");

    let candidates = [
        server_root
            .join("java")
            .join("patchbukkit")
            .join("build")
            .join("libs")
            .join("patchbukkit.jar"),
        server_root
            .join("patchbukkit")
            .join("java")
            .join("patchbukkit")
            .join("build")
            .join("libs")
            .join("patchbukkit.jar"),
    ];

    for candidate in &candidates {
        if candidate.exists() {
            let _ = fs::copy(candidate, &patchbukkit_jar_dest);
            break;
        }
    }

    Ok(PatchBukkitDirectories {
        base,
        plugins,
        plugin_updates,
        jassets,
        paper,
        paper_runtime,
    })
}
