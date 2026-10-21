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
            "EssentialsX.jar",
            "TAB.jar",
            "GSit.jar",
        ])
    })
}

#[test]
fn test_essentialsx() {
    server().run(plugins::essentialsx::verify_essentialsx_heavily);
}

#[test]
fn test_tab() {
    server().run(plugins::tab::verify_tab_heavily);
}

#[test]
fn test_gsit() {
    server().run(plugins::gsit::verify_gsit_heavily);
}
