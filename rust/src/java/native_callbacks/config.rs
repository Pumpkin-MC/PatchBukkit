use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::{common::EmptyRequest, config::GetPatchBukkitConfigResponse},
};

pub fn ffi_native_bridge_get_patch_bukkit_config_impl(
    _request: EmptyRequest,
) -> Option<GetPatchBukkitConfigResponse> {
    CALLBACK_CONTEXT
        .get()
        .map(|context| GetPatchBukkitConfigResponse {
            minimum_supported_plugin_api: context
                .config
                .settings
                .minimum_supported_plugin_api
                .clone()
                // Default floor is the Flattening (1.13): anything older predates
                // the modern API and is rejected by isSupportedApiVersion.
                // An explicit config value still overrides this.
                .unwrap_or("1.13".to_string()),
        })
}
