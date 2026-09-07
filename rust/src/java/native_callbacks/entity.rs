use std::str::FromStr;

use pumpkin::entity::EntityBase;

use crate::{
    java::native_callbacks::{CALLBACK_CONTEXT, utils::with_player},
    proto::patchbukkit::{
        common::Uuid,
        entity::{
            DamageEntityRequest, EntityHealthResponse, GetCooldownRequest, GetCooldownResponse,
            GetExperienceResponse, GetFoodLevelResponse, GetPlayerPoseStateResponse,
            KickPlayerRequest, PlayerConnectionInfoResponse, SendActionBarRequest,
            SendBlockChangeRequest, SendGameEventRequest, SendResourcePackRequest,
            SendTitleRequest, SetCompassTargetRequest, SetCooldownRequest, SetDisplayNameRequest,
            SetEntityHealthRequest, SetEntityVelocityRequest, SetExhaustionRequest,
            SetExperienceRequest, SetFoodLevelRequest, SetOpRequest,
            SetPlayerListHeaderFooterRequest, SetPlayerListNameRequest, SetPlayerTimeRequest,
            SetPlayerWeatherRequest, SetRespawnPointRequest, SetSaturationRequest,
            SetSneakingRequest, SetSprintingRequest, StopSoundRequest, TeleportEntityRequest,
        },
    },
};

pub fn ffi_native_bridge_get_entity_health_impl(request: Uuid) -> Option<EntityHealthResponse> {
    with_player(Some(&request), |player| {
        let health = f64::from(player.living_entity.health.load());

        EntityHealthResponse {
            health,
            max_health: 20.0,
        }
    })
}

pub fn ffi_native_bridge_set_entity_health_impl(request: SetEntityHealthRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let health = request.health as f32;
        player.set_health(health);
    })
}

pub fn ffi_native_bridge_damage_entity_impl(request: DamageEntityRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let amount = request.amount as f32;
        let current = player.living_entity.health.load();
        player.set_health((current - amount).max(0.0));
    })
}

pub fn ffi_native_bridge_get_entity_velocity_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::entity::EntityVelocityResponse> {
    with_player(Some(&request), |player| {
        let vel = player.living_entity.entity.velocity.load();
        crate::proto::patchbukkit::entity::EntityVelocityResponse {
            x: vel.x,
            y: vel.y,
            z: vel.z,
        }
    })
}

pub fn ffi_native_bridge_set_entity_velocity_impl(request: SetEntityVelocityRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let velocity = pumpkin_util::math::vector3::Vector3::new(request.x, request.y, request.z);
        player.living_entity.entity.set_velocity(velocity);
    })
}

pub fn ffi_native_bridge_set_entity_pose_impl(
    request: crate::proto::patchbukkit::entity::SetEntityPoseRequest,
) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        // Unknown names are ignored: the Java side already stores its
        // fixed-pose flag locally, and guessing a pose would be worse.
        if let Some(pose) = parse_pose(&request.pose) {
            player.living_entity.entity.set_pose(pose);
        }
    })
}

/// Maps a Bukkit `Pose` name (e.g. `FALL_FLYING`) to Pumpkin's `EntityPose`.
/// Matching ignores case and underscores; `SNEAKING` is Bukkit's crouch.
fn parse_pose(name: &str) -> Option<pumpkin_data::entity::EntityPose> {
    use pumpkin_data::entity::EntityPose;
    let key: String = name
        .chars()
        .filter(|c| *c != '_')
        .collect::<String>()
        .to_ascii_lowercase();
    Some(match key.as_str() {
        "standing" => EntityPose::Standing,
        "fallflying" => EntityPose::FallFlying,
        "sleeping" => EntityPose::Sleeping,
        "swimming" => EntityPose::Swimming,
        "spinattack" => EntityPose::SpinAttack,
        "crouching" | "sneaking" => EntityPose::Crouching,
        "longjumping" => EntityPose::LongJumping,
        "dying" => EntityPose::Dying,
        "croaking" => EntityPose::Croaking,
        "usingtongue" => EntityPose::UsingTongue,
        "sitting" => EntityPose::Sitting,
        "roaring" => EntityPose::Roaring,
        "sniffing" => EntityPose::Sniffing,
        "emerging" => EntityPose::Emerging,
        "digging" => EntityPose::Digging,
        "sliding" => EntityPose::Sliding,
        "shooting" => EntityPose::Shooting,
        "inhaling" => EntityPose::Inhaling,
        _ => return None,
    })
}

