use std::collections::HashMap;
use std::io::Cursor;
use std::sync::Arc;

use pumpkin_data::game_rules::{GameRule, GameRuleValue};
use pumpkin_nbt::Nbt;
use pumpkin_nbt::deserializer::NbtReadHelperJava;
use pumpkin_util::math::position::BlockPos;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_world::chunk::ChunkHeightmapType;

use crate::{
    java::native_callbacks::{
        CALLBACK_CONTEXT,
        sound::{resolve_sound, resolve_sound_category},
    },
    proto::patchbukkit::{
        common::{EmptyRequest, Uuid as ProtoUuid},
        world::{
            ChunkCoordProto, CreateWorldExplosionRequest, EntitySummaryProto, GetBiomeRequest,
            GetBiomeResponse, GetBlockDataRequest, GetBlockDataResponse, GetBlockEntityNbtRequest,
            GetBlockEntityNbtResponse, GetForceLoadedChunksRequest, GetForceLoadedChunksResponse,
            GetHighestBlockYRequest, GetHighestBlockYResponse, GetLightLevelRequest,
            GetLightLevelResponse, GetWorldBorderRequest, GetWorldEntitiesRequest,
            GetWorldEntitiesResponse, GetWorldGamerulesRequest, GetWorldGamerulesResponse,
            GetWorldInfoRequest, GetWorldInfoResponse, GetWorldsResponse, PlayWorldSoundRequest,
            SaveWorldRequest, SetBlockDataRequest, SetBlockEntityNbtRequest,
            SetChunkForceLoadedRequest, SetWorldBorderRequest, SetWorldDifficultyRequest,
            SetWorldGameruleRequest, SetWorldPvpRequest, SetWorldSpawnRequest, SetWorldTimeRequest,
            SetWorldWeatherRequest, SpawnParticleRequest, SpawnWorldEntityRequest,
            SpawnWorldEntityResponse, StrikeLightningRequest, WorldBorderData,
        },
    },
};

pub(crate) fn find_world(
    world_uuid_proto: Option<&ProtoUuid>,
) -> Option<Arc<pumpkin::world::World>> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let worlds = ctx.plugin_context.server.worlds.load();
    if let Some(proto) = world_uuid_proto
        && let Ok(world_uuid) = uuid::Uuid::parse_str(&proto.value)
        && let Some(found) = worlds.iter().find(|w| w.uuid == world_uuid)
    {
        return Some(found.clone());
    }
    worlds.first().cloned()
}

pub(crate) fn find_world_exact(world_uuid: uuid::Uuid) -> Option<Arc<pumpkin::world::World>> {
    let ctx = CALLBACK_CONTEXT.get()?;
    ctx.plugin_context
        .server
        .worlds
        .load()
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
}

fn ensure_chunk_loaded(world: &Arc<pumpkin::world::World>, pos: &BlockPos) {
    let (chunk_coordinate, _) = pos.chunk_and_chunk_relative_position();
    if !world.level.is_chunk_loaded(&chunk_coordinate)
        && let Some(ctx) = CALLBACK_CONTEXT.get()
    {
        ctx.runtime
            .block_on(world.level.get_or_fetch_chunk(chunk_coordinate, |_| ()));
    }
}

const LEGACY_GAMERULE_ALIASES: &[(&str, GameRule, bool)] = &[
    ("doDaylightCycle", GameRule::AdvanceTime, false),
    ("doWeatherCycle", GameRule::AdvanceWeather, false),
    ("doTileDrops", GameRule::BlockDrops, false),
    ("doEntityDrops", GameRule::EntityDrops, false),
    ("doMobLoot", GameRule::MobDrops, false),
    ("doMobSpawning", GameRule::SpawnMobs, false),
    ("doPatrolSpawning", GameRule::SpawnPatrols, false),
    ("doTraderSpawning", GameRule::SpawnWanderingTraders, false),
    ("doInsomnia", GameRule::SpawnPhantoms, false),
    ("doWardenSpawning", GameRule::SpawnWardens, false),
    ("doImmediateRespawn", GameRule::ImmediateRespawn, false),
    ("doLimitedCrafting", GameRule::LimitedCrafting, false),
    ("doFireTick", GameRule::FireSpreadRadiusAroundPlayer, false),
    ("doVinesSpread", GameRule::SpreadVines, false),
    (
        "naturalRegeneration",
        GameRule::NaturalHealthRegeneration,
        false,
    ),
    (
        "announceAdvancements",
        GameRule::ShowAdvancementMessages,
        false,
    ),
    ("spawnRadius", GameRule::RespawnRadius, false),
    (
        "disableElytraMovementCheck",
        GameRule::ElytraMovementCheck,
        true,
    ),
    (
        "disablePlayerMovementCheck",
        GameRule::PlayerMovementCheck,
        true,
    ),
    ("disableRaids", GameRule::Raids, true),
    (
        "maxCommandChainLength",
        GameRule::MaxCommandSequenceLength,
        false,
    ),
    ("maxCommandForkCount", GameRule::MaxCommandForks, false),
    (
        "commandModificationBlockLimit",
        GameRule::MaxBlockModifications,
        false,
    ),
    ("minecartMaxSpeed", GameRule::MaxMinecartSpeed, false),
    (
        "snowAccumulationHeight",
        GameRule::MaxSnowAccumulationHeight,
        false,
    ),
];

