use std::collections::HashMap;
use std::str::FromStr;
use std::sync::atomic::Ordering;
use std::sync::{Arc, LazyLock, Mutex};

use pumpkin::data::SaveJSONConfiguration;
use pumpkin::entity::EntityBase;
use pumpkin::entity::attributes::{Modifier, ModifierOperation, send_attribute_updates_for_living};
use pumpkin::entity::player::PlayerWeather;
use pumpkin::world::bossbar::{Bossbar, BossbarColor, BossbarDivisions, BossbarFlags};
use pumpkin_data::attributes::Attributes;
use pumpkin_data::effect::StatusEffect;
use pumpkin_data::potion::Effect;
use pumpkin_util::math::position::BlockPos;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;

use crate::{
    java::native_callbacks::{
        CALLBACK_CONTEXT,
        utils::{NativeBridgeWriteGuard, with_player, with_player_uuid},
        world::find_world_exact,
    },
    proto::patchbukkit::{
        common::{Location, Uuid, Vec3, World as ProtoWorld},
        entity::{
            AddPotionEffectRequest, AttributeModifierProto, DamageEntityRequest,
            EntityHealthResponse, EntityInfoResponse, EntityMetadataResponse,
            EntityPassengersResponse, EntityVelocityResponse, GetCooldownRequest,
            GetCooldownResponse, GetEntityAttributeRequest, GetEntityAttributeResponse,
            GetEntityIdResponse, GetEntityInfoRequest, GetEntityUuidRequest, GetEntityUuidResponse,
            GetExperienceResponse, GetFoodLevelResponse, GetGamemodeResponse,
            GetPlayerLocaleResponse, GetPlayerPoseStateResponse, GetPlayerTimeWeatherResponse,
            GetPotionEffectsResponse, IsOnGroundResponse, IsOpResponse, KickPlayerRequest,
            ModifyEntityAttributeModifierRequest, ModifyEntityPassengerRequest,
            ModifyScoreboardTagRequest, PlayerConnectionInfoResponse, PlayerSleepRequest,
            PotionEffectProto, RemovePotionEffectRequest, SendActionBarRequest,
            SendBlockChangeRequest, SendGameEventRequest, SendPluginMessageRequest,
            SendRawPacketRequest, SendResourcePackRequest, SendTitleRequest,
            SetCompassTargetRequest, SetCooldownRequest, SetDisplayNameRequest,
            SetEntityAbsorptionRequest, SetEntityAttributeBaseRequest, SetEntityHealthRequest,
            SetEntityMetadataRequest, SetEntityPoseRequest, SetEntityVelocityRequest,
            SetExhaustionRequest, SetExperienceRequest, SetFoodLevelRequest, SetGamemodeRequest,
            SetOpRequest, SetPlayerListHeaderFooterRequest, SetPlayerListNameRequest,
            SetPlayerTimeRequest, SetPlayerWeatherRequest, SetRespawnPointRequest,
            SetSaturationRequest, SetSneakingRequest, SetSpectatorTargetRequest,
            SetSprintingRequest, StopSoundRequest, SwingHandRequest, TeleportEntityRequest,
            UpdateBossBarRequest,
        },
    },
};

type CompassTargets = HashMap<uuid::Uuid, (f64, f64, f64)>;

static PLAYER_COMPASS_TARGETS: LazyLock<Mutex<CompassTargets>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn find_entity_arc(target_uuid: uuid::Uuid) -> Option<Arc<dyn EntityBase>> {
    if let Some(player) = with_player_uuid(target_uuid, |p| p as Arc<dyn EntityBase>) {
        return Some(player);
    }
    let ctx = CALLBACK_CONTEXT.get()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(target_uuid) {
            return Some(entity);
        }
    }
    None
}

fn resolve_status_effect(name: &str) -> Option<&'static StatusEffect> {
    let trimmed = name.trim();
    if let Some(eff) = StatusEffect::from_minecraft_name(trimmed) {
        return Some(eff);
    }
    let clean = trimmed
        .strip_prefix("minecraft:")
        .unwrap_or(trimmed)
        .to_ascii_lowercase();
    let with_ns = format!("minecraft:{clean}");
    if let Some(eff) = StatusEffect::from_minecraft_name(&with_ns) {
        return Some(eff);
    }
    if let Some(eff) = StatusEffect::from_name(&clean) {
        return Some(eff);
    }
    // Legacy Bukkit PotionEffectType names
    match clean.as_str() {
        "slow" => Some(&StatusEffect::SLOWNESS),
        "fast_digging" => Some(&StatusEffect::HASTE),
        "slow_digging" => Some(&StatusEffect::MINING_FATIGUE),
        "increase_damage" => Some(&StatusEffect::STRENGTH),
        "heal" => Some(&StatusEffect::INSTANT_HEALTH),
        "harm" => Some(&StatusEffect::INSTANT_DAMAGE),
        "jump" => Some(&StatusEffect::JUMP_BOOST),
        "confusion" => Some(&StatusEffect::NAUSEA),
        "damage_resistance" => Some(&StatusEffect::RESISTANCE),
        _ => None,
    }
}

fn resolve_attribute(name: &str) -> Option<&'static Attributes> {
    let clean = name
        .trim()
        .strip_prefix("minecraft:")
        .unwrap_or(name.trim())
        .to_ascii_lowercase();
    let normalized = clean
        .strip_prefix("generic.")
        .or_else(|| clean.strip_prefix("generic_"))
        .or_else(|| clean.strip_prefix("player."))
        .or_else(|| clean.strip_prefix("player_"))
        .or_else(|| clean.strip_prefix("zombie."))
        .or_else(|| clean.strip_prefix("zombie_"))
        .unwrap_or(&clean)
        .replace('.', "_");
    let target = format!("minecraft:{normalized}");
    Attributes::ALL.iter().find(|attr| attr.name == target)
}

fn entity_custom_name(entity: &pumpkin::entity::Entity) -> String {
    entity
        .custom_name
        .load()
        .as_ref()
        .as_ref()
        .map(|c| c.clone().get_text())
        .unwrap_or_default()
}

