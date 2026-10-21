mod common;

use std::fs;
use std::sync::Arc;
use std::time::Duration;

use common::bot::BotClient;
use patchbukkit::{
    PatchBukkitPlugin, commands::SimpleCommandSender, directories::setup_directories,
    java::jvm::commands::JvmCommand,
};
use pumpkin::{PumpkinServer, data::VanillaData, plugin::Context};
use pumpkin_config::{AdvancedConfiguration, BasicConfiguration, TelemetryConfig};
use tokio::sync::oneshot;

#[tokio::test(flavor = "multi_thread", worker_threads = 4)]
async fn test_fake_bot_join_and_interaction() {
    let _ = pumpkin::LOGGER_IMPL.set(None);
    let _ = tracing_subscriber::fmt()
        .with_ansi_sanitization(false)
        .try_init();
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let prev_dir = std::env::current_dir().expect("Failed to get current dir");
    std::env::set_current_dir(temp_dir.path()).expect("Failed to set current dir");

    let basic_config = BasicConfiguration {
        default_level_name: "test_bot_world".to_string(),
        ..Default::default()
    };

    let mut advanced_config = AdvancedConfiguration::default();
    advanced_config.networking.java.enabled = true;
    advanced_config.networking.java.address = "127.0.0.1:0".parse().unwrap();
    advanced_config.networking.java.online_mode = false;
    advanced_config.networking.java.encryption = false;
    advanced_config.networking.bedrock.enabled = false;
    advanced_config.commands.use_console = false;

    let vanilla_data = VanillaData::load();
    let telemetry_config = TelemetryConfig::default();
    let pumpkin_server = Arc::new(
        PumpkinServer::new(
            basic_config,
            advanced_config,
            telemetry_config,
            vanilla_data,
            vec![],
        )
        .await
        .expect("Failed to create PumpkinServer"),
    );

    let server_addr = pumpkin_server
        .tcp_listener
        .as_ref()
        .expect("TCP listener should be bound")
        .local_addr()
        .expect("Should have local address");

    println!("Pumpkin TCP server bound to ephemeral port: {server_addr}");

    let metadata = pumpkin::plugin::PluginMetadata {
        name: "patchbukkit".to_string(),
        version: "0.1.0".to_string(),
        authors: vec!["PumpkinMC".to_string()],
        description: "PatchBukkit".to_string(),
        dependencies: vec![],
        permissions: vec![],
    };

    let handlers = Arc::clone(&pumpkin_server.server.plugin_manager.handlers);
    let context = Arc::new(Context::new(
        metadata,
        pumpkin_server.server.clone(),
        handlers,
        Arc::clone(&pumpkin_server.server.plugin_manager),
        Arc::clone(&pumpkin::LOGGER_IMPL),
    ));

    // Setup directories
    let dirs = setup_directories(&context, "patchbukkit-plugins")
        .expect("Failed to setup PatchBukkit directories");

    // Copy test plugins if present
    let test_plugins_dir = if prev_dir.join("java").exists() {
        prev_dir
            .join("java")
            .join("patchbukkit")
            .join("build")
            .join("test-plugins")
    } else if let Some(parent) = prev_dir.parent() {
        parent
            .join("java")
            .join("patchbukkit")
            .join("build")
            .join("test-plugins")
    } else {
        std::path::PathBuf::from("java/patchbukkit/build/test-plugins")
    };

    let protocollib_source = test_plugins_dir.join("ProtocolLib.jar");
    if protocollib_source.exists() {
        let _ = fs::copy(&protocollib_source, dirs.plugins.join("ProtocolLib.jar"));
    }

    let veinminer_source = test_plugins_dir.join("Veinminer.jar");
    if veinminer_source.exists() {
        let _ = fs::copy(&veinminer_source, dirs.plugins.join("Veinminer.jar"));
    }

    let we_source = test_plugins_dir.join("WorldEdit.jar");
    if we_source.exists() {
        let _ = fs::copy(&we_source, dirs.plugins.join("WorldEdit.jar"));
    }

    let grim_source = test_plugins_dir.join("GrimAC.jar");
    if grim_source.exists() {
        let _ = fs::copy(&grim_source, dirs.plugins.join("GrimAC.jar"));
    }

    let plugin = PatchBukkitPlugin::new();
    patchbukkit::on_load_inner(&plugin, context.clone())
        .await
        .expect("Failed to on_load PatchBukkit");

    // Wait for JVM initialization
    for _ in 0..100 {
        tokio::time::sleep(Duration::from_millis(200)).await;
        let (tx, rx) = oneshot::channel();
        if plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: "version".to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .is_ok()
            && let Ok(res) = rx.await
            && res.is_ok()
        {
            break;
        }
    }

    // Wait for ProtocolLib to finish initializing if present
    if protocollib_source.exists() {
        for _ in 0..150 {
            tokio::time::sleep(Duration::from_millis(200)).await;
            let (tx, rx) = oneshot::channel();
            if plugin
                .command_tx
                .send(JvmCommand::TriggerCommand {
                    full_command: "protocol".to_string(),
                    command_sender: SimpleCommandSender::Console,
                    respond_to: tx,
                })
                .await
                .is_ok()
                && let Ok(res) = rx.await
                && res.is_ok()
            {
                println!("ProtocolLib command responded successfully!");
                break;
            }
        }
    }

    // Start Pumpkin server network accept loop in background
    let server_run = pumpkin_server.clone();
    let server_task = tokio::spawn(async move {
        server_run.start().await;
    });
    tokio::time::sleep(Duration::from_millis(200)).await;

    // Connect fake bot
    println!("Connecting fake bot 'TestBot_1' to {server_addr}...");
    let bot = BotClient::connect(server_addr, "TestBot_1")
        .await
        .expect("Failed to connect bot");

    // Await transition into Play state (completes Handshake -> Login -> Config -> Play)
    println!("Waiting for bot to enter Play state...");
    bot.wait_for_play(Duration::from_secs(30))
        .await
        .expect("Bot should enter Play state successfully");
    println!("Bot successfully in Play state!");

    // Verify player is registered on server
    tokio::time::sleep(Duration::from_millis(200)).await;
    let players = pumpkin_server.server.get_all_players();
    assert!(
        !players.is_empty(),
        "Server should have at least 1 player connected"
    );
    let player = players
        .iter()
        .find(|p| p.gameprofile.name == "TestBot_1")
        .expect("TestBot_1 should be registered on the server");
    assert_eq!(player.gameprofile.name, "TestBot_1");

    {
        let dispatcher = pumpkin_server.server.command_dispatcher.load();
        if grim_source.exists() {
            assert!(
                dispatcher.has_command("grim"),
                "GrimAC 'grim' command should be registered in Pumpkin CommandDispatcher"
            );
            assert!(
                !dispatcher.has_command("/grim"),
                "Normal Bukkit command 'grim' must not have a leading '/' node in CommandDispatcher"
            );
        }
        player.set_permission_lvl(
            &pumpkin_server.server,
            pumpkin_util::PermissionLvl::Four,
            &dispatcher,
        );
    }

    // Send arm swing packet from bot
    bot.swing_arm()
        .await
        .expect("Bot should be able to send SSwingArm");

    // Send movement packet from bot
    bot.move_to(0.0, 64.0, 0.0)
        .await
        .expect("Bot should be able to send SPlayerPosition");

    // Send command suggestion packet from bot
    bot.send_packet(&pumpkin_protocol::java::server::play::SCommandSuggestion {
        id: pumpkin_protocol::codec::var_int::VarInt(1),
        command: "/grim ",
    })
    .await
    .expect("Bot should be able to send SCommandSuggestion");

    // Send command packet from bot
    println!("Sending /version command...");
    bot.send_command("version")
        .await
        .expect("Bot should be able to send SChatCommand");

    println!("Sending /gamemode creative command...");
    bot.send_command("gamemode creative")
        .await
        .expect("Bot should be able to send SChatCommand");

    let chats_before = bot.system_chats.load(std::sync::atomic::Ordering::Relaxed);
    println!("Sending /grimac command...");
    bot.send_command("grimac")
        .await
        .expect("Bot should be able to send SChatCommand /grimac");

    println!("Sending /grim help command...");
    bot.send_command("grim help")
        .await
        .expect("Bot should be able to send SChatCommand");

    tokio::time::sleep(Duration::from_millis(1000)).await;
    let chats_after = bot.system_chats.load(std::sync::atomic::Ordering::Relaxed);
    println!(
        "System chat packets received from /grimac + /grim help: {}",
        chats_after - chats_before
    );
    assert!(
        chats_after > chats_before,
        "GrimAC command produced no chat output for the player"
    );
    for input in ["grimac ", "grimac h", "grim ", "grim help ", "/grim "] {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::GetCommandTabComplete {
                command_sender: SimpleCommandSender::Console,
                full_command: input.to_string(),
                respond_to: tx,
                location: None,
            })
            .await
            .unwrap();
        let res = rx.await.unwrap();
        println!(
            "TABCOMPLETE {input:?} -> {:?}",
            res.map(|v| v.into_iter().map(|s| s.suggestion).collect::<Vec<_>>())
        );
    }
    {
        let server = &pumpkin_server.server;
        let player = server.get_player_by_name("TestBot_1").expect("bot player");
        let source = player.get_command_source(server);
        for input in ["grim ", "grim", "grim h", "grim help ", "grimac "] {
            let s = server
                .command_dispatcher
                .load()
                .suggest_with_range(input, &source);
            println!(
                "DISPATCHSUGGEST {input:?} -> range={:?} {:?}",
                s.range,
                s.suggestions
                    .iter()
                    .map(|x| x.text.cached_text().clone())
                    .collect::<Vec<_>>()
            );
        }
    }
    for cmd in ["grim verbose"] {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: cmd.to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .unwrap();
        let _ = rx.await;
    }
    for cmd in ["/wand", "/pos1"] {
        let before = bot.system_chats.load(std::sync::atomic::Ordering::Relaxed);
        println!("=== bot sends SChatCommand {cmd:?} ===");
        bot.send_command_raw(cmd).await.unwrap();
        tokio::time::sleep(Duration::from_millis(800)).await;
        let after = bot.system_chats.load(std::sync::atomic::Ordering::Relaxed);
        println!("WE chat packets for {cmd:?}: {}", after - before);
    }
    for cmd in ["//wand", "/pos1"] {
        println!("=== console sends {cmd:?} ===");
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: cmd.to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .unwrap();
        let _ = rx.await;
        tokio::time::sleep(Duration::from_millis(300)).await;
    }
    println!("BOT SERVER POS: {:?}", bot.server_pos.lock().unwrap());
    println!("=== LEGIT: standing still for 60 ticks ===");
    bot.legit_ticks(60, 0.0).await.unwrap();
    {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: "pbdebug grim TestBot_1".to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .unwrap();
        let _ = rx.await;
    }
    println!("=== LEGIT: walking 0.2158 b/t for 60 ticks ===");
    bot.legit_ticks(60, 0.2158).await.unwrap();
    for cmd in ["pbdebug grim TestBot_1"] {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: cmd.to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .unwrap();
        let _ = rx.await;
    }
    println!("=== LEGIT done ===");
    tokio::time::sleep(Duration::from_millis(500)).await;
    {
        let before = bot.system_chats.load(std::sync::atomic::Ordering::Relaxed);
        let snapshot = |world: &std::sync::Arc<pumpkin::world::World>| {
            let mut v = std::collections::HashMap::new();
            let (bx, by, bz) = {
                let p = bot.server_pos.lock().unwrap().expect("bot pos").0;
                (p.x.floor() as i32, p.y.floor() as i32, p.z.floor() as i32)
            };
            for sx in bx + 8..=bx + 18 {
                for sz in bz - 3..=bz + 3 {
                    for y in by - 70..=by + 30 {
                        let st = world.get_block_state(
                            &pumpkin_util::math::position::BlockPos::new(sx, y, sz),
                        );
                        v.insert((sx, y, sz), st.id.as_u16());
                    }
                }
            }
            v
        };
        let world0 = pumpkin_server
            .server
            .worlds
            .load()
            .first()
            .cloned()
            .expect("world");
        let snap_before = snapshot(&world0);
        println!("=== WE //pos1 + //pos2 + //set stone ===");
        for cmd in ["/pos1", "/pos2", "/set stone"] {
            bot.send_command_raw(cmd).await.unwrap();
            tokio::time::sleep(Duration::from_millis(600)).await;
        }
        let after = bot.system_chats.load(std::sync::atomic::Ordering::Relaxed);
        println!("WE //set chat packets: {}", after - before);
        let world = pumpkin_server
            .server
            .worlds
            .load()
            .first()
            .cloned()
            .expect("world");
        let (x, z) = (0, 0);
        let snap_after = snapshot(&world);
        let mut changed: Vec<_> = snap_after
            .iter()
            .filter(|(k, v)| snap_before.get(*k) != Some(*v))
            .map(|(k, v)| (*k, snap_before[k], *v))
            .collect();
        changed.sort();
        println!(
            "BLOCKS CHANGED BY //set: {} {:?}",
            changed.len(),
            &changed[..changed.len().min(10)]
        );
        for y in 78..=83 {
            let state =
                world.get_block_state(&pumpkin_util::math::position::BlockPos::new(x, y, z));
            println!(
                "BLOCK AFTER //set at ({x},{y},{z}): state id {}",
                state.id.as_u16()
            );
        }
    }
    println!("Speed-hacking with the bot to see whether GrimAC flags it...");
    bot.send_command("gamemode survival").await.unwrap();
    tokio::time::sleep(Duration::from_millis(500)).await;
    for i in 0..80 {
        bot.move_to(1.0 + f64::from(i) * 4.0, 64.0, 1.0)
            .await
            .unwrap();
        tokio::time::sleep(Duration::from_millis(50)).await;
    }
    tokio::time::sleep(Duration::from_millis(1500)).await;
    for cmd in ["grim profile TestBot_1", "grim brands", "grim perf"] {
        println!("=== {cmd} ===");
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: cmd.to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .unwrap();
        let _ = rx.await;
        tokio::time::sleep(Duration::from_millis(300)).await;
    }
    println!("Verify server is still responsive after commands...");
    bot.move_to(1.0, 64.0, 1.0)
        .await
        .expect("Bot should be able to move after commands");
    tokio::time::sleep(Duration::from_millis(300)).await;

    // Disconnect bot
    bot.disconnect().await;
    tokio::time::sleep(Duration::from_millis(300)).await;

    // Gracefully stop server
    pumpkin::stop_server();
    let _ = server_task.await;

    // Gracefully unload PatchBukkit
    let _ = patchbukkit::on_unload_inner(&plugin, context.clone()).await;

    // Restore working directory
    let _ = std::env::set_current_dir(prev_dir);
    tokio::task::spawn_blocking(move || drop(plugin))
        .await
        .unwrap();
}