pub fn ffi_native_bridge_get_gamemode_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::entity::GetGamemodeResponse> {
    with_player(Some(&request), |player| {
        let gamemode = player.gamemode.load();
        crate::proto::patchbukkit::entity::GetGamemodeResponse {
            gamemode: gamemode as i32,
        }
    })
}

pub fn ffi_native_bridge_set_gamemode_impl(
    request: crate::proto::patchbukkit::entity::SetGamemodeRequest,
) -> Option<()> {
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

    with_player(request.uuid.as_ref(), |player| {
        let position = pumpkin_util::math::vector3::Vector3::new(pos.x, pos.y, pos.z);
        let world = player.living_entity.entity.world.load_full();
        let player = player.clone();
        ctx.runtime.spawn(async move {
            player.teleport(position, Some(yaw), Some(pitch), world);
        });
    })
}

pub fn ffi_native_bridge_is_on_ground_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::entity::IsOnGroundResponse> {
    with_player(Some(&request), |player| {
        let on_ground = player
            .living_entity
            .entity
            .on_ground
            .load(std::sync::atomic::Ordering::Relaxed);
        crate::proto::patchbukkit::entity::IsOnGroundResponse { on_ground }
    })
}

pub fn ffi_native_bridge_get_player_locale_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::entity::GetPlayerLocaleResponse> {
    with_player(Some(&request), |player| {
        let config = player.config.load();
        crate::proto::patchbukkit::entity::GetPlayerLocaleResponse {
            locale: config.locale.clone(),
        }
    })
}

pub fn ffi_native_bridge_is_op_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::entity::IsOpResponse> {
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
            .try_read()
            .is_ok_and(|ops| ops.get_entry(&player_uuid).is_some())
    });

    Some(crate::proto::patchbukkit::entity::IsOpResponse { is_op })
}

pub fn ffi_native_bridge_set_op_impl(request: SetOpRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let is_op = request.is_op;
    with_player(request.uuid.as_ref(), |player| {
        let op_lvl = ctx.plugin_context.server.basic_config.op_permission_level;
        let lvl = if is_op {
            op_lvl
        } else {
            pumpkin_util::PermissionLvl::Zero
        };
        player.permission_lvl.store(lvl);
    });
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
    with_player(request.uuid.as_ref(), |player| {
        let food_level = request.food_level as u8;
        player.hunger_manager.set_level(food_level);
        player.send_health();
    })
}

pub fn ffi_native_bridge_set_saturation_impl(request: SetSaturationRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let saturation = request.saturation;
        player.hunger_manager.set_saturation(saturation);
        player.send_health();
    })
}

pub fn ffi_native_bridge_set_exhaustion_impl(request: SetExhaustionRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        player.hunger_manager.set_exhaustion(request.exhaustion);
    })
}

pub fn ffi_native_bridge_get_experience_impl(request: Uuid) -> Option<GetExperienceResponse> {
    with_player(Some(&request), |player| {
        let level = player.get_experience_level();
        let progress = player.get_experience_progress();
        let total_experience = player.get_total_experience();
        GetExperienceResponse {
            level,
            progress,
            total_experience,
        }
    })
}