fn build_entity_info_from_player(player: &pumpkin::entity::player::Player) -> EntityInfoResponse {
    let base = &player.living_entity.entity;
    let pos = base.pos.load();
    EntityInfoResponse {
        uuid: Some(Uuid {
            value: player.gameprofile.id.to_string(),
        }),
        entity_id: base.entity_id,
        entity_type: "player".to_string(),
        world_uuid: Some(Uuid {
            value: player.world().uuid.to_string(),
        }),
        x: pos.x,
        y: pos.y,
        z: pos.z,
        yaw: base.yaw.load(),
        pitch: base.pitch.load(),
        custom_name: player.gameprofile.name.clone(),
        is_player: true,
        found: true,
    }
}

fn build_entity_info_from_entity(entity: &dyn EntityBase) -> EntityInfoResponse {
    let base = entity.get_entity();
    let pos = base.pos.load();
    EntityInfoResponse {
        uuid: Some(Uuid {
            value: base.entity_uuid.to_string(),
        }),
        entity_id: base.entity_id,
        entity_type: base.entity_type.resource_name.to_string(),
        world_uuid: Some(Uuid {
            value: base.world.load().uuid.to_string(),
        }),
        x: pos.x,
        y: pos.y,
        z: pos.z,
        yaw: base.yaw.load(),
        pitch: base.pitch.load(),
        custom_name: entity_custom_name(base),
        is_player: false,
        found: true,
    }
}

pub fn ffi_native_bridge_get_entity_health_impl(request: Uuid) -> Option<EntityHealthResponse> {
    if let Some(res) = with_player(Some(&request), |player| EntityHealthResponse {
        health: f64::from(player.living_entity.health.load()),
        max_health: f64::from(player.living_entity.get_max_health()),
        absorption: f64::from(player.living_entity.get_absorption()),
    }) {
        return Some(res);
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid)
            && let Some(living) = entity.get_living_entity()
        {
            return Some(EntityHealthResponse {
                health: f64::from(living.health.load()),
                max_health: f64::from(living.get_max_health()),
                absorption: f64::from(living.get_absorption()),
            });
        }
    }

    None
}

pub fn ffi_native_bridge_get_entity_id_impl(request: Uuid) -> Option<GetEntityIdResponse> {
    if let Some(id) = with_player(Some(&request), |player| {
        player.living_entity.entity.entity_id
    }) {
        return Some(GetEntityIdResponse { entity_id: id });
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.value).ok()?;

    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            return Some(GetEntityIdResponse {
                entity_id: entity.get_entity().entity_id,
            });
        }
    }

    None
}

pub fn ffi_native_bridge_get_entity_uuid_impl(
    request: GetEntityUuidRequest,
) -> Option<GetEntityUuidResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let id = request.entity_id;

    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(player) = world.get_player_by_id(id) {
            let base = &player.living_entity.entity;
            let pos = base.pos.load();
            return Some(GetEntityUuidResponse {
                uuid: Some(Uuid {
                    value: player.gameprofile.id.to_string(),
                }),
                entity_type: "player".to_string(),
                world_uuid: Some(Uuid {
                    value: world.uuid.to_string(),
                }),
                x: pos.x,
                y: pos.y,
                z: pos.z,
                yaw: base.yaw.load(),
                pitch: base.pitch.load(),
                custom_name: player.gameprofile.name.clone(),
                is_player: true,
            });
        }
        if let Some(entity) = world.get_entity_by_id(id) {
            let base = entity.get_entity();
            let pos = base.pos.load();
            return Some(GetEntityUuidResponse {
                uuid: Some(Uuid {
                    value: base.entity_uuid.to_string(),
                }),
                entity_type: base.entity_type.resource_name.to_string(),
                world_uuid: Some(Uuid {
                    value: world.uuid.to_string(),
                }),
                x: pos.x,
                y: pos.y,
                z: pos.z,
                yaw: base.yaw.load(),
                pitch: base.pitch.load(),
                custom_name: entity_custom_name(base),
                is_player: false,
            });
        }
    }

    None
}

pub fn ffi_native_bridge_get_entity_info_impl(
    request: GetEntityInfoRequest,
) -> Option<EntityInfoResponse> {
    if let Some(ref proto_uuid) = request.uuid
        && !proto_uuid.value.is_empty()
    {
        if let Some(info) = with_player(Some(proto_uuid), |p| build_entity_info_from_player(&p)) {
            return Some(info);
        }
        if let Ok(target_uuid) = uuid::Uuid::parse_str(&proto_uuid.value)
            && let Some(entity) = find_entity_arc(target_uuid)
        {
            return Some(build_entity_info_from_entity(entity.as_ref()));
        }
    }

    if request.entity_id > 0 {
        let ctx = CALLBACK_CONTEXT.get()?;
        for world in ctx.plugin_context.server.worlds.load().iter() {
            if let Some(player) = world.get_player_by_id(request.entity_id) {
                return Some(build_entity_info_from_player(&player));
            }
            if let Some(entity) = world.get_entity_by_id(request.entity_id) {
                return Some(build_entity_info_from_entity(entity.as_ref()));
            }
        }
    }

    Some(EntityInfoResponse {
        uuid: None,
        entity_id: 0,
        entity_type: String::new(),
        world_uuid: None,
        x: 0.0,
        y: 0.0,
        z: 0.0,
        yaw: 0.0,
        pitch: 0.0,
        custom_name: String::new(),
        is_player: false,
        found: false,
    })
}

pub fn ffi_native_bridge_set_entity_health_impl(request: SetEntityHealthRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    if let Some(()) = with_player(request.uuid.as_ref(), |player| {
        player.set_health(request.health as f32);
    }) {
        return Some(());
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid)
            && let Some(living) = entity.get_living_entity()
        {
            living.set_health(request.health as f32);
            return Some(());
        }
    }

    None
}

