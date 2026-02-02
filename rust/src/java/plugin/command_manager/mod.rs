use std::sync::{Arc, Mutex};

use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};
use pumpkin::{command::dispatcher::CommandError, plugin::Context};
use pumpkin_protocol::java::client::play::CommandSuggestion;
use pumpkin_util::permission::{Permission, PermissionDefault};
use tokio::sync::mpsc;

use crate::{
    commands::{SimpleCommandSender, init_java_command},
    config,
    java::{
        jvm::commands::{JvmCommand, Location},
        plugin::manager::Plugin,
    },
};

pub struct CommandManager {
    command_map: Option<Instance>,
}

impl CommandManager {
    pub fn new() -> Self {
        Self { command_map: None }
    }

    pub fn init(&mut self, jvm: &Jvm) -> Result<()> {
        let server_instance =
            jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;

        let command_map = jvm.invoke(&server_instance, "getCommandMap", InvocationArg::empty())?;
        let command_map = jvm.cast(
            &command_map,
            "org.patchbukkit.command.PatchBukkitCommandMap",
        )?;

        self.command_map = Some(command_map);

        Ok(())
    }

    pub fn get_tab_complete(
        &mut self,
        jvm: &Jvm,
        sender: SimpleCommandSender,
        full_command: String,
        location: Option<Location>,
    ) -> Result<Option<Vec<CommandSuggestion>>, CommandError> {
        match self.try_tab_complete(jvm, sender, full_command, location) {
            Ok(suggestions) => Ok(suggestions),
            Err(e) => {
                log::warn!("Tab completion failed: {e}");
                Ok(None)
            }
        }
    }

    fn try_tab_complete(
        &mut self,
        jvm: &Jvm,
        sender: SimpleCommandSender,
        full_command: String,
        location: Option<Location>,
    ) -> Result<Option<Vec<CommandSuggestion>>> {
        let command_map = match self.command_map {
            Some(ref command_map) => command_map,
            None => match self.init(jvm) {
                Ok(_) => self.command_map.as_ref().unwrap(),
                Err(_) => return Ok(None),
            },
        };

        let sender = Self::sender_to_jsender(jvm, sender)?;

        let completions = if let Some(location) = location {
            let world = jvm.invoke_static(
                "org.patchbukkit.world.PatchBukkitWorld",
                "getOrCreate",
                &[InvocationArg::try_from(location.world.to_string())?],
            )?;

            let location = match location.rotation {
                Some(rotation) => jvm.create_instance(
                    "org.bukkit.Location",
                    &[
                        InvocationArg::try_from(world)?,
                        InvocationArg::try_from(location.x)?.into_primitive()?,
                        InvocationArg::try_from(location.y)?.into_primitive()?,
                        InvocationArg::try_from(location.z)?.into_primitive()?,
                        InvocationArg::try_from(rotation.yaw)?.into_primitive()?,
                        InvocationArg::try_from(rotation.pitch)?.into_primitive()?,
                    ],
                )?,
                None => jvm.create_instance(
                    "org.bukkit.Location",
                    &[
                        InvocationArg::try_from(world)?,
                        InvocationArg::try_from(location.x)?.into_primitive()?,
                        InvocationArg::try_from(location.y)?.into_primitive()?,
                        InvocationArg::try_from(location.z)?.into_primitive()?,
                    ],
                )?,
            };

            jvm.invoke(
                command_map,
                "tabComplete",
                &[
                    InvocationArg::try_from(sender)?,
                    InvocationArg::try_from(full_command)?,
                    InvocationArg::try_from(location)?,
                ],
            )?
        } else {
            jvm.invoke(
                command_map,
                "tabComplete",
                &[
                    InvocationArg::try_from(sender)?,
                    InvocationArg::try_from(full_command)?,
                ],
            )?
        };

        let completions: Vec<String> = jvm.to_rust(completions)?;

        Ok(Some(
            completions
                .into_iter()
                .map(|completion| CommandSuggestion::new(completion, None))
                .collect(),
        ))
    }

