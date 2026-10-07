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

use directories::{get_base_directory, setup_directories};
use tokio::sync::{mpsc, oneshot};

use crate::{
    config::patchbukkit::PatchBukkitConfig,
    java::{
        jvm::{commands::JvmCommand, worker::JvmWorker},
        resources::setup_resources,
    },
};

pub async fn on_load_inner(plugin: &PatchBukkitPlugin, server: Arc<Context>) -> Result<(), String> {
    server.init_log();
    tracing::info!("Starting PatchBukkit");

    let base = get_base_directory(&server)?;
    std::fs::create_dir_all(&base)
        .map_err(|e| format!("Failed to create PatchBukkit base directory: {e}"))?;

    let mut config_path = base;
    config_path.push("patchbukkit.config.toml");
    let config = PatchBukkitConfig::get_or_create(config_path)
        .map_err(|e| format!("Failed to setup PatchBukkit config: {e}"))?;

    // Setup directories
    let dirs = setup_directories(&server, &config.plugins.directory)?;

    // Manage embedded resources
    setup_resources(&dirs.jassets).map_err(|e| format!("Failed to setup resources: {e}"))?;

    let runtime_handle = plugin.runtime.handle().clone();
    let command_tx = plugin.command_tx.clone();
    let server_clone = server.clone();

    server.register_event::<
        pumpkin::plugin::server::packet::PacketReceivedEvent,
        crate::events::handler::PatchBukkitEventHandler<pumpkin::plugin::server::packet::PacketReceivedEvent>,
    >(
        Arc::new(crate::events::handler::PatchBukkitEventHandler::new(
            "patchbukkit".to_string(),
            command_tx.clone(),
        )),
        pumpkin::plugin::EventPriority::Normal,
        false,
    );
    server.register_event::<
        pumpkin::plugin::server::packet::PacketSentEvent,
        crate::events::handler::PatchBukkitEventHandler<pumpkin::plugin::server::packet::PacketSentEvent>,
    >(
        Arc::new(crate::events::handler::PatchBukkitEventHandler::new(
            "patchbukkit".to_string(),
            command_tx.clone(),
        )),
        pumpkin::plugin::EventPriority::Normal,
        false,
    );

    // Run JVM initialization and Java plugin bootstrap in a background task
    // on PatchBukkit's dedicated runtime so Pumpkin's main startup is never blocked.
    plugin.runtime.spawn(async move {
        tracing::info!("Initializing JVM in background...");

        // 1. Initialize JVM
        let (tx, rx) = oneshot::channel();
        if let Err(e) = command_tx
            .send(JvmCommand::Initialize {
                jassets_path: dirs.jassets.clone(),
                paper_cache_path: dirs.paper.clone(),
                paper_runtime_path: dirs.paper_runtime.clone(),
                respond_to: tx,
                context: server_clone.clone(),
                runtime_handle,
                command_tx: command_tx.clone(),
                config,
            })
            .await
        {
            tracing::error!("Failed to send command to initialize JVM: {e}");
            return;
        }

        match rx.await {
            Ok(Ok(())) => tracing::info!("JVM initialization completed successfully"),
            Ok(Err(e)) => {
                tracing::error!("Failed to initialize JVM: {e}");
                return;
            }
            Err(e) => {
                tracing::error!("Unable to receive response from JVM initialization: {e}");
                return;
            }
        }

        // 2. Instantiate all Java plugins
        let (tx, rx) = oneshot::channel();
        if let Err(e) = command_tx
            .send(JvmCommand::InstantiateAllPlugins {
                plugins_dir: dirs.plugins.clone(),
                respond_to: tx,
                server: server_clone.clone(),
                command_tx: command_tx.clone(),
            })
            .await
        {
            tracing::error!("Failed to send command to instantiate plugins: {e}");
            return;
        }

        match rx.await {
            Ok(Ok(())) => tracing::info!("Java plugins instantiated successfully"),
            Ok(Err(e)) => {
                tracing::error!("Failed to instantiate Java plugins: {e}");
                return;
            }
            Err(e) => {
                tracing::error!("Unable to receive response from instantiate plugins: {e}");
                return;
            }
        }

        // 3. Enable all Java plugins
        let (tx, rx) = oneshot::channel();
        if let Err(e) = command_tx
            .send(JvmCommand::EnableAllPlugins { respond_to: tx })
            .await
        {
            tracing::error!("Failed to send command to enable all plugins: {e}");
            return;
        }

        match rx.await {
            Ok(Ok(())) => tracing::info!("PatchBukkit background initialization complete"),
            Ok(Err(e)) => tracing::error!("Failed to enable all plugins: {e}"),
            Err(e) => tracing::error!("Unable to receive response from enable all plugins: {e}"),
        }
    });

    tracing::info!("PatchBukkit loaded successfully");
    Ok(())
}

pub async fn on_unload_inner(
    plugin: &PatchBukkitPlugin,
    _server: Arc<Context>,
) -> Result<(), String> {
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
async fn on_load(&self, server: Arc<Context>) -> Result<(), String> {
    on_load_inner(self, server).await
}

#[plugin_method]
async fn on_unload(&self, server: Arc<Context>) -> Result<(), String> {
    on_unload_inner(self, server).await
}

#[plugin_impl]
pub struct PatchBukkitPlugin {
    pub command_tx: mpsc::Sender<JvmCommand>,
    pub runtime: Arc<tokio::runtime::Runtime>,
}

impl PatchBukkitPlugin {
    #[must_use]
    pub fn new() -> Self {
        let runtime = Arc::new(
            tokio::runtime::Builder::new_multi_thread()
                .worker_threads(4)
                .thread_name("patchbukkit-worker")
                .enable_all()
                .build()
                .expect("Failed to create PatchBukkit Tokio runtime"),
        );

        let (tx, rx) = mpsc::channel(4096);

        let runtime_clone = runtime.clone();
        std::thread::Builder::new()
            .name("patchbukkit-jvm-worker".to_string())
            .spawn(move || {
                runtime_clone.block_on(async move {
                    JvmWorker::new(rx).attach_thread().await;
                });
            })
            .unwrap();
        Self {
            command_tx: tx,
            runtime,
        }
    }
}

impl Default for PatchBukkitPlugin {
    fn default() -> Self {
        Self::new()
    }
}
