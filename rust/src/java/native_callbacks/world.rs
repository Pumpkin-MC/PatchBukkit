use pumpkin_data::world::WorldEvent;
use pumpkin_util::math::vector3::Vector3;
use std::sync::Arc;

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::{
        common::{EmptyRequest, Uuid as ProtoUuid},
        world::{
            ChunkCoordProto, CreateWorldExplosionRequest, EntitySummaryProto, GetBlockDataRequest,
            GetBlockDataResponse, GetForceLoadedChunksRequest, GetForceLoadedChunksResponse,
            GetWorldBorderRequest, GetWorldEntitiesRequest, GetWorldEntitiesResponse,
            GetWorldGamerulesRequest, GetWorldGamerulesResponse, GetWorldInfoRequest,
            GetWorldInfoResponse, GetWorldsResponse, PlayWorldEffectRequest, PlayWorldSoundRequest,
            SaveWorldRequest, SetBlockDataRequest, SetChunkForceLoadedRequest,
            SetWorldBorderRequest, SetWorldDifficultyRequest, SetWorldGameruleRequest,
            SetWorldPvpRequest, SetWorldSpawnRequest, SetWorldTimeRequest, SetWorldWeatherRequest,
            SpawnParticleRequest, SpawnWorldEntityRequest, SpawnWorldEntityResponse,
            WorldBorderData,
        },
    },
};

pub fn ffi_native_bridge_get_block_data_impl(
    request: GetBlockDataRequest,
) -> Option<GetBlockDataResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;
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
            for (i, (k, v)) in props.iter().enumerate() {
                if i > 0 {
                    block_state.push(',');
                }
                block_state.push_str(k);
                block_state.push('=');
                block_state.push_str(v);
            }
            block_state.push(']');
        }
    }

    Some(GetBlockDataResponse { block_state })
}

pub fn ffi_native_bridge_set_block_data_impl(request: SetBlockDataRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;
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
                let props: Vec<(&str, &str)> = props_str
                    .trim_end_matches(']')
                    .split(',')
                    .filter_map(|pair| pair.split_once('='))
                    .map(|(k, v)| (k.trim(), v.trim()))
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
    world.set_block_state(&pos, state_id, flags);

    Some(())
}

pub fn ffi_native_bridge_spawn_particle_impl(request: SpawnParticleRequest) -> Option<()> {
    let particle = resolve_particle(&request.particle)?;
    let pos = Vector3::new(request.x, request.y, request.z);
    let offset = Vector3::new(
        request.offset_x as f32,
        request.offset_y as f32,
        request.offset_z as f32,
    );
    let speed = request.extra as f32;
    let ctx = CALLBACK_CONTEXT.get()?;

    // Player-targeted when a UUID is attached, world broadcast otherwise.
    if let Some(uuid_proto) = request.player_uuid.as_ref() {
        let player_uuid = uuid::Uuid::parse_str(&uuid_proto.value).ok()?;
        let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;
        player.spawn_particle(pos, offset, speed, request.count, particle);
        return Some(());
    }

    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;
    world.spawn_particle(pos, offset, speed, request.count, particle);
    Some(())
}

pub fn ffi_native_bridge_play_world_effect_impl(request: PlayWorldEffectRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

    let event = world_event_from_id(request.effect_id as u16)?;
    let pos = pumpkin_util::math::position::BlockPos::new(request.x, request.y, request.z);
    world.sync_world_event(event, pos, request.data);
    Some(())
}

/// Maps a Bukkit `Particle` name (e.g. `FLAME`, `ANGRY_VILLAGER`) to
/// Pumpkin's registry particle. A few legacy Bukkit names are aliased.
fn resolve_particle(name: &str) -> Option<pumpkin_data::particle::Particle> {
    let lower = name.to_ascii_lowercase();
    let key: &str = match lower.as_str() {
        "spell" => "effect",
        "crit_magic" | "magic_crit" => "enchanted_hit",
        "spell_mob" | "spell_mob_ambient" => "entity_effect",
        "reddust" => "dust",
        "snowshovel" => "poof",
        "slime" => "item_slime",
        "footstep" => return None,
        "suspended" | "suspended_depth" | "depth_suspend" => return None,
        other => other,
    };
    pumpkin_data::particle::Particle::from_name(key)
}

