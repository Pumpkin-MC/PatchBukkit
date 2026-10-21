use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        BlockBreakEvent, BlockDamageEvent, BlockPlaceEvent, EntityDamageByEntityEvent,
        InventoryClickEvent, InventoryCloseEvent, InventoryOpenEvent, PlayerAnimationEvent,
        PlayerItemHeldEvent, PlayerMoveEvent, PlayerToggleSneakEvent, PlayerToggleSprintEvent,
        event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_grimac_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("GrimAC.jar") {
        return;
    }

    let attacker_uuid = Uuid::new_v4();
    let defender_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(attacker_uuid, "GrimFighter1", 4301)
        .await;
    harness
        .simulate_player_join(defender_uuid, "GrimFighter2", 4302)
        .await;

    harness.run_console_command("grim").await;
    harness.run_console_command("grim perf").await;
    harness.run_console_command("grim alerts").await;

    // 1. Toggle sprinting and simulate a realistic 15-tick movement + jump trajectory
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerToggleSprint(PlayerToggleSprintEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                is_sprinting: true,
            }),
        )
        .await;

    let jump_offsets = [
        0.0, 0.42, 0.75, 1.00, 1.16, 1.24, 1.24, 1.16, 1.00, 0.75, 0.42, 0.0, 0.0, 0.0, 0.0,
    ];
    let mut prev_y = 64.0;
    for (idx, &y_off) in jump_offsets.iter().enumerate() {
        let prev_x = idx as f64 * 0.28;
        let next_x = (idx + 1) as f64 * 0.28;
        let next_y = 64.0 + y_off;
        let move_res = harness
            .fire_event(
                "",
                EventData::PlayerMove(PlayerMoveEvent {
                    player_uuid: Some(ProtoUuid {
                        value: attacker_uuid.to_string(),
                    }),
                    from_x: prev_x,
                    from_y: prev_y,
                    from_z: 0.0,
                    to_x: next_x,
                    to_y: next_y,
                    to_z: 0.0,
                }),
            )
            .await;
        assert!(!move_res.cancelled);
        prev_y = next_y;
    }

    // 2. Arm swing + Melee hit (Combat reach & killaura check listeners)
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerAnimation(PlayerAnimationEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                animation_type: "ARM_SWING".to_string(),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::EntityDamageByEntity(EntityDamageByEntityEvent {
                entity_id: 4302,
                damager_id: 4301,
                damage: 5.0,
                cause: "ENTITY_ATTACK".to_string(),
            }),
        )
        .await;

    // 3. Hotbar switch + Inventory Open/Click/Close checks
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerItemHeld(PlayerItemHeldEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                previous_slot: 0,
                new_slot: 2,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::InventoryOpen(InventoryOpenEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::InventoryClick(InventoryClickEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                click_type: "ShiftLeft".to_string(),
                slot: 9,
                raw_slot: 9,
                clicked_item_name: "minecraft:diamond_sword".to_string(),
                clicked_item_count: 1,
                cursor_name: String::new(),
                cursor_count: 0,
                hotbar_button: -1,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::InventoryClose(InventoryCloseEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
            }),
        )
        .await;

    // 4. FastBreak / Scaffold block interaction checks
    let _ = harness
        .fire_event(
            "",
            EventData::BlockDamage(BlockDamageEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                block: "minecraft:stone".to_string(),
                block_x: 4,
                block_y: 64,
                block_z: 0,
                insta_break: false,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::BlockBreak(BlockBreakEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                block: "minecraft:stone".to_string(),
                block_x: 4,
                block_y: 64,
                block_z: 0,
                exp: 0,
                drop: true,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::BlockPlace(BlockPlaceEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                block_placed: "minecraft:cobblestone".to_string(),
                block_placed_against: "minecraft:stone".to_string(),
                block_x: 4,
                block_y: 64,
                block_z: 0,
                can_build: true,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerToggleSneak(PlayerToggleSneakEvent {
                player_uuid: Some(ProtoUuid {
                    value: attacker_uuid.to_string(),
                }),
                is_sneaking: false,
            }),
        )
        .await;

    harness
        .simulate_player_quit(attacker_uuid, "GrimFighter1")
        .await;
    harness
        .simulate_player_quit(defender_uuid, "GrimFighter2")
        .await;
}
