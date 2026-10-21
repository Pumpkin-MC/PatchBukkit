use pumpkin_util::math::vector3::Vector3;
use std::collections::HashMap;
use std::sync::{Arc, LazyLock, Mutex};

use crate::{
    java::native_callbacks::{CALLBACK_CONTEXT, sound::resolve_sound},
    proto::patchbukkit::{
        common::{EmptyRequest, Uuid as ProtoUuid},
        world::{
            ChunkCoordProto, CreateWorldExplosionRequest, EntitySummaryProto, GetBlockDataRequest,
            GetBlockDataResponse, GetForceLoadedChunksRequest, GetForceLoadedChunksResponse,
            GetWorldBorderRequest, GetWorldEntitiesRequest, GetWorldEntitiesResponse,
            GetWorldGamerulesRequest, GetWorldGamerulesResponse, GetWorldInfoRequest,
            GetWorldInfoResponse, GetWorldsResponse, PlayWorldSoundRequest, SaveWorldRequest,
            SetBlockDataRequest, SetChunkForceLoadedRequest, SetWorldBorderRequest,
            SetWorldDifficultyRequest, SetWorldGameruleRequest, SetWorldPvpRequest,
            SetWorldSpawnRequest, SetWorldTimeRequest, SetWorldWeatherRequest,
            SpawnParticleRequest, SpawnWorldEntityRequest, SpawnWorldEntityResponse,
            WorldBorderData,
        },
    },
};

static WORLD_PVP_OVERRIDES: LazyLock<Mutex<HashMap<uuid::Uuid, bool>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

static WORLD_GAMERULES: LazyLock<Mutex<HashMap<uuid::Uuid, HashMap<String, String>>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn default_gamerules() -> HashMap<String, String> {
    let mut gamerules = HashMap::new();
    gamerules.insert("doDaylightCycle".to_string(), "true".to_string());
    gamerules.insert("doMobSpawning".to_string(), "true".to_string());
    gamerules.insert("doFireTick".to_string(), "true".to_string());
    gamerules.insert("keepInventory".to_string(), "false".to_string());
    gamerules.insert("mobGriefing".to_string(), "true".to_string());
    gamerules.insert("doWeatherCycle".to_string(), "true".to_string());
    gamerules.insert("randomTickSpeed".to_string(), "3".to_string());
    gamerules.insert("spawnRadius".to_string(), "10".to_string());
    gamerules.insert("announceAdvancements".to_string(), "true".to_string());
    gamerules.insert("commandBlockOutput".to_string(), "true".to_string());
    gamerules.insert("doEntityDrops".to_string(), "true".to_string());
    gamerules.insert("doImmediateRespawn".to_string(), "false".to_string());
    gamerules.insert("doInsomnia".to_string(), "true".to_string());
    gamerules.insert("doLimitedCrafting".to_string(), "false".to_string());
    gamerules.insert("doMobLoot".to_string(), "true".to_string());
    gamerules.insert("doPatrolSpawning".to_string(), "true".to_string());
    gamerules.insert("doTileDrops".to_string(), "true".to_string());
    gamerules.insert("doTraderSpawning".to_string(), "true".to_string());
    gamerules.insert("drowningDamage".to_string(), "true".to_string());
    gamerules.insert("fallDamage".to_string(), "true".to_string());
    gamerules.insert("fireDamage".to_string(), "true".to_string());
    gamerules.insert("freezeDamage".to_string(), "true".to_string());
    gamerules.insert("naturalRegeneration".to_string(), "true".to_string());
    gamerules.insert("pvp".to_string(), "true".to_string());
    gamerules.insert("sendCommandFeedback".to_string(), "true".to_string());
    gamerules.insert("showDeathMessages".to_string(), "true".to_string());
    gamerules
}

fn find_world(world_uuid_proto: Option<&ProtoUuid>) -> Option<Arc<pumpkin::world::World>> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &world_uuid_proto?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let worlds = ctx.plugin_context.server.worlds.load();
    worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())
}

pub fn ffi_native_bridge_get_block_data_impl(
    request: GetBlockDataRequest,
) -> Option<GetBlockDataResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let pos = pumpkin_util::math::position::BlockPos::new(request.x, request.y, request.z);

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
    let pos = pumpkin_util::math::position::BlockPos::new(request.x, request.y, request.z);

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
    let (chunk_coordinate, _) = pos.chunk_and_chunk_relative_position();
    if !world.level.is_chunk_loaded(&chunk_coordinate)
        && let Some(ctx) = CALLBACK_CONTEXT.get()
    {
        ctx.runtime
            .block_on(world.level.get_or_fetch_chunk(chunk_coordinate, |_| ()));
    }
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

    let wb = world.worldborder.try_lock().ok()?;

    Some(WorldBorderData {
        center_x: wb.center_x,
        center_z: wb.center_z,
        size: wb.old_diameter,
        target_size: wb.new_diameter,
        speed: wb.speed,
        warning_time: wb.warning_time,
        warning_blocks: wb.warning_blocks,
        damage_per_block: wb.damage_per_block as f64,
        damage_buffer: wb.buffer as f64,
        max_center_coordinate: wb.portal_teleport_boundary,
    })
}

