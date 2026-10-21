use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        PlayerAnimationEvent, PlayerItemHeldEvent, PlayerMoveEvent, event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_packetevents_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("PacketEvents.jar") {
        return;
    }

    let player_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(player_uuid, "PacketEventsUser", 4101)
        .await;

    harness.run_console_command("packetevents").await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerMove(PlayerMoveEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 1.5,
                to_y: 64.0,
                to_z: 1.5,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerAnimation(PlayerAnimationEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                animation_type: "ARM_SWING".to_string(),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerItemHeld(PlayerItemHeldEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                previous_slot: 0,
                new_slot: 4,
            }),
        )
        .await;

    harness
        .simulate_player_quit(player_uuid, "PacketEventsUser")
        .await;
}
