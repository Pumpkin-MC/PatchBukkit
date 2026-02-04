use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin::plugin::block::block_break::BlockBreakEvent;
use pumpkin::plugin::block::block_place::BlockPlaceEvent;
use pumpkin::plugin::player::player_chat::PlayerChatEvent;
use pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent;
use pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent;
use pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent;
use pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent;
use pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent;
use pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent;
use pumpkin::plugin::player::player_interact_event::{InteractAction, PlayerInteractEvent};
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
use pumpkin::plugin::player::player_login::PlayerLoginEvent;
use pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent;
use pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent;
use pumpkin::plugin::player::player_animation::PlayerAnimationEvent;
use pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent;
use pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent;
use pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent;
use pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent;
use pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent;
use pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent;
use pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent;
use pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent;
use pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent;
use pumpkin::plugin::player::player_leave::PlayerLeaveEvent;
use pumpkin::plugin::player::player_move::PlayerMoveEvent;
use pumpkin::plugin::player::player_teleport::PlayerTeleportEvent;
use pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent;
use pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent;
use pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent;
use pumpkin::plugin::server::server_command::ServerCommandEvent;
use pumpkin_data::Block;
use pumpkin_data::BlockDirection;
use pumpkin_data::item::Item;
use pumpkin_world::item::ItemStack;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use pumpkin_util::Hand;
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
                            pumpkin::plugin::player::player_join::PlayerJoinEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_join::PlayerJoinEvent>,
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
                            pumpkin::plugin::player::player_login::PlayerLoginEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_login::PlayerLoginEvent>,
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
                "org.bukkit.event.player.AsyncPlayerPreLoginEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent>,
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
                            pumpkin::plugin::player::player_leave::PlayerLeaveEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_leave::PlayerLeaveEvent>,
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
                            pumpkin::plugin::player::player_move::PlayerMoveEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_move::PlayerMoveEvent>,
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
                "org.bukkit.event.player.PlayerAdvancementDoneEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent,
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
                "org.bukkit.event.player.PlayerAnimationEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_animation::PlayerAnimationEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_animation::PlayerAnimationEvent,
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
                "org.bukkit.event.player.PlayerArmorStandManipulateEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent,
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
                            pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent,
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
                "org.bukkit.event.player.PlayerBedLeaveEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent,
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
                            pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent,
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
                            pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent,
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
                "org.bukkit.event.player.PlayerBucketEntityEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent,
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
                "org.bukkit.event.player.PlayerChangedMainHandEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent,
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
                "org.bukkit.event.player.PlayerRegisterChannelEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent,
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
                "org.bukkit.event.player.PlayerUnregisterChannelEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent,
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
                            pumpkin::plugin::player::player_teleport::PlayerTeleportEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_teleport::PlayerTeleportEvent>,
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
                            pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent,
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
                            pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent,
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
                "org.bukkit.event.player.AsyncPlayerChatEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_chat::PlayerChatEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_chat::PlayerChatEvent>,
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
                "org.bukkit.event.player.PlayerCommandPreprocessEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent,
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
                            pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent,
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
                            pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent,
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
                "org.bukkit.event.player.PlayerEditBookEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent,
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
                "org.bukkit.event.player.PlayerEggThrowEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent,
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
                            pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent,
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
                            pumpkin::plugin::player::player_interact_event::PlayerInteractEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_interact_event::PlayerInteractEvent,
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
                            pumpkin::plugin::block::block_break::BlockBreakEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_break::BlockBreakEvent>,
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
                            pumpkin::plugin::block::block_place::BlockPlaceEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_place::BlockPlaceEvent>,
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
                "org.bukkit.event.entity.EntitySpawnEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent>,
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
                "org.bukkit.event.entity.EntityDamageEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::entity::entity_damage::EntityDamageEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::entity::entity_damage::EntityDamageEvent>,
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
                "org.bukkit.event.entity.EntityDeathEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::entity::entity_death::EntityDeathEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::entity::entity_death::EntityDeathEvent>,
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
                            pumpkin::plugin::server::server_command::ServerCommandEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::server::server_command::ServerCommandEvent,
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
                            pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent,
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
                    log::warn!(
                        "Unsupported Bukkit event type '{}' from plugin '{}'",
                        request.event_type, request.plugin_name
                    );
                }
            }
        });
    });

    Some(())
}

