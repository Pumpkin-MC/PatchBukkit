use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin_data::Block;
use pumpkin_data::item::Item;
use pumpkin_world::item::ItemStack;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use tokio::sync::Mutex;

use crate::events::handler::PatchBukkitEventHandler;
use crate::java::native_callbacks::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{CallEventRequest, CallEventResponse, RegisterEventRequest};

pub fn ffi_native_bridge_register_event_impl(request: RegisterEventRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let pumpkin_priority = match request.priority {
        0 => EventPriority::Lowest,
        1 => EventPriority::Low,
        2 => EventPriority::Normal,
        3 => EventPriority::High,
        _ => EventPriority::Highest,
    };

    log::info!(
        "Plugin '{}' registering listener for '{}' (priority={:?}, blocking={})",
        request.plugin_name,
        request.event_type,
        request.priority,
        request.blocking
    );

    let command_tx = ctx.command_tx.clone();
    let context = ctx.plugin_context.clone();

    tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            match request.event_type.as_str() {
                "org.bukkit.event.player.PlayerJoinEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_join::PlayerJoinEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_join::PlayerJoinEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerLoginEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_login::PlayerLoginEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_login::PlayerLoginEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerQuitEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_leave::PlayerLeaveEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_leave::PlayerLeaveEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerMoveEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_move::PlayerMoveEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_move::PlayerMoveEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerTeleportEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_teleport::PlayerTeleportEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_teleport::PlayerTeleportEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerChangedWorldEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_change_world::PlayerChangeWorldEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_change_world::PlayerChangeWorldEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerGameModeChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_gamemode_change::PlayerGamemodeChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_gamemode_change::PlayerGamemodeChangeEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.AsyncPlayerChatEvent"
                | "org.bukkit.event.player.PlayerChatEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_chat::PlayerChatEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_chat::PlayerChatEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerCommandSendEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerInteractEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockBreakEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_break::BlockBreakEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_break::BlockBreakEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockBurnEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_burn::BlockBurnEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_burn::BlockBurnEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockCanBuildEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_can_build::BlockCanBuildEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_can_build::BlockCanBuildEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockPlaceEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_place::BlockPlaceEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_place::BlockPlaceEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.server.ServerCommandEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::server::server_command::ServerCommandEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::server::server_command::ServerCommandEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.server.BroadcastMessageEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::server::server_broadcast::ServerBroadcastEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::server::server_broadcast::ServerBroadcastEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                _ => {
                    log::warn!("Unsupported Bukkit event registration: {}", request.event_type);
                }
            }
        })
    });

    Some(())
}

