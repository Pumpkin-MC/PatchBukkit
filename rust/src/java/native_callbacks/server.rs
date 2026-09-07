use pumpkin_util::text::TextComponent;
use std::sync::atomic::Ordering;

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::{
        common::{EmptyRequest, Uuid as ProtoUuid},
        server::{
            BanEntryProto, BroadcastMessageRequest, CreateWorldRequest, CreateWorldResponse,
            GetBanListRequest, GetBanListResponse, GetOperatorsResponse, GetWhitelistResponse,
            OperatorEntryProto, ServerInfoResponse, ServerTickInfoResponse, SetBanEntryRequest,
            SetOperatorRequest, SetServerDefaultGamemodeRequest, SetServerIdleTimeoutRequest,
            SetServerMaxPlayersRequest, SetServerMotdRequest, SetServerTickRateRequest,
            SetServerWhitelistEnforcedRequest, SetServerWhitelistRequest,
            SetWhitelistPlayerRequest, ShutdownServerRequest, UnloadWorldRequest,
            UnloadWorldResponse, WhitelistEntryProto,
        },
    },
};

pub fn ffi_native_bridge_get_server_info_impl(
    _request: EmptyRequest,
) -> Option<ServerInfoResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = &ctx.plugin_context.server;
    let java_config = &server.advanced_config.networking.java;

    let motd = java_config.motd.clone();
    let ip = java_config.address.ip().to_string();
    let port = java_config.address.port() as i32;
    let max_players = java_config.max_players as i32;
    let view_distance = java_config.view_distance.get() as i32;
    let simulation_distance = java_config.simulation_distance.get() as i32;
    let allow_flight = true;
    let allow_nether = server.basic_config.allow_nether;
    let allow_end = server.basic_config.allow_end;
    let online_mode = java_config.online_mode;
    let hardcore = server.basic_config.hardcore;
    let pvp = server.advanced_config.pvp.enabled;
    let has_whitelist = server.white_list.load(Ordering::Relaxed);
    let is_whitelist_enforced = server.basic_config.enforce_whitelist;
    let spawn_protection = 16;
    let idle_timeout = server.player_idle_timeout.load(Ordering::Relaxed);

    let default_gamemode = if let Ok(gm) = server.defaultgamemode.try_lock() {
        format!("{:?}", gm.gamemode)
    } else {
        "SURVIVAL".to_string()
    };

    Some(ServerInfoResponse {
        server_name: "Pumpkin".to_string(),
        version: "0.1.0".to_string(),
        bukkit_version: "1.21.4-R0.1-SNAPSHOT".to_string(),
        minecraft_version: "1.21.4".to_string(),
        motd,
        ip,
        port,
        max_players,
        view_distance,
        simulation_distance,
        allow_flight,
        allow_nether,
        allow_end,
        online_mode,
        hardcore,
        pvp,
        has_whitelist,
        is_whitelist_enforced,
        spawn_protection,
        default_gamemode,
        idle_timeout,
        generate_structures: true,
        max_world_size: 29999984,
    })
}

pub fn ffi_native_bridge_set_server_motd_impl(request: SetServerMotdRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    // Pumpkin has no MOTD setter; the ping response is served from a cached
    // status object, so rewrite it there to make setMotd() observable.
    if let Ok(mut status) = ctx.plugin_context.server.get_status().lock() {
        status.status_response.description = TextComponent::from_legacy_string(&request.motd);
    }
    Some(())
}

pub fn ffi_native_bridge_set_server_max_players_impl(
    request: SetServerMaxPlayersRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    if let Ok(mut status) = ctx.plugin_context.server.get_status().lock()
        && let Some(players) = &mut status.status_response.players
    {
        players.max = request.max_players.max(0) as u32;
    }
    Some(())
}

/// Explicit `setWhitelistEnforced` override. Pumpkin's
/// `basic_config.enforce_whitelist` has no write path, so the bridge keeps
/// one: -1 means "follow the config file".
static WHITELIST_ENFORCE_OVERRIDE: std::sync::atomic::AtomicI8 =
    std::sync::atomic::AtomicI8::new(-1);

