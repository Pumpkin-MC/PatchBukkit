use std::{path::Path, sync::Arc};

use anyhow::Result;
use jni::Env;
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::java::{jvm::commands::JvmCommand, plugin::command_manager::CommandManager};

pub struct PluginManager;

impl Default for PluginManager {
    fn default() -> Self {
        Self::new()
    }
}

impl PluginManager {
    #[must_use]
    pub const fn new() -> Self {
        Self
    }

    pub fn enable_all_plugins(&mut self, _env: &mut Env) -> Result<()> {
        Ok(())
    }

    pub fn disable_all_plugins(&mut self, env: &mut Env) -> Result<()> {
        tracing::info!("Disabling all Java plugins...");
        let result = env.call_static_method(
            jni::jni_str!("org/patchbukkit/loader/PatchBukkitBootstrap"),
            jni::jni_str!("disablePlugins"),
            jni::jni_sig!("()Z"),
            &[],
        );

        match result {
            Ok(val) => {
                if !val.z()? {
                    tracing::warn!("Disabling Java plugins reported warnings/errors");
                }
            }
            Err(e) => {
                env.exception_clear();
                tracing::warn!("Failed to call PatchBukkitBootstrap.disablePlugins: {e:?}");
            }
        }
        Ok(())
    }

    pub fn instantiate_all_plugins(
        &mut self,
        env: &mut Env,
        plugin_folder: &Path,
        _server: &Arc<Context>,
        _command_tx: mpsc::Sender<JvmCommand>,
        _command_manager: &mut CommandManager,
    ) -> Result<()> {
        tracing::info!("Bootstrapping Java plugins from {:?}", plugin_folder);
        let path_str = plugin_folder.to_string_lossy();
        let j_str = env.new_string(&path_str)?;
        let result = env.call_static_method(
            jni::jni_str!("org/patchbukkit/loader/PatchBukkitBootstrap"),
            jni::jni_str!("bootstrapPlugins"),
            jni::jni_sig!("(Ljava/lang/String;)Z"),
            &[(&j_str).into()],
        );

        match result {
            Ok(val) => {
                if !val.z()? {
                    tracing::error!("Bootstrap of Java plugins reported errors");
                }
            }
            Err(e) => {
                tracing::error!("Failed to bootstrap Java plugins: {:?}", e);
                return Err(e.into());
            }
        }
        Ok(())
    }

    pub fn unload_all_plugins(&mut self) -> Result<()> {
        Ok(())
    }
}
