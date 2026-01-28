use std::sync::Arc;

use pumpkin::{
    plugin::{BoxFuture, EventHandler, player::player_join::PlayerJoinEvent},
    server::Server,
};
use pumpkin_api_macros::with_runtime;
use tokio::sync::{mpsc, oneshot};

use crate::{events::Event, java::jvm::commands::JvmCommand};

pub struct PatchBukkitJoinHandler {
    pub command_tx: mpsc::Sender<JvmCommand>,
}

#[with_runtime(global)]
impl EventHandler<PlayerJoinEvent> for PatchBukkitJoinHandler {
    fn handle_blocking<'a>(
        &self,
        _server: &Arc<Server>,
        event: &'a mut PlayerJoinEvent,
    ) -> BoxFuture<'a, ()> {
        let event: &mut PlayerJoinEvent = event;

        let (tx, rx) = oneshot::channel();
        let sent_event = event.clone();
        self.command_tx
            .blocking_send(JvmCommand::TriggerEvent {
                event: Event::PlayerJoinEvent(sent_event),
                respond_to: tx,
            })
            .unwrap();

        match rx.await.unwrap().unwrap() {
            Event::PlayerJoinEvent(player_join_event) => *event = player_join_event,
            _ => unreachable!(),
        };

        Box::pin(async {})
    }
}
