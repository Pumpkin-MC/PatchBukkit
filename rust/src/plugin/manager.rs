use std::{
    collections::HashMap,
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
};

use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};
use pumpkin::{
    command::tree::{CommandTree, builder::literal},
    plugin::Context,
};
use pumpkin_util::permission::{Permission, PermissionDefault};
use tokio::sync::mpsc;

use crate::{
    config::{
        paper::PaperPluginYml,
        spigot::{Command, SpigotPluginYml},
    },
    java::jvm::{
        command_executor::{JavaCommandExecutor, SimpleCommandSender},
        commands::JvmCommand,
    },
};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PluginState {
    /// Plugin config has been loaded, but not yet initialized in JVM
    Registered,
    /// Plugin class has been loaded and instance created
    Loaded,
    /// Plugin is enabled (onEnable called)
    Enabled,
    /// Plugin is disabled (onDisable called)
    Disabled,
    /// Plugin failed to load or enable
    Errored,
}

#[derive(Debug)]
pub enum PluginType {
    Paper(PaperPluginData),
    Spigot(SpigotPluginData),
}

#[derive(Debug)]
pub struct PaperPluginData {
    pub paper_config: PaperPluginYml,
    pub spigot_config: Option<SpigotPluginYml>,
}

#[derive(Debug)]
pub struct SpigotPluginData {
    pub spigot_config: SpigotPluginYml,
}

pub struct Plugin {
    /// Unique plugin name
    pub name: String,
    /// Plugin version
    pub version: String,
    /// Main class fully qualified name
    pub main_class: String,
    /// Path to the JAR file
    pub path: PathBuf,
    /// Plugin-specific data (Paper or Spigot)
    pub plugin_type: PluginType,
    /// Current state
    pub state: PluginState,
    /// Data folder for this plugin
    pub data_folder: PathBuf,
    pub instance: Option<Instance>,
    // The registered commands
    pub commands: HashMap<String, Command>,

    pub listeners: HashMap<String, Instance>,
}

pub struct PluginManager {
    pub plugins: HashMap<String, Plugin>,
}

impl PluginManager {
    pub fn new() -> Self {
        Self {
            plugins: HashMap::new(),
        }
    }

    pub fn add_plugin(&mut self, plugin: Plugin) {
        self.plugins.insert(plugin.name.clone(), plugin);
    }

    pub fn load_paper_plugin<P: AsRef<Path>>(
        &mut self,
        jar_path: P,
        paper_plugin_config: &str,
        spigot_plugin_config: &Option<String>,
    ) -> Result<()> {
        let parsed_paper_plugin = PaperPluginYml::from_str(paper_plugin_config)?;
        let parsed_spigot_plugin = match spigot_plugin_config {
            Some(config) => Some(SpigotPluginYml::from_str(config)?),
            None => None,
        };

        let plugin = Plugin {
            name: parsed_paper_plugin.name.clone(),
            version: parsed_paper_plugin.version.clone(),
            main_class: parsed_paper_plugin.main.clone(),
            plugin_type: PluginType::Paper(PaperPluginData {
                paper_config: parsed_paper_plugin,
                spigot_config: parsed_spigot_plugin,
            }),
            state: PluginState::Registered,
            data_folder: jar_path.as_ref().parent().unwrap().join("data"),
            path: jar_path.as_ref().to_path_buf(),
            instance: None,
            commands: HashMap::new(),
            listeners: HashMap::new(),
        };

        self.add_plugin(plugin);
        Ok(())
    }

    pub fn load_spigot_plugin<P: AsRef<Path>>(
        &mut self,
        jar_path: P,
        spigot_plugin_config: &str,
    ) -> Result<()> {
        let parsed_spigot_plugin = SpigotPluginYml::from_str(spigot_plugin_config)?;

        let plugin = Plugin {
            name: parsed_spigot_plugin.name.clone(),
            version: parsed_spigot_plugin.version.clone(),
            main_class: parsed_spigot_plugin.main.clone(),
            plugin_type: PluginType::Spigot(SpigotPluginData {
                spigot_config: parsed_spigot_plugin.clone(),
            }),
            state: PluginState::Registered,
            data_folder: jar_path.as_ref().parent().unwrap().join("data"),
            path: jar_path.as_ref().to_path_buf(),
            instance: None,
            commands: parsed_spigot_plugin.commands.unwrap_or(HashMap::new()),
            listeners: HashMap::new(),
        };

        self.add_plugin(plugin);
        Ok(())
    }

