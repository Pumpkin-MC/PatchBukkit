use pumpkin_util::text::TextComponent;

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT, proto::patchbukkit::message::SendMessageRequest,
};

pub fn ffi_native_bridge_send_message_impl(request: SendMessageRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    // Never unwrap here: a panic would unwind across the FFI boundary into
    // the JVM, which is undefined behavior and kills the whole server.
    // A malformed UUID simply means "no such player".
    let player_uuid = uuid::Uuid::parse_str(&request.uuid?.value).ok()?;

    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid);
    if let Some(player) = player {
        // Prefer the structured form so click/hover interactions survive;
        // fall back to legacy text when it is absent or unparseable.
        let text = if request.message_json.is_empty() {
            TextComponent::from_legacy_string(&request.message)
        } else {
            serde_json::from_str::<TextComponent>(&request.message_json)
                .unwrap_or_else(|_| TextComponent::from_legacy_string(&request.message))
        };
        player.send_system_message(&text);
    }

    Some(())
}