fn snake_to_camel(snake: &str) -> String {
    let mut out = String::with_capacity(snake.len());
    let mut upper_next = false;
    for ch in snake.chars() {
        if ch == '_' {
            upper_next = true;
        } else if upper_next {
            out.push(ch.to_ascii_uppercase());
            upper_next = false;
        } else {
            out.push(ch);
        }
    }
    out
}

/// Resolves a modern snake_case (`minecraft:keep_inventory`, `keep_inventory`) or
/// legacy Bukkit camelCase (`keepInventory`, `doDaylightCycle`) gamerule name into
/// a Pumpkin `GameRule` and whether boolean polarity is inverted relative to the legacy name.
fn resolve_gamerule(name: &str) -> Option<(GameRule, bool)> {
    let stripped = name.strip_prefix("minecraft:").unwrap_or(name);

    // 1. Exact snake_case match against Pumpkin's GameRule::Display
    for rule in GameRule::all() {
        if rule.to_string().eq_ignore_ascii_case(stripped) {
            return Some((rule.clone(), false));
        }
    }

    // 2. Legacy Bukkit / pre-1.21.11 camelCase aliases
    for (alias, rule, inverted) in LEGACY_GAMERULE_ALIASES {
        if *alias == stripped {
            return Some((rule.clone(), *inverted));
        }
    }

    // 3. Generic camelCase -> snake_case conversion fallback
    let mut snake = String::with_capacity(stripped.len() + 6);
    for (idx, ch) in stripped.chars().enumerate() {
        if ch.is_uppercase() {
            if idx > 0 {
                snake.push('_');
            }
            snake.push(ch.to_ascii_lowercase());
        } else {
            snake.push(ch.to_ascii_lowercase());
        }
    }
    for rule in GameRule::all() {
        if rule.to_string() == snake {
            return Some((rule.clone(), false));
        }
    }

    None
}

fn resolve_entity_type(name: &str) -> Option<&'static pumpkin_data::entity::EntityType> {
    let clean = name
        .strip_prefix("minecraft:")
        .unwrap_or(name)
        .to_ascii_lowercase();
    if let Some(et) = pumpkin_data::entity::EntityType::from_name(&clean) {
        return Some(et);
    }
    match clean.as_str() {
        "lightning" => Some(&pumpkin_data::entity::EntityType::LIGHTNING_BOLT),
        "dropped_item" => Some(&pumpkin_data::entity::EntityType::ITEM),
        "primed_tnt" => Some(&pumpkin_data::entity::EntityType::TNT),
        "mushroom_cow" => Some(&pumpkin_data::entity::EntityType::MOOSHROOM),
        "snowman" => Some(&pumpkin_data::entity::EntityType::SNOW_GOLEM),
        "ender_crystal" => Some(&pumpkin_data::entity::EntityType::END_CRYSTAL),
        "firework" => Some(&pumpkin_data::entity::EntityType::FIREWORK_ROCKET),
        "fishing_hook" => Some(&pumpkin_data::entity::EntityType::FISHING_BOBBER),
        "thrown_exp_bottle" => Some(&pumpkin_data::entity::EntityType::EXPERIENCE_BOTTLE),
        "leash_hitch" => Some(&pumpkin_data::entity::EntityType::LEASH_KNOT),
        "minecart_chest" => Some(&pumpkin_data::entity::EntityType::CHEST_MINECART),
        "minecart_furnace" => Some(&pumpkin_data::entity::EntityType::FURNACE_MINECART),
        "minecart_hopper" => Some(&pumpkin_data::entity::EntityType::HOPPER_MINECART),
        "minecart_tnt" => Some(&pumpkin_data::entity::EntityType::TNT_MINECART),
        "minecart_command" => Some(&pumpkin_data::entity::EntityType::COMMAND_BLOCK_MINECART),
        "minecart_mob_spawner" => Some(&pumpkin_data::entity::EntityType::SPAWNER_MINECART),
        _ => None,
    }
}

