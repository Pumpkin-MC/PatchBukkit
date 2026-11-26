#![allow(clippy::async_yields_async)]

use std::sync::{Arc, atomic::Ordering};

use pumpkin::plugin::player::player_login::PlayerLoginEvent;
use pumpkin::plugin::{BoxFuture, Cancellable, Context, EventHandler, EventPriority};
use pumpkin::server::Server;
use pumpkin_api_macros::{plugin_impl, plugin_method};
use pumpkin_util::text::TextComponent;

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

/// Refuses logins until the JVM and its plugins are ready (`block-joins-until-ready`).
struct JoinGate;

impl EventHandler<PlayerLoginEvent> for JoinGate {
    fn handle_blocking<'a>(
        &'a self,
        _server: &'a Arc<Server>,
        event: &'a mut PlayerLoginEvent,
    ) -> BoxFuture<'a, ()> {
        if !crate::events::handler::JVM_READY.load(Ordering::Acquire) {
            event.set_cancelled(true);
            event.kick_message =
                TextComponent::text("The server is still starting. Please rejoin in a moment.");
        }
        Box::pin(async {})
    }
}

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

    if config.settings.block_joins_until_ready {
        server.register_event::<PlayerLoginEvent, JoinGate>(
            Arc::new(JoinGate),
            EventPriority::Highest,
            true,
        );
    }

    // Setup directories
    let dirs = setup_directories(&server, &config.plugins.directory)?;

    // Manage embedded resources
    setup_resources(&dirs.jassets).map_err(|e| format!("Failed to setup resources: {e}"))?;

    let runtime_handle = plugin.runtime.handle().clone();
    let command_tx = plugin.command_tx.clone();
    let server_clone = server.clone();

    // Run JVM initialization and Paper's native plugin bootstrap in a background task
    // on PatchBukkit's dedicated runtime so Pumpkin's main startup is never blocked.
    plugin.runtime.spawn(async move {
        tracing::info!("Initializing JVM and loading plugins via Paper in background...");

        let (tx, rx) = oneshot::channel();
        if let Err(e) = command_tx
            .send(JvmCommand::Initialize {
                jassets_path: dirs.jassets.clone(),
                paper_cache_path: dirs.paper.clone(),
                paper_runtime_path: dirs.paper_runtime.clone(),
                plugins_dir: dirs.plugins.clone(),
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
            Ok(Ok(())) => {
                crate::events::handler::JVM_READY.store(true, Ordering::Release);

                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::server::packet::PacketReceivedEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );

                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::server::packet::PacketSentEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    true,
                );

                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::server::server_tick_start::ServerTickStartEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );

                // Lifecycle & core bridge events that must always flow to the headless Paper
                // server even before any plugin explicitly registers a listener for them.
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_join::PlayerJoinEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    true,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_leave::PlayerLeaveEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    true,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_changed_world::PlayerChangedWorldEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_custom_payload::PlayerCustomPayloadEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::player_permission_check::PlayerPermissionCheckEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    true,
                );
                crate::java::native_callbacks::events::register_single_event::<
                    pumpkin::plugin::player::inventory_close::InventoryCloseEvent,
                >(
                    &server_clone,
                    &command_tx,
                    pumpkin::plugin::EventPriority::Normal,
                    false,
                );

                // Players that joined before the Java plugins finished loading were sent the
                // command tree without any Bukkit commands. Resend it so they get commands and
                // suggestions without having to rejoin.
                server_clone.reload_commands_for_everyone();

                tracing::info!("PatchBukkit background initialization complete");
            }
            Ok(Err(e)) => tracing::error!("Failed to initialize JVM and plugins: {e:#}"),
            Err(e) => tracing::error!("Unable to receive response from JVM initialization: {e}"),
        }
    });

    tracing::info!("PatchBukkit loaded successfully");
    Ok(())
}

pub async fn on_unload_inner(
    plugin: &PatchBukkitPlugin,
    _server: Arc<Context>,
) -> Result<(), String> {
    crate::events::handler::JVM_READY.store(false, Ordering::Release);
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

        std::thread::Builder::new()
            .name("patchbukkit-jvm-worker".to_string())
            .spawn(move || {
                JvmWorker::new(rx).attach_thread();
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
