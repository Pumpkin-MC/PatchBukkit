#![allow(clippy::async_yields_async)]

use std::sync::Arc;

use pumpkin::plugin::Context;
use pumpkin_api_macros::{plugin_impl, plugin_method};

pub mod commands;
pub mod config;
pub mod directories;
pub mod events;
pub mod java;
pub mod proto;

use directories::setup_directories;
use java::jar::discover_jar_files;
use tokio::sync::{
    mpsc::{self, Receiver},
    oneshot,
};

use crate::java::{
    jvm::{
        commands::{JvmCommand, LoadPluginResult},
        worker::JvmWorker,
    },
    resources::setup_j4rs,
};

async fn on_load_inner(plugin: &PatchBukkitPlugin, server: Arc<Context>) -> Result<(), String> {
    server.init_log();
    log::info!("Starting PatchBukkit");

    // Setup directories
    let dirs = setup_directories(&server)?;

    proto::patchbukkit::common::Uuid::default();

    // Discover and prepare JAR files
    let jar_paths = discover_jar_files(&dirs.plugins);
    for jar_path in jar_paths {
        {
            let (tx, rx) = oneshot::channel();
            let result = plugin
                .command_tx
                .send(JvmCommand::LoadPlugin {
                    plugin_path: jar_path.clone(),
                    respond_to: tx,
                })
                .await;
            if let Err(e) = result {
                log::error!(
                    "Failed to send command to load plugin {}: {}",
                    jar_path.display(),
                    e
                );
            }
            match rx.await {
                Ok(result) => match result {
                    LoadPluginResult::SuccessfullyLoadedSpigot => {
                        log::info!("Loaded Spigot plugin from JAR `{}`", jar_path.display());
                    }
                    LoadPluginResult::SuccessfullyLoadedPaper => {
                        log::info!("Loaded Paper plugin from JAR `{}`", jar_path.display());
                    }
                    LoadPluginResult::FailedToLoadSpigotPlugin(error) => {
                        log::error!(
                            "Failed to load Spigot plugin from JAR `{}` with error: {}",
                            jar_path.display(),
                            error
                        );
                    }
                    LoadPluginResult::FailedToLoadPaperPlugin(error) => {
                        log::error!(
                            "Failed to load Paper plugin from JAR `{}` with error: {}",
                            jar_path.display(),
                            error
                        );
                    }
                    LoadPluginResult::FailedToReadConfigurationFile(error) => {
                        log::error!(
                            "Failed to read configuration file from JAR `{}`: {}",
                            jar_path.display(),
                            error
                        );
                    }
                    LoadPluginResult::NoConfigurationFile => {
                        log::warn!(
                            "No configuration file found for plugin from JAR `{}`",
                            jar_path.display()
                        );
                    }
                },
                Err(e) => log::error!(
                    "Failed to receive load plugin response for JAR `{}`: {}",
                    jar_path.display(),
                    e
                ),
            }
        };
    }

    // Manage embedded resources
    setup_j4rs(&dirs.j4rs).map_err(|e| format!("Failed to setup J4RS: {e}"))?;

    {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::Initialize {
                j4rs_path: dirs.j4rs,
                respond_to: tx,
                context: server.clone(),
                command_tx: plugin.command_tx.clone(),
            })
            .await
            .map_err(|e| format!("Failed to send command to initialize J4RS: {e}"))?;
        rx.await
            .map_err(|e| format!("Unable to receive response from J4RS initialization: {e}"))?
            .map_err(|e| format!("Failed to initialize all plugins: {e}"))?;
    }

    {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::InstantiateAllPlugins {
                respond_to: tx,
                server: server.clone(),
                command_tx: plugin.command_tx.clone(),
            })
            .await
            .map_err(|e| format!("Failed to send command to instantiate plugins: {e}"))?;
        rx.await
            .map_err(|e| format!("Unable to receive response from instantiate plugins: {e}"))?
            .map_err(|e| format!("Failed to instantiate all plugins: {e}"))?;
    }

    {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::EnableAllPlugins { respond_to: tx })
            .await
            .map_err(|e| format!("Failed to send command to enable all plugins: {e}"))?;
        rx.await
            .map_err(|e| format!("Unable to receive response from enable all plugins: {e}"))?
            .map_err(|e| format!("Failed to enable all plugins: {e}"))?;
    };

    Ok(())
}

async fn on_unload_inner(plugin: &PatchBukkitPlugin, _server: Arc<Context>) -> Result<(), String> {
    {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::DisableAllPlugins { respond_to: tx })
            .await
            .map_err(|e| format!("Failed to send command to disable all plugins: {e}"))?;
        rx.await
            .map_err(|e| format!("Unable to receive response from disable all plugins: {e}"))?
            .map_err(|e| format!("Failed to disable all plugins: {e}"))?;
    }

    {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::Shutdown { respond_to: tx })
            .await
            .map_err(|e| format!("Failed to send command to shutdown: {e}"))?;
        rx.await
            .map_err(|e| format!("Unable to receive response from shutdown: {e}"))?
            .map_err(|e| format!("Failed to shutdown: {e}"))?;
    }

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
    pub command_tx: mpsc::Sender<JvmCommand>,
}

impl PatchBukkitPlugin {
    #[must_use]
    pub fn new() -> Self {
        let (tx, rx) = mpsc::channel(100);

        #[tokio::main]
        pub async fn jvm_thread_task(rx: Receiver<JvmCommand>) {
            JvmWorker::new(rx).attach_thread().await;
        }

        std::thread::Builder::new()
            .name("patchbukkit-jvm-worker".to_string())
            .spawn(move || {
                jvm_thread_task(rx);
            })
            .unwrap();
        Self { command_tx: tx }
    }
}

impl Default for PatchBukkitPlugin {
    fn default() -> Self {
        Self::new()
    }
}