pub fn ffi_native_bridge_get_block_data_impl(
    request: GetBlockDataRequest,
) -> Option<GetBlockDataResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, request.y, request.z);

    let state_id = world.get_block_state(&pos).id;
    let block = pumpkin_data::Block::from_state_id(state_id);
    let key = block.name;
    let mut block_state = if key.starts_with("minecraft:") {
        key.to_string()
    } else {
        format!("minecraft:{key}")
    };

    if let Some(props) = block.properties(state_id) {
        let props = props.to_props();
        if !props.is_empty() {
            block_state.push('[');
            for (i, prop) in props.iter().enumerate() {
                if i > 0 {
                    block_state.push(',');
                }
                block_state.push_str(prop.key_str());
                block_state.push('=');
                block_state.push_str(prop.value_str());
            }
            block_state.push(']');
        }
    }

    Some(GetBlockDataResponse { block_state })
}

pub fn ffi_native_bridge_set_block_data_impl(request: SetBlockDataRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, request.y, request.z);

    let block_state_str = request.block_state;
    let clean_key = block_state_str
        .split('[')
        .next()
        .unwrap_or(&block_state_str)
        .trim_start_matches("minecraft:");

    let state_id = if let Some(b) = pumpkin_data::Block::from_registry_key(clean_key) {
        match block_state_str.split_once('[') {
            Some((_, props_str)) => {
                let props: Vec<pumpkin_data::Property> = props_str
                    .trim_end_matches(']')
                    .split(',')
                    .filter_map(|pair| pair.split_once('='))
                    .filter_map(|(k, v)| pumpkin_data::Property::from_str_pair(k.trim(), v.trim()))
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

    let flags = if request.apply_physics {
        pumpkin::world::BlockFlags::NOTIFY_ALL
    } else {
        pumpkin::world::BlockFlags::NOTIFY_LISTENERS
    };
    ensure_chunk_loaded(&world, &pos);
    world.set_block_state(&pos, state_id, flags);

    Some(())
}

pub fn ffi_native_bridge_spawn_particle_impl(request: SpawnParticleRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let clean_name = request
        .particle
        .strip_prefix("minecraft:")
        .unwrap_or(&request.particle)
        .to_ascii_lowercase();
    let particle = pumpkin_data::particle::Particle::from_name(&clean_name)
        .unwrap_or(pumpkin_data::particle::Particle::Flame);
    let position = Vector3::new(request.x, request.y, request.z);
    let offset = Vector3::new(
        request.offset_x as f32,
        request.offset_y as f32,
        request.offset_z as f32,
    );
    let max_speed = request.extra as f32;
    let count = request.count;
    let force = request.force;
    let particle_id = pumpkin_protocol::codec::var_int::VarInt(particle as i32);

    let maybe_uuid = request
        .world_uuid
        .as_ref()
        .and_then(|u| uuid::Uuid::parse_str(&u.value).ok());
    let target_player = maybe_uuid.and_then(|u| ctx.plugin_context.server.get_player_by_uuid(u));
    let world = find_world(request.world_uuid.as_ref());
    let particle_data = request.data;

    ctx.runtime.spawn(async move {
        let packet = pumpkin_protocol::java::client::play::CParticle::new(
            force,
            false,
            position,
            offset,
            max_speed,
            count,
            particle_id,
            &particle_data,
        );
        if let Some(player) = target_player {
            player.send_client_packet(&packet).await;
        } else if let Some(w) = world {
            for player in w.players.load().iter() {
                player.send_client_packet(&packet).await;
            }
        }
    });

    Some(())
}

pub fn ffi_native_bridge_get_worlds_impl(_request: EmptyRequest) -> Option<GetWorldsResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let worlds = ctx.plugin_context.server.worlds.load();
    let world_uuids = worlds
        .iter()
        .map(|w| ProtoUuid {
            value: w.uuid.to_string(),
        })
        .collect();

    Some(GetWorldsResponse { world_uuids })
}

pub fn ffi_native_bridge_get_world_border_impl(
    request: GetWorldBorderRequest,
) -> Option<WorldBorderData> {
    let world = find_world(request.world_uuid.as_ref())?;

    let wb = world
        .worldborder
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);

    Some(WorldBorderData {
        center_x: wb.center_x,
        center_z: wb.center_z,
        size: wb.old_diameter,
        target_size: wb.new_diameter,
        speed: wb.speed,
        warning_time: wb.warning_time,
        warning_blocks: wb.warning_blocks,
        damage_per_block: f64::from(wb.damage_per_block),
        damage_buffer: f64::from(wb.buffer),
        max_center_coordinate: wb.portal_teleport_boundary,
    })
}