pub fn ffi_native_bridge_damage_entity_impl(request: DamageEntityRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let raw_amount = (request.amount as f32).max(0.0);

    if let Some(()) = with_player(request.uuid.as_ref(), |player| {
        let mut remaining = raw_amount;
        let abs = player.living_entity.get_absorption();
        if abs > 0.0 && remaining > 0.0 {
            let absorbed = abs.min(remaining);
            player.living_entity.set_absorption(abs - absorbed);
            remaining -= absorbed;
        }
        let current = player.living_entity.health.load();
        player.set_health((current - remaining).max(0.0));
        let hurt = pumpkin_protocol::java::client::play::CHurtAnimation::new(
            player.living_entity.entity.entity_id.into(),
            player.living_entity.entity.yaw.load(),
        );
        player.world().broadcast_packet_all(&hurt);
    }) {
        return Some(());
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid)
            && let Some(living) = entity.get_living_entity()
        {
            let mut remaining = raw_amount;
            let abs = living.get_absorption();
            if abs > 0.0 && remaining > 0.0 {
                let absorbed = abs.min(remaining);
                living.set_absorption(abs - absorbed);
                remaining -= absorbed;
            }
            let current = living.health.load();
            living.set_health((current - remaining).max(0.0));
            let hurt = pumpkin_protocol::java::client::play::CHurtAnimation::new(
                living.entity.entity_id.into(),
                living.entity.yaw.load(),
            );
            world.broadcast_packet_all(&hurt);
            return Some(());
        }
    }

    None
}

pub fn ffi_native_bridge_get_entity_velocity_impl(request: Uuid) -> Option<EntityVelocityResponse> {
    if let Some(res) = with_player(Some(&request), |player| {
        let vel = player.living_entity.entity.velocity.load();
        EntityVelocityResponse {
            x: vel.x,
            y: vel.y,
            z: vel.z,
        }
    }) {
        return Some(res);
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            let vel = entity.get_entity().velocity.load();
            return Some(EntityVelocityResponse {
                x: vel.x,
                y: vel.y,
                z: vel.z,
            });
        }
    }

    None
}

pub fn ffi_native_bridge_set_entity_velocity_impl(request: SetEntityVelocityRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let velocity = Vector3::new(request.x, request.y, request.z);
    if let Some(()) = with_player(request.uuid.as_ref(), |player| {
        player.living_entity.entity.set_velocity(velocity);
    }) {
        return Some(());
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            entity.get_entity().set_velocity(velocity);
            return Some(());
        }
    }

    None
}

pub fn ffi_native_bridge_set_entity_pose_impl(request: SetEntityPoseRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let pose = match request.pose.to_uppercase().as_str() {
        "FALL_FLYING" => pumpkin_data::entity::EntityPose::FallFlying,
        "SLEEPING" => pumpkin_data::entity::EntityPose::Sleeping,
        "SWIMMING" => pumpkin_data::entity::EntityPose::Swimming,
        "SPIN_ATTACK" => pumpkin_data::entity::EntityPose::SpinAttack,
        "SNEAKING" | "CROUCHING" => pumpkin_data::entity::EntityPose::Crouching,
        "LONG_JUMPING" => pumpkin_data::entity::EntityPose::LongJumping,
        "DYING" => pumpkin_data::entity::EntityPose::Dying,
        "CROAKING" => pumpkin_data::entity::EntityPose::Croaking,
        "USING_TONGUE" => pumpkin_data::entity::EntityPose::UsingTongue,
        "SITTING" => pumpkin_data::entity::EntityPose::Sitting,
        "ROARING" => pumpkin_data::entity::EntityPose::Roaring,
        "SNIFFING" => pumpkin_data::entity::EntityPose::Sniffing,
        "EMERGING" => pumpkin_data::entity::EntityPose::Emerging,
        "DIGGING" => pumpkin_data::entity::EntityPose::Digging,
        "SLIDING" => pumpkin_data::entity::EntityPose::Sliding,
        "SHOOTING" => pumpkin_data::entity::EntityPose::Shooting,
        "INHALING" => pumpkin_data::entity::EntityPose::Inhaling,
        _ => pumpkin_data::entity::EntityPose::Standing,
    };

    if let Some(()) = with_player(request.uuid.as_ref(), |player| {
        player.living_entity.entity.set_pose(pose);
    }) {
        return Some(());
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            entity.get_entity().set_pose(pose);
            return Some(());
        }
    }

    Some(())
}

pub fn ffi_native_bridge_get_gamemode_impl(request: Uuid) -> Option<GetGamemodeResponse> {
    with_player(Some(&request), |player| {
        let gamemode = player.gamemode.load();
        GetGamemodeResponse {
            gamemode: gamemode as i32,
        }
    })
}

pub fn ffi_native_bridge_set_gamemode_impl(request: SetGamemodeRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    with_player(request.uuid.as_ref(), |player| {
        let gamemode_val = request.gamemode as i8;
        if let Ok(gamemode) = pumpkin_util::gamemode::GameMode::try_from(gamemode_val) {
            player.set_gamemode(gamemode);
        }
    })
}

pub fn ffi_native_bridge_teleport_entity_impl(request: TeleportEntityRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let loc = request.location?;
    let pos = loc.position?;
    let yaw = loc.yaw;
    let pitch = loc.pitch;
    let requested_world = loc
        .world
        .as_ref()
        .and_then(|w| w.uuid.as_ref())
        .and_then(|u| uuid::Uuid::parse_str(&u.value).ok())
        .and_then(find_world_exact);

    if let Some(()) = with_player(request.uuid.as_ref(), |player| {
        let position = Vector3::new(pos.x, pos.y, pos.z);
        player.living_entity.entity.pos.store(position);
        player.living_entity.entity.yaw.store(yaw);
        player.living_entity.entity.pitch.store(pitch);
        let target_world = requested_world
            .clone()
            .unwrap_or_else(|| player.living_entity.entity.world.load_full());
        ctx.runtime.spawn(async move {
            let _guard = NativeBridgeWriteGuard::enter();
            player.teleport(position, Some(yaw), Some(pitch), target_world);
        });
    }) {
        return Some(());
    }

    let uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let position = Vector3::new(pos.x, pos.y, pos.z);
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            let base = entity.get_entity();
            base.pos.store(position);
            base.yaw.store(yaw);
            base.pitch.store(pitch);
            let target_world = requested_world.as_ref().unwrap_or(world);
            let _guard = NativeBridgeWriteGuard::enter();
            base.teleport(position, Some(yaw), Some(pitch), target_world);
            return Some(());
        }
    }

    None
}

pub fn ffi_native_bridge_remove_entity_impl(request: Uuid) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.value).ok()?;

    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            world.remove_entity(entity.get_entity() as &dyn EntityBase);
            return Some(());
        }
    }

    Some(())
}

