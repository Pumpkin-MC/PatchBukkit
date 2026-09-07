use super::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::common::Uuid as ProtoUuid;
use pumpkin::entity::player::Player;
use std::collections::HashMap;
use std::ffi::{CStr, c_char};
use std::sync::{Arc, RwLock};

static PLAYER_HANDLE_CACHE: RwLock<Option<HashMap<uuid::Uuid, Arc<Player>>>> = RwLock::new(None);

#[must_use]
pub fn get_string(str_ptr: *const c_char) -> String {
    unsafe { CStr::from_ptr(str_ptr).to_string_lossy().into_owned() }
}

pub fn cache_player(player: Arc<Player>) {
    let player_uuid = player.gameprofile.id;
    if let Ok(mut write_guard) = PLAYER_HANDLE_CACHE.write() {
        let cache = write_guard.get_or_insert_with(HashMap::new);
        cache.insert(player_uuid, player);
    }
}

pub fn with_player<F, R>(proto_uuid: Option<&ProtoUuid>, f: F) -> Option<R>
where
    F: FnOnce(Arc<Player>) -> R,
{
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &proto_uuid?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    // Always resolve the live player first. A cached `Arc<Player>` outlives
    // disconnects (quit/kick): the Java side unregisters the player while the
    // Rust cache would keep the dead handle alive and keep operating on a
    // disconnected client, whereas `get_player_by_uuid` correctly reports
    // them as gone. The cache is only refreshed from live lookups, and
    // entries for players that are no longer online are evicted.
    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid);
    if let Ok(mut write_guard) = PLAYER_HANDLE_CACHE.write() {
        match &player {
            Some(live) => {
                write_guard
                    .get_or_insert_with(HashMap::new)
                    .insert(player_uuid, live.clone());
            }
            None => {
                if let Some(cache) = write_guard.as_mut() {
                    cache.remove(&player_uuid);
                }
            }
        }
    }

    Some(f(player?))
}
