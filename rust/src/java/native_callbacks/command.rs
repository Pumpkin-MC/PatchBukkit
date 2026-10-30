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

    let cmd_clean = cmd_name.trim().trim_start_matches('/').trim().to_string();
    if cmd_clean.is_empty() || cmd_clean.contains(':') {
        return Some(());
    }

    let primary_name = cmd_clean.clone();

    let mut alias_names: Vec<String> = Vec::new();
    let slash_primary = format!("/{cmd_clean}");
    if !alias_names.contains(&slash_primary) {
        alias_names.push(slash_primary);
    }

    for alias in aliases {
        let a_clean = alias.trim().trim_start_matches('/').trim().to_string();
        if !a_clean.is_empty() && !a_clean.contains(':') {
            if a_clean != primary_name && !alias_names.contains(&a_clean) {
                alias_names.push(a_clean.clone());
            }
            let a_slash = format!("/{a_clean}");
            if a_slash != primary_name && !alias_names.contains(&a_slash) {
                alias_names.push(a_slash);
            }
        }
    }

    let node = init_java_command(primary_name.clone(), command_tx, description);

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
        "Successfully registered command '{}' with aliases {:?} and permission '{}'",
        primary_name,
        alias_names,
        permission
    );

    Some(())
}
