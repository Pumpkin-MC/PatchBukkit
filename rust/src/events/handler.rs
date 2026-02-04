use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin_api_macros::with_runtime;
use pumpkin_data::data_component_impl::EquipmentSlot;
use pumpkin_data::{Block, BlockDirection};
use pumpkin_data::item::Item;
use pumpkin_util::Hand;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use pumpkin_world::item::ItemStack;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::{Location, Uuid, Vec3, World};
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    BlockBreakEvent, BlockPlaceEvent, Event, PlayerChatEvent, PlayerCommandEvent, PlayerCommandSendEvent, PlayerJoinEvent,
    PlayerLeaveEvent, PlayerMoveEvent, PlayerInteractEvent, ServerBroadcastEvent, ServerCommandEvent,
    EntityDamageEvent, EntityDeathEvent, EntitySpawnEvent,
    PlayerLoginEvent, PlayerTeleportEvent, PlayerChangeWorldEvent, PlayerGamemodeChangeEvent,
    AsyncPlayerPreLoginEvent, PlayerAdvancementDoneEvent, PlayerAnimationEvent,
    PlayerArmorStandManipulateEvent,
    PlayerBedEnterEvent, PlayerBedLeaveEvent,
    PlayerBucketEmptyEvent, PlayerBucketFillEvent, PlayerBucketEntityEvent,
    PlayerChangedMainHandEvent,
    PlayerRegisterChannelEvent, PlayerUnregisterChannelEvent, PlayerDropItemEvent,
    PlayerEditBookEvent, PlayerEggThrowEvent, PlayerExpChangeEvent, PlayerFishEvent,
    PlayerInteractEntityEvent, PlayerInteractAtEntityEvent, PlayerItemHeldEvent,
    PlayerItemDamageEvent, PlayerItemBreakEvent, PlayerItemConsumeEvent, PlayerItemMendEvent,
    PlayerLevelChangeEvent, PlayerKickEvent,
    PlayerToggleSneakEvent, PlayerToggleSprintEvent, PlayerToggleFlightEvent,
    PlayerSwapHandItemsEvent, PlayerResourcePackStatusEvent, PlayerRespawnEvent,
    PlayerPickupArrowEvent, PlayerPortalEvent, PlayerRecipeDiscoverEvent, PlayerRiptideEvent,
    PlayerShearEntityEvent, PlayerSpawnLocationEvent, PlayerStatisticIncrementEvent,
    PlayerVelocityEvent,
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerChangedMainHand(PlayerChangedMainHandEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    main_hand: bukkit_main_hand(self.main_hand),
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
            Data::PlayerChangedMainHand(event) => {
                if let Ok(uuid) = uuid::Uuid::from_str(&event.player_uuid?.value) {
                    server.get_player_by_uuid(uuid)?;
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent {
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

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerRegisterChannel(event) => {
                if let Ok(uuid) = uuid::Uuid::from_str(&event.player_uuid?.value) {
                    server.get_player_by_uuid(uuid)?;
                }
                if !event.channel.is_empty() {
                    self.channel = event.channel;
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerUnregisterChannel(PlayerUnregisterChannelEvent {
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

    fn apply_modifications(&mut self, server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerUnregisterChannel(event) => {
                if let Ok(uuid) = uuid::Uuid::from_str(&event.player_uuid?.value) {
                    server.get_player_by_uuid(uuid)?;
                }
                if !event.channel.is_empty() {
                    self.channel = event.channel;
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent {
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
                if !event.command.is_empty() {
                    self.command = event.command;
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
                data: Some(Data::PlayerCommandSend(crate::proto::patchbukkit::events::PlayerCommandSendEvent {
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
        match data {
            Data::PlayerCommandSend(event) => {
                self.commands = event.commands;
            }
            _ => {}
        }

        Some(())
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
        match data {
            Data::PlayerDropItem(event) => {
                if let Some(uuid) = event.item_uuid {
                    if let Ok(item_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.item_uuid = item_uuid;
                    }
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
            _ => {}
        }
        Some(())
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
                    is_signing: self.is_signing,
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
            Data::PlayerEditBook(event) => {
                self.slot = event.slot;
                self.pages = event.pages;
                self.is_signing = event.is_signing;
                self.title = if event.title.is_empty() {
                    None
                } else {
                    Some(event.title)
                };
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent {
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
                    num_hatches: i32::from(self.num_hatches),
                    hatching_type: self.hatching_type.clone(),
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
            Data::PlayerEggThrow(event) => {
                if let Some(uuid) = event.egg_uuid {
                    if let Ok(egg_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.egg_uuid = egg_uuid;
                    }
                }
                self.hatching = event.hatching;
                if event.num_hatches >= 0 {
                    self.num_hatches = event.num_hatches.min(u8::MAX as i32) as u8;
                }
                if !event.hatching_type.is_empty() {
                    self.hatching_type = event.hatching_type;
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent {
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
        match data {
            Data::PlayerExpChange(event) => {
                self.amount = event.amount;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_fish::PlayerFishEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerFish(PlayerFishEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    caught_uuid: self.caught_uuid.map(|uuid| Uuid {
                        value: uuid.to_string(),
                    }),
                    caught_type: self.caught_type.clone(),
                    hook_uuid: Some(Uuid {
                        value: self.hook_uuid.to_string(),
                    }),
                    state: self.state.clone(),
                    hand: self.hand.clone(),
                    exp_to_drop: self.exp_to_drop,
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
            Data::PlayerFish(event) => {
                self.caught_uuid = event
                    .caught_uuid
                    .and_then(|uuid| uuid::Uuid::from_str(&uuid.value).ok());
                if !event.caught_type.is_empty() {
                    self.caught_type = event.caught_type;
                }
                if let Some(uuid) = event.hook_uuid {
                    if let Ok(hook_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.hook_uuid = hook_uuid;
                    }
                }
                if !event.state.is_empty() {
                    self.state = event.state;
                }
                if !event.hand.is_empty() {
                    self.hand = event.hand;
                }
                self.exp_to_drop = event.exp_to_drop;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_interact_entity::PlayerInteractEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteractEntity(PlayerInteractEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    entity_type: self.entity_type.clone(),
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
            Data::PlayerInteractEntity(event) => {
                if let Some(uuid) = event.entity_uuid {
                    if let Ok(entity_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.entity_uuid = entity_uuid;
                    }
                }
                if !event.entity_type.is_empty() {
                    self.entity_type = event.entity_type;
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

impl PatchBukkitEvent for pumpkin::plugin::player::player_interact_at_entity::PlayerInteractAtEntityEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerInteractAtEntity(PlayerInteractAtEntityEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    entity_type: self.entity_type.clone(),
                    hand: self.hand.clone(),
                    clicked_position: Some(Vec3 {
                        x: f64::from(self.clicked_position.x),
                        y: f64::from(self.clicked_position.y),
                        z: f64::from(self.clicked_position.z),
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
        match data {
            Data::PlayerInteractAtEntity(event) => {
                if let Some(uuid) = event.entity_uuid {
                    if let Ok(entity_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.entity_uuid = entity_uuid;
                    }
                }
                if !event.entity_type.is_empty() {
                    self.entity_type = event.entity_type;
                }
                if !event.hand.is_empty() {
                    self.hand = event.hand;
                }
                if let Some(pos) = event.clicked_position {
                    self.clicked_position = Vector3::new(pos.x as f32, pos.y as f32, pos.z as f32);
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_item_held::PlayerItemHeldEvent {
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
        match data {
            Data::PlayerItemHeld(event) => {
                self.previous_slot = event.previous_slot;
                self.new_slot = event.new_slot;
            }
            _ => {}
        }
        Some(())
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
        match data {
            Data::PlayerItemDamage(event) => {
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
            _ => {}
        }
        Some(())
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
        match data {
            Data::PlayerItemBreak(event) => {
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
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_item_consume::PlayerItemConsumeEvent {
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
        match data {
            Data::PlayerItemConsume(event) => {
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
            _ => {}
        }
        Some(())
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
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: i32::from(self.item_stack.item_count),
                    slot: equipment_slot_to_bukkit(&self.slot),
                    repair_amount: self.repair_amount,
                    orb_uuid: self.orb_uuid.map(|id| Uuid { value: id.to_string() }),
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
            Data::PlayerItemMend(event) => {
                self.repair_amount = event.repair_amount;
                if !event.slot.is_empty() {
                    if let Some(slot) = equipment_slot_from_bukkit(&event.slot) {
                        self.slot = slot;
                    }
                }
                if let Some(uuid) = event.orb_uuid {
                    if let Ok(orb_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.orb_uuid = Some(orb_uuid);
                    }
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
            _ => {}
        }
        Some(())
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerLevelChange(event) => {
                self.old_level = event.old_level;
                self.new_level = event.new_level;
            }
            _ => {}
        }
        Some(())
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
        match data {
            Data::PlayerKick(event) => {
                if !event.reason.is_empty() {
                    if let Ok(reason) = serde_json::from_str(&event.reason) {
                        self.reason = reason;
                    }
                }
                if !event.leave_message.is_empty() {
                    if let Ok(msg) = serde_json::from_str(&event.leave_message) {
                        self.leave_message = msg;
                    }
                }
                if !event.cause.is_empty() {
                    self.cause = event.cause;
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_toggle_sneak::PlayerToggleSneakEvent {
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerToggleSneak(event) => {
                self.is_sneaking = event.is_sneaking;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_toggle_sprint::PlayerToggleSprintEvent {
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

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::PlayerToggleSprint(event) => {
                self.is_sprinting = event.is_sprinting;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_toggle_flight::PlayerToggleFlightEvent {
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
        match data {
            Data::PlayerToggleFlight(event) => {
                self.is_flying = event.is_flying;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_swap_hand_items::PlayerSwapHandItemsEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerSwapHandItems(PlayerSwapHandItemsEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    main_hand_item_key: item_to_key(self.main_hand_item.item),
                    main_hand_item_amount: i32::from(self.main_hand_item.item_count),
                    off_hand_item_key: item_to_key(self.off_hand_item.item),
                    off_hand_item_amount: i32::from(self.off_hand_item.item_count),
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
            Data::PlayerSwapHandItems(event) => {
                if !event.main_hand_item_key.is_empty() || event.main_hand_item_amount > 0 {
                    let key = if event.main_hand_item_key.is_empty() {
                        item_to_key(self.main_hand_item.item)
                    } else {
                        event.main_hand_item_key
                    };
                    let count = if event.main_hand_item_amount > 0 {
                        event.main_hand_item_amount as u8
                    } else {
                        self.main_hand_item.item_count
                    };
                    self.main_hand_item = item_stack_from_key(&key, count);
                }
                if !event.off_hand_item_key.is_empty() || event.off_hand_item_amount > 0 {
                    let key = if event.off_hand_item_key.is_empty() {
                        item_to_key(self.off_hand_item.item)
                    } else {
                        event.off_hand_item_key
                    };
                    let count = if event.off_hand_item_amount > 0 {
                        event.off_hand_item_amount as u8
                    } else {
                        self.off_hand_item.item_count
                    };
                    self.off_hand_item = item_stack_from_key(&key, count);
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_resource_pack_status::PlayerResourcePackStatusEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerResourcePackStatus(PlayerResourcePackStatusEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    pack_uuid: Some(Uuid {
                        value: self.pack_uuid.to_string(),
                    }),
                    status: self.status.clone(),
                    hash: self.pack_hash.clone(),
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
            Data::PlayerResourcePackStatus(event) => {
                if let Some(uuid) = event.pack_uuid {
                    if let Ok(pack_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.pack_uuid = pack_uuid;
                    }
                }
                if !event.status.is_empty() {
                    self.status = event.status;
                }
                if !event.hash.is_empty() {
                    self.pack_hash = event.hash;
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_respawn::PlayerRespawnEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let yaw = self.player.living_entity.entity.yaw.load();
        let pitch = self.player.living_entity.entity.pitch.load();
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRespawn(PlayerRespawnEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    respawn_location: Some(build_location(
                        self.world_uuid,
                        &Vector3::new(
                            self.respawn_position.x,
                            self.respawn_position.y,
                            self.respawn_position.z,
                        ),
                        yaw,
                        pitch,
                    )),
                    is_bed_spawn: self.is_bed_spawn,
                    is_anchor_spawn: self.is_anchor_spawn,
                    is_missing_respawn_block: self.is_missing_respawn_block,
                    respawn_reason: self.reason.clone(),
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
            Data::PlayerRespawn(event) => {
                if let Some(loc) = event.respawn_location {
                    if let Some(pos) = location_to_vec3(loc.clone()) {
                        self.respawn_position = pos;
                    }
                    if let Some(world) = loc.world.and_then(|w| w.uuid) {
                        if let Ok(uuid) = uuid::Uuid::from_str(&world.value) {
                            self.world_uuid = uuid;
                        }
                    }
                }
                self.is_bed_spawn = event.is_bed_spawn;
                self.is_anchor_spawn = event.is_anchor_spawn;
                self.is_missing_respawn_block = event.is_missing_respawn_block;
                if !event.respawn_reason.is_empty() {
                    self.reason = event.respawn_reason;
                }
            }
            _ => {}
        }
        Some(())
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
                    arrow_uuid: Some(Uuid {
                        value: self.arrow_uuid.to_string(),
                    }),
                    item_uuid: Some(Uuid {
                        value: self.item_uuid.to_string(),
                    }),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: self.item_stack.item_count as i32,
                    remaining: self.remaining,
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
            Data::PlayerPickupArrow(event) => {
                if let Some(uuid) = event.arrow_uuid {
                    if let Ok(arrow_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.arrow_uuid = arrow_uuid;
                    }
                }
                if let Some(uuid) = event.item_uuid {
                    if let Ok(item_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.item_uuid = item_uuid;
                    }
                }
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
                self.remaining = event.remaining;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_portal::PlayerPortalEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let yaw = self.player.living_entity.entity.yaw.load();
        let pitch = self.player.living_entity.entity.pitch.load();
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerPortal(PlayerPortalEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    from: Some(build_location(
                        self.from_world_uuid,
                        &self.from_position,
                        yaw,
                        pitch,
                    )),
                    to: Some(build_location(
                        self.to_world_uuid,
                        &self.to_position,
                        yaw,
                        pitch,
                    )),
                    cause: self.cause.clone(),
                    search_radius: self.search_radius,
                    can_create_portal: self.can_create_portal,
                    creation_radius: self.creation_radius,
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
            Data::PlayerPortal(event) => {
                if let Some(from) = event.from {
                    if let Some(pos) = location_to_vec3(from.clone()) {
                        self.from_position = pos;
                    }
                    if let Some(world) = from.world.and_then(|w| w.uuid) {
                        if let Ok(uuid) = uuid::Uuid::from_str(&world.value) {
                            self.from_world_uuid = uuid;
                        }
                    }
                }
                if let Some(to) = event.to {
                    if let Some(pos) = location_to_vec3(to.clone()) {
                        self.to_position = pos;
                    }
                    if let Some(world) = to.world.and_then(|w| w.uuid) {
                        if let Ok(uuid) = uuid::Uuid::from_str(&world.value) {
                            self.to_world_uuid = uuid;
                        }
                    }
                }
                if !event.cause.is_empty() {
                    self.cause = event.cause;
                }
                self.search_radius = event.search_radius;
                self.can_create_portal = event.can_create_portal;
                self.creation_radius = event.creation_radius;
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_recipe_discover::PlayerRecipeDiscoverEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerRecipeDiscover(PlayerRecipeDiscoverEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    recipe_key: self.recipe_key.clone(),
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
            Data::PlayerRecipeDiscover(event) => {
                if !event.recipe_key.is_empty() {
                    self.recipe_key = event.recipe_key;
                }
            }
            _ => {}
        }
        Some(())
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
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: self.item_stack.item_count as i32,
                    velocity: Some(Vec3 {
                        x: self.velocity.x,
                        y: self.velocity.y,
                        z: self.velocity.z,
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
        match data {
            Data::PlayerRiptide(event) => {
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
                if let Some(velocity) = event.velocity {
                    self.velocity = Vector3::new(velocity.x, velocity.y, velocity.z);
                }
            }
            _ => {}
        }
        Some(())
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
                    entity_uuid: Some(Uuid {
                        value: self.entity_uuid.to_string(),
                    }),
                    entity_type: self.entity_type.clone(),
                    item_key: item_to_key(self.item_stack.item),
                    item_amount: self.item_stack.item_count as i32,
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
            Data::PlayerShearEntity(event) => {
                if let Some(uuid) = event.entity_uuid {
                    if let Ok(entity_uuid) = uuid::Uuid::from_str(&uuid.value) {
                        self.entity_uuid = entity_uuid;
                    }
                }
                if !event.entity_type.is_empty() {
                    self.entity_type = event.entity_type;
                }
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
                if !event.hand.is_empty() {
                    self.hand = event.hand;
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_spawn_location::PlayerSpawnLocationEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        let yaw = self.player.living_entity.entity.yaw.load();
        let pitch = self.player.living_entity.entity.pitch.load();
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerSpawnLocation(PlayerSpawnLocationEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    spawn_location: Some(build_location(
                        self.world_uuid,
                        &self.spawn_position,
                        yaw,
                        pitch,
                    )),
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
            Data::PlayerSpawnLocation(event) => {
                if let Some(loc) = event.spawn_location {
                    if let Some(pos) = location_to_vec3(loc.clone()) {
                        self.spawn_position = pos;
                    }
                    if let Some(world) = loc.world.and_then(|w| w.uuid) {
                        if let Ok(uuid) = uuid::Uuid::from_str(&world.value) {
                            self.world_uuid = uuid;
                        }
                    }
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::player::player_statistic_increment::PlayerStatisticIncrementEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerStatisticIncrement(PlayerStatisticIncrementEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    statistic: self.statistic.clone(),
                    initial_value: self.initial_value,
                    new_value: self.new_value,
                    entity_type: self.entity_type.clone(),
                    material_key: self.material_key.clone(),
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
            Data::PlayerStatisticIncrement(event) => {
                if !event.statistic.is_empty() {
                    self.statistic = event.statistic;
                }
                self.initial_value = event.initial_value;
                self.new_value = event.new_value;
                if !event.entity_type.is_empty() {
                    self.entity_type = event.entity_type;
                }
                if !event.material_key.is_empty() {
                    self.material_key = event.material_key;
                }
            }
            _ => {}
        }
        Some(())
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
                    velocity: Some(Vec3 {
                        x: self.velocity.x,
                        y: self.velocity.y,
                        z: self.velocity.z,
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
        match data {
            Data::PlayerVelocity(event) => {
                if let Some(velocity) = event.velocity {
                    self.velocity = Vector3::new(velocity.x, velocity.y, velocity.z);
                }
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

fn bukkit_main_hand(hand: Hand) -> String {
    match hand {
        Hand::Left => "LEFT".to_string(),
        Hand::Right => "RIGHT".to_string(),
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
