use std::sync::{Arc, OnceLock};

use anyhow::Result;
use j4rs::{InvocationArg, Jvm};
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::{java::jvm::commands::JvmCommand, proto::initialize_ffi_callbacks};

mod abilities;
pub use abilities::*;

pub mod events;
pub use events::*;

pub mod location;
pub use location::*;

pub mod memory;
pub use memory::*;

pub mod message;
pub use message::*;

pub mod registry;
pub use registry::*;

pub mod sound;
pub use sound::*;

pub mod world;
pub use world::*;

pub mod utils;

static CALLBACK_CONTEXT: OnceLock<CallbackContext> = OnceLock::new();

struct CallbackContext {
    pub plugin_context: Arc<Context>,
    pub runtime: tokio::runtime::Handle,
    pub command_tx: mpsc::Sender<JvmCommand>,
}

pub fn init_callback_context(
    plugin_context: Arc<Context>,
    runtime: tokio::runtime::Handle,
    command_tx: mpsc::Sender<JvmCommand>,
) -> Result<()> {
    let context = CallbackContext {
        plugin_context,
        runtime,
        command_tx,
    };

    CALLBACK_CONTEXT
        .set(context)
        .map_err(|_| anyhow::anyhow!("Failed to set callback context"))?;
    Ok(())
}

pub fn encode_with_length(data: Vec<u8>) -> *mut u8 {
    let len = data.len() as u32;
    let mut result = Vec::with_capacity(4 + data.len());
    result.extend_from_slice(&len.to_le_bytes());
    result.extend(data);

    let ptr = result.as_mut_ptr();
    std::mem::forget(result);
    ptr
}

pub extern "C" fn rust_free_bytes(ptr: *mut u8, len: u32) {
    if !ptr.is_null() {
        unsafe {
            let total_len = 4 + len as usize;
            drop(Vec::from_raw_parts(ptr, total_len, total_len));
        }
    }
}

pub fn initialize_callbacks(jvm: &Jvm) -> Result<()> {
    let get_location_addr = location::rust_get_location as *const () as i64;
    let free_string_addr = memory::rust_free_string as *const () as i64;
    let get_world_addr = world::rust_get_world as *const () as i64;
    let rust_get_registry_data_addr = registry::rust_get_registry_data as *const () as i64;
    let rust_player_entity_play_sound_addr =
        sound::rust_player_entity_play_sound as *const () as i64;
    let rust_player_play_sound_addr = sound::rust_player_play_sound as *const () as i64;

    jvm.invoke_static(
        "org.patchbukkit.bridge.NativePatchBukkit",
        "initCallbacks",
        &[
            InvocationArg::try_from(get_location_addr)?.into_primitive()?,
            InvocationArg::try_from(free_string_addr)?.into_primitive()?,
            InvocationArg::try_from(get_world_addr)?.into_primitive()?,
            InvocationArg::try_from(rust_get_registry_data_addr)?.into_primitive()?,
            InvocationArg::try_from(rust_player_entity_play_sound_addr)?.into_primitive()?,
            InvocationArg::try_from(rust_player_play_sound_addr)?.into_primitive()?,
        ],
    )?;

    initialize_ffi_callbacks(jvm)?;

    Ok(())
}
