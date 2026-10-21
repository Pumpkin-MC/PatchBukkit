use patchbukkit::{
    PatchBukkitPlugin,
    commands::SimpleCommandSender,
    directories::setup_directories,
    events::handler::{EventContext, JvmEventPayload},
    java::jvm::commands::JvmCommand,
    proto::patchbukkit::{
        common::Uuid as ProtoUuid,
        events::{
            AsyncPlayerPreLoginEvent, Event as ProtoEvent, FireEventResponse, PlayerJoinEvent,
            PlayerLeaveEvent, PlayerLoginEvent, event::Data as EventData,
        },
    },
};
use pumpkin::{PumpkinServer, data::VanillaData, plugin::Context};
use pumpkin_config::{AdvancedConfiguration, BasicConfiguration};
use std::{
    fs,
    future::Future,
    path::{Path, PathBuf},
    sync::{Arc, Mutex, Once},
    time::Duration,
};
use tokio::sync::oneshot;
use uuid::Uuid;

static CLEANUP_DIRS: Mutex<Vec<PathBuf>> = Mutex::new(Vec::new());
static ATEXIT_INIT: Once = Once::new();

#[cfg(unix)]
unsafe extern "C" {
    fn atexit(cb: extern "C" fn()) -> std::os::raw::c_int;
}

extern "C" fn cleanup_temp_dirs_on_exit() {
    let _ = std::env::set_current_dir("/tmp");
    if let Ok(mut dirs) = CLEANUP_DIRS.lock() {
        for dir in dirs.drain(..) {
            let _ = fs::remove_dir_all(&dir);
        }
    }
}

fn register_temp_dir_cleanup(path: PathBuf) {
    #[cfg(unix)]
    ATEXIT_INIT.call_once(|| unsafe {
        let _ = atexit(cleanup_temp_dirs_on_exit);
    });
    if let Ok(mut dirs) = CLEANUP_DIRS.lock() {
        dirs.push(path);
    }
}

#[allow(dead_code)]
pub struct SharedTestServer {
    runtime: tokio::runtime::Runtime,
    harness: TestServerHarness,
    test_lock: Mutex<()>,
}

#[allow(dead_code)]
impl SharedTestServer {
    pub fn boot(plugin_jars: &[&str]) -> Self {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .worker_threads(4)
            .enable_all()
            .build()
            .expect("Failed to create shared Tokio runtime");
        let harness = runtime.block_on(TestServerHarness::boot(plugin_jars));
        Self {
            runtime,
            harness,
            test_lock: Mutex::new(()),
        }
    }

    pub fn run<'a, F, Fut>(&'a self, f: F)
    where
        F: FnOnce(&'a TestServerHarness) -> Fut,
        Fut: Future<Output = ()> + 'a,
    {
        let _guard = self.test_lock.lock().unwrap_or_else(|e| e.into_inner());
        self.runtime.block_on(f(&self.harness));
    }
}

#[allow(dead_code)]
pub struct TestServerHarness {
    pub temp_dir: tempfile::TempDir,
    pub prev_dir: PathBuf,
    pub pumpkin_server: PumpkinServer,
    pub context: Arc<Context>,
    pub plugin: PatchBukkitPlugin,
    pub test_plugins_dir: PathBuf,
}

#[allow(dead_code)]
impl TestServerHarness {
    pub fn resolve_test_plugins_dir(base_dir: &Path) -> PathBuf {
        if base_dir.join("java").exists() {
            base_dir
                .join("java")
                .join("patchbukkit")
                .join("build")
                .join("test-plugins")
        } else if let Some(parent) = base_dir.parent() {
            parent
                .join("java")
                .join("patchbukkit")
                .join("build")
                .join("test-plugins")
        } else {
            PathBuf::from("java/patchbukkit/build/test-plugins")
        }
    }

