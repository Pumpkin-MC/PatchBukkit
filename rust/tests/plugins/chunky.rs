use crate::common::harness::TestServerHarness;

pub async fn verify_chunky_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("Chunky.jar") {
        return;
    }

    harness.run_console_command("chunky").await;
    harness.run_console_command("chunky world world").await;
    harness.run_console_command("chunky shape square").await;
    harness.run_console_command("chunky center 0 0").await;
    harness.run_console_command("chunky radius 500").await;
    harness.run_console_command("chunky quiet 5").await;
    harness.run_console_command("chunky start").await;
    tokio::time::sleep(tokio::time::Duration::from_millis(500)).await;
    harness.run_console_command("chunky progress").await;
    harness.run_console_command("chunky pause").await;
    harness.run_console_command("chunky continue").await;
    harness.run_console_command("chunky cancel").await;
    harness.run_console_command("chunky confirm").await;
    harness.run_console_command("chunky reload").await;
}