pub fn ffi_native_bridge_get_worlds_impl(_request: EmptyRequest) -> Option<GetWorldsResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let worlds = ctx.plugin_context.server.worlds.load_full();
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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let border_data = request.border?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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

    let pvp = ctx.plugin_context.server.advanced_config.pvp.enabled;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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

pub fn ffi_native_bridge_set_world_pvp_impl(_request: SetWorldPvpRequest) -> Option<()> {
    Some(())
}

pub fn ffi_native_bridge_set_world_gamerule_impl(request: SetWorldGameruleRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

    let rule = resolve_game_rule(&request.rule)?;
    let value = match request.value.to_ascii_lowercase().as_str() {
        "true" => pumpkin_data::game_rules::GameRuleValue::Bool(true),
        "false" => pumpkin_data::game_rules::GameRuleValue::Bool(false),
        number => pumpkin_data::game_rules::GameRuleValue::Int(number.parse::<i64>().ok()?),
    };
    world.set_game_rule(&rule, value);

    Some(())
}

pub fn ffi_native_bridge_get_world_gamerules_impl(
    request: GetWorldGamerulesRequest,
) -> Option<GetWorldGamerulesResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

    let mut gamerules = std::collections::HashMap::new();
    for rule in pumpkin_data::game_rules::GameRule::all() {
        let name = rule.to_string();
        let value = world.get_game_rule(rule).to_string();
        // Expose the Pumpkin id and, where one exists, the vanilla
        // camelCase alias Bukkit plugins ask for (e.g. doDaylightCycle).
        gamerules.insert(name.clone(), value.clone());
        if let Some(alias) = vanilla_gamerule_alias(&name) {
            gamerules.insert(alias.to_string(), value);
        }
    }

    Some(GetWorldGamerulesResponse { gamerules })
}

/// Maps a Bukkit/vanilla gamerule name to Pumpkin's `GameRule`.
///
/// Vanilla uses camelCase (`mobGriefing`, `doDaylightCycle`) while Pumpkin
/// uses snake_case ids (`mob_griefing`, `advance_time`), and a few rules
/// were renamed outright. Matching ignores case and underscores.
fn resolve_game_rule(name: &str) -> Option<pumpkin_data::game_rules::GameRule> {
    let normalized: String = name
        .chars()
        .filter(|c| *c != '_')
        .collect::<String>()
        .to_ascii_lowercase();
    let key: &str = match normalized.as_str() {
        "dodaylightcycle" => "advancetime",
        "doweathercycle" => "advanceweather",
        "domobspawning" => "spawnmobs",
        "dotiledrops" | "doblockdrops" => "blockdrops",
        "doentitydrops" => "entitydrops",
        "naturalregeneration" => "naturalhealthregeneration",
        "announceadvancements" => "showadvancementmessages",
        "dolimitedcrafting" => "limitedcrafting",
        "dotraderspawning" => "spawnwanderingtraders",
        "dopatrolspawning" => "spawnpatrols",
        "dowardenspawning" => "spawnwardens",
        "doinsomnia" => "spawnphantoms",
        other => other,
    };
    pumpkin_data::game_rules::GameRule::all()
        .iter()
        .find(|rule| rule.to_string().replace('_', "") == key)
        .cloned()
}

