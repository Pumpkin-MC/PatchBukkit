use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{PlayerRespawnEvent, PlayerTeleportEvent, event::Data as EventData},
};
use uuid::Uuid;

pub async fn verify_multiverse_core_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("Multiverse-Core.jar") {
        return;
    }

    let explorer_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(explorer_uuid, "MV_Explorer", 2401)
        .await;

    harness.run_console_command("mv version").await;
    harness.run_console_command("mv list").await;
    harness.run_console_command("mv info world").await;
    harness.run_console_command("mv who").await;

    // Exercise teleport and respawn listeners monitored by Multiverse-Core
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerTeleport(PlayerTeleportEvent {
                player_uuid: Some(ProtoUuid {
                    value: explorer_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 128.0,
                to_y: 72.0,
                to_z: -64.0,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerRespawn(PlayerRespawnEvent {
                player_uuid: Some(ProtoUuid {
                    value: explorer_uuid.to_string(),
                }),
                previous_world_uuid: None,
                respawned_world_uuid: None,
                pos_x: 0.0,
                pos_y: 64.0,
                pos_z: 0.0,
                yaw: 0.0,
                pitch: 0.0,
                alive: true,
            }),
        )
        .await;

    harness
        .simulate_player_quit(explorer_uuid, "MV_Explorer")
        .await;
}
