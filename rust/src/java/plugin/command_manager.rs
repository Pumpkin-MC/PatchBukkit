use anyhow::Result;
use jni::Env;
use pumpkin_protocol::java::client::play::CommandSuggestion;

use crate::{commands::SimpleCommandSender, java::jvm::commands::Location};

pub struct CommandManager;

impl Default for CommandManager {
    fn default() -> Self {
        Self::new()
    }
}

impl CommandManager {
    #[must_use]
    pub const fn new() -> Self {
        Self
    }

    pub fn get_tab_complete(
        &self,
        env: &mut Env,
        sender: SimpleCommandSender,
        full_command: String,
        location: Option<Location>,
    ) -> Option<Vec<CommandSuggestion>> {
        Self::get_tab_complete_static(env, sender, full_command, location)
    }

    pub fn get_tab_complete_static(
        env: &mut Env,
        sender: SimpleCommandSender,
        full_command: String,
        location: Option<Location>,
    ) -> Option<Vec<CommandSuggestion>> {
        match Self::try_tab_complete(env, sender, full_command, location) {
            Ok(suggestions) => suggestions,
            Err(e) => {
                if env.exception_check() {
                    env.exception_describe();
                    env.exception_clear();
                }
                tracing::warn!("Tab completion failed: {e}");
                None
            }
        }
    }

    fn try_tab_complete(
        env: &mut Env,
        sender: SimpleCommandSender,
        full_command: String,
        location: Option<Location>,
    ) -> Result<Option<Vec<CommandSuggestion>>> {
        let (uuid_jstr, name_jstr, is_op) = match sender {
            SimpleCommandSender::Console => (None, None, true),
            SimpleCommandSender::Player(uuid_str, name, is_op) => (
                Some(env.new_string(&uuid_str)?),
                Some(env.new_string(&name)?),
                is_op,
            ),
        };

        let cmd_jstr = env.new_string(&full_command)?;
        let (world_jstr, x, y, z) = match location {
            Some(loc) => (
                Some(env.new_string(loc.world.to_string())?),
                loc.x,
                loc.y,
                loc.z,
            ),
            None => (None, 0.0, 0.0, 0.0),
        };

        let null_obj = jni::objects::JObject::null();
        let uuid_val = uuid_jstr.as_deref().unwrap_or(&null_obj).into();
        let name_val = name_jstr.as_deref().unwrap_or(&null_obj).into();
        let world_val = world_jstr.as_deref().unwrap_or(&null_obj).into();

        let result = env.call_static_method(
            jni::jni_str!("org/patchbukkit/bootstrap/CommandBridge"),
            jni::jni_str!("tabCompleteRaw"),
            jni::jni_sig!("(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;Ljava/lang/String;DDD)[Ljava/lang/String;"),
            &[
                uuid_val,
                name_val,
                jni::objects::JValue::Bool(is_op),
                (&cmd_jstr).into(),
                world_val,
                jni::objects::JValue::Double(x),
                jni::objects::JValue::Double(y),
                jni::objects::JValue::Double(z),
            ],
        )?;

        let obj = result.l()?;
        if obj.is_null() {
            return Ok(None);
        }

        let array = jni::objects::JObjectArray::<jni::objects::JString>::cast_local(env, obj)?;
        let len = array.len(env)?;
        let mut suggestions = Vec::with_capacity(len);
        for i in 0..len {
            let elem = array.get_element(env, i)?;
            if !elem.is_null() {
                let rust_str = elem.try_to_string(env)?;
                suggestions.push(CommandSuggestion::new(rust_str, None));
            }
        }

        Ok(Some(suggestions))
    }

    pub fn trigger_command(
        &mut self,
        env: &mut Env,
        full_command: String,
        sender: SimpleCommandSender,
    ) -> Result<()> {
        Self::trigger_command_static(env, full_command, sender)
    }

    pub fn trigger_command_static(
        env: &mut Env,
        full_command: String,
        sender: SimpleCommandSender,
    ) -> Result<()> {
        let (uuid_jstr, name_jstr, is_op) = match sender {
            SimpleCommandSender::Console => (None, None, true),
            SimpleCommandSender::Player(uuid_str, name, is_op) => (
                Some(env.new_string(&uuid_str)?),
                Some(env.new_string(&name)?),
                is_op,
            ),
        };

        let cmd_jstr = env.new_string(&full_command)?;
        let null_obj = jni::objects::JObject::null();
        let uuid_val = uuid_jstr.as_deref().unwrap_or(&null_obj).into();
        let name_val = name_jstr.as_deref().unwrap_or(&null_obj).into();

        let result = env.call_static_method(
            jni::jni_str!("org/patchbukkit/bootstrap/CommandBridge"),
            jni::jni_str!("dispatchRaw"),
            jni::jni_sig!("(Ljava/lang/String;Ljava/lang/String;ZLjava/lang/String;)Z"),
            &[
                uuid_val,
                name_val,
                jni::objects::JValue::Bool(is_op),
                (&cmd_jstr).into(),
            ],
        );

        match result {
            Ok(val) => {
                let handled = val.z()?;
                if !handled {
                    tracing::warn!("Command not handled by any Java plugin: {full_command}");
                }
            }
            Err(e) => {
                if env.exception_check() {
                    env.exception_describe();
                    env.exception_clear();
                }
                tracing::error!("Java exception during command dispatch '{full_command}': {e:?}");
            }
        }

        Ok(())
    }
}

#[cfg(test)]
const PATCHBUKKIT_PERMISSION_NAMESPACE: &str = "patchbukkit";

#[cfg(test)]
fn build_permission_node(
    cmd_name: &str,
    cmd_data: &crate::config::spigot::Command,
) -> (String, pumpkin_util::permission::PermissionDefault) {
    let clean = cmd_name.trim_start_matches('/');
    if let Some(permission) = &cmd_data.permission {
        let perm_node = if permission.contains(':') {
            permission.clone()
        } else {
            format!("{PATCHBUKKIT_PERMISSION_NAMESPACE}:{permission}")
        };
        (
            perm_node,
            pumpkin_util::permission::PermissionDefault::Allow,
        )
    } else {
        (
            format!("{PATCHBUKKIT_PERMISSION_NAMESPACE}:command.{clean}"),
            pumpkin_util::permission::PermissionDefault::Allow,
        )
    }
}

#[cfg(test)]
mod tests {
    use pumpkin_util::permission::PermissionDefault;

    use crate::config::spigot::Command;

    use super::build_permission_node;

    #[test]
    fn permission_node_uses_explicit_plugin_permission() {
        let command = Command {
            description: None,
            usage: None,
            aliases: None,
            permission: Some("simplespawn.spawn".to_string()),
            permission_message: None,
        };

        assert_eq!(
            build_permission_node("spawn", &command),
            (
                "patchbukkit:simplespawn.spawn".to_string(),
                PermissionDefault::Allow
            )
        );
    }

    #[test]
    fn permission_node_falls_back_to_patchbukkit_namespace() {
        let command = Command {
            description: None,
            usage: None,
            aliases: None,
            permission: None,
            permission_message: None,
        };

        assert_eq!(
            build_permission_node("spawn", &command),
            (
                "patchbukkit:command.spawn".to_string(),
                PermissionDefault::Allow
            )
        );
    }
}
