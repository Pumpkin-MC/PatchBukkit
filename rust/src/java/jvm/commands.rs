use std::{path::PathBuf, sync::Arc};

use anyhow::Result;
use pumpkin::{command::dispatcher::CommandError, plugin::Context};
use pumpkin_protocol::java::client::play::CommandSuggestion;
use tokio::sync::{mpsc, oneshot};
use uuid::Uuid;

use crate::{commands::SimpleCommandSender, events::Event};

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
        full_command: String,
        command_sender: SimpleCommandSender,
        respond_to: oneshot::Sender<Result<()>>,
    },
    GetCommandTabComplete {
        command_sender: SimpleCommandSender,
        full_command: String,
        respond_to: oneshot::Sender<Result<Option<Vec<CommandSuggestion>>, CommandError>>,
        location: Option<Location>,
    },
}

pub struct Rotation {
    pub yaw: f32,
    pub pitch: f32,
}

impl Rotation {
    pub fn new(yaw: f32, pitch: f32) -> Self {
        Rotation { yaw, pitch }
    }
}

pub struct Location {
    pub world: Uuid,
    pub x: f64,
    pub y: f64,
    pub z: f64,
    pub rotation: Option<Rotation>,
}

impl Location {
    pub fn new(world: Uuid, x: f64, y: f64, z: f64, rotation: Option<Rotation>) -> Self {
        Location {
            world,
            x,
            y,
            z,
            rotation,
        }
    }
}
