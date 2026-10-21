use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        AsyncPlayerChatEvent, InventoryClickEvent, InventoryCloseEvent, InventoryOpenEvent,
        PlayerAnimationEvent, event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_protocollib_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("ProtocolLib.jar") {
        return;
    }

    let player_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(player_uuid, "ProtocolTester", 4201)
        .await;

    harness.run_console_command("protocol version").await;
    harness.run_console_command("protocol listeners").await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerAnimation(PlayerAnimationEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                animation_type: "ARM_SWING".to_string(),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::InventoryOpen(InventoryOpenEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::InventoryClick(InventoryClickEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                click_type: "Left".to_string(),
                slot: 1,
                raw_slot: 1,
                clicked_item_name: "minecraft:iron_sword".to_string(),
                clicked_item_count: 1,
                cursor_name: String::new(),
                cursor_count: 0,
                hotbar_button: -1,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::InventoryClose(InventoryCloseEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::AsyncPlayerChat(AsyncPlayerChatEvent {
                player_uuid: Some(ProtoUuid {
                    value: player_uuid.to_string(),
                }),
                message: "ProtocolLib channel pipeline check".to_string(),
                format: "<%1$s> %2$s".to_string(),
            }),
        )
        .await;

    harness
        .simulate_player_quit(player_uuid, "ProtocolTester")
        .await;
}
