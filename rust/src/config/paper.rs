use serde::Deserialize;
use std::collections::HashMap;

pub const PAPER_PLUGIN_CONFIG: &str = "paper-plugin.yml";

/// Represents the load order for a dependency
#[derive(Debug, Deserialize, Default, Clone, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum LoadOrder {
    /// Load the dependency before this plugin
    Before,
    /// Load the dependency after this plugin
    After,
    /// Undefined ordering behavior
    #[default]
    Omit,
}

/// Represents a single dependency declaration in Paper plugins
#[derive(Debug, Deserialize, Clone)]
pub struct PaperDependency {
    /// Whether this plugin should load before or after your plugin
    /// Defaults to OMIT
    #[serde(default)]
    pub load: LoadOrder,

    /// Whether this plugin is required for your plugin to load
    /// Defaults to true
    #[serde(default = "default_true")]
    pub required: bool,

    /// Whether your plugin should have access to their classpath
    /// Defaults to true
    #[serde(rename = "join-classpath")]
    #[serde(default = "default_true")]
    pub join_classpath: bool,
}

const fn default_true() -> bool {
    true
}

impl Default for PaperDependency {
    fn default() -> Self {
        Self {
            load: LoadOrder::Omit,
            required: true,
            join_classpath: true,
        }
    }
}

/// Represents the dependencies section which is split into bootstrap and server
#[derive(Debug, Deserialize, Default, Clone)]
pub struct PaperDependencies {
    /// Dependencies used in the bootstrapper phase
    #[serde(default)]
    pub bootstrap: Option<HashMap<String, PaperDependency>>,

    /// Dependencies used for core functionality during server runtime
    #[serde(default)]
    pub server: Option<HashMap<String, PaperDependency>>,
}

impl PaperDependencies {
    /// Get all bootstrap dependencies
    #[must_use]
    pub fn get_bootstrap_deps(&self) -> Vec<(&String, &PaperDependency)> {
        self.bootstrap
            .as_ref()
            .map(|deps| deps.iter().collect())
            .unwrap_or_default()
    }

    /// Get all server dependencies
    #[must_use]
    pub fn get_server_deps(&self) -> Vec<(&String, &PaperDependency)> {
        self.server
            .as_ref()
            .map(|deps| deps.iter().collect())
            .unwrap_or_default()
    }

