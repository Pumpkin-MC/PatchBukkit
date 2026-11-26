mod common;

use common::harness::TestServerHarness;
use std::fs;
use uuid::Uuid;

#[tokio::test]
async fn test_bukkit_paper_conformance_suite() {
    let harness = TestServerHarness::boot(&["patchbukkit-test-plugin.jar"]).await;

    assert!(
        harness.has_plugin("patchbukkit-test-plugin.jar"),
        "patchbukkit-test-plugin.jar must be built and copied into the test server plugins dir"
    );

    let tester_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(tester_uuid, "ConformanceBot", 501)
        .await;

    tokio::time::sleep(std::time::Duration::from_millis(1000)).await;

    // Run the full Bukkit & Paper API conformance suite
    harness.run_console_command("pbtest all").await;

    let results_path = harness
        .plugins_dir
        .join("PatchBukkitTest")
        .join("pbtest-results.txt");
    assert!(
        results_path.exists(),
        "pbtest results file must be generated at {results_path:?}"
    );
    let report = fs::read_to_string(&results_path).expect("Failed to read pbtest-results.txt");
    println!("=== PBTEST REPORT ===\n{report}");

    let header = report.lines().next().unwrap_or_default();
    let total: usize = header
        .split_whitespace()
        .find_map(|tok| tok.strip_prefix("total=").and_then(|v| v.parse().ok()))
        .expect("Missing total= in pbtest report");
    let failed: usize = header
        .split_whitespace()
        .find_map(|tok| tok.strip_prefix("failed=").and_then(|v| v.parse().ok()))
        .expect("Missing failed= in pbtest report");

    assert!(
        total > 0,
        "Conformance suite must execute at least one test"
    );
    assert_eq!(
        failed, 0,
        "Conformance suite had {failed} failure(s):\n{report}"
    );

    harness
        .simulate_player_quit(tester_uuid, "ConformanceBot")
        .await;
    harness.shutdown().await;
}
