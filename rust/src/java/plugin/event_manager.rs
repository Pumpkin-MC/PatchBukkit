use std::sync::Arc;

use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};
use prost::Message;
use pumpkin::{entity::player::Player, server::Server};

use crate::{
    events::handler::JvmEventPayload,
    proto::patchbukkit::events::{FireEventResponse, event::Data},
};

pub struct EventManager {}

impl Default for EventManager {
    fn default() -> Self {
        Self::new()
    }
}

impl EventManager {
    #[must_use]
    pub const fn new() -> Self {
        Self {}
    }

    pub fn fire_event(
        &self,
        jvm: &Jvm,
        payload: JvmEventPayload,
        plugin_name: String,
    ) -> Result<FireEventResponse> {
        let server = jvm.invoke_static("org.bukkit.Bukkit", "getServer", InvocationArg::empty())?;
        let patch_server = jvm.cast(&server, "org.patchbukkit.PatchBukkitServer")?;
        let event_manager = jvm.invoke(&patch_server, "getEventManager", InvocationArg::empty())?;

        if let Some(ref event) = payload.event.data
            && matches!(event, Data::PlayerJoin(_) | Data::PlayerLogin(_))
            && let Some(ref player) = payload.context.player
        {
            Self::register_player(jvm, &patch_server, player, &payload.context.server)?;
        }

        let bytes = payload.event.encode_to_vec();
        let j_bytes = jvm.create_java_array(
            "byte",
            &bytes
                .iter()
                .map(|&b| {
                    InvocationArg::try_from(b as i8)
                        .unwrap()
                        .into_primitive()
                        .unwrap()
                })
                .collect::<Vec<_>>(),
        )?;

        let j_event = jvm.invoke_static(
            "org.patchbukkit.events.PatchBukkitEventFactory",
            "createEventFromBytes",
            &[InvocationArg::from(j_bytes)],
        )?;

        let is_null: bool = jvm.to_rust(jvm.invoke_static(
            "java.util.Objects",
            "isNull",
            &[InvocationArg::from(jvm.clone_instance(&j_event)?)],
        )?)?;

        if is_null {
            return Err(anyhow::anyhow!(
                "Failed to create event - factory returned null"
            ));
        }

        jvm.invoke(
            &event_manager,
            "fireEvent",
            &[
                InvocationArg::from(jvm.clone_instance(&j_event)?),
                InvocationArg::try_from(plugin_name)?,
            ],
        )?;

        let response_bytes: Vec<i8> = jvm.to_rust(jvm.invoke_static(
            "org.patchbukkit.events.PatchBukkitEventFactory",
            "toFireEventResponse",
            &[InvocationArg::from(j_event)],
        )?)?;

        let response_bytes: Vec<u8> = response_bytes.iter().map(|&b| b as u8).collect();
        let response = FireEventResponse::decode(response_bytes.as_slice())?;

        Ok(response)
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
            .map_err(|e| format!("Failed to create Java UUID: {e}"))
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
        }

        jvm.invoke(
            patch_server,
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
