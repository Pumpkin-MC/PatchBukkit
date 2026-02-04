use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin_api_macros::with_runtime;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::{Location, Uuid, Vec3, World};
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    Event, PlayerChatEvent, PlayerCommandEvent, PlayerJoinEvent, PlayerLeaveEvent, PlayerMoveEvent,
    ServerBroadcastEvent, ServerCommandEvent,
};

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
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_leave::PlayerLeaveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLeave(PlayerLeaveEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    leave_message: serde_json::to_string(&self.leave_message).unwrap(),
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
            Data::PlayerLeave(event) => {
                self.leave_message = serde_json::from_str(&event.leave_message).ok()?;
                server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_move::PlayerMoveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let yaw = self.player.living_entity.entity.yaw.load();
        let pitch = self.player.living_entity.entity.pitch.load();

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerMove(PlayerMoveEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from: Some(build_location(world_uuid, &self.from, yaw, pitch)),
                    to: Some(build_location(world_uuid, &self.to, yaw, pitch)),
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
            Data::PlayerMove(event) => {
                server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
                if let Some(from) = event.from {
                    if let Some(from_vec) = location_to_vec3(from) {
                        self.from = from_vec;
                    }
                }
                if let Some(to) = event.to {
                    if let Some(to_vec) = location_to_vec3(to) {
                        self.to = to_vec;
                    }
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_chat::PlayerChatEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let recipients = self
            .recipients
            .iter()
            .map(|player| Uuid {
                value: player.gameprofile.id.to_string(),
            })
            .collect();

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChat(PlayerChatEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    message: self.message.clone(),
                    recipients,
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
            Data::PlayerChat(event) => {
                self.message = event.message;
                if !event.recipients.is_empty() {
                    let mut recipients = Vec::new();
                    for recipient in event.recipients {
                        let uuid = uuid::Uuid::from_str(&recipient.value).ok()?;
                        if let Some(player) = server.get_player_by_uuid(uuid) {
                            recipients.push(player);
                        }
                    }
                    self.recipients = recipients;
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerCommand(PlayerCommandEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    command: self.command.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerCommand(event) => {
                self.command = event.command;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::server_command::ServerCommandEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerCommand(ServerCommandEvent {
                    command: self.command.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::ServerCommand(event) => {
                self.command = event.command;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerBroadcast(ServerBroadcastEvent {
                    message: serde_json::to_string(&self.message).unwrap(),
                    sender: serde_json::to_string(&self.sender).unwrap(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::ServerBroadcast(event) => {
                self.message = serde_json::from_str(&event.message).unwrap_or_else(|_| {
                    TextComponent::from_legacy_string(&event.message)
                });
                self.sender = serde_json::from_str(&event.sender).unwrap_or_else(|_| {
                    TextComponent::from_legacy_string(&event.sender)
                });
            }
            _ => {}
        }

        Some(())
    }
}

fn build_location(world_uuid: uuid::Uuid, position: &Vector3<f64>, yaw: f32, pitch: f32) -> Location {
    Location {
        world: Some(World {
            uuid: Some(Uuid {
                value: world_uuid.to_string(),
            }),
        }),
        position: Some(Vec3 {
            x: position.x,
            y: position.y,
            z: position.z,
        }),
        yaw,
        pitch,
    }
}

fn location_to_vec3(location: Location) -> Option<Vector3<f64>> {
    let pos = location.position?;
    Some(Vector3::new(pos.x, pos.y, pos.z))
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
                log::error!("Failed to send event to JVM worker: {e}");
                return;
            }

            match rx.await {
                Ok(response) => {
                    event.set_cancelled(response.cancelled);
                    event.apply_modifications(server, response.data.unwrap().data.unwrap());
                }
                Err(_) => {
                    log::warn!("JVM worker dropped response channel for event");
                }
            }
        })
    }
}