pub fn ffi_native_bridge_set_experience_impl(request: SetExperienceRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let level = request.level;
        let progress = request.progress;
        let total_experience = request.total_experience;
        player.set_experience(level, progress, total_experience);
    })
}

pub fn ffi_native_bridge_kick_player_impl(request: KickPlayerRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let msg = request.message;
        let text = pumpkin_util::text::TextComponent::from_legacy_string(&msg);
        player.kick(pumpkin::net::DisconnectReason::Kicked, &text);
    })
}

pub fn ffi_native_bridge_send_title_impl(request: SendTitleRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let title = request.title;
        let subtitle = request.subtitle;
        let fade_in = request.fade_in;
        let stay = request.stay;
        let fade_out = request.fade_out;
        if fade_in >= 0 && stay >= 0 && fade_out >= 0 {
            player.send_title_animation(fade_in, stay, fade_out);
        }
        if !title.is_empty() {
            let text = pumpkin_util::text::TextComponent::from_legacy_string(&title);
            player.show_title(&text, &pumpkin::entity::player::TitleMode::Title);
        }
        if !subtitle.is_empty() {
            let text = pumpkin_util::text::TextComponent::from_legacy_string(&subtitle);
            player.show_title(&text, &pumpkin::entity::player::TitleMode::SubTitle);
        }
    })
}

pub fn ffi_native_bridge_send_action_bar_impl(request: SendActionBarRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let msg = request.message;
        let text = pumpkin_util::text::TextComponent::from_legacy_string(&msg);
        player.send_system_message_raw(&text, true);
    })
}

pub fn ffi_native_bridge_reset_title_impl(request: Uuid) -> Option<()> {
    with_player(Some(&request), |player| {
        let text = pumpkin_util::text::TextComponent::text("");
        player.show_title(&text, &pumpkin::entity::player::TitleMode::Title);
        player.show_title(&text, &pumpkin::entity::player::TitleMode::SubTitle);
    })
}

pub fn ffi_native_bridge_set_display_name_impl(request: SetDisplayNameRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let name = request.display_name;
        let comp = if name.is_empty() {
            None
        } else {
            Some(pumpkin_util::text::TextComponent::from_legacy_string(&name))
        };
        player.set_display_name(comp);
    })
}

pub fn ffi_native_bridge_set_player_list_name_impl(
    request: SetPlayerListNameRequest,
) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let name = request.list_name;
        let comp = if name.is_empty() {
            None
        } else {
            Some(pumpkin_util::text::TextComponent::from_legacy_string(&name))
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
        let player = player.clone();
        ctx.runtime.spawn(async move {
            let h = pumpkin_util::text::TextComponent::from_legacy_string(&header);
            let f = pumpkin_util::text::TextComponent::from_legacy_string(&footer);
            player.set_tab_list_header_footer(&h, &f);
        });
    })
}

pub fn ffi_native_bridge_get_player_pose_state_impl(
    request: Uuid,
) -> Option<GetPlayerPoseStateResponse> {
    with_player(Some(&request), |player| {
        let is_sneaking = player
            .living_entity
            .entity
            .sneaking
            .load(std::sync::atomic::Ordering::Relaxed);
        let is_sprinting = player
            .living_entity
            .entity
            .sprinting
            .load(std::sync::atomic::Ordering::Relaxed);
        let is_gliding = player
            .living_entity
            .entity
            .fall_flying
            .load(std::sync::atomic::Ordering::Relaxed);
        let is_swimming = player
            .living_entity
            .entity
            .swimming
            .load(std::sync::atomic::Ordering::Relaxed);
        let is_sleeping = player.sleeping_since.load().is_some();
        GetPlayerPoseStateResponse {
            is_sneaking,
            is_sprinting,
            is_gliding,
            is_swimming,
            is_sleeping,
        }
    })
}

pub fn ffi_native_bridge_set_sneaking_impl(request: SetSneakingRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let sneaking = request.sneaking;
        player.living_entity.entity.set_sneaking(sneaking);
    })
}