pub fn ffi_native_bridge_set_server_whitelist_impl(
    request: SetServerWhitelistRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    ctx.plugin_context
        .server
        .white_list
        .store(request.enabled, Ordering::Relaxed);
    // Vanilla behavior: enabling the whitelist with enforcement evicts
    // everyone not on it (mirrors /whitelist on).
    if request.enabled {
        let enforced = match WHITELIST_ENFORCE_OVERRIDE.load(Ordering::Relaxed) {
            0 => false,
            1 => true,
            _ => ctx.plugin_context.server.basic_config.enforce_whitelist,
        };
        if enforced {
            kick_non_whitelisted_players(&ctx.plugin_context.server);
        }
    }
    Some(())
}

pub fn ffi_native_bridge_set_server_whitelist_enforced_impl(
    request: SetServerWhitelistEnforcedRequest,
) -> Option<()> {
    WHITELIST_ENFORCE_OVERRIDE.store(i8::from(request.enforced), Ordering::Relaxed);
    Some(())
}

/// Kick every online player that is neither whitelisted nor an operator.
/// Same rule as Pumpkin's `/whitelist on` path (whose helper is private to
/// its command module, hence replicated here).
fn kick_non_whitelisted_players(server: &pumpkin::server::Server) {
    let mut allowed = std::collections::HashSet::new();
    if let Ok(list) = server.data.whitelist_config.try_read() {
        for entry in &list.whitelist {
            allowed.insert(entry.uuid);
        }
    }
    if let Ok(ops) = server.data.operator_config.try_read() {
        for op in &ops.ops {
            allowed.insert(op.uuid);
        }
    }
    for player in server.get_all_players() {
        if !allowed.contains(&player.gameprofile.id) {
            player.kick(
                pumpkin::net::DisconnectReason::Kicked,
                &TextComponent::text("You are not white-listed on this server!"),
            );
        }
    }
}

pub fn ffi_native_bridge_set_server_idle_timeout_impl(
    request: SetServerIdleTimeoutRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    ctx.plugin_context
        .server
        .player_idle_timeout
        .store(request.timeout_minutes, Ordering::Relaxed);
    Some(())
}

pub fn ffi_native_bridge_set_server_default_gamemode_impl(
    request: SetServerDefaultGamemodeRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    if let Ok(mut gm) = ctx.plugin_context.server.defaultgamemode.try_lock() {
        let mode = match request.gamemode.to_uppercase().as_str() {
            "CREATIVE" => pumpkin_util::GameMode::Creative,
            "ADVENTURE" => pumpkin_util::GameMode::Adventure,
            "SPECTATOR" => pumpkin_util::GameMode::Spectator,
            _ => pumpkin_util::GameMode::Survival,
        };
        gm.gamemode = mode;
    }
    Some(())
}

pub fn ffi_native_bridge_get_server_tick_info_impl(
    _request: EmptyRequest,
) -> Option<ServerTickInfoResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = &ctx.plugin_context.server;

    let tick_rate = server.tick_rate_manager.tickrate();
    let is_frozen = server.tick_rate_manager.is_frozen();
    let is_sprinting = server.tick_rate_manager.is_sprinting();
    let is_stepping = server.tick_rate_manager.is_stepping_forward();
    let tick_count = server.tick_count.load(Ordering::Relaxed) as i64;
    let avg_nanos = server.aggregated_tick_times_nanos.load(Ordering::Relaxed);
    let average_tick_time = (avg_nanos as f64) / 1_000_000.0 / 100.0;

    let mut tick_times_nanos = Vec::new();
    if let Ok(times) = server.tick_times_nanos.try_lock() {
        tick_times_nanos.extend_from_slice(&*times);
    } else {
        tick_times_nanos.resize(100, 50_000_000);
    }

    let tps_val = (1_000.0 / (average_tick_time.max(50.0))).min(tick_rate as f64);
    let tps = vec![tps_val, tps_val, tps_val];

    Some(ServerTickInfoResponse {
        tps,
        tick_rate,
        is_frozen,
        is_sprinting,
        is_stepping,
        average_tick_time,
        tick_count,
        tick_times_nanos,
    })
}

