use std::{
    collections::{BinaryHeap, HashMap, HashSet},
    path::{Path, PathBuf},
    sync::Arc,
};

use anyhow::Result;
use j4rs::{Instance, InvocationArg, Jvm};
use pumpkin::plugin::Context;
use tokio::sync::mpsc;

use crate::{
    config::{
        paper::PaperPluginYml,
        spigot::{Command, SpigotPluginYml},
    },
    java::{jvm::commands::JvmCommand, plugin::command_manager::CommandManager},
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

    // Dependency metadata (normalized to lowercase)
    pub provides: Vec<String>,
    pub depends: Vec<String>,
    pub soft_depends: Vec<String>,
    pub load_before: Vec<String>,
    pub load_after: Vec<String>,
    pub classpath_deps: Vec<String>,

    // Library coordinates (original casing preserved)
    pub libraries: Vec<String>,
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
        let key = normalize_name(&plugin.name);
        if self.plugins.contains_key(&key) {
            log::warn!(
                "Duplicate plugin name detected ({}). Keeping the first instance.",
                plugin.name
            );
            return;
        }
        self.plugins.insert(key, plugin);
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

        let (spigot_depends, spigot_soft_depends, spigot_load_before, spigot_load_after, spigot_provides, spigot_libraries, spigot_skip_libraries) = match &parsed_spigot_plugin {
            Some(config) => (
                normalize_names(config.depend.clone()),
                normalize_names(config.softdepend.clone()),
                normalize_names(config.loadbefore.clone()),
                Vec::new(),
                normalize_names(config.provides.clone()),
                config.libraries.clone().unwrap_or_default(),
                config.paper_skip_libraries.unwrap_or(false),
            ),
            None => (Vec::new(), Vec::new(), Vec::new(), Vec::new(), Vec::new(), Vec::new(), false),
        };

        let mut paper_depends = Vec::new();
        let mut paper_soft_depends = Vec::new();
        let mut paper_load_before = Vec::new();
        let mut paper_load_after = Vec::new();
        let mut paper_classpath = Vec::new();

        if let Some(ref deps) = parsed_paper_plugin.dependencies {
            for (name, dep) in deps
                .get_bootstrap_deps()
                .into_iter()
                .chain(deps.get_server_deps())
            {
                let normalized = normalize_name(name);
                if dep.required {
                    paper_depends.push(normalized.clone());
                } else {
                    paper_soft_depends.push(normalized.clone());
                }

                match dep.load {
                    crate::config::paper::LoadOrder::Before => {
                        paper_load_before.push(normalized.clone());
                    }
                    crate::config::paper::LoadOrder::After => {
                        paper_load_after.push(normalized.clone());
                    }
                    crate::config::paper::LoadOrder::Omit => {}
                }

                if dep.join_classpath {
                    paper_classpath.push(normalized);
                }
            }
        }

        let mut provides = normalize_names(parsed_paper_plugin.provides.clone());
        provides.extend(spigot_provides);

        let mut depends = paper_depends;
        depends.extend(spigot_depends);

        let mut soft_depends = paper_soft_depends;
        soft_depends.extend(spigot_soft_depends);

        let mut load_before = paper_load_before;
        load_before.extend(spigot_load_before);

        let mut load_after = paper_load_after;
        load_after.extend(spigot_load_after);

        let classpath_deps = paper_classpath;

        let commands = match &parsed_spigot_plugin {
            Some(config) => config.commands.clone().unwrap_or_default(),
            None => HashMap::new(),
        };

        let libraries = if spigot_skip_libraries {
            Vec::new()
        } else {
            spigot_libraries
        };

        let name = parsed_paper_plugin.name.clone();
        let version = parsed_paper_plugin.version.clone();
        let main_class = parsed_paper_plugin.main.clone();
        let paper_config = parsed_paper_plugin;
        let spigot_config = parsed_spigot_plugin;

        let plugin = Plugin {
            name,
            version,
            main_class,
            plugin_type: PluginType::Paper(PaperPluginData {
                paper_config,
                spigot_config,
            }),
            state: PluginState::Registered,
            data_folder: jar_path.as_ref().parent().unwrap().join("data"),
            path: jar_path.as_ref().to_path_buf(),
            instance: None,
            commands,
            listeners: HashMap::new(),

            provides,
            depends: dedupe_names(depends),
            soft_depends: dedupe_names(soft_depends),
            load_before: dedupe_names(load_before),
            load_after: dedupe_names(load_after),
            classpath_deps: dedupe_names(classpath_deps),
            libraries: dedupe_strings(libraries),
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

        let depends = normalize_names(parsed_spigot_plugin.depend.clone());
        let soft_depends = normalize_names(parsed_spigot_plugin.softdepend.clone());
        let load_before = normalize_names(parsed_spigot_plugin.loadbefore.clone());
        let provides = normalize_names(parsed_spigot_plugin.provides.clone());
        let libraries = if parsed_spigot_plugin.paper_skip_libraries.unwrap_or(false) {
            Vec::new()
        } else {
            parsed_spigot_plugin.libraries.clone().unwrap_or_default()
        };

        let name = parsed_spigot_plugin.name.clone();
        let version = parsed_spigot_plugin.version.clone();
        let main_class = parsed_spigot_plugin.main.clone();
        let spigot_config = parsed_spigot_plugin.clone();
        let commands = parsed_spigot_plugin.commands.clone().unwrap_or_default();

        let plugin = Plugin {
            name,
            version,
            main_class,
            plugin_type: PluginType::Spigot(SpigotPluginData { spigot_config }),
            state: PluginState::Registered,
            data_folder: jar_path.as_ref().parent().unwrap().join("data"),
            path: jar_path.as_ref().to_path_buf(),
            instance: None,
            commands,
            listeners: HashMap::new(),

            provides,
            depends: dedupe_names(depends),
            soft_depends: dedupe_names(soft_depends),
            load_before: dedupe_names(load_before),
            load_after: Vec::new(),
            classpath_deps: Vec::new(),
            libraries: dedupe_strings(libraries),
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
            if plugin.instance.is_none() {
                continue;
            }
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
            if plugin.instance.is_none() {
                continue;
            }
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

    pub async fn instantiate_all_plugins(
        &mut self,
        jvm: &Jvm,
        server: &Arc<Context>,
        command_tx: mpsc::Sender<JvmCommand>,
        command_manager: &mut CommandManager,
    ) -> Result<()> {
        let load_order = self.compute_load_order();

        for plugin_key in load_order {
            let (classpath, libraries) = match self.plugins.get(&plugin_key) {
                Some(plugin) => (
                    self.classpath_string_for(plugin),
                    self.library_string_for(plugin),
                ),
                None => continue,
            };

            let plugin = match self.plugins.get_mut(&plugin_key) {
                Some(plugin) => plugin,
                None => continue,
            };
            let plugin_instance = jvm.invoke_static(
                "org.patchbukkit.loader.PatchBukkitPluginLoader",
                "createPlugin",
                &[
                    InvocationArg::try_from(&plugin.path.to_string_lossy().to_string())?,
                    InvocationArg::try_from(&plugin.main_class)?,
                    InvocationArg::try_from(&classpath)?,
                    InvocationArg::try_from(&libraries)?,
                ],
            )?;

            plugin.instance = Some(plugin_instance);
            for (cmd_name, cmd_data) in &plugin.commands {
                match command_manager
                    .register_command(
                        &jvm,
                        server,
                        &plugin,
                        cmd_name.clone(),
                        cmd_data,
                        command_tx.clone(),
                    )
                    .await
                {
                    Ok(_) => (),
                    Err(e) => {
                        log::error!(
                            "Failed to register command {} for plugin {}: {:?}",
                            cmd_name,
                            plugin.name,
                            e
                        );
                    }
                }
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

    fn resolve_dependency_name(
        &self,
        name: &str,
        provides_map: &HashMap<String, String>,
    ) -> Option<String> {
        let key = normalize_name(name);
        if self.plugins.contains_key(&key) {
            Some(key)
        } else {
            provides_map.get(&key).cloned()
        }
    }

    fn compute_load_order(&mut self) -> Vec<String> {
        let mut provides_map: HashMap<String, String> = HashMap::new();
        for (key, plugin) in &self.plugins {
            for provide in &plugin.provides {
                if provide.is_empty() {
                    continue;
                }
                provides_map.entry(provide.clone()).or_insert_with(|| key.clone());
            }
        }

        let mut active: HashSet<String> = self.plugins.keys().cloned().collect();

        let mut missing_required_by_plugin: HashMap<String, Vec<String>> = HashMap::new();
        for (key, plugin) in &self.plugins {
            let mut missing_required = Vec::new();
            for dep in &plugin.depends {
                if self.resolve_dependency_name(dep, &provides_map).is_none() {
                    missing_required.push(dep.clone());
                }
            }
            if !missing_required.is_empty() {
                missing_required_by_plugin.insert(key.clone(), missing_required);
            }
        }

        for (key, missing_required) in missing_required_by_plugin {
            if let Some(plugin) = self.plugins.get_mut(&key) {
                plugin.state = PluginState::Errored;
                active.remove(&key);
                log::warn!(
                    "Skipping plugin {} due to missing required dependencies: {}",
                    plugin.name,
                    missing_required.join(", ")
                );
            }
        }

        let mut edges: HashMap<String, HashSet<String>> = HashMap::new();
        let mut indegree: HashMap<String, usize> =
            active.iter().map(|k| (k.clone(), 0)).collect();

        for (key, plugin) in &self.plugins {
            if !active.contains(key) {
                continue;
            }

            let mut before = Vec::new();
            for dep in plugin
                .depends
                .iter()
                .chain(plugin.soft_depends.iter())
            {
                if let Some(resolved) = self.resolve_dependency_name(dep, &provides_map) {
                    if resolved != *key && active.contains(&resolved) {
                        before.push(resolved);
                    }
                }
            }

            for dep in before {
                add_edge(&mut edges, &mut indegree, &dep, key);
            }

            for target in &plugin.load_before {
                if let Some(resolved) = self.resolve_dependency_name(target, &provides_map) {
                    if resolved != *key && active.contains(&resolved) {
                        add_edge(&mut edges, &mut indegree, key, &resolved);
                    }
                }
            }

            for target in &plugin.load_after {
                if let Some(resolved) = self.resolve_dependency_name(target, &provides_map) {
                    if resolved != *key && active.contains(&resolved) {
                        add_edge(&mut edges, &mut indegree, &resolved, key);
                    }
                }
            }
        }

        let mut ready: BinaryHeap<std::cmp::Reverse<String>> = indegree
            .iter()
            .filter_map(|(key, &deg)| {
                if deg == 0 {
                    Some(std::cmp::Reverse(key.clone()))
                } else {
                    None
                }
            })
            .collect();
        let mut order = Vec::with_capacity(active.len());

        while let Some(std::cmp::Reverse(node)) = ready.pop() {
            order.push(node.clone());
            if let Some(children) = edges.get(&node) {
                for child in children {
                    if let Some(deg) = indegree.get_mut(child) {
                        if *deg > 0 {
                            *deg -= 1;
                        }
                        if *deg == 0 {
                            ready.push(std::cmp::Reverse(child.clone()));
                        }
                    }
                }
            }
        }

        if order.len() != active.len() {
            let mut remaining: Vec<String> = active
                .into_iter()
                .filter(|k| !order.contains(k))
                .collect();
            remaining.sort();
            log::warn!(
                "Detected plugin dependency cycle(s). Loading remaining plugins in name order: {}",
                remaining.join(", ")
            );
            order.extend(remaining);
        }

        order
    }

    fn classpath_string_for(&self, plugin: &Plugin) -> String {
        if plugin.classpath_deps.is_empty() {
            return String::new();
        }

        let mut provides_map: HashMap<String, String> = HashMap::new();
        for (key, plugin) in &self.plugins {
            for provide in &plugin.provides {
                if provide.is_empty() {
                    continue;
                }
                provides_map.entry(provide.clone()).or_insert_with(|| key.clone());
            }
        }

        let mut paths = Vec::new();
        for dep in &plugin.classpath_deps {
            if let Some(resolved) = self.resolve_dependency_name(dep, &provides_map) {
                if let Some(dep_plugin) = self.plugins.get(&resolved) {
                    paths.push(dep_plugin.path.to_string_lossy().to_string());
                }
            }
        }

        paths.join(";")
    }

    fn library_string_for(&self, plugin: &Plugin) -> String {
        if plugin.libraries.is_empty() {
            return String::new();
        }
        plugin.libraries.join("\n")
    }
}

fn normalize_name(name: &str) -> String {
    name.trim().to_lowercase()
}

fn normalize_names(names: Option<Vec<String>>) -> Vec<String> {
    names
        .unwrap_or_default()
        .into_iter()
        .map(|name| normalize_name(&name))
        .filter(|name| !name.is_empty())
        .collect()
}

fn dedupe_names(names: Vec<String>) -> Vec<String> {
    let mut seen = HashSet::new();
    let mut output = Vec::new();
    for name in names {
        if seen.insert(name.clone()) {
            output.push(name);
        }
    }
    output
}

fn dedupe_strings(values: Vec<String>) -> Vec<String> {
    let mut seen = HashSet::new();
    let mut output = Vec::new();
    for value in values {
        let trimmed = value.trim().to_string();
        if trimmed.is_empty() {
            continue;
        }
        if seen.insert(trimmed.clone()) {
            output.push(trimmed);
        }
    }
    output
}

fn add_edge(
    edges: &mut HashMap<String, HashSet<String>>,
    indegree: &mut HashMap<String, usize>,
    from: &str,
    to: &str,
) {
    if from == to {
        return;
    }
    let entry = edges.entry(from.to_string()).or_default();
    if entry.insert(to.to_string()) {
        if let Some(count) = indegree.get_mut(to) {
            *count += 1;
        }
    }
}
