use std::{fs, path::Path};

use serde::{Deserialize, Serialize};

#[derive(Deserialize, Serialize, Debug, Clone, Default, PartialEq, Eq)]
pub struct PatchBukkitConfig {
    #[serde(default)]
    pub settings: SettingsConfig,
    #[serde(default)]
    pub jvm: JvmConfig,
    #[serde(default)]
    pub plugins: PluginsConfig,
    #[serde(default)]
    pub libraries: LibrariesConfig,
    #[serde(default)]
    pub diagnostics: DiagnosticsConfig,
    #[serde(default)]
    pub paper: PaperConfig,
}

#[derive(Deserialize, Serialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub struct SettingsConfig {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub minimum_supported_plugin_api: Option<String>,
}

#[derive(Deserialize, Serialize, Debug, Clone, Default, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub struct JvmConfig {
    /// Maximum heap memory allocated to the JVM (e.g. "2G", "1024M")
    #[serde(skip_serializing_if = "Option::is_none")]
    pub max_heap: Option<String>,
    /// Initial heap memory allocated to the JVM (e.g. "512M")
    #[serde(skip_serializing_if = "Option::is_none")]
    pub initial_heap: Option<String>,
    /// Additional JVM command-line flags
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub extra_args: Vec<String>,
}

#[derive(Deserialize, Serialize, Debug, Clone, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub struct PluginsConfig {
    /// Directory where Bukkit/Paper plugins are located
    #[serde(default = "default_plugins_dir")]
    pub directory: String,
    /// List of plugin names to skip loading (case-insensitive)
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub disabled: Vec<String>,
}

fn default_plugins_dir() -> String {
    "patchbukkit-plugins".to_string()
}

impl Default for PluginsConfig {
    fn default() -> Self {
        Self {
            directory: default_plugins_dir(),
            disabled: Vec::new(),
        }
    }
}

#[derive(Deserialize, Serialize, Debug, Clone, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub struct LibrariesConfig {
    /// Whether to automatically download declared Maven libraries for plugins
    #[serde(default = "default_true")]
    pub download_dependencies: bool,
}

const fn default_true() -> bool {
    true
}

impl Default for LibrariesConfig {
    fn default() -> Self {
        Self {
            download_dependencies: true,
        }
    }
}

#[derive(Deserialize, Serialize, Debug, Clone, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub struct DiagnosticsConfig {
    /// Minimum log level ("info", "warn", "error", "debug", "trace")
    #[serde(default = "default_log_level")]
    pub log_level: String,
    /// Log warnings when plugins call unimplemented Bukkit API methods
    #[serde(default = "default_true")]
    pub warn_unimplemented_api: bool,
    /// Enable verbose bridge and event logging
    #[serde(default)]
    pub debug_bridge: bool,
}

fn default_log_level() -> String {
    "info".to_string()
}

impl Default for DiagnosticsConfig {
    fn default() -> Self {
        Self {
            log_level: default_log_level(),
            warn_unimplemented_api: true,
            debug_bridge: false,
        }
    }
}

/// Paper server that is downloaded and patched locally at runtime.
/// Must target the same Minecraft version as Pumpkin.
#[derive(Deserialize, Serialize, Debug, Clone, PartialEq, Eq)]
#[serde(rename_all = "kebab-case")]
pub struct PaperConfig {
    /// Minecraft version (e.g. "26.3")
    #[serde(default = "default_paper_version")]
    pub version: String,
    /// Paper build number for `version`
    #[serde(default = "default_paper_build")]
    pub build: u32,
    /// Download Paper automatically if it is not installed yet
    #[serde(default = "default_true")]
    pub auto_download: bool,
}

pub const DEFAULT_PAPER_VERSION: &str = "26.3";
pub const DEFAULT_PAPER_BUILD: u32 = 8;

fn default_paper_version() -> String {
    DEFAULT_PAPER_VERSION.to_string()
}

const fn default_paper_build() -> u32 {
    DEFAULT_PAPER_BUILD
}

impl Default for PaperConfig {
    fn default() -> Self {
        Self {
            version: default_paper_version(),
            build: DEFAULT_PAPER_BUILD,
            auto_download: true,
        }
    }
}

impl PatchBukkitConfig {
    pub const DEFAULT: PatchBukkitConfig = PatchBukkitConfig {
        settings: SettingsConfig {
            minimum_supported_plugin_api: None,
        },
        jvm: JvmConfig {
            max_heap: None,
            initial_heap: None,
            extra_args: Vec::new(),
        },
        plugins: PluginsConfig {
            directory: String::new(),
            disabled: Vec::new(),
        },
        libraries: LibrariesConfig {
            download_dependencies: true,
        },
        diagnostics: DiagnosticsConfig {
            log_level: String::new(),
            warn_unimplemented_api: true,
            debug_bridge: false,
        },
        paper: PaperConfig {
            version: String::new(),
            build: DEFAULT_PAPER_BUILD,
            auto_download: true,
        },
    };