pub fn ffi_native_bridge_set_world_border_impl(request: SetWorldBorderRequest) -> Option<()> {
    let border_data = request.border?;
    let world = find_world(request.world_uuid.as_ref())?;

    {
        let mut wb = world
            .worldborder
            .lock()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        wb.center_x = border_data.center_x;
        wb.center_z = border_data.center_z;
        wb.old_diameter = border_data.size;
        wb.new_diameter = border_data.target_size;
        wb.speed = border_data.speed;
        wb.warning_time = border_data.warning_time;
        wb.warning_blocks = border_data.warning_blocks;
        wb.damage_per_block = border_data.damage_per_block as f32;
        wb.buffer = border_data.damage_buffer as f32;
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
    let seed = 0i64;
    let difficulty = format!("{:?}", level_data.difficulty);
    let hardcore = false;
    let spawn_x = level_data.spawn_x;
    let spawn_y = level_data.spawn_y;
    let spawn_z = level_data.spawn_z;
    let spawn_angle = level_data.spawn_yaw;

    let (time, full_time) = if let Ok(lt) = world.level_time.try_lock() {
        (lt.time_of_day, lt.world_age)
    } else {
        (0, 0)
    };

    let (is_storm, is_thundering, weather_duration, thunder_duration, clear_weather_duration) =
        if let Ok(w) = world.weather.try_lock() {
            (
                w.raining,
                w.thundering,
                w.rain_time,
                w.thunder_time,
                w.clear_weather_time,
            )
        } else {
            (false, false, 0, 0, 0)
        };

    let pvp = WORLD_PVP_OVERRIDES
        .lock()
        .ok()
        .and_then(|g| g.get(&world.uuid).copied())
        .unwrap_or(ctx.plugin_context.server.advanced_config.pvp.enabled);

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

    if let Ok(mut lt) = world.level_time.try_lock() {
        if request.time >= 0 {
            lt.time_of_day = request.time;
        }
        if request.full_time >= 0 {
            lt.world_age = request.full_time;
        }
    }
    Some(())
}

pub fn ffi_native_bridge_set_world_weather_impl(request: SetWorldWeatherRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;

    if let Ok(mut w) = world.weather.try_lock() {
        w.raining = request.storm;
        w.thundering = request.thundering;
        if request.weather_duration > 0 {
            w.rain_time = request.weather_duration;
        }
        if request.thunder_duration > 0 {
            w.thunder_time = request.thunder_duration;
        }
        if request.clear_weather_duration > 0 {
            w.clear_weather_time = request.clear_weather_duration;
        }
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

    let mut level_data = (**world.level_info.load()).clone();
    level_data.difficulty = diff;
    world.level_info.store(Arc::new(level_data));

    Some(())
}

pub fn ffi_native_bridge_set_world_pvp_impl(request: SetWorldPvpRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;
    if let Ok(mut g) = WORLD_PVP_OVERRIDES.lock() {
        g.insert(world.uuid, request.pvp);
    }
    Some(())
}

pub fn ffi_native_bridge_set_world_gamerule_impl(request: SetWorldGameruleRequest) -> Option<()> {
    let world = find_world(request.world_uuid.as_ref())?;
    if let Ok(mut g) = WORLD_GAMERULES.lock() {
        let entry = g.entry(world.uuid).or_insert_with(default_gamerules);
        entry.insert(request.rule, request.value);
    }
    Some(())
}

pub fn ffi_native_bridge_get_world_gamerules_impl(
    request: GetWorldGamerulesRequest,
) -> Option<GetWorldGamerulesResponse> {
    let world = find_world(request.world_uuid.as_ref())?;
    let gamerules = if let Ok(mut g) = WORLD_GAMERULES.lock() {
        g.entry(world.uuid)
            .or_insert_with(default_gamerules)
            .clone()
    } else {
        default_gamerules()
    };
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
            entity_type: "PLAYER".to_string(),
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
        entities.push(EntitySummaryProto {
            uuid: Some(ProtoUuid {
                value: base.entity_uuid.to_string(),
            }),
            entity_type: format!("{:?}", base.entity_type),
            x: pos.x,
            y: pos.y,
            z: pos.z,
            yaw: base.yaw.load(),
            pitch: base.pitch.load(),
            is_player: false,
            custom_name: String::new(),
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

    let clean_type = request
        .entity_type
        .strip_prefix("minecraft:")
        .unwrap_or(&request.entity_type)
        .to_ascii_lowercase();
    let entity_type: &'static pumpkin_data::entity::EntityType =
        pumpkin_data::entity::EntityType::from_name(&clean_type).unwrap_or(
            match request.entity_type.to_uppercase().as_str() {
                "LIGHTNING_BOLT" | "LIGHTNING" => &pumpkin_data::entity::EntityType::LIGHTNING_BOLT,
                "ITEM" | "DROPPED_ITEM" => &pumpkin_data::entity::EntityType::ITEM,
                _ => &pumpkin_data::entity::EntityType::PIG,
            },
        );
    let entity: std::sync::Arc<dyn pumpkin::entity::EntityBase> = if entity_type.id
        == pumpkin_data::entity::EntityType::ITEM.id
        && request.item_stack.is_some()
    {
        let stack = super::itemstack::proto_item_to_pumpkin(request.item_stack.as_ref());
        let base = pumpkin::entity::Entity::from_uuid(new_uuid, world.clone(), pos, entity_type);
        std::sync::Arc::new(pumpkin::entity::item::ItemEntity::new(base, stack))
    } else if entity_type.id == pumpkin_data::entity::EntityType::EXPERIENCE_ORB.id
        && request.experience > 0
    {
        let base = pumpkin::entity::Entity::from_uuid(new_uuid, world.clone(), pos, entity_type);
        std::sync::Arc::new(pumpkin::entity::experience_orb::ExperienceOrbEntity::new(
            base,
            request.experience as u32,
        ))
    } else {
        pumpkin::entity::r#type::from_type(entity_type, pos, &world, new_uuid)
    };
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
    if let Some(sound) = resolve_sound(&request.sound) {
        world.play_sound_raw(
            sound as u16,
            pumpkin_data::sound::SoundCategory::Master,
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
