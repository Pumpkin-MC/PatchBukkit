use crate::common::harness::TestServerHarness;
use patchbukkit::proto::patchbukkit::{
    common::Uuid as ProtoUuid,
    events::{
        BlockBreakEvent, PlayerToggleSneakEvent, ServerTickStartEvent, event::Data as EventData,
    },
};
use pumpkin::world::BlockFlags;
use pumpkin_data::Block;
use pumpkin_util::math::position::BlockPos;
use uuid::Uuid;

pub async fn verify_veinminer_heavily(harness: &TestServerHarness) {
    if !harness.has_plugin("Veinminer.jar") {
        return;
    }

    let miner_uuid = Uuid::new_v4();
    harness
        .simulate_player_join(miner_uuid, "VeinMinerPro", 3401)
        .await;

    // Give the player a diamond pickaxe so Veinminer's tool & drop checks pass
    harness
        .run_console_command("give VeinMinerPro minecraft:diamond_pickaxe 1")
        .await;

    let world = harness
        .pumpkin_server
        .server
        .worlds
        .load()
        .first()
        .cloned()
        .expect("default world must exist");

    // Place a 4-block connected vein of coal_ore at (10..=11, 64..=65, 10)
    let vein_positions = [
        BlockPos::new(10, 64, 10),
        BlockPos::new(11, 64, 10),
        BlockPos::new(10, 65, 10),
        BlockPos::new(11, 65, 10),
    ];
    for pos in &vein_positions {
        let _ = world.get_block_state_id_async(pos).await;
        world.set_block_state(
            pos,
            Block::COAL_ORE.default_state.id,
            BlockFlags::NOTIFY_ALL,
        );
        assert_eq!(
            world.get_block_state(pos).id,
            Block::COAL_ORE.default_state.id,
            "Coal ore must be placed at {pos:?} before mining"
        );
    }

    // 1. Enable sneak mode for vein-mining activation
    let sneak_on = harness
        .fire_event(
            "",
            EventData::PlayerToggleSneak(PlayerToggleSneakEvent {
                player_uuid: Some(ProtoUuid {
                    value: miner_uuid.to_string(),
                }),
                is_sneaking: true,
            }),
        )
        .await;
    assert!(!sneak_on.cancelled);

    // 2. Break the first ore at (10, 64, 10) and tick the scheduler so Veinminer breaks the connected vein
    let vein_break = harness
        .fire_event(
            "",
            EventData::BlockBreak(BlockBreakEvent {
                player_uuid: Some(ProtoUuid {
                    value: miner_uuid.to_string(),
                }),
                block: "minecraft:coal_ore".to_string(),
                block_x: 10,
                block_y: 64,
                block_z: 10,
                exp: 2,
                drop: true,
            }),
        )
        .await;
    assert!(
        !vein_break.cancelled,
        "Veinminer ore break for coal_ore vein should not be cancelled"
    );

    // Tick the Paper scheduler so Veinminer's scheduled 0-tick break tasks execute
    harness
        .fire_event(
            "",
            EventData::ServerTickStart(ServerTickStartEvent { tick: 1 }),
        )
        .await;

    // Verify all connected ores in the vein were destroyed by Veinminer
    for connected_pos in &vein_positions[1..] {
        assert_eq!(
            world.get_block_state(connected_pos).id,
            Block::AIR.default_state.id,
            "Connected ore at {connected_pos:?} should have been destroyed by Veinminer"
        );
    }

    // 2. Break 8 different ore types while sneaking
    for (idx, ore) in [
        "minecraft:coal_ore",
        "minecraft:deepslate_coal_ore",
        "minecraft:iron_ore",
        "minecraft:copper_ore",
        "minecraft:gold_ore",
        "minecraft:redstone_ore",
        "minecraft:lapis_ore",
        "minecraft:diamond_ore",
        "minecraft:emerald_ore",
    ]
    .iter()
    .enumerate()
    {
        let break_res = harness
            .fire_event(
                "",
                EventData::BlockBreak(BlockBreakEvent {
                    player_uuid: Some(ProtoUuid {
                        value: miner_uuid.to_string(),
                    }),
                    block: (*ore).to_string(),
                    block_x: idx as i32,
                    block_y: 64,
                    block_z: 0,
                    exp: 3,
                    drop: true,
                }),
            )
            .await;
        assert!(
            !break_res.cancelled,
            "Veinminer ore break for {ore} should not be cancelled"
        );
    }

    // 3. Disable sneak and break regular stone
    let sneak_off = harness
        .fire_event(
            "",
            EventData::PlayerToggleSneak(PlayerToggleSneakEvent {
                player_uuid: Some(ProtoUuid {
                    value: miner_uuid.to_string(),
                }),
                is_sneaking: false,
            }),
        )
        .await;
    assert!(!sneak_off.cancelled);

    let regular_break = harness
        .fire_event(
            "",
            EventData::BlockBreak(BlockBreakEvent {
                player_uuid: Some(ProtoUuid {
                    value: miner_uuid.to_string(),
                }),
                block: "minecraft:stone".to_string(),
                block_x: 20,
                block_y: 64,
                block_z: 0,
                exp: 0,
                drop: true,
            }),
        )
        .await;
    assert!(!regular_break.cancelled);

    harness
        .simulate_player_quit(miner_uuid, "VeinMinerPro")
        .await;
}