pub fn ffi_native_bridge_set_world_border_impl(request: SetWorldBorderRequest) -> Option<()> {
    let border_data = request.border?;
    let world = find_world(request.world_uuid.as_ref())?;

    let mut wb = world
        .worldborder
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    wb.set_center(&world, border_data.center_x, border_data.center_z);
    let diameter = if border_data.target_size > 0.0 {
        border_data.target_size
    } else {
        border_data.size
    };
    let speed = (border_data.speed > 0).then_some(border_data.speed);
    if diameter > 0.0 {
        wb.set_diameter(&world, diameter, speed);
    }
    wb.set_warning_delay(&world, border_data.warning_time);
    wb.set_warning_distance(&world, border_data.warning_blocks);
    wb.damage_per_block = border_data.damage_per_block as f32;
    wb.buffer = border_data.damage_buffer as f32;
    if border_data.max_center_coordinate > 0 {
        wb.portal_teleport_boundary = border_data.max_center_coordinate;
    }

    Some(())
}

pub fn ffi_native_bridge_get_world_info_impl(
    request: GetWorldInfoRequest,
) -> Option<GetWorldInfoResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let world = find_world(request.world_uuid.as_ref())?;

    let min_height = world.dimension.min_y;
    let height = world.dimension.height;
    let max_height = min_height + height;
    let logical_height = world.dimension.logical_height;
    let sea_level = world.sea_level;
    let dimension = world.dimension.minecraft_name.to_string();

    let level_data = world.level_info.load();
    let name = level_data.level_name.clone();
    let seed = level_data.world_gen_settings.seed;
    let difficulty = format!("{:?}", level_data.difficulty);
    let hardcore = ctx.plugin_context.server.basic_config.hardcore;
    let spawn_x = level_data.spawn_x;
    let spawn_y = level_data.spawn_y;
    let spawn_z = level_data.spawn_z;
    let spawn_angle = level_data.spawn_yaw;

    let (time, full_time) = {
        let lt = world
            .level_time
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        (lt.time_of_day, lt.world_age)
    };

    let (is_storm, is_thundering, weather_duration, thunder_duration, clear_weather_duration) = {
        let w = world
            .weather
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        (
            w.raining,
            w.thundering,
            w.rain_time,
            w.thunder_time,
            w.clear_weather_time,
        )
    };

    let pvp = match world.get_game_rule(&GameRule::Pvp) {
        GameRuleValue::Bool(val) => val,
        GameRuleValue::Int(val) => val != 0,
    };

    Some(GetWorldInfoResponse {
        min_height,
        max_height,
        height,
        seed,
        name,
        dimension,
        sea_level,
        logical_height,
        difficulty,
        hardcore,
        pvp,
        spawn_x,
        spawn_y,
        spawn_z,
        spawn_angle,
        time,
        full_time,
        is_storm,
        is_thundering,
        weather_duration,
        thunder_duration,
        clear_weather_duration,
    })
}

pub fn ffi_native_bridge_set_world_time_impl(request: SetWorldTimeRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let level_time = {
        let mut lt = world
            .level_time
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        if request.time >= 0 {
            lt.set_time(request.time);
        }
        if request.full_time >= 0 {
            lt.world_age = request.full_time;
        }
        lt.clone()
    };
    level_time.send_time(&world);
    Some(())
}