pub fn ffi_native_bridge_is_on_ground_impl(request: Uuid) -> Option<IsOnGroundResponse> {
    if let Some(res) = with_player(Some(&request), |player| {
        let on_ground = player
            .living_entity
            .entity
            .on_ground
            .load(Ordering::Relaxed);
        IsOnGroundResponse { on_ground }
    }) {
        return Some(res);
    }

    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    for world in ctx.plugin_context.server.worlds.load().iter() {
        if let Some(entity) = world.get_entity_by_uuid(uuid) {
            let on_ground = entity.get_entity().on_ground.load(Ordering::Relaxed);
            return Some(IsOnGroundResponse { on_ground });
        }
    }

    None
}

pub fn ffi_native_bridge_get_player_locale_impl(request: Uuid) -> Option<GetPlayerLocaleResponse> {
    with_player(Some(&request), |player| {
        let config = player.config.load();
        GetPlayerLocaleResponse {
            locale: config.locale.clone(),
        }
    })
}

pub fn ffi_native_bridge_is_op_impl(request: Uuid) -> Option<IsOpResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let player_uuid = uuid::Uuid::parse_str(&request.value).ok()?;

    let is_op = with_player(Some(&request), |player| {
        let perm_lvl = player.permission_lvl.load();
        let op_lvl = ctx.plugin_context.server.basic_config.op_permission_level;
        let mut is_op = perm_lvl >= op_lvl || perm_lvl > pumpkin_util::PermissionLvl::Zero;

        if !is_op
            && let Ok(op_config) = ctx.plugin_context.server.data.operator_config.try_read()
            && op_config.get_entry(&player_uuid).is_some()
        {
            is_op = true;
        }

        is_op
    })
    .unwrap_or_else(|| {
        ctx.plugin_context
            .server
            .data
            .operator_config
            .read()
            .is_ok_and(|ops| ops.get_entry(&player_uuid).is_some())
    });

    Some(IsOpResponse { is_op })
}

pub fn ffi_native_bridge_set_op_impl(request: SetOpRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let is_op = request.is_op;
    let uuid_str = request.uuid.as_ref()?.value.clone();
    let player_uuid = uuid::Uuid::parse_str(&uuid_str).ok()?;
    let op_lvl = ctx.plugin_context.server.basic_config.op_permission_level;
    let lvl = if is_op {
        op_lvl
    } else {
        pumpkin_util::PermissionLvl::Zero
    };

    let mut player_name = String::new();
    with_player(request.uuid.as_ref(), |player| {
        player_name = player.gameprofile.name.clone();
        player.permission_lvl.store(lvl);
    });

    let server = ctx.plugin_context.server.clone();
    let mut op_config = server
        .data
        .operator_config
        .write()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if is_op {
        if let Some(op) = op_config.ops.iter_mut().find(|e| e.uuid == player_uuid) {
            op.level = lvl;
            if !player_name.is_empty() {
                op.name = player_name;
            }
        } else {
            op_config.ops.push(pumpkin_config::op::Op {
                uuid: player_uuid,
                name: player_name,
                level: lvl,
                bypasses_player_limit: false,
            });
        }
    } else {
        op_config.ops.retain(|e| e.uuid != player_uuid);
    }
    op_config.save();
    drop(op_config);
    if let Some(player) = server.get_player_by_uuid(player_uuid) {
        let dispatcher = server.command_dispatcher.load();
        player.set_permission_lvl(&server, lvl, &dispatcher);
    }
    Some(())
}

pub fn ffi_native_bridge_get_food_level_impl(request: Uuid) -> Option<GetFoodLevelResponse> {
    with_player(Some(&request), |player| {
        let food_level = i32::from(player.hunger_manager.level.load());
        let saturation = player.hunger_manager.saturation.load();
        let exhaustion = player.hunger_manager.get_exhaustion();
        GetFoodLevelResponse {
            food_level,
            saturation,
            exhaustion,
        }
    })
}

pub fn ffi_native_bridge_set_food_level_impl(request: SetFoodLevelRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    with_player(request.uuid.as_ref(), |player| {
        let food_level = request.food_level.clamp(0, 20) as u8;
        player.hunger_manager.set_level(food_level);
        player.send_health();
    })
}

pub fn ffi_native_bridge_set_saturation_impl(request: SetSaturationRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.hunger_manager.set_saturation(request.saturation);
        player.send_health();
    })
}

pub fn ffi_native_bridge_set_exhaustion_impl(request: SetExhaustionRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.hunger_manager.set_exhaustion(request.exhaustion);
    })
}

pub fn ffi_native_bridge_get_experience_impl(request: Uuid) -> Option<GetExperienceResponse> {
    with_player(Some(&request), |player| GetExperienceResponse {
        level: player.get_experience_level(),
        progress: player.get_experience_progress(),
        total_experience: player.get_total_experience(),
    })
}

pub fn ffi_native_bridge_set_experience_impl(request: SetExperienceRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.set_experience(request.level, request.progress, request.total_experience);
    })
}

pub fn ffi_native_bridge_kick_player_impl(request: KickPlayerRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let text = TextComponent::from_legacy_string(&request.message);
        player.kick(pumpkin::net::DisconnectReason::Kicked, &text);
    })
}

pub fn ffi_native_bridge_send_title_impl(request: SendTitleRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        if request.fade_in >= 0 && request.stay >= 0 && request.fade_out >= 0 {
            player.send_title_animation(request.fade_in, request.stay, request.fade_out);
        }
        if !request.title.is_empty() {
            let text = TextComponent::from_legacy_string(&request.title);
            player.show_title(&text, &pumpkin::entity::player::TitleMode::Title);
        }
        if !request.subtitle.is_empty() {
            let text = TextComponent::from_legacy_string(&request.subtitle);
            player.show_title(&text, &pumpkin::entity::player::TitleMode::SubTitle);
        }
    })
}

pub fn ffi_native_bridge_send_action_bar_impl(request: SendActionBarRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let text = TextComponent::from_legacy_string(&request.message);
        player.send_system_message_raw(&text, true);
    })
}

pub fn ffi_native_bridge_reset_title_impl(request: Uuid) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(Some(&request), |player| {
        ctx.runtime.spawn(async move {
            let packet = pumpkin_protocol::java::client::play::CClearTitle::new(true);
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_set_display_name_impl(request: SetDisplayNameRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let comp = if request.display_name.is_empty() {
            None
        } else {
            Some(TextComponent::from_legacy_string(&request.display_name))
        };
        player.set_display_name(comp);
    })
}

pub fn ffi_native_bridge_set_player_list_name_impl(
    request: SetPlayerListNameRequest,
) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let comp = if request.list_name.is_empty() {
            None
        } else {
            Some(TextComponent::from_legacy_string(&request.list_name))
        };
        player.set_tab_list_name(comp);
    })
}

