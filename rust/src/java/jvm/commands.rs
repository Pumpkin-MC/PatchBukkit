use std::path::PathBuf;

use anyhow::Result;
use j4rs::Instance;
use tokio::sync::oneshot;

use crate::events::Event;

pub enum LoadPluginResult {
    SuccessfullyLoadedSpigot,
    SuccessfullyLoadedPaper,
    FailedToLoadSpigotPlugin(anyhow::Error),
    FailedToLoadPaperPlugin(anyhow::Error),
    FailedToReadConfigurationFile(anyhow::Error),
    NoConfigurationFile,
}

pub enum JvmCommand {
    Initialize {
        j4rs_path: PathBuf,
        respond_to: oneshot::Sender<Result<()>>,
    },
    JavaCallback {
        instance: Instance,
        respond_to: oneshot::Sender<Result<()>>,
    },
    LoadPlugin {
        plugin_path: PathBuf,
        respond_to: oneshot::Sender<LoadPluginResult>,
    },
    InstantiateAllPlugins {
        respond_to: oneshot::Sender<Result<()>>,
    },
    EnableAllPlugins {
        respond_to: oneshot::Sender<Result<()>>,
    },
    DisableAllPlugins {
        respond_to: oneshot::Sender<Result<()>>,
    },
    Shutdown {
        respond_to: oneshot::Sender<Result<()>>,
    },
    TriggerEvent {
        event: Event,
        respond_to: oneshot::Sender<Result<Event>>,
    },
}