/// Vanilla camelCase alias for a Pumpkin snake_case gamerule id, if the
/// names differ (used so `getWorldGamerules` answers both spellings).
fn vanilla_gamerule_alias(pumpkin_name: &str) -> Option<&'static str> {
    match pumpkin_name {
        "advance_time" => Some("doDaylightCycle"),
        "advance_weather" => Some("doWeatherCycle"),
        "spawn_mobs" => Some("doMobSpawning"),
        "block_drops" => Some("doTileDrops"),
        "entity_drops" => Some("doEntityDrops"),
        "natural_health_regeneration" => Some("naturalRegeneration"),
        "show_advancement_messages" => Some("announceAdvancements"),
        "limited_crafting" => Some("doLimitedCrafting"),
        "spawn_wandering_traders" => Some("doTraderSpawning"),
        "spawn_patrols" => Some("doPatrolSpawning"),
        "spawn_wardens" => Some("doWardenSpawning"),
        "spawn_phantoms" => Some("doInsomnia"),
        _ => None,
    }
}

/// Maps a vanilla world-event id (Bukkit Effect.getId) to Pumpkin's WorldEvent.
/// Generated from pumpkin-data's world_event.rs; unknown ids yield None.
fn world_event_from_id(id: u16) -> Option<WorldEvent> {
    match id {
        3001 => Some(WorldEvent::AnimationDragonSummonRoar),
        3000 => Some(WorldEvent::AnimationEndGatewaySpawn),
        3018 => Some(WorldEvent::AnimationSpawnCobweb),
        3014 => Some(WorldEvent::AnimationTrialSpawnerEjectItem),
        3015 => Some(WorldEvent::AnimationVaultActivate),
        3016 => Some(WorldEvent::AnimationVaultDeactivate),
        3017 => Some(WorldEvent::AnimationVaultEjectItem),
        1500 => Some(WorldEvent::ComposterFill),
        1504 => Some(WorldEvent::DripstoneDrip),
        1503 => Some(WorldEvent::EndPortalFrameFill),
        1501 => Some(WorldEvent::LavaFizz),
        3008 => Some(WorldEvent::ParticlesAndSoundBrushBlockComplete),
        1505 => Some(WorldEvent::ParticlesAndSoundPlantGrowth),
        3003 => Some(WorldEvent::ParticlesAndSoundWaxOn),
        2011 => Some(WorldEvent::ParticlesBeeGrowth),
        2001 => Some(WorldEvent::ParticlesDestroyBlock),
        2008 => Some(WorldEvent::ParticlesDragonBlockBreak),
        2006 => Some(WorldEvent::ParticlesDragonFireballSplash),
        3009 => Some(WorldEvent::ParticlesEggCrack),
        3002 => Some(WorldEvent::ParticlesElectricSpark),
        2003 => Some(WorldEvent::ParticlesEyeOfEnderDeath),
        2007 => Some(WorldEvent::ParticlesInstantPotionSplash),
        2004 => Some(WorldEvent::ParticlesMobblockSpawn),
        3005 => Some(WorldEvent::ParticlesScrape),
        3006 => Some(WorldEvent::ParticlesSculkCharge),
        3007 => Some(WorldEvent::ParticlesSculkShriek),
        2000 => Some(WorldEvent::ParticlesShootSmoke),
        2010 => Some(WorldEvent::ParticlesShootWhiteSmoke),
        2013 => Some(WorldEvent::ParticlesSmashAttack),
        2002 => Some(WorldEvent::ParticlesSpellPotionSplash),
        3020 => Some(WorldEvent::ParticlesTrialSpawnerBecomeOminous),
        3013 => Some(WorldEvent::ParticlesTrialSpawnerDetectPlayer),
        3019 => Some(WorldEvent::ParticlesTrialSpawnerDetectPlayerOminous),
        3011 => Some(WorldEvent::ParticlesTrialSpawnerSpawn),
        3021 => Some(WorldEvent::ParticlesTrialSpawnerSpawnItem),
        3012 => Some(WorldEvent::ParticlesTrialSpawnerSpawnMobAt),
        2012 => Some(WorldEvent::ParticlesTurtleEggPlacement),
        2009 => Some(WorldEvent::ParticlesWaterEvaporating),
        3004 => Some(WorldEvent::ParticlesWaxOff),
        1502 => Some(WorldEvent::RedstoneTorchBurnout),
        1029 => Some(WorldEvent::SoundAnvilBroken),
        1031 => Some(WorldEvent::SoundAnvilLand),
        1030 => Some(WorldEvent::SoundAnvilUsed),
        1025 => Some(WorldEvent::SoundBatLiftoff),
        1018 => Some(WorldEvent::SoundBlazeFireball),
        1035 => Some(WorldEvent::SoundBrewingStandBrew),
        1034 => Some(WorldEvent::SoundChorusDeath),
        1033 => Some(WorldEvent::SoundChorusGrow),
        1049 => Some(WorldEvent::SoundCrafterCraft),
        1050 => Some(WorldEvent::SoundCrafterFail),
        1000 => Some(WorldEvent::SoundDispenserDispense),
        1001 => Some(WorldEvent::SoundDispenserFail),
        1002 => Some(WorldEvent::SoundDispenserProjectileLaunch),
        1028 => Some(WorldEvent::SoundDragonDeath),
        1017 => Some(WorldEvent::SoundDragonFireball),
        1046 => Some(WorldEvent::SoundDripLavaIntoCauldron),
        1047 => Some(WorldEvent::SoundDripWaterIntoCauldron),
        1038 => Some(WorldEvent::SoundEndPortalSpawn),
        1009 => Some(WorldEvent::SoundExtinguishFire),
        1004 => Some(WorldEvent::SoundFireworkShoot),
        1016 => Some(WorldEvent::SoundGhastFireball),
        1015 => Some(WorldEvent::SoundGhastWarning),
        1042 => Some(WorldEvent::SoundGrindstoneUsed),
        1041 => Some(WorldEvent::SoundHuskToZombie),
        1043 => Some(WorldEvent::SoundPageTurn),
        1039 => Some(WorldEvent::SoundPhantomBite),
        1010 => Some(WorldEvent::SoundPlayJukeboxSong),
        1045 => Some(WorldEvent::SoundPointedDripstoneLand),
        1032 => Some(WorldEvent::SoundPortalTravel),
        1048 => Some(WorldEvent::SoundSkeletonToStray),
        1044 => Some(WorldEvent::SoundSmithingTableUsed),
        1011 => Some(WorldEvent::SoundStopJukeboxSong),
        1052 => Some(WorldEvent::SoundSulfurSpikeLand),
        1051 => Some(WorldEvent::SoundWindChargeShoot),
        1022 => Some(WorldEvent::SoundWitherBlockBreak),
        1024 => Some(WorldEvent::SoundWitherBossShoot),
        1023 => Some(WorldEvent::SoundWitherBossSpawn),
        1027 => Some(WorldEvent::SoundZombieConverted),
        1021 => Some(WorldEvent::SoundZombieDoorCrash),
        1026 => Some(WorldEvent::SoundZombieInfected),
        1020 => Some(WorldEvent::SoundZombieIronDoor),
        1040 => Some(WorldEvent::SoundZombieToDrowned),
        1019 => Some(WorldEvent::SoundZombieWoodenDoor),
        _ => None,
    }
}
pub fn ffi_native_bridge_get_world_entities_impl(
    request: GetWorldEntitiesRequest,
) -> Option<GetWorldEntitiesResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
        });
    }

    Some(GetWorldEntitiesResponse { entities })
}

