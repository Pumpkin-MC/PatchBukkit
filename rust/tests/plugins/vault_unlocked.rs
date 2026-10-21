use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{AsyncPlayerChatEvent, event::Data as EventData},
};
use uuid::Uuid;

pub async fn verify_vault_unlocked_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("VaultUnlocked.jar") {
        return;
    }

    let player_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(player_uuid, "VaultTrader", 1201)
        .await;

    // Inspect registered Economy, Permission, and Chat providers bridged through VaultUnlocked
    harness.run_console_command("vault-info").await;
    harness.run_console_command("vault-convert").await;

    let chat_res = harness
        .fire_event(
            "",
            EventData::AsyncPlayerChat(AsyncPlayerChatEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                message: "Testing VaultUnlocked permission & chat hooks".to_string(),
                format: "<%1$s> %2$s".to_string(),
            }),
        )
        .await;
    assert!(!chat_res.cancelled);

    harness
        .simulate_player_quit(player_uuid, "VaultTrader")
        .await;
}