pub fn ffi_native_bridge_call_event_impl(request: CallEventRequest) -> Option<CallEventResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;

    let handled = tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            let Some(data) = request.event.and_then(|event| event.data) else {
                return Some(false);
            };

            match data {
                Data::PlayerChat(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let mut recipients = Vec::new();
                    for recipient in event.recipients {
                        let rid = uuid::Uuid::parse_str(&recipient.value).ok()?;
                        if let Some(recipient_player) = ctx.plugin_context.server.get_player_by_uuid(rid) {
                            recipients.push(recipient_player);
                        }
                    }
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_chat::PlayerChatEvent::new(
                            player,
                            event.message,
                            recipients,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerCommandSend(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let command = event.commands.first().cloned().unwrap_or_default();
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent::new(
                            player,
                            command,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerInteract(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let action = match event.action.as_str() {
                        "LEFT_CLICK_BLOCK" => pumpkin::plugin::api::events::player::player_interact_event::InteractAction::LeftClickBlock,
                        "LEFT_CLICK_AIR" => pumpkin::plugin::api::events::player::player_interact_event::InteractAction::LeftClickAir,
                        "RIGHT_CLICK_AIR" => pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickAir,
                        "RIGHT_CLICK_BLOCK" => pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickBlock,
                        _ => pumpkin::plugin::api::events::player::player_interact_event::InteractAction::RightClickAir,
                    };
                    let block = block_from_key(&event.block_key);
                    let clicked_pos = event
                        .clicked
                        .and_then(location_to_vec3)
                        .map(|pos| pumpkin_util::math::position::BlockPos::new(
                            pos.x.floor() as i32,
                            pos.y.floor() as i32,
                            pos.z.floor() as i32,
                        ));
                    let item = Arc::new(Mutex::new(item_stack_from_key(&event.item_key, 1)));
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent::new(
                            &player,
                            action,
                            &item,
                            block,
                            clicked_pos,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerMove(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let from = event.from.and_then(location_to_vec3).unwrap_or_else(Vector3::default);
                    let to = event.to.and_then(location_to_vec3).unwrap_or_else(Vector3::default);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_move::PlayerMoveEvent::new(
                            player,
                            from,
                            to,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerTeleport(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let from = event.from.and_then(location_to_vec3).unwrap_or_else(Vector3::default);
                    let to = event.to.and_then(location_to_vec3).unwrap_or_else(Vector3::default);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_teleport::PlayerTeleportEvent::new(
                            player,
                            from,
                            to,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChangeWorld(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let previous_world = event
                        .previous_world
                        .and_then(|w| w.uuid)
                        .and_then(|id| uuid::Uuid::parse_str(&id.value).ok())
                        .and_then(|id| find_world_by_uuid(&ctx.plugin_context.server, id))
                        .unwrap_or_else(|| ctx.plugin_context.server.worlds.load()[0].clone());
                    let new_world = event
                        .new_world
                        .and_then(|w| w.uuid)
                        .and_then(|id| uuid::Uuid::parse_str(&id.value).ok())
                        .and_then(|id| find_world_by_uuid(&ctx.plugin_context.server, id))
                        .unwrap_or_else(|| ctx.plugin_context.server.worlds.load()[0].clone());
                    let position = event
                        .position
                        .and_then(location_to_vec3)
                        .unwrap_or_else(Vector3::default);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_change_world::PlayerChangeWorldEvent::new(
                            player,
                            previous_world,
                            new_world,
                            position,
                            event.yaw,
                            event.pitch,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerGamemodeChange(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let previous = gamemode_from_bukkit(&event.previous_gamemode)
                        .unwrap_or(pumpkin_util::GameMode::Survival);
                    let new_mode = gamemode_from_bukkit(&event.new_gamemode)
                        .unwrap_or(pumpkin_util::GameMode::Survival);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_gamemode_change::PlayerGamemodeChangeEvent::new(
                            player,
                            previous,
                            new_mode,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockBreak(event) => {
                    let player = event
                        .player_uuid
                        .and_then(|id| uuid::Uuid::parse_str(&id.value).ok())
                        .and_then(|id| ctx.plugin_context.server.get_player_by_uuid(id));
                    let block = block_from_key(&event.block_key);
                    let position = event
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| pumpkin_util::math::position::BlockPos::new(0, 0, 0));
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_break::BlockBreakEvent::new(
                            player,
                            block,
                            position,
                            event.exp,
                            event.drop,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockBurn(event) => {
                    let block = block_from_key(&event.block_key);
                    let igniting_block = block_from_key(&event.igniting_block_key);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_burn::BlockBurnEvent {
                            igniting_block,
                            block,
                            cancelled: false,
                        };
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockCanBuild(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let block_to_build = block_from_key(&event.block_key);
                    let block_against = block_from_key(&event.block_against_key);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_can_build::BlockCanBuildEvent {
                            block_to_build,
                            buildable: event.can_build,
                            player,
                            block: block_against,
                            cancelled: false,
                        };
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockPlace(event) => {
                    let uuid = uuid::Uuid::parse_str(&event.player_uuid?.value).ok()?;
                    let player = ctx.plugin_context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&event.block_key);
                    let against = block_from_key(&event.block_against_key);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_place::BlockPlaceEvent {
                            player,
                            block_placed: block,
                            block_placed_against: against,
                            can_build: event.can_build,
                            cancelled: false,
                        };
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::ServerCommand(event) => {
                    let pumpkin_event =
                        pumpkin::plugin::api::events::server::server_command::ServerCommandEvent::new(
                            event.command,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::ServerBroadcast(event) => {
                    let message = serde_json::from_str(&event.message)
                        .unwrap_or_else(|_| TextComponent::from_legacy_string(&event.message));
                    let sender = serde_json::from_str(&event.sender)
                        .unwrap_or_else(|_| TextComponent::from_legacy_string(&event.sender));
                    let pumpkin_event =
                        pumpkin::plugin::api::events::server::server_broadcast::ServerBroadcastEvent::new(
                            message,
                            sender,
                        );
                    ctx.plugin_context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                _ => Some(false),
            }
        })
    })?;

    Some(CallEventResponse { handled })
}

fn block_from_key(key: &str) -> &'static Block {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    Block::from_registry_key(trimmed).unwrap_or(&Block::AIR)
}

fn item_stack_from_key(key: &str, amount: i32) -> ItemStack {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    let item = Item::from_registry_key(trimmed).unwrap_or(&Item::AIR);
    let count = amount.clamp(0, u8::MAX as i32) as u8;
    ItemStack::new(count, item)
}

fn location_to_vec3(location: crate::proto::patchbukkit::common::Location) -> Option<Vector3<f64>> {
    let pos = location.position?;
    Some(Vector3::new(pos.x, pos.y, pos.z))
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

fn find_world_by_uuid(
    server: &std::sync::Arc<pumpkin::server::Server>,
    world_uuid: uuid::Uuid,
) -> Option<std::sync::Arc<pumpkin::world::World>> {
    server
        .worlds
        .load()
        .iter()
        .find(|world| world.uuid == world_uuid)
        .cloned()
}

