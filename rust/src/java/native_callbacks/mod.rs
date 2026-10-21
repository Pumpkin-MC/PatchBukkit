use std::sync::{Arc, OnceLock};

use anyhow::Result;
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::{
    config::patchbukkit::PatchBukkitConfig, java::jvm::commands::JvmCommand,
    proto::initialize_ffi_callbacks,
};

mod abilities;
pub use abilities::*;

pub mod events;
pub use events::*;

pub mod location;
pub use location::*;

pub mod message;
pub use message::*;

pub mod registry;
pub use registry::*;

pub mod sound;
pub use sound::*;

pub mod utils;

pub mod log;
pub use log::*;

pub mod config;
pub use config::*;

pub mod entity;
pub use entity::*;

pub mod world;
pub use world::*;

pub mod itemstack;
pub use itemstack::*;

pub mod command;
pub use command::*;

pub mod server;
pub use server::*;

pub mod fast_path;
pub use fast_path::*;

pub(crate) static CALLBACK_CONTEXT: OnceLock<CallbackContext> = OnceLock::new();

pub(crate) struct CallbackContext {
    pub plugin_context: Arc<Context>,
    pub runtime: tokio::runtime::Handle,
    pub command_tx: mpsc::Sender<JvmCommand>,
    pub config: PatchBukkitConfig,
}

pub fn init_callback_context(
    plugin_context: Arc<Context>,
    runtime: tokio::runtime::Handle,
    command_tx: mpsc::Sender<JvmCommand>,
    config: PatchBukkitConfig,
) -> Result<()> {
    let context = CallbackContext {
        plugin_context,
        runtime,
        command_tx,
        config,
    };

    CALLBACK_CONTEXT
        .set(context)
        .map_err(|_| anyhow::anyhow!("Failed to set callback context"))?;
    Ok(())
}

pub fn initialize_callbacks(env: &mut jni::Env) -> Result<()> {
    initialize_ffi_callbacks(env)?;
    initialize_fast_path_callbacks(env)?;
    Ok(())
}