    /// Get all required bootstrap dependencies
    #[must_use]
    pub fn get_required_bootstrap_deps(&self) -> Vec<&String> {
        self.bootstrap
            .as_ref()
            .map(|deps| {
                deps.iter()
                    .filter(|(_, dep)| dep.required)
                    .map(|(name, _)| name)
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Get all required server dependencies
    #[must_use]
    pub fn get_required_server_deps(&self) -> Vec<&String> {
        self.server
            .as_ref()
            .map(|deps| {
                deps.iter()
                    .filter(|(_, dep)| dep.required)
                    .map(|(name, _)| name)
                    .collect()
            })
            .unwrap_or_default()
    }

    /// Check if a plugin is a bootstrap dependency
    #[must_use]
    pub fn has_bootstrap_dep(&self, name: &str) -> bool {
        self.bootstrap
            .as_ref()
            .is_some_and(|deps| deps.contains_key(name))
    }

    /// Check if a plugin is a server dependency
    #[must_use]
    pub fn has_server_dep(&self, name: &str) -> bool {
        self.server
            .as_ref()
            .is_some_and(|deps| deps.contains_key(name))
    }
}

/// The main paper-plugin.yml configuration structure
#[derive(Debug, Deserialize)]
pub struct PaperPluginYml {
    // Required fields
    /// The name of your plugin
    pub name: String,

    /// The current version of the plugin
    pub version: String,

    /// The main class of your plugin (extends `JavaPlugin`)
    pub main: String,

    // Optional metadata fields
    /// A short description of your plugin
    #[serde(default)]
    pub description: Option<String>,

    /// The author(s) of the plugin
    #[serde(default)]
    pub author: Option<String>,

    /// List of authors
    #[serde(default)]
    pub authors: Option<Vec<String>>,

    /// Contributors to the plugin
    #[serde(default)]
    pub contributors: Option<Vec<String>>,

    /// The website of the plugin
    #[serde(default)]
    pub website: Option<String>,

    // API version
    /// The version of the Paper API (e.g., "1.21")
    #[serde(rename = "api-version")]
    #[serde(default)]
    pub api_version: Option<String>,

    // Paper-specific fields
    /// The bootstrapper class that implements `PluginBootstrap`
    #[serde(default)]
    pub bootstrapper: Option<String>,

    /// The loader class that implements `PluginLoader`
    #[serde(default)]
    pub loader: Option<String>,

    // Dependencies (Paper plugin style)
    /// Dependencies split into bootstrap and server sections
    #[serde(default)]
    pub dependencies: Option<PaperDependencies>,

    // Optional: provides field (similar to Bukkit)
    /// Other plugins this plugin can substitute for
    #[serde(default)]
    pub provides: Option<Vec<String>>,
}

impl PaperPluginYml {
    /// Parse a paper-plugin.yml from a YAML string
    pub fn parse(yaml: &str) -> Result<Self, serde_saphyr::Error> {
        serde_saphyr::from_str(yaml)
    }

    /// Get all authors (combines author and authors fields)
    #[must_use]
    pub fn get_all_authors(&self) -> Vec<String> {
        let mut result = Vec::new();
        if let Some(ref author) = self.author {
            result.push(author.clone());
        }
        if let Some(ref authors) = self.authors {
            result.extend(authors.clone());
        }
        result
    }

    /// Check if this plugin has a bootstrapper
    #[must_use]
    pub const fn has_bootstrapper(&self) -> bool {
        self.bootstrapper.is_some()
    }

    /// Check if this plugin has a custom loader
    #[must_use]
    pub const fn has_loader(&self) -> bool {
        self.loader.is_some()
    }

    /// Get dependencies (returns default if none specified)
    #[must_use]
    pub fn get_dependencies(&self) -> PaperDependencies {
        self.dependencies.clone().unwrap_or_default()
    }

    /// Check if a plugin is required (in either bootstrap or server)
    #[must_use]
    pub fn requires_plugin(&self, name: &str) -> bool {
        if let Some(ref deps) = self.dependencies {
            // Check bootstrap dependencies
            if let Some(ref bootstrap) = deps.bootstrap
                && let Some(dep) = bootstrap.get(name)
                && dep.required
            {
                return true;
            }
            // Check server dependencies
            if let Some(ref server) = deps.server
                && let Some(dep) = server.get(name)
                && dep.required
            {
                return true;
            }
        }
        false
    }

    /// Get all plugins that should load BEFORE this plugin
    #[must_use]
    pub fn get_load_before_deps(&self) -> Vec<String> {
        let mut result = Vec::new();
        if let Some(ref deps) = self.dependencies {
            if let Some(ref bootstrap) = deps.bootstrap {
                for (name, dep) in bootstrap {
                    if dep.load == LoadOrder::Before {
                        result.push(name.clone());
                    }
                }
            }
            if let Some(ref server) = deps.server {
                for (name, dep) in server {
                    if dep.load == LoadOrder::Before {
                        result.push(name.clone());
                    }
                }
            }
        }
        result
    }

    /// Get all plugins that should load AFTER this plugin
    #[must_use]
    pub fn get_load_after_deps(&self) -> Vec<String> {
        let mut result = Vec::new();
        if let Some(ref deps) = self.dependencies {
            if let Some(ref bootstrap) = deps.bootstrap {
                for (name, dep) in bootstrap {
                    if dep.load == LoadOrder::After {
                        result.push(name.clone());
                    }
                }
            }
            if let Some(ref server) = deps.server {
                for (name, dep) in server {
                    if dep.load == LoadOrder::After {
                        result.push(name.clone());
                    }
                }
            }
        }
        result
    }
}
