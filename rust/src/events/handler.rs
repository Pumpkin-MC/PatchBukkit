use std::marker::PhantomData;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin_api_macros::with_runtime;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;

#[derive(Clone)]
pub enum PatchBukkitEvent {
    PlayerJoinEvent {
        server: Arc<Server>,
        player: Arc<Player>,
        join_message: String,
    },
}

pub trait IntoEventData {
    fn into_patch_bukkit_event(&self, server: Arc<Server>) -> PatchBukkitEvent;
}

impl IntoEventData for pumpkin::plugin::player::player_join::PlayerJoinEvent {
    fn into_patch_bukkit_event(&self, server: Arc<Server>) -> PatchBukkitEvent {
        PatchBukkitEvent::PlayerJoinEvent {
            server: server,
            player: self.player.clone(),
            join_message: self.join_message.clone().get_text(),
        }
    }
}

pub struct PatchBukkitEventHandler<E: IntoEventData> {
    plugin_name: String,
    command_tx: mpsc::Sender<JvmCommand>,
    _phantom: PhantomData<E>,
}

impl<E: IntoEventData> PatchBukkitEventHandler<E> {
    pub fn new(plugin_name: String, command_tx: mpsc::Sender<JvmCommand>) -> Self {
        Self {
            plugin_name,
            command_tx,
            _phantom: PhantomData,
        }
    }
}

#[with_runtime(global)]
impl<E> EventHandler<E> for PatchBukkitEventHandler<E>
where
    E: IntoEventData + Payload + Cancellable + 'static,
{
    fn handle_blocking<'a>(
        &'a self,
        server: &'a Arc<Server>,
        event: &'a mut E,
    ) -> BoxFuture<'a, ()> {
        let command_tx = self.command_tx.clone();

        Box::pin(async move {
            let (tx, rx) = oneshot::channel();
            if let Err(e) = command_tx
                .send(JvmCommand::FireEvent {
                    patchbukkit_event: event.into_patch_bukkit_event(server.clone()),
                    respond_to: tx,
                    plugin: self.plugin_name.clone(),
                })
                .await
            {
                log::error!("Failed to send event to JVM worker: {}", e);
                return;
            }

            match rx.await {
                Ok(cancelled) => {
                    if cancelled {
                        log::debug!("Event was cancelled by a Java plugin");
                        event.set_cancelled(true);
                    }
                }
                Err(_) => {
                    log::warn!("JVM worker dropped response channel for event");
                }
            }
        })
    }
}
