use std::ffi::{CString, c_char};

use pumpkin::command::args::entities::{EntitySelectorType, TargetSelector};

use crate::java::native_callbacks::{CALLBACK_CONTEXT, utils::get_string};

pub extern "C" fn rust_get_world(entity_uuid_ptr: *const c_char) -> *mut c_char {
    if entity_uuid_ptr.is_null() {
        return std::ptr::null_mut();
    }

    let entity_uuid = get_string(entity_uuid_ptr);

    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        return std::ptr::null_mut();
    };

    let Ok(uuid) = uuid::Uuid::parse_str(&entity_uuid) else {
        return std::ptr::null_mut();
    };

    let entities = ctx
        .plugin_context
        .server
        .select_entities(&TargetSelector::new(EntitySelectorType::Uuid(uuid)), None);

    if entities.len() != 1 {
        return std::ptr::null_mut();
    }

    let entity = entities.first().unwrap().get_entity();
    let world = entity.world.load();
    let world_uuid = world.uuid.to_string();

    match CString::new(world_uuid) {
        Ok(cstring) => cstring.into_raw(), // Transfers ownership to caller
        Err(_) => std::ptr::null_mut(),
    }
}
