use std::sync::Arc;

use pumpkin::plugin::EventPriority;
use pumpkin::plugin::block::block_break::BlockBreakEvent;
use pumpkin::plugin::block::block_damage::BlockDamageEvent;
use pumpkin::plugin::block::block_damage_abort::BlockDamageAbortEvent;
use pumpkin::plugin::block::block_dispense::BlockDispenseEvent;
use pumpkin::plugin::block::block_drop_item::BlockDropItemEvent;
use pumpkin::plugin::block::block_explode::BlockExplodeEvent;
use pumpkin::plugin::block::block_fade::BlockFadeEvent;
use pumpkin::plugin::block::block_fertilize::BlockFertilizeEvent;
use pumpkin::plugin::block::block_place::BlockPlaceEvent;
use pumpkin::plugin::block::block_can_build::BlockCanBuildEvent;
use pumpkin::plugin::block::block_burn::BlockBurnEvent;
use pumpkin::plugin::block::block_ignite::BlockIgniteEvent;
use pumpkin::plugin::block::block_spread::BlockSpreadEvent;
use pumpkin::plugin::player::player_chat::PlayerChatEvent;
use pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent;
use pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent;
use pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent;
use pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent;
use pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent;
use pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent;
use pumpkin::plugin::player::player_fish::PlayerFishEvent;
use pumpkin::plugin::player::player_interact_entity::PlayerInteractEntityEvent;
use pumpkin::plugin::player::player_interact_at_entity::PlayerInteractAtEntityEvent;
use pumpkin::plugin::player::player_item_held::PlayerItemHeldEvent;
use pumpkin::plugin::player::player_item_damage::PlayerItemDamageEvent;
use pumpkin::plugin::player::player_item_break::PlayerItemBreakEvent;
use pumpkin::plugin::player::player_item_consume::PlayerItemConsumeEvent;
use pumpkin::plugin::player::player_item_mend::PlayerItemMendEvent;
use pumpkin::plugin::player::player_level_change::PlayerLevelChangeEvent;
use pumpkin::plugin::player::player_kick::PlayerKickEvent;
use pumpkin::plugin::player::player_toggle_sneak::PlayerToggleSneakEvent;
use pumpkin::plugin::player::player_toggle_sprint::PlayerToggleSprintEvent;
use pumpkin::plugin::player::player_toggle_flight::PlayerToggleFlightEvent;
use pumpkin::plugin::player::player_swap_hand_items::PlayerSwapHandItemsEvent;
use pumpkin::plugin::player::player_resource_pack_status::PlayerResourcePackStatusEvent;
use pumpkin::plugin::player::player_respawn::PlayerRespawnEvent;
use pumpkin::plugin::player::player_pickup_arrow::PlayerPickupArrowEvent;
use pumpkin::plugin::player::player_portal::PlayerPortalEvent;
use pumpkin::plugin::player::player_recipe_discover::PlayerRecipeDiscoverEvent;
use pumpkin::plugin::player::player_riptide::PlayerRiptideEvent;
use pumpkin::plugin::player::player_shear_entity::PlayerShearEntityEvent;
use pumpkin::plugin::player::player_spawn_location::PlayerSpawnLocationEvent;
use pumpkin::plugin::player::player_statistic_increment::PlayerStatisticIncrementEvent;
use pumpkin::plugin::player::player_velocity::PlayerVelocityEvent;
use pumpkin::plugin::player::player_harvest_block::PlayerHarvestBlockEvent;
use pumpkin::plugin::player::player_interact_event::{InteractAction, PlayerInteractEvent};
use pumpkin::plugin::player::player_join::PlayerJoinEvent;
use pumpkin::plugin::player::player_login::PlayerLoginEvent;
use pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent;
use pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent;
use pumpkin::plugin::player::player_animation::PlayerAnimationEvent;
use pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent;
use pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent;
use pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent;
use pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent;
use pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent;
use pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent;
use pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent;
use pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent;
use pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent;
use pumpkin::plugin::player::player_leave::PlayerLeaveEvent;
use pumpkin::plugin::player::player_move::PlayerMoveEvent;
use pumpkin::plugin::player::player_teleport::PlayerTeleportEvent;
use pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent;
use pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent;
use pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent;
use pumpkin::plugin::server::server_command::ServerCommandEvent;
use pumpkin_data::Block;
use pumpkin_data::BlockDirection;
use pumpkin_data::data_component_impl::EquipmentSlot;
use pumpkin_data::item::Item;
use pumpkin_world::item::ItemStack;
use pumpkin_util::math::vector3::Vector3;
use pumpkin_util::text::TextComponent;
use pumpkin_util::Hand;
use tokio::sync::Mutex;

use crate::events::handler::PatchBukkitEventHandler;
use crate::java::native_callbacks::CALLBACK_CONTEXT;
use crate::proto::patchbukkit::events::event::Data;
use crate::proto::patchbukkit::events::{
    CallEventRequest, CallEventResponse, RegisterEventRequest,
};

