use std::{
    path::{Path, PathBuf},
    sync::{Arc, Mutex},
};

use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};

use crate::config::{paper::PaperPluginYml, spigot::SpigotPluginYml};

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
}

pub struct PluginManager {
    plugins: Arc<Mutex<Vec<Plugin>>>,
}

impl PluginManager {
    pub fn new() -> Self {
        Self {
            plugins: Arc::new(Mutex::new(Vec::new())),
        }
    }

    pub fn add_plugin(&self, plugin: Plugin) {
        self.plugins.lock().unwrap().push(plugin);
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
                spigot_config: parsed_spigot_plugin,
            }),
            state: PluginState::Registered,
            data_folder: jar_path.as_ref().parent().unwrap().join("data"),
            path: jar_path.as_ref().to_path_buf(),
            instance: None,
        };

        self.add_plugin(plugin);
        Ok(())
    }

    pub fn enable_all_plugins(&mut self, jvm: &Jvm) -> Result<()> {
        for plugin in &mut *self.plugins.lock().unwrap() {
            let result = jvm.invoke(
                plugin.instance.as_ref().unwrap(),
                "onEnable",
                InvocationArg::empty(),
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
        for plugin in &mut *self.plugins.lock().unwrap() {
            let result = jvm.invoke(
                plugin.instance.as_ref().unwrap(),
                "onDisable",
                InvocationArg::empty(),
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

    pub fn load_all_plugins(&mut self, jvm: &Jvm) -> Result<()> {
        for plugin in &mut *self.plugins.lock().unwrap() {
            let result = jvm.invoke_static(
                "org.patchbukkit.loader.PatchBukkitPluginLoader",
                "createPlugin",
                &[
                    InvocationArg::try_from(&plugin.path.to_string_lossy().to_string())?,
                    InvocationArg::try_from(&plugin.main_class)?,
                ],
            );

            match result {
                Ok(instance) => {
                    plugin.instance = Some(instance);
                    plugin.state = PluginState::Loaded;
                    log::info!("Loaded PatchBukkit plugin: {}", plugin.name);
                }
                Err(e) => {
                    plugin.state = PluginState::Errored;
                    log::error!("Failed to load PatchBukkit plugin {}: {:?}", plugin.name, e);
                }
            }
        }
        Ok(())
    }

    pub fn unload_all_plugins(&mut self) -> Result<()> {
        self.plugins.lock().unwrap().clear();
        Ok(())
    }
}
