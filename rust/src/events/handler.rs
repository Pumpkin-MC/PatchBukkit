use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin_api_macros::with_runtime;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::Uuid;
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{Event, PlayerJoinEvent};

pub struct EventContext {
    pub server: Arc<Server>,
    pub player: Option<Arc<Player>>,
}

pub struct JvmEventPayload {
    pub event: Event,
    pub context: EventContext,
}

pub trait PatchBukkitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload;
    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()>;
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_join::PlayerJoinEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerJoin(PlayerJoinEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    join_message: serde_json::to_string(&self.join_message).unwrap(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerJoin(event) => {
                self.join_message = serde_json::from_str(&event.join_message).ok()?;
                server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
            }
        }

        Some(())
    }
}

pub struct PatchBukkitEventHandler<E: PatchBukkitEvent> {
    plugin_name: String,
    command_tx: mpsc::Sender<JvmCommand>,
    _phantom: PhantomData<E>,
}

impl<E: PatchBukkitEvent> PatchBukkitEventHandler<E> {
    #[must_use]
    pub const fn new(plugin_name: String, command_tx: mpsc::Sender<JvmCommand>) -> Self {
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
    E: PatchBukkitEvent + Payload + Cancellable + 'static,
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
                    payload: event.to_payload(server.clone()),
                    respond_to: tx,
                    plugin: self.plugin_name.clone(),
                })
                .await
            {
                tracing::error!("Failed to send event to JVM worker: {e}");
                return;
            }

            match rx.await {
                Ok(response) => {
                    event.set_cancelled(response.cancelled);
                    event.apply_modifications(server, response.data.unwrap().data.unwrap());
                }
                Err(_) => {
                    tracing::warn!("JVM worker dropped response channel for event");
                }
            }
        })
    }
}
