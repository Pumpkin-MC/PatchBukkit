use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        AsyncPlayerChatEvent, BlockPlaceEvent, PlayerInteractEvent, PlayerMoveEvent,
        PlayerRespawnEvent, PlayerTeleportEvent, event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_essentialsx_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("EssentialsX.jar") {
        return;
    }

    let p1_uuid = Uuid::new_v4();
    let p2_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(p1_uuid, "EssPlayerOne", 3101)
        .await;
    harness
        .simulate_player_join(p2_uuid, "EssPlayerTwo", 3102)
        .await;

    // 1. Server status, info, and broadcast commands
    harness.run_console_command("essentials version").await;
    harness.run_console_command("gc").await;
    harness.run_console_command("list").await;
    harness.run_console_command("rules").await;
    harness.run_console_command("motd").await;
    harness
        .run_console_command("broadcast Welcome to PatchBukkit EssentialsX test!")
        .await;

    // 2. Economy commands (balance, eco give/take/set, baltop)
    harness
        .run_console_command("eco give EssPlayerOne 500")
        .await;
    harness
        .run_console_command("eco take EssPlayerOne 100")
        .await;
    harness
        .run_console_command("eco set EssPlayerTwo 1000")
        .await;
    harness.run_console_command("balance EssPlayerOne").await;
    harness.run_console_command("baltop").await;

    // 3. Warps, Kits, and Seen
    harness.run_console_command("warps").await;
    harness.run_console_command("kit").await;
    harness.run_console_command("seen EssPlayerOne").await;

    // 4. Player-dispatched Essentials commands
    harness.run_player_command(p1_uuid, "afk").await;
    harness.run_player_command(p1_uuid, "balance").await;
    harness.run_player_command(p1_uuid, "rules").await;

    // 5. Gameplay event listeners (Move/AFK cancel, Teleport/back tracking, Respawn, Interact, Place, Chat)
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerMove(PlayerMoveEvent {
                player_uuid: Some(ProtoUuid {
                    value: p1_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 4.0,
                to_y: 64.0,
                to_z: 4.0,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerTeleport(PlayerTeleportEvent {
                player_uuid: Some(ProtoUuid {
                    value: p2_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 100.0,
                to_y: 70.0,
                to_z: -50.0,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerRespawn(PlayerRespawnEvent {
                player_uuid: Some(ProtoUuid {
                    value: p2_uuid.to_string(),
                }),
                previous_world_uuid: None,
                respawned_world_uuid: None,
                pos_x: 0.0,
                pos_y: 64.0,
                pos_z: 0.0,
                yaw: 0.0,
                pitch: 0.0,
                alive: true,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::BlockPlace(BlockPlaceEvent {
                player_uuid: Some(ProtoUuid {
                    value: p1_uuid.to_string(),
                }),
                block_placed: "minecraft:crafting_table".to_string(),
                block_placed_against: "minecraft:stone".to_string(),
                block_x: 3,
                block_y: 64,
                block_z: 3,
                can_build: true,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerInteract(PlayerInteractEvent {
                player_uuid: Some(ProtoUuid {
                    value: p1_uuid.to_string(),
                }),
                action: "RIGHT_CLICK_BLOCK".to_string(),
                clicked_pos_x: 3,
                clicked_pos_y: 64,
                clicked_pos_z: 3,
                block: "minecraft:crafting_table".to_string(),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::AsyncPlayerChat(AsyncPlayerChatEvent {
                player_uuid: Some(ProtoUuid {
                    value: p1_uuid.to_string(),
                }),
                message: "Testing EssentialsX chat & economy!".to_string(),
                format: "<%1$s> %2$s".to_string(),
            }),
        )
        .await;

    harness.simulate_player_quit(p1_uuid, "EssPlayerOne").await;
    harness.simulate_player_quit(p2_uuid, "EssPlayerTwo").await;
}
