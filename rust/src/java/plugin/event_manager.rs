use std::sync::Arc;

use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};
use pumpkin::{entity::player::Player, server::Server};

use crate::events::handler::PatchBukkitEvent;

pub struct EventManager {}

impl EventManager {
    pub fn new() -> Self {
        Self {}
    }

    pub fn fire_event(
        &self,
        jvm: &Jvm,
        event: PatchBukkitEvent,
        plugin_name: String,
    ) -> Result<bool> {
        let server = jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;
        let patch_server = jvm.cast(&server, "org.patchbukkit.PatchBukkitServer")?;
        let event_manager = jvm.invoke(&patch_server, "getEventManager", InvocationArg::empty())?;

        let j_event = match event {
            PatchBukkitEvent::PlayerJoinEvent {
                server,
                player,
                join_message,
            } => {
                EventManager::register_player(jvm, &patch_server, &player, &server)?;
                jvm.invoke_static(
                    "org.patchbukkit.events.PatchBukkitEventFactory",
                    "createPlayerJoinEvent",
                    &[
                        InvocationArg::try_from(player.gameprofile.id.to_string())?,
                        InvocationArg::try_from(join_message)?,
                    ],
                )?
            }
        };

        let j_event_for_fire = jvm.clone_instance(&j_event)?;
        jvm.invoke(
            &event_manager,
            "fireEvent",
            &[
                InvocationArg::from(j_event_for_fire),
                InvocationArg::try_from(plugin_name)?,
            ],
        )?;

        let is_cancellable: bool = jvm.to_rust(jvm.invoke_static(
            "org.patchbukkit.events.PatchBukkitEventFactory",
            "isCancellable",
            &[InvocationArg::from(jvm.clone_instance(&j_event)?)],
        )?)?;

        let cancelled = match is_cancellable {
            true => match jvm.invoke(
                &jvm.clone_instance(&j_event)?,
                "isCancelled",
                InvocationArg::empty(),
            ) {
                Ok(instance) => jvm.to_rust::<bool>(instance).unwrap_or(false),
                Err(_) => false,
            },
            false => false,
        };

        Ok(cancelled)
    }

    pub fn register_player(
        jvm: &Jvm,
        patch_server: &Instance,
        player: &Arc<Player>,
        server: &Arc<Server>,
    ) -> Result<()> {
        let j_uuid = jvm
            .invoke_static(
                "java.util.UUID",
                "fromString",
                &[InvocationArg::try_from(player.gameprofile.id.to_string())?],
            )
            .map_err(|e| format!("Failed to create Java UUID: {}", e))
            .unwrap();

        let j_player = jvm.create_instance(
            "org.patchbukkit.entity.PatchBukkitPlayer",
            &[
                InvocationArg::from(j_uuid),
                InvocationArg::try_from(player.gameprofile.name.clone())?,
            ],
        )?;

        let player_permission_level = player.permission_lvl.load();
        if player_permission_level >= server.basic_config.op_permission_level {
            jvm.invoke(
                &j_player,
                "setOp",
                &[InvocationArg::try_from(true)
                    .unwrap()
                    .into_primitive()
                    .unwrap()],
            )?;
        };

        jvm.invoke(
            &patch_server,
            "registerPlayer",
            &[InvocationArg::from(j_player)],
        )?;

        Ok(())
    }

    pub fn call_event(&self, jvm: &Jvm, event: Instance) -> Result<()> {
        let server = jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;
        let plugin_manager = jvm.invoke(&server, "getPluginManager", InvocationArg::empty())?;
        jvm.invoke(
            &plugin_manager,
            "callEvent",
            &[InvocationArg::try_from(event)?],
        )?;
        Ok(())
    }
}
