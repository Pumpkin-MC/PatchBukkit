#![allow(clippy::all)]
use std::any::TypeId;
use std::collections::HashSet;
use std::sync::{Arc, LazyLock, Mutex};

use pumpkin::plugin::{Context, EventPriority, Payload};
use tokio::sync::mpsc;

use crate::events::handler::{PatchBukkitEvent, PatchBukkitEventHandler};
use crate::java::jvm::commands::JvmCommand;
use crate::proto::patchbukkit::events::{
    CallEventRequest, CallEventResponse, FireEventResponse, RegisterEventRequest,
};

/// Deduplicates Pumpkin event handler registrations by Pumpkin's concrete event `TypeId`.
/// Because Java's `PatchBukkitEventFactory` dispatches each fired event across all enabled
/// plugins in Bukkit `EventPriority` order, each Pumpkin event type only needs to be
/// registered once globally.
static REGISTERED_PUMPKIN_EVENTS: LazyLock<Mutex<HashSet<TypeId>>> =
    LazyLock::new(|| Mutex::new(HashSet::new()));

pub fn ffi_native_bridge_call_event_impl(request: CallEventRequest) -> Option<CallEventResponse> {
    Some(CallEventResponse {
        response: Some(FireEventResponse {
            cancelled: false,
            data: request.event,
        }),
    })
}

pub(crate) fn register_single_event<E>(
    plugin_context: &Arc<Context>,
    command_tx: &mpsc::Sender<JvmCommand>,
    priority: EventPriority,
    blocking: bool,
) where
    E: Payload + PatchBukkitEvent + Send + Sync + 'static,
{
    {
        let mut registered = REGISTERED_PUMPKIN_EVENTS.lock().unwrap();
        if !registered.insert(TypeId::of::<E>()) {
            return;
        }
    }

    plugin_context.register_event::<E, PatchBukkitEventHandler<E>>(
        Arc::new(PatchBukkitEventHandler::new(
            String::new(),
            command_tx.clone(),
        )),
        priority,
        blocking,
    );
}

macro_rules! register_bukkit_events {
    (
        $event_type:expr,
        $ctx:expr,
        $tx:expr,
        $prio:expr,
        $blocking:expr;
        $( $($pattern:literal)|+ => $pumpkin_event:ty ),* $(,)?
    ) => {
        match $event_type {
            $(
                $($pattern)|+ => {
                    register_single_event::<$pumpkin_event>($ctx, $tx, $prio, $blocking);
                }
            )*
            "org.bukkit.event.server.RemoteServerCommandEvent" => {
                // RCON is not supported in modern Pumpkin
            }
            "org.bukkit.event.player.PlayerEvent"
            | "org.bukkit.event.entity.EntityEvent"
            | "org.bukkit.event.block.BlockEvent"
            | "org.bukkit.event.inventory.InventoryEvent"
            | "org.bukkit.event.server.ServerEvent"
            | "org.bukkit.event.world.WorldEvent"
            | "org.bukkit.event.weather.WeatherEvent"
            | "org.bukkit.event.vehicle.VehicleEvent"
            | "org.bukkit.event.hanging.HangingEvent"
            | "org.bukkit.event.raid.RaidEvent" => {
                tracing::info!("Registered Bukkit generic event category '{}'", $event_type);
            }
            name if name.starts_with("io.papermc.paper.event.")
                || name.starts_with("com.destroystokyo.paper.event.")
                || name.starts_with("org.spigotmc.event.") =>
            {
                tracing::info!("Registered Paper/Spigot event listener '{}'", $event_type);
            }
            _ => {
                tracing::warn!("Unknown or unhandled Bukkit event type '{}'", $event_type);
            }
        }
    };
}

