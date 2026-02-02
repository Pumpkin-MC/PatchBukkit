use std::{path::PathBuf, sync::Arc};

use j4rs::{InvocationArg, Jvm, JvmBuilder};
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::{
    events::Event,
    java::{
        jar::read_configs_from_jar,
        jvm::commands::{JvmCommand, LoadPluginResult},
        native_callbacks::{init_callback_context, initialize_callbacks},
        plugin::{
            command_manager::CommandManager, event_manager::EventManager, manager::PluginManager,
        },
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
                } => {
                    init_callback_context(context.clone(), tokio::runtime::Handle::current())
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
                JvmCommand::TriggerEvent {
                    event,
                    respond_to: _,
                } => {
                    let jvm = match self.jvm {
                        Some(ref jvm) => jvm,
                        None => &Jvm::attach_thread().unwrap(),
                    };

                    match event {
                        Event::PlayerJoinEvent(player_join_event) => {
                            let server_instance = jvm
                                .invoke_static(
                                    "org.bukkit.Bukkit",
                                    "getServer",
                                    InvocationArg::empty(),
                                )
                                .map_err(|e| format!("Failed to get server: {}", e))
                                .unwrap();

                            // let cloned_server_instance =
                            //     jvm.clone_instance(&server_instance).unwrap();

                            // let j_entity = jvm
                            //     .create_instance(
                            //         "org.patchbukkit.entity.PatchBukkitEntity",
                            //         &[
                            //             InvocationArg::from(cloned_server_instance),
                            //             // InvocationArg::from(InvocationArg::empty()), // Placeholder for the actual NMS entity
                            //         ],
                            //     )
                            //     .unwrap();
                            // let cloned_server_instance =
                            //     jvm.clone_instance(&server_instance).unwrap();

                            let player = player_join_event.player;

                            let j_uuid = jvm
                                .invoke_static(
                                    "java.util.UUID",
                                    "fromString",
                                    &[InvocationArg::try_from(player.gameprofile.id.to_string())
                                        .unwrap()],
                                )
                                .map_err(|e| format!("Failed to create Java UUID: {}", e))
                                .unwrap();

                            let j_player = jvm
                                .create_instance(
                                    "org.patchbukkit.entity.PatchBukkitPlayer",
                                    &[
                                        InvocationArg::from(j_uuid),
                                        InvocationArg::try_from(player.gameprofile.name.clone())
                                            .unwrap(),
                                    ],
                                )
                                .map_err(|e| format!("Failed to create player instance: {}", e))
                                .unwrap();

                            let player_permission_level = player.permission_lvl.load();
                            if player_permission_level
                                >= self
                                    .context
                                    .as_ref()
                                    .unwrap()
                                    .server
                                    .basic_config
                                    .op_permission_level
                            {
                                jvm.invoke(
                                    &j_player,
                                    "setOp",
                                    &[InvocationArg::try_from(true)
                                        .unwrap()
                                        .into_primitive()
                                        .unwrap()],
                                )
                                .map_err(|e| {
                                    format!("Failed to give operator status to new player: {}", e)
                                })
                                .unwrap();
                            };

                            let patch_server = jvm
                                .cast(&server_instance, "org.patchbukkit.PatchBukkitServer")
                                .unwrap();

                            jvm.invoke(
                                &patch_server,
                                "registerPlayer",
                                &[InvocationArg::from(j_player)],
                            )
                            .unwrap();
                            // self.event_manager.call_event(&jvm, event)
                        }
                    }
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
        log::info!("Initializing JVM with path: {:?}", j4rs_path);

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
