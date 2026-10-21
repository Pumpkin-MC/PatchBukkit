use std::sync::atomic::{AtomicBool, AtomicUsize, Ordering};

use pumpkin_util::math::{position::BlockPos, vector3::Vector3};

use crate::java::native_callbacks::{CALLBACK_CONTEXT, utils::with_player_uuid};

/// Whether any virtual Netty channel on the Java side has custom pipeline handlers
/// (e.g. PacketEvents / ProtocolLib) that require forwarding inbound/outbound packets.
pub static PACKET_INTERCEPTION_ENABLED: AtomicBool = AtomicBool::new(false);

/// Panama FFM upcall stub address for `VirtualChannelManager.handlePacketUpcall`.
static PACKET_UPCALL_ADDR: AtomicUsize = AtomicUsize::new(0);

/// Panama FFM upcall stub address for `SchedulerBridge.onServerTick`.
static HEARTBEAT_UPCALL_ADDR: AtomicUsize = AtomicUsize::new(0);

type PacketUpcallFn = unsafe extern "C" fn(
    uuid_msb: i64,
    uuid_lsb: i64,
    packet_id: i32,
    payload_ptr: i64,
    payload_len: i32,
    is_outbound: i32,
) -> i32;

type HeartbeatUpcallFn = unsafe extern "C" fn();

/// Fast-path FFI entrypoint to toggle Rust-side packet event dispatch (`PacketReceivedEvent` / `PacketSentEvent`).
#[unsafe(no_mangle)]
pub extern "C" fn ffi_fast_set_packet_interception(enabled: i32) {
    PACKET_INTERCEPTION_ENABLED.store(enabled != 0, Ordering::Relaxed);
}

/// Fast-path FFI entrypoint called by `NativeFastPathFfi.init` to register Panama FFM upcall stubs.
#[unsafe(no_mangle)]
pub extern "C" fn ffi_fast_register_upcalls(packet_upcall_addr: i64, heartbeat_upcall_addr: i64) {
    PACKET_UPCALL_ADDR.store(packet_upcall_addr as usize, Ordering::Release);
    HEARTBEAT_UPCALL_ADDR.store(heartbeat_upcall_addr as usize, Ordering::Release);
}

/// Invokes the Java `VirtualChannelManager.handlePacketUpcall` stub directly via Panama FFM
/// without JNI method lookup, `JByteArray` allocation, or Protobuf serialization.
/// Returns `true` if the upcall stub was registered and invoked.
pub fn try_dispatch_packet_upcall(
    player_uuid: uuid::Uuid,
    packet_id: i32,
    payload: &[u8],
    is_outbound: bool,
) -> bool {
    try_dispatch_packet_upcall_result(player_uuid, packet_id, payload, is_outbound).is_some()
}

/// Like [`try_dispatch_packet_upcall`] but returns the Java result: for outbound packets `1`
/// means the Java pipeline took ownership and will write the packet out itself, in order.
/// `None` if the upcall stub is not registered.
pub fn try_dispatch_packet_upcall_result(
    player_uuid: uuid::Uuid,
    packet_id: i32,
    payload: &[u8],
    is_outbound: bool,
) -> Option<i32> {
    let addr = PACKET_UPCALL_ADDR.load(Ordering::Acquire);
    if addr == 0 {
        return None;
    }
    let (msb, lsb) = player_uuid.as_u64_pair();
    let func: PacketUpcallFn = unsafe { std::mem::transmute(addr) };
    Some(unsafe {
        func(
            msb as i64,
            lsb as i64,
            packet_id,
            payload.as_ptr() as i64,
            payload.len() as i32,
            i32::from(is_outbound),
        )
    })
}

/// Invokes `SchedulerBridge.onServerTick` directly via a Panama FFM upcall stub if registered.
pub fn try_dispatch_heartbeat_upcall() -> bool {
    let addr = HEARTBEAT_UPCALL_ADDR.load(Ordering::Acquire);
    if addr == 0 {
        return false;
    }
    let func: HeartbeatUpcallFn = unsafe { std::mem::transmute(addr) };
    unsafe {
        func();
    }
    true
}