pub fn ffi_native_bridge_set_server_tick_rate_impl(
    request: SetServerTickRateRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = &ctx.plugin_context.server;
    if request.tick_rate > 0.0 {
        server
            .tick_rate_manager
            .set_tick_rate(server, request.tick_rate);
    }
    server.tick_rate_manager.set_frozen(server, request.frozen);
    Some(())
}

pub fn ffi_native_bridge_shutdown_server_impl(request: ShutdownServerRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = ctx.plugin_context.server.clone();
    ctx.runtime.spawn(async move {
        if request.save {
            let _ = server.save_all().await;
        }
        server.shutdown().await;
    });
    Some(())
}

pub fn ffi_native_bridge_broadcast_message_impl(request: BroadcastMessageRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = ctx.plugin_context.server.clone();
    let text = TextComponent::text(request.message);
    let sender = TextComponent::text("Server");
    server.broadcast_message(&text, &sender, 0, None);
    Some(())
}

pub fn ffi_native_bridge_get_whitelist_impl(
    _request: EmptyRequest,
) -> Option<GetWhitelistResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let mut players = Vec::new();
    if let Ok(config) = ctx.plugin_context.server.data.whitelist_config.try_read() {
        for entry in &config.whitelist {
            players.push(WhitelistEntryProto {
                uuid: Some(ProtoUuid {
                    value: entry.uuid.to_string(),
                }),
                name: entry.name.clone(),
            });
        }
    }
    Some(GetWhitelistResponse { players })
}

pub fn ffi_native_bridge_set_whitelist_player_impl(
    request: SetWhitelistPlayerRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.uuid.as_ref()?.value;
    let target_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let target_name = request.name;
    let server = ctx.plugin_context.server.clone();

    let mut config = server
        .data
        .whitelist_config
        .write()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if request.whitelisted {
        if !config.whitelist.iter().any(|e| e.uuid == target_uuid) {
            config
                .whitelist
                .push(pumpkin_config::whitelist::WhitelistEntry {
                    uuid: target_uuid,
                    name: target_name,
                });
        }
    } else {
        config.whitelist.retain(|e| e.uuid != target_uuid);
    }
    Some(())
}

pub fn ffi_native_bridge_get_operators_impl(
    _request: EmptyRequest,
) -> Option<GetOperatorsResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let mut operators = Vec::new();
    if let Ok(config) = ctx.plugin_context.server.data.operator_config.try_read() {
        for op in &config.ops {
            operators.push(OperatorEntryProto {
                uuid: Some(ProtoUuid {
                    value: op.uuid.to_string(),
                }),
                name: op.name.clone(),
                level: op.level as i32,
                bypasses_player_limit: op.bypasses_player_limit,
            });
        }
    }
    Some(GetOperatorsResponse { operators })
}

pub fn ffi_native_bridge_set_operator_impl(request: SetOperatorRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.uuid.as_ref()?.value;
    let target_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let target_name = request.name;
    let level = match request.level {
        1 => pumpkin_util::permission::PermissionLvl::One,
        2 => pumpkin_util::permission::PermissionLvl::Two,
        3 => pumpkin_util::permission::PermissionLvl::Three,
        _ => pumpkin_util::permission::PermissionLvl::Four,
    };
    let server = ctx.plugin_context.server.clone();

    let mut config = server
        .data
        .operator_config
        .write()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if request.is_op {
        if let Some(op) = config.ops.iter_mut().find(|e| e.uuid == target_uuid) {
            op.level = level;
        } else {
            config.ops.push(pumpkin_config::op::Op {
                uuid: target_uuid,
                name: target_name,
                level,
                bypasses_player_limit: false,
            });
        }
    } else {
        config.ops.retain(|e| e.uuid != target_uuid);
    }
    Some(())
}

pub fn ffi_native_bridge_get_ban_list_impl(
    request: GetBanListRequest,
) -> Option<GetBanListResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let mut entries = Vec::new();

    if request.ban_type == "IP" {
        if let Ok(list) = ctx.plugin_context.server.data.banned_ip_list.try_read() {
            for entry in &list.banned_ips {
                let created = entry.created.unix_timestamp();
                let expires = entry.expires.map(|e| e.unix_timestamp()).unwrap_or(0);
                entries.push(BanEntryProto {
                    target: entry.ip.to_string(),
                    source: entry.source.clone(),
                    created,
                    expires,
                    reason: entry.reason.clone(),
                });
            }
        }
    } else {
        if let Ok(list) = ctx.plugin_context.server.data.banned_player_list.try_read() {
            for entry in &list.banned_players {
                let created = entry.created.unix_timestamp();
                let expires = entry.expires.map(|e| e.unix_timestamp()).unwrap_or(0);
                entries.push(BanEntryProto {
                    target: entry.name.clone(),
                    source: entry.source.clone(),
                    created,
                    expires,
                    reason: entry.reason.clone(),
                });
            }
        }
    }

    Some(GetBanListResponse { entries })
}