pub fn ffi_native_bridge_set_world_weather_impl(request: SetWorldWeatherRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let mut w = world
        .weather
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    let clear_time = if request.clear_weather_duration > 0 {
        request.clear_weather_duration
    } else {
        w.clear_weather_time
    };
    let rain_time = if request.weather_duration > 0 {
        request.weather_duration
    } else {
        w.rain_time
    };
    w.set_weather_parameters(
        &world,
        clear_time,
        rain_time,
        request.storm,
        request.thundering,
    );
    if request.thunder_duration > 0 {
        w.thunder_time = request.thunder_duration;
    }
    Some(())
}

pub fn ffi_native_bridge_set_world_spawn_impl(request: SetWorldSpawnRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let mut level_data = (**world.level_info.load()).clone();
    level_data.spawn_x = request.x;
    level_data.spawn_y = request.y;
    level_data.spawn_z = request.z;
    level_data.spawn_yaw = request.angle;
    world.level_info.store(Arc::new(level_data));

    Some(())
}

pub fn ffi_native_bridge_set_world_difficulty_impl(
    request: SetWorldDifficultyRequest,
) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let diff = match request.difficulty.to_uppercase().as_str() {
        "PEACEFUL" => pumpkin_util::Difficulty::Peaceful,
        "EASY" => pumpkin_util::Difficulty::Easy,
        "HARD" => pumpkin_util::Difficulty::Hard,
        _ => pumpkin_util::Difficulty::Normal,
    };

    world.set_difficulty(diff);

    Some(())
}

pub fn ffi_native_bridge_set_world_pvp_impl(request: SetWorldPvpRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;
    world.set_game_rule(&GameRule::Pvp, GameRuleValue::Bool(request.pvp));
    Some(())
}

pub fn ffi_native_bridge_set_world_gamerule_impl(request: SetWorldGameruleRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;
    let Some((rule, inverted)) = resolve_gamerule(&request.rule) else {
        return Some(());
    };
    let current = world.get_game_rule(&rule);
    match current {
        GameRuleValue::Bool(_) => {
            if let Ok(mut b) = request.value.trim().to_ascii_lowercase().parse::<bool>() {
                if inverted {
                    b = !b;
                }
                world.set_game_rule(&rule, GameRuleValue::Bool(b));
            }
        }
        GameRuleValue::Int(_) => {
            if let Ok(i) = request.value.trim().parse::<i64>() {
                world.set_game_rule(&rule, GameRuleValue::Int(i));
            } else if request.rule == "doFireTick"
                && let Ok(b) = request.value.trim().to_ascii_lowercase().parse::<bool>()
            {
                world.set_game_rule(&rule, GameRuleValue::Int(if b { 128 } else { 0 }));
            }
        }
    }
    Some(())
}

pub fn ffi_native_bridge_get_world_gamerules_impl(
    request: GetWorldGamerulesRequest,
) -> Option<GetWorldGamerulesResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let mut gamerules =
        HashMap::with_capacity(GameRule::all().len() * 2 + LEGACY_GAMERULE_ALIASES.len());
    for rule in GameRule::all() {
        let key = rule.to_string();
        let val = world.get_game_rule(rule).to_string();
        let camel = snake_to_camel(&key);
        if camel != key {
            gamerules.entry(camel).or_insert_with(|| val.clone());
        }
        gamerules.insert(key, val);
    }
    for (alias, rule, inverted) in LEGACY_GAMERULE_ALIASES {
        let raw_val = world.get_game_rule(rule);
        let mapped = match raw_val {
            GameRuleValue::Bool(b) if *inverted => (!b).to_string(),
            other => other.to_string(),
        };
        gamerules.insert((*alias).to_string(), mapped);
    }
    Some(GetWorldGamerulesResponse { gamerules })
}

pub fn ffi_native_bridge_get_world_entities_impl(
    request: GetWorldEntitiesRequest,
) -> Option<GetWorldEntitiesResponse> {
    let world = find_world(request.world_uuid.as_ref())?;

    let mut entities = Vec::new();
    for p in world.players.load().iter() {
        let pos = p.living_entity.entity.pos.load();
        entities.push(EntitySummaryProto {
            uuid: Some(ProtoUuid {
                value: p.gameprofile.id.to_string(),
            }),
            entity_type: "player".to_string(),
            x: pos.x,
            y: pos.y,
            z: pos.z,
            yaw: p.living_entity.entity.yaw.load(),
            pitch: p.living_entity.entity.pitch.load(),
            is_player: true,
            custom_name: p.gameprofile.name.clone(),
            entity_id: p.living_entity.entity.entity_id,
        });
    }

    for e in world.entities.load().iter() {
        let base = e.get_entity();
        let pos = base.pos.load();
        let custom_name = base
            .custom_name
            .load()
            .as_ref()
            .as_ref()
            .map(|c| c.clone().get_text())
            .unwrap_or_default();
        entities.push(EntitySummaryProto {
            uuid: Some(ProtoUuid {
                value: base.entity_uuid.to_string(),
            }),
            entity_type: base.entity_type.resource_name.to_string(),
            x: pos.x,
            y: pos.y,
            z: pos.z,
            yaw: base.yaw.load(),
            pitch: base.pitch.load(),
            is_player: false,
            custom_name,
            entity_id: base.entity_id,
        });
    }

    Some(GetWorldEntitiesResponse { entities })
}

