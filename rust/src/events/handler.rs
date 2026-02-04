use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin_api_macros::with_runtime;
use pumpkin_data::{Block, BlockDirection};
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::{Location, Uuid, Vec3, World};
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    BlockBreakEvent, BlockPlaceEvent, Event, PlayerChatEvent, PlayerCommandEvent, PlayerJoinEvent,
    PlayerLeaveEvent, PlayerMoveEvent, PlayerInteractEvent, ServerBroadcastEvent, ServerCommandEvent,
    EntityDamageEvent, EntityDeathEvent, EntitySpawnEvent,
    PlayerLoginEvent, PlayerTeleportEvent, PlayerChangeWorldEvent, PlayerGamemodeChangeEvent,
    AsyncPlayerPreLoginEvent, PlayerAdvancementDoneEvent, PlayerAnimationEvent,
    PlayerArmorStandManipulateEvent,
    PlayerBedEnterEvent, PlayerBedLeaveEvent,
    PlayerBucketEmptyEvent, PlayerBucketFillEvent, PlayerBucketEntityEvent,
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_login::PlayerLoginEvent {
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
        match data {
            Data::PlayerLogin(event) => {
                self.kick_message = serde_json::from_str(&event.kick_message).ok()?;
                server.get_player_by_uuid(uuid::Uuid::from_str(&event.player_uuid?.value).ok()?)?;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::AsyncPlayerPreLogin(AsyncPlayerPreLoginEvent {
                    name: self.name.clone(),
                    player_uuid: Some(Uuid {
                        value: self.player_uuid.to_string(),
                    }),
                    address: self.address.clone(),
                    result: self.result.clone(),
                    kick_message: self.kick_message.clone(),
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
            Data::AsyncPlayerPreLogin(event) => {
                self.result = event.result;
                self.kick_message = event.kick_message;
                if let Some(uuid) = event.player_uuid {
                    self.player_uuid = uuid::Uuid::from_str(&uuid.value).ok()?;
                }
                if !event.name.is_empty() {
                    self.name = event.name;
                }
                if !event.address.is_empty() {
                    self.address = event.address;
                }
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerAdvancementDone(PlayerAdvancementDoneEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    advancement_key: self.advancement_key.clone(),
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_animation::PlayerAnimationEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerAnimation(PlayerAnimationEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    animation_type: self.animation_type.clone(),
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerArmorStandManipulate(
                    PlayerArmorStandManipulateEvent {
                        player_uuid: Some(Uuid {
                            value: self.player.gameprofile.id.to_string(),
                        }),
                        armor_stand_uuid: Some(Uuid {
                            value: self.armor_stand_uuid.to_string(),
                        }),
                        item_key: self.item_key.clone(),
                        armor_stand_item_key: self.armor_stand_item_key.clone(),
                        slot: self.slot.clone(),
                    },
                )),
            },
            context: EventContext {
                server,
                player: Some(self.player.clone()),
            },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerArmorStandManipulate(event) => {
                if !event.item_key.is_empty() {
                    self.item_key = event.item_key;
                }
                if !event.armor_stand_item_key.is_empty() {
                    self.armor_stand_item_key = event.armor_stand_item_key;
                }
                if !event.slot.is_empty() {
                    self.slot = event.slot;
                }
                if let Some(uuid) = event.armor_stand_uuid {
                    self.armor_stand_uuid = uuid::Uuid::from_str(&uuid.value).ok()?;
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent {
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
        match data {
            Data::PlayerBedEnter(event) => {
                if let Some(location) = event.bed_location {
                    if let Some(pos) = location_to_vec3(location) {
                        self.bed_position = pos;
                    }
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerBedLeave(PlayerBedLeaveEvent {
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
        match data {
            Data::PlayerBedLeave(event) => {
                if let Some(location) = event.bed_location {
                    if let Some(pos) = location_to_vec3(location) {
                        self.bed_position = pos;
                    }
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent {
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
        match data {
            Data::PlayerBucketEmpty(event) => {
                if let Some(location) = event.location {
                    if let Some(pos) = location_to_vec3(location) {
                        self.position = pos;
                    }
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
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent {
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
        match data {
            Data::PlayerBucketFill(event) => {
                if let Some(location) = event.location {
                    if let Some(pos) = location_to_vec3(location) {
                        self.position = pos;
                    }
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
            _ => {}
        }
        Some(())
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
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    entity_type: self.entity_type.clone(),
                    original_bucket_key: self.original_bucket_key.clone(),
                    entity_bucket_key: self.entity_bucket_key.clone(),
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
        match data {
            Data::PlayerBucketEntity(event) => {
                if let Some(uuid) = event.entity_uuid {
                    self.entity_uuid = uuid::Uuid::from_str(&uuid.value).ok()?;
                }
                if !event.entity_type.is_empty() {
                    self.entity_type = event.entity_type;
                }
                if !event.original_bucket_key.is_empty() {
                    self.original_bucket_key = event.original_bucket_key;
                }
                if !event.entity_bucket_key.is_empty() {
                    self.entity_bucket_key = event.entity_bucket_key;
                }
                if !event.hand.is_empty() {
                    self.hand = event.hand;
                }
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_teleport::PlayerTeleportEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let yaw = self.player.living_entity.entity.yaw.load();
        let pitch = self.player.living_entity.entity.pitch.load();

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerTeleport(PlayerTeleportEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from: Some(build_location(world_uuid, &self.from, yaw, pitch)),
                    to: Some(build_location(world_uuid, &self.to, yaw, pitch)),
                    cause: String::new(),
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
            Data::PlayerTeleport(event) => {
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let location = build_location(self.new_world.uuid, &self.position, self.yaw, self.pitch);

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChangeWorld(PlayerChangeWorldEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    previous_world: Some(World {
                        uuid: Some(Uuid {
                            value: self.previous_world.uuid.to_string(),
                        }),
                    }),
                    new_world: Some(World {
                        uuid: Some(Uuid {
                            value: self.new_world.uuid.to_string(),
                        }),
                    }),
                    position: Some(location),
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerChangeWorld(event) => {
                if let Some(position) = event.position.and_then(location_to_vec3) {
                    self.position = position;
                }
                self.yaw = event.yaw;
                self.pitch = event.pitch;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent {
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
        match data {
            Data::PlayerGamemodeChange(event) => {
                if let Some(prev) = gamemode_from_bukkit(&event.previous_gamemode) {
                    self.previous_gamemode = prev;
                }
                if let Some(next) = gamemode_from_bukkit(&event.new_gamemode) {
                    self.new_gamemode = next;
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_interact_event::PlayerInteractEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let world_uuid = self.player.world().uuid;
        let yaw = self.player.living_entity.entity.yaw.load();
        let pitch = self.player.living_entity.entity.pitch.load();

        let clicked = self.clicked_pos.map(|pos| {
            build_location(
                world_uuid,
                &Vector3::new(
                    f64::from(pos.0.x),
                    f64::from(pos.0.y),
                    f64::from(pos.0.z),
                ),
                yaw,
                pitch,
            )
        });

        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteract(PlayerInteractEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    action: interact_action_to_bukkit(&self.action),
                    block_key: block_to_key(self.block),
                    clicked,
                    item_key: self.item_key.clone(),
                    block_face: block_face_to_bukkit(self.face),
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

impl PatchBukkitEvent for pumpkin::plugin::block::block_break::BlockBreakEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let (player_uuid, world_uuid) = if let Some(player) = &self.player {
            (player.gameprofile.id.to_string(), Some(player.world().uuid))
        } else {
            ("00000000-0000-0000-0000-000000000000".to_string(), None)
        };

        let location = world_uuid.map(|world_uuid| {
            build_location(
                world_uuid,
                &Vector3::new(
                    f64::from(self.block_position.0.x),
                    f64::from(self.block_position.0.y),
                    f64::from(self.block_position.0.z),
                ),
                0.0,
                0.0,
            )
        });

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockBreak(BlockBreakEvent {
                    player_uuid: Some(Uuid { value: player_uuid }),
                    block_key: block_to_key(self.block),
                    location,
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
        match data {
            Data::BlockBreak(event) => {
                self.exp = event.exp;
                self.drop = event.drop;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::block::block_place::BlockPlaceEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let player_uuid = self.player.gameprofile.id.to_string();
        let world_uuid = self.player.world().uuid;
        let location = build_location(
            world_uuid,
            &Vector3::new(
                f64::from(self.position.0.x),
                f64::from(self.position.0.y),
                f64::from(self.position.0.z),
            ),
            0.0,
            0.0,
        );

        JvmEventPayload {
            event: Event {
                data: Some(Data::BlockPlace(BlockPlaceEvent {
                    player_uuid: Some(Uuid { value: player_uuid }),
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
        match data {
            Data::BlockPlace(event) => {
                self.can_build = event.can_build;
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntitySpawn(EntitySpawnEvent {
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    entity_type: self.entity_type.id.to_string(),
                })),
            },
            context: EventContext { server, player: None },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, _data: Data) -> Option<()> {
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_damage::EntityDamageEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDamage(EntityDamageEvent {
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    damage: self.damage,
                    damage_type: self.damage_type.message_id.to_string(),
                })),
            },
            context: EventContext { server, player: None },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::EntityDamage(event) => {
                self.damage = event.damage;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::entity::entity_death::EntityDeathEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::EntityDeath(EntityDeathEvent {
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    damage_type: self.damage_type.message_id.to_string(),
                })),
            },
            context: EventContext { server, player: None },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, _data: Data) -> Option<()> {
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

fn block_to_key(block: &Block) -> String {
    format!("minecraft:{}", block.name)
}

fn interact_action_to_bukkit(action: &pumpkin::plugin::player::player_interact_event::InteractAction) -> String {
    match action {
        pumpkin::plugin::player::player_interact_event::InteractAction::LeftClickBlock => {
            "LEFT_CLICK_BLOCK".to_string()
        }
        pumpkin::plugin::player::player_interact_event::InteractAction::LeftClickAir => {
            "LEFT_CLICK_AIR".to_string()
        }
        pumpkin::plugin::player::player_interact_event::InteractAction::RightClickAir => {
            "RIGHT_CLICK_AIR".to_string()
        }
        pumpkin::plugin::player::player_interact_event::InteractAction::RightClickBlock => {
            "RIGHT_CLICK_BLOCK".to_string()
        }
    }
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