pub fn ffi_native_bridge_set_ban_entry_impl(request: SetBanEntryRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = ctx.plugin_context.server.clone();

    if let Some(entry) = request.entry {
        if request.ban_type == "IP" {
            if let Ok(ip) = entry.target.parse::<std::net::IpAddr>() {
                let mut list = server
                    .data
                    .banned_ip_list
                    .write()
                    .unwrap_or_else(std::sync::PoisonError::into_inner);
                if request.remove {
                    list.banned_ips.retain(|e| e.ip != ip);
                } else {
                    list.banned_ips.retain(|e| e.ip != ip);
                    let expires = if entry.expires > 0 {
                        time::OffsetDateTime::from_unix_timestamp(entry.expires).ok()
                    } else {
                        None
                    };
                    list.banned_ips
                        .push(pumpkin::data::banlist_serializer::BannedIpEntry {
                            ip,
                            created: time::OffsetDateTime::now_utc(),
                            source: entry.source,
                            expires,
                            reason: entry.reason,
                        });
                }
            }
        } else {
            let mut list = server
                .data
                .banned_player_list
                .write()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            let target_name = entry.target.clone();
            if request.remove {
                list.banned_players
                    .retain(|e| !e.name.eq_ignore_ascii_case(&target_name));
            } else {
                list.banned_players
                    .retain(|e| !e.name.eq_ignore_ascii_case(&target_name));
                let expires = if entry.expires > 0 {
                    time::OffsetDateTime::from_unix_timestamp(entry.expires).ok()
                } else {
                    None
                };
                list.banned_players
                    .push(pumpkin::data::banlist_serializer::BannedPlayerEntry {
                        uuid: uuid::Uuid::nil(),
                        name: target_name,
                        created: time::OffsetDateTime::now_utc(),
                        source: entry.source,
                        expires,
                        reason: entry.reason,
                    });
            }
        }
    }
    Some(())
}

pub fn ffi_native_bridge_create_world_impl(
    request: CreateWorldRequest,
) -> Option<CreateWorldResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = ctx.plugin_context.server.clone();
    let name = request.name;
    let dim = match request.dimension.as_str() {
        "minecraft:the_nether" => pumpkin_data::dimension::Dimension::THE_NETHER,
        "minecraft:the_end" => pumpkin_data::dimension::Dimension::THE_END,
        _ => pumpkin_data::dimension::Dimension::OVERWORLD,
    };

    let world_name = name.clone();
    let (tx, rx) = tokio::sync::oneshot::channel();
    ctx.runtime.spawn(async move {
        let world = server.create_world(world_name, dim);
        let _ = tx.send(world.uuid);
    });

    let world_uuid = rx.blocking_recv().ok()?;
    Some(CreateWorldResponse {
        world_uuid: Some(ProtoUuid {
            value: world_uuid.to_string(),
        }),
        name,
        success: true,
    })
}

pub fn ffi_native_bridge_unload_world_impl(
    request: UnloadWorldRequest,
) -> Option<UnloadWorldResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = ctx.plugin_context.server.clone();
    let name = request.world_name;
    let (tx, rx) = tokio::sync::oneshot::channel();
    ctx.runtime.spawn(async move {
        let res = server.unload_world(&name).await;
        let _ = tx.send(res.is_ok());
    });
    let success = rx.blocking_recv().unwrap_or(false);
    Some(UnloadWorldResponse { success })
}

pub fn ffi_native_bridge_save_all_worlds_impl(_request: EmptyRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let server = ctx.plugin_context.server.clone();
    ctx.runtime.spawn(async move {
        let _ = server.save_all().await;
    });
    Some(())
}
