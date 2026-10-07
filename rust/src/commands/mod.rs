use std::borrow::Cow;
use std::sync::Arc;

use pumpkin::{
    command::{
        CommandExecutor, CommandExecutorResult, CommandSender,
        argument_builder::{ArgumentBuilder, CommandArgumentBuilder, argument, command},
        argument_types::core::string::StringArgumentType,
        context::command_context::CommandContext,
        suggestion::{
            provider::SuggestionProvider,
            suggestions::{Suggestions, SuggestionsBuilder},
        },
    },
    entity::EntityBase,
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
    /// UUID, Name, is_op
    Player(String, String, bool),
}

pub struct JavaSuggestionProvider {
    pub command_tx: mpsc::Sender<JvmCommand>,
}

impl SuggestionProvider for JavaSuggestionProvider {
    fn suggest(&self, context: &CommandContext, mut builder: SuggestionsBuilder) -> Suggestions {
        let sender = &context.source.output;
        let location = if let Some(world) = &context.source.world {
            let position = context.source.position;
            let rotation = if let Some(player) = context.source.as_player() {
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
        let full_command = builder.input.clone();

        let res = if let Ok(handle) = tokio::runtime::Handle::try_current() {
            tokio::task::block_in_place(|| {
                handle.block_on(async move {
                    let (tx, rx) = oneshot::channel();
                    if let Err(e) = self
                        .command_tx
                        .send(JvmCommand::GetCommandTabComplete {
                            command_sender,
                            full_command,
                            respond_to: tx,
                            location,
                        })
                        .await
                    {
                        tracing::warn!("Failed to send tab complete to JVM worker: {e}");
                        return None;
                    }

                    rx.await.unwrap_or(None)
                })
            })
        } else {
            let (tx, rx) = oneshot::channel();
            if let Err(e) = self
                .command_tx
                .blocking_send(JvmCommand::GetCommandTabComplete {
                    command_sender,
                    full_command,
                    respond_to: tx,
                    location,
                })
            {
                tracing::warn!("Failed to send tab complete to JVM worker: {e}");
                return builder.build();
            }

            rx.blocking_recv().unwrap_or(None)
        };

        if let Some(suggestions) = res {
            for item in suggestions {
                if let Some(tooltip) = item.tooltip {
                    builder = builder.suggest_with_tooltip(item.suggestion, tooltip);
                } else {
                    builder = builder.suggest(item.suggestion);
                }
            }
        }

        builder.build()
    }
}

impl From<&CommandSender> for SimpleCommandSender {
    fn from(val: &CommandSender) -> Self {
        match val {
            CommandSender::Console | CommandSender::Dummy => Self::Console,
            CommandSender::Player(player) => {
                let is_op =
                    player.permission_lvl.load() >= pumpkin_util::permission::PermissionLvl::Two;
                Self::Player(
                    player.gameprofile.id.to_string(),
                    player.gameprofile.name.clone(),
                    is_op,
                )
            }
            CommandSender::CommandBlock(_block_entity, _world) => Self::Console,
        }
    }
}

impl CommandExecutor for JavaCommandExecutor {
    fn execute(&self, context: &CommandContext) -> CommandExecutorResult {
        if let CommandSender::Player(player) = &context.source.output {
            crate::java::native_callbacks::utils::cache_player(player.clone());
        }

        let full_command = match context.get_argument::<String>(ARG_ANY) {
            Ok(msg) => {
                if self.cmd_name.starts_with('/') {
                    format!("{} {}", self.cmd_name, msg)
                } else {
                    format!("/{} {}", self.cmd_name, msg)
                }
            }
            Err(_) => {
                if self.cmd_name.starts_with('/') {
                    self.cmd_name.clone()
                } else {
                    format!("/{}", self.cmd_name)
                }
            }
        };

        let command_sender: SimpleCommandSender = (&context.source.output).into();

        if let Ok(handle) = tokio::runtime::Handle::try_current() {
            tokio::task::block_in_place(|| {
                handle.block_on(async move {
                    let (tx, rx) = oneshot::channel();
                    if let Err(e) = self
                        .command_tx
                        .send(JvmCommand::TriggerCommand {
                            full_command,
                            respond_to: tx,
                            command_sender,
                        })
                        .await
                    {
                        tracing::error!("Failed to send command to JVM worker: {e}");
                        return Ok(0);
                    }

                    let _ = rx.await;
                    Ok(1)
                })
            })
        } else {
            let (tx, rx) = oneshot::channel();
            if let Err(e) = self.command_tx.blocking_send(JvmCommand::TriggerCommand {
                full_command,
                respond_to: tx,
                command_sender,
            }) {
                tracing::error!("Failed to send command to JVM worker: {e}");
                return Ok(0);
            }

            let _ = rx.blocking_recv();
            Ok(1)
        }
    }
}

pub fn init_java_command(
    cmd_name: impl Into<String>,
    command_tx: mpsc::Sender<JvmCommand>,
    description: impl Into<Cow<'static, str>>,
) -> CommandArgumentBuilder {
    let cmd_name = cmd_name.into();
    let executor = Arc::new(JavaCommandExecutor {
        cmd_name: cmd_name.clone(),
        command_tx: command_tx.clone(),
    });

    command(cmd_name, description)
        .executes_arc(executor.clone())
        .then(
            argument(ARG_ANY, StringArgumentType::GreedyPhrase)
                .suggests(JavaSuggestionProvider { command_tx })
                .executes_arc(executor),
        )
}

#[cfg(test)]
mod tests {
    use super::*;
    use pumpkin::command::dispatcher::CommandDispatcher;

    #[test]
    fn test_init_java_command_registration() {
        let mut dispatcher = CommandDispatcher::default();

        let (tx, _rx) = mpsc::channel(100);
        let aliases = vec!["/fly".to_string(), "//fly".to_string()];
        let builder = init_java_command("fly", tx, "Fly command");
        dispatcher.register_with_aliases(builder, &aliases);

        assert!(dispatcher.has_command("fly"));
        assert!(dispatcher.has_command("/fly"));
        assert!(dispatcher.has_command("//fly"));
    }
}
