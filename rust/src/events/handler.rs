use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin::world::World as PumpkinWorld;
use pumpkin_api_macros::with_runtime;
use pumpkin_data::block_properties::Instrument;
use pumpkin_data::data_component_impl::EquipmentSlot;
use pumpkin_data::item::Item;
use pumpkin_data::{Block, BlockDirection};
use pumpkin_util::Hand;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use pumpkin_world::item::ItemStack;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::{Location, Uuid, Vec3, World as ProtoWorld};
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    BlockBreakEvent, BlockBurnEvent, BlockCanBuildEvent, BlockDispenseEvent, BlockFormEvent,
    BlockIgniteEvent, BlockMultiPlaceBlockEntry, BlockMultiPlaceEvent, BlockPlaceEvent,
    BlockRedstoneEvent, Event, MoistureChangeEvent, NotePlayEvent, PlayerBedEnterEvent,
    PlayerBucketEmptyEvent, PlayerBucketFillEvent, PlayerChangeWorldEvent, PlayerChatEvent,
    PlayerCommandSendEvent, PlayerDropItemEvent, PlayerExpChangeEvent, PlayerGamemodeChangeEvent,
    PlayerInteractEvent, PlayerItemBreakEvent, PlayerItemConsumeEvent, PlayerItemDamageEvent,
    PlayerItemHeldEvent, PlayerItemMendEvent, PlayerJoinEvent, PlayerKickEvent, PlayerLeaveEvent,
    PlayerLevelChangeEvent, PlayerLoginEvent, PlayerMoveEvent, PlayerTeleportEvent,
    PlayerToggleFlightEvent, ServerBroadcastEvent, ServerCommandEvent, SignChangeEvent,
    TntPrimeEvent,
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

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_join::PlayerJoinEvent {
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
        if let Data::PlayerJoin(event) = data {
            self.join_message = serde_json::from_str(&event.join_message).ok()?;
            server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_login::PlayerLoginEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerLogin(PlayerLoginEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    kick_message: serde_json::to_string(&self.kick_message).unwrap(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerLogin(event) = data {
            self.kick_message = serde_json::from_str(&event.kick_message).ok()?;
            server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_leave::PlayerLeaveEvent {
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
        if let Data::PlayerLeave(event) = data {
            self.leave_message = serde_json::from_str(&event.leave_message).ok()?;
            server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_move::PlayerMoveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerMove(PlayerMoveEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from: Some(build_location(world_uuid, &self.from, 0.0, 0.0)),
                    to: Some(build_location(world_uuid, &self.to, 0.0, 0.0)),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerMove(event) = data {
            if let Some(from) = event.from.and_then(location_to_vec3) {
                self.from = from;
            }
            if let Some(to) = event.to.and_then(location_to_vec3) {
                self.to = to;
            }
        }

        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_teleport::PlayerTeleportEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerTeleport(PlayerTeleportEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from: Some(build_location(world_uuid, &self.from, 0.0, 0.0)),
                    to: Some(build_location(world_uuid, &self.to, 0.0, 0.0)),
                    cause: "UNKNOWN".to_string(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerTeleport(event) = data {
            if let Some(from) = event.from.and_then(location_to_vec3) {
                self.from = from;
            }
            if let Some(to) = event.to.and_then(location_to_vec3) {
                self.to = to;
            }
        }

        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_change_world::PlayerChangeWorldEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChangeWorld(PlayerChangeWorldEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_world: Some(ProtoWorld {
                        uuid: Some(Uuid {
                            value: self.previous_world.uuid.to_string(),
                        }),
                    }),
                    new_world: Some(ProtoWorld {
                        uuid: Some(Uuid {
                            value: self.new_world.uuid.to_string(),
                        }),
                    }),
                    position: Some(build_location(
                        self.new_world.uuid,
                        &self.position,
                        self.yaw,
                        self.pitch,
                    )),
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

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerChangeWorld(event) = data {
            if let Some(world) = event.previous_world.and_then(|w| w.uuid) {
                self.previous_world =
                    find_world_by_uuid(server, uuid::Uuid::from_str(&world.value).ok()?)?;
            }
            if let Some(world) = event.new_world.and_then(|w| w.uuid) {
                self.new_world =
                    find_world_by_uuid(server, uuid::Uuid::from_str(&world.value).ok()?)?;
            }
            if let Some(position) = event.position.and_then(location_to_vec3) {
                self.position = position;
            }
            if event.yaw != 0.0 {
                self.yaw = event.yaw;
            }
            if event.pitch != 0.0 {
                self.pitch = event.pitch;
            }
        }

        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_gamemode_change::PlayerGamemodeChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerGamemodeChange(PlayerGamemodeChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_gamemode: gamemode_to_bukkit(self.previous_gamemode),
                    new_gamemode: gamemode_to_bukkit(self.new_gamemode),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerGamemodeChange(event) = data {
            if let Some(mode) = gamemode_from_bukkit(&event.previous_gamemode) {
                self.previous_gamemode = mode;
            }
            if let Some(mode) = gamemode_from_bukkit(&event.new_gamemode) {
                self.new_gamemode = mode;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_chat::PlayerChatEvent {
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
        if let Data::PlayerChat(event) = data {
            if !event.message.is_empty() {
                self.message = event.message;
            }
            if !event.recipients.is_empty() {
                let mut recipients = Vec::new();
                for uuid in event.recipients {
                    let player_uuid = uuid::Uuid::from_str(&uuid.value).ok()?;
                    if let Some(player) = server.get_player_by_uuid(player_uuid) {
                        recipients.push(player);
                    }
                }
                self.recipients = recipients;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerCommandSend(PlayerCommandSendEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    commands: self.commands.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerCommandSend(event) = data
            && !event.commands.is_empty()
        {
            self.commands = event.commands;
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let clicked = self.clicked_pos.as_ref().map(|pos| {
            build_location(
                world_uuid,
                &Vector3::new(f64::from(pos.0.x), f64::from(pos.0.y), f64::from(pos.0.z)),
                0.0,
                0.0,
            )
        });
        let item_key = self
            .item
            .try_lock()
            .ok()
            .map(|item| item_to_key(item.get_item()))
            .unwrap_or_default();

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteract(PlayerInteractEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    action: interact_action_to_bukkit(&self.action),
                    block_key: block_to_key(self.block),
                    clicked,
                    item_key,
                    block_face: String::new(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerInteract(event) = data {
            if let Some(action) = interact_action_from_bukkit(&event.action) {
                self.action = action;
            }
            if let Some(loc) = event.clicked.and_then(location_to_vec3) {
                self.clicked_pos = Some(pumpkin_util::math::position::BlockPos::new(
                    loc.x.floor() as i32,
                    loc.y.floor() as i32,
                    loc.z.floor() as i32,
                ));
            }
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_break::BlockBreakEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self
            .player
            .as_ref()
            .map(|p| p.world().uuid)
            .or_else(|| server.worlds.load().first().map(|w| w.uuid))
            .unwrap_or_else(uuid::Uuid::new_v4);
        let location = build_location(
            world_uuid,
            &Vector3::new(
                f64::from(self.block_position.0.x),
                f64::from(self.block_position.0.y),
                f64::from(self.block_position.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockBreak(BlockBreakEvent {
                    player_uuid: self.player.as_ref().map(|p| Uuid {
                        value: p.gameprofile.id.to_string(),
                    }),
                    block_key: block_to_key(self.block),
                    location: Some(location),
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockBreak(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            self.exp = event.exp;
            self.drop = event.drop;
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_burn::BlockBurnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockBurn(BlockBurnEvent {
                    block_key: block_to_key(self.block),
                    igniting_block_key: block_to_key(self.igniting_block),
                    location: None,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockBurn(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            if !event.igniting_block_key.is_empty()
                && let Some(block) = Block::from_name(&event.igniting_block_key)
            {
                self.igniting_block = block;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_can_build::BlockCanBuildEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let position = self.player.position();
        let location = build_location(
            world_uuid,
            &Vector3::new(position.x, position.y, position.z),
            0.0,
            0.0,
        );
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockCanBuild(BlockCanBuildEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_key: block_to_key(self.block_to_build),
                    block_against_key: block_to_key(self.block),
                    location: Some(location),
                    can_build: self.buildable,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockCanBuild(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block_to_build = block;
            }
            if !event.block_against_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_against_key)
            {
                self.block = block;
            }
            self.buildable = event.can_build;
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_place::BlockPlaceEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let position = self.player.position();
        let location = build_location(
            world_uuid,
            &Vector3::new(position.x, position.y, position.z),
            0.0,
            0.0,
        );
        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockPlace(BlockPlaceEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_key: block_to_key(self.block_placed),
                    block_against_key: block_to_key(self.block_placed_against),
                    location: Some(location),
                    can_build: self.can_build,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockPlace(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block_placed = block;
            }
            if !event.block_against_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_against_key)
            {
                self.block_placed_against = block;
            }
            self.can_build = event.can_build;
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::server::server_command::ServerCommandEvent {
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
        if let Data::ServerCommand(event) = data {
            self.command = event.command;
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::server::server_broadcast::ServerBroadcastEvent
{
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
        if let Data::ServerBroadcast(event) = data {
            self.message = serde_json::from_str(&event.message)
                .unwrap_or_else(|_| TextComponent::from_legacy_string(&event.message));
            self.sender = serde_json::from_str(&event.sender)
                .unwrap_or_else(|_| TextComponent::from_legacy_string(&event.sender));
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_bed_enter::PlayerBedEnterEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBedEnter(PlayerBedEnterEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    bed_location: Some(build_location(world_uuid, &self.bed_position, 0.0, 0.0)),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerBedEnter(event) = data
            && let Some(location) = event.bed_location
            && let Some(pos) = location_to_vec3(location)
        {
            self.bed_position = pos;
        }

        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_bucket_empty::PlayerBucketEmptyEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBucketEmpty(PlayerBucketEmptyEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    location: Some(build_location(world_uuid, &self.position, 0.0, 0.0)),
                    block_key: self.block_key.clone(),
                    block_face: block_face_to_bukkit(self.face),
                    bucket_item_key: self.bucket_item_key.clone(),
                    hand: self.hand.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerBucketEmpty(event) = data {
            if let Some(location) = event.location
                && let Some(pos) = location_to_vec3(location)
            {
                self.position = pos;
            }
            if !event.block_key.is_empty() {
                self.block_key = event.block_key;
            }
            if !event.bucket_item_key.is_empty() {
                self.bucket_item_key = event.bucket_item_key;
            }
            if !event.hand.is_empty() {
                self.hand = event.hand;
            }
            self.face = bukkit_block_face_from_string(&event.block_face);
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_bucket_fill::PlayerBucketFillEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBucketFill(PlayerBucketFillEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    location: Some(build_location(world_uuid, &self.position, 0.0, 0.0)),
                    block_key: self.block_key.clone(),
                    block_face: block_face_to_bukkit(self.face),
                    bucket_item_key: self.bucket_item_key.clone(),
                    hand: self.hand.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerBucketFill(event) = data {
            if let Some(location) = event.location
                && let Some(pos) = location_to_vec3(location)
            {
                self.position = pos;
            }
            if !event.block_key.is_empty() {
                self.block_key = event.block_key;
            }
            if !event.bucket_item_key.is_empty() {
                self.bucket_item_key = event.bucket_item_key;
            }
            if !event.hand.is_empty() {
                self.hand = event.hand;
            }
            self.face = bukkit_block_face_from_string(&event.block_face);
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_drop_item::PlayerDropItemEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerDropItem(PlayerDropItemEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_uuid: Some(Uuid {
                        value: self.item_uuid.to_string(),
                    }),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerDropItem(event) = data {
            if let Some(uuid) = event.item_uuid
                && let Ok(item_uuid) = uuid::Uuid::from_str(&uuid.value)
            {
                self.item_uuid = item_uuid;
            }
            let mut key = if event.item_key.is_empty() {
                None
            } else {
                Some(event.item_key)
            };
            let mut amount = if event.item_amount > 0 {
                Some(event.item_amount as u8)
            } else {
                None
            };

            if key.is_some() || amount.is_some() {
                let fallback_key = item_to_key(self.item_stack.item);
                let key = key.take().unwrap_or(fallback_key);
                let count = amount.take().unwrap_or(self.item_stack.item_count);
                self.item_stack = item_stack_from_key(&key, count);
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_exp_change::PlayerExpChangeEvent
{
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerExpChange(event) = data {
            self.amount = event.amount;
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_item_break::PlayerItemBreakEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemBreak(PlayerItemBreakEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerItemBreak(event) = data {
            let mut key = if event.item_key.is_empty() {
                None
            } else {
                Some(event.item_key)
            };
            let mut amount = if event.item_amount > 0 {
                Some(event.item_amount as u8)
            } else {
                None
            };

            if key.is_some() || amount.is_some() {
                let fallback_key = item_to_key(self.item_stack.item);
                let key = key.take().unwrap_or(fallback_key);
                let count = amount.take().unwrap_or(self.item_stack.item_count);
                self.item_stack = item_stack_from_key(&key, count);
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_item_consume::PlayerItemConsumeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let hand = match self.hand {
            Hand::Left => "OFF_HAND",
            Hand::Right => "HAND",
        };
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemConsume(PlayerItemConsumeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                    hand: hand.to_string(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerItemConsume(event) = data {
            if !event.hand.is_empty() {
                self.hand = if event.hand == "OFF_HAND" {
                    Hand::Left
                } else {
                    Hand::Right
                };
            }

            let mut key = if event.item_key.is_empty() {
                None
            } else {
                Some(event.item_key)
            };
            let mut amount = if event.item_amount > 0 {
                Some(event.item_amount as u8)
            } else {
                None
            };

            if key.is_some() || amount.is_some() {
                let fallback_key = item_to_key(self.item_stack.item);
                let key = key.take().unwrap_or(fallback_key);
                let count = amount.take().unwrap_or(self.item_stack.item_count);
                self.item_stack = item_stack_from_key(&key, count);
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_item_damage::PlayerItemDamageEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemDamage(PlayerItemDamageEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                    damage: self.damage,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerItemDamage(event) = data {
            self.damage = event.damage;
            let mut key = if event.item_key.is_empty() {
                None
            } else {
                Some(event.item_key)
            };
            let mut amount = if event.item_amount > 0 {
                Some(event.item_amount as u8)
            } else {
                None
            };

            if key.is_some() || amount.is_some() {
                let fallback_key = item_to_key(self.item_stack.item);
                let key = key.take().unwrap_or(fallback_key);
                let count = amount.take().unwrap_or(self.item_stack.item_count);
                self.item_stack = item_stack_from_key(&key, count);
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_item_held::PlayerItemHeldEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemHeld(PlayerItemHeldEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_slot: self.previous_slot,
                    new_slot: self.new_slot,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerItemHeld(event) = data {
            self.previous_slot = event.previous_slot;
            self.new_slot = event.new_slot;
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_item_mend::PlayerItemMendEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerItemMend(PlayerItemMendEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                    slot: equipment_slot_to_bukkit(&self.slot),
                    repair_amount: self.repair_amount,
                    orb_uuid: self.orb_uuid.map(|id| Uuid {
                        value: id.to_string(),
                    }),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerItemMend(event) = data {
            self.repair_amount = event.repair_amount;
            if !event.slot.is_empty()
                && let Some(slot) = equipment_slot_from_bukkit(&event.slot)
            {
                self.slot = slot;
            }
            if let Some(uuid) = event.orb_uuid
                && let Ok(orb_uuid) = uuid::Uuid::from_str(&uuid.value)
            {
                self.orb_uuid = Some(orb_uuid);
            }
            let mut key = if event.item_key.is_empty() {
                None
            } else {
                Some(event.item_key)
            };
            let mut amount = if event.item_amount > 0 {
                Some(event.item_amount as u8)
            } else {
                None
            };

            if key.is_some() || amount.is_some() {
                let fallback_key = item_to_key(self.item_stack.item);
                let key = key.take().unwrap_or(fallback_key);
                let count = amount.take().unwrap_or(self.item_stack.item_count);
                self.item_stack = item_stack_from_key(&key, count);
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_kick::PlayerKickEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerKick(PlayerKickEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    reason: serde_json::to_string(&self.reason).unwrap_or_default(),
                    leave_message: serde_json::to_string(&self.leave_message).unwrap_or_default(),
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerKick(event) = data {
            if !event.reason.is_empty()
                && let Ok(reason) = serde_json::from_str(&event.reason)
            {
                self.reason = reason;
            }
            if !event.leave_message.is_empty()
                && let Ok(msg) = serde_json::from_str(&event.leave_message)
            {
                self.leave_message = msg;
            }
            if !event.cause.is_empty() {
                self.cause = event.cause;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_level_change::PlayerLevelChangeEvent
{
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerLevelChange(event) = data {
            self.old_level = event.old_level;
            self.new_level = event.new_level;
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::player::player_toggle_flight::PlayerToggleFlightEvent
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::PlayerToggleFlight(event) = data {
            self.is_flying = event.is_flying;
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_dispense::BlockDispenseEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_position.0.x),
                f64::from(self.block_position.0.y),
                f64::from(self.block_position.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockDispense(BlockDispenseEvent {
                    block_key: block_to_key(self.block),
                    location: Some(location),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                    velocity: Some(Vec3 {
                        x: self.velocity.x,
                        y: self.velocity.y,
                        z: self.velocity.z,
                    }),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockDispense(event) = data {
            if !event.item_key.is_empty() || event.item_amount > 0 {
                let key = if event.item_key.is_empty() {
                    item_to_key(self.item_stack.item)
                } else {
                    event.item_key
                };
                let count = if event.item_amount > 0 {
                    event.item_amount as u8
                } else {
                    self.item_stack.item_count
                };
                self.item_stack = item_stack_from_key(&key, count);
            }
            if let Some(vel) = event.velocity {
                self.velocity = Vector3::new(vel.x, vel.y, vel.z);
            }
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_form::BlockFormEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockForm(BlockFormEvent {
                    block_key: block_to_key(self.old_block),
                    new_block_key: block_to_key(self.new_block),
                    location: Some(location),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockForm(event) = data {
            if !event.new_block_key.is_empty()
                && let Some(block) = Block::from_name(&event.new_block_key)
            {
                self.new_block = block;
            }
            if let Some(loc) = event.location
                && let Some(pos) = location_to_vec3(loc.clone())
            {
                self.block_pos = pumpkin_util::math::position::BlockPos::new(
                    pos.x.floor() as i32,
                    pos.y.floor() as i32,
                    pos.z.floor() as i32,
                );
            }
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.old_block = block;
            }
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_ignite::BlockIgniteEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockIgnite(BlockIgniteEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_key: block_to_key(self.block),
                    igniting_block_key: block_to_key(self.igniting_block),
                    location: Some(location),
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockIgnite(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            if !event.igniting_block_key.is_empty()
                && let Some(block) = Block::from_name(&event.igniting_block_key)
            {
                self.igniting_block = block;
            }
            if let Some(loc) = event.location {
                if let Some(pos) = location_to_vec3(loc.clone()) {
                    self.block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                }
                if let Some(world) = loc.world.and_then(|w| w.uuid)
                    && let Ok(uuid) = uuid::Uuid::from_str(&world.value)
                {
                    self.world_uuid = uuid;
                }
            }
            if !event.cause.is_empty() {
                self.cause = event.cause;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::block::block_multi_place::BlockMultiPlaceEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let origin = self
            .positions
            .first()
            .copied()
            .unwrap_or_else(|| self.player.position().to_block_pos());
        let location = build_location(
            world_uuid,
            &Vector3::new(
                f64::from(origin.0.x),
                f64::from(origin.0.y),
                f64::from(origin.0.z),
            ),
            0.0,
            0.0,
        );
        let blocks = self
            .positions
            .iter()
            .map(|pos| {
                let loc = build_location(
                    world_uuid,
                    &Vector3::new(f64::from(pos.0.x), f64::from(pos.0.y), f64::from(pos.0.z)),
                    0.0,
                    0.0,
                );
                BlockMultiPlaceBlockEntry {
                    block_key: block_to_key(self.block_placed),
                    location: Some(loc),
                }
            })
            .collect();

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockMultiPlace(BlockMultiPlaceEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_key: block_to_key(self.block_placed),
                    location: Some(location),
                    blocks,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, _data: Data) -> Option<()> {
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::block_redstone::BlockRedstoneEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockRedstone(BlockRedstoneEvent {
                    block_key: block_to_key(self.block),
                    location: Some(location),
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::BlockRedstone(event) = data {
            self.new_current = event.new_current;
        }
        Some(())
    }
}

impl PatchBukkitEvent
    for pumpkin::plugin::api::events::block::moisture_change::MoistureChangeEvent
{
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );
        let new_block_key = block_to_key(Block::from_state_id(self.new_state_id));

        JvmEventPayload {
            event: Event {
                data: Some(Data::MoistureChange(MoistureChangeEvent {
                    block_key: block_to_key(self.block),
                    location: Some(location),
                    new_block_key,
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::MoistureChange(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            if let Some(loc) = event.location {
                if let Some(pos) = location_to_vec3(loc.clone()) {
                    self.block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                }
                if let Some(world) = loc.world.and_then(|w| w.uuid)
                    && let Ok(uuid) = uuid::Uuid::from_str(&world.value)
                {
                    self.world_uuid = uuid;
                }
            }
            if !event.new_block_key.is_empty()
                && let Some(block) = Block::from_name(&event.new_block_key)
            {
                self.new_state_id = block.default_state.id;
            }
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::note_play::NotePlayEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::NotePlay(NotePlayEvent {
                    block_key: block_to_key(self.block),
                    location: Some(location),
                    instrument: instrument_to_bukkit(self.instrument),
                    note: i32::from(self.note),
                })),
            },
            context: EventContext {
                server,
                player: None,
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::NotePlay(event) = data {
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            if let Some(loc) = event.location {
                if let Some(pos) = location_to_vec3(loc.clone()) {
                    self.block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                }
                if let Some(world) = loc.world.and_then(|w| w.uuid)
                    && let Ok(uuid) = uuid::Uuid::from_str(&world.value)
                {
                    self.world_uuid = uuid;
                }
            }
            if !event.instrument.is_empty()
                && let Some(instrument) = instrument_from_bukkit(&event.instrument)
            {
                self.instrument = instrument;
            }
            self.note = event.note.clamp(0, 24) as u8;
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::sign_change::SignChangeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let location = build_location(
            world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::SignChange(SignChangeEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    block_key: block_to_key(self.block),
                    location: Some(location),
                    lines: self.lines.clone(),
                    is_front_text: self.is_front_text,
                })),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::SignChange(event) = data {
            if let Some(uuid) = event.player_uuid
                && let Ok(uuid) = uuid::Uuid::from_str(&uuid.value)
                && let Some(player) = server.get_player_by_uuid(uuid)
            {
                self.player = player;
            }
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            if let Some(loc) = event.location
                && let Some(pos) = location_to_vec3(loc.clone())
            {
                self.block_pos = pumpkin_util::math::position::BlockPos::new(
                    pos.x.floor() as i32,
                    pos.y.floor() as i32,
                    pos.z.floor() as i32,
                );
            }
            if !event.lines.is_empty() {
                self.lines = event.lines;
            }
            self.is_front_text = event.is_front_text;
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::block::tnt_prime::TNTPrimeEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(
            self.world_uuid,
            &Vector3::new(
                f64::from(self.block_pos.0.x),
                f64::from(self.block_pos.0.y),
                f64::from(self.block_pos.0.z),
            ),
            0.0,
            0.0,
        );
        let player_uuid = self.player.as_ref().map(|player| Uuid {
            value: player.gameprofile.id.to_string(),
        });

        JvmEventPayload {
            event: Event {
                data: Some(Data::TntPrime(TntPrimeEvent {
                    player_uuid,
                    block_key: block_to_key(self.block),
                    location: Some(location),
                    cause: self.cause.clone(),
                })),
            },
            context: EventContext {
                server,
                player: self.player.clone(),
            },
        }
    }

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        if let Data::TntPrime(event) = data {
            if let Some(uuid) = event.player_uuid {
                if let Ok(uuid) = uuid::Uuid::from_str(&uuid.value) {
                    self.player = server.get_player_by_uuid(uuid);
                }
            } else {
                self.player = None;
            }
            if !event.block_key.is_empty()
                && let Some(block) = Block::from_name(&event.block_key)
            {
                self.block = block;
            }
            if let Some(loc) = event.location {
                if let Some(pos) = location_to_vec3(loc.clone()) {
                    self.block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                }
                if let Some(world) = loc.world.and_then(|w| w.uuid)
                    && let Ok(uuid) = uuid::Uuid::from_str(&world.value)
                {
                    self.world_uuid = uuid;
                }
            }
            if !event.cause.is_empty() {
                self.cause = event.cause;
            }
        }
        Some(())
    }
}

fn build_location(
    world_uuid: uuid::Uuid,
    position: &Vector3<f64>,
    yaw: f32,
    pitch: f32,
) -> Location {
    Location {
        world: Some(ProtoWorld {
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

fn block_to_key(block: &Block) -> String {
    format!("minecraft:{}", block.name)
}

fn item_to_key(item: &Item) -> String {
    format!("minecraft:{}", item.registry_key)
}

fn item_stack_from_key(key: &str, amount: u8) -> ItemStack {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    let item = Item::from_registry_key(trimmed).unwrap_or(&Item::AIR);
    ItemStack::new(amount, item)
}

fn equipment_slot_to_bukkit(slot: &EquipmentSlot) -> String {
    match slot {
        EquipmentSlot::MainHand(_) => "HAND".to_string(),
        EquipmentSlot::OffHand(_) => "OFF_HAND".to_string(),
        EquipmentSlot::Feet(_) => "FEET".to_string(),
        EquipmentSlot::Legs(_) => "LEGS".to_string(),
        EquipmentSlot::Chest(_) => "CHEST".to_string(),
        EquipmentSlot::Head(_) => "HEAD".to_string(),
        EquipmentSlot::Body(_) => "BODY".to_string(),
        EquipmentSlot::Saddle(_) => "SADDLE".to_string(),
    }
}

fn equipment_slot_from_bukkit(slot: &str) -> Option<EquipmentSlot> {
    match slot {
        "HAND" => Some(EquipmentSlot::MAIN_HAND),
        "OFF_HAND" => Some(EquipmentSlot::OFF_HAND),
        "FEET" => Some(EquipmentSlot::FEET),
        "LEGS" => Some(EquipmentSlot::LEGS),
        "CHEST" => Some(EquipmentSlot::CHEST),
        "HEAD" => Some(EquipmentSlot::HEAD),
        "BODY" => Some(EquipmentSlot::BODY),
        "SADDLE" => Some(EquipmentSlot::SADDLE),
        _ => None,
    }
}

fn find_world_by_uuid(server: &Arc<Server>, world_uuid: uuid::Uuid) -> Option<Arc<PumpkinWorld>> {
    server
        .worlds
        .load()
        .iter()
        .find(|world| world.uuid == world_uuid)
        .cloned()
}

fn gamemode_to_bukkit(mode: pumpkin_util::GameMode) -> String {
    match mode {
        pumpkin_util::GameMode::Survival => "SURVIVAL".to_string(),
        pumpkin_util::GameMode::Creative => "CREATIVE".to_string(),
        pumpkin_util::GameMode::Adventure => "ADVENTURE".to_string(),
        pumpkin_util::GameMode::Spectator => "SPECTATOR".to_string(),
    }
}

fn gamemode_from_bukkit(mode: &str) -> Option<pumpkin_util::GameMode> {
    match mode {
        "SURVIVAL" => Some(pumpkin_util::GameMode::Survival),
        "CREATIVE" => Some(pumpkin_util::GameMode::Creative),
        "ADVENTURE" => Some(pumpkin_util::GameMode::Adventure),
        "SPECTATOR" => Some(pumpkin_util::GameMode::Spectator),
        _ => None,
    }
}

fn block_face_to_bukkit(face: Option<BlockDirection>) -> String {
    match face {
        Some(BlockDirection::Up) => "UP".to_string(),
        Some(BlockDirection::Down) => "DOWN".to_string(),
        Some(BlockDirection::North) => "NORTH".to_string(),
        Some(BlockDirection::South) => "SOUTH".to_string(),
        Some(BlockDirection::West) => "WEST".to_string(),
        Some(BlockDirection::East) => "EAST".to_string(),
        None => String::new(),
    }
}

fn bukkit_block_face_from_string(face: &str) -> Option<BlockDirection> {
    match face {
        "UP" => Some(BlockDirection::Up),
        "DOWN" => Some(BlockDirection::Down),
        "NORTH" => Some(BlockDirection::North),
        "SOUTH" => Some(BlockDirection::South),
        "WEST" => Some(BlockDirection::West),
        "EAST" => Some(BlockDirection::East),
        _ => None,
    }
}

fn instrument_to_bukkit(instrument: Instrument) -> String {
    match instrument {
        Instrument::Harp => "PIANO".to_string(),
        Instrument::Basedrum => "BASS_DRUM".to_string(),
        Instrument::Snare => "SNARE_DRUM".to_string(),
        Instrument::Hat => "STICKS".to_string(),
        Instrument::Bass => "BASS_GUITAR".to_string(),
        Instrument::Flute => "FLUTE".to_string(),
        Instrument::Bell => "BELL".to_string(),
        Instrument::Guitar => "GUITAR".to_string(),
        Instrument::Chime => "CHIME".to_string(),
        Instrument::Xylophone => "XYLOPHONE".to_string(),
        Instrument::IronXylophone => "IRON_XYLOPHONE".to_string(),
        Instrument::CowBell => "COW_BELL".to_string(),
        Instrument::Didgeridoo => "DIDGERIDOO".to_string(),
        Instrument::Bit => "BIT".to_string(),
        Instrument::Banjo => "BANJO".to_string(),
        Instrument::Pling => "PLING".to_string(),
        Instrument::Zombie => "ZOMBIE".to_string(),
        Instrument::Skeleton => "SKELETON".to_string(),
        Instrument::Creeper => "CREEPER".to_string(),
        Instrument::Dragon => "DRAGON".to_string(),
        Instrument::WitherSkeleton => "WITHER_SKELETON".to_string(),
        Instrument::Piglin => "PIGLIN".to_string(),
        Instrument::CustomHead => "CUSTOM_HEAD".to_string(),
    }
}

fn instrument_from_bukkit(name: &str) -> Option<Instrument> {
    match name {
        "PIANO" => Some(Instrument::Harp),
        "BASS_DRUM" => Some(Instrument::Basedrum),
        "SNARE_DRUM" => Some(Instrument::Snare),
        "STICKS" => Some(Instrument::Hat),
        "BASS_GUITAR" => Some(Instrument::Bass),
        "FLUTE" => Some(Instrument::Flute),
        "BELL" => Some(Instrument::Bell),
        "GUITAR" => Some(Instrument::Guitar),
        "CHIME" => Some(Instrument::Chime),
        "XYLOPHONE" => Some(Instrument::Xylophone),
        "IRON_XYLOPHONE" => Some(Instrument::IronXylophone),
        "COW_BELL" => Some(Instrument::CowBell),
        "DIDGERIDOO" => Some(Instrument::Didgeridoo),
        "BIT" => Some(Instrument::Bit),
        "BANJO" => Some(Instrument::Banjo),
        "PLING" => Some(Instrument::Pling),
        "ZOMBIE" => Some(Instrument::Zombie),
        "SKELETON" => Some(Instrument::Skeleton),
        "CREEPER" => Some(Instrument::Creeper),
        "DRAGON" => Some(Instrument::Dragon),
        "WITHER_SKELETON" => Some(Instrument::WitherSkeleton),
        "PIGLIN" => Some(Instrument::Piglin),
        "CUSTOM_HEAD" => Some(Instrument::CustomHead),
        _ => None,
    }
}

fn interact_action_to_bukkit(
    action: &pumpkin::plugin::api::events::player::player_interact_event::InteractAction,
) -> String {
    match action {
        pumpkin::plugin::api::events::player::player_interact_event::InteractAction::LeftClickBlock => {
            "LEFT_CLICK_BLOCK".to_string()
        }
        pumpkin::plugin::api::events::player::player_interact_event::InteractAction::LeftClickAir => {
            "LEFT_CLICK_AIR".to_string()
        }
        pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickAir => {
            "RIGHT_CLICK_AIR".to_string()
        }
        pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickBlock => {
            "RIGHT_CLICK_BLOCK".to_string()
        }
    }
}

fn interact_action_from_bukkit(
    action: &str,
) -> Option<pumpkin::plugin::api::events::player::player_interact_event::InteractAction> {
    match action {
        "LEFT_CLICK_BLOCK" => Some(
            pumpkin::plugin::api::events::player::player_interact_event::InteractAction::LeftClickBlock,
        ),
        "LEFT_CLICK_AIR" => Some(
            pumpkin::plugin::api::events::player::player_interact_event::InteractAction::LeftClickAir,
        ),
        "RIGHT_CLICK_AIR" => Some(
            pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickAir,
        ),
        "RIGHT_CLICK_BLOCK" => Some(
            pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickBlock,
        ),
        _ => None,
    }
}

pub struct PatchBukkitEventHandler<E: PatchBukkitEvent> {
    plugin_name: String,
    command_tx: mpsc::Sender<JvmCommand>,
    _phantom: PhantomData<E>,
}

trait CancellationBridge {
    fn apply_cancelled(&mut self, cancelled: bool);
}

macro_rules! impl_cancellation_bridge {
    ($($ty:path),+ $(,)?) => {
        $(
            impl CancellationBridge for $ty {
                fn apply_cancelled(&mut self, cancelled: bool) {
                    self.set_cancelled(cancelled);
                }
            }
        )+
    };
}

impl_cancellation_bridge!(
    pumpkin::plugin::api::events::player::player_join::PlayerJoinEvent,
    pumpkin::plugin::api::events::player::player_login::PlayerLoginEvent,
    pumpkin::plugin::api::events::player::player_leave::PlayerLeaveEvent,
    pumpkin::plugin::api::events::player::player_move::PlayerMoveEvent,
    pumpkin::plugin::api::events::player::player_teleport::PlayerTeleportEvent,
    pumpkin::plugin::api::events::player::player_change_world::PlayerChangeWorldEvent,
    pumpkin::plugin::api::events::player::player_gamemode_change::PlayerGamemodeChangeEvent,
    pumpkin::plugin::api::events::player::player_chat::PlayerChatEvent,
    pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent,
    pumpkin::plugin::api::events::player::player_bed_enter::PlayerBedEnterEvent,
    pumpkin::plugin::api::events::player::player_bucket_empty::PlayerBucketEmptyEvent,
    pumpkin::plugin::api::events::player::player_bucket_fill::PlayerBucketFillEvent,
    pumpkin::plugin::api::events::player::player_drop_item::PlayerDropItemEvent,
    pumpkin::plugin::api::events::player::player_item_consume::PlayerItemConsumeEvent,
    pumpkin::plugin::api::events::player::player_item_damage::PlayerItemDamageEvent,
    pumpkin::plugin::api::events::player::player_item_held::PlayerItemHeldEvent,
    pumpkin::plugin::api::events::player::player_item_mend::PlayerItemMendEvent,
    pumpkin::plugin::api::events::player::player_kick::PlayerKickEvent,
    pumpkin::plugin::api::events::player::player_toggle_flight::PlayerToggleFlightEvent,
    pumpkin::plugin::api::events::block::block_break::BlockBreakEvent,
    pumpkin::plugin::api::events::block::block_burn::BlockBurnEvent,
    pumpkin::plugin::api::events::block::block_can_build::BlockCanBuildEvent,
    pumpkin::plugin::api::events::block::block_place::BlockPlaceEvent,
    pumpkin::plugin::api::events::block::block_dispense::BlockDispenseEvent,
    pumpkin::plugin::api::events::block::block_form::BlockFormEvent,
    pumpkin::plugin::api::events::block::block_ignite::BlockIgniteEvent,
    pumpkin::plugin::api::events::block::block_multi_place::BlockMultiPlaceEvent,
    pumpkin::plugin::api::events::block::block_redstone::BlockRedstoneEvent,
    pumpkin::plugin::api::events::block::moisture_change::MoistureChangeEvent,
    pumpkin::plugin::api::events::block::note_play::NotePlayEvent,
    pumpkin::plugin::api::events::block::sign_change::SignChangeEvent,
    pumpkin::plugin::api::events::block::tnt_prime::TNTPrimeEvent,
    pumpkin::plugin::api::events::server::server_command::ServerCommandEvent,
    pumpkin::plugin::api::events::server::server_broadcast::ServerBroadcastEvent,
);

impl CancellationBridge
    for pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent
{
    fn apply_cancelled(&mut self, _cancelled: bool) {}
}

impl CancellationBridge
    for pumpkin::plugin::api::events::player::player_exp_change::PlayerExpChangeEvent
{
    fn apply_cancelled(&mut self, _cancelled: bool) {}
}

impl CancellationBridge
    for pumpkin::plugin::api::events::player::player_item_break::PlayerItemBreakEvent
{
    fn apply_cancelled(&mut self, _cancelled: bool) {}
}

impl CancellationBridge
    for pumpkin::plugin::api::events::player::player_level_change::PlayerLevelChangeEvent
{
    fn apply_cancelled(&mut self, _cancelled: bool) {}
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
    E: PatchBukkitEvent + Payload + CancellationBridge + 'static,
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
                    event.apply_cancelled(response.cancelled);
                    if let Some(data) = response.data.and_then(|d| d.data) {
                        event.apply_modifications(server, data);
                    }
                }
                Err(_) => {
                    log::warn!("JVM worker dropped response channel for event");
                }
            }
        })
    }
}
