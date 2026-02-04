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
                .map(|s| {
                    let mut sound_event = SoundEvent::default();
                    sound_event.id = *s as u32;
                    sound_event.name = s.to_name().to_string();
                    sound_event
                })
                .collect::<Vec<_>>();
            let mut data = SoundEventRegistryData::default();
            data.sound_events = sounds;
            Registry::SoundEvent(data)
        }
        _ => unreachable!(),
    };

    let mut response = GetRegistryDataResponse::default();
    response.registry = Some(registry);
    Some(response)
}
