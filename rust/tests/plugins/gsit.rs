use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        BlockPlaceEvent, PlayerInteractEvent, PlayerToggleSneakEvent, event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_gsit_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("GSit.jar") {
        return;
    }

    let sitter_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(sitter_uuid, "GSitTester", 3301)
        .await;

    harness.run_console_command("gsit reload").await;

    // Place stairs & slabs and right-click them to trigger GSit block interaction listeners
    for (idx, block) in [
        "minecraft:oak_stairs",
        "minecraft:stone_brick_stairs",
        "minecraft:oak_slab",
    ]
    .iter()
    .enumerate()
    {
        let x = 10 + idx as i32;
        let _ = harness
            .fire_event(
                "",
                EventData::BlockPlace(BlockPlaceEvent {
                    player_uuid: Some(ProtoUuid {
                        value: sitter_uuid.to_string(),
                    }),
                    block_placed: (*block).to_string(),
                    block_placed_against: "minecraft:stone".to_string(),
                    block_x: x,
                    block_y: 64,
                    block_z: 10,
                    can_build: true,
                }),
            )
            .await;

        let _ = harness
            .fire_event(
                "",
                EventData::PlayerInteract(PlayerInteractEvent {
                    player_uuid: Some(ProtoUuid {
                        value: sitter_uuid.to_string(),
                    }),
                    action: "RIGHT_CLICK_BLOCK".to_string(),
                    clicked_pos_x: x,
                    clicked_pos_y: 64,
                    clicked_pos_z: 10,
                    block: (*block).to_string(),
                }),
            )
            .await;

        let _ = harness
            .fire_event(
                "",
                EventData::PlayerToggleSneak(PlayerToggleSneakEvent {
                    player_uuid: Some(ProtoUuid {
                        value: sitter_uuid.to_string(),
                    }),
                    is_sneaking: true,
                }),
            )
            .await;

        let _ = harness
            .fire_event(
                "",
                EventData::PlayerToggleSneak(PlayerToggleSneakEvent {
                    player_uuid: Some(ProtoUuid {
                        value: sitter_uuid.to_string(),
                    }),
                    is_sneaking: false,
                }),
            )
            .await;
    }

    harness
        .simulate_player_quit(sitter_uuid, "GSitTester")
        .await;
}