pub fn ffi_native_bridge_spawn_world_entity_impl(
    request: SpawnWorldEntityRequest,
) -> Option<SpawnWorldEntityResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

    let new_uuid = uuid::Uuid::new_v4();
    let pos = Vector3::new(request.x, request.y, request.z);

    // Resolve before spawning: unknown types fail honestly (Java keeps a
    // local-only entity) instead of spawning a random pig.
    let entity_type = resolve_spawn_type(&request.entity_type)?;
    let item_stack = if std::ptr::eq(entity_type, &pumpkin_data::entity::EntityType::ITEM) {
        resolve_item_stack(&request.item_type, request.item_count)
    } else {
        None
    };

    let w = world.clone();
    ctx.runtime.spawn(async move {
        // Dropped items are built directly so the stack payload survives:
        // from_type would spawn them empty with no way to reach the stack
        // afterwards (EntityBase::as_any is not object-safe).
        let entity: std::sync::Arc<dyn pumpkin::entity::EntityBase> =
            if std::ptr::eq(entity_type, &pumpkin_data::entity::EntityType::ITEM) {
                let base =
                    pumpkin::entity::Entity::from_uuid(new_uuid, w.clone(), pos, entity_type);
                let stack = item_stack.unwrap_or_else(|| {
                    pumpkin_data::item_stack::ItemStack::new(1, &pumpkin_data::item::Item::AIR)
                });
                std::sync::Arc::new(pumpkin::entity::item::ItemEntity::new(base, stack))
            } else {
                pumpkin::entity::r#type::from_type(entity_type, pos, &w, new_uuid)
            };
        w.spawn_entity(entity);
    });

    Some(SpawnWorldEntityResponse {
        entity_uuid: Some(ProtoUuid {
            value: new_uuid.to_string(),
        }),
        success: true,
    })
}