    pub async fn register_command(
        &mut self,
        jvm: &Jvm,
        context: &Arc<Context>,
        plugin: &Plugin,
        cmd_name: String,
        cmd_data: &config::spigot::Command,
        command_tx: mpsc::Sender<JvmCommand>,
    ) -> Result<()> {
        let command_map = match self.command_map {
            Some(ref command_map) => command_map,
            None => match self.init(jvm) {
                Ok(_) => self.command_map.as_ref().unwrap(),
                Err(err) => return Err(err),
            },
        };

        let plugin_instance =
            jvm.clone_instance(plugin.instance.as_ref().expect(
                "This function should never be called with a plugin that has no instance",
            ))?;
        let j_plugin_arg = InvocationArg::from(plugin_instance);

        let j_plugin_cmd = Arc::new(Mutex::new(jvm.invoke_static(
            "org.patchbukkit.command.CommandFactory",
            "create",
            &[InvocationArg::try_from(&cmd_name)?, j_plugin_arg],
        )?));
        log::info!("Registering Bukkit command: {}", &cmd_name);
        {
            let cmd_lock = j_plugin_cmd.lock().unwrap();
            let j_plugin_cmd_owned = jvm.clone_instance(&*cmd_lock)?;
            jvm.invoke(
                command_map,
                "register",
                &[
                    InvocationArg::try_from(&cmd_name)?,
                    InvocationArg::try_from(&plugin.name)?,
                    InvocationArg::from(j_plugin_cmd_owned),
                ],
            )?;
        }

        let node = init_java_command(
            cmd_name.clone(),
            command_tx.clone(),
            [&cmd_name],
            cmd_data.description.clone().unwrap_or_default(),
        );

        // TODO
        // let permission = if let Some(perm) = cmd_data.permission.clone() {
        //     perm
        // } else {
        //     format!("patchbukkit:{}", cmd_name) // TODO
        // };
        let permission = format!("patchbukkit:{}", cmd_name);

        context
            .register_permission(Permission::new(
                &permission,
                &permission,
                PermissionDefault::Allow,
            ))
            .await
            .unwrap();

        context.register_command(node, permission).await;

        Ok(())
    }

    pub fn trigger_command(
        &mut self,
        jvm: &Jvm,
        full_command: String,
        sender: SimpleCommandSender,
    ) -> Result<()> {
        let command_map = match self.command_map {
            Some(ref command_map) => command_map,
            None => match self.init(jvm) {
                Ok(_) => self.command_map.as_ref().unwrap(),
                Err(err) => return Err(err),
            },
        };

        let j_sender = Self::sender_to_jsender(jvm, sender)?;

        let dispatch_result = jvm.invoke(
            command_map,
            "dispatch",
            &[
                InvocationArg::from(j_sender),
                InvocationArg::try_from(full_command)?,
            ],
        )?;

        let handled: bool = jvm.to_rust(dispatch_result)?;

        if !handled {
            //log::warn!("Command was not handled by any Java plugin: {}", cmd_name);
        }

        Ok(())
    }

    pub fn sender_to_jsender(jvm: &Jvm, sender: SimpleCommandSender) -> Result<Instance> {
        match sender {
            SimpleCommandSender::Console => Ok(jvm.invoke_static(
                "org.bukkit.Bukkit",
                "getConsoleSender",
                InvocationArg::empty(),
            )?),

            SimpleCommandSender::Player(uuid_str) => {
                let server =
                    jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;
                let patch_server = jvm.cast(&server, "org.patchbukkit.PatchBukkitServer")?;

                let j_uuid = jvm.invoke_static(
                    "java.util.UUID",
                    "fromString",
                    &[InvocationArg::try_from(uuid_str)?],
                )?;

                Ok(jvm.invoke(&patch_server, "getPlayer", &[InvocationArg::from(j_uuid)])?)
            }
        }
    }
}
