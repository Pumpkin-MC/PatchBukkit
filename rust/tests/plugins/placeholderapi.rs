use crate::common::harness::TestServerHarness;
use uuid::Uuid;

pub async fn verify_placeholderapi_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("PlaceholderAPI.jar") {
        return;
    }

    let player_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(player_uuid, "PapiTester", 1301)
        .await;

    harness.run_console_command("papi version").await;
    harness.run_console_command("papi list").await;
    harness
        .run_console_command("papi parse --null Server=%server_name%_Online=%server_online%")
        .await;
    harness
        .run_console_command("papi parse PapiTester Player=%player_name%_World=%player_world%")
        .await;
    harness.run_console_command("papi reload").await;
    harness
        .run_console_command("papi parse --null AfterReloadCheck")
        .await;

    harness
        .simulate_player_quit(player_uuid, "PapiTester")
        .await;
}
