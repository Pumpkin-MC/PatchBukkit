use crate::common::harness::TestServerHarness;
use uuid::Uuid;

pub async fn verify_viaversion_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("ViaVersion.jar") {
        return;
    }

    let player_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(player_uuid, "ViaClientPlayer", 4401)
        .await;

    harness.run_console_command("viaversion version").await;
    harness.run_console_command("viaversion list").await;
    harness.run_console_command("viaversion pp").await;
    harness.run_console_command("viaversion autoteam").await;
    harness.run_console_command("viaversion dontbugme").await;

    harness
        .simulate_player_quit(player_uuid, "ViaClientPlayer")
        .await;
}