pub fn ffi_native_bridge_register_event_impl(request: RegisterEventRequest) -> Option<()> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let pumpkin_priority = match request.priority {
        0 => EventPriority::Lowest,
        1 => EventPriority::Low,
        2 => EventPriority::Normal,
        3 => EventPriority::High,
        _ => EventPriority::Highest,
    };

    log::info!(
        "Plugin '{}' registering listener for '{}' (priority={:?}, blocking={})",
        request.plugin_name,
        request.event_type,
        request.priority,
        request.blocking
    );

    let command_tx = ctx.command_tx.clone();
    let context = ctx.plugin_context.clone();

    tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
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
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerLoginEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_login::PlayerLoginEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_login::PlayerLoginEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.AsyncPlayerPreLoginEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_pre_login::PlayerPreLoginEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
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
                        )
                        .await;
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
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerAdvancementDoneEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_advancement_done::PlayerAdvancementDoneEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerAnimationEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_animation::PlayerAnimationEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_animation::PlayerAnimationEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerArmorStandManipulateEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_armor_stand_manipulate::PlayerArmorStandManipulateEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerBedEnterEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bed_enter::PlayerBedEnterEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerBedLeaveEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bed_leave::PlayerBedLeaveEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerBucketEmptyEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bucket_empty::PlayerBucketEmptyEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerBucketFillEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bucket_fill::PlayerBucketFillEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerBucketEntityEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_bucket_entity::PlayerBucketEntityEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerChangedMainHandEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_changed_main_hand::PlayerChangedMainHandEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerRegisterChannelEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_register_channel::PlayerRegisterChannelEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerUnregisterChannelEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_unregister_channel::PlayerUnregisterChannelEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerTeleportEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_teleport::PlayerTeleportEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::player::player_teleport::PlayerTeleportEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerChangedWorldEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_change_world::PlayerChangeWorldEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerGameModeChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_gamemode_change::PlayerGamemodeChangeEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.AsyncPlayerChatEvent" => {
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
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerCommandPreprocessEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_command_preprocess::PlayerCommandPreprocessEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerCommandSendEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_command_send::PlayerCommandSendEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerDropItemEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_drop_item::PlayerDropItemEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerEditBookEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_edit_book::PlayerEditBookEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerEggThrowEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_egg_throw::PlayerEggThrowEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerExpChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_exp_change::PlayerExpChangeEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerFishEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_fish::PlayerFishEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_fish::PlayerFishEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerInteractEntityEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_interact_entity::PlayerInteractEntityEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_interact_entity::PlayerInteractEntityEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerInteractAtEntityEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_interact_at_entity::PlayerInteractAtEntityEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_interact_at_entity::PlayerInteractAtEntityEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerItemHeldEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_item_held::PlayerItemHeldEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_item_held::PlayerItemHeldEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerItemDamageEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_item_damage::PlayerItemDamageEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_item_damage::PlayerItemDamageEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerItemBreakEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_item_break::PlayerItemBreakEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_item_break::PlayerItemBreakEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerItemConsumeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_item_consume::PlayerItemConsumeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_item_consume::PlayerItemConsumeEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerItemMendEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_item_mend::PlayerItemMendEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_item_mend::PlayerItemMendEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerLevelChangeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_level_change::PlayerLevelChangeEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_level_change::PlayerLevelChangeEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerKickEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_kick::PlayerKickEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_kick::PlayerKickEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerToggleSneakEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_toggle_sneak::PlayerToggleSneakEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_toggle_sneak::PlayerToggleSneakEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerToggleSprintEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_toggle_sprint::PlayerToggleSprintEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_toggle_sprint::PlayerToggleSprintEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerToggleFlightEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_toggle_flight::PlayerToggleFlightEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_toggle_flight::PlayerToggleFlightEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerSwapHandItemsEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_swap_hand_items::PlayerSwapHandItemsEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_swap_hand_items::PlayerSwapHandItemsEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerResourcePackStatusEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_resource_pack_status::PlayerResourcePackStatusEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_resource_pack_status::PlayerResourcePackStatusEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerRespawnEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_respawn::PlayerRespawnEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_respawn::PlayerRespawnEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerPickupArrowEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_pickup_arrow::PlayerPickupArrowEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_pickup_arrow::PlayerPickupArrowEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerPortalEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_portal::PlayerPortalEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_portal::PlayerPortalEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerRecipeDiscoverEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_recipe_discover::PlayerRecipeDiscoverEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_recipe_discover::PlayerRecipeDiscoverEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerRiptideEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_riptide::PlayerRiptideEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_riptide::PlayerRiptideEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerShearEntityEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_shear_entity::PlayerShearEntityEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_shear_entity::PlayerShearEntityEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.spigotmc.event.player.PlayerSpawnLocationEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_spawn_location::PlayerSpawnLocationEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_spawn_location::PlayerSpawnLocationEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerStatisticIncrementEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_statistic_increment::PlayerStatisticIncrementEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_statistic_increment::PlayerStatisticIncrementEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerVelocityEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_velocity::PlayerVelocityEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_velocity::PlayerVelocityEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerHarvestBlockEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_harvest_block::PlayerHarvestBlockEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_harvest_block::PlayerHarvestBlockEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.player.PlayerInteractEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::player::player_interact_event::PlayerInteractEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::player::player_interact_event::PlayerInteractEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
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
                        )
                        .await;
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
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockDamageAbortEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_damage_abort::BlockDamageAbortEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::block::block_damage_abort::BlockDamageAbortEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockDispenseEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_dispense::BlockDispenseEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_dispense::BlockDispenseEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockDropItemEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_drop_item::BlockDropItemEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_drop_item::BlockDropItemEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockExplodeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_explode::BlockExplodeEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_explode::BlockExplodeEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
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
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockFertilizeEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_fertilize::BlockFertilizeEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_fertilize::BlockFertilizeEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockCanBuildEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_can_build::BlockCanBuildEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_can_build::BlockCanBuildEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockBurnEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_burn::BlockBurnEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_burn::BlockBurnEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
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
                        )
                        .await;
                }
                "org.bukkit.event.block.BlockSpreadEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::block::block_spread::BlockSpreadEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::block::block_spread::BlockSpreadEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
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
                        )
                        .await;
                }
                "org.bukkit.event.entity.EntitySpawnEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.entity.EntityDamageEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::entity::entity_damage::EntityDamageEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::entity::entity_damage::EntityDamageEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.entity.EntityDeathEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::entity::entity_death::EntityDeathEvent,
                            PatchBukkitEventHandler<pumpkin::plugin::entity::entity_death::EntityDeathEvent>,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.server.ServerCommandEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::server::server_command::ServerCommandEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::server::server_command::ServerCommandEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                "org.bukkit.event.server.BroadcastMessageEvent" => {
                    context
                        .register_event::<
                            pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent,
                            PatchBukkitEventHandler<
                                pumpkin::plugin::server::server_broadcast::ServerBroadcastEvent,
                            >,
                        >(
                            Arc::new(PatchBukkitEventHandler::new(
                                request.plugin_name.clone(),
                                command_tx.clone(),
                            )),
                            pumpkin_priority,
                            request.blocking,
                        )
                        .await;
                }
                _ => {
                    log::warn!(
                        "Unsupported Bukkit event type '{}' from plugin '{}'",
                        request.event_type, request.plugin_name
                    );
                }
            }
        });
    });

    Some(())
}