pub fn ffi_native_bridge_spawn_world_entity_impl(
    request: SpawnWorldEntityRequest,
) -> Option<SpawnWorldEntityResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let world = find_world(request.world_uuid.as_ref())?;

    let new_uuid = if let Some(ref u) = request.entity_uuid {
        uuid::Uuid::parse_str(&u.value).unwrap_or_else(|_| uuid::Uuid::new_v4())
    } else {
        uuid::Uuid::new_v4()
    };
    let pos = Vector3::new(request.x, request.y, request.z);

    let Some(entity_type) = resolve_entity_type(&request.entity_type) else {
        return Some(SpawnWorldEntityResponse {
            entity_uuid: None,
            success: false,
            entity_id: -1,
        });
    };

    let entity: Arc<dyn pumpkin::entity::EntityBase> = if entity_type.id
        == pumpkin_data::entity::EntityType::ITEM.id
        && request.item_stack.is_some()
    {
        let stack = super::itemstack::proto_item_to_pumpkin(request.item_stack.as_ref());
        let base = pumpkin::entity::Entity::from_uuid(new_uuid, world.clone(), pos, entity_type);
        Arc::new(pumpkin::entity::item::ItemEntity::new(base, stack))
    } else if entity_type.id == pumpkin_data::entity::EntityType::EXPERIENCE_ORB.id
        && request.experience > 0
    {
        let base = pumpkin::entity::Entity::from_uuid(new_uuid, world.clone(), pos, entity_type);
        Arc::new(pumpkin::entity::experience_orb::ExperienceOrbEntity::new(
            base,
            request.experience as u32,
        ))
    } else {
        pumpkin::entity::r#type::from_type(entity_type, pos, &world, new_uuid)
    };
    entity.get_entity().set_rotation(request.yaw, request.pitch);
    let entity_id = entity.get_entity().entity_id;

    let _enter = ctx.runtime.enter();
    world.spawn_entity(entity);

    Some(SpawnWorldEntityResponse {
        entity_uuid: Some(ProtoUuid {
            value: new_uuid.to_string(),
        }),
        success: true,
        entity_id,
    })
}

pub fn ffi_native_bridge_create_world_explosion_impl(
    request: CreateWorldExplosionRequest,
) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let pos = Vector3::new(request.x, request.y, request.z);
    let power = request.power;
    let interaction = if request.break_blocks {
        pumpkin::world::ExplosionInteraction::Block
    } else {
        pumpkin::world::ExplosionInteraction::None
    };

    world.explode(pos, power, interaction);

    Some(())
}

pub fn ffi_native_bridge_play_world_sound_impl(request: PlayWorldSoundRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let pos = Vector3::new(request.x, request.y, request.z);
    let category = resolve_sound_category(&request.category);
    if let Some(sound) = resolve_sound(&request.sound) {
        if request.seed != 0 {
            let packet = pumpkin_protocol::java::client::play::CSoundEffect::new(
                pumpkin_protocol::IdOr::Id(sound as u16),
                category,
                &pos,
                request.volume,
                request.pitch,
                request.seed,
            );
            world.broadcast_packet_all(&packet);
        } else {
            world.play_sound_raw(sound as u16, category, &pos, request.volume, request.pitch);
        }
    } else if !request.sound.is_empty() {
        world.play_custom_sound(
            &request.sound,
            category,
            &pos,
            request.volume,
            request.pitch,
        );
    }

    Some(())
}

