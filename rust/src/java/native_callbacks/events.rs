use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
use pumpkin_util::text::TextComponent;

use crate::events::handler::PatchBukkitEventHandler;
use crate::java::native_callbacks::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::events::call_event_request::EventData;
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
    let event_data = request.event_data?;
    log::debug!("Java calling event {:?}", event_data);

    let context = ctx.plugin_context.clone();

    tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            match event_data {
                EventData::PlayerJoin(player_join_event_data) => {
                    if let Ok(uuid) =
                        uuid::Uuid::parse_str(&player_join_event_data.player_uuid?.value)
                    {
                        if let Some(player) = context.server.get_player_by_uuid(uuid) {
                            let pumpkin_event = PlayerJoinEvent::new(
                                player,
                                TextComponent::from_legacy_string(
                                    &player_join_event_data.join_message,
                                ),
                            );
                            context.server.plugin_manager.fire(pumpkin_event).await;
                            return Some(true);
                        }
                    }
                    Some(false)
                }
                false
            } else {
                log::warn!("Unknown event type for Pumpkin: {event_type}");
                false
            }
        })
    });

    let mut response = CallEventResponse::default();
    response.handled = handled?;
    Some(response)
}
