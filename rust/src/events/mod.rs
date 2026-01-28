use std::sync::Arc;

use pumpkin::plugin::{Context, EventPriority, player::player_join::PlayerJoinEvent};
use tokio::sync::mpsc;

use crate::{events::join::PatchBukkitJoinHandler, java::jvm::commands::JvmCommand};

pub mod join;

pub enum Event {
    PlayerJoinEvent(PlayerJoinEvent),
}

pub async fn register_handlers(command_tx: mpsc::Sender<JvmCommand>, server: &Arc<Context>) {
    server
        .register_event(
            Arc::new(PatchBukkitJoinHandler { command_tx }),
            EventPriority::Highest,
            true,
        )
        .await;
}
