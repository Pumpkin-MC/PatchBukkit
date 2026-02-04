use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin::plugin::player::player_chat::PlayerChatEvent;
use pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent;
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
use pumpkin::plugin::player::player_leave::PlayerLeaveEvent;
use pumpkin::plugin::player::player_move::PlayerMoveEvent;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;

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
                        PlayerCommandSendEvent::new(player, player_command_event_data.command);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
            }
        })
    })?;

    Some(CallEventResponse { handled })
}
