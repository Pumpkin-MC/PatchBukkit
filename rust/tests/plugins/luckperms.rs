use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{AsyncPlayerChatEvent, event::Data as EventData},
};
use uuid::Uuid;

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
    harness.run_console_command("lp info").await;
    harness.run_console_command("lp creategroup vip").await;
    harness
        .run_console_command("lp creategroup moderator")
        .await;
    harness.run_console_command("lp creategroup admin").await;

    harness
        .run_console_command("lp group vip setweight 10")
        .await;
    harness
        .run_console_command("lp group moderator setweight 50")
        .await;
    harness
        .run_console_command("lp group admin setweight 100")
        .await;

    harness
        .run_console_command("lp group vip permission set essentials.fly true")
        .await;
    harness
        .run_console_command("lp group vip permission set essentials.hat true")
        .await;
    harness
        .run_console_command("lp group vip meta setprefix 10 &a[VIP] ")
        .await;

    harness
        .run_console_command("lp group moderator parent add vip")
        .await;
    harness
        .run_console_command("lp group moderator permission set worldguard.region.bypass.* true")
        .await;
    harness
        .run_console_command("lp group moderator meta setprefix 50 &9[Mod] ")
        .await;

    harness
        .run_console_command("lp group admin parent add moderator")
        .await;
    harness
        .run_console_command("lp group admin permission set patchbukkit.admin.* true")
        .await;
    harness
        .run_console_command("lp group admin meta setprefix 100 &c[Admin] ")
        .await;

    // 2. Create promotion track and promote/demote online user
    harness.run_console_command("lp createtrack staff").await;
    harness
        .run_console_command("lp track staff append vip")
        .await;
    harness
        .run_console_command("lp track staff append moderator")
        .await;
    harness
        .run_console_command("lp track staff append admin")
        .await;
    harness.run_console_command("lp track staff info").await;

    // 3. Assign user groups, permissions, and check effective permissions
    harness
        .run_console_command(&format!("lp user {alice_uuid} parent add vip"))
        .await;
    harness
        .run_console_command(&format!("lp user {alice_uuid} promote staff"))
        .await;
    harness
        .run_console_command(&format!(
            "lp user {alice_uuid} permission set patchbukkit.custom.perk true"
        ))
        .await;
    harness
        .run_console_command(&format!(
            "lp user {alice_uuid} permission check essentials.fly"
        ))
        .await;
    harness
        .run_console_command(&format!("lp user {alice_uuid} info"))
        .await;
    harness
        .run_console_command(&format!("lp user {alice_uuid} permission info"))
        .await;

    harness
        .run_console_command(&format!("lp user {bob_uuid} parent add vip"))
        .await;
    harness
        .run_console_command(&format!("lp user {bob_uuid} info"))
        .await;

    harness.run_console_command("lp listgroups").await;
    harness.run_console_command("lp listtracks").await;
    harness.run_console_command("lp group admin info").await;
    harness.run_console_command("lp tree").await;
    harness.run_console_command("lp networksync").await;

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