/// Fast-path FFI entrypoint to read a block state ID (`u16` as `i32`, or `-1` on error)
/// without Protobuf serialization or String formatting.
#[unsafe(no_mangle)]
pub extern "C" fn ffi_fast_get_block_state_id(
    world_msb: i64,
    world_lsb: i64,
    x: i32,
    y: i32,
    z: i32,
) -> i32 {
    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        return -1;
    };
    let world_uuid = uuid::Uuid::from_u64_pair(world_msb as u64, world_lsb as u64);
    let worlds = ctx.plugin_context.server.worlds.load();
    let Some(world) = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .or_else(|| worlds.first())
    else {
        return -1;
    };

    let pos = BlockPos::new(x, y, z);
    i32::from(world.get_block_state(&pos).id.as_u16())
}

/// Fast-path FFI entrypoint to write a block state ID (`u16`) directly
/// without Protobuf serialization or String parsing.
#[unsafe(no_mangle)]
pub extern "C" fn ffi_fast_set_block_state_id(
    world_msb: i64,
    world_lsb: i64,
    x: i32,
    y: i32,
    z: i32,
    state_id: i32,
    apply_physics: i32,
) -> i32 {
    if !(0..=65535).contains(&state_id) {
        return 0;
    }
    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        return 0;
    };
    let world_uuid = uuid::Uuid::from_u64_pair(world_msb as u64, world_lsb as u64);
    let worlds = ctx.plugin_context.server.worlds.load();
    let Some(world) = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .or_else(|| worlds.first())
    else {
        return 0;
    };

    let pos = BlockPos::new(x, y, z);
    let id = pumpkin_data::BlockStateId::new_or_air(state_id as u16);
    let flags = if apply_physics != 0 {
        pumpkin::world::BlockFlags::NOTIFY_ALL
    } else {
        pumpkin::world::BlockFlags::NOTIFY_LISTENERS
    };
    let (chunk_coordinate, _) = pos.chunk_and_chunk_relative_position();
    if !world.level.is_chunk_loaded(&chunk_coordinate) {
        ctx.runtime
            .block_on(world.level.get_or_fetch_chunk(chunk_coordinate, |_| ()));
    }
    world.set_block_state(&pos, id, flags);
    1
}

/// Fast-path FFI entrypoint to teleport an entity or player by UUID (`msb`, `lsb`).
#[unsafe(no_mangle)]
pub extern "C" fn ffi_fast_teleport_entity(
    entity_msb: i64,
    entity_lsb: i64,
    world_msb: i64,
    world_lsb: i64,
    x: f64,
    y: f64,
    z: f64,
    yaw: f32,
    pitch: f32,
) -> i32 {
    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        return 0;
    };
    let entity_uuid = uuid::Uuid::from_u64_pair(entity_msb as u64, entity_lsb as u64);
    let position = Vector3::new(x, y, z);

    if let Some(()) = with_player_uuid(entity_uuid, |player| {
        player.living_entity.entity.pos.store(position);
        player.living_entity.entity.yaw.store(yaw);
        player.living_entity.entity.pitch.store(pitch);
        let target_world = if world_msb != 0 || world_lsb != 0 {
            let world_uuid = uuid::Uuid::from_u64_pair(world_msb as u64, world_lsb as u64);
            ctx.plugin_context
                .server
                .worlds
                .load()
                .iter()
                .find(|w| w.uuid == world_uuid)
                .cloned()
                .unwrap_or_else(|| player.living_entity.entity.world.load_full())
        } else {
            player.living_entity.entity.world.load_full()
        };
        player
            .living_entity
            .entity
            .teleport(position, Some(yaw), Some(pitch), &target_world);
    }) {
        return 1;
    }

    let worlds = ctx.plugin_context.server.worlds.load();
    for world in worlds.iter() {
        if let Some(entity) = world.get_entity_by_uuid(entity_uuid) {
            let base = entity.get_entity();
            base.pos.store(position);
            base.yaw.store(yaw);
            base.pitch.store(pitch);
            base.teleport(position, Some(yaw), Some(pitch), world);
            return 1;
        }
    }

    0
}

/// Fast-path FFI entrypoint to update an entity's or player's velocity by UUID (`msb`, `lsb`).
#[unsafe(no_mangle)]
pub extern "C" fn ffi_fast_set_entity_velocity(
    entity_msb: i64,
    entity_lsb: i64,
    vx: f64,
    vy: f64,
    vz: f64,
) -> i32 {
    let velocity = Vector3::new(vx, vy, vz);
    let entity_uuid = uuid::Uuid::from_u64_pair(entity_msb as u64, entity_lsb as u64);

    if let Some(()) = with_player_uuid(entity_uuid, |player| {
        player.living_entity.entity.set_velocity(velocity);
    }) {
        return 1;
    }

    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        return 0;
    };
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(entity_uuid) {
            entity.get_entity().set_velocity(velocity);
            return 1;
        }
    }

    0
}

