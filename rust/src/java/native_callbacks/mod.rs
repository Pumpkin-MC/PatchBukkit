use std::sync::{Arc, OnceLock};

use anyhow::Result;
use j4rs::{InvocationArg, Jvm};
use pumpkin::plugin::Context;

pub mod abilities;
pub mod events;
pub mod location;
pub mod memory;
pub mod message;
pub mod registry;
pub mod utils;
pub mod world;

static CALLBACK_CONTEXT: OnceLock<CallbackContext> = OnceLock::new();

struct CallbackContext {
    pub plugin_context: Arc<Context>,
    pub runtime: tokio::runtime::Handle,
}

pub fn init_callback_context(
    plugin_context: Arc<Context>,
    runtime: tokio::runtime::Handle,
) -> Result<()> {
    let context = CallbackContext {
        plugin_context,
        runtime,
    };

    CALLBACK_CONTEXT
        .set(context)
        .map_err(|_| anyhow::anyhow!("Failed to set callback context"))?;
    Ok(())
}

pub fn initialize_callbacks(jvm: &Jvm) -> Result<()> {
    let send_message_addr = message::rust_send_message as *const () as i64;
    let register_event_addr = events::rust_register_event as *const () as i64;
    let get_abilities_addr = abilities::rust_get_abilities as *const () as i64;
    let set_abilities_addr = abilities::rust_set_abilities as *const () as i64;
    let get_location_addr = location::rust_get_location as *const () as i64;
    let free_string_addr = memory::rust_free_string as *const () as i64;
    let get_world_addr = world::rust_get_world as *const () as i64;
    let rust_get_registry_data_addr = registry::rust_get_registry_data as *const () as i64;

    jvm.invoke_static(
        "org.patchbukkit.bridge.NativePatchBukkit",
        "initCallbacks",
        &[
            InvocationArg::try_from(send_message_addr)?.into_primitive()?,
            InvocationArg::try_from(register_event_addr)?.into_primitive()?,
            InvocationArg::try_from(get_abilities_addr)?.into_primitive()?,
            InvocationArg::try_from(set_abilities_addr)?.into_primitive()?,
            InvocationArg::try_from(get_location_addr)?.into_primitive()?,
            InvocationArg::try_from(free_string_addr)?.into_primitive()?,
            InvocationArg::try_from(get_world_addr)?.into_primitive()?,
            InvocationArg::try_from(rust_get_registry_data_addr)?.into_primitive()?,
        ],
    )?;

    Ok(())
}
