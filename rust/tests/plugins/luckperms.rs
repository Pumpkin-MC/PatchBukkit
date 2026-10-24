use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{AsyncPlayerChatEvent, event::Data as EventData},
};
use std::time::Duration;
use tokio::time::sleep;
use uuid::Uuid;

async fn run_lp_cmd(harness: &TestServerHarness, cmd: &str) {
    harness.run_console_command(cmd).await;
    sleep(Duration::from_millis(60)).await;
}

pub async fn verify_luckperms_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("LuckPerms.jar") {
        return;
    }

    let alice_uuid = Uuid::new_v4();
    let bob_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(alice_uuid, "LP_Alice", 1101)
        .await;
    harness.simulate_player_join(bob_uuid, "LP_Bob", 1102).await;

    // 1. Create groups, set weights, prefixes, and build a 3-tier inheritance tree
    run_lp_cmd(harness, "lp info").await;
    run_lp_cmd(harness, "lp creategroup vip").await;
    run_lp_cmd(harness, "lp creategroup moderator").await;
    run_lp_cmd(harness, "lp creategroup admin").await;

    run_lp_cmd(harness, "lp group vip setweight 10").await;
    run_lp_cmd(harness, "lp group moderator setweight 50").await;
    run_lp_cmd(harness, "lp group admin setweight 100").await;

    run_lp_cmd(harness, "lp group vip permission set essentials.fly true").await;
    run_lp_cmd(harness, "lp group vip permission set essentials.hat true").await;
    run_lp_cmd(harness, "lp group vip meta setprefix 10 &a[VIP] ").await;

    run_lp_cmd(harness, "lp group moderator parent add vip").await;
    run_lp_cmd(
        harness,
        "lp group moderator permission set worldguard.region.bypass.* true",
    )
    .await;
    run_lp_cmd(harness, "lp group moderator meta setprefix 50 &9[Mod] ").await;

    run_lp_cmd(harness, "lp group admin parent add moderator").await;
    run_lp_cmd(
        harness,
        "lp group admin permission set patchbukkit.admin.* true",
    )
    .await;
    run_lp_cmd(harness, "lp group admin meta setprefix 100 &c[Admin] ").await;

    // 2. Create promotion track and promote/demote online user
    run_lp_cmd(harness, "lp createtrack staff").await;
    run_lp_cmd(harness, "lp track staff append vip").await;
    run_lp_cmd(harness, "lp track staff append moderator").await;
    run_lp_cmd(harness, "lp track staff append admin").await;
    run_lp_cmd(harness, "lp track staff info").await;

    // 3. Assign user groups, permissions, and check effective permissions
    run_lp_cmd(harness, &format!("lp user {alice_uuid} parent add vip")).await;
    run_lp_cmd(harness, &format!("lp user {alice_uuid} promote staff")).await;
    run_lp_cmd(
        harness,
        &format!("lp user {alice_uuid} permission set patchbukkit.custom.perk true"),
    )
    .await;
    run_lp_cmd(
        harness,
        &format!("lp user {alice_uuid} permission settemp patchbukkit.temp.perk true 1d"),
    )
    .await;
    run_lp_cmd(
        harness,
        &format!("lp user {alice_uuid} permission set patchbukkit.world.perk true world=world"),
    )
    .await;
    run_lp_cmd(
        harness,
        &format!("lp user {alice_uuid} permission check essentials.fly"),
    )
    .await;
    run_lp_cmd(
        harness,
        &format!("lp user {alice_uuid} permission check patchbukkit.custom.perk"),
    )
    .await;
    run_lp_cmd(
        harness,
        &format!("lp user {alice_uuid} permission check patchbukkit.world.perk"),
    )
    .await;
    run_lp_cmd(harness, &format!("lp user {alice_uuid} info")).await;
    run_lp_cmd(harness, &format!("lp user {alice_uuid} permission info")).await;

    // Test demote and re-promote on track
    run_lp_cmd(harness, &format!("lp user {alice_uuid} demote staff")).await;
    run_lp_cmd(harness, &format!("lp user {alice_uuid} promote staff")).await;

    run_lp_cmd(harness, &format!("lp user {bob_uuid} parent add vip")).await;
    run_lp_cmd(
        harness,
        &format!("lp user {bob_uuid} permission set essentials.fly false"),
    )
    .await;
    run_lp_cmd(
        harness,
        &format!("lp user {bob_uuid} permission check essentials.fly"),
    )
    .await;
    run_lp_cmd(harness, &format!("lp user {bob_uuid} info")).await;

    run_lp_cmd(harness, "lp listgroups").await;
    run_lp_cmd(harness, "lp listtracks").await;
    run_lp_cmd(harness, "lp group admin info").await;
    run_lp_cmd(harness, "lp tree").await;
    run_lp_cmd(harness, "lp networksync").await;

    // Run player commands through Alice and Bob
    harness
        .run_player_command(alice_uuid, "lp user LP_Alice info")
        .await;
    sleep(Duration::from_millis(60)).await;
    harness
        .run_player_command(bob_uuid, "lp user LP_Bob info")
        .await;
    sleep(Duration::from_millis(60)).await;

    // Give LP time to finish background sync tasks
    sleep(Duration::from_millis(200)).await;

    let chat_res = harness
        .fire_event(
            "",
            EventData::AsyncPlayerChat(AsyncPlayerChatEvent {
                player_uuid: Some(ProtoUuid {
                    value: alice_uuid.to_string(),
                }),
                message: "LuckPerms permission & prefix check!".to_string(),
                format: "<%1$s> %2$s".to_string(),
            }),
        )
        .await;
    assert!(!chat_res.cancelled);

    harness.simulate_player_quit(alice_uuid, "LP_Alice").await;
    harness.simulate_player_quit(bob_uuid, "LP_Bob").await;
}
