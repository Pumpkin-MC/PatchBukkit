use std::{fs, path::Path};

use serde::{Deserialize, Serialize};

#[derive(Deserialize, Serialize, Debug)]
pub struct PatchBukkitConfig {
    pub settings: SettingsConfig,
}

#[derive(Deserialize, Serialize, Debug)]
#[serde(rename_all = "kebab-case")]
pub struct SettingsConfig {
    pub minimum_supported_plugin_api: Option<String>,
}

impl PatchBukkitConfig {
    pub const DEFAULT: PatchBukkitConfig = PatchBukkitConfig {
        settings: SettingsConfig {
            minimum_supported_plugin_api: None,
        },
    };

    pub fn parse<S: AsRef<str>>(config: S) -> Result<Self, toml::de::Error> {
        toml::from_str(config.as_ref())
    }

    pub fn get_or_create<P: AsRef<Path>>(config_path: P) -> anyhow::Result<Self> {
        let config_path = config_path.as_ref();
        if !config_path.exists() {
            let default = PatchBukkitConfig::DEFAULT;
            std::fs::write(config_path, &toml::to_string_pretty(&default)?)?;
            return Ok(default);
        }
        let config = PatchBukkitConfig::parse(fs::read_to_string(config_path)?)?;
        Ok(config)
    }
}
