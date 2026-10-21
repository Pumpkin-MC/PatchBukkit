use pumpkin_data::sound::{Sound, SoundCategory};
use pumpkin_protocol::{IdOr, java::client::play::CEntitySoundEffect};
use pumpkin_util::math::vector3::Vector3;
use rand::{RngExt, rng};

use crate::{
    java::native_callbacks::CALLBACK_CONTEXT,
    proto::patchbukkit::sound::{PlayerEntityPlaySoundRequest, PlayerPlaySoundRequest},
};

pub(crate) fn resolve_sound(name: &str) -> Option<Sound> {
    let clean = name.strip_prefix("minecraft:").unwrap_or(name);
    if let Some(s) = Sound::from_name(clean) {
        return Some(s);
    }
    let lower = clean.to_ascii_lowercase();
    if let Some(s) = Sound::from_name(&lower) {
        return Some(s);
    }
    let dotted = lower.replace('_', ".");
    Sound::from_name(&dotted)
}

pub(crate) fn resolve_sound_category(category: &str) -> SoundCategory {
    let clean = category
        .strip_prefix("minecraft:")
        .unwrap_or(category)
        .to_ascii_lowercase();
    SoundCategory::from_name(&clean).unwrap_or(SoundCategory::Master)
}

pub fn ffi_native_bridge_player_entity_play_sound_impl(
    request: PlayerEntityPlaySoundRequest,
) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let player_uuid = uuid::Uuid::parse_str(&request.player_uuid?.value).ok()?;
    let entity_uuid = uuid::Uuid::parse_str(&request.entity_uuid?.value).ok()?;

    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;

    let sound = request.sound?;
    let pumpkin_sound = resolve_sound(&sound.name)?;
    let category = resolve_sound_category(&sound.category);

    let seed = rng().random::<i64>();

    ctx.runtime.spawn(async move {
        let entity_id = if let Some(target_player) =
            ctx.plugin_context.server.get_player_by_uuid(entity_uuid)
        {
            target_player.living_entity.entity.entity_id
        } else {
            let entity = ctx
                .plugin_context
                .server
                .worlds
                .load()
                .iter()
                .find_map(|world| world.get_entity_by_uuid(entity_uuid))?;
            entity.get_entity().entity_id
        };

        player
            .send_client_packet(&CEntitySoundEffect::new(
                IdOr::Id(pumpkin_sound as u16),
                category,
                entity_id.into(),
                request.volume,
                request.pitch,
                seed,
            ))
            .await;

        Some(())
    });

    Some(())
}

pub fn ffi_native_bridge_player_play_sound_impl(request: PlayerPlaySoundRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let player_uuid = uuid::Uuid::parse_str(&request.player_uuid?.value).ok()?;
    let player = ctx.plugin_context.server.get_player_by_uuid(player_uuid)?;

    let sound = request.sound?;
    let pumpkin_sound = resolve_sound(&sound.name)?;
    let category = resolve_sound_category(&sound.category);

    let seed = match request.seed {
        Some(seed) => seed,
        None => rng().random::<i64>(),
    };

    let position = request.location?.position?;
    let position: Vector3<f64> = Vector3::new(position.x, position.y, position.z);

    player.play_sound(
        pumpkin_sound as u16,
        category,
        &position,
        request.volume,
        request.pitch,
        seed,
    );

    Some(())
}
