use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        BlockBreakEvent, BlockPlaceEvent, EntityDamageByEntityEvent, PlayerBucketEmptyEvent,
        PlayerDropItemEvent, PlayerInteractEvent, PlayerMoveEvent, event::Data as EventData,
    },
};
use uuid::Uuid;

pub async fn verify_worldguard_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("WorldGuard.jar") {
        return;
    }

    let owner_uuid = Uuid::new_v4();
    let visitor_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(owner_uuid, "WG_Owner", 2201)
        .await;
    harness
        .simulate_player_join(visitor_uuid, "WG_Visitor", 2202)
        .await;

    // 1. Diagnostics and region creation/configuration
    harness.run_console_command("wg version").await;
    harness.run_console_command("wg running").await;
    harness.run_console_command("wg flushstates").await;
    harness.run_console_command("rg list -w world").await;

    harness
        .run_console_command("rg define -w world spawn_hub -g")
        .await;
    harness
        .run_console_command("rg flag -w world spawn_hub pvp deny")
        .await;
    harness
        .run_console_command("rg flag -w world spawn_hub passthrough allow")
        .await;
    harness
        .run_console_command("rg flag -w world spawn_hub mob-spawning deny")
        .await;
    harness
        .run_console_command("rg setpriority -w world spawn_hub 10")
        .await;
    harness
        .run_console_command("rg addowner -w world spawn_hub WG_Owner")
        .await;
    harness
        .run_console_command("rg addmember -w world spawn_hub WG_Visitor")
        .await;
    harness
        .run_console_command("rg info -w world spawn_hub")
        .await;
    harness.run_console_command("rg save -w world").await;

    // 2. Exercise region protection listeners (Move, Interact, Break, Place, Bucket, Drop, PvP Damage)
    let _ = harness
        .fire_event(
            "",
            EventData::PlayerMove(PlayerMoveEvent {
                player_uuid: Some(ProtoUuid {
                    value: visitor_uuid.to_string(),
                }),
                from_x: 0.0,
                from_y: 64.0,
                from_z: 0.0,
                to_x: 2.0,
                to_y: 64.0,
                to_z: 2.0,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerInteract(PlayerInteractEvent {
                player_uuid: Some(ProtoUuid {
                    value: visitor_uuid.to_string(),
                }),
                action: "RIGHT_CLICK_BLOCK".to_string(),
                clicked_pos_x: 2,
                clicked_pos_y: 64,
                clicked_pos_z: 2,
                block: "minecraft:chest".to_string(),
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::BlockBreak(BlockBreakEvent {
                player_uuid: Some(ProtoUuid {
                    value: visitor_uuid.to_string(),
                }),
                block: "minecraft:stone".to_string(),
                block_x: 2,
                block_y: 64,
                block_z: 2,
                exp: 0,
                drop: true,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::BlockPlace(BlockPlaceEvent {
                player_uuid: Some(ProtoUuid {
                    value: visitor_uuid.to_string(),
                }),
                block_placed: "minecraft:cobblestone".to_string(),
                block_placed_against: "minecraft:stone".to_string(),
                block_x: 2,
                block_y: 65,
                block_z: 2,
                can_build: true,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerBucketEmpty(PlayerBucketEmptyEvent {
                player_uuid: Some(ProtoUuid {
                    value: visitor_uuid.to_string(),
                }),
                bucket: "minecraft:lava_bucket".to_string(),
                block_x: 2,
                block_y: 64,
                block_z: 2,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::PlayerDropItem(PlayerDropItemEvent {
                player_uuid: Some(ProtoUuid {
                    value: visitor_uuid.to_string(),
                }),
                item_name: "minecraft:dirt".to_string(),
                count: 1,
            }),
        )
        .await;

    let _ = harness
        .fire_event(
            "",
            EventData::EntityDamageByEntity(EntityDamageByEntityEvent {
                entity_id: 2202,
                damager_id: 2201,
                damage: 4.0,
                cause: "ENTITY_ATTACK".to_string(),
            }),
        )
        .await;

    harness.simulate_player_quit(owner_uuid, "WG_Owner").await;
    harness
        .simulate_player_quit(visitor_uuid, "WG_Visitor")
        .await;
}
