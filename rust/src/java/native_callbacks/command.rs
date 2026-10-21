use std::sync::{LazyLock, Mutex};

use pumpkin_util::permission::{Permission, PermissionDefault};

use crate::{commands::init_java_command, proto::patchbukkit::command::RegisterCommandRequest};

static COMMAND_REGISTER_LOCK: LazyLock<Mutex<()>> = LazyLock::new(|| Mutex::new(()));

pub fn ffi_native_bridge_register_command_impl(request: RegisterCommandRequest) -> Option<()> {
    let Some(context) = super::CALLBACK_CONTEXT.get() else {
        tracing::warn!(
            "Failed to register command '{}': CALLBACK_CONTEXT is not initialized",
            request.cmd_name
        );
        return None;
    };

    let cmd_name = request.cmd_name;
    let aliases = request.aliases;
    let description = request.description;
    let plugin_name = request.plugin_name;
    let command_tx = context.command_tx.clone();
    let plugin_context = context.plugin_context.clone();

    tracing::info!(
        "Registering Bukkit command '{}' from plugin '{}' (aliases: {:?})",
        cmd_name,
        plugin_name,
        aliases
    );

    let mut raw_names: Vec<String> = vec![cmd_name.clone()];
    raw_names.extend(aliases);

    let mut names: Vec<String> = Vec::new();
    for name in raw_names {
        let trimmed = name.trim();
        let had_slash = trimmed.starts_with('/');
        let clean = trimmed.trim_start_matches('/').trim().to_string();
        if !clean.is_empty() && !clean.contains(':') {
            let normalized = if had_slash {
                format!("/{clean}")
            } else {
                clean
            };
            if !names.contains(&normalized) {
                names.push(normalized);
            }
        }
    }

    let cmd_trimmed = cmd_name.trim();
    let cmd_clean = cmd_trimmed.trim_start_matches('/').trim();
    if cmd_clean.is_empty() || cmd_clean.contains(':') {
        return Some(());
    }
    let primary_name = if cmd_trimmed.starts_with('/') {
        format!("/{cmd_clean}")
    } else {
        cmd_clean.to_string()
    };
    let alias_names: Vec<String> = names
        .iter()
        .filter(|n| *n != &primary_name)
        .cloned()
        .collect();

    let node = init_java_command(primary_name, command_tx, description);

    let clean_perm = cmd_name.trim_start_matches('/');
    let permission = format!("patchbukkit:command.{clean_perm}");

    let registry = plugin_context.server.permission_manager.clone();

    if let Err(e) = registry.register_permission(Permission::new(
        &permission,
        &format!("Allows running command `{cmd_name}` from `{plugin_name}`"),
        PermissionDefault::Allow,
    )) {
        tracing::debug!("Permission '{}' registration notice: {}", permission, e);
    }

    {
        let _guard = COMMAND_REGISTER_LOCK.lock().unwrap();
        plugin_context.register_command_with_aliases(node, &alias_names, permission.clone());
    }

    tracing::info!(
        "Successfully registered command '{}' with names {:?} and permission '{}'",
        cmd_name,
        names,
        permission
    );

    Some(())
}