/// Resolves a Bukkit `EntityType` name (e.g. `ZOMBIE`, `DROPPED_ITEM`) to
/// Pumpkin's registry type. Returns `None` for unresolvable names (notably
/// `PLAYER`, which cannot be spawned) instead of substituting another mob.
fn resolve_spawn_type(name: &str) -> Option<&'static pumpkin_data::entity::EntityType> {
    let lower = name.to_ascii_lowercase();
    let key: &str = match lower.as_str() {
        "dropped_item" => "item",
        "lightning" => "lightning_bolt",
        "primed_tnt" => "tnt",
        "fishing_hook" => "fishing_bobber",
        "splash_potion" | "lingering_potion" => "potion",
        "ender_signal" => "eye_of_ender",
        "thrown_exp_bottle" => "experience_bottle",
        "firework" => "firework_rocket",
        "tipped_arrow" => "arrow",
        "ender_crystal" => "end_crystal",
        "egg" => "egg",
        "ender_pearl" => "ender_pearl",
        "snowball" => "snowball",
        other => other,
    };
    pumpkin_data::entity::EntityType::from_name(key)
}

/// Builds the dropped-item stack from a vanilla item id
/// (e.g. `minecraft:diamond_sword`) and count. `None` means "spawn empty".
fn resolve_item_stack(
    item_type: &str,
    item_count: i32,
) -> Option<pumpkin_data::item_stack::ItemStack> {
    if item_type.is_empty() {
        return None;
    }
    let item = pumpkin_data::item::Item::from_registry_key(item_type)?;
    let count = item_count.clamp(1, 64) as u8;
    Some(pumpkin_data::item_stack::ItemStack::new(count, item))
}

pub fn ffi_native_bridge_create_world_explosion_impl(
    request: CreateWorldExplosionRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

    let pos = Vector3::new(request.x, request.y, request.z);
    let sound_name = request.sound;
    if let Some(sound) = pumpkin_data::sound::Sound::from_name(&sound_name) {
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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

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
    let uuid_str = &request.world_uuid.as_ref()?.value;
    let world_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;

    let worlds = ctx.plugin_context.server.worlds.load_full();
    let world = worlds
        .iter()
        .find(|w| w.uuid == world_uuid)
        .cloned()
        .or_else(|| worlds.first().cloned())?;

    let w = world.clone();
    ctx.runtime.spawn(async move {
        let _ = w.save().await;
    });

    Some(())
}