    pub fn enable_all_plugins(&mut self, jvm: &Jvm) -> Result<()> {
        // IMPORANT: enable trough PluginManager not manually
        let plugin_manager = jvm.invoke_static(
            "org.bukkit.Bukkit",
            "getPluginManager",
            InvocationArg::empty(),
        )?;
        for (_plugin_name, plugin) in &mut self.plugins {
            let plugin_instance = plugin.instance.as_ref().unwrap();
            let plugin_instance = jvm.clone_instance(&plugin_instance).unwrap();

            let result = jvm.invoke(
                &plugin_manager,
                "enablePlugin",
                &[InvocationArg::from(plugin_instance)],
            );

            match result {
                Ok(_) => {
                    plugin.state = PluginState::Enabled;
                    log::info!("Enabled PatchBukkit plugin: {}", plugin.name);
                }
                Err(e) => {
                    plugin.state = PluginState::Errored;
                    log::error!(
                        "Failed to enable PatchBukkit plugin {}: {:?}",
                        plugin.name,
                        e
                    );
                }
            }
        }
        Ok(())
    }

    pub fn disable_all_plugins(&mut self, jvm: &Jvm) -> Result<()> {
        let plugin_manager = jvm.invoke_static(
            "org.bukkit.Bukkit",
            "getPluginManager",
            InvocationArg::empty(),
        )?;
        for (_plugin_name, plugin) in &mut self.plugins {
            let plugin_instance = plugin.instance.as_ref().unwrap();
            let plugin_instance = jvm.clone_instance(&plugin_instance).unwrap();

            let result = jvm.invoke(
                &plugin_manager,
                "disablePlugin",
                &[InvocationArg::from(plugin_instance)],
            );

            match result {
                Ok(_) => {
                    plugin.state = PluginState::Disabled;
                    log::info!("Disabled PatchBukkit plugin: {}", plugin.name);
                }
                Err(e) => {
                    plugin.state = PluginState::Disabled;
                    log::error!(
                        "Failed to disable PatchBukkit plugin {}: {:?}",
                        plugin.name,
                        e
                    );
                }
            }
        }
        Ok(())
    }

    pub fn trigger_command(
        &self,
        jvm: &Jvm,
        cmd_name: &str,
        _command: Arc<Mutex<Instance>>,
        sender: SimpleCommandSender,
        _args: Vec<String>,
    ) -> Result<(), String> {
        let j_sender = match sender {
            SimpleCommandSender::Console => jvm
                .invoke_static(
                    "org.bukkit.Bukkit",
                    "getConsoleSender",
                    InvocationArg::empty(),
                )
                .map_err(|e| e.to_string())?,

            SimpleCommandSender::Player(uuid_str) => {
                // Get the server and cast it to your implementation
                let server = jvm
                    .invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())
                    .map_err(|e| e.to_string())?;
                let patch_server = jvm
                    .cast(&server, "org.patchbukkit.PatchBukkitServer")
                    .map_err(|e| e.to_string())?;

                // Create Java UUID object
                let j_uuid = jvm
                    .invoke_static(
                        "java.util.UUID",
                        "fromString",
                        &[InvocationArg::try_from(uuid_str).map_err(|e| e.to_string())?],
                    )
                    .map_err(|e| e.to_string())?;

                // Call your getPlayer(UUID) method on the server
                jvm.invoke(&patch_server, "getPlayer", &[InvocationArg::from(j_uuid)])
                    .map_err(|e| e.to_string())?
            }
        };

