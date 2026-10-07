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
    let _ = tracing_subscriber::fmt().try_init();
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

    let handlers = Arc::new(arc_swap::ArcSwap::from_pointee(
        std::collections::HashMap::new(),
    ));
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

    // Send arm swing packet from bot
    bot.swing_arm()
        .await
        .expect("Bot should be able to send SSwingArm");

    // Send movement packet from bot
    bot.move_to(0.0, 64.0, 0.0)
        .await
        .expect("Bot should be able to send SPlayerPosition");

    // Send command packet from bot
    bot.send_command("version")
        .await
        .expect("Bot should be able to send SChatCommand");

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
