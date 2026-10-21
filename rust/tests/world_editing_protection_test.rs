mod common;
mod plugins;

use common::harness::SharedTestServer;
use std::sync::OnceLock;

static SERVER: OnceLock<SharedTestServer> = OnceLock::new();

fn server() -> &'static SharedTestServer {
    SERVER.get_or_init(|| SharedTestServer::boot(&["WorldEdit.jar", "WorldGuard.jar"]))
}

#[test]
fn test_worldedit() {
    server().run(plugins::worldedit::verify_worldedit_heavily);
}

#[test]
fn test_worldguard() {
    server().run(plugins::worldguard::verify_worldguard_heavily);
}
