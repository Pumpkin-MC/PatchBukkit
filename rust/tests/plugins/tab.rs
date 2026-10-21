use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{PlayerMoveEvent, event::Data as EventData},
};
use uuid::Uuid;

pub async fn verify_tab_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("TAB.jar") {
        return;
    }

    let p1_uuid = Uuid::new_v4();
    let p2_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(p1_uuid, "TabPlayerOne", 3201)
        .await;
    harness
        .simulate_player_join(p2_uuid, "TabPlayerTwo", 3202)
        .await;

    harness.run_console_command("tab").await;
    harness.run_console_command("tab cpu").await;
    harness.run_console_command("tab debug").await;
    harness.run_console_command("tab debug TabPlayerOne").await;
    harness
        .run_console_command("tab group default tabprefix &7[Member] ")
        .await;
    harness
        .run_console_command("tab player TabPlayerOne tabprefix &b[MVP] ")
        .await;
    harness
        .run_console_command("tab player TabPlayerOne tabsuffix &e*")
        .await;
    harness.run_console_command("tab reload").await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerMove(PlayerMoveEvent {
                player_uuid: Some(ProtoUuid {
                    value: p1_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 5.0,
                to_y: 64.0,
                to_z: 5.0,
            }),
        )
        .await;

    harness.simulate_player_quit(p1_uuid, "TabPlayerOne").await;
    harness.simulate_player_quit(p2_uuid, "TabPlayerTwo").await;
}