    pub async fn boot(plugin_jars: &[&str]) -> Self {
        let _ = tracing_subscriber::fmt().try_init();
        let temp_dir = tempfile::tempdir().expect("Failed to create tempdir");
        register_temp_dir_cleanup(temp_dir.path().to_path_buf());
        let prev_dir = std::env::current_dir().expect("Failed to get current dir");
        let test_plugins_dir = Self::resolve_test_plugins_dir(&prev_dir);
        std::env::set_current_dir(temp_dir.path()).expect("Failed to set current dir");

        let basic_config = BasicConfiguration {
            default_level_name: "world".to_string(),
            ..Default::default()
        };

        let mut advanced_config = AdvancedConfiguration::default();
        advanced_config.networking.java.enabled = false;
        advanced_config.networking.bedrock.enabled = false;

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
        .expect("Failed to create PumpkinServer");

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

        let dirs = setup_directories(&context, "patchbukkit-plugins")
            .expect("Failed to setup PatchBukkit directories");

        // Share the patched Paper 26.3 cache across integration test processes so Paperclip
        // only downloads and patches once instead of in every temporary test directory.
        let shared_paper_cache = test_plugins_dir.join("paper-cache");
        let _ = fs::create_dir_all(&shared_paper_cache);
        if let Some(cache_parent) = dirs.paper.parent() {
            let _ = fs::create_dir_all(cache_parent);
            let _ = fs::remove_dir_all(&dirs.paper);
            #[cfg(unix)]
            let _ = std::os::unix::fs::symlink(&shared_paper_cache, &dirs.paper);
        }

        for &jar_name in plugin_jars {
            let src = if jar_name == "patchbukkit-test-plugin.jar" {
                let direct = if prev_dir.join("java").exists() {
                    prev_dir
                        .join("java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar")
                } else if let Some(parent) = prev_dir.parent() {
                    parent
                        .join("java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar")
                } else {
                    PathBuf::from(
                        "java/patchbukkit-test-plugin/build/libs/patchbukkit-test-plugin.jar",
                    )
                };
                if direct.exists() {
                    direct
                } else {
                    test_plugins_dir.join(jar_name)
                }
            } else {
                test_plugins_dir.join(jar_name)
            };

            if src.exists() {
                fs::copy(&src, dirs.plugins.join(jar_name))
                    .unwrap_or_else(|e| panic!("Failed to copy {jar_name}: {e}"));
                println!("Copied {jar_name} to plugins directory: {:?}", dirs.plugins);
            } else {
                println!("WARN: Plugin {jar_name} not found at {:?}, skipping", src);
            }
        }

        patchbukkit::events::handler::JVM_READY.store(false, std::sync::atomic::Ordering::Release);
        let plugin = PatchBukkitPlugin::new();
        patchbukkit::on_load_inner(&plugin, context.clone())
            .await
            .expect("Failed to on_load PatchBukkit");

        // Wait for PatchBukkit JVM initialization AND all plugins to finish enabling
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
        assert!(
            initialized,
            "PatchBukkit JVM should be initialized and ready"
        );

        Self {
            temp_dir,
            prev_dir,
            pumpkin_server,
            context,
            plugin,
            test_plugins_dir,
        }
    }

    pub fn has_plugin(&self, jar_name: &str) -> bool {
        self.test_plugins_dir.join(jar_name).exists()
    }

