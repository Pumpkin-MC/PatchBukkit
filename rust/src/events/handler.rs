#![allow(clippy::all)]
use pumpkin::plugin::{BoxFuture, EventHandler, Payload};
use pumpkin::server::Server;
use std::marker::PhantomData;
use std::sync::Arc;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::Uuid;
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::*;

pub struct EventContext {
    pub server: Arc<Server>,
    pub player: Option<Arc<pumpkin::entity::player::Player>>,
}

pub struct JvmEventPayload {
    pub event: Event,
    pub context: EventContext,
}

pub trait PatchBukkitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload;
    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        let _ = (server, data);
        Some(())
    }
    fn set_cancelled(&mut self, _cancelled: bool) {}
}

impl PatchBukkitEvent
    for pumpkin::plugin::world::async_structure_generate::AsyncStructureGenerateEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::AsyncStructureGenerate(AsyncStructureGenerateEvent {
                    world_name: self.world_name.clone(),
                    structure_name: self.structure_name.clone(),
                    block_x: self.pos.0.x,
                    block_y: self.pos.0.y,
                    block_z: self.pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::async_structure_spawn::AsyncStructureSpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::AsyncStructureSpawn(AsyncStructureSpawnEvent {
                    world_name: self.world_name.clone(),
                    structure_name: self.structure_name.clone(),
                    block_x: self.pos.0.x,
                    block_y: self.pos.0.y,
                    block_z: self.pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::chunk_load::ChunkLoad {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ChunkLoad(ChunkLoad { world_uuid: None })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::chunk_populate::ChunkPopulateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ChunkPopulate(ChunkPopulateEvent {
                    chunk_x: self.chunk_pos.x,
                    chunk_z: self.chunk_pos.y,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::chunk_save::ChunkSave {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ChunkSave(ChunkSave { world_uuid: None })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::chunk_send::ChunkSend {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ChunkSend(ChunkSend { world_uuid: None })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::chunk_unload::ChunkUnloadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ChunkUnload(ChunkUnloadEvent {
                    chunk_x: self.chunk_pos.x,
                    chunk_z: self.chunk_pos.y,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::entities_load::EntitiesLoadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntitiesLoad(EntitiesLoadEvent {
                    chunk_x: self.chunk_pos.x,
                    chunk_z: self.chunk_pos.y,
                    entity_count: self.entity_count as i64,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::entities_unload::EntitiesUnloadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntitiesUnload(EntitiesUnloadEvent {
                    chunk_x: self.chunk_pos.x,
                    chunk_z: self.chunk_pos.y,
                    entity_count: self.entity_count as i64,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::generic_game::GenericGameEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::GenericGame(GenericGameEvent {
                    event_key: self.event_key.clone(),
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::lightning_strike::LightningStrikeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::LightningStrike(LightningStrikeEvent {
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    is_effect: self.is_effect,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::loot_generate::LootGenerateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::LootGenerate(LootGenerateEvent {
                    loot_table: self.loot_table.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::portal_create::PortalCreateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PortalCreate(PortalCreateEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    portal_type: format!("{:?}", self.portal_type),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::spawn_change::SpawnChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SpawnChange(SpawnChangeEvent {
                    world_uuid: None,
                    previous_position_x: self.previous_position.0.x,
                    previous_position_y: self.previous_position.0.y,
                    previous_position_z: self.previous_position.0.z,
                    previous_yaw: self.previous_yaw,
                    previous_pitch: self.previous_pitch,
                    new_position_x: self.new_position.0.x,
                    new_position_y: self.new_position.0.y,
                    new_position_z: self.new_position.0.z,
                    new_yaw: self.new_yaw,
                    new_pitch: self.new_pitch,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::structure_grow::StructureGrowEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::StructureGrow(StructureGrowEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    species: format!("{:?}", self.species),
                    bone_meal: self.bone_meal,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::time_skip::TimeSkipEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::TimeSkip(TimeSkipEvent {
                    skip_amount: self.skip_amount,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::weather_change::WeatherChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::WeatherChange(WeatherChangeEvent {
                    world_uuid: None,
                    to_weather_state: self.to_weather_state,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::weather_change::ThunderChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ThunderChange(ThunderChangeEvent {
                    world_uuid: None,
                    to_thunder_state: self.to_thunder_state,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::world_init::WorldInitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::WorldInit(WorldInitEvent { world_uuid: None })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::world_load::WorldLoadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::WorldLoad(WorldLoadEvent { world_uuid: None })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::world_load::WorldUnloadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::WorldUnload(WorldUnloadEvent { world_uuid: None })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::world::world_save::WorldSaveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::WorldSave(WorldSaveEvent {
                    world_name: self.world_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::list_ping::ServerListPingEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerListPing(ServerListPingEvent {
                    motd: serde_json::to_string(&self.motd).unwrap_or_default(),
                    max_players: self.max_players,
                    num_players: self.num_players,
                    favicon: self.favicon.clone().unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::map_initialize::MapInitializeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::MapInitialize(MapInitializeEvent {
                    map_id: self.map_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::packet::PacketReceivedEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PacketReceived(PacketReceivedEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    packet_id: self.packet_id,
                    payload: self.payload.to_vec(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::packet::PacketSentEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PacketSent(PacketSentEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    packet_id: self.packet_id,
                    payload: self.payload.to_vec(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::plugin_disable::PluginDisableEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PluginDisable(PluginDisableEvent {
                    plugin_name: self.plugin_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::plugin_enable::PluginEnableEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PluginEnable(PluginEnableEvent {
                    plugin_name: self.plugin_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::remote_server_command::RemoteServerCommandEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::RemoteServerCommand(RemoteServerCommandEvent {
                    command: self.command.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerBroadcast(ServerBroadcastEvent {
                    message: serde_json::to_string(&self.message).unwrap_or_default(),
                    sender: serde_json::to_string(&self.sender).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
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

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::server_load::ServerLoadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerLoad(ServerLoadEvent {
                    load_type: format!("{:?}", self.load_type),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::server_tick_end::ServerTickEndEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerTickEnd(ServerTickEndEvent {
                    tick: self.tick,
                    duration_nanos: self.duration_nanos,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::server_tick_start::ServerTickStartEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerTickStart(ServerTickStartEvent {
                    tick: self.tick,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::service_register::ServiceRegisterEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServiceRegister(ServiceRegisterEvent {
                    service_name: self.service_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::service_unregister::ServiceUnregisterEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServiceUnregister(ServiceUnregisterEvent {
                    service_name: self.service_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::server::tab_complete::TabCompleteEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::TabComplete(TabCompleteEvent {
                    buffer: self.buffer.clone(),
                    completions: self.completions.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::vehicle::vehicle_block_collision::VehicleBlockCollisionEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleBlockCollision(VehicleBlockCollisionEvent {
                    vehicle_id: self.vehicle_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_collision::VehicleCollisionEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleCollision(VehicleCollisionEvent {
                    vehicle_id: self.vehicle_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_create::VehicleCreateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleCreate(VehicleCreateEvent {
                    vehicle_id: self.vehicle_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_damage::VehicleDamageEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleDamage(VehicleDamageEvent {
                    vehicle_id: self.vehicle_id,
                    damage: self.damage,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_destroy::VehicleDestroyEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleDestroy(VehicleDestroyEvent {
                    vehicle_id: self.vehicle_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_enter::VehicleEnterEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleEnter(VehicleEnterEvent {
                    vehicle_id: self.vehicle_id,
                    entered_id: self.entered_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::vehicle::vehicle_entity_collision::VehicleEntityCollisionEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleEntityCollision(VehicleEntityCollisionEvent {
                    vehicle_id: self.vehicle_id,
                    collided_entity_id: self.collided_entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_exit::VehicleExitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleExit(VehicleExitEvent {
                    vehicle_id: self.vehicle_id,
                    exited_id: self.exited_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_move::VehicleMoveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleMove(VehicleMoveEvent {
                    vehicle_id: self.vehicle_id,
                    from_x: self.from.x,
                    from_y: self.from.y,
                    from_z: self.from.z,
                    to_x: self.to.x,
                    to_y: self.to.y,
                    to_z: self.to.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::vehicle::vehicle_update::VehicleUpdateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VehicleUpdate(VehicleUpdateEvent {
                    vehicle_id: self.vehicle_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::brew::BrewEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::Brew(BrewEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    fuel_level: u32::from(self.fuel_level),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::brewing_stand_fuel::BrewingStandFuelEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BrewingStandFuel(BrewingStandFuelEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    fuel_power: u32::from(self.fuel_power),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::craft_item::CraftItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::CraftItem(CraftItemEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    recipe_id: self.recipe_id.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::furnace_burn::FurnaceBurnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FurnaceBurn(FurnaceBurnEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    fuel_item: self.fuel_item.clone(),
                    burn_time: self.burn_time,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::furnace_extract::FurnaceExtractEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FurnaceExtract(FurnaceExtractEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    item_id: self.item_id.clone(),
                    item_amount: self.item_amount,
                    exp_gained: self.exp_gained,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::furnace_smelt::FurnaceSmeltEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FurnaceSmelt(FurnaceSmeltEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    source_item: self.source_item.clone(),
                    result_item: self.result_item.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::furnace_start_smelt::FurnaceStartSmeltEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FurnaceStartSmelt(FurnaceStartSmeltEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    source_item: self.source_item.clone(),
                    cooking_time: self.cooking_time,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::inventory::hopper_inventory_search::HopperInventorySearchEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::HopperInventorySearch(HopperInventorySearchEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    search_pos_x: self.search_pos.0.x,
                    search_pos_y: self.search_pos.0.y,
                    search_pos_z: self.search_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::inventory_creative::InventoryCreativeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryCreative(InventoryCreativeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    slot: i32::from(self.slot),
                    item_id: self.item_id.clone(),
                    item_count: u32::from(self.item_count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::inventory_drag::InventoryDragEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryDrag(InventoryDragEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::inventory_interact::InventoryInteractEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryInteract(InventoryInteractEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::inventory_move_item::InventoryMoveItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryMoveItem(InventoryMoveItemEvent {
                    source_pos_x: self.source_pos.0.x,
                    source_pos_y: self.source_pos.0.y,
                    source_pos_z: self.source_pos.0.z,
                    target_pos_x: self.target_pos.0.x,
                    target_pos_y: self.target_pos.0.y,
                    target_pos_z: self.target_pos.0.z,
                    item_id: self.item_id.clone(),
                    item_amount: self.item_amount,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::inventory_open::InventoryOpenEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryOpen(InventoryOpenEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::inventory::inventory_pickup_item::InventoryPickupItemEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryPickupItem(InventoryPickupItemEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    item_entity_id: self.item_entity_id,
                    item_id: self.item_id.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::prepare_anvil::PrepareAnvilEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PrepareAnvil(PrepareAnvilEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    rename_text: self.rename_text.clone(),
                    repair_cost: self.repair_cost,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::prepare_grindstone::PrepareGrindstoneEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PrepareGrindstone(PrepareGrindstoneEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    result_item: self.result_item.clone().unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::inventory::prepare_inventory_result::PrepareInventoryResultEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PrepareInventoryResult(PrepareInventoryResultEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    result_item: self.result_item.clone().unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::prepare_item_craft::PrepareItemCraftEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PrepareItemCraft(PrepareItemCraftEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    recipe_id: self.recipe_id.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::prepare_smithing::PrepareSmithingEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PrepareSmithing(PrepareSmithingEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    result_item: self.result_item.clone().unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::smith_item::SmithItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SmithItem(SmithItemEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    recipe_id: self.recipe_id.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::inventory::trade_select::TradeSelectEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::TradeSelect(TradeSelectEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    slot_index: u32::from(self.slot_index),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::bell_resonate::BellResonateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BellResonate(BellResonateEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::bell_ring::BellRingEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BellRing(BellRingEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    entity_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_break::BlockBreakEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockBreak(BlockBreakEvent {
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                    block: self.block.name.to_string(),
                    block_x: self.block_position.0.x,
                    block_y: self.block_position.0.y,
                    block_z: self.block_position.0.z,
                    exp: self.exp,
                    drop: self.drop,
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_brush::BlockBrushEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockBrush(BlockBrushEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_burn::BlockBurnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockBurn(BlockBurnEvent {
                    igniting_block: self.igniting_block.name.to_string(),
                    block: self.block.name.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_can_build::BlockCanBuildEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockCanBuild(BlockCanBuildEvent {
                    block_to_build: self.block_to_build.name.to_string(),
                    buildable: self.buildable,
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block: self.block.name.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_cook::BlockCookEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockCook(BlockCookEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    source_name: self.source.item.registry_key.to_string(),
                    source_count: i32::from(self.source.item_count),
                    result_name: self.result.item.registry_key.to_string(),
                    result_count: i32::from(self.result.item_count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_damage::BlockDamageEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDamage(BlockDamageEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block: self.block.name.to_string(),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    insta_break: self.insta_break,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_damage_abort::BlockDamageAbortEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDamageAbort(BlockDamageAbortEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    item_in_hand_name: self.item_in_hand.item.registry_key.to_string(),
                    item_in_hand_count: i32::from(self.item_in_hand.item_count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_dispense::BlockDispenseEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDispense(BlockDispenseEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    item_name: self.item_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_dispense_armor::BlockDispenseArmorEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDispenseArmor(BlockDispenseArmorEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    target_uuid: None,
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_dispense_loot::BlockDispenseLootEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDispenseLoot(BlockDispenseLootEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    items_names: self
                        .items
                        .iter()
                        .map(|i| i.item.registry_key.to_string())
                        .collect(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_drop_item::BlockDropItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDropItem(BlockDropItemEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                    items_names: self
                        .items
                        .iter()
                        .map(|i| i.item.registry_key.to_string())
                        .collect(),
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_exp::BlockExpEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockExp(BlockExpEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    exp: self.exp,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_explode::BlockExplodeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockExplode(BlockExplodeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    yield_rate: self.yield_rate,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_fade::BlockFadeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockFade(BlockFadeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    block: self.block.name.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_fertilize::BlockFertilizeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockFertilize(BlockFertilizeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_form::BlockFormEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockForm(BlockFormEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    block: self.block.name.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_from_to::BlockFromToEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockFromTo(BlockFromToEvent {
                    from_pos_x: self.from_pos.0.x,
                    from_pos_y: self.from_pos.0.y,
                    from_pos_z: self.from_pos.0.z,
                    to_pos_x: self.to_pos.0.x,
                    to_pos_y: self.to_pos.0.y,
                    to_pos_z: self.to_pos.0.z,
                    block: self.block.name.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_grow::BlockGrowEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockGrow(BlockGrowEvent {
                    world_uuid: None,
                    old_block: self.old_block.name.to_string(),
                    old_state_id: i32::from(self.old_state_id.as_u16()),
                    new_block: self.new_block.name.to_string(),
                    new_state_id: i32::from(self.new_state_id.as_u16()),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_ignite::BlockIgniteEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockIgnite(BlockIgniteEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    igniting_block: self.igniting_block.name.to_string(),
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_multi_place::BlockMultiPlaceEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockMultiPlace(BlockMultiPlaceEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    world_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_physics::BlockPhysicsEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockPhysics(BlockPhysicsEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    changed_pos_x: self.changed_pos.0.x,
                    changed_pos_y: self.changed_pos.0.y,
                    changed_pos_z: self.changed_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_piston::BlockPistonExtendEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockPistonExtend(BlockPistonExtendEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    direction: self.direction.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_piston::BlockPistonRetractEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockPistonRetract(BlockPistonRetractEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    direction: self.direction.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_place::BlockPlaceEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockPlace(BlockPlaceEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_placed: self.block_placed.name.to_string(),
                    block_placed_against: self.block_placed_against.name.to_string(),
                    block_x: self.block_position.0.x,
                    block_y: self.block_position.0.y,
                    block_z: self.block_position.0.z,
                    can_build: self.can_build,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_receive_game::BlockReceiveGameEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockReceiveGame(BlockReceiveGameEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    game_event: self.game_event.clone(),
                    source_entity_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_redstone::BlockRedstoneEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockRedstone(BlockRedstoneEvent {
                    world_uuid: None,
                    block_state_id: i32::from(self.block_state_id.as_u16()),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    old_current: self.old_current,
                    new_current: self.new_current,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_shear_entity::BlockShearEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockShearEntity(BlockShearEntityEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    target_uuid: None,
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_spread::BlockSpreadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockSpread(BlockSpreadEvent {
                    source_pos_x: self.source_pos.0.x,
                    source_pos_y: self.source_pos.0.y,
                    source_pos_z: self.source_pos.0.z,
                    target_pos_x: self.target_pos.0.x,
                    target_pos_y: self.target_pos.0.y,
                    target_pos_z: self.target_pos.0.z,
                    world_uuid: None,
                    new_state_id: i32::from(self.new_state_id.as_u16()),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::brewing_start::BrewingStartEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BrewingStart(BrewingStartEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    brewing_time: self.brewing_time,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::campfire_start::CampfireStartEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::CampfireStart(CampfireStartEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                    slot: u32::from(self.slot),
                    cooking_time: self.cooking_time,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::cauldron_level_change::CauldronLevelChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::CauldronLevelChange(CauldronLevelChangeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    old_level: self.old_level,
                    new_level: self.new_level,
                    reason: format!("{:?}", self.reason),
                    entity_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::crafter_craft::CrafterCraftEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::CrafterCraft(CrafterCraftEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    result_name: self.result.item.registry_key.to_string(),
                    result_count: i32::from(self.result.item_count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::entity_block_form::EntityBlockFormEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityBlockForm(EntityBlockFormEvent {
                    entity_uuid: None,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    new_state_id: i32::from(self.new_state_id.as_u16()),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::fluid_level_change::FluidLevelChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FluidLevelChange(FluidLevelChangeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    new_state_id: i32::from(self.new_state_id.as_u16()),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::inventory_block_start::InventoryBlockStartEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryBlockStart(InventoryBlockStartEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::leaves_decay::LeavesDecayEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::LeavesDecay(LeavesDecayEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::moisture_change::MoistureChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::MoistureChange(MoistureChangeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    new_moisture: self.new_moisture,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::note_play::NotePlayEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::NotePlay(NotePlayEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    instrument: self.instrument.clone(),
                    note: u32::from(self.note),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::sculk_bloom::SculkBloomEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SculkBloom(SculkBloomEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    charge: self.charge,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::sign_change::SignChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SignChange(SignChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    lines: self.lines.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::sponge_absorb::SpongeAbsorbEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SpongeAbsorb(SpongeAbsorbEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::tnt_prime::TNTPrimeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::TntPrime(TntPrimeEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    prime_reason: self.prime_reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::vault_display_item::VaultDisplayItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VaultDisplayItem(VaultDisplayItemEvent {
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    world_uuid: None,
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::dialog::dialog_clear::DialogClearEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::DialogClear(DialogClearEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::dialog::dialog_click_action::DialogClickActionEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::DialogClickAction(DialogClickActionEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    id: self.id.clone(),
                    payload: self
                        .payload
                        .as_ref()
                        .map(|b| b.to_vec())
                        .unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::dialog::dialog_show::DialogShowEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::DialogShow(DialogShowEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::async_player_chat::AsyncPlayerChatEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::AsyncPlayerChat(AsyncPlayerChatEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    message: self.message.clone(),
                    format: serde_json::to_string(&self.format).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::async_player_pre_login::AsyncPlayerPreLoginEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::AsyncPlayerPreLogin(AsyncPlayerPreLoginEvent {
                    player_name: self.player_name.clone(),
                    player_uuid: Some(Uuid {
                        value: self.player_uuid.to_string(),
                    }),
                    ip_address: self.ip_address.to_string(),
                    kick_message: serde_json::to_string(&self.kick_message).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::bedrock_form_response::BedrockFormResponseEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BedrockFormResponse(BedrockFormResponseEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    form_id: self.form_id,
                    response_data: self.response_data.clone().unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::changed_main_hand::PlayerChangedMainHandEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChangedMainHand(PlayerChangedMainHandEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    main_hand: format!("{:?}", self.main_hand),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::egg_throw::PlayerEggThrowEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerEggThrow(PlayerEggThrowEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    egg_uuid: Some(Uuid {
                        value: self.egg_uuid.to_string(),
                    }),
                    hatching: self.hatching,
                    num_hatches: u32::from(self.num_hatches),
                    hatching_type: self.hatching_type.id.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::exp_change::PlayerExpChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerExpChange(PlayerExpChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    amount: self.amount,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::fish::PlayerFishEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerFish(PlayerFishEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    caught_uuid: self.caught_uuid.map(|u| Uuid {
                        value: u.to_string(),
                    }),
                    caught_type: self.caught_type.clone(),
                    hook_uuid: Some(Uuid {
                        value: self.hook_uuid.to_string(),
                    }),
                    state: match self.state {
                        pumpkin::plugin::player::PlayerFishState::Fishing => "FISHING",
                        pumpkin::plugin::player::PlayerFishState::CaughtFish => "CAUGHT_FISH",
                        pumpkin::plugin::player::PlayerFishState::CaughtEntity => "CAUGHT_ENTITY",
                        pumpkin::plugin::player::PlayerFishState::InGround => "IN_GROUND",
                        pumpkin::plugin::player::PlayerFishState::FailedAttempt => "FAILED_ATTEMPT",
                        pumpkin::plugin::player::PlayerFishState::ReelIn => "REEL_IN",
                        pumpkin::plugin::player::PlayerFishState::Bite => "BITE",
                    }
                    .to_string(),
                    hand: format!("{:?}", self.hand),
                    exp_to_drop: self.exp_to_drop,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::inventory_close::InventoryCloseEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryClose(InventoryCloseEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::inventory_interact::InventoryClickEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::InventoryClick(InventoryClickEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    click_type: format!("{:?}", self.click_type),
                    slot: i32::from(self.slot),
                    raw_slot: i32::from(self.raw_slot),
                    clicked_item_name: self
                        .clicked_item
                        .as_ref()
                        .map(|i| i.item.registry_key.to_string())
                        .unwrap_or_default(),
                    clicked_item_count: self
                        .clicked_item
                        .as_ref()
                        .map(|i| i32::from(i.item_count))
                        .unwrap_or(0),
                    cursor_name: self
                        .cursor
                        .as_ref()
                        .map(|i| i.item.registry_key.to_string())
                        .unwrap_or_default(),
                    cursor_count: self
                        .cursor
                        .as_ref()
                        .map(|i| i32::from(i.item_count))
                        .unwrap_or(0),
                    hotbar_button: self.hotbar_button,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::item_held::PlayerItemHeldEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemHeld(PlayerItemHeldEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_slot: u32::from(self.previous_slot),
                    new_slot: u32::from(self.new_slot),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerAdvancementDone(PlayerAdvancementDoneEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    advancement_id: self.advancement_id.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_animation::PlayerAnimationEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerAnimation(PlayerAnimationEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    animation_type: format!("{:?}", self.animation_type),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerArmorStandManipulate(
                    PlayerArmorStandManipulateEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        armor_stand_id: self.armor_stand_id,
                        slot: u32::from(self.slot),
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bed::PlayerBedEnterEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBedEnter(PlayerBedEnterEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    bed_pos_x: self.bed_pos.0.x,
                    bed_pos_y: self.bed_pos.0.y,
                    bed_pos_z: self.bed_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bed::PlayerBedLeaveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBedLeave(PlayerBedLeaveEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    bed_pos_x: self.bed_pos.0.x,
                    bed_pos_y: self.bed_pos.0.y,
                    bed_pos_z: self.bed_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bucket::PlayerBucketEmptyEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBucketEmpty(PlayerBucketEmptyEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    bucket: self.bucket.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bucket::PlayerBucketFillEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBucketFill(PlayerBucketFillEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    bucket: self.bucket.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBucketEntity(PlayerBucketEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                    bucket_item: self.bucket_item.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChangeWorld(PlayerChangeWorldEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_world_uuid: None,
                    new_world_uuid: None,
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    yaw: self.yaw,
                    pitch: self.pitch,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_changed_world::PlayerChangedWorldEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChangedWorld(PlayerChangedWorldEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from_world_uuid: None,
                    to_world_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_channel::PlayerChannelEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChannel(PlayerChannelEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    channel: self.channel.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_chat::PlayerChatEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChat(PlayerChatEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    message: self.message.clone(),
                    recipients_uuids: self
                        .recipients
                        .iter()
                        .map(|p| Uuid {
                            value: p.gameprofile.id.to_string(),
                        })
                        .collect(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerCommandPreprocess(
                    PlayerCommandPreprocessEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        command: self.command.clone(),
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerCommandSend(PlayerCommandSendEvent {
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

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_custom_payload::PlayerCustomPayloadEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerCustomPayload(PlayerCustomPayloadEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    channel: self.channel.clone(),
                    data: self.data.to_vec(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerDropItem(PlayerDropItemEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item_name.clone(),
                    count: u32::from(self.count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerEditBook(PlayerEditBookEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    slot: self.slot,
                    pages: self.pages.clone(),
                    title: self.title.clone().unwrap_or_default(),
                    signing: self.signing,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_elytra_boost::PlayerElytraBoostEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerElytraBoost(PlayerElytraBoostEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    firework_id: self.firework_id,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_exp_cooldown_change::PlayerExpCooldownChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerExpCooldownChange(
                    PlayerExpCooldownChangeEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        new_cooldown: self.new_cooldown,
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerGamemodeChange(PlayerGamemodeChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_gamemode: format!("{:?}", self.previous_gamemode),
                    new_gamemode: format!("{:?}", self.new_gamemode),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_harvest_block::PlayerHarvestBlockEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerHarvestBlock(PlayerHarvestBlockEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    harvested_items_names: self
                        .harvested_items
                        .iter()
                        .map(|i| i.item.registry_key.to_string())
                        .collect(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_hide_entity::PlayerHideEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerHideEntity(PlayerHideEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_input::PlayerInputEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInput(PlayerInputEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    input: self.input.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_interact_at_entity::PlayerInteractAtEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteractAtEntity(PlayerInteractAtEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                    clicked_x: self.clicked_x,
                    clicked_y: self.clicked_y,
                    clicked_z: self.clicked_z,
                    hand: u32::from(self.hand),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_interact_entity_event::PlayerInteractEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteractEntity(PlayerInteractEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    target_uuid: None,
                    action: format!("{:?}", self.action),
                    target_position_x: self.target_position.map(|p| p.x).unwrap_or(0.0),
                    target_position_y: self.target_position.map(|p| p.y).unwrap_or(0.0),
                    target_position_z: self.target_position.map(|p| p.z).unwrap_or(0.0),
                    sneaking: self.sneaking,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_interact_event::PlayerInteractEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteract(PlayerInteractEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    action: match self.action {
                        pumpkin::plugin::player::InteractAction::LeftClickBlock => {
                            "LEFT_CLICK_BLOCK"
                        }
                        pumpkin::plugin::player::InteractAction::LeftClickAir => "LEFT_CLICK_AIR",
                        pumpkin::plugin::player::InteractAction::RightClickAir => "RIGHT_CLICK_AIR",
                        pumpkin::plugin::player::InteractAction::RightClickBlock => {
                            "RIGHT_CLICK_BLOCK"
                        }
                    }
                    .to_string(),
                    clicked_pos_x: self.clicked_pos.map(|p| p.0.x).unwrap_or(0),
                    clicked_pos_y: self.clicked_pos.map(|p| p.0.y).unwrap_or(0),
                    clicked_pos_z: self.clicked_pos.map(|p| p.0.z).unwrap_or(0),
                    block: self.block.name.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_interact_unknown_entity_event::PlayerInteractUnknownEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteractUnknownEntity(PlayerInteractUnknownEntityEvent {
                    player_uuid: Some(Uuid { value: self.player.gameprofile.id.to_string() }),
                    entity_id: self.entity_id,
                    action: format!("{:?}", self.action),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_item_break::PlayerItemBreakEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemBreak(PlayerItemBreakEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_item_consume::PlayerItemConsumeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemConsume(PlayerItemConsumeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_item_damage::PlayerItemDamageEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemDamage(PlayerItemDamageEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item_name.clone(),
                    damage: self.damage,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_item_mend::PlayerItemMendEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemMend(PlayerItemMendEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item_name.clone(),
                    repair_amount: self.repair_amount,
                    exp_consumed: self.exp_consumed,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_join::PlayerJoinEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerJoin(PlayerJoinEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    join_message: serde_json::to_string(&self.join_message).unwrap_or_default(),
                    player_name: self.player.gameprofile.name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_kick::PlayerKickEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerKick(PlayerKickEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    reason: self.reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_leash_entity::PlayerLeashEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLeashEntity(PlayerLeashEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                    holder_id: self.holder_id,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
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
                    leave_message: serde_json::to_string(&self.leave_message).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_level_change::PlayerLevelChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLevelChange(PlayerLevelChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    old_level: self.old_level,
                    new_level: self.new_level,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_links_send::PlayerLinksSendEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLinksSend(PlayerLinksSendEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    links: self.links.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_locale_change::PlayerLocaleChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLocaleChange(PlayerLocaleChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    new_locale: self.new_locale.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_login::PlayerLoginEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLogin(PlayerLoginEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    kick_message: serde_json::to_string(&self.kick_message).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_move::PlayerMoveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerMove(PlayerMoveEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from_x: self.from.x,
                    from_y: self.from.y,
                    from_z: self.from.z,
                    to_x: self.to.x,
                    to_y: self.to.y,
                    to_z: self.to.z,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_name_entity::PlayerNameEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerNameEntity(PlayerNameEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                    name: serde_json::to_string(&self.name).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_open_sign::PlayerOpenSignEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerOpenSign(PlayerOpenSignEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    is_front: self.is_front,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_permission_check::PlayerPermissionCheckEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerPermissionCheck(PlayerPermissionCheckEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    permission: self.permission.clone(),
                    result: self.result,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_pickup_arrow::PlayerPickupArrowEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerPickupArrow(PlayerPickupArrowEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    arrow_id: self.arrow_id,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_portal::PlayerPortalEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerPortal(PlayerPortalEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from_pos_x: self.from_pos.0.x,
                    from_pos_y: self.from_pos.0.y,
                    from_pos_z: self.from_pos.0.z,
                    to_pos_x: self.to_pos.map(|p| p.0.x).unwrap_or(0),
                    to_pos_y: self.to_pos.map(|p| p.0.y).unwrap_or(0),
                    to_pos_z: self.to_pos.map(|p| p.0.z).unwrap_or(0),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerPreLogin(PlayerPreLoginEvent {
                    player_name: self.player_name.clone(),
                    player_uuid: Some(Uuid {
                        value: self.player_uuid.to_string(),
                    }),
                    ip_address: self.ip_address.to_string(),
                    kick_message: serde_json::to_string(&self.kick_message).unwrap_or_default(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_recipe_book_click::PlayerRecipeBookClickEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRecipeBookClick(PlayerRecipeBookClickEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    recipe_id: self.recipe_id.clone(),
                    make_all: self.make_all,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_recipe_book_settings_change::PlayerRecipeBookSettingsChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRecipeBookSettingsChange(PlayerRecipeBookSettingsChangeEvent {
                    player_uuid: Some(Uuid { value: self.player.gameprofile.id.to_string() }),
                    book_type: self.book_type.clone(),
                    is_open: self.is_open,
                    is_filtering: self.is_filtering,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_recipe_discover::PlayerRecipeDiscoverEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRecipeDiscover(PlayerRecipeDiscoverEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    recipe_id: self.recipe_id.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRegisterChannel(PlayerRegisterChannelEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    channel: self.channel.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_resource_pack_status::PlayerResourcePackStatusEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerResourcePackStatus(
                    PlayerResourcePackStatusEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        pack_id: self.pack_id.clone(),
                        status: self.status.clone(),
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_respawn::PlayerRespawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRespawn(PlayerRespawnEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_world_uuid: None,
                    respawned_world_uuid: None,
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    yaw: self.yaw,
                    pitch: self.pitch,
                    alive: self.alive,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_riptide::PlayerRiptideEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRiptide(PlayerRiptideEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_shear_entity::PlayerShearEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerShearEntity(PlayerShearEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                    hand: u32::from(self.hand),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_show_entity::PlayerShowEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerShowEntity(PlayerShowEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_spawn_change::PlayerSpawnChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerSpawnChange(PlayerSpawnChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    new_spawn_x: self.new_spawn.map(|p| p.0.x).unwrap_or(0),
                    new_spawn_y: self.new_spawn.map(|p| p.0.y).unwrap_or(0),
                    new_spawn_z: self.new_spawn.map(|p| p.0.z).unwrap_or(0),
                    forced: self.forced,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_spawn_location::PlayerSpawnLocationEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerSpawnLocation(PlayerSpawnLocationEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    spawn_pos_x: self.spawn_pos.x,
                    spawn_pos_y: self.spawn_pos.y,
                    spawn_pos_z: self.spawn_pos.z,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_statistic_increment::PlayerStatisticIncrementEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerStatisticIncrement(
                    PlayerStatisticIncrementEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        statistic_id: self.statistic_id.clone(),
                        amount: self.amount,
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_swap_hands::PlayerSwapHandItemsEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerSwapHandItems(PlayerSwapHandItemsEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_take_lectern_book::PlayerTakeLecternBookEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerTakeLecternBook(PlayerTakeLecternBookEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    book_name: self.book.item.registry_key.to_string(),
                    book_count: i32::from(self.book.item_count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_teleport::PlayerTeleportEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerTeleport(PlayerTeleportEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from_x: self.from.x,
                    from_y: self.from.y,
                    from_z: self.from.z,
                    to_x: self.to.x,
                    to_y: self.to.y,
                    to_z: self.to.z,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_toggle_flight_event::PlayerToggleFlightEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerToggleFlight(PlayerToggleFlightEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    is_flying: self.is_flying,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_toggle_sneak_event::PlayerToggleSneakEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerToggleSneak(PlayerToggleSneakEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    is_sneaking: self.is_sneaking,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_toggle_sprint_event::PlayerToggleSprintEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerToggleSprint(PlayerToggleSprintEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    is_sprinting: self.is_sprinting,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_unleash_entity::PlayerUnleashEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerUnleashEntity(PlayerUnleashEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerUnregisterChannel(
                    PlayerUnregisterChannelEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        channel: self.channel.clone(),
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_velocity::PlayerVelocityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerVelocity(PlayerVelocityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    velocity_x: self.velocity.x,
                    velocity_y: self.velocity.y,
                    velocity_z: self.velocity.z,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::hanging::hanging_break::HangingBreakEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::HangingBreak(HangingBreakEvent {
                    entity_uuid: None,
                    remover_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::hanging::hanging_break_by_entity::HangingBreakByEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::HangingBreakByEntity(HangingBreakByEntityEvent {
                    entity_uuid: None,
                    remover_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::hanging::hanging_place::HangingPlaceEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::HangingPlace(HangingPlaceEvent {
                    entity_uuid: None,
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    block_face: format!("{:?}", self.block_face),
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::area_effect_cloud_apply::AreaEffectCloudApplyEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::AreaEffectCloudApply(AreaEffectCloudApplyEvent {
                    entity_id: self.entity_id,
                    affected_entities: self.affected_entities.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::arrow_body_count_change::ArrowBodyCountChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ArrowBodyCountChange(ArrowBodyCountChangeEvent {
                    entity_id: self.entity_id,
                    old_amount: self.old_amount,
                    new_amount: self.new_amount,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::bat_toggle_sleep::BatToggleSleepEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BatToggleSleep(BatToggleSleepEvent {
                    entity_id: self.entity_id,
                    is_awake: self.is_awake,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::creature_spawn::CreatureSpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::CreatureSpawn(CreatureSpawnEvent {
                    entity_id: self.entity_id,
                    entity_type: self.entity_type.clone(),
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    world_uuid: None,
                    spawn_reason: self.spawn_reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::creeper_power::CreeperPowerEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::CreeperPower(CreeperPowerEvent {
                    entity_id: self.entity_id,
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::ender_dragon_change_phase::EnderDragonChangePhaseEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EnderDragonChangePhase(EnderDragonChangePhaseEvent {
                    entity_id: self.entity_id,
                    current_phase: self.current_phase.clone(),
                    new_phase: self.new_phase.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_air_change::EntityAirChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityAirChange(EntityAirChangeEvent {
                    entity_id: self.entity_id,
                    amount: self.amount,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_break_door::EntityBreakDoorEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityBreakDoor(EntityBreakDoorEvent {
                    entity_id: self.entity_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_breed::EntityBreedEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityBreed(EntityBreedEvent {
                    father_id: self.father_id,
                    mother_id: self.mother_id,
                    child_id: self.child_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_change_block::EntityChangeBlockEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityChangeBlock(EntityChangeBlockEvent {
                    entity_id: self.entity_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    new_block: self.new_block.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_combust::EntityCombustEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityCombust(EntityCombustEvent {
                    entity_id: self.entity_id,
                    duration_secs: self.duration_secs,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_combust_by_block::EntityCombustByBlockEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityCombustByBlock(EntityCombustByBlockEvent {
                    entity_id: self.entity_id,
                    combuster_x: self.combuster.0.x,
                    combuster_y: self.combuster.0.y,
                    combuster_z: self.combuster.0.z,
                    duration: self.duration,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_combust_by_entity::EntityCombustByEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityCombustByEntity(EntityCombustByEntityEvent {
                    entity_id: self.entity_id,
                    combuster_id: self.combuster_id,
                    duration: self.duration,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_damage::EntityDamageEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDamage(EntityDamageEvent {
                    entity_id: self.entity_id,
                    damage: self.damage,
                    damage_type: self.damage_type.message_id.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_damage_by_block::EntityDamageByBlockEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDamageByBlock(EntityDamageByBlockEvent {
                    entity_id: self.entity_id,
                    damager_pos_x: self.damager_pos.map(|p| p.0.x).unwrap_or(0),
                    damager_pos_y: self.damager_pos.map(|p| p.0.y).unwrap_or(0),
                    damager_pos_z: self.damager_pos.map(|p| p.0.z).unwrap_or(0),
                    damage: self.damage,
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_damage_by_entity::EntityDamageByEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDamageByEntity(EntityDamageByEntityEvent {
                    entity_id: self.entity_id,
                    damager_id: self.damager_id,
                    damage: self.damage,
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_death::EntityDeathEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDeath(EntityDeathEvent {
                    entity_id: self.entity_id,
                    dropped_exp: self.dropped_exp,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_death::PlayerDeathEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerDeath(PlayerDeathEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    death_message: serde_json::to_string(&self.death_message).unwrap_or_default(),
                    dropped_exp: self.dropped_exp,
                    keep_inventory: self.keep_inventory,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_dismount::EntityDismountEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDismount(EntityDismountEvent {
                    entity_id: self.entity_id,
                    dismounted_id: self.dismounted_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_drop_item::EntityDropItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDropItem(EntityDropItemEvent {
                    entity_id: self.entity_id,
                    item_name: self.item_name.clone(),
                    count: u32::from(self.count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_dye::EntityDyeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDye(EntityDyeEvent {
                    entity_id: self.entity_id,
                    color: format!("{:?}", self.color),
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_enter_block::EntityEnterBlockEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityEnterBlock(EntityEnterBlockEvent {
                    entity_id: self.entity_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_enter_love_mode::EntityEnterLoveModeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityEnterLoveMode(EntityEnterLoveModeEvent {
                    entity_id: self.entity_id,
                    ticks_in_love: self.ticks_in_love,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_exhaustion::EntityExhaustionEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityExhaustion(EntityExhaustionEvent {
                    entity_id: self.entity_id,
                    exhaustion: self.exhaustion,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_explode::EntityExplodeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityExplode(EntityExplodeEvent {
                    entity_id: self.entity_id,
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    yield_rate: self.yield_rate,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_interact::EntityInteractEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityInteract(EntityInteractEvent {
                    entity_id: self.entity_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_knockback::EntityKnockbackEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityKnockback(EntityKnockbackEvent {
                    entity_id: self.entity_id,
                    knockback_x: self.knockback.x,
                    knockback_y: self.knockback.y,
                    knockback_z: self.knockback.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_knockback_by_entity::EntityKnockbackByEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityKnockbackByEntity(
                    EntityKnockbackByEntityEvent {
                        entity_id: self.entity_id,
                        hit_by_id: self.hit_by_id,
                        force: self.force,
                        x: self.x,
                        z: self.z,
                    },
                )),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_mount::EntityMountEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityMount(EntityMountEvent {
                    entity_id: self.entity_id,
                    mount_id: self.mount_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_pickup_item::EntityPickupItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPickupItem(EntityPickupItemEvent {
                    entity_id: self.entity_id,
                    item_name: self.item_name.clone(),
                    count: u32::from(self.count),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_place::EntityPlaceEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPlace(EntityPlaceEvent {
                    entity_id: self.entity_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                    block_name: self.block_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_portal::EntityPortalEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPortal(EntityPortalEvent {
                    entity_id: self.entity_id,
                    portal_pos_x: self.portal_pos.0.x,
                    portal_pos_y: self.portal_pos.0.y,
                    portal_pos_z: self.portal_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_portal_enter::EntityPortalEnterEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPortalEnter(EntityPortalEnterEvent {
                    entity_id: self.entity_id,
                    location_x: self.location.0.x,
                    location_y: self.location.0.y,
                    location_z: self.location.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_portal_exit::EntityPortalExitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPortalExit(EntityPortalExitEvent {
                    entity_id: self.entity_id,
                    from_pos_x: self.from_pos.0.x,
                    from_pos_y: self.from_pos.0.y,
                    from_pos_z: self.from_pos.0.z,
                    to_pos_x: self.to_pos.map(|p| p.0.x).unwrap_or(0),
                    to_pos_y: self.to_pos.map(|p| p.0.y).unwrap_or(0),
                    to_pos_z: self.to_pos.map(|p| p.0.z).unwrap_or(0),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_pose_change::EntityPoseChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPoseChange(EntityPoseChangeEvent {
                    entity_id: self.entity_id,
                    pose: self.pose.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_potion_effect::EntityPotionEffectEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityPotionEffect(EntityPotionEffectEvent {
                    entity_id: self.entity_id,
                    effect_name: self.effect_name.clone(),
                    duration: self.duration,
                    amplifier: u32::from(self.amplifier),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_regain_health::EntityRegainHealthEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityRegainHealth(EntityRegainHealthEvent {
                    entity_id: self.entity_id,
                    amount: self.amount,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_remove::EntityRemoveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityRemove(EntityRemoveEvent {
                    entity_id: self.entity_id,
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_resurrect::EntityResurrectEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityResurrect(EntityResurrectEvent {
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_shoot_bow::EntityShootBowEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityShootBow(EntityShootBowEvent {
                    entity_id: self.entity_id,
                    weapon_name: self.weapon_name.clone(),
                    force: self.force,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntitySpawn(EntitySpawnEvent {
                    entity_id: self.entity_id,
                    entity_type: self.entity_type.clone(),
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    world_uuid: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_spell_cast::EntitySpellCastEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntitySpellCast(EntitySpellCastEvent {
                    entity_id: self.entity_id,
                    spell: self.spell.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_tame::EntityTameEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityTame(EntityTameEvent {
                    entity_id: self.entity_id,
                    owner_uuid: Some(Uuid {
                        value: self.owner.gameprofile.id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.owner.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_target::EntityTargetEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityTarget(EntityTargetEvent {
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_target_block::EntityTargetBlockEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityTargetBlock(EntityTargetBlockEvent {
                    entity_id: self.entity_id,
                    block_x: self.block_pos.0.x,
                    block_y: self.block_pos.0.y,
                    block_z: self.block_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::entity_target_living_entity::EntityTargetLivingEntityEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityTargetLivingEntity(
                    EntityTargetLivingEntityEvent {
                        entity_id: self.entity_id,
                        reason: self.reason.clone(),
                    },
                )),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_teleport::EntityTeleportEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityTeleport(EntityTeleportEvent {
                    entity_id: self.entity_id,
                    from_position_x: self.from_position.x,
                    from_position_y: self.from_position.y,
                    from_position_z: self.from_position.z,
                    to_position_x: self.to_position.x,
                    to_position_y: self.to_position.y,
                    to_position_z: self.to_position.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_toggle_glide::EntityToggleGlideEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityToggleGlide(EntityToggleGlideEvent {
                    entity_id: self.entity_id,
                    is_gliding: self.is_gliding,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_toggle_swim::EntityToggleSwimEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityToggleSwim(EntityToggleSwimEvent {
                    entity_id: self.entity_id,
                    is_swimming: self.is_swimming,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_transform::EntityTransformEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityTransform(EntityTransformEvent {
                    entity_id: self.entity_id,
                    new_entity_id: self.new_entity_id,
                    transform_reason: self.transform_reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_unleash::EntityUnleashEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityUnleash(EntityUnleashEvent {
                    entity_id: self.entity_id,
                    reason: self.reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::exp_bottle::ExpBottleEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ExpBottle(ExpBottleEvent {
                    entity_id: self.entity_id,
                    experience: self.experience,
                    location_x: self.location.0.x,
                    location_y: self.location.0.y,
                    location_z: self.location.0.z,
                    show_effect: self.show_effect,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::explosion_prime::ExplosionPrimeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ExplosionPrime(ExplosionPrimeEvent {
                    entity_id: self.entity_id,
                    radius: self.radius,
                    fire: self.fire,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::firework_explode::FireworkExplodeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FireworkExplode(FireworkExplodeEvent {
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::food_level_change::FoodLevelChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::FoodLevelChange(FoodLevelChangeEvent {
                    entity_id: self.entity_id,
                    food_level: u32::from(self.food_level),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::horse_jump::HorseJumpEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::HorseJump(HorseJumpEvent {
                    entity_id: self.entity_id,
                    power: self.power,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::item_despawn::ItemDespawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ItemDespawn(ItemDespawnEvent {
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::item_merge::ItemMergeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ItemMerge(ItemMergeEvent {
                    entity_id: self.entity_id,
                    target_id: self.target_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::item_spawn::ItemSpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ItemSpawn(ItemSpawnEvent {
                    entity_id: self.entity_id,
                    pos_x: self.position.x,
                    pos_y: self.position.y,
                    pos_z: self.position.z,
                    item_name: self.item_name.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::lingering_potion_splash::LingeringPotionSplashEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::LingeringPotionSplash(LingeringPotionSplashEvent {
                    entity_id: self.entity_id,
                    location_x: self.location.0.x,
                    location_y: self.location.0.y,
                    location_z: self.location.0.z,
                    potion_item: self.potion_item.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::pig_zap::PigZapEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PigZap(PigZapEvent {
                    entity_id: self.entity_id,
                    lightning_id: self.lightning_id,
                    pig_zombie_id: self.pig_zombie_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::pig_zombie_anger::PigZombieAngerEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PigZombieAnger(PigZombieAngerEvent {
                    entity_id: self.entity_id,
                    new_anger: self.new_anger,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::piglin_barter::PiglinBarterEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PiglinBarter(PiglinBarterEvent {
                    entity_id: self.entity_id,
                    input_item_name: self.input_item.item.registry_key.to_string(),
                    input_item_count: i32::from(self.input_item.item_count),
                    outcome_names: self
                        .outcome
                        .iter()
                        .map(|i| i.item.registry_key.to_string())
                        .collect(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::potion_splash::PotionSplashEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PotionSplash(PotionSplashEvent {
                    entity_id: self.entity_id,
                    location_x: self.location.0.x,
                    location_y: self.location.0.y,
                    location_z: self.location.0.z,
                    potion_item: self.potion_item.clone(),
                    affected_entities: self.affected_entities.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::projectile_hit::ProjectileHitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ProjectileHit(ProjectileHitEvent {
                    entity_id: self.entity_id,
                    hit_position_x: self.hit_position.x,
                    hit_position_y: self.hit_position.y,
                    hit_position_z: self.hit_position.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::projectile_launch::ProjectileLaunchEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ProjectileLaunch(ProjectileLaunchEvent {
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::sheep_dye_wool::SheepDyeWoolEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SheepDyeWool(SheepDyeWoolEvent {
                    entity_id: self.entity_id,
                    dye_color: u32::from(self.dye_color),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::sheep_regrow_wool::SheepRegrowWoolEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SheepRegrowWool(SheepRegrowWoolEvent {
                    entity_id: self.entity_id,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::slime_split::SlimeSplitEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SlimeSplit(SlimeSplitEvent {
                    entity_id: self.entity_id,
                    count: self.count,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::spawner_spawn::SpawnerSpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::SpawnerSpawn(SpawnerSpawnEvent {
                    entity_id: self.entity_id,
                    spawner_pos_x: self.spawner_pos.0.x,
                    spawner_pos_y: self.spawner_pos.0.y,
                    spawner_pos_z: self.spawner_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::strider_temperature_change::StriderTemperatureChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::StriderTemperatureChange(
                    StriderTemperatureChangeEvent {
                        entity_id: self.entity_id,
                        is_shivering: self.is_shivering,
                    },
                )),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::trial_spawner_spawn::TrialSpawnerSpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::TrialSpawnerSpawn(TrialSpawnerSpawnEvent {
                    entity_id: self.entity_id,
                    spawner_pos_x: self.spawner_pos.0.x,
                    spawner_pos_y: self.spawner_pos.0.y,
                    spawner_pos_z: self.spawner_pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::villager_acquire_trade::VillagerAcquireTradeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VillagerAcquireTrade(VillagerAcquireTradeEvent {
                    entity_id: self.entity_id,
                    recipe_index: self.recipe_index,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::villager_career_change::VillagerCareerChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VillagerCareerChange(VillagerCareerChangeEvent {
                    entity_id: self.entity_id,
                    profession: self.profession.clone(),
                    reason: self.reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::villager_replenish_trade::VillagerReplenishTradeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VillagerReplenishTrade(VillagerReplenishTradeEvent {
                    entity_id: self.entity_id,
                    restock_quantity: self.restock_quantity,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::entity::villager_reputation_change::VillagerReputationChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::VillagerReputationChange(
                    VillagerReputationChangeEvent {
                        entity_id: self.entity_id,
                        target_id: self.target_id,
                        reputation_change: self.reputation_change,
                    },
                )),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::warden_anger_change::WardenAngerChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::WardenAngerChange(WardenAngerChangeEvent {
                    entity_id: self.entity_id,
                    target_id: self.target_id,
                    old_anger: self.old_anger,
                    new_anger: self.new_anger,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::raid::raid_finish::RaidFinishEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::RaidFinish(RaidFinishEvent {
                    victory: self.victory,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::raid::raid_spawn_wave::RaidSpawnWaveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::RaidSpawnWave(RaidSpawnWaveEvent {
                    wave: self.wave,
                    block_x: self.pos.0.x,
                    block_y: self.pos.0.y,
                    block_z: self.pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::raid::raid_stop::RaidStopEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::RaidStop(RaidStopEvent {
                    reason: self.reason.clone(),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::raid::raid_trigger::RaidTriggerEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::RaidTrigger(RaidTriggerEvent {
                    block_x: self.pos.0.x,
                    block_y: self.pos.0.y,
                    block_z: self.pos.0.z,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent for pumpkin::plugin::enchantment::enchant_item::EnchantItemEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EnchantItem(EnchantItemEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                    option: self.option,
                    exp_level_cost: self.exp_level_cost,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::enchantment::prepare_item_enchant::PrepareItemEnchantEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PrepareItemEnchant(PrepareItemEnchantEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_name: self.item.item.registry_key.to_string(),
                    item_count: i32::from(self.item.item_count),
                    level_requirements: self.level_requirements.to_vec(),
                    enchantment_id: self.enchantment_id.to_vec(),
                    enchantment_level: self.enchantment_level.to_vec(),
                    bookshelf_count: self.bookshelf_count,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn set_cancelled(&mut self, cancelled: bool) {
        pumpkin::plugin::Cancellable::set_cancelled(self, cancelled);
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

impl<E> EventHandler<E> for PatchBukkitEventHandler<E>
where
    E: PatchBukkitEvent + Payload + 'static,
{
    fn handle<'a>(&'a self, server: &'a Arc<Server>, event: &'a E) -> BoxFuture<'a, ()> {
        let command_tx = self.command_tx.clone();
        let payload = event.to_payload(server.clone());
        if let Some(player) = &payload.context.player {
            crate::java::native_callbacks::utils::cache_player(player.clone());
        }

        Box::pin(async move {
            let (tx, rx) = oneshot::channel();
            if let Err(e) = command_tx
                .send(JvmCommand::FireEvent {
                    payload,
                    respond_to: tx,
                    plugin: self.plugin_name.clone(),
                })
                .await
            {
                tracing::error!("Failed to send event to JVM worker: {e}");
                return;
            }

            let _ = rx.await;
        })
    }

    fn handle_blocking<'a>(
        &'a self,
        server: &'a Arc<Server>,
        event: &'a mut E,
    ) -> BoxFuture<'a, ()> {
        let command_tx = self.command_tx.clone();
        let payload = event.to_payload(server.clone());
        if let Some(player) = &payload.context.player {
            crate::java::native_callbacks::utils::cache_player(player.clone());
        }

        Box::pin(async move {
            let (tx, rx) = oneshot::channel();
            if let Err(e) = command_tx
                .send(JvmCommand::FireEvent {
                    payload,
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
                    if let Some(event_data) = response.data.and_then(|d| d.data) {
                        let _ = event.apply_modifications(server, event_data);
                    }
                }
                Err(_) => {
                    tracing::warn!("JVM worker dropped response channel for event");
                }
            }
        })
    }
}
