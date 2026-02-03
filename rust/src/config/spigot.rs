use std::collections::HashMap;

use serde::Deserialize;

pub const SPIGOT_PLUGIN_CONFIG: &str = "plugin.yml";

/// Represents when a plugin should be loaded
#[derive(Debug, Deserialize, Default, Clone, PartialEq, Eq)]
#[serde(rename_all = "UPPERCASE")]
pub enum LoadOrder {
    #[serde(alias = "startup", alias = "Startup")]
    Startup,
    #[default]
    #[serde(alias = "postworld", alias = "Postworld")]
    Postworld,
}

/// Represents the default permission value
#[derive(Debug, Deserialize, Clone, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
#[derive(Default)]
pub enum DefaultPermission {
    #[default]
    Op,
    #[serde(alias = "notop")]
    NotOp,
    True,
    False,
}

/// Represents a permission node definition
#[derive(Debug, Deserialize, Clone)]
pub struct Permission {
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub default: Option<DefaultPermission>,
    #[serde(default)]
    pub children: Option<HashMap<String, bool>>,
}

/// Represents a command definition
#[derive(Debug, Deserialize, Clone)]
pub struct Command {
    #[serde(default)]
    pub description: Option<String>,
    #[serde(default)]
    pub usage: Option<String>,
    #[serde(default)]
    pub aliases: Option<StringOrList>,
    #[serde(default)]
    pub permission: Option<String>,
    #[serde(rename = "permission-message")]
    #[serde(default)]
    pub permission_message: Option<String>,
}

/// Helper type to handle fields that can be either a single string or a list of strings
#[derive(Debug, Deserialize, Clone)]
#[serde(untagged)]
pub enum StringOrList {
    Single(String),
    Multiple(Vec<String>),
}

impl StringOrList {
    /// Convert to a Vec<String> regardless of the variant
    #[must_use]
    pub fn to_vec(&self) -> Vec<String> {
        match self {
            Self::Single(s) => vec![s.clone()],
            Self::Multiple(v) => v.clone(),
        }
    }
}

/// The main plugin.yml configuration structure
#[derive(Debug, Deserialize, Clone)]
pub struct SpigotPluginYml {
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
    /// The author(s) of the plugin (can be single or list)
    #[serde(default)]
    pub author: Option<String>,
    /// List of authors (alternative to single author)
    #[serde(default)]
    pub authors: Option<Vec<String>>,
    /// Contributors to the plugin
    #[serde(default)]
    pub contributors: Option<Vec<String>>,
    /// The website of the plugin
    #[serde(default)]
    pub website: Option<String>,

    // API and loading configuration
    /// The version of the Paper API (e.g., "1.21")
    #[serde(rename = "api-version")]
    #[serde(default)]
    pub api_version: Option<String>,
    /// When to load the plugin (STARTUP or POSTWORLD)
    #[serde(default)]
    pub load: Option<LoadOrder>,
    /// The prefix for log messages
    #[serde(default)]
    pub prefix: Option<String>,

    // Dependencies
    /// Plugins that must be present for this plugin to load
    #[serde(default)]
    pub depend: Option<Vec<String>>,
    /// Plugins that enhance functionality if present
    #[serde(default)]
    pub softdepend: Option<Vec<String>>,
    /// Plugins that this plugin should load before
    #[serde(default)]
    pub loadbefore: Option<Vec<String>>,
    /// Other plugins this plugin can substitute for
    #[serde(default)]
    pub provides: Option<Vec<String>>,

    // Libraries
    /// Maven dependencies to download
    #[serde(default)]
    pub libraries: Option<Vec<String>>,

    // Commands and permissions
    /// Command definitions
    #[serde(default)]
    pub commands: Option<HashMap<String, Command>>,
    /// Permission definitions
    #[serde(default)]
    pub permissions: Option<HashMap<String, Permission>>,
    /// Default permission value for unspecified permissions
    #[serde(rename = "default-permission")]
    #[serde(default)]
    pub default_permission: Option<DefaultPermission>,

    // Paper-specific fields
    /// Paper plugin loader class
    #[serde(rename = "paper-plugin-loader")]
    #[serde(default)]
    pub paper_plugin_loader: Option<String>,
    /// Skip library resolution (delegate to loader)
    #[serde(rename = "paper-skip-libraries")]
    #[serde(default)]
    pub paper_skip_libraries: Option<bool>,
}

impl SpigotPluginYml {
    /// Parse a plugin.yml from a YAML string
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

    /// Check if this plugin depends on another plugin
    #[must_use]
    pub fn depends_on(&self, plugin_name: &str) -> bool {
        if let Some(ref deps) = self.depend {
            return deps.iter().any(|d| d == plugin_name);
        }
        false
    }

    /// Check if this plugin soft-depends on another plugin
    #[must_use]
    pub fn soft_depends_on(&self, plugin_name: &str) -> bool {
        if let Some(ref deps) = self.softdepend {
            return deps.iter().any(|d| d == plugin_name);
        }
        false
    }
}