pub fn ffi_native_bridge_set_player_list_header_footer_impl(
    request: SetPlayerListHeaderFooterRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let header = request.header;
        let footer = request.footer;
        ctx.runtime.spawn(async move {
            let h = TextComponent::from_legacy_string(&header);
            let f = TextComponent::from_legacy_string(&footer);
            player.set_tab_list_header_footer(&h, &f);
        });
    })
}

pub fn ffi_native_bridge_get_player_pose_state_impl(
    request: Uuid,
) -> Option<GetPlayerPoseStateResponse> {
    with_player(Some(&request), |player| {
        let e = &player.living_entity.entity;
        GetPlayerPoseStateResponse {
            is_sneaking: e.sneaking.load(Ordering::Relaxed),
            is_sprinting: e.sprinting.load(Ordering::Relaxed),
            is_gliding: e.fall_flying.load(Ordering::Relaxed),
            is_swimming: e.swimming.load(Ordering::Relaxed),
            is_sleeping: player.sleeping_since.load().is_some(),
        }
    })
}

pub fn ffi_native_bridge_set_sneaking_impl(request: SetSneakingRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    with_player(request.uuid.as_ref(), |player| {
        player.living_entity.entity.set_sneaking(request.sneaking);
    })
}

pub fn ffi_native_bridge_set_sprinting_impl(request: SetSprintingRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    with_player(request.uuid.as_ref(), |player| {
        player.living_entity.entity.set_sprinting(request.sprinting);
    })
}

pub fn ffi_native_bridge_set_player_time_impl(request: SetPlayerTimeRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.set_player_time(request.time.max(0) as u64, request.relative);
    })
}

pub fn ffi_native_bridge_reset_player_time_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        player.reset_player_time();
    })
}

pub fn ffi_native_bridge_set_player_weather_impl(request: SetPlayerWeatherRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let weather = if request.weather == 1 {
            PlayerWeather::Downfall
        } else {
            PlayerWeather::Clear
        };
        player.set_player_weather(weather);
    })
}

pub fn ffi_native_bridge_reset_player_weather_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        player.reset_player_weather();
    })
}

pub fn ffi_native_bridge_get_player_time_weather_impl(
    request: Uuid,
) -> Option<GetPlayerTimeWeatherResponse> {
    with_player(Some(&request), |player| {
        let player_time_opt = player.get_player_time();
        let time_relative = player.is_player_time_relative();
        let player_weather = match player.get_player_weather() {
            Some(PlayerWeather::Clear) => 0,
            Some(PlayerWeather::Downfall) => 1,
            None => -1,
        };
        GetPlayerTimeWeatherResponse {
            has_player_time: player_time_opt.is_some(),
            player_time: player_time_opt.unwrap_or(0) as i64,
            time_relative,
            player_weather,
        }
    })
}

