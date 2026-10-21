use crate::proto::patchbukkit::registry::{
    GetRegistryDataRequest, GetRegistryDataResponse, RegistryType, SoundEvent,
    SoundEventRegistryData, get_registry_data_response::Registry,
};

pub fn ffi_native_bridge_get_registry_data_impl(
    request: GetRegistryDataRequest,
) -> Option<GetRegistryDataResponse> {
    let registry = match request.registry {
        val if val == RegistryType::SoundEvent as i32 => {
            let sounds = pumpkin_data::sound::Sound::slice()
                .iter()
                .map(|s| SoundEvent {
                    id: *s as u32,
                    name: s.to_name().to_string(),
                })
                .collect::<Vec<_>>();
            Registry::SoundEvent(SoundEventRegistryData {
                sound_events: sounds,
            })
        }
        _ => return None,
    };

    Some(GetRegistryDataResponse {
        registry: Some(registry),
    })
}