        let server_instance = jvm
            .invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())
            .unwrap();

        let command_map = jvm
            .invoke(&server_instance, "getCommandMap", InvocationArg::empty())
            .unwrap();

        let dispatch_result = jvm
            .invoke(
                &command_map,
                "dispatch",
                &[
                    InvocationArg::from(j_sender),
                    InvocationArg::try_from(cmd_name).unwrap(),
                ],
            )
            .unwrap();

        let handled: bool = jvm.to_rust(dispatch_result).unwrap();

        if !handled {
            //log::warn!("Command was not handled by any Java plugin: {}", cmd_name);
        }

        Ok(())
    }

    pub async fn instantiate_all_plugins(
        &mut self,
        jvm: &Jvm,
        server: &Arc<Context>,
        command_tx: mpsc::Sender<JvmCommand>,
    ) -> Result<()> {
        let server_instance =
            jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;

        for (_plugin_name, plugin) in &mut self.plugins {
            let plugin_instance = jvm.invoke_static(
                "org.patchbukkit.loader.PatchBukkitPluginLoader",
                "createPlugin",
                &[
                    InvocationArg::try_from(&plugin.path.to_string_lossy().to_string())?,
                    InvocationArg::try_from(&plugin.main_class)?,
                ],
            )?;

            plugin.instance = Some(plugin_instance);
            let instance_ref = plugin.instance.as_ref().unwrap();

            let command_map =
                jvm.invoke(&server_instance, "getCommandMap", InvocationArg::empty())?;

            for (cmd_name, cmd_data) in &plugin.commands {
                let cloned_plugin_instance = jvm.clone_instance(instance_ref)?;
                let j_plugin_arg = InvocationArg::from(cloned_plugin_instance);

                let j_plugin_cmd = Arc::new(Mutex::new(jvm.invoke_static(
                    "org.patchbukkit.command.CommandFactory",
                    "create",
                    &[InvocationArg::try_from(cmd_name)?, j_plugin_arg],
                )?));
                log::info!("Registering Bukkit command: {}", cmd_name);
                {
                    let cmd_lock = j_plugin_cmd.lock().unwrap();
                    let j_plugin_cmd_owned = jvm.clone_instance(&*cmd_lock)?;
                    jvm.invoke(
                        &command_map,
                        "register",
                        &[
                            InvocationArg::try_from(cmd_name)?,
                            InvocationArg::try_from(&plugin.name)?,
                            InvocationArg::from(j_plugin_cmd_owned),
                        ],
                    )?;
                }
                let j_sender = jvm
                    .invoke_static(
                        "org.bukkit.Bukkit",
                        "getConsoleSender",
                        InvocationArg::empty(),
                    )
                    .map_err(|e| e.to_string())
                    .unwrap();

                // Make the tab working, at least the first thingy
                let dispatch_result = jvm
                    .invoke(
                        &command_map,
                        "tabComplete",
                        &[
                            InvocationArg::from(j_sender),
                            InvocationArg::try_from(format!("{} ", cmd_name)).unwrap(),
                        ],
                    )
                    .unwrap();

                let tab_list: Vec<String> = jvm.to_rust(dispatch_result).unwrap();

                let mut node =
                    CommandTree::new([cmd_name], cmd_data.description.clone().unwrap_or_default())
                        .execute(JavaCommandExecutor {
                            cmd_name: cmd_name.clone(),
                            command_tx: command_tx.clone(),
                            command: j_plugin_cmd.clone(),
                        });
                for tab in tab_list {
                    node = node.then(literal(tab).execute(JavaCommandExecutor {
                        cmd_name: cmd_name.clone(),
                        command_tx: command_tx.clone(),
                        command: j_plugin_cmd.clone(),
                    }));
                }
                // TODO
                // let permission = if let Some(perm) = cmd_data.permission.clone() {
                //     perm
                // } else {
                //     format!("patchbukkit:{}", cmd_name) // TODO
                // };
                let permission = format!("patchbukkit:{}", cmd_name);

                server
                    .register_permission(Permission::new(
                        &permission,
                        &permission,
                        PermissionDefault::Allow,
                    ))
                    .await
                    .unwrap();
                server.register_command(node, permission).await
            }

            plugin.state = PluginState::Loaded;
            log::info!("Loaded and registered commands for: {}", plugin.name);
        }
        Ok(())
    }

    pub fn unload_all_plugins(&mut self) -> Result<()> {
        self.plugins.clear();
        Ok(())
    }
}