pub fn ffi_native_bridge_set_compass_target_impl(request: SetCompassTargetRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let pos = request.position?;
    if let Some(uuid_str) = request.uuid.as_ref().map(|u| &u.value)
        && let Ok(player_uuid) = uuid::Uuid::parse_str(uuid_str)
    {
        PLAYER_COMPASS_TARGETS
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .insert(player_uuid, (pos.x, pos.y, pos.z));
    }
    with_player(request.uuid.as_ref(), |player| {
        ctx.runtime.spawn(async move {
            let block_pos = BlockPos::new(pos.x as i32, pos.y as i32, pos.z as i32);
            let dimension_name = player.world().dimension.minecraft_name.to_string();
            let packet = pumpkin_protocol::java::client::play::CPlayerSpawnPosition::new(
                block_pos,
                0.0,
                0.0,
                dimension_name,
            );
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_get_compass_target_impl(request: Uuid) -> Option<Vec3> {
    if let Ok(player_uuid) = uuid::Uuid::parse_str(&request.value)
        && let Some(&(x, y, z)) = PLAYER_COMPASS_TARGETS
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner)
            .get(&player_uuid)
    {
        return Some(Vec3 { x, y, z });
    }
    with_player(Some(&request), |player| {
        let info = player.world().level_info.load();
        Vec3 {
            x: f64::from(info.spawn_x),
            y: f64::from(info.spawn_y),
            z: f64::from(info.spawn_z),
        }
    })
}

pub fn ffi_native_bridge_set_respawn_point_impl(request: SetRespawnPointRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    with_player(request.uuid.as_ref(), |player| {
        if request.clear {
            let mut guard = player
                .respawn_point
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            *guard = None;
            return;
        }
        let Some(pos) = request.position else {
            return;
        };
        let block_pos = BlockPos::new(pos.x as i32, pos.y as i32, pos.z as i32);
        let dim = request
            .world_uuid
            .as_ref()
            .and_then(|u| uuid::Uuid::parse_str(&u.value).ok())
            .and_then(find_world_exact)
            .map_or_else(|| player.world().dimension.clone(), |w| w.dimension.clone());
        player.set_respawn_point(dim, block_pos, request.yaw, request.pitch, request.force);
    })
}

pub fn ffi_native_bridge_get_respawn_point_impl(request: Uuid) -> Option<Location> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(Some(&request), |player| {
        let guard = player
            .respawn_point
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        let (pos, yaw, world_uuid) = match *guard {
            Some(ref pt) => {
                let matched_world_uuid = ctx
                    .plugin_context
                    .server
                    .worlds
                    .load()
                    .iter()
                    .find(|w| w.dimension.minecraft_name == pt.dimension.minecraft_name)
                    .map_or_else(|| player.world().uuid.to_string(), |w| w.uuid.to_string());
                (
                    Vec3 {
                        x: f64::from(pt.position.0.x),
                        y: f64::from(pt.position.0.y),
                        z: f64::from(pt.position.0.z),
                    },
                    pt.yaw,
                    matched_world_uuid,
                )
            }
            None => {
                let info = player.world().level_info.load();
                (
                    Vec3 {
                        x: f64::from(info.spawn_x),
                        y: f64::from(info.spawn_y),
                        z: f64::from(info.spawn_z),
                    },
                    info.spawn_yaw,
                    player.world().uuid.to_string(),
                )
            }
        };
        Location {
            position: Some(pos),
            yaw,
            pitch: 0.0,
            world: Some(ProtoWorld {
                uuid: Some(Uuid { value: world_uuid }),
            }),
        }
    })
}

pub fn ffi_native_bridge_stop_sound_impl(request: StopSoundRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let sound = if request.sound.is_empty() {
            None
        } else {
            pumpkin_util::resource_location::ResourceLocation::from_str(&request.sound).ok()
        };
        let category = if request.category.is_empty() {
            None
        } else {
            pumpkin_data::sound::SoundCategory::from_name(&request.category.to_lowercase())
        };
        player.stop_sound(sound, category);
    })
}

pub fn ffi_native_bridge_send_block_change_impl(request: SendBlockChangeRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let pos = BlockPos::new(request.x, request.y, request.z);
        let state_str = request.block_state;
        let clean_key = state_str
            .split('[')
            .next()
            .unwrap_or(&state_str)
            .trim_start_matches("minecraft:");
        let state_id = if let Some(b) = pumpkin_data::Block::from_registry_key(clean_key) {
            match state_str.split_once('[') {
                Some((_, props_str)) => {
                    let props: Vec<pumpkin_data::Property> = props_str
                        .trim_end_matches(']')
                        .split(',')
                        .filter_map(|pair| pair.split_once('='))
                        .filter_map(|(k, v)| {
                            pumpkin_data::Property::from_str_pair(k.trim(), v.trim())
                        })
                        .collect();
                    if props.is_empty() {
                        b.default_state.id
                    } else {
                        b.from_properties(&props).to_state_id(b)
                    }
                }
                None => b.default_state.id,
            }
        } else {
            pumpkin_data::BlockStateId::new_or_air(0)
        };
        ctx.runtime.spawn(async move {
            let packet = pumpkin_protocol::java::client::play::CBlockUpdate::new(
                pos,
                pumpkin_protocol::codec::var_int::VarInt(i32::from(state_id.as_u16())),
            );
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_send_resource_pack_impl(request: SendResourcePackRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let url = request.url;
        let prompt = request.prompt;
        let required = request.required;
        let hash = request.hash;
        let id = request
            .pack_id
            .as_ref()
            .and_then(|u| uuid::Uuid::parse_str(&u.value).ok())
            .unwrap_or_else(uuid::Uuid::new_v4);
        ctx.runtime.spawn(async move {
            let prompt_comp = if prompt.is_empty() {
                None
            } else {
                Some(TextComponent::from_legacy_string(&prompt))
            };
            let packet = pumpkin_protocol::java::client::play::CAddResourcePack::new(
                &id,
                &url,
                &hash,
                required,
                prompt_comp,
            );
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_get_player_connection_info_impl(
    request: Uuid,
) -> Option<PlayerConnectionInfoResponse> {
    with_player(Some(&request), |player| {
        let ping = player.ping.load(Ordering::Relaxed) as i32;
        let addr = player.client.address();
        PlayerConnectionInfoResponse {
            ping,
            address: addr.ip().to_string(),
            port: i32::from(addr.port()),
            client_brand: "vanilla".to_string(),
        }
    })
}

pub fn ffi_native_bridge_send_game_event_impl(request: SendGameEventRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let event_type = request.event_type as u8;
        let value = request.value;
        ctx.runtime.spawn(async move {
            let packet = pumpkin_protocol::java::client::play::CGameEvent {
                event: event_type,
                value,
            };
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_send_raw_packet_impl(request: SendRawPacketRequest) -> Option<()> {
    let payload = bytes::Bytes::from(request.payload);
    with_player(request.uuid.as_ref(), |player| {
        player.client.try_enqueue_packet_data_unobserved(payload);
    })
}

pub fn ffi_native_bridge_set_cooldown_impl(request: SetCooldownRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.start_cooldown(request.item_group, request.duration_ticks);
    })
}

pub fn ffi_native_bridge_get_cooldown_impl(
    request: GetCooldownRequest,
) -> Option<GetCooldownResponse> {
    with_player(request.uuid.as_ref(), |player| {
        let (cooldown, is_on_cooldown, remaining_ticks) =
            if let Ok(guard) = player.item_cooldowns.try_lock() {
                if let Some(cd) = guard.get(&request.item_group) {
                    let current_tick = player.tick_counter.load(Ordering::Relaxed);
                    let elapsed = current_tick - cd.start_tick;
                    if elapsed < cd.duration && cd.duration > 0 {
                        let remaining = (cd.duration - elapsed).max(0);
                        (1.0 - (elapsed as f32 / cd.duration as f32), true, remaining)
                    } else {
                        (0.0, false, 0)
                    }
                } else {
                    (0.0, false, 0)
                }
            } else {
                (0.0, false, 0)
            };
        GetCooldownResponse {
            cooldown,
            is_on_cooldown,
            remaining_ticks,
        }
    })
}

pub fn ffi_native_bridge_open_ender_chest_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        player.open_ender_chest();
    })
}

pub fn ffi_native_bridge_update_inventory_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        player.on_screen_handler_opened(&player.player_screen_handler);
    })
}

// --- Extended Entity & LivingEntity Coverage ---

pub fn ffi_native_bridge_get_potion_effects_impl(
    request: Uuid,
) -> Option<GetPotionEffectsResponse> {
    let target_uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;
    let guard = living
        .active_effects
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let effects = guard
        .values()
        .map(|eff| PotionEffectProto {
            effect_type: eff.effect_type.minecraft_name.to_string(),
            duration: eff.duration,
            amplifier: i32::from(eff.amplifier),
            ambient: eff.ambient,
            show_particles: eff.show_particles,
            show_icon: eff.show_icon,
        })
        .collect();
    Some(GetPotionEffectsResponse { effects })
}

pub fn ffi_native_bridge_add_potion_effect_impl(request: AddPotionEffectRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let proto_eff = request.effect?;
    let effect_type = resolve_status_effect(&proto_eff.effect_type)?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;

    living.add_effect(Effect {
        effect_type,
        duration: proto_eff.duration,
        amplifier: proto_eff.amplifier.clamp(0, 255) as u8,
        ambient: proto_eff.ambient,
        show_particles: proto_eff.show_particles,
        show_icon: proto_eff.show_icon,
        blend: false,
    });
    Some(())
}

pub fn ffi_native_bridge_remove_potion_effect_impl(
    request: RemovePotionEffectRequest,
) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let effect_type = resolve_status_effect(&request.effect_type)?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;
    living.remove_effect(effect_type);
    Some(())
}

pub fn ffi_native_bridge_clear_potion_effects_impl(request: Uuid) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let target_uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;
    let types: Vec<&'static StatusEffect> = {
        let guard = living
            .active_effects
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        guard.keys().copied().collect()
    };
    for effect_type in types {
        living.remove_effect(effect_type);
    }
    Some(())
}

