use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin_data::item::Item;
use pumpkin_data::{Block, BlockDirection};
use pumpkin_util::math::position::BlockPos;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use pumpkin_world::item::ItemStack;
use tokio::sync::Mutex;

use crate::events::handler::PatchBukkitEventHandler;
use crate::java::native_callbacks::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    CallEventRequest, CallEventResponse, RegisterEventRequest,
};

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
                "org.bukkit.event.player.PlayerBedEnterEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_bed_enter::PlayerBedEnterEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_bed_enter::PlayerBedEnterEvent,
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
                "org.bukkit.event.player.PlayerBucketEmptyEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_bucket_empty::PlayerBucketEmptyEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_bucket_empty::PlayerBucketEmptyEvent,
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
                "org.bukkit.event.player.PlayerBucketFillEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_bucket_fill::PlayerBucketFillEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_bucket_fill::PlayerBucketFillEvent,
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
                "org.bukkit.event.player.PlayerDropItemEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_drop_item::PlayerDropItemEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_drop_item::PlayerDropItemEvent,
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
                "org.bukkit.event.player.PlayerExpChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_exp_change::PlayerExpChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_exp_change::PlayerExpChangeEvent,
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
                "org.bukkit.event.player.PlayerItemBreakEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_item_break::PlayerItemBreakEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_item_break::PlayerItemBreakEvent,
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
                "org.bukkit.event.player.PlayerItemConsumeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_item_consume::PlayerItemConsumeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_item_consume::PlayerItemConsumeEvent,
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
                "org.bukkit.event.player.PlayerItemDamageEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_item_damage::PlayerItemDamageEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_item_damage::PlayerItemDamageEvent,
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
                "org.bukkit.event.player.PlayerItemHeldEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_item_held::PlayerItemHeldEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_item_held::PlayerItemHeldEvent,
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
                "org.bukkit.event.player.PlayerItemMendEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_item_mend::PlayerItemMendEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_item_mend::PlayerItemMendEvent,
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
                "org.bukkit.event.player.PlayerKickEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_kick::PlayerKickEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_kick::PlayerKickEvent,
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
                "org.bukkit.event.player.PlayerLevelChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_level_change::PlayerLevelChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_level_change::PlayerLevelChangeEvent,
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
                "org.bukkit.event.player.PlayerToggleFlightEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::player::player_toggle_flight::PlayerToggleFlightEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::player::player_toggle_flight::PlayerToggleFlightEvent,
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
                "org.bukkit.event.block.BlockDispenseEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_dispense::BlockDispenseEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_dispense::BlockDispenseEvent,
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
                "org.bukkit.event.block.BlockFormEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_form::BlockFormEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_form::BlockFormEvent,
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
                "org.bukkit.event.block.BlockIgniteEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_ignite::BlockIgniteEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_ignite::BlockIgniteEvent,
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
                "org.bukkit.event.block.BlockMultiPlaceEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_multi_place::BlockMultiPlaceEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_multi_place::BlockMultiPlaceEvent,
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
                "org.bukkit.event.block.BlockRedstoneEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::block_redstone::BlockRedstoneEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::block_redstone::BlockRedstoneEvent,
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
                "org.bukkit.event.block.MoistureChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::moisture_change::MoistureChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::moisture_change::MoistureChangeEvent,
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
                "org.bukkit.event.block.NotePlayEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::note_play::NotePlayEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::note_play::NotePlayEvent,
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
                "org.bukkit.event.block.SignChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::sign_change::SignChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::sign_change::SignChangeEvent,
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
                "org.bukkit.event.block.TNTPrimeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::api::events::block::tnt_prime::TNTPrimeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::api::events::block::tnt_prime::TNTPrimeEvent,
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
                }                _ => {
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
                    let commands = if event.commands.is_empty() {
                        vec![String::new()]
                    } else {
                        event.commands
                    };
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_command_send::PlayerCommandSendEvent::new(
                            player,
                            commands,
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
                    let item_key = event.item_key;
                    let block_face = block_face_from_bukkit(&event.block_face);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::player::player_interact_event::PlayerInteractEvent::new(
                            &player,
                            action,
                            &item,
                            item_key,
                            block,
                            clicked_pos,
                            block_face,
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
                    let block_pos = location_to_block_pos(event.location.as_ref());
                    let world_uuid =
                        location_world_uuid(event.location.as_ref(), &ctx.plugin_context.server);
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_burn::BlockBurnEvent {
                            igniting_block,
                            block,
                            block_pos,
                            world_uuid,
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
                    let block_pos = location_to_block_pos(event.location.as_ref());
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_can_build::BlockCanBuildEvent {
                            block_to_build,
                            buildable: event.can_build,
                            player,
                            block: block_against,
                            block_pos,
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
                    let position = location_to_block_pos(event.location.as_ref());
                    let pumpkin_event =
                        pumpkin::plugin::api::events::block::block_place::BlockPlaceEvent {
                            player,
                            block_placed: block,
                            block_placed_against: against,
                            position,
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

fn block_face_from_bukkit(face: &str) -> Option<BlockDirection> {
    match face {
        "DOWN" => Some(BlockDirection::Down),
        "UP" => Some(BlockDirection::Up),
        "NORTH" => Some(BlockDirection::North),
        "SOUTH" => Some(BlockDirection::South),
        "WEST" => Some(BlockDirection::West),
        "EAST" => Some(BlockDirection::East),
        _ => None,
    }
}

fn location_to_block_pos(
    location: Option<&crate::proto::patchbukkit::common::Location>,
) -> BlockPos {
    let pos = location.and_then(|loc| loc.position);
    match pos {
        Some(pos) => BlockPos::new(
            pos.x.floor() as i32,
            pos.y.floor() as i32,
            pos.z.floor() as i32,
        ),
        None => BlockPos::new(0, 0, 0),
    }
}

fn location_world_uuid(
    location: Option<&crate::proto::patchbukkit::common::Location>,
    server: &std::sync::Arc<pumpkin::server::Server>,
) -> uuid::Uuid {
    location
        .and_then(|loc| loc.world.as_ref())
        .and_then(|world| world.uuid.as_ref())
        .and_then(|id| uuid::Uuid::parse_str(&id.value).ok())
        .or_else(|| server.worlds.load().first().map(|world| world.uuid))
        .unwrap_or_else(uuid::Uuid::new_v4)
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