/// Fast-path FFI entrypoint to read an entity's or player's live position, velocity, and rotation
/// directly into an 8-element `f64` buffer (`[x, y, z, vx, vy, vz, yaw, pitch]`).
///
/// # Safety
/// `out_ptr` must be either null or valid for writes of `8 * size_of::<f64>()` bytes.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ffi_fast_sync_entity_state(
    entity_msb: i64,
    entity_lsb: i64,
    out_ptr: *mut f64,
) -> i32 {
    if out_ptr.is_null() {
        return 0;
    }
    let entity_uuid = uuid::Uuid::from_u64_pair(entity_msb as u64, entity_lsb as u64);

    if let Some(()) = with_player_uuid(entity_uuid, |player| {
        let base = &player.living_entity.entity;
        let pos = base.pos.load();
        let vel = base.velocity.load();
        let yaw = f64::from(base.yaw.load());
        let pitch = f64::from(base.pitch.load());
        unsafe {
            *out_ptr.add(0) = pos.x;
            *out_ptr.add(1) = pos.y;
            *out_ptr.add(2) = pos.z;
            *out_ptr.add(3) = vel.x;
            *out_ptr.add(4) = vel.y;
            *out_ptr.add(5) = vel.z;
            *out_ptr.add(6) = yaw;
            *out_ptr.add(7) = pitch;
        }
    }) {
        return 1;
    }

    let Some(ctx) = CALLBACK_CONTEXT.get() else {
        return 0;
    };
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(entity_uuid) {
            let base = entity.get_entity();
            let pos = base.pos.load();
            let vel = base.velocity.load();
            let yaw = f64::from(base.yaw.load());
            let pitch = f64::from(base.pitch.load());
            unsafe {
                *out_ptr.add(0) = pos.x;
                *out_ptr.add(1) = pos.y;
                *out_ptr.add(2) = pos.z;
                *out_ptr.add(3) = vel.x;
                *out_ptr.add(4) = vel.y;
                *out_ptr.add(5) = vel.z;
                *out_ptr.add(6) = yaw;
                *out_ptr.add(7) = pitch;
            }
            return 1;
        }
    }

    0
}

/// Fast-path FFI entrypoint to enqueue a raw outbound packet to a player's network connection
/// without Protobuf `SendRawPacketRequest` / `ByteString` serialization.
///
/// # Safety
/// `data_ptr` must be valid for reads of `data_len` bytes when `data_len > 0`.
#[unsafe(no_mangle)]
pub unsafe extern "C" fn ffi_fast_send_raw_packet(
    player_msb: i64,
    player_lsb: i64,
    data_ptr: *const u8,
    data_len: i32,
) -> i32 {
    if data_ptr.is_null() || data_len <= 0 {
        return 0;
    }
    let slice = unsafe { std::slice::from_raw_parts(data_ptr, data_len as usize) };
    let payload = bytes::Bytes::copy_from_slice(slice);
    let player_uuid = uuid::Uuid::from_u64_pair(player_msb as u64, player_lsb as u64);

    if let Some(()) = with_player_uuid(player_uuid, |player| {
        player.client.try_enqueue_packet_data(payload);
    }) {
        1
    } else {
        0
    }
}

pub fn initialize_fast_path_callbacks(env: &mut jni::Env) -> anyhow::Result<()> {
    let args = [
        jni::objects::JValue::Long(ffi_fast_get_block_state_id as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_set_block_state_id as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_teleport_entity as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_set_entity_velocity as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_set_packet_interception as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_sync_entity_state as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_send_raw_packet as *const () as i64),
        jni::objects::JValue::Long(ffi_fast_register_upcalls as *const () as i64),
    ];
    if let Err(e) = env.call_static_method(
        jni::jni_str!("org/patchbukkit/bridge/NativeFastPathFfi"),
        jni::jni_str!("init"),
        jni::jni_sig!("(JJJJJJJJ)V"),
        &args,
    ) {
        env.exception_describe();
        env.exception_clear();
        return Err(anyhow::anyhow!(
            "Failed to initialize NativeFastPathFfi: {e:?}"
        ));
    }
    Ok(())
}
