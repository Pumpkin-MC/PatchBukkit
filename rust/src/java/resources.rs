use std::{
    collections::HashSet,
    fs,
    path::{Path, PathBuf},
};

use anyhow::Result;
use rust_embed::Embed;

#[derive(Embed)]
#[folder = "resources/"]
pub struct Resources;

pub fn cleanup_stale_files(j4rs_folder: &PathBuf) {
    let allowed: HashSet<PathBuf> = Resources::iter()
        .map(|p| PathBuf::from(p.to_string()))
        .collect();

    for entry in walkdir::WalkDir::new(j4rs_folder)
        .into_iter()
        .filter_map(std::result::Result::ok)
        .filter(|e| e.file_type().is_file())
    {
        let path = entry.path();

        if let Ok(rel_path) = path.strip_prefix(j4rs_folder)
            && !allowed.contains(rel_path)
        {
            log::warn!("Removing stale j4rs file: {}", rel_path.display());
            if let Err(e) = fs::remove_file(path) {
                log::error!("Failed to remove stale file {}: {}", path.display(), e);
            }
        }
    }
}

pub fn sync_embedded_resources(j4rs_folder: &Path) -> Result<(), String> {
    for resource_path_str in Resources::iter() {
        let resource_path = j4rs_folder.join(resource_path_str.to_string());
        let resource = Resources::get(&resource_path_str).unwrap();

        if resource_path.exists() {
            update_resource_if_changed(&resource_path, &resource.data)?;
        } else {
            log::info!("Extracting new resource: {}", resource_path.display());
            write_resource(&resource_path, &resource.data)?;
        }
    }
    Ok(())
}

fn write_resource(path: &Path, data: &[u8]) -> Result<(), String> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)
            .map_err(|_| format!("Failed to create directory: {}", parent.display()))?;
    }

    fs::write(path, data).map_err(|_| format!("Failed to write resource: {}", path.display()))?;
    Ok(())
}

fn update_resource_if_changed(path: &Path, new_data: &[u8]) -> Result<(), String> {
    // Quick check: If file sizes differ, it's definitely changed, TODO: use Hash ?
    let metadata = fs::metadata(path).ok();
    let size_matches = metadata.is_some_and(|m| m.len() == new_data.len() as u64);

    if !size_matches || fs::read(path).map_err(|e| e.to_string())? != new_data {
        log::debug!("Updating changed resource: {}", path.display());
        fs::write(path, new_data)
            .map_err(|_| format!("Failed to update resource: {}", path.display()))?;
    }

    Ok(())
}