pub fn ffi_native_bridge_call_event_impl(request: CallEventRequest) -> Option<CallEventResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let event = request.event?;
    log::debug!("Java calling event {:?}", event);

    let context = ctx.plugin_context.clone();

    let handled = tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            match event.data? {
                Data::PlayerJoin(player_join_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_join_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerJoinEvent::new(
                        player,
                        TextComponent::from_legacy_string(&player_join_event_data.join_message),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerLogin(player_login_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_login_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerLoginEvent::new(
                        player,
                        TextComponent::from_legacy_string(&player_login_event_data.kick_message),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::AsyncPlayerPreLogin(player_pre_login_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_pre_login_event_data.player_uuid?.value).ok()?;
                    let pumpkin_event = PlayerPreLoginEvent::new(
                        player_pre_login_event_data.name,
                        uuid,
                        player_pre_login_event_data.address,
                        player_pre_login_event_data.result,
                        player_pre_login_event_data.kick_message,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerLeave(player_leave_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_leave_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerLeaveEvent::new(
                        player,
                        TextComponent::from_legacy_string(&player_leave_event_data.leave_message),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerMove(player_move_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_move_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let from = player_move_event_data.from?.position?;
                    let to = player_move_event_data.to?.position?;
                    let pumpkin_event = PlayerMoveEvent::new(
                        player,
                        Vector3::new(from.x, from.y, from.z),
                        Vector3::new(to.x, to.y, to.z),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerAdvancementDone(player_advancement_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_advancement_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerAdvancementDoneEvent::new(
                        player,
                        player_advancement_event_data.advancement_key,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerAnimation(player_animation_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_animation_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerAnimationEvent::new(
                        player,
                        player_animation_event_data.animation_type,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerArmorStandManipulate(player_armor_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_armor_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let armor_uuid = uuid::Uuid::parse_str(
                        &player_armor_event_data.armor_stand_uuid?.value,
                    )
                    .ok()?;
                    let pumpkin_event = PlayerArmorStandManipulateEvent::new(
                        player,
                        armor_uuid,
                        player_armor_event_data.item_key,
                        player_armor_event_data.armor_stand_item_key,
                        player_armor_event_data.slot,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBedEnter(player_bed_enter_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bed_enter_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bed_enter_event_data
                        .bed_location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let pumpkin_event = PlayerBedEnterEvent::new(player, position);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBedLeave(player_bed_leave_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bed_leave_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bed_leave_event_data
                        .bed_location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let pumpkin_event = PlayerBedLeaveEvent::new(player, position);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBucketEmpty(player_bucket_empty_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bucket_empty_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bucket_empty_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let face = bukkit_block_face_to_direction(
                        &player_bucket_empty_event_data.block_face,
                    );
                    let pumpkin_event = PlayerBucketEmptyEvent::new(
                        player,
                        position,
                        player_bucket_empty_event_data.block_key,
                        face,
                        player_bucket_empty_event_data.bucket_item_key,
                        player_bucket_empty_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBucketFill(player_bucket_fill_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bucket_fill_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bucket_fill_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let face = bukkit_block_face_to_direction(
                        &player_bucket_fill_event_data.block_face,
                    );
                    let pumpkin_event = PlayerBucketFillEvent::new(
                        player,
                        position,
                        player_bucket_fill_event_data.block_key,
                        face,
                        player_bucket_fill_event_data.bucket_item_key,
                        player_bucket_fill_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBucketEntity(player_bucket_entity_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bucket_entity_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let entity_uuid =
                        uuid::Uuid::parse_str(&player_bucket_entity_event_data.entity_uuid?.value).ok()?;
                    let pumpkin_event = PlayerBucketEntityEvent::new(
                        player,
                        entity_uuid,
                        player_bucket_entity_event_data.entity_type,
                        player_bucket_entity_event_data.original_bucket_key,
                        player_bucket_entity_event_data.entity_bucket_key,
                        player_bucket_entity_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChangedMainHand(player_changed_main_hand_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_changed_main_hand_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let main_hand = main_hand_from_bukkit(
                        &player_changed_main_hand_event_data.main_hand,
                    )
                    .unwrap_or(Hand::Right);
                    let pumpkin_event = PlayerChangedMainHandEvent::new(player, main_hand);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerRegisterChannel(player_register_channel_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_register_channel_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerRegisterChannelEvent::new(
                        player,
                        player_register_channel_event_data.channel,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerUnregisterChannel(player_unregister_channel_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_unregister_channel_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerUnregisterChannelEvent::new(
                        player,
                        player_unregister_channel_event_data.channel,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerTeleport(player_teleport_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_teleport_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let from = player_teleport_event_data.from?.position?;
                    let to = player_teleport_event_data.to?.position?;
                    let pumpkin_event = PlayerTeleportEvent::new(
                        player,
                        Vector3::new(from.x, from.y, from.z),
                        Vector3::new(to.x, to.y, to.z),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChangeWorld(player_change_world_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_change_world_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let prev_uuid = uuid::Uuid::parse_str(
                        &player_change_world_event_data.previous_world?.uuid?.value,
                    )
                    .ok()?;
                    let new_uuid = uuid::Uuid::parse_str(
                        &player_change_world_event_data.new_world?.uuid?.value,
                    )
                    .ok()?;
                    let prev_world = context
                        .server
                        .worlds
                        .load()
                        .iter()
                        .find(|world| world.uuid == prev_uuid)?
                        .clone();
                    let new_world = context
                        .server
                        .worlds
                        .load()
                        .iter()
                        .find(|world| world.uuid == new_uuid)?
                        .clone();
                    let position = player_change_world_event_data
                        .position
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let pumpkin_event = PlayerChangeWorldEvent::new(
                        player,
                        prev_world,
                        new_world,
                        position,
                        player_change_world_event_data.yaw,
                        player_change_world_event_data.pitch,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerGamemodeChange(player_gamemode_change_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_gamemode_change_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let prev = gamemode_from_bukkit(&player_gamemode_change_event_data.previous_gamemode)
                        .unwrap_or(player.gamemode.load());
                    let next = gamemode_from_bukkit(&player_gamemode_change_event_data.new_gamemode)
                        .unwrap_or(player.gamemode.load());
                    let pumpkin_event = PlayerGamemodeChangeEvent::new(player, prev, next);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChat(player_chat_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_chat_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let mut recipients = Vec::new();
                    if player_chat_event_data.recipients.is_empty() {
                        recipients = context.server.get_all_players();
                    } else {
                        for recipient in player_chat_event_data.recipients {
                            if let Ok(uuid) = uuid::Uuid::parse_str(&recipient.value) {
                                if let Some(recipient_player) =
                                    context.server.get_player_by_uuid(uuid)
                                {
                                    recipients.push(recipient_player);
                                }
                            }
                        }
                    }

                    let pumpkin_event = PlayerChatEvent::new(
                        player,
                        player_chat_event_data.message,
                        recipients,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerCommand(player_command_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_command_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event =
                        PlayerCommandPreprocessEvent::new(player, player_command_event_data.command);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerCommandSend(player_command_send_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_command_send_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event =
                        PlayerCommandSendEvent::new(player, player_command_send_event_data.commands);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerDropItem(player_drop_item_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_drop_item_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let item_uuid =
                        uuid::Uuid::parse_str(&player_drop_item_event_data.item_uuid?.value).ok()?;
                    let item_stack = item_stack_from_key(
                        &player_drop_item_event_data.item_key,
                        player_drop_item_event_data.item_amount,
                    );
                    let pumpkin_event =
                        PlayerDropItemEvent::new(player, item_uuid, item_stack);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerEditBook(player_edit_book_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_edit_book_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let title = if player_edit_book_event_data.title.is_empty() {
                        None
                    } else {
                        Some(player_edit_book_event_data.title)
                    };
                    let pumpkin_event = PlayerEditBookEvent::new(
                        player,
                        player_edit_book_event_data.slot,
                        player_edit_book_event_data.pages,
                        title,
                        player_edit_book_event_data.is_signing,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerEggThrow(player_egg_throw_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_egg_throw_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let egg_uuid =
                        uuid::Uuid::parse_str(&player_egg_throw_event_data.egg_uuid?.value).ok()?;
                    let pumpkin_event = PlayerEggThrowEvent::new(
                        player,
                        egg_uuid,
                        player_egg_throw_event_data.hatching,
                        player_egg_throw_event_data.num_hatches.min(u8::MAX as i32) as u8,
                        player_egg_throw_event_data.hatching_type,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerExpChange(player_exp_change_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_exp_change_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event =
                        PlayerExpChangeEvent::new(player, player_exp_change_event_data.amount);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerInteract(player_interact_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_interact_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let action = match player_interact_event_data.action.as_str() {
                        "LEFT_CLICK_BLOCK" => InteractAction::LeftClickBlock,
                        "LEFT_CLICK_AIR" => InteractAction::LeftClickAir,
                        "RIGHT_CLICK_BLOCK" => InteractAction::RightClickBlock,
                        "RIGHT_CLICK_AIR" => InteractAction::RightClickAir,
                        _ => InteractAction::RightClickAir,
                    };

                    let clicked_pos = player_interact_event_data
                        .clicked
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        });

                    let block = block_from_key(&player_interact_event_data.block_key);
                    let item = Arc::new(Mutex::new(item_from_key(
                        &player_interact_event_data.item_key,
                    )));
                    let face = bukkit_block_face_to_direction(
                        &player_interact_event_data.block_face,
                    );

                    let pumpkin_event = PlayerInteractEvent::new(
                        &player,
                        action,
                        &item,
                        player_interact_event_data.item_key,
                        block,
                        clicked_pos,
                        face,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockBreak(block_break_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_break_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid);
                    let block = block_from_key(&block_break_event_data.block_key);
                    let position = block_break_event_data
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

                    let pumpkin_event = BlockBreakEvent::new(
                        player,
                        block,
                        position,
                        block_break_event_data.exp,
                        block_break_event_data.drop,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockPlace(block_place_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_place_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_place_event_data.block_key);
                    let against = block_from_key(&block_place_event_data.block_against_key);

                    let pumpkin_event = BlockPlaceEvent {
                        player,
                        block_placed: block,
                        block_placed_against: against,
                        can_build: block_place_event_data.can_build,
                        cancelled: false,
                    };

                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::EntitySpawn(entity_spawn_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&entity_spawn_event_data.entity_uuid?.value)
                        .ok()?;
                    let pumpkin_event =
                        pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent::new(
                            uuid,
                            &pumpkin_data::entity::EntityType::PLAYER,
                        );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::EntityDamage(entity_damage_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&entity_damage_event_data.entity_uuid?.value)
                        .ok()?;
                    let pumpkin_event =
                        pumpkin::plugin::entity::entity_damage::EntityDamageEvent::new(
                            uuid,
                            entity_damage_event_data.damage,
                            pumpkin_data::damage::DamageType::GENERIC,
                        );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::EntityDeath(entity_death_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&entity_death_event_data.entity_uuid?.value)
                        .ok()?;
                    let pumpkin_event =
                        pumpkin::plugin::entity::entity_death::EntityDeathEvent::new(
                            uuid,
                            pumpkin_data::damage::DamageType::GENERIC,
                        );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::ServerCommand(server_command_event_data) => {
                    let pumpkin_event =
                        ServerCommandEvent::new(server_command_event_data.command);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::ServerBroadcast(server_broadcast_event_data) => {
                    let message = serde_json::from_str(&server_broadcast_event_data.message)
                        .unwrap_or_else(|_| {
                            TextComponent::from_legacy_string(&server_broadcast_event_data.message)
                        });
                    let sender = serde_json::from_str(&server_broadcast_event_data.sender)
                        .unwrap_or_else(|_| {
                            TextComponent::from_legacy_string(&server_broadcast_event_data.sender)
                        });
                    let pumpkin_event = ServerBroadcastEvent::new(message, sender);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
            }
        })
    })?;

    Some(CallEventResponse { handled })
}

fn block_from_key(key: &str) -> &'static Block {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    Block::from_registry_key(trimmed).unwrap_or(&Block::AIR)
}

fn item_from_key(key: &str) -> ItemStack {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    let item = Item::from_registry_key(trimmed).unwrap_or(&Item::AIR);
    ItemStack::new(1, item)
}

fn item_stack_from_key(key: &str, amount: i32) -> ItemStack {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    let item = Item::from_registry_key(trimmed).unwrap_or(&Item::AIR);
    let count = amount.clamp(0, u8::MAX as i32) as u8;
    ItemStack::new(count, item)
}

fn bukkit_block_face_to_direction(face: &str) -> Option<BlockDirection> {
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

fn gamemode_from_bukkit(mode: &str) -> Option<pumpkin_util::GameMode> {
    match mode {
        "SURVIVAL" => Some(pumpkin_util::GameMode::Survival),
        "CREATIVE" => Some(pumpkin_util::GameMode::Creative),
        "ADVENTURE" => Some(pumpkin_util::GameMode::Adventure),
        "SPECTATOR" => Some(pumpkin_util::GameMode::Spectator),
        _ => None,
    }
}

fn main_hand_from_bukkit(hand: &str) -> Option<Hand> {
    match hand {
        "LEFT" => Some(Hand::Left),
        "RIGHT" => Some(Hand::Right),
        _ => None,
    }
}
