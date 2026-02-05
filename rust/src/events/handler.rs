use std::marker::PhantomData;
use std::str::FromStr;
use std::sync::Arc;

use pumpkin::entity::player::Player;
use pumpkin::plugin::{BoxFuture, Cancellable, EventHandler, Payload};
use pumpkin::server::Server;
use pumpkin::world::World as PumpkinWorld;
use pumpkin_api_macros::with_runtime;
use pumpkin_data::Block;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::common::{Location, Uuid, Vec3, World as ProtoWorld};
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    BlockBreakEvent, BlockBurnEvent, BlockCanBuildEvent, BlockPlaceEvent, Event, PlayerChangeWorldEvent,
    PlayerChatEvent, PlayerCommandSendEvent, PlayerGamemodeChangeEvent, PlayerInteractEvent, PlayerJoinEvent,
    PlayerLeaveEvent, PlayerLoginEvent, PlayerMoveEvent, PlayerTeleportEvent, ServerBroadcastEvent, ServerCommandEvent,
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
        match data {
            Data::PlayerMove(event) => {
                if let Some(from) = event.from.and_then(location_to_vec3) {
                    self.from = from;
                }
                if let Some(to) = event.to.and_then(location_to_vec3) {
                    self.to = to;
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_teleport::PlayerTeleportEvent {
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
                    cause: String::new(),
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
            Data::PlayerTeleport(event) => {
                if let Some(from) = event.from.and_then(location_to_vec3) {
                    self.from = from;
                }
                if let Some(to) = event.to.and_then(location_to_vec3) {
                    self.to = to;
                }
            }
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_change_world::PlayerChangeWorldEvent {
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
                    position: Some(build_location(self.new_world.uuid, &self.position, self.yaw, self.pitch)),
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
        match data {
            Data::PlayerChangeWorld(event) => {
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
            _ => {}
        }

        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_gamemode_change::PlayerGamemodeChangeEvent {
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
                if let Some(mode) = gamemode_from_bukkit(&event.previous_gamemode) {
                    self.previous_gamemode = mode;
                }
                if let Some(mode) = gamemode_from_bukkit(&event.new_gamemode) {
                    self.new_gamemode = mode;
                }
            }
            _ => {}
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
        match data {
            Data::PlayerChat(event) => {
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
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::PlayerCommandSend(PlayerCommandSendEvent {
                    player_uuid: Some(Uuid {
                        value: self.player.gameprofile.id.to_string(),
                    }),
                    commands: vec![self.command.clone()],
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
                if let Some(command) = event.commands.first() {
                    self.command = command.clone();
                }
            }
            _ => {}
        }
        Some(())
    }
}

impl PatchBukkitEvent for pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent {
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
        match data {
            Data::PlayerInteract(event) => {
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
                if !event.block_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_key) {
                        self.block = block;
                    }
                }
            }
            _ => {}
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
        match data {
            Data::BlockBreak(event) => {
                if !event.block_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_key) {
                        self.block = block;
                    }
                }
                self.exp = event.exp;
                self.drop = event.drop;
            }
            _ => {}
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
            context: EventContext { server, player: None },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::BlockBurn(event) => {
                if !event.block_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_key) {
                        self.block = block;
                    }
                }
                if !event.igniting_block_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.igniting_block_key) {
                        self.igniting_block = block;
                    }
                }
            }
            _ => {}
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
        match data {
            Data::BlockCanBuild(event) => {
                if !event.block_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_key) {
                        self.block_to_build = block;
                    }
                }
                if !event.block_against_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_against_key) {
                        self.block = block;
                    }
                }
                self.buildable = event.can_build;
            }
            _ => {}
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
        match data {
            Data::BlockPlace(event) => {
                if !event.block_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_key) {
                        self.block_placed = block;
                    }
                }
                if !event.block_against_key.is_empty() {
                    if let Some(block) = Block::from_name(&event.block_against_key) {
                        self.block_placed_against = block;
                    }
                }
                self.can_build = event.can_build;
            }
            _ => {}
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
            context: EventContext { server, player: None },
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

impl PatchBukkitEvent for pumpkin::plugin::api::events::server::server_broadcast::ServerBroadcastEvent {
    fn to_payload(&self, server: Arc<Server>) -> JvmEventPayload {
        JvmEventPayload {
            event: Event {
                data: Some(Data::ServerBroadcast(ServerBroadcastEvent {
                    message: serde_json::to_string(&self.message).unwrap(),
                    sender: serde_json::to_string(&self.sender).unwrap(),
                })),
            },
            context: EventContext { server, player: None },
        }
    }

    fn apply_modifications(&mut self, _server: &Arc<Server>, data: Data) -> Option<()> {
        match data {
            Data::ServerBroadcast(event) => {
                self.message = serde_json::from_str(&event.message)
                    .unwrap_or_else(|_| TextComponent::from_legacy_string(&event.message));
                self.sender = serde_json::from_str(&event.sender)
                    .unwrap_or_else(|_| TextComponent::from_legacy_string(&event.sender));
            }
            _ => {}
        }
        Some(())
    }
}

fn build_location(world_uuid: uuid::Uuid, position: &Vector3<f64>, yaw: f32, pitch: f32) -> Location {
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

fn item_to_key(item: &pumpkin_data::item::Item) -> String {
    format!("minecraft:{}", item.registry_key)
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
