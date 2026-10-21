use crate::common::harness::TestServerHarness;

pub async fn verify_chunky_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("Chunky.jar") {
        return;
    }

    harness.run_console_command("chunky").await;
    harness.run_console_command("chunky world world").await;
    harness.run_console_command("chunky shape square").await;
    harness.run_console_command("chunky shape circle").await;
    harness.run_console_command("chunky center 0 0").await;
    harness.run_console_command("chunky radius 32").await;
    harness
        .run_console_command("chunky pattern concentric")
        .await;
    harness.run_console_command("chunky quiet 5").await;
    harness.run_console_command("chunky progress").await;
    harness.run_console_command("chunky reload").await;
}
