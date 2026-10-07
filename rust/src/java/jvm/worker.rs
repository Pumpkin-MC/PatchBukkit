use std::{
    path::{Path, PathBuf},
    sync::Arc,
};

use jni::{Env, InitArgsBuilder, JNIVersion, JavaVM};
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::{
    java::{
        jar::read_configs_from_jar,
        jvm::commands::{JvmCommand, LoadPluginResult},
        native_callbacks::{init_callback_context, initialize_callbacks},
        paper::ensure_paper_server,
        plugin::{
            command_manager::CommandManager, event_manager::EventManager, manager::PluginManager,
        },
    },
    proto::patchbukkit::events::FireEventResponse,
};

pub struct JvmWorker {
    command_rx: mpsc::Receiver<JvmCommand>,
    pub plugin_manager: PluginManager,
    pub event_manager: EventManager,
    pub command_manager: CommandManager,
    jvm: Option<Arc<JavaVM>>,
    context: Option<Arc<Context>>,
}

impl JvmWorker {
    #[must_use]
    pub fn new(command_rx: mpsc::Receiver<JvmCommand>) -> Self {
        Self {
            command_rx,
            plugin_manager: PluginManager::new(),
            event_manager: EventManager::new(),
            command_manager: CommandManager::new(),
            jvm: None,
            context: None,
        }
    }