pub fn ffi_native_bridge_get_entity_attribute_impl(
    request: GetEntityAttributeRequest,
) -> Option<GetEntityAttributeResponse> {
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let Some(attr) = resolve_attribute(&request.attribute) else {
        return Some(GetEntityAttributeResponse {
            base_value: 0.0,
            value: 0.0,
            modifiers: Vec::new(),
            found: false,
        });
    };
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;

    let base_value = living.get_attribute_base(attr);
    let value = living.get_attribute_value(attr);
    let modifiers = {
        let map = living
            .attributes
            .read()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        map.get(&attr.id)
            .map(|inst| {
                inst.modifiers
                    .iter()
                    .map(|m| AttributeModifierProto {
                        id: m.id.clone(),
                        amount: m.amount,
                        operation: m.operation as i32,
                    })
                    .collect()
            })
            .unwrap_or_default()
    };

    Some(GetEntityAttributeResponse {
        base_value,
        value,
        modifiers,
        found: true,
    })
}

pub fn ffi_native_bridge_set_entity_attribute_base_impl(
    request: SetEntityAttributeBaseRequest,
) -> Option<()> {
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let attr = resolve_attribute(&request.attribute)?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;

    if *attr == Attributes::MAX_HEALTH {
        living.set_max_health(request.base_value as f32);
        if let Some(()) = with_player_uuid(target_uuid, |player| {
            player.send_health();
        }) {}
    } else {
        living.set_attribute_base(attr, request.base_value);
        send_attribute_updates_for_living(living, vec![attr.clone()]);
    }
    Some(())
}

pub fn ffi_native_bridge_modify_entity_attribute_modifier_impl(
    request: ModifyEntityAttributeModifierRequest,
) -> Option<()> {
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let attr = resolve_attribute(&request.attribute)?;
    let proto_mod = request.modifier?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;

    if request.remove {
        living.update_attribute(attr, |inst| {
            inst.remove_modifier(&proto_mod.id);
        });
    } else {
        let operation = match proto_mod.operation {
            1 => ModifierOperation::MultiplyBase,
            2 => ModifierOperation::MultiplyTotal,
            _ => ModifierOperation::Add,
        };
        let modifier = Modifier {
            id: proto_mod.id,
            amount: proto_mod.amount,
            operation,
        };
        living.update_attribute(attr, |inst| {
            inst.add_or_replace_modifier(modifier);
        });
    }
    send_attribute_updates_for_living(living, vec![attr.clone()]);
    if *attr == Attributes::MAX_HEALTH {
        with_player_uuid(target_uuid, |player| {
            player.send_health();
        });
    }
    Some(())
}

pub fn ffi_native_bridge_set_entity_absorption_impl(
    request: SetEntityAbsorptionRequest,
) -> Option<()> {
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;
    living.set_absorption((request.absorption as f32).max(0.0));
    with_player_uuid(target_uuid, |player| {
        player.send_health();
    });
    Some(())
}

pub fn ffi_native_bridge_get_entity_metadata_impl(request: Uuid) -> Option<EntityMetadataResponse> {
    let target_uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let base = entity.get_entity();
    let fall_distance = entity
        .get_living_entity()
        .map_or(0.0, |l| l.fall_distance.load());
    let scoreboard_tags = base
        .scoreboard_tags
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .iter()
        .cloned()
        .collect();

    Some(EntityMetadataResponse {
        custom_name: entity_custom_name(base),
        custom_name_visible: base.custom_name_visible.load(Ordering::Relaxed),
        fire_ticks: base.fire_ticks.load(Ordering::Relaxed),
        visual_fire: base.has_visual_fire.load(Ordering::Relaxed),
        freeze_ticks: base.frozen_ticks.load(Ordering::Relaxed),
        air_supply: base.air_supply.load(Ordering::Relaxed),
        glowing: base.glowing.load(Ordering::Relaxed),
        invisible: base.invisible.load(Ordering::Relaxed),
        silent: base.silent.load(Ordering::Relaxed),
        no_gravity: base.has_no_gravity(),
        invulnerable: base.invulnerable.load(Ordering::Relaxed),
        fall_distance,
        swimming: base.swimming.load(Ordering::Relaxed),
        gliding: base.fall_flying.load(Ordering::Relaxed),
        scoreboard_tags,
    })
}

pub fn ffi_native_bridge_set_entity_metadata_impl(request: SetEntityMetadataRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let base = entity.get_entity();

    if request.clear_custom_name == Some(true) {
        base.clear_custom_name();
    } else if let Some(ref name) = request.custom_name {
        base.set_custom_name(TextComponent::from_legacy_string(name));
    }
    if let Some(visible) = request.custom_name_visible {
        base.set_custom_name_visible(visible);
    }
    if let Some(fire_ticks) = request.fire_ticks {
        if fire_ticks <= 0 {
            base.extinguish();
        } else {
            base.fire_ticks.store(fire_ticks, Ordering::Relaxed);
            base.set_on_fire(true);
        }
    }
    if let Some(visual_fire) = request.visual_fire {
        base.set_on_fire(visual_fire);
    }
    if let Some(freeze_ticks) = request.freeze_ticks {
        base.set_frozen_ticks(freeze_ticks);
    }
    if let Some(air_supply) = request.air_supply {
        base.set_air_supply(air_supply);
    }
    if let Some(glowing) = request.glowing {
        base.set_glowing(glowing);
    }
    if let Some(invisible) = request.invisible {
        base.set_invisible(invisible);
    }
    if let Some(silent) = request.silent {
        base.set_silent(silent);
    }
    if let Some(no_gravity) = request.no_gravity {
        base.set_has_no_gravity(no_gravity);
    }
    if let Some(invulnerable) = request.invulnerable {
        base.set_invulnerable(invulnerable);
    }
    if let Some(fall_distance) = request.fall_distance
        && let Some(living) = entity.get_living_entity()
    {
        living.fall_distance.store(fall_distance);
    }
    if let Some(swimming) = request.swimming {
        base.set_swimming(swimming);
    }
    if let Some(gliding) = request.gliding {
        base.set_fall_flying(gliding);
    }
    if request.yaw.is_some() || request.pitch.is_some() {
        let yaw = request.yaw.unwrap_or_else(|| base.yaw.load());
        let pitch = request.pitch.unwrap_or_else(|| base.pitch.load());
        base.set_rotation(yaw, pitch);
    }

    Some(())
}

