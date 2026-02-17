use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::{common::EmptyRequest, config::GetPatchBukkitConfigResponse},
};

pub fn ffi_native_bridge_get_patch_bukkit_config_impl(
    _request: EmptyRequest,
) -> Option<GetPatchBukkitConfigResponse> {
    if let Some(context) = CALLBACK_CONTEXT.get() {
        Some(GetPatchBukkitConfigResponse {
            minimum_supported_plugin_api: context
                .config
                .settings
                .minimum_supported_plugin_api
                .clone()
                .unwrap_or("0.0.0".to_string()),
        })
    } else {
        None
    }
}
