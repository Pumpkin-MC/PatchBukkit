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
async fn test_live_player_and_pumpkin_state_coverage() {
    let _ = pumpkin::LOGGER_IMPL.set(None);
    let _ = tracing_subscriber::fmt()
        .with_ansi_sanitization(false)
        .try_init();

    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let prev_dir = std::env::current_dir().expect("Failed to get current dir");
    let test_plugins_dir = if prev_dir.join("java").exists() {
        prev_dir.join("java/patchbukkit/build/test-plugins")
    } else if let Some(parent) = prev_dir.parent() {
        parent.join("java/patchbukkit/build/test-plugins")
    } else {
        std::path::PathBuf::from("java/patchbukkit/build/test-plugins")
    };
    let test_plugin_jar = if prev_dir.join("java").exists() {
        prev_dir.join("java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar")
    } else if let Some(parent) = prev_dir.parent() {
        parent.join("java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar")
    } else {
        std::path::PathBuf::from(
            "java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar",
        )
    };
    assert!(
        test_plugin_jar.exists(),
        "patchbukkit-test-plugin.jar must be built first at {test_plugin_jar:?}"
    );

    std::env::set_current_dir(temp_dir.path()).expect("Failed to set current dir");

    let basic_config = BasicConfiguration {
        default_level_name: "world".to_string(),
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
            false,
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

    let dirs = setup_directories(&context, "patchbukkit-plugins")
        .expect("Failed to setup PatchBukkit directories");

    // Share Paper 26.3 cache
    let shared_paper_cache = test_plugins_dir.join("paper-cache");
    let _ = fs::create_dir_all(&shared_paper_cache);
    if let Some(cache_parent) = dirs.paper.parent() {
        let _ = fs::create_dir_all(cache_parent);
        let _ = fs::remove_dir_all(&dirs.paper);
        #[cfg(unix)]
        let _ = std::os::unix::fs::symlink(&shared_paper_cache, &dirs.paper);
    }

    fs::copy(
        &test_plugin_jar,
        dirs.plugins.join("patchbukkit-test-plugin.jar"),
    )
    .expect("Failed to copy patchbukkit-test-plugin.jar");

    patchbukkit::events::handler::JVM_READY.store(false, std::sync::atomic::Ordering::Release);
    let plugin = PatchBukkitPlugin::new();
    patchbukkit::on_load_inner(&plugin, context.clone())
        .await
        .expect("Failed to on_load PatchBukkit");

    // Wait for JVM + plugins ready
    let mut initialized = false;
    for _ in 0..200 {
        tokio::time::sleep(Duration::from_millis(200)).await;
        if !patchbukkit::events::handler::JVM_READY.load(std::sync::atomic::Ordering::Acquire) {
            continue;
        }
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
            initialized = true;
            break;
        }
    }
    assert!(initialized, "PatchBukkit JVM should be initialized");

    // Start Pumpkin network accept loop
    let server_run = pumpkin_server.clone();
    let server_task = tokio::spawn(async move {
        server_run.start().await;
    });
    tokio::time::sleep(Duration::from_millis(200)).await;

    // Connect a real Minecraft bot player
    let bot = BotClient::connect(server_addr, "CoverageBot")
        .await
        .expect("Failed to connect CoverageBot");
    bot.wait_for_play(Duration::from_secs(30))
        .await
        .expect("CoverageBot should reach Play state");
    tokio::time::sleep(Duration::from_millis(500)).await;

    let pumpkin_player = pumpkin_server
        .server
        .get_all_players()
        .into_iter()
        .find(|p| p.gameprofile.name == "CoverageBot")
        .expect("CoverageBot must exist in Pumpkin server");

    // Run the live PLAYER_BRIDGE conformance suite via Bukkit
    let (tx, rx) = oneshot::channel();
    plugin
        .command_tx
        .send(JvmCommand::TriggerCommand {
            full_command: "pbtest player_bridge".to_string(),
            command_sender: SimpleCommandSender::Console,
            respond_to: tx,
        })
        .await
        .expect("Failed to send pbtest player_bridge");
    let res = rx.await.expect("Failed to receive command response");
    assert!(res.is_ok(), "pbtest player_bridge command failed: {res:?}");

    let results_path = dirs
        .plugins
        .join("PatchBukkitTest")
        .join("pbtest-results.txt");
    assert!(
        results_path.exists(),
        "pbtest-results.txt must be generated at {results_path:?}"
    );
    let report = fs::read_to_string(&results_path).expect("Failed to read pbtest-results.txt");
    println!("=== LIVE PLAYER BRIDGE REPORT ===\n{report}");

    let header = report.lines().next().unwrap_or_default();
    let total: usize = header
        .split_whitespace()
        .find_map(|tok| tok.strip_prefix("total=").and_then(|v| v.parse().ok()))
        .expect("Missing total=");
    let failed: usize = header
        .split_whitespace()
        .find_map(|tok| tok.strip_prefix("failed=").and_then(|v| v.parse().ok()))
        .expect("Missing failed=");
    assert!(total >= 5, "Expected at least 5 live player bridge tests");
    assert_eq!(
        failed, 0,
        "Live player bridge suite had {failed} failure(s):\n{report}"
    );

    // Directly verify Pumpkin's Rust-side state reflects Bukkit mutations!
    {
        let ec_items = pumpkin_player
            .ender_chest_inventory
            .items
            .read()
            .unwrap_or_else(std::sync::PoisonError::into_inner);
        assert_eq!(
            ec_items[0].item.registry_key, "diamond",
            "Pumpkin's native ender_chest_inventory slot 0 must contain diamond set via Bukkit"
        );
        assert_eq!(
            ec_items[0].item_count, 5,
            "Pumpkin's native ender_chest_inventory slot 0 must have count 5"
        );
    }

    assert_eq!(
        pumpkin_player.hunger_manager.level.load(),
        14,
        "Pumpkin's native hunger_manager must reflect player.setFoodLevel(14)"
    );

    // Clean shutdown
    drop(bot);
    server_task.abort();
    patchbukkit::on_unload_inner(&plugin, context.clone())
        .await
        .expect("Failed to on_unload PatchBukkit");
    let _ = std::env::set_current_dir(&prev_dir);
    tokio::task::spawn_blocking(move || drop(plugin))
        .await
        .unwrap();
}
