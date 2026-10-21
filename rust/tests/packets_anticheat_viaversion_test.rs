mod common;
mod plugins;

use common::harness::SharedTestServer;
use std::sync::OnceLock;

static SERVER: OnceLock<SharedTestServer> = OnceLock::new();

fn server() -> &'static SharedTestServer {
    SERVER.get_or_init(|| {
        SharedTestServer::boot(&[
            "PacketEvents.jar",
            "ProtocolLib.jar",
            "GrimAC.jar",
            "ViaVersion.jar",
        ])
    })
}

#[test]
fn test_packetevents() {
    server().run(plugins::packetevents::verify_packetevents_heavily);
}

#[test]
fn test_protocollib() {
    server().run(plugins::protocollib::verify_protocollib_heavily);
}

#[test]
fn test_grimac() {
    server().run(plugins::grimac::verify_grimac_heavily);
}

#[test]
fn test_viaversion() {
    server().run(plugins::viaversion::verify_viaversion_heavily);
}