    pub async fn attach_thread(mut self) {
        tracing::info!("JVM worker thread started");

        while let Some(command) = self.command_rx.recv().await {
            match command {
                JvmCommand::Initialize {
                    jassets_path,
                    paper_cache_path,
                    paper_runtime_path,
                    respond_to,
                    context,
                    runtime_handle,
                    command_tx,
                    config,
                } => {
                    init_callback_context(
                        context.clone(),
                        runtime_handle,
                        command_tx.clone(),
                        config.clone(),
                    )
                    .unwrap();
                    self.context = Some(context);
                    let result = self.initialize_jvm(
                        &jassets_path,
                        &paper_cache_path,
                        &paper_runtime_path,
                        &config,
                    );
                    let _ = respond_to.send(result);
                }
                JvmCommand::LoadPlugin {
                    plugin_path,
                    respond_to,
                } => {
                    let _ = match read_configs_from_jar(&plugin_path) {
                        Ok(configs) => match configs {
                            (Some(paper_plugin_config), spigot) => {
                                match self.plugin_manager.load_paper_plugin(
                                    &plugin_path,
                                    &paper_plugin_config,
                                    &spigot,
                                ) {
                                    Ok(()) => {
                                        respond_to.send(LoadPluginResult::SuccessfullyLoadedPaper)
                                    }
                                    Err(err) => respond_to
                                        .send(LoadPluginResult::FailedToLoadPaperPlugin(err)),
                                }
                            }
                            (None, Some(spigot)) => {
                                match self
                                    .plugin_manager
                                    .load_spigot_plugin(&plugin_path, &spigot)
                                {
                                    Ok(()) => {
                                        respond_to.send(LoadPluginResult::SuccessfullyLoadedSpigot)
                                    }
                                    Err(err) => respond_to
                                        .send(LoadPluginResult::FailedToLoadSpigotPlugin(err)),
                                }
                            }
                            (None, None) => respond_to.send(LoadPluginResult::NoConfigurationFile),
                        },
                        Err(err) => {
                            respond_to.send(LoadPluginResult::FailedToReadConfigurationFile(err))
                        }
                    };
                }
                JvmCommand::InstantiateAllPlugins {
                    plugins_dir,
                    respond_to,
                    server,
                    command_tx,
                } => {
                    let res = if let Some(ref jvm) = self.jvm {
                        let plugins_dir = plugins_dir.clone();
                        let server = server.clone();
                        let command_tx = command_tx.clone();
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            self.plugin_manager.instantiate_all_plugins(
                                env,
                                &plugins_dir,
                                &server,
                                command_tx,
                                &mut self.command_manager,
                            )
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to instantiate plugins: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::EnableAllPlugins { respond_to } => {
                    let res = if let Some(ref jvm) = self.jvm {
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            self.plugin_manager.enable_all_plugins(env)
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to enable plugins: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::DisableAllPlugins { respond_to } => {
                    let res = if let Some(ref jvm) = self.jvm {
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            self.plugin_manager.disable_all_plugins(env)
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to disable plugins: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::Shutdown { respond_to } => {
                    let _ = respond_to.send(self.plugin_manager.unload_all_plugins());
                    break;
                }
                JvmCommand::FireEvent {
                    respond_to,
                    plugin,
                    payload,
                } => {
                    let original_event = payload.event.clone();
                    let response = if let Some(ref jvm) = self.jvm {
                        match jvm.attach_current_thread(
                            |env| -> anyhow::Result<FireEventResponse> {
                                self.event_manager.fire_event(env, payload, plugin)
                            },
                        ) {
                            Ok(resp) => resp,
                            Err(e) => {
                                tracing::error!("Failed to fire event: {e}");
                                FireEventResponse {
                                    cancelled: false,
                                    data: Some(original_event),
                                }
                            }
                        }
                    } else {
                        FireEventResponse {
                            cancelled: false,
                            data: Some(original_event),
                        }
                    };

                    let _ = respond_to.send(response);
                }
                JvmCommand::TriggerCommand {
                    full_command,
                    command_sender,
                    respond_to,
                } => {
                    let res = if let Some(ref jvm) = self.jvm {
                        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
                            self.command_manager
                                .trigger_command(env, full_command, command_sender)
                        })
                        .map_err(|e| anyhow::anyhow!("Failed to trigger command: {e}"))
                    } else {
                        Err(anyhow::anyhow!("JVM is not initialized"))
                    };

                    let _ = respond_to.send(res);
                }
                JvmCommand::GetCommandTabComplete {
                    command_sender,
                    full_command,
                    respond_to,
                    location,
                } => {
                    let res = if let Some(ref jvm) = self.jvm {
                        match jvm.attach_current_thread(|env| -> anyhow::Result<_> {
                            Ok(self.command_manager.get_tab_complete(
                                env,
                                command_sender,
                                full_command,
                                location,
                            ))
                        }) {
                            Ok(inner_res) => inner_res,
                            Err(e) => {
                                tracing::error!("Failed to get tab complete: {e}");
                                None
                            }
                        }
                    } else {
                        None
                    };

                    let _ = respond_to.send(res);
                }
            }
        }

        tracing::info!("JVM worker thread exited");
    }

    fn initialize_jvm(
        &mut self,
        jassets_path: &PathBuf,
        paper_cache_path: &Path,
        paper_runtime_path: &Path,
        config: &crate::config::patchbukkit::PatchBukkitConfig,
    ) -> anyhow::Result<()> {
        tracing::info!("Initializing JVM with assets path: {jassets_path:?}");

        let mut jar_paths = Vec::new();

        for entry in walkdir::WalkDir::new(jassets_path)
            .into_iter()
            .filter_map(|e| e.ok())
            .filter(|e| e.file_type().is_file())
        {
            let path = entry.path().to_path_buf();
            if let Some(filename) = path.file_name().and_then(|n| n.to_str())
                && filename.ends_with(".jar")
            {
                jar_paths.push(path);
            }
        }

        if jar_paths.is_empty() {
            tracing::warn!(
                "No JAR files found in jassets directory ({:?})",
                jassets_path
            );
        } else {
            tracing::info!(
                "Found {} JAR entries in jassets: {:?}",
                jar_paths.len(),
                jar_paths
            );
        }

        let has_patchbukkit = jar_paths.iter().any(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .is_some_and(|n| n.contains("patchbukkit"))
        });
        if !has_patchbukkit {
            tracing::warn!(
                "patchbukkit.jar was not found in {:?}. Server classes may fail to load!",
                jassets_path
            );
        }

        // The real Paper server (CraftBukkit + NMS) is never shipped with PatchBukkit;
        // it is downloaded and patched locally. It goes after patchbukkit.jar so our
        // class overrides (e.g. SimpleCommandMap) and newer protobuf runtime take precedence.
        let paper_jars = ensure_paper_server(paper_cache_path, &config.paper)?;
        tracing::info!(
            "Using Paper {} build {} ({} classpath entries)",
            config.paper.version,
            config.paper.build,
            paper_jars.len()
        );
        jar_paths.extend(paper_jars);

        let separator = if cfg!(windows) { ";" } else { ":" };
        let classpath = jar_paths
            .iter()
            .map(|p| p.to_string_lossy())
            .collect::<Vec<_>>()
            .join(separator);

        let mut builder = InitArgsBuilder::new()
            .version(JNIVersion::V21)
            .option(format!("-Djava.class.path={classpath}"))
            .option("-XX:+IgnoreUnrecognizedVMOptions")
            .option("-XX:+EnableDynamicAgentLoading")
            .option("-Dnet.bytebuddy.experimental=true")
            .option("--enable-native-access=ALL-UNNAMED")
            .option("--enable-final-field-mutation=ALL-UNNAMED")
            .option("--add-opens=java.base/java.lang=ALL-UNNAMED")
            .option("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED")
            .option("-Dcom.google.protobuf.useUnsafe=false");

        if let Some(ref max_heap) = config.jvm.max_heap {
            builder = builder.option(format!("-Xmx{max_heap}"));
        }
        if let Some(ref initial_heap) = config.jvm.initial_heap {
            builder = builder.option(format!("-Xms{initial_heap}"));
        }
        for arg in &config.jvm.extra_args {
            builder = builder.option(arg.clone());
        }

        if config.diagnostics.debug_bridge {
            tracing::info!(
                "Configuring JVM: max_heap={:?}, initial_heap={:?}, extra_args={:?}",
                config.jvm.max_heap,
                config.jvm.initial_heap,
                config.jvm.extra_args
            );
        }

        let jvm_args = builder
            .build()
            .map_err(|e| anyhow::anyhow!("Failed to build JVM init args: {e:?}"))?;

        let jvm = match JavaVM::new(jvm_args) {
            Ok(jvm) => Arc::new(jvm),
            Err(e) => {
                if let Ok(existing) = JavaVM::singleton() {
                    tracing::info!("Reusing existing JavaVM in process");
                    Arc::new(existing)
                } else {
                    return Err(anyhow::anyhow!("Failed to create JavaVM: {e:?}"));
                }
            }
        };

        jvm.attach_current_thread(|env| -> anyhow::Result<()> {
            initialize_callbacks(env).map_err(|e| {
                tracing::error!("Failed to initialize callbacks: {e:?}");
                e
            })?;

            // Must run before PatchBukkitServer is initialized: the real CraftServer
            // sets the global registries, which can only happen once per JVM.
            boot_headless_paper(env, paper_runtime_path);

            setup_patchbukkit_server(env).map_err(|e| {
                tracing::error!("Failed to setup PatchBukkit server: {e:?}");
                e
            })?;

            Ok(())
        })
        .map_err(|e| anyhow::anyhow!("Failed to attach thread for JVM initialization: {e:?}"))?;

        self.jvm = Some(jvm);

        tracing::info!("JVM initialized successfully");
        Ok(())
    }
}

/// Boots the real Paper server headlessly (CraftBukkit + NMS, never ticked).
/// On failure PatchBukkit falls back to its hand-assembled server stubs.
fn boot_headless_paper(env: &mut Env, runtime_dir: &Path) {
    fn call(env: &mut Env, runtime_dir: &Path) -> jni::errors::Result<bool> {
        let dir = env.new_string(runtime_dir.to_string_lossy())?;
        env.call_static_method(
            jni::jni_str!("org/patchbukkit/bootstrap/HeadlessPaperServer"),
            jni::jni_str!("boot"),
            jni::jni_sig!("(Ljava/lang/String;)Z"),
            &[(&dir).into()],
        )?
        .z()
    }

    match call(env, runtime_dir) {
        Ok(true) => tracing::info!("Headless Paper server booted"),
        Ok(false) => tracing::warn!("Headless Paper server failed to boot, using fallback stubs"),
        Err(e) => {
            env.exception_describe();
            env.exception_clear();
            tracing::warn!("Failed to call HeadlessPaperServer.boot, using fallback stubs: {e:?}");
        }
    }
}

pub fn setup_patchbukkit_server(env: &mut Env) -> anyhow::Result<()> {
    if let Err(e) = env.call_static_method(
        jni::jni_str!("org/patchbukkit/PatchBukkitServer"),
        jni::jni_str!("initServer"),
        jni::jni_sig!("()Lorg/patchbukkit/PatchBukkitServer;"),
        &[],
    ) {
        env.exception_describe();
        env.exception_clear();
        return Err(anyhow::anyhow!("Failed to setup PatchBukkit server: {e:?}"));
    }

    Ok(())
}
