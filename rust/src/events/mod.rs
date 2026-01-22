use std::sync::Arc;

use pumpkin::plugin::{Context, EventPriority};

use crate::events::join::PatchBukkitJoinHandler;

pub mod join;

pub async fn register_handlers(server: &Arc<Context>) {
    server
        .register_event(
            Arc::new(PatchBukkitJoinHandler),
            EventPriority::Highest,
            true,
        )
        .await;
}
