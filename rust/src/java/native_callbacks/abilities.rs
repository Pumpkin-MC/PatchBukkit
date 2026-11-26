use crate::java::native_callbacks::utils::with_player;
use crate::proto::patchbukkit::{
    abilities::{Abilities, SetAbilitiesRequest, SetAbilitiesResponse},
    common::Uuid,
};

pub fn ffi_native_bridge_get_abilities_impl(request: Uuid) -> Option<Abilities> {
    with_player(Some(&request), |player| {
        let abilities = match player.abilities.try_lock() {
            Ok(guard) => Abilities {
                invulnerable: guard.invulnerable,
                flying: guard.flying,
                allow_flying: guard.allow_flying,
                creative: guard.creative,
                allow_modify_world: guard.allow_modify_world,
                fly_speed: guard.fly_speed,
                walk_speed: guard.walk_speed,
            },
            Err(_) => {
                let mut acquired = None;
                for _ in 0..10 {
                    std::thread::yield_now();
                    if let Ok(guard) = player.abilities.try_lock() {
                        acquired = Some(Abilities {
                            invulnerable: guard.invulnerable,
                            flying: guard.flying,
                            allow_flying: guard.allow_flying,
                            creative: guard.creative,
                            allow_modify_world: guard.allow_modify_world,
                            fly_speed: guard.fly_speed,
                            walk_speed: guard.walk_speed,
                        });
                        break;
                    }
                }
                acquired.unwrap_or(Abilities {
                    invulnerable: false,
                    flying: false,
                    allow_flying: false,
                    creative: false,
                    allow_modify_world: true,
                    fly_speed: 0.05,
                    walk_speed: 0.1,
                })
            }
        };

        Some(abilities)
    })?
}

pub fn ffi_native_bridge_set_abilities_impl(
    request: SetAbilitiesRequest,
) -> Option<SetAbilitiesResponse> {
    let abilities = request.abilities?;
    with_player(request.uuid.as_ref(), |player| {
        {
            let mut pumpkin_abilities = player
                .abilities
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            pumpkin_abilities.invulnerable = abilities.invulnerable;
            pumpkin_abilities.flying = abilities.flying;
            pumpkin_abilities.allow_flying = abilities.allow_flying;
            pumpkin_abilities.creative = abilities.creative;
            pumpkin_abilities.allow_modify_world = abilities.allow_modify_world;
            pumpkin_abilities.fly_speed = abilities.fly_speed;
            pumpkin_abilities.walk_speed = abilities.walk_speed;
        }
        player.send_abilities_update();

        SetAbilitiesResponse { success: true }
    })
}
