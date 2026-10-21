use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        BlockBreakEvent, EntityDamageByEntityEvent, PlayerExpChangeEvent, PlayerMoveEvent,
        event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_auraskills_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("AuraSkills.jar") {
        return;
    }

    let hero_uuid = Uuid::new_v4();
    let target_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(hero_uuid, "AuraHero", 3501)
        .await;
    harness
        .simulate_player_join(target_uuid, "AuraDummy", 3502)
        .await;

    // 1. Admin & leaderboard commands
    harness.run_console_command("skills version").await;
    harness.run_console_command("skills rank").await;
    harness
        .run_console_command("skills xp add AuraHero mining 250")
        .await;
    harness
        .run_console_command("skills xp add AuraHero foraging 150")
        .await;
    harness
        .run_console_command("skills xp add AuraHero fighting 100")
        .await;
    harness.run_console_command("skills profile AuraHero").await;

    // 2. Trigger Mining, Foraging, and Farming XP listeners via BlockBreakEvent
    for (idx, block) in [
        "minecraft:stone",
        "minecraft:diamond_ore",
        "minecraft:oak_log",
        "minecraft:birch_log",
        "minecraft:wheat",
    ]
    .iter()
    .enumerate()
    {
        let _ = harness
            .fire_event(
                "",
                EventData::BlockBreak(BlockBreakEvent {
                    player_uuid: Some(ProtoUuid {
                        value: hero_uuid.to_string(),
                    }),
                    block: (*block).to_string(),
                    block_x: 30 + idx as i32,
                    block_y: 64,
                    block_z: 0,
                    exp: 2,
                    drop: true,
                }),
            )
            .await;
    }

    // 3. Trigger Agility / Jump & XP listeners
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerMove(PlayerMoveEvent {
                player_uuid: Some(ProtoUuid {
                    value: hero_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 2.0,
                to_y: 65.0,
                to_z: 2.0,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerExpChange(PlayerExpChangeEvent {
                player_uuid: Some(ProtoUuid {
                    value: hero_uuid.to_string(),
                }),
                amount: 35,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::EntityDamageByEntity(EntityDamageByEntityEvent {
                entity_id: 3502,
                damager_id: 3501,
                damage: 6.0,
                cause: "ENTITY_ATTACK".to_string(),
            }),
        )
        .await;

    harness.simulate_player_quit(hero_uuid, "AuraHero").await;
    harness.simulate_player_quit(target_uuid, "AuraDummy").await;
}