    pub fn parse<S: AsRef<str>>(config: S) -> Result<Self, toml::de::Error> {
        toml::from_str(config.as_ref())
    }

    pub fn get_or_create<P: AsRef<Path>>(config_path: P) -> anyhow::Result<Self> {
        let config_path = config_path.as_ref();
        if !config_path.exists() {
            let default = PatchBukkitConfig::default();
            let template = r#"# PatchBukkit Configuration File

[settings]
# Minimum Bukkit/Paper plugin API version required (e.g. "1.20"). Leave unset for all versions.
# minimum-supported-plugin-api = "1.20"

[jvm]
# Maximum and initial heap memory allocated to the embedded JVM (e.g. "2G", "512M")
# max-heap = "2G"
# initial-heap = "512M"

# Additional JVM arguments passed on startup
extra-args = []

[plugins]
# Directory where plugin JARs are located
directory = "patchbukkit-plugins"

# List of plugin names to skip loading (case-insensitive)
disabled = []

[libraries]
# Automatically download declared Maven libraries for plugins
download-dependencies = true

[diagnostics]
# Minimum log level: "info", "warn", "error", "debug", "trace" (default: "info")
log-level = "info"

# Log warnings when plugins call unimplemented Bukkit API methods
warn-unimplemented-api = true

# Enable verbose logging for bridge and FFI events
debug-bridge = false

[paper]
# The Paper server is downloaded from PaperMC and patched locally on first start.
# It must target the same Minecraft version as Pumpkin.
# version = "26.3"
# build = 8

# Download Paper automatically if it is not installed yet
auto-download = true
"#;
            std::fs::write(config_path, template)?;
            return Ok(default);
        }
        let config = PatchBukkitConfig::parse(fs::read_to_string(config_path)?)?;
        Ok(config)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_parse_empty_toml() {
        let config = PatchBukkitConfig::parse("").unwrap();
        assert_eq!(config.plugins.directory, "patchbukkit-plugins");
        assert!(config.plugins.disabled.is_empty());
        assert!(config.libraries.download_dependencies);
        assert_eq!(config.diagnostics.log_level, "info");
        assert!(config.diagnostics.warn_unimplemented_api);
        assert!(!config.diagnostics.debug_bridge);
        assert_eq!(config.jvm.max_heap, None);
        assert_eq!(config.jvm.initial_heap, None);
        assert!(config.jvm.extra_args.is_empty());
    }

    #[test]
    fn test_parse_legacy_toml() {
        let toml_str = r#"
[settings]
minimum-supported-plugin-api = "1.20"
"#;
        let config = PatchBukkitConfig::parse(toml_str).unwrap();
        assert_eq!(
            config.settings.minimum_supported_plugin_api.as_deref(),
            Some("1.20")
        );
        assert_eq!(config.plugins.directory, "patchbukkit-plugins");
        assert_eq!(config.diagnostics.log_level, "info");
        assert!(config.libraries.download_dependencies);
    }

    #[test]
    fn test_parse_full_toml() {
        let toml_str = r#"
[settings]
minimum-supported-plugin-api = "1.20.4"

[jvm]
max-heap = "4G"
initial-heap = "1G"
extra-args = ["-XX:+UseG1GC", "-Dcustom.prop=true"]

[plugins]
directory = "plugins"
disabled = ["ProblematicPlugin", "OldPlugin"]

[libraries]
download-dependencies = false

[diagnostics]
log-level = "debug"
warn-unimplemented-api = false
debug-bridge = true
"#;
        let config = PatchBukkitConfig::parse(toml_str).unwrap();
        assert_eq!(
            config.settings.minimum_supported_plugin_api.as_deref(),
            Some("1.20.4")
        );
        assert_eq!(config.jvm.max_heap.as_deref(), Some("4G"));
        assert_eq!(config.jvm.initial_heap.as_deref(), Some("1G"));
        assert_eq!(
            config.jvm.extra_args,
            vec!["-XX:+UseG1GC", "-Dcustom.prop=true"]
        );
        assert_eq!(config.plugins.directory, "plugins");
        assert_eq!(
            config.plugins.disabled,
            vec!["ProblematicPlugin", "OldPlugin"]
        );
        assert!(!config.libraries.download_dependencies);
        assert_eq!(config.diagnostics.log_level, "debug");
        assert!(!config.diagnostics.warn_unimplemented_api);
        assert!(config.diagnostics.debug_bridge);
    }
}
