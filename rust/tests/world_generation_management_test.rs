mod common;
mod plugins;

use common::harness::SharedTestServer;
use std::sync::OnceLock;

static SERVER: OnceLock<SharedTestServer> = OnceLock::new();

fn server() -> &'static SharedTestServer {
    SERVER.get_or_init(|| SharedTestServer::boot(&["Chunky.jar", "Multiverse-Core.jar"]))
}

#[test]
fn test_chunky() {
    server().run(plugins::chunky::verify_chunky_heavily);
}

#[test]
fn test_multiverse_core() {
    server().run(plugins::multiverse_core::verify_multiverse_core_heavily);
}
