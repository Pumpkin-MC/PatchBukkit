use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{PlayerInteractEvent, event::Data as EventData},
};
use uuid::Uuid;

pub async fn verify_worldedit_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("WorldEdit.jar") {
        return;
    }

    let builder_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(builder_uuid, "WE_Architect", 2101)
        .await;

    // 1. Console diagnostic and entity management commands
    harness.run_console_command("worldedit version").await;
    harness
        .run_console_command("worldedit tz Europe/Berlin")
        .await;
    harness.run_console_command("worldedit perf").await;
    harness.run_console_command("butcher").await;

    // 2. Player-dispatched double-slash selection & region analysis commands
    harness.run_player_command(builder_uuid, "/wand").await;
    harness
        .run_player_command(builder_uuid, "/pos1 0,64,0")
        .await;
    harness
        .run_player_command(builder_uuid, "/pos2 8,70,8")
        .await;
    harness.run_player_command(builder_uuid, "/size").await;
    harness
        .run_player_command(builder_uuid, "/expand 2 up")
        .await;
    harness
        .run_player_command(builder_uuid, "/contract 1 down")
        .await;
    harness
        .run_player_command(builder_uuid, "/count stone")
        .await;
    harness.run_player_command(builder_uuid, "/distr").await;
    harness.run_player_command(builder_uuid, "/sel").await;

    // 3. Simulate left and right block clicks for WorldEdit interaction listeners
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerInteract(PlayerInteractEvent {
                player_uuid: Some(ProtoUuid {
                    value: builder_uuid.to_string(),
                }),
                action: "LEFT_CLICK_BLOCK".to_string(),
                clicked_pos_x: 0,
                clicked_pos_y: 64,
                clicked_pos_z: 0,
                block: "minecraft:stone".to_string(),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerInteract(PlayerInteractEvent {
                player_uuid: Some(ProtoUuid {
                    value: builder_uuid.to_string(),
                }),
                action: "RIGHT_CLICK_BLOCK".to_string(),
                clicked_pos_x: 8,
                clicked_pos_y: 70,
                clicked_pos_z: 8,
                block: "minecraft:stone".to_string(),
            }),
        )
        .await;

    harness
        .simulate_player_quit(builder_uuid, "WE_Architect")
        .await;
}
