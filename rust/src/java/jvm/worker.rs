use std::path::PathBuf;

use anyhow::bail;
use j4rs::{Instance, InvocationArg, Jvm, JvmBuilder};
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
use tokio::sync::{mpsc, oneshot};

use crate::{
    events::Event,
    java::{
        jar::read_configs_from_jar,
        jvm::commands::{JvmCommand, LoadPluginResult},
    },
    plugin::{event_manager::EventManager, manager::PluginManager},
};

pub struct JvmWorker {
    command_rx: mpsc::Receiver<JvmCommand>,
    command_tx: mpsc::Sender<JvmCommand>,
    pub plugin_manager: PluginManager,
    pub event_manager: EventManager,
    jvm: Option<j4rs::Jvm>,
}

impl JvmWorker {
    pub fn new(
        command_tx: mpsc::Sender<JvmCommand>,
        command_rx: mpsc::Receiver<JvmCommand>,
    ) -> Self {
        Self {
            command_rx,
            command_tx,
            plugin_manager: PluginManager::new(),
            event_manager: EventManager::new(),
            jvm: None,
        }
    }

    pub fn attach_thread(mut self) {
        log::info!("JVM worker thread started");

        while let Some(command) = self.command_rx.blocking_recv() {
            match command {
                JvmCommand::Initialize {
                    j4rs_path,
                    respond_to,
                } => {
                    let result = self.initialize_jvm(&j4rs_path);
                    let _ = respond_to.send(result);
                }
                JvmCommand::JavaCallback {
                    instance,
                    respond_to,
                } => {
                    let result = self.process_java_callback(instance);
                    let _ = respond_to.send(result);
                }
                JvmCommand::LoadPlugin {
                    plugin_path,
                    respond_to,
                } => {
                    let _ = match read_configs_from_jar(&plugin_path) {
                        Ok(configs) => match configs {
                            (Some(paper_plugin_config), spigot @ _) => {
                                match self.plugin_manager.load_paper_plugin(
                                    &plugin_path,
                                    &paper_plugin_config,
                                    &spigot,
                                ) {
                                    Ok(_) => {
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
                                    Ok(_) => {
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
                JvmCommand::InstantiateAllPlugins { respond_to } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let _ = respond_to.send(self.plugin_manager.instantiate_all_plugins(jvm));
                }
                JvmCommand::EnableAllPlugins { respond_to } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let _ = respond_to.send(self.plugin_manager.enable_all_plugins(jvm));
                }
                JvmCommand::DisableAllPlugins { respond_to } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let _ = respond_to.send(self.plugin_manager.disable_all_plugins(jvm));
                }
                JvmCommand::Shutdown { respond_to } => {
                    let _ = respond_to.send(self.plugin_manager.unload_all_plugins());
                    break;
                }
                JvmCommand::TriggerEvent { event, respond_to } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    match event {
                        Event::PlayerJoinEvent(player_join_event) => {
                            // let player = jvm.create_instance("org.bukkit.entity.Player", &[]);
                            // self.event_manager.call_event(&jvm, event)
                        }
                    }
                }
            }
        }

        log::info!("JVM worker thread exited");
    }

    fn process_java_callback(&mut self, instance: Instance) -> anyhow::Result<()> {
        let jvm = match self.jvm {
            Some(ref jvm) => jvm,
            None => bail!("JVM not initialized"),
        };

        let result = jvm.invoke(&instance, "getCallbackName", InvocationArg::empty())?;

        let callback_name: String = jvm.to_rust(result)?;

        match callback_name.as_str() {
            "REGISTER_EVENT_CALLBACK" => {
                let listener = jvm.cast(
                    &jvm.invoke(&instance, "getArg", &[&InvocationArg::try_from(0_i32)?])?,
                    "org.bukkit.event.Listener",
                )?;
                let plugin_instance = jvm.cast(
                    &jvm.invoke(&instance, "getArg", &[&InvocationArg::try_from(1_i32)?])?,
                    "org.bukkit.plugin.Plugin",
                )?;
                let plugin_name: String = jvm.to_rust(jvm.invoke(
                    &plugin_instance,
                    "getName",
                    InvocationArg::empty(),
                )?)?;

                match self.plugin_manager.plugins.get_mut(&plugin_name) {
                    Some(rust_plugin) => rust_plugin
                        .listeners
                        .insert("listener_name".to_string(), listener),
                    None => todo!(),
                };
            }
            _ => log::warn!("Received unknown callback: {:?}", callback_name),
        }

        Ok(())
    }

    fn initialize_jvm(&mut self, j4rs_path: &PathBuf) -> anyhow::Result<()> {
        log::info!("Initializing JVM with path: {:?}", j4rs_path);

        let jvm = JvmBuilder::new().with_base_path(j4rs_path).build()?;

        setup_patchbukkit_server(&jvm)?;

        let callback_instance =
            jvm.create_instance("org.patchbukkit.NativeCallbacks", InvocationArg::empty())?;

        let callback_receiver = jvm.init_callback_channel(&callback_instance)?;

        let command_tx = self.command_tx.clone();
        std::thread::Builder::new()
            .name("patchbukkit-jvm-callbacks".to_string())
            .spawn(move || {
                let thread_runtime = tokio::runtime::Builder::new_current_thread()
                    .enable_all()
                    .build()
                    .unwrap();

                thread_runtime.block_on(async {
                    loop {
                        match callback_receiver.rx().recv() {
                            Ok(instance) => {
                                let (sender, receiver) = oneshot::channel();
                                if command_tx
                                    .send(JvmCommand::JavaCallback {
                                        instance,
                                        respond_to: sender,
                                    })
                                    .await
                                    .is_err()
                                {
                                    log::info!(
                                        "Command channel closed, stopping callback forwarder"
                                    );
                                    break;
                                }

                                if let Err(e) = receiver.await {
                                    log::error!("Callback failed: {}", e);
                                }
                            }
                            Err(e) => {
                                log::error!("Java callback channel closed: {}", e);
                                break;
                            }
                        }
                    }
                });
            })?;

        self.jvm = Some(jvm);

        log::info!("JVM initialized successfully");
        Ok(())
    }
}

pub fn setup_patchbukkit_server(jvm: &Jvm) -> anyhow::Result<()> {
    let patchbukkit_server =
        jvm.create_instance("org.patchbukkit.PatchBukkitServer", InvocationArg::empty())?;

    jvm.invoke_static(
        "org.bukkit.Bukkit",
        "setServer",
        &[InvocationArg::from(patchbukkit_server)],
    )?;

    Ok(())
}