pub fn ffi_native_bridge_set_chunk_force_loaded_impl(
    request: SetChunkForceLoadedRequest,
) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    let coord = pumpkin_util::math::vector2::Vector2::new(request.x, request.z);
    if let Ok(mut forced) = world.forced_chunks.lock() {
        if request.forced {
            forced.insert(coord);
        } else {
            forced.remove(&coord);
        }
    }

    Some(())
}

pub fn ffi_native_bridge_get_force_loaded_chunks_impl(
    request: GetForceLoadedChunksRequest,
) -> Option<GetForceLoadedChunksResponse> {
    let world = find_world(request.world_uuid.as_ref())?;

    let mut chunks = Vec::new();
    if let Ok(forced) = world.forced_chunks.lock() {
        for coord in forced.iter() {
            chunks.push(ChunkCoordProto {
                x: coord.x,
                z: coord.y,
            });
        }
    }

    Some(GetForceLoadedChunksResponse { chunks })
}

pub fn ffi_native_bridge_save_world_impl(request: SaveWorldRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let world = find_world(request.world_uuid.as_ref())?;

    ctx.runtime.spawn(async move {
        let _ = world.save().await;
    });

    Some(())
}

pub fn ffi_native_bridge_get_biome_impl(request: GetBiomeRequest) -> Option<GetBiomeResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, request.y, request.z);
    ensure_chunk_loaded(&world, &pos);
    let biome = world.get_biome(&pos);
    let name = if biome.registry_id.starts_with("minecraft:") {
        biome.registry_id.to_string()
    } else {
        format!("minecraft:{}", biome.registry_id)
    };
    Some(GetBiomeResponse { biome: name })
}

pub fn ffi_native_bridge_get_highest_block_y_impl(
    request: GetHighestBlockYRequest,
) -> Option<GetHighestBlockYResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, 0, request.z);
    ensure_chunk_loaded(&world, &pos);
    let hm = match request.heightmap {
        1 => ChunkHeightmapType::MotionBlocking,
        2 => ChunkHeightmapType::MotionBlockingNoLeaves,
        _ => ChunkHeightmapType::WorldSurface,
    };
    let y = world.get_heightmap_height(hm, request.x, request.z);
    Some(GetHighestBlockYResponse { y })
}

pub fn ffi_native_bridge_get_light_level_impl(
    request: GetLightLevelRequest,
) -> Option<GetLightLevelResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, request.y, request.z);
    ensure_chunk_loaded(&world, &pos);
    let block_light = i32::from(world.get_block_light_level(&pos).unwrap_or(0));
    let sky_light = i32::from(world.get_sky_light_level(&pos));
    Some(GetLightLevelResponse {
        block_light,
        sky_light,
    })
}

pub fn ffi_native_bridge_strike_lightning_impl(request: StrikeLightningRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = Vector3::new(request.x, request.y, request.z);
    let _enter = ctx.runtime.enter();
    world.strike_lightning(pos, request.effect_only);
    Some(())
}

pub fn ffi_native_bridge_get_block_entity_nbt_impl(
    request: GetBlockEntityNbtRequest,
) -> Option<GetBlockEntityNbtResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, request.y, request.z);
    ensure_chunk_loaded(&world, &pos);
    if let Some(compound) = world.get_block_entity_nbt(&pos) {
        let bytes = Nbt::new(String::new(), compound).write().to_vec();
        Some(GetBlockEntityNbtResponse {
            exists: true,
            nbt: bytes,
        })
    } else {
        Some(GetBlockEntityNbtResponse {
            exists: false,
            nbt: Vec::new(),
        })
    }
}

pub fn ffi_native_bridge_set_block_entity_nbt_impl(
    request: SetBlockEntityNbtRequest,
) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = BlockPos::new(request.x, request.y, request.z);
    ensure_chunk_loaded(&world, &pos);

    if request.remove || request.nbt.is_empty() {
        world.remove_block_entity(&pos);
        return Some(());
    }

    let mut reader = NbtReadHelperJava::new(Cursor::new(request.nbt.as_slice()));
    if let Ok(mut parsed) = Nbt::read(&mut reader) {
        parsed.root_tag.put_int("x", request.x);
        parsed.root_tag.put_int("y", request.y);
        parsed.root_tag.put_int("z", request.z);
        if let Some(block_entity) =
            pumpkin::block::entities::block_entity_from_nbt(&parsed.root_tag)
        {
            world.add_block_entity(block_entity);
        } else {
            world.add_block_entity_nbt(pos, &parsed.root_tag);
        }
    }

    Some(())
}
