use std::ffi::c_char;

use pumpkin::entity::player::Abilities;
use tokio::sync::MutexGuard;

use crate::java::native_callbacks::{CALLBACK_CONTEXT, utils::get_string};

#[repr(C)]
pub struct AbilitiesFFI {
    pub invulnerable: bool,
    pub flying: bool,
    pub allow_flying: bool,
    pub creative: bool,
    pub allow_modify_world: bool,
    pub fly_speed: f32,
    pub walk_speed: f32,
}

impl AbilitiesFFI {
    fn new(abilities: MutexGuard<'_, Abilities>) -> Self {
        Self {
            invulnerable: abilities.invulnerable,
            flying: abilities.flying,
            allow_flying: abilities.allow_flying,
            creative: abilities.creative,
            allow_modify_world: abilities.allow_modify_world,
            fly_speed: abilities.fly_speed,
            walk_speed: abilities.walk_speed,
        }
    }
}

pub extern "C" fn rust_set_abilities(
    uuid_ptr: *const c_char,
    abilities: *mut AbilitiesFFI,
) -> bool {
    let uuid_str = get_string(uuid_ptr);
    if let Some(ctx) = CALLBACK_CONTEXT.get() {
        let uuid = uuid::Uuid::parse_str(&uuid_str).unwrap();
        let player = ctx.plugin_context.server.get_player_by_uuid(uuid);
        if let Some(player) = player {
            tokio::task::block_in_place(|| {
                ctx.runtime.block_on(async {
                    let mut server_abilities = player.abilities.lock().await;
                    unsafe {
                        server_abilities.allow_flying = (*abilities).allow_flying;
                        server_abilities.allow_modify_world = (*abilities).allow_modify_world;
                        server_abilities.creative = (*abilities).creative;
                        server_abilities.fly_speed = (*abilities).fly_speed;
                        server_abilities.flying = (*abilities).flying;
                        server_abilities.invulnerable = (*abilities).invulnerable;
                        server_abilities.walk_speed = (*abilities).walk_speed;
                    }
                });
            });

            let player = player;
            ctx.runtime.spawn(async move {
                player.send_abilities_update().await;
            });

            return true;
        }
    }

    false
}

pub extern "C" fn rust_get_abilities(uuid_ptr: *const c_char, out: *mut AbilitiesFFI) -> bool {
    let uuid_str = get_string(uuid_ptr);
    if let Some(ctx) = CALLBACK_CONTEXT.get() {
        let uuid = uuid::Uuid::parse_str(&uuid_str).unwrap();
        let player = ctx.plugin_context.server.get_player_by_uuid(uuid);
        if let Some(player) = player {
            let abilities = tokio::task::block_in_place(|| {
                ctx.runtime
                    .block_on(async { AbilitiesFFI::new(player.abilities.lock().await) })
            });

            unsafe {
                *out = abilities;
            }

            return true;
        }
    }

    false
}
