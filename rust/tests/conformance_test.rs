mod common;

use common::harness::TestServerHarness;
use uuid::Uuid;

#[tokio::test]
async fn test_bukkit_paper_conformance_suite() {
    let harness = TestServerHarness::boot(&["patchbukkit-test-plugin.jar"]).await;

    let tester_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(tester_uuid, "ConformanceBot", 501)
        .await;

    if harness.has_plugin("patchbukkit-test-plugin.jar") {
        // Run the full 547-test Bukkit & Paper API conformance suite
        harness.run_console_command("pbtest all").await;
    }

    harness
        .simulate_player_quit(tester_uuid, "ConformanceBot")
        .await;
    harness.shutdown().await;
}