pub fn ffi_native_bridge_set_sprinting_impl(request: SetSprintingRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let sprinting = request.sprinting;
        player.living_entity.entity.set_sprinting(sprinting);
    })
}

pub fn ffi_native_bridge_set_player_time_impl(request: SetPlayerTimeRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let time = request.time;
        let relative = request.relative;
        let player = player.clone();
        ctx.runtime.spawn(async move {
            let packet = pumpkin_protocol::java::client::play::CUpdateTime::new(0, time, relative);
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_reset_player_time_impl(request: Uuid) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(Some(&request), |player| {
        let player = player.clone();
        ctx.runtime.spawn(async move {
            let world_time = player
                .world()
                .level_time
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner)
                .time_of_day;
            let packet = pumpkin_protocol::java::client::play::CUpdateTime::new(
                world_time, world_time, true,
            );
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_set_player_weather_impl(request: SetPlayerWeatherRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let weather = request.weather;
        let player = player.clone();
        ctx.runtime.spawn(async move {
            let event = if weather == 1 {
                pumpkin_protocol::java::client::play::GameEvent::BeginRaining
            } else {
                pumpkin_protocol::java::client::play::GameEvent::EndRaining
            };
            let packet = pumpkin_protocol::java::client::play::CGameEvent::new(event, 0.0);
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_reset_player_weather_impl(request: Uuid) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(Some(&request), |player| {
        let player = player.clone();
        ctx.runtime.spawn(async move {
            let is_raining = player.world().is_raining();
            let event = if is_raining {
                pumpkin_protocol::java::client::play::GameEvent::BeginRaining
            } else {
                pumpkin_protocol::java::client::play::GameEvent::EndRaining
            };
            let packet = pumpkin_protocol::java::client::play::CGameEvent::new(event, 0.0);
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_set_compass_target_impl(request: SetCompassTargetRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let pos = request.position?;
    with_player(request.uuid.as_ref(), |player| {
        let player = player.clone();
        ctx.runtime.spawn(async move {
            let block_pos = pumpkin_util::math::position::BlockPos::new(
                pos.x as i32,
                pos.y as i32,
                pos.z as i32,
            );
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

pub fn ffi_native_bridge_get_compass_target_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::common::Vec3> {
    with_player(Some(&request), |player| {
        let info = player.world().level_info.load();
        crate::proto::patchbukkit::common::Vec3 {
            x: info.spawn_x as f64,
            y: info.spawn_y as f64,
            z: info.spawn_z as f64,
        }
    })
}

pub fn ffi_native_bridge_set_respawn_point_impl(request: SetRespawnPointRequest) -> Option<()> {
    let pos = request.position?;
    with_player(request.uuid.as_ref(), |player| {
        let block_pos =
            pumpkin_util::math::position::BlockPos::new(pos.x as i32, pos.y as i32, pos.z as i32);
        let yaw = request.yaw;
        let force = request.force;
        let dim = player.world().dimension.clone();
        player.set_respawn_point(dim, block_pos, yaw, 0.0, force);
    })
}

pub fn ffi_native_bridge_get_respawn_point_impl(
    request: Uuid,
) -> Option<crate::proto::patchbukkit::common::Location> {
    with_player(Some(&request), |player| {
        let (pos, yaw, world_uuid) = if let Ok(guard) = player.respawn_point.try_lock() {
            match *guard {
                Some(ref pt) => (
                    crate::proto::patchbukkit::common::Vec3 {
                        x: pt.position.0.x as f64,
                        y: pt.position.0.y as f64,
                        z: pt.position.0.z as f64,
                    },
                    pt.yaw,
                    player.world().uuid.to_string(),
                ),
                None => {
                    let info = player.world().level_info.load();
                    (
                        crate::proto::patchbukkit::common::Vec3 {
                            x: info.spawn_x as f64,
                            y: info.spawn_y as f64,
                            z: info.spawn_z as f64,
                        },
                        info.spawn_yaw,
                        player.world().uuid.to_string(),
                    )
                }
            }
        } else {
            let info = player.world().level_info.load();
            (
                crate::proto::patchbukkit::common::Vec3 {
                    x: info.spawn_x as f64,
                    y: info.spawn_y as f64,
                    z: info.spawn_z as f64,
                },
                info.spawn_yaw,
                player.world().uuid.to_string(),
            )
        };
        crate::proto::patchbukkit::common::Location {
            position: Some(pos),
            yaw,
            pitch: 0.0,
            world: Some(crate::proto::patchbukkit::common::World {
                uuid: Some(crate::proto::patchbukkit::common::Uuid { value: world_uuid }),
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
        let player = player.clone();
        let pos = pumpkin_util::math::position::BlockPos::new(request.x, request.y, request.z);
        let state_str = request.block_state;
        let clean_key = state_str
            .split('[')
            .next()
            .unwrap_or(&state_str)
            .trim_start_matches("minecraft:");
        let state_id = if let Some(b) = pumpkin_data::Block::from_registry_key(clean_key) {
            b.default_state.id
        } else {
            pumpkin_data::BlockStateId::new_or_air(0)
        };
        ctx.runtime.spawn(async move {
            let packet = pumpkin_protocol::java::client::play::CBlockUpdate::new(
                pos,
                pumpkin_protocol::codec::var_int::VarInt(state_id.as_u16() as i32),
            );
            player.send_client_packet(&packet).await;
        });
    })
}

pub fn ffi_native_bridge_send_resource_pack_impl(request: SendResourcePackRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let player = player.clone();
        let url = request.url;
        let prompt = request.prompt;
        let required = request.required;
        ctx.runtime.spawn(async move {
            let prompt_comp = if prompt.is_empty() {
                None
            } else {
                Some(pumpkin_util::text::TextComponent::from_legacy_string(
                    &prompt,
                ))
            };
            let id = uuid::Uuid::new_v4();
            let packet = pumpkin_protocol::java::client::play::CAddResourcePack::new(
                &id,
                &url,
                "",
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
        let ping = player.ping.load(std::sync::atomic::Ordering::Relaxed) as i32;
        let addr = player.client.address();
        PlayerConnectionInfoResponse {
            ping,
            address: addr.ip().to_string(),
            port: addr.port() as i32,
            client_brand: "vanilla".to_string(),
            player_name: player.gameprofile.name.clone(),
        }
    })
}

pub fn ffi_native_bridge_send_game_event_impl(request: SendGameEventRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    with_player(request.uuid.as_ref(), |player| {
        let player = player.clone();
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

pub fn ffi_native_bridge_set_cooldown_impl(request: SetCooldownRequest) -> Option<()> {
    with_player(request.uuid.as_ref(), |player| {
        let group = request.item_group;
        let duration = request.duration_ticks;
        player.start_cooldown(group, duration);
    })
}

pub fn ffi_native_bridge_get_cooldown_impl(
    request: GetCooldownRequest,
) -> Option<GetCooldownResponse> {
    with_player(request.uuid.as_ref(), |player| {
        let (cooldown, is_on_cooldown) = if let Ok(guard) = player.item_cooldowns.try_lock() {
            if let Some(cooldown) = guard.get(&request.item_group) {
                let current_tick = player
                    .tick_counter
                    .load(std::sync::atomic::Ordering::Relaxed);
                let elapsed = current_tick - cooldown.start_tick;
                if elapsed < cooldown.duration {
                    (1.0 - (elapsed as f32 / cooldown.duration as f32), true)
                } else {
                    (0.0, false)
                }
            } else {
                (0.0, false)
            }
        } else {
            (0.0, false)
        };
        GetCooldownResponse {
            cooldown,
            is_on_cooldown,
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
