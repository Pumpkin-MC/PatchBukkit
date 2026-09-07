use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::permission::{
        HasPlayerPermissionRequest, HasPlayerPermissionResponse, RegisterBridgePermissionRequest,
    },
};

pub fn ffi_native_bridge_has_player_permission_impl(
    request: HasPlayerPermissionRequest,
) -> Option<HasPlayerPermissionResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.uuid.as_ref()?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;
    // The shortcut also fires Pumpkin's PlayerPermissionCheckEvent.
    let has = player.has_permission(&ctx.plugin_context.server, &request.node);
    Some(HasPlayerPermissionResponse { has })
}

pub fn ffi_native_bridge_set_player_permission_impl(
    request: crate::proto::patchbukkit::permission::SetPlayerPermissionRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.uuid.as_ref()?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    // The manager keys by id and tolerates offline players, so no live
    // lookup is needed here.
    ctx.plugin_context.server.permission_manager.set_permission(
        player_uuid,
        request.node,
        request.value,
    );
    Some(())
}

pub fn ffi_native_bridge_unset_player_permission_impl(
    request: crate::proto::patchbukkit::permission::UnsetPlayerPermissionRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let uuid_str = &request.uuid.as_ref()?.value;
    let player_uuid = uuid::Uuid::parse_str(uuid_str).ok()?;
    ctx.plugin_context
        .server
        .permission_manager
        .unset_permission(&player_uuid, &request.node);
    Some(())
}

pub fn ffi_native_bridge_register_bridge_permission_impl(
    request: RegisterBridgePermissionRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let default = match request.default_name.to_ascii_uppercase().as_str() {
        "TRUE" => pumpkin_util::permission::PermissionDefault::Allow,
        "OP" => pumpkin_util::permission::PermissionDefault::Op(
            ctx.plugin_context.server.basic_config.op_permission_level,
        ),
        _ => pumpkin_util::permission::PermissionDefault::Deny,
    };
    // Duplicate registration is a plain error, not a failure: Bukkit
    // plugins re-register on reload.
    let _ = ctx
        .plugin_context
        .server
        .permission_manager
        .register_permission(pumpkin_util::permission::Permission {
            node: request.node,
            description: request.description,
            default,
            children: std::collections::HashMap::new(),
        });
    Some(())
}
