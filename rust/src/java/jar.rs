use std::{
    fs::File,
    io::Read,
    path::{Path, PathBuf},
};

use anyhow::Result;
use glob::glob;
use zip::ZipArchive;

use crate::config::{paper::PAPER_PLUGIN_CONFIG, spigot::SPIGOT_PLUGIN_CONFIG};

pub fn discover_jar_files(plugin_folder: &PathBuf) -> impl Iterator<Item = PathBuf> {
    let pattern = format!("{}/**/*.jar", plugin_folder.display());

    glob(&pattern)
        .expect("Invalid glob pattern")
        .filter_map(|entry| {
            entry
                .map_err(|e| log::error!("Glob error: {:?}", e))
                .ok()?
                .canonicalize()
                .map_err(|e| log::error!("Canonicalize error: {:?}", e))
                .ok()
        })
}

pub fn read_configs_from_jar<P: AsRef<Path>>(
    jar_path: P,
) -> Result<(Option<String>, Option<String>)> {
    let file = File::open(jar_path.as_ref())?;
    let mut archive = ZipArchive::new(file)?;

    let paper_plugin_yml = match archive.by_name(PAPER_PLUGIN_CONFIG).ok() {
        Some(mut file) => {
            let mut content = String::new();
            file.read_to_string(&mut content)?;
            Some(content)
        }
        None => None,
    };

    let spigot_plugin_yml = match archive.by_name(SPIGOT_PLUGIN_CONFIG).ok() {
        Some(mut file) => {
            let mut content = String::new();
            file.read_to_string(&mut content)?;
            Some(content)
        }
        None => None,
    };

    Ok((paper_plugin_yml, spigot_plugin_yml))
}
