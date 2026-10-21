mod common;
mod plugins;

use common::harness::SharedTestServer;
use std::sync::OnceLock;

static SERVER: OnceLock<SharedTestServer> = OnceLock::new();

fn server() -> &'static SharedTestServer {
    SERVER.get_or_init(|| {
        SharedTestServer::boot(&["LuckPerms.jar", "VaultUnlocked.jar", "PlaceholderAPI.jar"])
    })
}

#[test]
fn test_luckperms() {
    server().run(plugins::luckperms::verify_luckperms_heavily);
}

#[test]
fn test_vault_unlocked() {
    server().run(plugins::vault_unlocked::verify_vault_unlocked_heavily);
}

#[test]
fn test_placeholderapi() {
    server().run(plugins::placeholderapi::verify_placeholderapi_heavily);
}
