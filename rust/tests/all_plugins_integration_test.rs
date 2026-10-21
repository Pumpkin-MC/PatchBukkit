mod common;
mod plugins;

use common::harness::TestServerHarness;
use uuid::Uuid;

#[tokio::test]
async fn test_all_17_plugins_loaded_together() {
    let harness = TestServerHarness::boot(&[
        "PacketEvents.jar",
        "ProtocolLib.jar",
        "GrimAC.jar",
        "Veinminer.jar",
        "LuckPerms.jar",
        "VaultUnlocked.jar",
        "PlaceholderAPI.jar",
        "EssentialsX.jar",
        "WorldEdit.jar",
        "WorldGuard.jar",
        "Chunky.jar",
        "ViaVersion.jar",
        "TAB.jar",
        "Multiverse-Core.jar",
        "GSit.jar",
        "AuraSkills.jar",
        "patchbukkit-test-plugin.jar",
    ])
    .await;

    // Exercise each of the 16 popular plugins and the conformance suite on the same combined server
    plugins::luckperms::verify_luckperms_heavily(&harness).await;
    plugins::vault_unlocked::verify_vault_unlocked_heavily(&harness).await;
    plugins::placeholderapi::verify_placeholderapi_heavily(&harness).await;
    plugins::worldedit::verify_worldedit_heavily(&harness).await;
    plugins::worldguard::verify_worldguard_heavily(&harness).await;
    plugins::chunky::verify_chunky_heavily(&harness).await;
    plugins::multiverse_core::verify_multiverse_core_heavily(&harness).await;
    plugins::essentialsx::verify_essentialsx_heavily(&harness).await;
    plugins::tab::verify_tab_heavily(&harness).await;
    plugins::gsit::verify_gsit_heavily(&harness).await;
    plugins::veinminer::verify_veinminer_heavily(&harness).await;
    plugins::auraskills::verify_auraskills_heavily(&harness).await;
    plugins::packetevents::verify_packetevents_heavily(&harness).await;
    plugins::protocollib::verify_protocollib_heavily(&harness).await;
    plugins::grimac::verify_grimac_heavily(&harness).await;
    plugins::viaversion::verify_viaversion_heavily(&harness).await;

    if harness.has_plugin("patchbukkit-test-plugin.jar") {
        let tester_uuid = Uuid::new_v4();
        harness
            .simulate_player_join(tester_uuid, "FullSuiteTester", 9001)
            .await;
        harness.run_console_command("pbtest all").await;
        harness
            .simulate_player_quit(tester_uuid, "FullSuiteTester")
            .await;
    }

    harness.shutdown().await;
}
