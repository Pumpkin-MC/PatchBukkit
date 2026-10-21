mod common;
mod plugins;

use common::harness::SharedTestServer;
use std::sync::OnceLock;

static SERVER: OnceLock<SharedTestServer> = OnceLock::new();

fn server() -> &'static SharedTestServer {
    SERVER.get_or_init(|| {
        SharedTestServer::boot(&[
            "LuckPerms.jar",
            "VaultUnlocked.jar",
            "PlaceholderAPI.jar",
            "Veinminer.jar",
            "AuraSkills.jar",
        ])
    })
}

#[test]
fn test_veinminer() {
    server().run(plugins::veinminer::verify_veinminer_heavily);
}

#[test]
fn test_auraskills() {
    server().run(plugins::auraskills::verify_auraskills_heavily);
}
