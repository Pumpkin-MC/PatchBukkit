use std::{fs, path::PathBuf};

use pumpkin::plugin::Context;

pub struct PatchBukkitDirectories {
    pub base: PathBuf,
    pub plugins: PathBuf,
    pub plugin_updates: PathBuf,
    pub j4rs: PathBuf,
    pub jassets: PathBuf,
}

pub fn setup_directories(server: &Context) -> Result<PatchBukkitDirectories, String> {
    let base = std::path::absolute(server.get_data_folder())
        .map_err(|_| "Failed to get absolute directory from relative")?;

    let plugins = base.join("patchbukkit-plugins");
    let plugin_updates = plugins.join("update");
    let j4rs = base.join("j4rs");
    let jassets = j4rs.join("jassets");

    fs::create_dir_all(&jassets)
        .map_err(|err| format!("Failed to create jassets folder: {:?}", err))?;

    fs::create_dir_all(&plugins)
        .map_err(|err| format!("Failed to create patchbukkit-plugins folder: {:?}", err))?;

    Ok(PatchBukkitDirectories {
        base,
        plugins,
        plugin_updates,
        j4rs,
        jassets,
    })
}