pub fn ffi_native_bridge_register_event_impl(request: RegisterEventRequest) -> Option<()> {
    let context = crate::java::native_callbacks::CALLBACK_CONTEXT.get()?;
    let plugin_context = &context.plugin_context;
    let command_tx = &context.command_tx;

    let pumpkin_priority = match request.priority {
        0 => EventPriority::Lowest,
        1 => EventPriority::Low,
        2 => EventPriority::Normal,
        3 => EventPriority::High,
        4 => EventPriority::Highest,
        _ => EventPriority::Normal,
    };

    register_bukkit_events!(
        request.event_type.as_str(),
        plugin_context,
        command_tx,
        pumpkin_priority,
        request.blocking;

        // World & Weather events
        "org.bukkit.event.world.AsyncStructureGenerateEvent" => pumpkin::plugin::world::async_structure_generate::AsyncStructureGenerateEvent,
        "org.bukkit.event.world.AsyncStructureSpawnEvent" => pumpkin::plugin::world::async_structure_spawn::AsyncStructureSpawnEvent,
        "org.bukkit.event.world.ChunkLoadEvent" => pumpkin::plugin::world::chunk_load::ChunkLoad,
        "org.bukkit.event.world.ChunkPopulateEvent" => pumpkin::plugin::world::chunk_populate::ChunkPopulateEvent,
        "org.bukkit.event.world.ChunkUnloadEvent" => pumpkin::plugin::world::chunk_save::ChunkSave,
        "org.bukkit.event.world.EntitiesLoadEvent" => pumpkin::plugin::world::entities_load::EntitiesLoadEvent,
        "org.bukkit.event.world.EntitiesUnloadEvent" => pumpkin::plugin::world::entities_unload::EntitiesUnloadEvent,
        "org.bukkit.event.world.GenericGameEvent" => pumpkin::plugin::world::generic_game::GenericGameEvent,
        "org.bukkit.event.weather.LightningStrikeEvent" => pumpkin::plugin::world::lightning_strike::LightningStrikeEvent,
        "org.bukkit.event.world.LootGenerateEvent" => pumpkin::plugin::world::loot_generate::LootGenerateEvent,
        "org.bukkit.event.world.PortalCreateEvent" => pumpkin::plugin::world::portal_create::PortalCreateEvent,
        "org.bukkit.event.world.SpawnChangeEvent" => pumpkin::plugin::world::spawn_change::SpawnChangeEvent,
        "org.bukkit.event.world.StructureGrowEvent" => pumpkin::plugin::world::structure_grow::StructureGrowEvent,
        "org.bukkit.event.world.TimeSkipEvent" => pumpkin::plugin::world::time_skip::TimeSkipEvent,
        "org.bukkit.event.weather.WeatherChangeEvent" => pumpkin::plugin::world::weather_change::WeatherChangeEvent,
        "org.bukkit.event.weather.ThunderChangeEvent" => pumpkin::plugin::world::weather_change::ThunderChangeEvent,
        "org.bukkit.event.world.WorldInitEvent" => pumpkin::plugin::world::world_init::WorldInitEvent,
        "org.bukkit.event.world.WorldLoadEvent" => pumpkin::plugin::world::world_load::WorldLoadEvent,
        "org.bukkit.event.world.WorldUnloadEvent" => pumpkin::plugin::world::world_load::WorldUnloadEvent,
        "org.bukkit.event.world.WorldSaveEvent" => pumpkin::plugin::world::world_save::WorldSaveEvent,

        // Server events
        "org.bukkit.event.server.ServerListPingEvent" => pumpkin::plugin::server::list_ping::ServerListPingEvent,
        "org.bukkit.event.server.MapInitializeEvent" => pumpkin::plugin::server::map_initialize::MapInitializeEvent,
        "org.bukkit.event.server.PluginDisableEvent" => pumpkin::plugin::server::plugin_disable::PluginDisableEvent,
        "org.bukkit.event.server.PluginEnableEvent" => pumpkin::plugin::server::plugin_enable::PluginEnableEvent,
        "org.bukkit.event.server.BroadcastMessageEvent" => pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent,
        "org.bukkit.event.server.ServerCommandEvent" => pumpkin::plugin::server::server_command::ServerCommandEvent,
        "org.bukkit.event.server.ServerLoadEvent" => pumpkin::plugin::server::server_load::ServerLoadEvent,
        "com.destroystokyo.paper.event.server.ServerTickEndEvent" => pumpkin::plugin::server::server_tick_end::ServerTickEndEvent,
        "com.destroystokyo.paper.event.server.ServerTickStartEvent" => pumpkin::plugin::server::server_tick_start::ServerTickStartEvent,
        "org.bukkit.event.server.ServiceRegisterEvent" => pumpkin::plugin::server::service_register::ServiceRegisterEvent,
        "org.bukkit.event.server.ServiceUnregisterEvent" => pumpkin::plugin::server::service_unregister::ServiceUnregisterEvent,
        "org.bukkit.event.server.TabCompleteEvent" => pumpkin::plugin::server::tab_complete::TabCompleteEvent,

        // Vehicle events
        "org.bukkit.event.vehicle.VehicleBlockCollisionEvent" => pumpkin::plugin::vehicle::vehicle_block_collision::VehicleBlockCollisionEvent,
        "org.bukkit.event.vehicle.VehicleCollisionEvent" => pumpkin::plugin::vehicle::vehicle_collision::VehicleCollisionEvent,
        "org.bukkit.event.vehicle.VehicleCreateEvent" => pumpkin::plugin::vehicle::vehicle_create::VehicleCreateEvent,
        "org.bukkit.event.vehicle.VehicleDamageEvent" => pumpkin::plugin::vehicle::vehicle_damage::VehicleDamageEvent,
        "org.bukkit.event.vehicle.VehicleDestroyEvent" => pumpkin::plugin::vehicle::vehicle_destroy::VehicleDestroyEvent,
        "org.bukkit.event.vehicle.VehicleEnterEvent" => pumpkin::plugin::vehicle::vehicle_enter::VehicleEnterEvent,
        "org.bukkit.event.vehicle.VehicleEntityCollisionEvent" => pumpkin::plugin::vehicle::vehicle_entity_collision::VehicleEntityCollisionEvent,
        "org.bukkit.event.vehicle.VehicleExitEvent" => pumpkin::plugin::vehicle::vehicle_exit::VehicleExitEvent,
        "org.bukkit.event.vehicle.VehicleMoveEvent" => pumpkin::plugin::vehicle::vehicle_move::VehicleMoveEvent,
        "org.bukkit.event.vehicle.VehicleUpdateEvent" => pumpkin::plugin::vehicle::vehicle_update::VehicleUpdateEvent,

        // Inventory events
        "org.bukkit.event.inventory.BrewEvent" => pumpkin::plugin::inventory::brew::BrewEvent,
        "org.bukkit.event.inventory.BrewingStandFuelEvent" => pumpkin::plugin::inventory::brewing_stand_fuel::BrewingStandFuelEvent,
        "org.bukkit.event.inventory.CraftItemEvent" => pumpkin::plugin::inventory::craft_item::CraftItemEvent,
        "org.bukkit.event.inventory.FurnaceBurnEvent" => pumpkin::plugin::inventory::furnace_burn::FurnaceBurnEvent,
        "org.bukkit.event.inventory.FurnaceExtractEvent" => pumpkin::plugin::inventory::furnace_extract::FurnaceExtractEvent,
        "org.bukkit.event.inventory.FurnaceSmeltEvent" => pumpkin::plugin::inventory::furnace_smelt::FurnaceSmeltEvent,
        "org.bukkit.event.inventory.FurnaceStartSmeltEvent" => pumpkin::plugin::inventory::furnace_start_smelt::FurnaceStartSmeltEvent,
        "org.bukkit.event.inventory.HopperInventorySearchEvent" => pumpkin::plugin::inventory::hopper_inventory_search::HopperInventorySearchEvent,
        "org.bukkit.event.inventory.InventoryCreativeEvent" => pumpkin::plugin::inventory::inventory_creative::InventoryCreativeEvent,
        "org.bukkit.event.inventory.InventoryDragEvent" => pumpkin::plugin::inventory::inventory_drag::InventoryDragEvent,
        "org.bukkit.event.inventory.InventoryInteractEvent" => pumpkin::plugin::inventory::inventory_interact::InventoryInteractEvent,
        "org.bukkit.event.inventory.InventoryMoveItemEvent" => pumpkin::plugin::inventory::inventory_move_item::InventoryMoveItemEvent,
        "org.bukkit.event.inventory.InventoryOpenEvent" => pumpkin::plugin::inventory::inventory_open::InventoryOpenEvent,
        "org.bukkit.event.inventory.InventoryPickupItemEvent" => pumpkin::plugin::inventory::inventory_pickup_item::InventoryPickupItemEvent,
        "org.bukkit.event.inventory.PrepareAnvilEvent" => pumpkin::plugin::inventory::prepare_anvil::PrepareAnvilEvent,
        "org.bukkit.event.inventory.PrepareGrindstoneEvent" => pumpkin::plugin::inventory::prepare_grindstone::PrepareGrindstoneEvent,
        "org.bukkit.event.inventory.PrepareInventoryResultEvent" => pumpkin::plugin::inventory::prepare_inventory_result::PrepareInventoryResultEvent,
        "org.bukkit.event.inventory.PrepareItemCraftEvent" => pumpkin::plugin::inventory::prepare_item_craft::PrepareItemCraftEvent,
        "org.bukkit.event.inventory.PrepareSmithingEvent" => pumpkin::plugin::inventory::prepare_smithing::PrepareSmithingEvent,
        "org.bukkit.event.inventory.SmithItemEvent" => pumpkin::plugin::inventory::smith_item::SmithItemEvent,
        "org.bukkit.event.inventory.TradeSelectEvent" => pumpkin::plugin::inventory::trade_select::TradeSelectEvent,
        "org.bukkit.event.inventory.InventoryCloseEvent" => pumpkin::plugin::player::inventory_close::InventoryCloseEvent,
        "org.bukkit.event.inventory.InventoryClickEvent" => pumpkin::plugin::player::inventory_interact::InventoryClickEvent,

        // Block events
        "org.bukkit.event.block.BellResonateEvent" => pumpkin::plugin::block::bell_resonate::BellResonateEvent,
        "org.bukkit.event.block.BellRingEvent" => pumpkin::plugin::block::bell_ring::BellRingEvent,
        "org.bukkit.event.block.BlockBreakEvent" => pumpkin::plugin::block::block_break::BlockBreakEvent,
        "org.bukkit.event.block.BlockBurnEvent" => pumpkin::plugin::block::block_burn::BlockBurnEvent,
        "org.bukkit.event.block.BlockCanBuildEvent" => pumpkin::plugin::block::block_can_build::BlockCanBuildEvent,
        "org.bukkit.event.block.BlockCookEvent" => pumpkin::plugin::block::block_cook::BlockCookEvent,
        "org.bukkit.event.block.BlockDamageEvent" => pumpkin::plugin::block::block_damage::BlockDamageEvent,
        "org.bukkit.event.block.BlockDamageAbortEvent" => pumpkin::plugin::block::block_damage_abort::BlockDamageAbortEvent,
        "org.bukkit.event.block.BlockDispenseEvent" => pumpkin::plugin::block::block_dispense::BlockDispenseEvent,
        "org.bukkit.event.block.BlockDispenseArmorEvent" => pumpkin::plugin::block::block_dispense_armor::BlockDispenseArmorEvent,
        "org.bukkit.event.block.BlockDispenseLootEvent" => pumpkin::plugin::block::block_dispense_loot::BlockDispenseLootEvent,
        "org.bukkit.event.block.BlockDropItemEvent" => pumpkin::plugin::block::block_drop_item::BlockDropItemEvent,
        "org.bukkit.event.block.BlockExpEvent" => pumpkin::plugin::block::block_exp::BlockExpEvent,
        "org.bukkit.event.block.BlockExplodeEvent" => pumpkin::plugin::block::block_explode::BlockExplodeEvent,
        "org.bukkit.event.block.BlockFadeEvent" => pumpkin::plugin::block::block_fade::BlockFadeEvent,
        "org.bukkit.event.block.BlockFertilizeEvent" => pumpkin::plugin::block::block_fertilize::BlockFertilizeEvent,
        "org.bukkit.event.block.BlockFormEvent" => pumpkin::plugin::block::block_form::BlockFormEvent,
        "org.bukkit.event.block.BlockFromToEvent" => pumpkin::plugin::block::block_from_to::BlockFromToEvent,
        "org.bukkit.event.block.BlockGrowEvent" => pumpkin::plugin::block::block_grow::BlockGrowEvent,
        "org.bukkit.event.block.BlockIgniteEvent" => pumpkin::plugin::block::block_ignite::BlockIgniteEvent,
        "org.bukkit.event.block.BlockMultiPlaceEvent" => pumpkin::plugin::block::block_multi_place::BlockMultiPlaceEvent,
        "org.bukkit.event.block.BlockPhysicsEvent" => pumpkin::plugin::block::block_physics::BlockPhysicsEvent,
        "org.bukkit.event.block.BlockPistonExtendEvent" => pumpkin::plugin::block::block_piston::BlockPistonExtendEvent,
        "org.bukkit.event.block.BlockPistonRetractEvent" => pumpkin::plugin::block::block_piston::BlockPistonRetractEvent,
        "org.bukkit.event.block.BlockPlaceEvent" => pumpkin::plugin::block::block_place::BlockPlaceEvent,
        "org.bukkit.event.block.BlockReceiveGameEvent" => pumpkin::plugin::block::block_receive_game::BlockReceiveGameEvent,
        "org.bukkit.event.block.BlockRedstoneEvent" => pumpkin::plugin::block::block_redstone::BlockRedstoneEvent,
        "org.bukkit.event.block.BlockShearEntityEvent" => pumpkin::plugin::block::block_shear_entity::BlockShearEntityEvent,
        "org.bukkit.event.block.BlockSpreadEvent" => pumpkin::plugin::block::block_spread::BlockSpreadEvent,
        "org.bukkit.event.block.BrewingStartEvent" => pumpkin::plugin::block::brewing_start::BrewingStartEvent,
        "org.bukkit.event.block.CampfireStartEvent" => pumpkin::plugin::block::campfire_start::CampfireStartEvent,
        "org.bukkit.event.block.CauldronLevelChangeEvent" => pumpkin::plugin::block::cauldron_level_change::CauldronLevelChangeEvent,
        "org.bukkit.event.block.CrafterCraftEvent" => pumpkin::plugin::block::crafter_craft::CrafterCraftEvent,
        "org.bukkit.event.block.EntityBlockFormEvent" => pumpkin::plugin::block::entity_block_form::EntityBlockFormEvent,
        "org.bukkit.event.block.FluidLevelChangeEvent" => pumpkin::plugin::block::fluid_level_change::FluidLevelChangeEvent,
        "org.bukkit.event.block.InventoryBlockStartEvent" => pumpkin::plugin::block::inventory_block_start::InventoryBlockStartEvent,
        "org.bukkit.event.block.LeavesDecayEvent" => pumpkin::plugin::block::leaves_decay::LeavesDecayEvent,
        "org.bukkit.event.block.MoistureChangeEvent" => pumpkin::plugin::block::moisture_change::MoistureChangeEvent,
        "org.bukkit.event.block.NotePlayEvent" => pumpkin::plugin::block::note_play::NotePlayEvent,
        "org.bukkit.event.block.SculkBloomEvent" => pumpkin::plugin::block::sculk_bloom::SculkBloomEvent,
        "org.bukkit.event.block.SignChangeEvent" => pumpkin::plugin::block::sign_change::SignChangeEvent,
        "org.bukkit.event.block.SpongeAbsorbEvent" => pumpkin::plugin::block::sponge_absorb::SpongeAbsorbEvent,
        "org.bukkit.event.block.TNTPrimeEvent" => pumpkin::plugin::block::tnt_prime::TNTPrimeEvent,
        "org.bukkit.event.block.VaultDisplayItemEvent" => pumpkin::plugin::block::vault_display_item::VaultDisplayItemEvent,

        // Player events
        "org.bukkit.event.player.AsyncPlayerChatEvent"
        | "io.papermc.paper.event.player.AsyncChatEvent" => pumpkin::plugin::player::async_player_chat::AsyncPlayerChatEvent,
        "org.bukkit.event.player.AsyncPlayerPreLoginEvent" => pumpkin::plugin::player::async_player_pre_login::AsyncPlayerPreLoginEvent,
        "org.bukkit.event.player.PlayerChangedMainHandEvent" => pumpkin::plugin::player::changed_main_hand::PlayerChangedMainHandEvent,
        "org.bukkit.event.player.PlayerEggThrowEvent" => pumpkin::plugin::player::egg_throw::PlayerEggThrowEvent,
        "org.bukkit.event.player.PlayerExpChangeEvent" => pumpkin::plugin::player::exp_change::PlayerExpChangeEvent,
        "org.bukkit.event.player.PlayerFishEvent" => pumpkin::plugin::player::fish::PlayerFishEvent,
        "org.bukkit.event.player.PlayerItemHeldEvent" => pumpkin::plugin::player::item_held::PlayerItemHeldEvent,
        "org.bukkit.event.player.PlayerAdvancementDoneEvent" => pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent,
        "org.bukkit.event.player.PlayerAnimationEvent"
        | "io.papermc.paper.event.player.PlayerArmSwingEvent" => pumpkin::plugin::player::player_animation::PlayerAnimationEvent,
        "org.bukkit.event.player.PlayerArmorStandManipulateEvent" => pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent,
        "org.bukkit.event.player.PlayerBedEnterEvent" => pumpkin::plugin::player::player_bed::PlayerBedEnterEvent,
        "org.bukkit.event.player.PlayerBedLeaveEvent" => pumpkin::plugin::player::player_bed::PlayerBedLeaveEvent,
        "org.bukkit.event.player.PlayerBucketEmptyEvent" => pumpkin::plugin::player::player_bucket::PlayerBucketEmptyEvent,
        "org.bukkit.event.player.PlayerBucketFillEvent" => pumpkin::plugin::player::player_bucket::PlayerBucketFillEvent,
        "org.bukkit.event.player.PlayerBucketEntityEvent" => pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent,
        "org.bukkit.event.player.PlayerChangedWorldEvent" => pumpkin::plugin::player::player_changed_world::PlayerChangedWorldEvent,
        "org.bukkit.event.player.PlayerChannelEvent" => pumpkin::plugin::player::player_channel::PlayerChannelEvent,
        "org.bukkit.event.player.PlayerChatEvent" => pumpkin::plugin::player::player_chat::PlayerChatEvent,
        "org.bukkit.event.player.PlayerCommandPreprocessEvent" => pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent,
        "org.bukkit.event.player.PlayerCommandSendEvent" => pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent,
        "org.bukkit.event.player.PlayerDropItemEvent" => pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent,
        "org.bukkit.event.player.PlayerEditBookEvent" => pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent,
        "com.destroystokyo.paper.event.player.PlayerElytraBoostEvent" => pumpkin::plugin::player::player_elytra_boost::PlayerElytraBoostEvent,
        "org.bukkit.event.player.PlayerExpCooldownChangeEvent" => pumpkin::plugin::player::player_exp_cooldown_change::PlayerExpCooldownChangeEvent,
        "org.bukkit.event.player.PlayerGameModeChangeEvent" => pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent,
        "org.bukkit.event.player.PlayerHarvestBlockEvent" => pumpkin::plugin::player::player_harvest_block::PlayerHarvestBlockEvent,
        "org.bukkit.event.player.PlayerHideEntityEvent" => pumpkin::plugin::player::player_hide_entity::PlayerHideEntityEvent,
        "org.bukkit.event.player.PlayerInputEvent" => pumpkin::plugin::player::player_input::PlayerInputEvent,
        "org.bukkit.event.player.PlayerInteractAtEntityEvent" => pumpkin::plugin::player::player_interact_at_entity::PlayerInteractAtEntityEvent,
        "org.bukkit.event.player.PlayerInteractEntityEvent" => pumpkin::plugin::player::player_interact_entity_event::PlayerInteractEntityEvent,
        "org.bukkit.event.player.PlayerInteractEvent" => pumpkin::plugin::player::player_interact_event::PlayerInteractEvent,
        "org.bukkit.event.player.PlayerItemBreakEvent" => pumpkin::plugin::player::player_item_break::PlayerItemBreakEvent,
        "org.bukkit.event.player.PlayerItemConsumeEvent" => pumpkin::plugin::player::player_item_consume::PlayerItemConsumeEvent,
        "org.bukkit.event.player.PlayerItemDamageEvent" => pumpkin::plugin::player::player_item_damage::PlayerItemDamageEvent,
        "org.bukkit.event.player.PlayerItemMendEvent" => pumpkin::plugin::player::player_item_mend::PlayerItemMendEvent,
        "org.bukkit.event.player.PlayerJoinEvent" => pumpkin::plugin::player::player_join::PlayerJoinEvent,
        "org.bukkit.event.player.PlayerKickEvent" => pumpkin::plugin::player::player_kick::PlayerKickEvent,
        "org.bukkit.event.entity.PlayerLeashEntityEvent" => pumpkin::plugin::player::player_leash_entity::PlayerLeashEntityEvent,
        "org.bukkit.event.player.PlayerQuitEvent" => pumpkin::plugin::player::player_leave::PlayerLeaveEvent,
        "org.bukkit.event.player.PlayerLevelChangeEvent" => pumpkin::plugin::player::player_level_change::PlayerLevelChangeEvent,
        "org.bukkit.event.player.PlayerLinksSendEvent" => pumpkin::plugin::player::player_links_send::PlayerLinksSendEvent,
        "org.bukkit.event.player.PlayerLocaleChangeEvent" => pumpkin::plugin::player::player_locale_change::PlayerLocaleChangeEvent,
        "org.bukkit.event.player.PlayerLoginEvent" => pumpkin::plugin::player::player_login::PlayerLoginEvent,
        "org.bukkit.event.player.PlayerMoveEvent"
        | "com.destroystokyo.paper.event.player.PlayerJumpEvent" => pumpkin::plugin::player::player_move::PlayerMoveEvent,
        "io.papermc.paper.event.player.PlayerNameEntityEvent" => pumpkin::plugin::player::player_name_entity::PlayerNameEntityEvent,
        "io.papermc.paper.event.player.PlayerOpenSignEvent" => pumpkin::plugin::player::player_open_sign::PlayerOpenSignEvent,
        "org.bukkit.event.player.PlayerPickupArrowEvent" => pumpkin::plugin::player::player_pickup_arrow::PlayerPickupArrowEvent,
        "org.bukkit.event.player.PlayerPortalEvent" => pumpkin::plugin::player::player_portal::PlayerPortalEvent,
        "org.bukkit.event.player.PlayerPreLoginEvent"
        | "io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent" => pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent,
        "org.bukkit.event.player.PlayerRecipeBookClickEvent" => pumpkin::plugin::player::player_recipe_book_click::PlayerRecipeBookClickEvent,
        "org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent" => pumpkin::plugin::player::player_recipe_book_settings_change::PlayerRecipeBookSettingsChangeEvent,
        "org.bukkit.event.player.PlayerRecipeDiscoverEvent" => pumpkin::plugin::player::player_recipe_discover::PlayerRecipeDiscoverEvent,
        "org.bukkit.event.player.PlayerRegisterChannelEvent" => pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent,
        "org.bukkit.event.player.PlayerResourcePackStatusEvent" => pumpkin::plugin::player::player_resource_pack_status::PlayerResourcePackStatusEvent,
        "org.bukkit.event.player.PlayerRespawnEvent" => pumpkin::plugin::player::player_respawn::PlayerRespawnEvent,
        "org.bukkit.event.player.PlayerRiptideEvent" => pumpkin::plugin::player::player_riptide::PlayerRiptideEvent,
        "org.bukkit.event.player.PlayerShearEntityEvent" => pumpkin::plugin::player::player_shear_entity::PlayerShearEntityEvent,
        "org.bukkit.event.player.PlayerShowEntityEvent" => pumpkin::plugin::player::player_show_entity::PlayerShowEntityEvent,
        "org.bukkit.event.player.PlayerSpawnChangeEvent" => pumpkin::plugin::player::player_spawn_change::PlayerSpawnChangeEvent,
        "org.spigotmc.event.player.PlayerSpawnLocationEvent" => pumpkin::plugin::player::player_spawn_location::PlayerSpawnLocationEvent,
        "org.bukkit.event.player.PlayerStatisticIncrementEvent" => pumpkin::plugin::player::player_statistic_increment::PlayerStatisticIncrementEvent,
        "org.bukkit.event.player.PlayerSwapHandItemsEvent" => pumpkin::plugin::player::player_swap_hands::PlayerSwapHandItemsEvent,
        "org.bukkit.event.player.PlayerTakeLecternBookEvent" => pumpkin::plugin::player::player_take_lectern_book::PlayerTakeLecternBookEvent,
        "org.bukkit.event.player.PlayerTeleportEvent" => pumpkin::plugin::player::player_teleport::PlayerTeleportEvent,
        "org.bukkit.event.player.PlayerToggleFlightEvent" => pumpkin::plugin::player::player_toggle_flight_event::PlayerToggleFlightEvent,
        "org.bukkit.event.player.PlayerToggleSneakEvent" => pumpkin::plugin::player::player_toggle_sneak_event::PlayerToggleSneakEvent,
        "org.bukkit.event.player.PlayerToggleSprintEvent" => pumpkin::plugin::player::player_toggle_sprint_event::PlayerToggleSprintEvent,
        "org.bukkit.event.player.PlayerUnleashEntityEvent" => pumpkin::plugin::player::player_unleash_entity::PlayerUnleashEntityEvent,
        "org.bukkit.event.player.PlayerUnregisterChannelEvent" => pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent,
        "org.bukkit.event.player.PlayerVelocityEvent" => pumpkin::plugin::player::player_velocity::PlayerVelocityEvent,

        // Hanging events
        "org.bukkit.event.hanging.HangingBreakEvent" => pumpkin::plugin::hanging::hanging_break::HangingBreakEvent,
        "org.bukkit.event.hanging.HangingBreakByEntityEvent" => pumpkin::plugin::hanging::hanging_break_by_entity::HangingBreakByEntityEvent,
        "org.bukkit.event.hanging.HangingPlaceEvent" => pumpkin::plugin::hanging::hanging_place::HangingPlaceEvent,

        // Entity events
        "org.bukkit.event.entity.AreaEffectCloudApplyEvent" => pumpkin::plugin::entity::area_effect_cloud_apply::AreaEffectCloudApplyEvent,
        "org.bukkit.event.entity.ArrowBodyCountChangeEvent" => pumpkin::plugin::entity::arrow_body_count_change::ArrowBodyCountChangeEvent,
        "org.bukkit.event.entity.BatToggleSleepEvent" => pumpkin::plugin::entity::bat_toggle_sleep::BatToggleSleepEvent,
        "org.bukkit.event.entity.CreatureSpawnEvent" => pumpkin::plugin::entity::creature_spawn::CreatureSpawnEvent,
        "org.bukkit.event.entity.CreeperPowerEvent" => pumpkin::plugin::entity::creeper_power::CreeperPowerEvent,
        "org.bukkit.event.entity.EnderDragonChangePhaseEvent" => pumpkin::plugin::entity::ender_dragon_change_phase::EnderDragonChangePhaseEvent,
        "org.bukkit.event.entity.EntityAirChangeEvent" => pumpkin::plugin::entity::entity_air_change::EntityAirChangeEvent,
        "org.bukkit.event.entity.EntityBreakDoorEvent" => pumpkin::plugin::entity::entity_break_door::EntityBreakDoorEvent,
        "org.bukkit.event.entity.EntityBreedEvent" => pumpkin::plugin::entity::entity_breed::EntityBreedEvent,
        "org.bukkit.event.entity.EntityChangeBlockEvent" => pumpkin::plugin::entity::entity_change_block::EntityChangeBlockEvent,
        "org.bukkit.event.entity.EntityCombustEvent" => pumpkin::plugin::entity::entity_combust::EntityCombustEvent,
        "org.bukkit.event.entity.EntityCombustByBlockEvent" => pumpkin::plugin::entity::entity_combust_by_block::EntityCombustByBlockEvent,
        "org.bukkit.event.entity.EntityCombustByEntityEvent" => pumpkin::plugin::entity::entity_combust_by_entity::EntityCombustByEntityEvent,
        "org.bukkit.event.entity.EntityDamageEvent" => pumpkin::plugin::entity::entity_damage::EntityDamageEvent,
        "org.bukkit.event.entity.EntityDamageByBlockEvent" => pumpkin::plugin::entity::entity_damage_by_block::EntityDamageByBlockEvent,
        "org.bukkit.event.entity.EntityDamageByEntityEvent" => pumpkin::plugin::entity::entity_damage_by_entity::EntityDamageByEntityEvent,
        "org.bukkit.event.entity.EntityDeathEvent" => pumpkin::plugin::entity::entity_death::EntityDeathEvent,
        "org.bukkit.event.entity.PlayerDeathEvent" => pumpkin::plugin::entity::entity_death::PlayerDeathEvent,
        "org.bukkit.event.entity.EntityDismountEvent" => pumpkin::plugin::entity::entity_dismount::EntityDismountEvent,
        "org.bukkit.event.entity.EntityDropItemEvent" => pumpkin::plugin::entity::entity_drop_item::EntityDropItemEvent,
        "io.papermc.paper.event.entity.EntityDyeEvent" => pumpkin::plugin::entity::entity_dye::EntityDyeEvent,
        "org.bukkit.event.entity.EntityEnterBlockEvent" => pumpkin::plugin::entity::entity_enter_block::EntityEnterBlockEvent,
        "org.bukkit.event.entity.EntityEnterLoveModeEvent" => pumpkin::plugin::entity::entity_enter_love_mode::EntityEnterLoveModeEvent,
        "org.bukkit.event.entity.EntityExhaustionEvent" => pumpkin::plugin::entity::entity_exhaustion::EntityExhaustionEvent,
        "org.bukkit.event.entity.EntityExplodeEvent" => pumpkin::plugin::entity::entity_explode::EntityExplodeEvent,
        "org.bukkit.event.entity.EntityInteractEvent" => pumpkin::plugin::entity::entity_interact::EntityInteractEvent,
        "org.bukkit.event.entity.EntityKnockbackEvent" => pumpkin::plugin::entity::entity_knockback::EntityKnockbackEvent,
        "org.bukkit.event.entity.EntityKnockbackByEntityEvent" => pumpkin::plugin::entity::entity_knockback_by_entity::EntityKnockbackByEntityEvent,
        "org.bukkit.event.entity.EntityMountEvent" => pumpkin::plugin::entity::entity_mount::EntityMountEvent,
        "org.bukkit.event.entity.EntityPickupItemEvent" => pumpkin::plugin::entity::entity_pickup_item::EntityPickupItemEvent,
        "org.bukkit.event.entity.EntityPlaceEvent" => pumpkin::plugin::entity::entity_place::EntityPlaceEvent,
        "org.bukkit.event.entity.EntityPortalEvent" => pumpkin::plugin::entity::entity_portal::EntityPortalEvent,
        "org.bukkit.event.entity.EntityPortalEnterEvent" => pumpkin::plugin::entity::entity_portal_enter::EntityPortalEnterEvent,
        "org.bukkit.event.entity.EntityPortalExitEvent" => pumpkin::plugin::entity::entity_portal_exit::EntityPortalExitEvent,
        "org.bukkit.event.entity.EntityPoseChangeEvent" => pumpkin::plugin::entity::entity_pose_change::EntityPoseChangeEvent,
        "org.bukkit.event.entity.EntityPotionEffectEvent" => pumpkin::plugin::entity::entity_potion_effect::EntityPotionEffectEvent,
        "org.bukkit.event.entity.EntityRegainHealthEvent" => pumpkin::plugin::entity::entity_regain_health::EntityRegainHealthEvent,
        "org.bukkit.event.entity.EntityRemoveEvent" => pumpkin::plugin::entity::entity_remove::EntityRemoveEvent,
        "org.bukkit.event.entity.EntityResurrectEvent" => pumpkin::plugin::entity::entity_resurrect::EntityResurrectEvent,
        "org.bukkit.event.entity.EntityShootBowEvent" => pumpkin::plugin::entity::entity_shoot_bow::EntityShootBowEvent,
        "org.bukkit.event.entity.EntitySpawnEvent" => pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent,
        "org.bukkit.event.entity.EntitySpellCastEvent" => pumpkin::plugin::entity::entity_spell_cast::EntitySpellCastEvent,
        "org.bukkit.event.entity.EntityTameEvent" => pumpkin::plugin::entity::entity_tame::EntityTameEvent,
        "org.bukkit.event.entity.EntityTargetEvent" => pumpkin::plugin::entity::entity_target::EntityTargetEvent,
        "org.bukkit.event.entity.EntityTargetLivingEntityEvent" => pumpkin::plugin::entity::entity_target_living_entity::EntityTargetLivingEntityEvent,
        "org.bukkit.event.entity.EntityTeleportEvent" => pumpkin::plugin::entity::entity_teleport::EntityTeleportEvent,
        "org.bukkit.event.entity.EntityToggleGlideEvent" => pumpkin::plugin::entity::entity_toggle_glide::EntityToggleGlideEvent,
        "org.bukkit.event.entity.EntityToggleSwimEvent" => pumpkin::plugin::entity::entity_toggle_swim::EntityToggleSwimEvent,
        "org.bukkit.event.entity.EntityTransformEvent" => pumpkin::plugin::entity::entity_transform::EntityTransformEvent,
        "org.bukkit.event.entity.EntityUnleashEvent" => pumpkin::plugin::entity::entity_unleash::EntityUnleashEvent,
        "org.bukkit.event.entity.ExpBottleEvent" => pumpkin::plugin::entity::exp_bottle::ExpBottleEvent,
        "org.bukkit.event.entity.ExplosionPrimeEvent" => pumpkin::plugin::entity::explosion_prime::ExplosionPrimeEvent,
        "org.bukkit.event.entity.FireworkExplodeEvent" => pumpkin::plugin::entity::firework_explode::FireworkExplodeEvent,
        "org.bukkit.event.entity.FoodLevelChangeEvent" => pumpkin::plugin::entity::food_level_change::FoodLevelChangeEvent,
        "org.bukkit.event.entity.HorseJumpEvent" => pumpkin::plugin::entity::horse_jump::HorseJumpEvent,
        "org.bukkit.event.entity.ItemDespawnEvent" => pumpkin::plugin::entity::item_despawn::ItemDespawnEvent,
        "org.bukkit.event.entity.ItemMergeEvent" => pumpkin::plugin::entity::item_merge::ItemMergeEvent,
        "org.bukkit.event.entity.ItemSpawnEvent" => pumpkin::plugin::entity::item_spawn::ItemSpawnEvent,
        "org.bukkit.event.entity.LingeringPotionSplashEvent" => pumpkin::plugin::entity::lingering_potion_splash::LingeringPotionSplashEvent,
        "org.bukkit.event.entity.PigZapEvent" => pumpkin::plugin::entity::pig_zap::PigZapEvent,
        "org.bukkit.event.entity.PigZombieAngerEvent" => pumpkin::plugin::entity::pig_zombie_anger::PigZombieAngerEvent,
        "org.bukkit.event.entity.PiglinBarterEvent" => pumpkin::plugin::entity::piglin_barter::PiglinBarterEvent,
        "org.bukkit.event.entity.PotionSplashEvent" => pumpkin::plugin::entity::potion_splash::PotionSplashEvent,
        "org.bukkit.event.entity.ProjectileHitEvent" => pumpkin::plugin::entity::projectile_hit::ProjectileHitEvent,
        "org.bukkit.event.entity.ProjectileLaunchEvent" => pumpkin::plugin::entity::projectile_launch::ProjectileLaunchEvent,
        "org.bukkit.event.entity.SheepDyeWoolEvent" => pumpkin::plugin::entity::sheep_dye_wool::SheepDyeWoolEvent,
        "org.bukkit.event.entity.SheepRegrowWoolEvent" => pumpkin::plugin::entity::sheep_regrow_wool::SheepRegrowWoolEvent,
        "org.bukkit.event.entity.SlimeSplitEvent" => pumpkin::plugin::entity::slime_split::SlimeSplitEvent,
        "org.bukkit.event.entity.SpawnerSpawnEvent" => pumpkin::plugin::entity::spawner_spawn::SpawnerSpawnEvent,
        "org.bukkit.event.entity.StriderTemperatureChangeEvent" => pumpkin::plugin::entity::strider_temperature_change::StriderTemperatureChangeEvent,
        "org.bukkit.event.entity.TrialSpawnerSpawnEvent" => pumpkin::plugin::entity::trial_spawner_spawn::TrialSpawnerSpawnEvent,
        "org.bukkit.event.entity.VillagerAcquireTradeEvent" => pumpkin::plugin::entity::villager_acquire_trade::VillagerAcquireTradeEvent,
        "org.bukkit.event.entity.VillagerCareerChangeEvent" => pumpkin::plugin::entity::villager_career_change::VillagerCareerChangeEvent,
        "org.bukkit.event.entity.VillagerReplenishTradeEvent" => pumpkin::plugin::entity::villager_replenish_trade::VillagerReplenishTradeEvent,
        "io.papermc.paper.event.entity.WardenAngerChangeEvent" => pumpkin::plugin::entity::warden_anger_change::WardenAngerChangeEvent,

        // Raid events
        "org.bukkit.event.raid.RaidFinishEvent" => pumpkin::plugin::raid::raid_finish::RaidFinishEvent,
        "org.bukkit.event.raid.RaidSpawnWaveEvent" => pumpkin::plugin::raid::raid_spawn_wave::RaidSpawnWaveEvent,
        "org.bukkit.event.raid.RaidStopEvent" => pumpkin::plugin::raid::raid_stop::RaidStopEvent,
        "org.bukkit.event.raid.RaidTriggerEvent" => pumpkin::plugin::raid::raid_trigger::RaidTriggerEvent,

        // Enchantment events
        "org.bukkit.event.enchantment.EnchantItemEvent" => pumpkin::plugin::enchantment::enchant_item::EnchantItemEvent,
        "org.bukkit.event.enchantment.PrepareItemEnchantEvent" => pumpkin::plugin::enchantment::prepare_item_enchant::PrepareItemEnchantEvent,
    );

    Some(())
}
