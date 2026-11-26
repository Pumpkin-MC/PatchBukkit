use super::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::common::Uuid as ProtoUuid;
use pumpkin::entity::player::Player;
use std::cell::Cell;
use std::collections::HashMap;
use std::ffi::{CStr, c_char};
use std::sync::{Arc, RwLock};

static PLAYER_HANDLE_CACHE: RwLock<Option<HashMap<uuid::Uuid, Arc<Player>>>> = RwLock::new(None);

thread_local! {
    static NATIVE_BRIDGE_WRITE_DEPTH: Cell<u32> = const { Cell::new(0) };
}

pub(crate) struct NativeBridgeWriteGuard;

impl NativeBridgeWriteGuard {
    pub(crate) fn enter() -> Self {
        NATIVE_BRIDGE_WRITE_DEPTH.with(|d| d.set(d.get().saturating_add(1)));
        Self
    }
}

impl Drop for NativeBridgeWriteGuard {
    fn drop(&mut self) {
        NATIVE_BRIDGE_WRITE_DEPTH.with(|d| d.set(d.get().saturating_sub(1)));
    }
}

#[must_use]
pub(crate) fn is_in_native_bridge_write() -> bool {
    NATIVE_BRIDGE_WRITE_DEPTH.with(|d| d.get() > 0)
}

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

pub fn remove_cached_player(player_uuid: &uuid::Uuid) {
    if let Ok(mut write_guard) = PLAYER_HANDLE_CACHE.write()
        && let Some(ref mut cache) = *write_guard
    {
        cache.remove(player_uuid);
    }
}

pub fn with_player_uuid<F, R>(player_uuid: uuid::Uuid, f: F) -> Option<R>
where
    F: FnOnce(Arc<Player>) -> R,
{
    let ctx = CALLBACK_CONTEXT.get()?;

    if let Some(player) = ctx.plugin_context.server.get_player_by_uuid(player_uuid) {
        return Some(f(player));
    }

    // Release the read guard before calling `f`: it may send packets or fire events,
    // which call `cache_player` (write lock) and would deadlock this thread.
    cached_player(player_uuid).map(f)
}

fn cached_player(player_uuid: uuid::Uuid) -> Option<Arc<Player>> {
    PLAYER_HANDLE_CACHE
        .read()
        .ok()?
        .as_ref()?
        .get(&player_uuid)
        .cloned()
}

pub fn with_player<F, R>(proto_uuid: Option<&ProtoUuid>, f: F) -> Option<R>
where
    F: FnOnce(Arc<Player>) -> R,
{
    let uuid_str = &proto_uuid?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    with_player_uuid(player_uuid, f)
}
