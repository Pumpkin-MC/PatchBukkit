use std::borrow::Cow;

use pumpkin::{
    command::{
        CommandExecutor, CommandSender,
        args::{Arg, ArgumentConsumer, GetClientSideArgParser, SuggestResult},
        tree::{CommandTree, builder::argument},
    },
    entity::EntityBase,
};
use pumpkin_protocol::java::client::play::{
    ArgumentType, StringProtoArgBehavior, SuggestionProviders,
};
use tokio::sync::{mpsc, oneshot};

use crate::java::jvm::commands::{JvmCommand, Location, Rotation};

const ARG_ANY: &str = "any";

pub struct JavaCommandExecutor {
    pub cmd_name: String,
    pub command_tx: mpsc::Sender<JvmCommand>,
}

#[derive(Clone)]
pub enum SimpleCommandSender {
    Console,
    /// UUID
    Player(String),
}

pub struct AnyCommandNode {
    command_tx: mpsc::Sender<JvmCommand>,
}

impl GetClientSideArgParser for AnyCommandNode {
    fn get_client_side_parser(&self) -> ArgumentType<'_> {
        ArgumentType::String(StringProtoArgBehavior::GreedyPhrase)
    }

    fn get_client_side_suggestion_type_override(&self) -> Option<SuggestionProviders> {
        Some(SuggestionProviders::AskServer)
    }
}

impl ArgumentConsumer for AnyCommandNode {
    fn consume<'a>(
        &'a self,
        _sender: &'a pumpkin::command::CommandSender,
        _server: &'a pumpkin::server::Server,
        args: &mut pumpkin::command::tree::RawArgs<'a>,
    ) -> pumpkin::command::args::ConsumeResult<'a> {
        let first_word_opt = args.pop();

        let mut msg = match first_word_opt {
            Some(word) => word.to_string(),
            None => return Box::pin(async { None }),
        };

        while let Some(word) = args.pop() {
            msg.push(' ');
            msg.push_str(word);
        }

        Box::pin(async move { Some(Arg::Msg(msg)) })
    }

    fn suggest<'a>(
        &'a self,
        sender: &pumpkin::command::CommandSender,
        _server: &'a pumpkin::server::Server,
        input: &'a str,
    ) -> SuggestResult<'a> {
        let location = if let Some(position) = sender.position()
            && let Some(world) = sender.world()
        {
            let rotation = if let Some(player) = sender.as_player() {
                let entity = player.get_entity();
                let yaw = entity.yaw.load();
                let pitch = entity.pitch.load();
                Some(Rotation::new(yaw, pitch))
            } else {
                None
            };

            Some(Location::new(
                world.uuid, position.x, position.y, position.z, rotation,
            ))
        } else {
            None
        };

        let command_sender: SimpleCommandSender = sender.into();

        Box::pin(async move {
            let (tx, rx) = oneshot::channel();
            self.command_tx
                .send(JvmCommand::GetCommandTabComplete {
                    command_sender,
                    full_command: input.to_string(),
                    respond_to: tx,
                    location,
                })
                .await
                .unwrap();

            let suggestion = rx.await.unwrap();
            suggestion
        })
    }
}

impl Into<SimpleCommandSender> for &CommandSender {
    fn into(self) -> SimpleCommandSender {
        match self {
            CommandSender::Rcon(_mutex) => todo!(),
            CommandSender::Console => SimpleCommandSender::Console,
            CommandSender::Player(player) => {
                SimpleCommandSender::Player(player.gameprofile.id.to_string())
            }
            CommandSender::CommandBlock(_block_entity, _world) => todo!(),
        }
    }
}

impl CommandExecutor for JavaCommandExecutor {
    fn execute<'a>(
        &'a self,
        sender: &'a pumpkin::command::CommandSender,
        _server: &'a pumpkin::server::Server,
        args: &'a pumpkin::command::args::ConsumedArgs<'a>,
    ) -> pumpkin::command::CommandResult<'a> {
        Box::pin(async move {
            let full_command = match args.get(ARG_ANY) {
                Some(Arg::Msg(msg)) => format!("/{} {}", self.cmd_name, msg),
                _ => format!("/{}", self.cmd_name),
            };

            let (tx, _rx) = oneshot::channel();
            self.command_tx
                .send(JvmCommand::TriggerCommand {
                    full_command: full_command,
                    respond_to: tx,
                    command_sender: sender.into(),
                })
                .await
                .unwrap();
            Ok(())
        })
    }
}

pub fn init_java_command(
    cmd_name: impl Into<String>,
    command_tx: mpsc::Sender<JvmCommand>,
    names: impl IntoIterator<Item: Into<String>>,
    description: impl Into<Cow<'static, str>>,
) -> CommandTree {
    let cmd_name = cmd_name.into();
    CommandTree::new(names, description)
        .execute(JavaCommandExecutor {
            cmd_name: cmd_name.clone(),
            command_tx: command_tx.clone(),
        })
        .then(
            argument(
                ARG_ANY,
                AnyCommandNode {
                    command_tx: command_tx.clone(),
                },
            )
            .execute(JavaCommandExecutor {
                cmd_name,
                command_tx,
            }),
        )
}
