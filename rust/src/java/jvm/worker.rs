use std::{path::PathBuf, sync::Arc};

use j4rs::{InvocationArg, Jvm, JvmBuilder};
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::java::{
    jar::read_configs_from_jar,
    jvm::commands::{JvmCommand, LoadPluginResult},
    native_callbacks::{init_callback_context, initialize_callbacks},
    plugin::{
        command_manager::CommandManager, event_manager::EventManager, manager::PluginManager,
    },
};

pub struct JvmWorker {
    command_rx: mpsc::Receiver<JvmCommand>,
    pub plugin_manager: PluginManager,
    pub event_manager: EventManager,
    pub command_manager: CommandManager,
    jvm: Option<j4rs::Jvm>,
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
        log::info!("JVM worker thread started");

        while let Some(command) = self.command_rx.recv().await {
            match command {
                JvmCommand::Initialize {
                    j4rs_path,
                    respond_to,
                    context,
                    command_tx,
                } => {
                    init_callback_context(
                        context.clone(),
                        tokio::runtime::Handle::current(),
                        command_tx.clone(),
                    )
                    .unwrap();
                    self.context = Some(context);
                    let result = self.initialize_jvm(&j4rs_path);
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
                    respond_to,
                    server,
                    command_tx,
                } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let _ = respond_to.send(
                        self.plugin_manager
                            .instantiate_all_plugins(
                                jvm,
                                &server,
                                command_tx,
                                &mut self.command_manager,
                            )
                            .await,
                    );
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
                JvmCommand::FireEvent {
                    respond_to,
                    plugin,
                    patchbukkit_event,
                } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let cancelled =
                        match self
                            .event_manager
                            .fire_event(jvm, patchbukkit_event, plugin)
                        {
                            Ok(c) => c,
                            Err(e) => {
                                log::error!("Failed to fire event: {e}");
                                false
                            }
                        };

                    let _ = respond_to.send(cancelled);
                }
                JvmCommand::TriggerCommand {
                    full_command,
                    command_sender,
                    respond_to,
                } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let result =
                        self.command_manager
                            .trigger_command(jvm, full_command, command_sender);

                    let _ = respond_to.send(result);
                }
                JvmCommand::GetCommandTabComplete {
                    command_sender,
                    full_command,
                    respond_to,
                    location,
                } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    let result = self.command_manager.get_tab_complete(
                        jvm,
                        command_sender,
                        full_command,
                        location,
                    );

                    let _ = respond_to.send(result);
                }
            }
        }

        log::info!("JVM worker thread exited");
    }

    fn initialize_jvm(&mut self, j4rs_path: &PathBuf) -> anyhow::Result<()> {
        log::info!("Initializing JVM with path: {j4rs_path:?}");

        let jvm = JvmBuilder::new().with_base_path(j4rs_path).build()?;

        initialize_callbacks(&jvm)?;

        setup_patchbukkit_server(&jvm)?;

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
