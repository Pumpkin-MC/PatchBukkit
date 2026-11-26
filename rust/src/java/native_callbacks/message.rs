use pumpkin_util::text::TextComponent;

use crate::{
    java::native_callbacks::utils::with_player, proto::patchbukkit::message::SendMessageRequest,
};

pub fn ffi_native_bridge_send_message_impl(request: SendMessageRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.send_system_message(&TextComponent::from_legacy_string(&request.message));
    });

    Some(())
}