pub fn ffi_native_bridge_call_event_impl(request: CallEventRequest) -> Option<CallEventResponse> {
    let ctx = CALLBACK_CONTEXT.get()?;
    let event = request.event?;
    log::debug!("Java calling event {:?}", event);

    let context = ctx.plugin_context.clone();

    let handled = tokio::task::block_in_place(|| {
        ctx.runtime.block_on(async {
            match event.data? {
                Data::PlayerJoin(player_join_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_join_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerJoinEvent::new(
                        player,
                        TextComponent::from_legacy_string(&player_join_event_data.join_message),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerLogin(player_login_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_login_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerLoginEvent::new(
                        player,
                        TextComponent::from_legacy_string(&player_login_event_data.kick_message),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::AsyncPlayerPreLogin(player_pre_login_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_pre_login_event_data.player_uuid?.value).ok()?;
                    let pumpkin_event = PlayerPreLoginEvent::new(
                        player_pre_login_event_data.name,
                        uuid,
                        player_pre_login_event_data.address,
                        player_pre_login_event_data.result,
                        player_pre_login_event_data.kick_message,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerLeave(player_leave_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_leave_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerLeaveEvent::new(
                        player,
                        TextComponent::from_legacy_string(&player_leave_event_data.leave_message),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerMove(player_move_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_move_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let from = player_move_event_data.from?.position?;
                    let to = player_move_event_data.to?.position?;
                    let pumpkin_event = PlayerMoveEvent::new(
                        player,
                        Vector3::new(from.x, from.y, from.z),
                        Vector3::new(to.x, to.y, to.z),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerAdvancementDone(player_advancement_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_advancement_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerAdvancementDoneEvent::new(
                        player,
                        player_advancement_event_data.advancement_key,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerAnimation(player_animation_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_animation_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerAnimationEvent::new(
                        player,
                        player_animation_event_data.animation_type,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerArmorStandManipulate(player_armor_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_armor_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let armor_uuid = uuid::Uuid::parse_str(
                        &player_armor_event_data.armor_stand_uuid?.value,
                    )
                    .ok()?;
                    let pumpkin_event = PlayerArmorStandManipulateEvent::new(
                        player,
                        armor_uuid,
                        player_armor_event_data.item_key,
                        player_armor_event_data.armor_stand_item_key,
                        player_armor_event_data.slot,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBedEnter(player_bed_enter_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bed_enter_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bed_enter_event_data
                        .bed_location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let pumpkin_event = PlayerBedEnterEvent::new(player, position);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBedLeave(player_bed_leave_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bed_leave_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bed_leave_event_data
                        .bed_location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let pumpkin_event = PlayerBedLeaveEvent::new(player, position);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBucketEmpty(player_bucket_empty_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bucket_empty_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bucket_empty_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let face = bukkit_block_face_to_direction(
                        &player_bucket_empty_event_data.block_face,
                    );
                    let pumpkin_event = PlayerBucketEmptyEvent::new(
                        player,
                        position,
                        player_bucket_empty_event_data.block_key,
                        face,
                        player_bucket_empty_event_data.bucket_item_key,
                        player_bucket_empty_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBucketFill(player_bucket_fill_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bucket_fill_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let position = player_bucket_fill_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let face = bukkit_block_face_to_direction(
                        &player_bucket_fill_event_data.block_face,
                    );
                    let pumpkin_event = PlayerBucketFillEvent::new(
                        player,
                        position,
                        player_bucket_fill_event_data.block_key,
                        face,
                        player_bucket_fill_event_data.bucket_item_key,
                        player_bucket_fill_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerBucketEntity(player_bucket_entity_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_bucket_entity_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let entity_uuid =
                        uuid::Uuid::parse_str(&player_bucket_entity_event_data.entity_uuid?.value).ok()?;
                    let pumpkin_event = PlayerBucketEntityEvent::new(
                        player,
                        entity_uuid,
                        player_bucket_entity_event_data.entity_type,
                        player_bucket_entity_event_data.original_bucket_key,
                        player_bucket_entity_event_data.entity_bucket_key,
                        player_bucket_entity_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChangedMainHand(player_changed_main_hand_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_changed_main_hand_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let main_hand = main_hand_from_bukkit(
                        &player_changed_main_hand_event_data.main_hand,
                    )
                    .unwrap_or(Hand::Right);
                    let pumpkin_event = PlayerChangedMainHandEvent::new(player, main_hand);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerRegisterChannel(player_register_channel_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_register_channel_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerRegisterChannelEvent::new(
                        player,
                        player_register_channel_event_data.channel,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerUnregisterChannel(player_unregister_channel_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_unregister_channel_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerUnregisterChannelEvent::new(
                        player,
                        player_unregister_channel_event_data.channel,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerTeleport(player_teleport_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_teleport_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let from = player_teleport_event_data.from?.position?;
                    let to = player_teleport_event_data.to?.position?;
                    let pumpkin_event = PlayerTeleportEvent::new(
                        player,
                        Vector3::new(from.x, from.y, from.z),
                        Vector3::new(to.x, to.y, to.z),
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChangeWorld(player_change_world_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_change_world_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let prev_uuid = uuid::Uuid::parse_str(
                        &player_change_world_event_data.previous_world?.uuid?.value,
                    )
                    .ok()?;
                    let new_uuid = uuid::Uuid::parse_str(
                        &player_change_world_event_data.new_world?.uuid?.value,
                    )
                    .ok()?;
                    let prev_world = context
                        .server
                        .worlds
                        .load()
                        .iter()
                        .find(|world| world.uuid == prev_uuid)?
                        .clone();
                    let new_world = context
                        .server
                        .worlds
                        .load()
                        .iter()
                        .find(|world| world.uuid == new_uuid)?
                        .clone();
                    let position = player_change_world_event_data
                        .position
                        .and_then(|loc| loc.position)
                        .map(|pos| Vector3::new(pos.x, pos.y, pos.z))
                        .unwrap_or_else(|| player.position());
                    let pumpkin_event = PlayerChangeWorldEvent::new(
                        player,
                        prev_world,
                        new_world,
                        position,
                        player_change_world_event_data.yaw,
                        player_change_world_event_data.pitch,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerGamemodeChange(player_gamemode_change_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_gamemode_change_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let prev = gamemode_from_bukkit(&player_gamemode_change_event_data.previous_gamemode)
                        .unwrap_or(player.gamemode.load());
                    let next = gamemode_from_bukkit(&player_gamemode_change_event_data.new_gamemode)
                        .unwrap_or(player.gamemode.load());
                    let pumpkin_event = PlayerGamemodeChangeEvent::new(player, prev, next);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerChat(player_chat_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_chat_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let mut recipients = Vec::new();
                    if player_chat_event_data.recipients.is_empty() {
                        recipients = context.server.get_all_players();
                    } else {
                        for recipient in player_chat_event_data.recipients {
                            if let Ok(uuid) = uuid::Uuid::parse_str(&recipient.value) {
                                if let Some(recipient_player) =
                                    context.server.get_player_by_uuid(uuid)
                                {
                                    recipients.push(recipient_player);
                                }
                            }
                        }
                    }

                    let pumpkin_event = PlayerChatEvent::new(
                        player,
                        player_chat_event_data.message,
                        recipients,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerCommand(player_command_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_command_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event =
                        PlayerCommandPreprocessEvent::new(player, player_command_event_data.command);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerCommandSend(player_command_send_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_command_send_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event =
                        PlayerCommandSendEvent::new(player, player_command_send_event_data.commands);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerDropItem(player_drop_item_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_drop_item_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let item_uuid =
                        uuid::Uuid::parse_str(&player_drop_item_event_data.item_uuid?.value).ok()?;
                    let item_stack = item_stack_from_key(
                        &player_drop_item_event_data.item_key,
                        player_drop_item_event_data.item_amount,
                    );
                    let pumpkin_event =
                        PlayerDropItemEvent::new(player, item_uuid, item_stack);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerEditBook(player_edit_book_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_edit_book_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let title = if player_edit_book_event_data.title.is_empty() {
                        None
                    } else {
                        Some(player_edit_book_event_data.title)
                    };
                    let pumpkin_event = PlayerEditBookEvent::new(
                        player,
                        player_edit_book_event_data.slot,
                        player_edit_book_event_data.pages,
                        title,
                        player_edit_book_event_data.is_signing,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerEggThrow(player_egg_throw_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_egg_throw_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let egg_uuid =
                        uuid::Uuid::parse_str(&player_egg_throw_event_data.egg_uuid?.value).ok()?;
                    let pumpkin_event = PlayerEggThrowEvent::new(
                        player,
                        egg_uuid,
                        player_egg_throw_event_data.hatching,
                        player_egg_throw_event_data.num_hatches.min(u8::MAX as i32) as u8,
                        player_egg_throw_event_data.hatching_type,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerExpChange(player_exp_change_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_exp_change_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event =
                        PlayerExpChangeEvent::new(player, player_exp_change_event_data.amount);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerFish(player_fish_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_fish_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let caught_uuid = player_fish_event_data
                        .caught_uuid
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok());
                    let hook_uuid =
                        uuid::Uuid::parse_str(&player_fish_event_data.hook_uuid?.value).ok()?;
                    let pumpkin_event = PlayerFishEvent::new(
                        player,
                        caught_uuid,
                        hook_uuid,
                        player_fish_event_data.caught_type,
                        player_fish_event_data.state,
                        player_fish_event_data.hand,
                        player_fish_event_data.exp_to_drop,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerInteractEntity(player_interact_entity_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_interact_entity_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let entity_uuid =
                        uuid::Uuid::parse_str(&player_interact_entity_event_data.entity_uuid?.value).ok()?;
                    let pumpkin_event = PlayerInteractEntityEvent::new(
                        player,
                        entity_uuid,
                        player_interact_entity_event_data.entity_type,
                        player_interact_entity_event_data.hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerInteractAtEntity(player_interact_at_entity_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_interact_at_entity_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let entity_uuid =
                        uuid::Uuid::parse_str(&player_interact_at_entity_event_data.entity_uuid?.value).ok()?;
                    let position = player_interact_at_entity_event_data
                        .clicked_position
                        .map(|pos| Vector3::new(pos.x as f32, pos.y as f32, pos.z as f32))
                        .unwrap_or_else(|| Vector3::new(0.0, 0.0, 0.0));
                    let pumpkin_event = PlayerInteractAtEntityEvent::new(
                        player,
                        entity_uuid,
                        player_interact_at_entity_event_data.entity_type,
                        player_interact_at_entity_event_data.hand,
                        position,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerItemHeld(player_item_held_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_item_held_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerItemHeldEvent::new(
                        player,
                        player_item_held_event_data.previous_slot,
                        player_item_held_event_data.new_slot,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerItemDamage(player_item_damage_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_item_damage_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let key = if player_item_damage_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_item_damage_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_item_damage_event_data.item_amount);
                    let pumpkin_event = PlayerItemDamageEvent::new(
                        player,
                        item,
                        player_item_damage_event_data.damage,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerItemBreak(player_item_break_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_item_break_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let key = if player_item_break_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_item_break_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_item_break_event_data.item_amount);
                    let pumpkin_event = PlayerItemBreakEvent::new(player, item);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerItemConsume(player_item_consume_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_item_consume_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let key = if player_item_consume_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_item_consume_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_item_consume_event_data.item_amount);
                    let hand = if player_item_consume_event_data.hand == "OFF_HAND" {
                        Hand::Left
                    } else {
                        Hand::Right
                    };
                    let pumpkin_event = PlayerItemConsumeEvent::new(player, item, hand);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerItemMend(player_item_mend_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_item_mend_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let key = if player_item_mend_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_item_mend_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_item_mend_event_data.item_amount);
                    let slot = equipment_slot_from_bukkit(&player_item_mend_event_data.slot)
                        .unwrap_or(EquipmentSlot::MAIN_HAND);
                    let orb_uuid = player_item_mend_event_data
                        .orb_uuid
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok());
                    let pumpkin_event = PlayerItemMendEvent::new(
                        player,
                        item,
                        slot,
                        player_item_mend_event_data.repair_amount,
                        orb_uuid,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerLevelChange(player_level_change_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_level_change_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerLevelChangeEvent::new(
                        player,
                        player_level_change_event_data.old_level,
                        player_level_change_event_data.new_level,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerKick(player_kick_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_kick_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let reason = serde_json::from_str(&player_kick_event_data.reason)
                        .unwrap_or_else(|_| TextComponent::text(&player_kick_event_data.reason));
                    let leave_message = serde_json::from_str(&player_kick_event_data.leave_message)
                        .unwrap_or_else(|_| TextComponent::text(&player_kick_event_data.leave_message));
                    let cause = if player_kick_event_data.cause.is_empty() {
                        "UNKNOWN".to_string()
                    } else {
                        player_kick_event_data.cause
                    };
                    let pumpkin_event =
                        PlayerKickEvent::new(player, reason, leave_message, cause);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerToggleSneak(player_toggle_sneak_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_toggle_sneak_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerToggleSneakEvent::new(
                        player,
                        player_toggle_sneak_event_data.is_sneaking,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerToggleSprint(player_toggle_sprint_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_toggle_sprint_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerToggleSprintEvent::new(
                        player,
                        player_toggle_sprint_event_data.is_sprinting,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerToggleFlight(player_toggle_flight_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_toggle_flight_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerToggleFlightEvent::new(
                        player,
                        player_toggle_flight_event_data.is_flying,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerSwapHandItems(player_swap_hand_items_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_swap_hand_items_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let main_key = if player_swap_hand_items_event_data.main_hand_item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_swap_hand_items_event_data.main_hand_item_key.as_str()
                    };
                    let off_key = if player_swap_hand_items_event_data.off_hand_item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_swap_hand_items_event_data.off_hand_item_key.as_str()
                    };
                    let main_item = item_stack_from_key(
                        main_key,
                        player_swap_hand_items_event_data.main_hand_item_amount,
                    );
                    let off_item = item_stack_from_key(
                        off_key,
                        player_swap_hand_items_event_data.off_hand_item_amount,
                    );
                    let pumpkin_event = PlayerSwapHandItemsEvent::new(player, main_item, off_item);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerResourcePackStatus(player_resource_pack_status_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_resource_pack_status_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pack_uuid = player_resource_pack_status_event_data
                        .pack_uuid
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                        .unwrap_or_else(uuid::Uuid::new_v4);
                    let pumpkin_event = PlayerResourcePackStatusEvent::new(
                        player,
                        pack_uuid,
                        player_resource_pack_status_event_data.hash,
                        player_resource_pack_status_event_data.status,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerRespawn(player_respawn_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_respawn_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let (world_uuid, pos) = if let Some(loc) = player_respawn_event_data.respawn_location {
                        let world_uuid = loc
                            .world
                            .and_then(|w| w.uuid)
                            .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                            .unwrap_or_else(|| player.world().uuid);
                        let pos = location_to_vec3(loc).unwrap_or_else(|| player.position());
                        (world_uuid, pos)
                    } else {
                        (player.world().uuid, player.position())
                    };
                    let pumpkin_event = PlayerRespawnEvent::new(
                        player,
                        pos,
                        world_uuid,
                        player_respawn_event_data.is_bed_spawn,
                        player_respawn_event_data.is_anchor_spawn,
                        player_respawn_event_data.is_missing_respawn_block,
                        player_respawn_event_data.respawn_reason,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerPickupArrow(player_pickup_arrow_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_pickup_arrow_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let arrow_uuid = player_pickup_arrow_event_data
                        .arrow_uuid
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                        .unwrap_or_else(uuid::Uuid::new_v4);
                    let item_uuid = player_pickup_arrow_event_data
                        .item_uuid
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                        .unwrap_or_else(uuid::Uuid::new_v4);
                    let key = if player_pickup_arrow_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_pickup_arrow_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_pickup_arrow_event_data.item_amount);
                    let pumpkin_event = PlayerPickupArrowEvent::new(
                        player,
                        arrow_uuid,
                        item_uuid,
                        item,
                        player_pickup_arrow_event_data.remaining,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerPortal(player_portal_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_portal_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let (from_world_uuid, from_pos) = if let Some(loc) = player_portal_event_data.from {
                        let world_uuid = loc
                            .world
                            .and_then(|w| w.uuid)
                            .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                            .unwrap_or_else(|| player.world().uuid);
                        let pos = location_to_vec3(loc).unwrap_or_else(|| player.position());
                        (world_uuid, pos)
                    } else {
                        (player.world().uuid, player.position())
                    };
                    let (to_world_uuid, to_pos) = if let Some(loc) = player_portal_event_data.to {
                        let world_uuid = loc
                            .world
                            .and_then(|w| w.uuid)
                            .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                            .unwrap_or_else(|| player.world().uuid);
                        let pos = location_to_vec3(loc).unwrap_or_else(|| player.position());
                        (world_uuid, pos)
                    } else {
                        (player.world().uuid, player.position())
                    };
                    let cause = if player_portal_event_data.cause.is_empty() {
                        "UNKNOWN".to_string()
                    } else {
                        player_portal_event_data.cause
                    };
                    let pumpkin_event = PlayerPortalEvent::new(
                        player,
                        from_pos,
                        from_world_uuid,
                        to_pos,
                        to_world_uuid,
                        cause,
                        player_portal_event_data.search_radius,
                        player_portal_event_data.can_create_portal,
                        player_portal_event_data.creation_radius,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerRecipeDiscover(player_recipe_discover_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_recipe_discover_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerRecipeDiscoverEvent::new(
                        player,
                        player_recipe_discover_event_data.recipe_key,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerRiptide(player_riptide_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_riptide_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let key = if player_riptide_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_riptide_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_riptide_event_data.item_amount);
                    let velocity = player_riptide_event_data
                        .velocity
                        .map(|v| Vector3::new(v.x, v.y, v.z))
                        .unwrap_or_else(Vector3::default);
                    let pumpkin_event = PlayerRiptideEvent::new(player, item, velocity);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerShearEntity(player_shear_entity_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_shear_entity_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let entity_uuid = player_shear_entity_event_data
                        .entity_uuid
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                        .unwrap_or_else(uuid::Uuid::new_v4);
                    let key = if player_shear_entity_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_shear_entity_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(key, player_shear_entity_event_data.item_amount);
                    let hand = if player_shear_entity_event_data.hand.is_empty() {
                        "HAND".to_string()
                    } else {
                        player_shear_entity_event_data.hand
                    };
                    let entity_type = player_shear_entity_event_data.entity_type;
                    let pumpkin_event = PlayerShearEntityEvent::new(
                        player,
                        entity_uuid,
                        entity_type,
                        item,
                        hand,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerSpawnLocation(player_spawn_location_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_spawn_location_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let (world_uuid, pos) = if let Some(loc) = player_spawn_location_event_data.spawn_location {
                        let world_uuid = loc
                            .world
                            .and_then(|w| w.uuid)
                            .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                            .unwrap_or_else(|| player.world().uuid);
                        let pos = location_to_vec3(loc).unwrap_or_else(|| player.position());
                        (world_uuid, pos)
                    } else {
                        (player.world().uuid, player.position())
                    };
                    let pumpkin_event = PlayerSpawnLocationEvent::new(player, pos, world_uuid);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerStatisticIncrement(player_statistic_increment_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_statistic_increment_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let pumpkin_event = PlayerStatisticIncrementEvent::new(
                        player,
                        player_statistic_increment_event_data.statistic,
                        player_statistic_increment_event_data.initial_value,
                        player_statistic_increment_event_data.new_value,
                        player_statistic_increment_event_data.entity_type,
                        player_statistic_increment_event_data.material_key,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerVelocity(player_velocity_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_velocity_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let velocity = player_velocity_event_data
                        .velocity
                        .map(|v| Vector3::new(v.x, v.y, v.z))
                        .unwrap_or_else(Vector3::default);
                    let pumpkin_event = PlayerVelocityEvent::new(player, velocity);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerHarvestBlock(player_harvest_block_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_harvest_block_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let (world_uuid, pos) =
                        if let Some(loc) = player_harvest_block_event_data.block_location {
                            let world_uuid = loc
                                .world
                                .and_then(|w| w.uuid)
                                .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                                .unwrap_or_else(|| player.world().uuid);
                            let pos = location_to_vec3(loc).unwrap_or_else(|| player.position());
                            (world_uuid, pos)
                        } else {
                            (player.world().uuid, player.position())
                        };
                    let block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                    let key = if player_harvest_block_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        player_harvest_block_event_data.item_key.as_str()
                    };
                    let item = item_stack_from_key(
                        key,
                        player_harvest_block_event_data.item_amount,
                    );
                    let block_key = if player_harvest_block_event_data.block_key.is_empty() {
                        "minecraft:air".to_string()
                    } else {
                        player_harvest_block_event_data.block_key
                    };
                    let pumpkin_event = PlayerHarvestBlockEvent::new(
                        player,
                        block_pos,
                        block_key,
                        item,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::PlayerInteract(player_interact_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&player_interact_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let action = match player_interact_event_data.action.as_str() {
                        "LEFT_CLICK_BLOCK" => InteractAction::LeftClickBlock,
                        "LEFT_CLICK_AIR" => InteractAction::LeftClickAir,
                        "RIGHT_CLICK_BLOCK" => InteractAction::RightClickBlock,
                        "RIGHT_CLICK_AIR" => InteractAction::RightClickAir,
                        _ => InteractAction::RightClickAir,
                    };

                    let clicked_pos = player_interact_event_data
                        .clicked
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        });

                    let block = block_from_key(&player_interact_event_data.block_key);
                    let item = Arc::new(Mutex::new(item_from_key(
                        &player_interact_event_data.item_key,
                    )));
                    let face = bukkit_block_face_to_direction(
                        &player_interact_event_data.block_face,
                    );

                    let pumpkin_event = PlayerInteractEvent::new(
                        &player,
                        action,
                        &item,
                        player_interact_event_data.item_key,
                        block,
                        clicked_pos,
                        face,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockBreak(block_break_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_break_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid);
                    let block = block_from_key(&block_break_event_data.block_key);
                    let position = block_break_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| pumpkin_util::math::position::BlockPos::new(0, 0, 0));

                    let pumpkin_event = BlockBreakEvent::new(
                        player,
                        block,
                        position,
                        block_break_event_data.exp,
                        block_break_event_data.drop,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockDamage(block_damage_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_damage_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_damage_event_data.block_key);
                    let position = block_damage_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| player.position().to_block_pos());
                    let key = if block_damage_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        block_damage_event_data.item_key.as_str()
                    };
                    let item_stack = item_stack_from_key(key, 1);
                    let pumpkin_event = BlockDamageEvent::new(
                        player,
                        block,
                        position,
                        item_stack,
                        block_damage_event_data.insta_break,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockDamageAbort(block_damage_abort_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_damage_abort_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_damage_abort_event_data.block_key);
                    let position = block_damage_abort_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| player.position().to_block_pos());
                    let key = if block_damage_abort_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        block_damage_abort_event_data.item_key.as_str()
                    };
                    let item_stack = item_stack_from_key(key, 1);
                    let pumpkin_event =
                        BlockDamageAbortEvent::new(player, block, position, item_stack);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockDispense(block_dispense_event_data) => {
                    let block = block_from_key(&block_dispense_event_data.block_key);
                    let position = block_dispense_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| pumpkin_util::math::position::BlockPos::new(0, 0, 0));
                    let key = if block_dispense_event_data.item_key.is_empty() {
                        "minecraft:air"
                    } else {
                        block_dispense_event_data.item_key.as_str()
                    };
                    let item_stack =
                        item_stack_from_key(key, block_dispense_event_data.item_amount);
                    let velocity = block_dispense_event_data
                        .velocity
                        .map(|v| Vector3::new(v.x, v.y, v.z))
                        .unwrap_or_else(|| Vector3::new(0.0, 0.0, 0.0));
                    let pumpkin_event =
                        BlockDispenseEvent::new(block, position, item_stack, velocity);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockDropItem(block_drop_item_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_drop_item_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_drop_item_event_data.block_key);
                    let position = block_drop_item_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| player.position().to_block_pos());
                    let mut items = Vec::new();
                    for entry in block_drop_item_event_data.items {
                        if entry.item_key.is_empty() && entry.item_amount <= 0 {
                            continue;
                        }
                        let key = if entry.item_key.is_empty() {
                            "minecraft:air"
                        } else {
                            entry.item_key.as_str()
                        };
                        let stack = item_stack_from_key(key, entry.item_amount);
                        if !stack.is_empty() {
                            items.push(stack);
                        }
                    }
                    let pumpkin_event = BlockDropItemEvent::new(player, block, position, items);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockExplode(block_explode_event_data) => {
                    let block = block_from_key(&block_explode_event_data.block_key);
                    let position = block_explode_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| pumpkin_util::math::position::BlockPos::new(0, 0, 0));
                    let world_uuid = block_explode_event_data
                        .location
                        .and_then(|loc| loc.world)
                        .and_then(|w| w.uuid)
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                        .unwrap_or_else(|| {
                            context
                                .server
                                .worlds
                                .load()
                                .first()
                                .map(|world| world.uuid)
                                .unwrap_or_default()
                        });
                    let mut blocks = Vec::new();
                    for entry in block_explode_event_data.blocks {
                        if let Some(loc) = entry.location
                            && let Some(pos) = location_to_vec3(loc.clone())
                        {
                            blocks.push(pumpkin_util::math::position::BlockPos::new(
                                pos.x.floor() as i32,
                                pos.y.floor() as i32,
                                pos.z.floor() as i32,
                            ));
                        }
                    }
                    let pumpkin_event = BlockExplodeEvent::new(
                        block,
                        position,
                        world_uuid,
                        blocks,
                        block_explode_event_data.yield_,
                    );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockFade(block_fade_event_data) => {
                    let block = block_from_key(&block_fade_event_data.block_key);
                    let new_block = block_from_key(&block_fade_event_data.new_block_key);
                    let position = block_fade_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| pumpkin_util::math::position::BlockPos::new(0, 0, 0));
                    let world_uuid = block_fade_event_data
                        .location
                        .and_then(|loc| loc.world)
                        .and_then(|w| w.uuid)
                        .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                        .unwrap_or_else(|| {
                            context
                                .server
                                .worlds
                                .load()
                                .first()
                                .map(|world| world.uuid)
                                .unwrap_or_default()
                        });
                    let pumpkin_event = BlockFadeEvent::new(block, new_block, position, world_uuid);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockFertilize(block_fertilize_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_fertilize_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_fertilize_event_data.block_key);
                    let position = block_fertilize_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| player.position().to_block_pos());
                    let mut blocks = Vec::new();
                    for entry in block_fertilize_event_data.blocks {
                        if let Some(loc) = entry.location
                            && let Some(pos) = location_to_vec3(loc.clone())
                        {
                            blocks.push(pumpkin_util::math::position::BlockPos::new(
                                pos.x.floor() as i32,
                                pos.y.floor() as i32,
                                pos.z.floor() as i32,
                            ));
                        }
                    }
                    let pumpkin_event =
                        BlockFertilizeEvent::new(player, block, position, blocks);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockPlace(block_place_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&block_place_event_data.player_uuid?.value)
                        .ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_place_event_data.block_key);
                    let against = block_from_key(&block_place_event_data.block_against_key);
                    let position = block_place_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| player.position().to_block_pos());

                    let pumpkin_event = BlockPlaceEvent {
                        player,
                        block_placed: block,
                        block_placed_against: against,
                        position,
                        can_build: block_place_event_data.can_build,
                        cancelled: false,
                    };

                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockCanBuild(block_can_build_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&block_can_build_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block_to_build = block_from_key(&block_can_build_event_data.block_key);
                    let block_against = block_from_key(&block_can_build_event_data.block_against_key);
                    let position = block_can_build_event_data
                        .location
                        .and_then(|loc| loc.position)
                        .map(|pos| {
                            pumpkin_util::math::position::BlockPos::new(
                                pos.x as i32,
                                pos.y as i32,
                                pos.z as i32,
                            )
                        })
                        .unwrap_or_else(|| player.position().to_block_pos());
                    let pumpkin_event = BlockCanBuildEvent {
                        player,
                        block_to_build,
                        buildable: block_can_build_event_data.can_build,
                        block: block_against,
                        block_pos: position,
                        cancelled: false,
                    };
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockBurn(block_burn_event_data) => {
                    let block = block_from_key(&block_burn_event_data.block_key);
                    let igniting_block =
                        block_from_key(&block_burn_event_data.igniting_block_key);
                    let (world_uuid, pos) = if let Some(loc) = block_burn_event_data.location {
                        let world_uuid = loc
                            .world
                            .and_then(|w| w.uuid)
                            .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                            .unwrap_or_else(|| {
                                context
                                    .server
                                    .worlds
                                    .load()
                                    .first()
                                    .map(|w| w.uuid)
                                    .unwrap_or_else(uuid::Uuid::new_v4)
                            });
                        let pos = location_to_vec3(loc).unwrap_or_else(Vector3::default);
                        (world_uuid, pos)
                    } else {
                        (
                            context
                                .server
                                .worlds
                                .load()
                                .first()
                                .map(|w| w.uuid)
                                .unwrap_or_else(uuid::Uuid::new_v4),
                            Vector3::default(),
                        )
                    };
                    let block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                    let pumpkin_event = BlockBurnEvent {
                        igniting_block,
                        block,
                        block_pos,
                        world_uuid,
                        cancelled: false,
                    };
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockIgnite(block_ignite_event_data) => {
                    let uuid =
                        uuid::Uuid::parse_str(&block_ignite_event_data.player_uuid?.value).ok()?;
                    let player = context.server.get_player_by_uuid(uuid)?;
                    let block = block_from_key(&block_ignite_event_data.block_key);
                    let igniting_block =
                        block_from_key(&block_ignite_event_data.igniting_block_key);
                    let (world_uuid, pos) = if let Some(loc) = block_ignite_event_data.location {
                        let world_uuid = loc
                            .world
                            .and_then(|w| w.uuid)
                            .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                            .unwrap_or_else(|| player.world().uuid);
                        let pos = location_to_vec3(loc).unwrap_or_else(Vector3::default);
                        (world_uuid, pos)
                    } else {
                        (player.world().uuid, Vector3::default())
                    };
                    let block_pos = pumpkin_util::math::position::BlockPos::new(
                        pos.x.floor() as i32,
                        pos.y.floor() as i32,
                        pos.z.floor() as i32,
                    );
                    let cause = if block_ignite_event_data.cause.is_empty() {
                        "UNKNOWN".to_string()
                    } else {
                        block_ignite_event_data.cause
                    };
                    let pumpkin_event = BlockIgniteEvent {
                        player,
                        block,
                        igniting_block,
                        block_pos,
                        world_uuid,
                        cause,
                        cancelled: false,
                    };
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::BlockSpread(block_spread_event_data) => {
                    let source_block = block_from_key(&block_spread_event_data.source_block_key);
                    let block = block_from_key(&block_spread_event_data.block_key);
                    let (world_uuid, source_pos) =
                        if let Some(loc) = block_spread_event_data.source_location {
                            let world_uuid = loc
                                .world
                                .and_then(|w| w.uuid)
                                .and_then(|uuid| uuid::Uuid::parse_str(&uuid.value).ok())
                                .unwrap_or_else(|| {
                                    context
                                        .server
                                        .worlds
                                        .load()
                                        .first()
                                        .map(|w| w.uuid)
                                        .unwrap_or_else(uuid::Uuid::new_v4)
                                });
                            let pos = location_to_vec3(loc).unwrap_or_else(Vector3::default);
                            (
                                world_uuid,
                                pumpkin_util::math::position::BlockPos::new(
                                    pos.x.floor() as i32,
                                    pos.y.floor() as i32,
                                    pos.z.floor() as i32,
                                ),
                            )
                        } else {
                            (
                                context
                                    .server
                                    .worlds
                                    .load()
                                    .first()
                                    .map(|w| w.uuid)
                                    .unwrap_or_else(uuid::Uuid::new_v4),
                                pumpkin_util::math::position::BlockPos::new(0, 0, 0),
                            )
                        };
                    let block_pos = if let Some(loc) = block_spread_event_data.location {
                        let pos = location_to_vec3(loc).unwrap_or_else(Vector3::default);
                        pumpkin_util::math::position::BlockPos::new(
                            pos.x.floor() as i32,
                            pos.y.floor() as i32,
                            pos.z.floor() as i32,
                        )
                    } else {
                        source_pos
                    };
                    let pumpkin_event = BlockSpreadEvent {
                        source_block,
                        source_pos,
                        block,
                        block_pos,
                        world_uuid,
                        cancelled: false,
                    };
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::EntitySpawn(entity_spawn_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&entity_spawn_event_data.entity_uuid?.value)
                        .ok()?;
                    let pumpkin_event =
                        pumpkin::plugin::entity::entity_spawn::EntitySpawnEvent::new(
                            uuid,
                            &pumpkin_data::entity::EntityType::PLAYER,
                        );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::EntityDamage(entity_damage_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&entity_damage_event_data.entity_uuid?.value)
                        .ok()?;
                    let pumpkin_event =
                        pumpkin::plugin::entity::entity_damage::EntityDamageEvent::new(
                            uuid,
                            entity_damage_event_data.damage,
                            pumpkin_data::damage::DamageType::GENERIC,
                        );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::EntityDeath(entity_death_event_data) => {
                    let uuid = uuid::Uuid::parse_str(&entity_death_event_data.entity_uuid?.value)
                        .ok()?;
                    let pumpkin_event =
                        pumpkin::plugin::entity::entity_death::EntityDeathEvent::new(
                            uuid,
                            pumpkin_data::damage::DamageType::GENERIC,
                        );
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::ServerCommand(server_command_event_data) => {
                    let pumpkin_event =
                        ServerCommandEvent::new(server_command_event_data.command);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
                Data::ServerBroadcast(server_broadcast_event_data) => {
                    let message = serde_json::from_str(&server_broadcast_event_data.message)
                        .unwrap_or_else(|_| {
                            TextComponent::from_legacy_string(&server_broadcast_event_data.message)
                        });
                    let sender = serde_json::from_str(&server_broadcast_event_data.sender)
                        .unwrap_or_else(|_| {
                            TextComponent::from_legacy_string(&server_broadcast_event_data.sender)
                        });
                    let pumpkin_event = ServerBroadcastEvent::new(message, sender);
                    context.server.plugin_manager.fire(pumpkin_event).await;
                    Some(true)
                }
            }
        })
    })?;

    Some(CallEventResponse { handled })
}

fn block_from_key(key: &str) -> &'static Block {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    Block::from_registry_key(trimmed).unwrap_or(&Block::AIR)
}

fn item_from_key(key: &str) -> ItemStack {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    let item = Item::from_registry_key(trimmed).unwrap_or(&Item::AIR);
    ItemStack::new(1, item)
}

fn item_stack_from_key(key: &str, amount: i32) -> ItemStack {
    let trimmed = key.strip_prefix("minecraft:").unwrap_or(key);
    let item = Item::from_registry_key(trimmed).unwrap_or(&Item::AIR);
    let count = amount.clamp(0, u8::MAX as i32) as u8;
    ItemStack::new(count, item)
}

fn equipment_slot_from_bukkit(slot: &str) -> Option<EquipmentSlot> {
    match slot {
        "HAND" => Some(EquipmentSlot::MAIN_HAND),
        "OFF_HAND" => Some(EquipmentSlot::OFF_HAND),
        "FEET" => Some(EquipmentSlot::FEET),
        "LEGS" => Some(EquipmentSlot::LEGS),
        "CHEST" => Some(EquipmentSlot::CHEST),
        "HEAD" => Some(EquipmentSlot::HEAD),
        "BODY" => Some(EquipmentSlot::BODY),
        "SADDLE" => Some(EquipmentSlot::SADDLE),
        _ => None,
    }
}

fn bukkit_block_face_to_direction(face: &str) -> Option<BlockDirection> {
    match face {
        "UP" => Some(BlockDirection::Up),
        "DOWN" => Some(BlockDirection::Down),
        "NORTH" => Some(BlockDirection::North),
        "SOUTH" => Some(BlockDirection::South),
        "WEST" => Some(BlockDirection::West),
        "EAST" => Some(BlockDirection::East),
        _ => None,
    }
}

fn gamemode_from_bukkit(mode: &str) -> Option<pumpkin_util::GameMode> {
    match mode {
        "SURVIVAL" => Some(pumpkin_util::GameMode::Survival),
        "CREATIVE" => Some(pumpkin_util::GameMode::Creative),
        "ADVENTURE" => Some(pumpkin_util::GameMode::Adventure),
        "SPECTATOR" => Some(pumpkin_util::GameMode::Spectator),
        _ => None,
    }
}

fn main_hand_from_bukkit(hand: &str) -> Option<Hand> {
    match hand {
        "LEFT" => Some(Hand::Left),
        "RIGHT" => Some(Hand::Right),
        _ => None,
    }
}
