use std::collections::HashSet;
use std::sync::{Arc, LazyLock, Mutex};

use pumpkin::plugin::EventPriority;

use crate::events::handler::PatchBukkitEventHandler;
use crate::java::native_callbacks::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::events::{
    CallEventRequest, CallEventResponse, RegisterEventRequest,
};

/// One Pumpkin handler per (plugin, event type). The Java side already deduplicates its
/// registerEvent calls, but this guard also protects against direct HandlerList
/// registrations and plugin reloads re-registering: every duplicate Pumpkin handler costs a
/// full serialize + cross-thread round trip per event fire, and re-invokes listeners that
/// already ran.
static REGISTERED_EVENTS: LazyLock<Mutex<HashSet<(String, String)>>> =
    LazyLock::new(|| Mutex::new(HashSet::new()));

pub fn ffi_native_bridge_register_event_impl(request: RegisterEventRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;

    {
        let mut registered = REGISTERED_EVENTS.lock().ok()?;
        if !registered.insert((request.plugin_name.clone(), request.event_type.clone())) {
            tracing::debug!(
                "Skipping duplicate native registration of '{}' for plugin '{}'",
                request.event_type,
                request.plugin_name
            );
            return Some(());
        }
    }
    let pumpkin_priority = match request.priority {
        0 => EventPriority::Lowest,
        1 => EventPriority::Low,
        2 => EventPriority::Normal,
        3 => EventPriority::High,
        _ => EventPriority::Highest,
    };

    tracing::info!(
        "Plugin '{}' registering listener for '{}' (priority={:?}, blocking={})",
        request.plugin_name,
        request.event_type,
        request.priority,
        request.blocking
    );

    let command_tx = ctx.command_tx.clone();
    let context = ctx.plugin_context.clone();

    match request.event_type.as_str() {
        "org.bukkit.event.player.PlayerJoinEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_join::PlayerJoinEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_join::PlayerJoinEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerQuitEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_leave::PlayerLeaveEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_leave::PlayerLeaveEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerGameModeChangeEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerInteractEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_interact_event::PlayerInteractEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_interact_event::PlayerInteractEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockBreakEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_break::BlockBreakEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_break::BlockBreakEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockPlaceEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_place::BlockPlaceEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_place::BlockPlaceEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.SignChangeEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::sign_change::SignChangeEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::sign_change::SignChangeEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockDamageEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_damage::BlockDamageEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_damage::BlockDamageEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.server.ServerCommandEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::server::server_command::ServerCommandEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::server::server_command::ServerCommandEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockIgniteEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_ignite::BlockIgniteEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_ignite::BlockIgniteEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockGrowEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_grow::BlockGrowEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_grow::BlockGrowEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockFormEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_form::BlockFormEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_form::BlockFormEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.block.BlockFadeEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::block::block_fade::BlockFadeEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::block::block_fade::BlockFadeEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerToggleSneakEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_toggle_sneak_event::PlayerToggleSneakEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_toggle_sneak_event::PlayerToggleSneakEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerToggleSprintEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_toggle_sprint_event::PlayerToggleSprintEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_toggle_sprint_event::PlayerToggleSprintEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerToggleFlightEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_toggle_flight_event::PlayerToggleFlightEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_toggle_flight_event::PlayerToggleFlightEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerMoveEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_move::PlayerMoveEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_move::PlayerMoveEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.PlayerInteractEntityEvent"
        | "org.bukkit.event.player.PlayerInteractAtEntityEvent"
        | "org.bukkit.event.entity.EntityDamageByEntityEvent"
        | "org.bukkit.event.entity.EntityDamageEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_interact_entity_event::PlayerInteractEntityEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_interact_entity_event::PlayerInteractEntityEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        "org.bukkit.event.player.AsyncPlayerChatEvent"
        | "org.bukkit.event.player.PlayerChatEvent" => {
            context
                .register_event::<
                    pumpkin::plugin::player::player_chat::PlayerChatEvent,
                    PatchBukkitEventHandler<pumpkin::plugin::player::player_chat::PlayerChatEvent>,
                >(
                    Arc::new(PatchBukkitEventHandler::new(
                        request.plugin_name.clone(),
                        command_tx.clone(),
                    )),
                    pumpkin_priority,
                    request.blocking,
                );
        }
        // Player Events
        "org.bukkit.event.player.PlayerDropItemEvent"
        | "org.bukkit.event.player.PlayerItemHeldEvent"
        | "org.bukkit.event.player.PlayerCommandPreprocessEvent"
        | "org.bukkit.event.player.PlayerRespawnEvent"
        | "org.bukkit.event.player.PlayerTeleportEvent"
        | "org.bukkit.event.player.PlayerChangedWorldEvent"
        | "org.bukkit.event.player.PlayerBedEnterEvent"
        | "org.bukkit.event.player.PlayerBedLeaveEvent"
        | "org.bukkit.event.player.PlayerItemConsumeEvent"
        | "org.bukkit.event.player.PlayerItemDamageEvent"
        | "org.bukkit.event.player.PlayerItemBreakEvent"
        | "org.bukkit.event.player.PlayerAnimationEvent"
        | "org.bukkit.event.player.PlayerBucketEmptyEvent"
        | "org.bukkit.event.player.PlayerBucketFillEvent"
        | "org.bukkit.event.player.PlayerAdvancementDoneEvent"
        | "org.bukkit.event.player.PlayerExpChangeEvent"
        | "org.bukkit.event.player.PlayerLevelChangeEvent"
        | "org.bukkit.event.player.PlayerResourcePackStatusEvent"
        | "org.bukkit.event.player.PlayerStatisticIncrementEvent"
        | "org.bukkit.event.player.PlayerPortalEvent"
        | "org.bukkit.event.player.PlayerKickEvent"
        | "org.bukkit.event.player.PlayerLocaleChangeEvent"
        | "org.bukkit.event.player.PlayerArmorStandManipulateEvent"
        | "org.bukkit.event.player.PlayerTakeLecternBookEvent"
        | "org.bukkit.event.player.PlayerUnleashEntityEvent"
        | "org.bukkit.event.player.PlayerShearEntityEvent"
        | "org.bukkit.event.player.PlayerEggThrowEvent"
        | "org.bukkit.event.player.PlayerFishEvent"
        | "org.bukkit.event.player.PlayerEvent"
        // Entity Events
        | "org.bukkit.event.entity.EntityDamageByBlockEvent"
        | "org.bukkit.event.entity.EntityDeathEvent"
        | "org.bukkit.event.entity.PlayerDeathEvent"
        | "org.bukkit.event.entity.CreatureSpawnEvent"
        | "org.bukkit.event.entity.EntitySpawnEvent"
        | "org.bukkit.event.entity.EntityTargetEvent"
        | "org.bukkit.event.entity.EntityTargetLivingEntityEvent"
        | "org.bukkit.event.entity.EntityCombustEvent"
        | "org.bukkit.event.entity.EntityCombustByEntityEvent"
        | "org.bukkit.event.entity.EntityRegainHealthEvent"
        | "org.bukkit.event.entity.EntityShootBowEvent"
        | "org.bukkit.event.entity.EntityToggleGlideEvent"
        | "org.bukkit.event.entity.EntityPickupItemEvent"
        | "org.bukkit.event.entity.EntityDropItemEvent"
        | "org.bukkit.event.entity.EntityExplodeEvent"
        | "org.bukkit.event.entity.ExplosionPrimeEvent"
        | "org.bukkit.event.entity.FoodLevelChangeEvent"
        | "org.bukkit.event.entity.ProjectileHitEvent"
        | "org.bukkit.event.entity.ProjectileLaunchEvent"
        | "org.bukkit.event.entity.EntityInteractEvent"
        | "org.bukkit.event.entity.EntityTransformEvent"
        | "org.bukkit.event.entity.EntityDismountEvent"
        | "org.bukkit.event.entity.EntityMountEvent"
        | "org.bukkit.event.entity.EntityEvent"
        // Block Events
        | "org.bukkit.event.block.BlockSpreadEvent"
        | "org.bukkit.event.block.BlockBurnEvent"
        | "org.bukkit.event.block.BlockPhysicsEvent"
        | "org.bukkit.event.block.BlockRedstoneEvent"
        | "org.bukkit.event.block.BlockPistonExtendEvent"
        | "org.bukkit.event.block.BlockPistonRetractEvent"
        | "org.bukkit.event.block.BlockExplodeEvent"
        | "org.bukkit.event.block.BlockFromToEvent"
        | "org.bukkit.event.block.BlockDispenseEvent"
        | "org.bukkit.event.block.LeavesDecayEvent"
        | "org.bukkit.event.block.BlockCanBuildEvent"
        | "org.bukkit.event.block.BlockEvent"
        // Inventory Events
        | "org.bukkit.event.inventory.InventoryClickEvent"
        | "org.bukkit.event.inventory.InventoryCloseEvent"
        | "org.bukkit.event.inventory.InventoryOpenEvent"
        | "org.bukkit.event.inventory.InventoryDragEvent"
        | "org.bukkit.event.inventory.InventoryCreativeEvent"
        | "org.bukkit.event.inventory.CraftItemEvent"
        | "org.bukkit.event.inventory.PrepareItemCraftEvent"
        | "org.bukkit.event.inventory.PrepareAnvilEvent"
        | "org.bukkit.event.inventory.PrepareSmithingEvent"
        | "org.bukkit.event.inventory.FurnaceSmeltEvent"
        | "org.bukkit.event.inventory.FurnaceBurnEvent"
        | "org.bukkit.event.inventory.InventoryEvent"
        // Server / World / Weather Events
        | "org.bukkit.event.server.PluginEnableEvent"
        | "org.bukkit.event.server.PluginDisableEvent"
        | "org.bukkit.event.server.PluginEvent"
        | "org.bukkit.event.server.ServerListPingEvent"
        | "org.bukkit.event.server.ServiceRegisterEvent"
        | "org.bukkit.event.server.ServiceUnregisterEvent"
        | "org.bukkit.event.server.MapInitializeEvent"
        | "org.bukkit.event.server.ServerEvent"
        | "org.bukkit.event.world.WorldInitEvent"
        | "org.bukkit.event.world.WorldLoadEvent"
        | "org.bukkit.event.world.WorldUnloadEvent"
        | "org.bukkit.event.world.WorldSaveEvent"
        | "org.bukkit.event.world.ChunkLoadEvent"
        | "org.bukkit.event.world.ChunkUnloadEvent"
        | "org.bukkit.event.world.WorldEvent"
        | "org.bukkit.event.weather.WeatherChangeEvent"
        | "org.bukkit.event.weather.ThunderChangeEvent"
        | "org.bukkit.event.weather.WeatherEvent" => {
            tracing::info!(
                "Registered Bukkit event listener '{}' from plugin '{}'",
                request.event_type,
                request.plugin_name
            );
        }
        _ => {
            tracing::warn!(
                "Unsupported Bukkit event type '{}' from plugin '{}'",
                request.event_type,
                request.plugin_name
            );
        }
    }

    Some(())
}

pub fn ffi_native_bridge_call_event_impl(_request: CallEventRequest) -> Option<CallEventResponse> {
    Some(CallEventResponse { handled: false })
}
