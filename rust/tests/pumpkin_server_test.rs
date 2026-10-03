use patchbukkit::{
    PatchBukkitPlugin, commands::SimpleCommandSender, directories::setup_directories,
    java::jvm::commands::JvmCommand,
};
use pumpkin::{PumpkinServer, data::VanillaData, plugin::Context};
use pumpkin_config::{AdvancedConfiguration, BasicConfiguration};
use std::{fs, sync::Arc};
use tokio::sync::oneshot;

#[tokio::test]
async fn test_pumpkin_server_with_patchbukkit_and_plugins() {
    let _ = tracing_subscriber::fmt().try_init();
    let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
    let prev_dir = std::env::current_dir().expect("Failed to get current dir");
    std::env::set_current_dir(temp_dir.path()).expect("Failed to set current dir");

    let basic_config = BasicConfiguration {
        default_level_name: "test_world".to_string(),
        ..Default::default()
    };

    let mut advanced_config = AdvancedConfiguration::default();
    advanced_config.networking.java.enabled = false;
    advanced_config.networking.bedrock.enabled = false;
    advanced_config.networking.rcon.enabled = false;

    let vanilla_data = VanillaData::load();
    let telemetry_config = pumpkin_config::TelemetryConfig::default();
    let pumpkin_server = PumpkinServer::new(
        basic_config,
        advanced_config,
        telemetry_config,
        vanilla_data,
        vec![],
    )
    .await
    .expect("Failed to start PumpkinServer");

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

    // Resolve test plugins built in java/patchbukkit/build/test-plugins
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

    let packetevents_source = test_plugins_dir.join("PacketEvents.jar");
    if packetevents_source.exists() {
        fs::copy(&packetevents_source, dirs.plugins.join("PacketEvents.jar"))
            .expect("Failed to copy PacketEvents.jar to plugins dir");
        println!(
            "Copied PacketEvents.jar to plugins directory: {:?}",
            dirs.plugins
        );
    }

    let protocollib_source = test_plugins_dir.join("ProtocolLib.jar");
    if protocollib_source.exists() {
        fs::copy(&protocollib_source, dirs.plugins.join("ProtocolLib.jar"))
            .expect("Failed to copy ProtocolLib.jar to plugins dir");
        println!(
            "Copied ProtocolLib.jar to plugins directory: {:?}",
            dirs.plugins
        );
    }

    let grimac_source = test_plugins_dir.join("GrimAC.jar");
    if grimac_source.exists() {
        let _ = fs::copy(&grimac_source, dirs.plugins.join("GrimAC.jar"));
        println!("Copied GrimAC.jar to plugins directory: {:?}", dirs.plugins);
    }

    let test_plugin_source = {
        let direct = if prev_dir.join("java").exists() {
            prev_dir.join("java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar")
        } else if let Some(parent) = prev_dir.parent() {
            parent.join("java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar")
        } else {
            std::path::PathBuf::from(
                "java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar",
            )
        };
        if direct.exists() {
            direct
        } else {
            test_plugins_dir.join("patchbukkit-test-plugin.jar")
        }
    };
    if test_plugin_source.exists() {
        fs::copy(
            &test_plugin_source,
            dirs.plugins.join("patchbukkit-test-plugin.jar"),
        )
        .expect("Failed to copy patchbukkit-test-plugin.jar to plugins dir");
        println!(
            "Copied patchbukkit-test-plugin.jar to plugins directory: {:?}",
            dirs.plugins
        );
    }

    let plugin = PatchBukkitPlugin::new();

    // Call on_load_inner
    patchbukkit::on_load_inner(&plugin, context.clone())
        .await
        .expect("Failed to on_load PatchBukkit");

    // Wait for PatchBukkit JVM initialization and plugin loading to complete
    let mut initialized = false;
    for _ in 0..100 {
        tokio::time::sleep(std::time::Duration::from_millis(200)).await;
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
    assert!(
        initialized,
        "PatchBukkit JVM should be initialized and ready"
    );

    if packetevents_source.exists() {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: "packetevents".to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .expect("Failed to send packetevents TriggerCommand");

        let pe_res = rx.await.expect("Failed to receive packetevents response");
        assert!(
            pe_res.is_ok(),
            "packetevents command should execute successfully: {pe_res:?}"
        );
    }

    if grimac_source.exists() {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: "grim".to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .expect("Failed to send grim TriggerCommand");

        let grim_res = rx.await.expect("Failed to receive grim response");
        assert!(
            grim_res.is_ok(),
            "grim command should execute successfully: {grim_res:?}"
        );

        // Test PlayerJoinEvent with GrimAC and PacketEvents
        let player_uuid = uuid::Uuid::new_v4();
        let (join_tx, join_rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::FireEvent {
                payload: patchbukkit::events::handler::JvmEventPayload {
                    event: patchbukkit::proto::patchbukkit::events::Event {
                        data: Some(
                            patchbukkit::proto::patchbukkit::events::event::Data::PlayerJoin(
                                patchbukkit::proto::patchbukkit::events::PlayerJoinEvent {
                                    player_uuid: Some(
                                        patchbukkit::proto::patchbukkit::common::Uuid {
                                            value: player_uuid.to_string(),
                                        },
                                    ),
                                    join_message: String::new(),
                                    entity_id: 1,
                                },
                            ),
                        ),
                    },
                    context: patchbukkit::events::handler::EventContext {
                        server: pumpkin_server.server.clone(),
                        player: None,
                    },
                },
                plugin: "GrimAC".to_string(),
                respond_to: join_tx,
            })
            .await
            .expect("Failed to send PlayerJoin JvmCommand");

        let join_res = join_rx
            .await
            .expect("Failed to receive PlayerJoin response");
        assert!(
            !join_res.cancelled,
            "PlayerJoin should not be cancelled: {join_res:?}"
        );
    }

    // Run Bukkit API Conformance tests via patchbukkit-test-plugin
    if test_plugin_source.exists() {
        let (tx, rx) = oneshot::channel();
        plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: "pbtest all".to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .expect("Failed to send pbtest all TriggerCommand");

        let pbtest_res = rx.await.expect("Failed to receive pbtest all response");
        assert!(
            pbtest_res.is_ok(),
            "pbtest all command should execute successfully: {pbtest_res:?}"
        );
    }

    // Call on_unload_inner to gracefully shut down
    patchbukkit::on_unload_inner(&plugin, context.clone())
        .await
        .expect("Failed to on_unload PatchBukkit");

    // Restore working directory
    let _ = std::env::set_current_dir(prev_dir);

    tokio::task::spawn_blocking(move || drop(plugin))
        .await
        .unwrap();
}
