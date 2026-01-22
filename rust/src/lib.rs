use std::sync::Arc;

use j4rs::Jvm;
use pumpkin::plugin::Context;
use pumpkin_api_macros::{plugin_impl, plugin_method};

pub mod config;
pub mod directories;
pub mod events;
pub mod java;
pub mod plugin;

use directories::setup_directories;
use java::{
    jar::discover_jar_files,
    jvm::{initialize_jvm, setup_patchbukkit_server},
    resources::{cleanup_stale_files, sync_embedded_resources},
};

use crate::{
    events::register_handlers, java::jar::read_configs_from_jar, plugin::manager::PluginManager,
};

async fn on_load_inner(plugin: &mut PatchBukkitPlugin, server: Arc<Context>) -> Result<(), String> {
    server.init_log();
    log::info!("Starting PatchBukkit");

    register_handlers(&server).await;

    // Setup directories
    let dirs = setup_directories(&server)?;

    // Discover and prepare JAR files
    let jar_paths = discover_jar_files(&dirs.plugins);
    for jar_path in &jar_paths {
        match read_configs_from_jar(jar_path) {
            Ok(configs) => match configs {
                (Some(paper_plugin_config), spigot @ _) => {
                    match plugin.plugin_manager.load_paper_plugin(
                        jar_path,
                        &paper_plugin_config,
                        &spigot,
                    ) {
                        Ok(_) => log::info!("Loaded Paper plugin from JAR: {}", jar_path.display()),
                        Err(err) => log::error!("Failed to load Paper plugin from JAR: {}", err),
                    }
                }
                (None, Some(spigot)) => {
                    match plugin.plugin_manager.load_spigot_plugin(jar_path, &spigot) {
                        Ok(_) => {
                            log::info!("Loaded Spigot plugin from JAR: {}", jar_path.display())
                        }
                        Err(err) => log::error!("Failed to load Spigot plugin from JAR: {}", err),
                    }
                }
                (None, None) => log::warn!(
                    "Could not find any plugin configuration in JAR: {}",
                    jar_path.display()
                ),
            },
            Err(err) => {
                log::error!(
                    "Failed to read configs from PatchBukkit plugin jar: {}",
                    err
                );
            }
        }
    }

    // Manage embedded resources
    cleanup_stale_files(&dirs.j4rs);
    sync_embedded_resources(&dirs.j4rs)?;

    // Initialize JVM and PatchBukkit server
    let jvm = initialize_jvm(&dirs.j4rs)?;
    setup_patchbukkit_server(&jvm)?;

    plugin
        .plugin_manager
        .load_all_plugins(&jvm)
        .map_err(|err| format!("Failed to load PatchBukkit plugins: {}", err))?;

    plugin
        .plugin_manager
        .enable_all_plugins(&jvm)
        .map_err(|err| format!("Failed to enable PatchBukkit plugins: {}", err))?;

    Ok(())
}

async fn on_unload_inner(
    plugin: &mut PatchBukkitPlugin,
    _server: Arc<Context>,
) -> Result<(), String> {
    let jvm = Jvm::attach_thread().map_err(|e| format!("Failed to attach thread to JVM: {}", e))?;

    plugin
        .plugin_manager
        .disable_all_plugins(&jvm)
        .map_err(|err| format!("Failed to disable PatchBukkit plugins: {}", err))?;

    plugin
        .plugin_manager
        .unload_all_plugins()
        .map_err(|err| format!("Failed to unload PatchBukkit plugins: {}", err))?;

    Ok(())
}

#[plugin_method]
async fn on_load(&mut self, server: Arc<Context>) -> Result<(), String> {
    on_load_inner(self, server).await
}

#[plugin_method]
async fn on_unload(&mut self, server: Arc<Context>) -> Result<(), String> {
    on_unload_inner(self, server).await
}

#[plugin_impl]
pub struct PatchBukkitPlugin {
    plugin_manager: PluginManager,
}

impl PatchBukkitPlugin {
    pub fn new() -> Self {
        PatchBukkitPlugin {
            plugin_manager: PluginManager::new(),
        }
    }
}

impl Default for PatchBukkitPlugin {
    fn default() -> Self {
        Self::new()
    }
}
