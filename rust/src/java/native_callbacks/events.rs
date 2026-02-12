use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
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

    tracing::info!(
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
                    tracing::warn!(
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
    tracing::debug!("Java calling event {:?}", event);

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
            }
        })
    })?;

    Some(CallEventResponse { handled })
}
