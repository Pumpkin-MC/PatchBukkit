use std::ffi::c_char;
use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
use pumpkin_util::text::TextComponent;

use crate::events::handler::PatchBukkitEventHandler;
use crate::java::native_callbacks::{CALLBACK_CONTEXT, utils::get_string};

pub extern "C" fn rust_register_event(
    event_type_ptr: *const c_char,
    plugin_name_ptr: *const c_char,
    priority: i32,
    blocking: bool,
) {
    let event_type = get_string(event_type_ptr);
    let plugin_name = get_string(plugin_name_ptr);

    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        log::error!("CallbackContext not initialized when registering event");
        return;
    };

    let pumpkin_priority = match priority {
        0 => EventPriority::Lowest,
        1 => EventPriority::Low,
        2 => EventPriority::Normal,
        3 => EventPriority::High,
        _ => EventPriority::Highest,
    };

    log::info!(
        "Plugin '{plugin_name}' registering listener for '{event_type}' (priority={priority:?}, blocking={blocking})"
    );

    let command_tx = ctx.command_tx.clone();
    let context = ctx.plugin_context.clone();
    let event_type_owned = event_type;

    tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            match event_type_owned.as_str() {
                "org.bukkit.event.player.PlayerJoinEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_join::PlayerJoinEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_join::PlayerJoinEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            blocking,
                        )
                        .await;
                }
                _ => {
                    log::warn!(
                        "Unsupported Bukkit event type '{event_type_owned}' from plugin '{plugin_name}'"
                    );
                }
            }
        });
    });
}

pub extern "C" fn rust_call_event(
    event_type_ptr: *const c_char,
    event_data_ptr: *const c_char,
) -> bool {
    let event_type = get_string(event_type_ptr);
    let event_data_json = get_string(event_data_ptr);

    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        log::error!("CallbackContext not initialized when calling event");
        return false;
    };

    log::debug!("Java calling event '{event_type}' with data: {event_data_json}");

    let event_data: serde_json::Value = match serde_json::from_str(&event_data_json) {
        Ok(v) => v,
        Err(e) => {
            log::error!("Failed to parse event data JSON: {e}");
            return false;
        }
    };

    let context = ctx.plugin_context.clone();

    tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            if event_type.as_str() == "org.bukkit.event.player.PlayerJoinEvent" {
                let player_uuid_str = event_data["playerUuid"].as_str().unwrap_or("");
                let join_message_str = event_data["joinMessage"].as_str().unwrap_or("");
                if let Ok(uuid) = uuid::Uuid::parse_str(player_uuid_str)
                    && let Some(player) = context.server.get_player_by_uuid(uuid)
                {
                    let pumpkin_event = PlayerJoinEvent::new(
                        player,
                        TextComponent::from_legacy_string(join_message_str),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    return true;
                }
                false
            } else {
                log::warn!("Unknown event type for Pumpkin: {event_type}");
                false
            }
        })
    })
}
