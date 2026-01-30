use std::{
    path::PathBuf,
    sync::{Arc, Mutex},
};

use anyhow::Result;
use j4rs::Instance;
use pumpkin::{command::CommandSender, plugin::Context, server::Server};
use tokio::sync::{mpsc, oneshot};

use crate::{events::Event, java::jvm::command_executor::SimpleCommandSender};

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
        context: Arc<Context>,
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
        server: Arc<Context>,
        command_tx: mpsc::Sender<JvmCommand>,
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
    TriggerCommand {
        cmd_name: String,
        command_sender: SimpleCommandSender,
        respond_to: oneshot::Sender<Result<Event>>,
        command: Arc<Mutex<Instance>>,
    },
}