pub fn ffi_native_bridge_modify_scoreboard_tag_impl(
    request: ModifyScoreboardTagRequest,
) -> Option<()> {
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let base = entity.get_entity();
    if request.add {
        base.add_scoreboard_tag(&request.tag);
    } else {
        base.remove_scoreboard_tag(&request.tag);
    }
    Some(())
}

pub fn ffi_native_bridge_get_entity_passengers_impl(
    request: Uuid,
) -> Option<EntityPassengersResponse> {
    let target_uuid = uuid::Uuid::parse_str(&request.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let base = entity.get_entity();

    let passenger_uuids = base
        .passengers
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .iter()
        .map(|p| Uuid {
            value: p.get_entity().entity_uuid.to_string(),
        })
        .collect();
    let vehicle_uuid = base
        .vehicle
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .as_ref()
        .map(|v| Uuid {
            value: v.get_entity().entity_uuid.to_string(),
        });

    Some(EntityPassengersResponse {
        passenger_uuids,
        vehicle_uuid,
    })
}

pub fn ffi_native_bridge_modify_entity_passenger_impl(
    request: ModifyEntityPassengerRequest,
) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    let vehicle_uuid = uuid::Uuid::parse_str(&request.vehicle_uuid.as_ref()?.value).ok()?;
    let passenger_uuid = uuid::Uuid::parse_str(&request.passenger_uuid.as_ref()?.value).ok()?;
    let vehicle = find_entity_arc(vehicle_uuid)?;
    let passenger = find_entity_arc(passenger_uuid)?;

    if request.add {
        vehicle
            .get_entity()
            .add_passenger(vehicle.clone(), passenger);
    } else {
        vehicle
            .get_entity()
            .remove_passenger(passenger.get_entity().entity_id);
    }
    Some(())
}

pub fn ffi_native_bridge_swing_entity_hand_impl(request: SwingHandRequest) -> Option<()> {
    let target_uuid = uuid::Uuid::parse_str(&request.uuid.as_ref()?.value).ok()?;
    let entity = find_entity_arc(target_uuid)?;
    let living = entity.get_living_entity()?;
    if request.off_hand {
        living.swing_off_hand();
    } else {
        living.swing_hand();
    }
    Some(())
}

pub fn ffi_native_bridge_send_health_update_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        player.send_health();
    })
}

pub fn ffi_native_bridge_send_plugin_message_impl(request: SendPluginMessageRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let channel = request.channel;
    let data = request.data;
    with_player(request.uuid.as_ref(), |player| {
        ctx.runtime.spawn(async move {
            if let Some(java_player) = player.as_java() {
                java_player.send_custom_payload(&channel, &data).await;
            }
        });
    })
}

pub fn ffi_native_bridge_update_boss_bar_impl(request: UpdateBossBarRequest) -> Option<()> {
    let bossbar_uuid = uuid::Uuid::parse_str(&request.bossbar_uuid.as_ref()?.value).ok()?;
    let color = match request.color {
        0 => BossbarColor::Pink,
        1 => BossbarColor::Blue,
        2 => BossbarColor::Red,
        3 => BossbarColor::Green,
        4 => BossbarColor::Yellow,
        5 => BossbarColor::Purple,
        _ => BossbarColor::White,
    };
    let division = match request.division {
        1 => BossbarDivisions::Notches6,
        2 => BossbarDivisions::Notches10,
        3 => BossbarDivisions::Notches12,
        4 => BossbarDivisions::Notches20,
        _ => BossbarDivisions::NoDivision,
    };
    let flags = BossbarFlags::from_bits_truncate(request.flags as u8);

    with_player(request.player_uuid.as_ref(), |player| {
        match request.action {
            0 => {
                // Add
                let bar = Bossbar {
                    uuid: bossbar_uuid,
                    title: TextComponent::from_legacy_string(&request.title),
                    health: request.progress.clamp(0.0, 1.0),
                    color,
                    division,
                    flags,
                };
                player.send_bossbar(&bar);
            }
            1 => {
                // Remove
                player.remove_bossbar(bossbar_uuid);
            }
            2 => {
                // Update progress
                player.update_bossbar_health(&bossbar_uuid, request.progress.clamp(0.0, 1.0));
            }
            3 => {
                // Update title
                player.update_bossbar_title(
                    &bossbar_uuid,
                    TextComponent::from_legacy_string(&request.title),
                );
            }
            4 => {
                // Update style
                player.update_bossbar_style(&bossbar_uuid, color, division);
            }
            5 => {
                // Update flags
                player.update_bossbar_flags(&bossbar_uuid, flags);
            }
            _ => {}
        }
    })
}

pub fn ffi_native_bridge_set_spectator_target_impl(
    request: SetSpectatorTargetRequest,
) -> Option<()> {
    with_player(request.player_uuid.as_ref(), |player| {
        if let Some(ref target_proto) = request.target_uuid
            && !target_proto.value.is_empty()
            && let Ok(target_uuid) = uuid::Uuid::parse_str(&target_proto.value)
            && let Some(target_entity) = find_entity_arc(target_uuid)
        {
            player.set_camera_entity_id(target_entity.get_entity().entity_id);
            return;
        }
        player.reset_camera();
    })
}

pub fn ffi_native_bridge_player_sleep_impl(request: PlayerSleepRequest) -> Option<()> {
    let _guard = NativeBridgeWriteGuard::enter();
    with_player(request.uuid.as_ref(), |player| {
        if request.wake_up {
            player.wake_up();
        } else {
            player.sleep(BlockPos::new(request.x, request.y, request.z));
        }
    })
}
