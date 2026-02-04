use pumpkin::command::args::entities::{EntitySelectorType, TargetSelector};

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::common::{Location, Uuid, Vec3, World},
};

#[repr(C)]
pub struct Vec3FFI {
    pub x: f64,
    pub y: f64,
    pub z: f64,
}

pub fn ffi_native_bridge_get_location_impl(entity_uuid: Uuid) -> Option<Location> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&entity_uuid.value).ok()?;

    let entity = ctx
        .plugin_context
        .server
        .select_entities(&TargetSelector::new(EntitySelectorType::Uuid(uuid)), None);

    if entity.len() == 1 {
        let entity = entity.first().unwrap().get_entity();
        let position = entity.pos.load();
        let world = entity.world.load().uuid;
        let yaw = entity.yaw.load();
        let pitch = entity.pitch.load();

        let mut response_position = Vec3::default();
        response_position.x = position.x;
        response_position.y = position.y;
        response_position.z = position.z;

        let mut world_uuid = Uuid::default();
        world_uuid.value = world.to_string();

        let mut response_world = World::default();
        response_world.uuid = Some(world_uuid);

        let mut location = Location::default();
        location.pitch = pitch;
        location.position = Some(response_position);
        location.world = Some(response_world);
        location.yaw = yaw;

        return Some(location);
    }

    None
}
