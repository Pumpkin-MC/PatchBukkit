use std::sync::Arc;

use pumpkin::{
    plugin::{BoxFuture, EventHandler, player::player_join::PlayerJoinEvent},
    server::Server,
};
use pumpkin_api_macros::with_runtime;
use pumpkin_util::text::{TextComponent, color::NamedColor};

pub struct PatchBukkitJoinHandler;

#[with_runtime(global)]
impl EventHandler<PlayerJoinEvent> for PatchBukkitJoinHandler {
    fn handle_blocking<'a>(
        &self,
        _server: &Arc<Server>,
        event: &'a mut PlayerJoinEvent,
    ) -> BoxFuture<'a, ()> {
        Box::pin(async {
            event.join_message =
                TextComponent::text(format!("Welcome, {}!", event.player.gameprofile.name))
                    .color_named(NamedColor::Green);
        })
    }
}