    pub async fn run_console_command(&self, full_command: &str) {
        let (tx, rx) = oneshot::channel();
        self.plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: full_command.to_string(),
                command_sender: SimpleCommandSender::Console,
                respond_to: tx,
            })
            .await
            .unwrap_or_else(|_| panic!("Failed to send console command '{full_command}'"));

        let res = rx
            .await
            .unwrap_or_else(|_| panic!("Failed to receive response for '{full_command}'"));
        assert!(
            res.is_ok(),
            "Console command '{full_command}' failed: {res:?}"
        );
    }

    pub async fn run_player_command(&self, player_uuid: Uuid, full_command: &str) {
        let (tx, rx) = oneshot::channel();
        self.plugin
            .command_tx
            .send(JvmCommand::TriggerCommand {
                full_command: full_command.to_string(),
                command_sender: SimpleCommandSender::Player(
                    player_uuid.to_string(),
                    "Player".to_string(),
                    true,
                ),
                respond_to: tx,
            })
            .await
            .unwrap_or_else(|_| panic!("Failed to send player command '{full_command}'"));

        let res = rx
            .await
            .unwrap_or_else(|_| panic!("Failed to receive response for '{full_command}'"));
        assert!(
            res.is_ok(),
            "Player command '{full_command}' failed: {res:?}"
        );
    }

    pub async fn fire_event(&self, plugin_name: &str, data: EventData) -> FireEventResponse {
        let (tx, rx) = oneshot::channel();
        self.plugin
            .command_tx
            .send(JvmCommand::FireEvent {
                payload: JvmEventPayload {
                    event: ProtoEvent { data: Some(data) },
                    context: EventContext {
                        server: self.pumpkin_server.server.clone(),
                        player: None,
                    },
                },
                plugin: plugin_name.to_string(),
                respond_to: Some(tx),
            })
            .await
            .expect("Failed to send FireEvent command");

        rx.await.expect("Failed to receive FireEvent response")
    }

    /// Simulates a full player login & join sequence (`AsyncPlayerPreLoginEvent` ->
    /// `PlayerLoginEvent` -> `PlayerJoinEvent`) across all enabled plugins.
    pub async fn simulate_player_join(&self, uuid: Uuid, name: &str, entity_id: i32) {
        let pre_login = self
            .fire_event(
                "",
                EventData::AsyncPlayerPreLogin(AsyncPlayerPreLoginEvent {
                    player_uuid: Some(ProtoUuid {
                        value: uuid.to_string(),
                    }),
                    player_name: name.to_string(),
                    ip_address: "127.0.0.1".to_string(),
                    kick_message: String::new(),
                }),
            )
            .await;
        assert!(
            !pre_login.cancelled,
            "AsyncPlayerPreLoginEvent should not be cancelled for {name}"
        );

        let login = self
            .fire_event(
                "",
                EventData::PlayerLogin(PlayerLoginEvent {
                    player_uuid: Some(ProtoUuid {
                        value: uuid.to_string(),
                    }),
                    kick_message: String::new(),
                }),
            )
            .await;
        assert!(
            !login.cancelled,
            "PlayerLoginEvent should not be cancelled for {name}"
        );

        let join = self
            .fire_event(
                "",
                EventData::PlayerJoin(PlayerJoinEvent {
                    player_uuid: Some(ProtoUuid {
                        value: uuid.to_string(),
                    }),
                    join_message: format!("{name} joined the game"),
                    entity_id,
                }),
            )
            .await;
        assert!(
            !join.cancelled,
            "PlayerJoinEvent should not be cancelled for {name}"
        );
    }

    pub async fn simulate_player_quit(&self, uuid: Uuid, name: &str) {
        let quit = self
            .fire_event(
                "",
                EventData::PlayerLeave(PlayerLeaveEvent {
                    player_uuid: Some(ProtoUuid {
                        value: uuid.to_string(),
                    }),
                    leave_message: format!("{name} left the game"),
                }),
            )
            .await;
        assert!(
            !quit.cancelled,
            "PlayerLeaveEvent should not be cancelled for {name}"
        );
    }

    pub async fn shutdown(self) {
        patchbukkit::on_unload_inner(&self.plugin, self.context.clone())
            .await
            .expect("Failed to on_unload PatchBukkit");
        let _ = std::env::set_current_dir(&self.prev_dir);
        let plugin = self.plugin;
        tokio::task::spawn_blocking(move || drop(plugin))
            .await
            .unwrap();
    }
}
