package org.patchbukkit.events;

import com.google.protobuf.InvalidProtocolBufferException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import patchbukkit.common.UUID;
import patchbukkit.events.*;

import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PatchBukkitEventFactory {
    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final java.util.Map<java.util.UUID, String> PRELOGIN_PLAYER_NAMES =
        new java.util.concurrent.ConcurrentHashMap<>();

    @Nullable
    public static org.bukkit.event.Event createEventFromBytes(byte[] data) {
        try {
            Event event = Event.parseFrom(data);
            return createEvent(event);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.log(Level.SEVERE, "Failed to parse Event", e);
            return null;
        }
    }

    public static byte[] fireEventFromBytes(byte[] data, String pluginName) {
        synchronized (org.patchbukkit.bootstrap.SchedulerBridge.class) {
        Boolean prevThreadState = org.patchbukkit.bootstrap.HeadlessPaperServer.enterPrimaryThread();
        try {
            org.bukkit.event.Event event = createEventFromBytes(data);
            if (event == null) {
                return FireEventResponse.newBuilder().setCancelled(false).build().toByteArray();
            }
            if (event.isAsynchronous()) {
                org.patchbukkit.bootstrap.HeadlessPaperServer.enterAsyncThread();
            }
            if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                if (event instanceof com.destroystokyo.paper.event.server.ServerTickStartEvent) {
                    org.patchbukkit.bootstrap.SchedulerBridge.onServerTick();
                }
                if (event instanceof org.bukkit.event.player.AsyncPlayerChatEvent apce) {
                    dispatchPaperAsyncChatEvent(apce, pluginName);
                } else if (event instanceof org.bukkit.event.player.PlayerAnimationEvent pae) {
                    dispatchPaperPlayerArmSwingEvent(pae, pluginName);
                } else if (event instanceof org.bukkit.event.player.PlayerMoveEvent pme) {
                    dispatchPaperPlayerJumpEvent(pme, pluginName);
                }
                for (org.bukkit.plugin.RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
                    if (pluginName != null && !pluginName.isEmpty() && !listener.getPlugin().getName().equals(pluginName)) {
                        continue;
                    }
                    if (!listener.getPlugin().isEnabled()) continue;
                    try {
                        listener.callEvent(event);
                    } catch (Throwable ex) {
                        LOGGER.log(Level.SEVERE, "Could not pass event " + event.getEventName() + " to " + listener.getPlugin().getName(), ex);
                    }
                }
                if (event instanceof org.bukkit.event.player.PlayerQuitEvent pqe) {
                    org.patchbukkit.network.VirtualChannelManager.getInstance().removePlayer(pqe.getPlayer().getUniqueId());
                } else if (event instanceof org.bukkit.event.player.PlayerLoginEvent
                        || event instanceof org.bukkit.event.player.PlayerJoinEvent
                        || event instanceof com.destroystokyo.paper.event.server.ServerTickStartEvent) {
                    org.patchbukkit.network.VirtualChannelManager.getInstance().refreshPacketInterceptionState();
                } else if (event instanceof org.bukkit.event.inventory.InventoryClickEvent ice) {
                    if (ice.getWhoClicked() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
                        var handle = cp.getHandle();
                        if (handle.containerMenu != handle.inventoryMenu && ice.isCancelled()) {
                            try {
                                handle.containerMenu.sendAllDataToRemote();
                            } catch (Throwable ignored) {}
                        }
                    }
                } else if (event instanceof org.bukkit.event.inventory.InventoryCloseEvent iclose) {
                    if (iclose.getPlayer() instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
                        var handle = cp.getHandle();
                        if (handle.containerMenu != handle.inventoryMenu) {
                            handle.containerMenu = handle.inventoryMenu;
                        }
                    }
                }
            }
            return toFireEventResponse(event);
        } finally {
            org.patchbukkit.bootstrap.HeadlessPaperServer.restoreThreadState(prevThreadState);
        }
        }
    }

    private static void dispatchPaperAsyncChatEvent(
        org.bukkit.event.player.AsyncPlayerChatEvent apce,
        String pluginName
    ) {
        try {
            org.bukkit.plugin.RegisteredListener[] paperListeners =
                io.papermc.paper.event.player.AsyncChatEvent.getHandlerList().getRegisteredListeners();
            if (paperListeners.length == 0) return;

            Component initialMsg = Component.text(apce.getMessage());
            io.papermc.paper.event.player.AsyncChatEvent paperChat =
                new io.papermc.paper.event.player.AsyncChatEvent(
                    true,
                    apce.getPlayer(),
                    new java.util.HashSet<>(apce.getRecipients()),
                    io.papermc.paper.chat.ChatRenderer.defaultRenderer(),
                    initialMsg,
                    initialMsg,
                    net.kyori.adventure.chat.SignedMessage.system(apce.getMessage(), initialMsg)
                );

            for (org.bukkit.plugin.RegisteredListener listener : paperListeners) {
                if (pluginName != null && !pluginName.isEmpty() && !listener.getPlugin().getName().equals(pluginName)) {
                    continue;
                }
                if (!listener.getPlugin().isEnabled()) continue;
                try {
                    listener.callEvent(paperChat);
                } catch (Throwable ex) {
                    LOGGER.log(Level.SEVERE, "Could not pass event AsyncChatEvent to " + listener.getPlugin().getName(), ex);
                }
            }

            if (paperChat.isCancelled()) {
                apce.setCancelled(true);
            }
            if (!initialMsg.equals(paperChat.message())) {
                apce.setMessage(
                    net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .serialize(paperChat.message())
                );
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Failed to dispatch Paper AsyncChatEvent", t);
        }
    }

    private static void dispatchPaperPlayerArmSwingEvent(
        org.bukkit.event.player.PlayerAnimationEvent pae,
        String pluginName
    ) {
        try {
            org.bukkit.plugin.RegisteredListener[] paperListeners =
                io.papermc.paper.event.player.PlayerArmSwingEvent.getHandlerList().getRegisteredListeners();
            if (paperListeners.length == 0) return;

            org.bukkit.inventory.EquipmentSlot hand =
                pae.getAnimationType() == org.bukkit.event.player.PlayerAnimationType.OFF_ARM_SWING
                    ? org.bukkit.inventory.EquipmentSlot.OFF_HAND
                    : org.bukkit.inventory.EquipmentSlot.HAND;
            io.papermc.paper.event.player.PlayerArmSwingEvent swingEvent =
                new io.papermc.paper.event.player.PlayerArmSwingEvent(pae.getPlayer(), hand);

            for (org.bukkit.plugin.RegisteredListener listener : paperListeners) {
                if (pluginName != null && !pluginName.isEmpty() && !listener.getPlugin().getName().equals(pluginName)) {
                    continue;
                }
                if (!listener.getPlugin().isEnabled()) continue;
                try {
                    listener.callEvent(swingEvent);
                } catch (Throwable ex) {
                    LOGGER.log(Level.SEVERE, "Could not pass event PlayerArmSwingEvent to " + listener.getPlugin().getName(), ex);
                }
            }
            if (swingEvent.isCancelled()) {
                pae.setCancelled(true);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Failed to dispatch Paper PlayerArmSwingEvent", t);
        }
    }

    private static void dispatchPaperPlayerJumpEvent(
        org.bukkit.event.player.PlayerMoveEvent pme,
        String pluginName
    ) {
        try {
            if (pme.getTo() == null || pme.getTo().getY() - pme.getFrom().getY() <= 0.1) return;
            org.bukkit.plugin.RegisteredListener[] paperListeners =
                com.destroystokyo.paper.event.player.PlayerJumpEvent.getHandlerList().getRegisteredListeners();
            if (paperListeners.length == 0) return;

            com.destroystokyo.paper.event.player.PlayerJumpEvent jumpEvent =
                new com.destroystokyo.paper.event.player.PlayerJumpEvent(
                    pme.getPlayer(),
                    pme.getFrom().clone(),
                    pme.getTo().clone()
                );

            for (org.bukkit.plugin.RegisteredListener listener : paperListeners) {
                if (pluginName != null && !pluginName.isEmpty() && !listener.getPlugin().getName().equals(pluginName)) {
                    continue;
                }
                if (!listener.getPlugin().isEnabled()) continue;
                try {
                    listener.callEvent(jumpEvent);
                } catch (Throwable ex) {
                    LOGGER.log(Level.SEVERE, "Could not pass event PlayerJumpEvent to " + listener.getPlugin().getName(), ex);
                }
            }
            if (jumpEvent.isCancelled()) {
                pme.setCancelled(true);
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "Failed to dispatch Paper PlayerJumpEvent", t);
        }
    }

    @Nullable
    public static org.bukkit.event.Event createEvent(@NotNull Event event) {
        try {
            Event.DataCase dataCase = event.getDataCase();

            return switch (dataCase) {
            case ASYNC_STRUCTURE_GENERATE -> {
                var ev = event.getAsyncStructureGenerate();
                yield createGenericBukkitEvent("org.bukkit.event.world.AsyncStructureGenerateEvent", ev);
            }
            case ASYNC_STRUCTURE_SPAWN -> {
                var ev = event.getAsyncStructureSpawn();
                yield createGenericBukkitEvent("org.bukkit.event.world.AsyncStructureSpawnEvent", ev);
            }
            case CHUNK_LOAD -> {
                var ev = event.getChunkLoad();
                yield createGenericBukkitEvent("org.bukkit.event.world.ChunkLoadEvent", ev);
            }
            case CHUNK_POPULATE -> {
                var ev = event.getChunkPopulate();
                yield createGenericBukkitEvent("org.bukkit.event.world.ChunkPopulateEvent", ev);
            }
            case CHUNK_SAVE -> {
                var ev = event.getChunkSave();
                yield createGenericBukkitEvent("org.bukkit.event.world.ChunkUnloadEvent", ev);
            }
            case CHUNK_SEND -> {
                var ev = event.getChunkSend();
                yield createGenericBukkitEvent("org.bukkit.event.world.ChunkLoadEvent", ev);
            }
            case CHUNK_UNLOAD -> {
                var ev = event.getChunkUnload();
                yield createGenericBukkitEvent("org.bukkit.event.world.ChunkUnloadEvent", ev);
            }
            case ENTITIES_LOAD -> {
                var ev = event.getEntitiesLoad();
                yield createGenericBukkitEvent("org.bukkit.event.world.EntitiesLoadEvent", ev);
            }
            case ENTITIES_UNLOAD -> {
                var ev = event.getEntitiesUnload();
                yield createGenericBukkitEvent("org.bukkit.event.world.EntitiesUnloadEvent", ev);
            }
            case GENERIC_GAME -> {
                var ev = event.getGenericGame();
                yield createGenericBukkitEvent("org.bukkit.event.world.GenericGameEvent", ev);
            }
            case LIGHTNING_STRIKE -> {
                var ev = event.getLightningStrike();
                yield createGenericBukkitEvent("org.bukkit.event.weather.LightningStrikeEvent", ev);
            }
            case LOOT_GENERATE -> {
                var ev = event.getLootGenerate();
                yield createGenericBukkitEvent("org.bukkit.event.world.LootGenerateEvent", ev);
            }
            case PORTAL_CREATE -> {
                var ev = event.getPortalCreate();
                yield createGenericBukkitEvent("org.bukkit.event.world.PortalCreateEvent", ev);
            }
            case SPAWN_CHANGE -> {
                var ev = event.getSpawnChange();
                yield createGenericBukkitEvent("org.bukkit.event.world.SpawnChangeEvent", ev);
            }
            case STRUCTURE_GROW -> {
                var ev = event.getStructureGrow();
                yield createGenericBukkitEvent("org.bukkit.event.world.StructureGrowEvent", ev);
            }
            case TIME_SKIP -> {
                var ev = event.getTimeSkip();
                yield createGenericBukkitEvent("org.bukkit.event.world.TimeSkipEvent", ev);
            }
            case WEATHER_CHANGE -> {
                var ev = event.getWeatherChange();
                World w = ev.hasWorldUuid() ? getWorld(ev.getWorldUuid().getValue()) : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) yield null;
                yield new org.bukkit.event.weather.WeatherChangeEvent(w, ev.getToWeatherState(), org.bukkit.event.weather.WeatherChangeEvent.Cause.PLUGIN);
            }
            case THUNDER_CHANGE -> {
                var ev = event.getThunderChange();
                World w = ev.hasWorldUuid() ? getWorld(ev.getWorldUuid().getValue()) : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) yield null;
                yield new org.bukkit.event.weather.ThunderChangeEvent(w, ev.getToThunderState(), org.bukkit.event.weather.ThunderChangeEvent.Cause.PLUGIN);
            }
            case WORLD_INIT -> {
                var ev = event.getWorldInit();
                World w = ev.hasWorldUuid() ? getWorld(ev.getWorldUuid().getValue()) : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) yield null;
                yield new org.bukkit.event.world.WorldInitEvent(w);
            }
            case WORLD_LOAD -> {
                var ev = event.getWorldLoad();
                World w = ev.hasWorldUuid() ? getWorld(ev.getWorldUuid().getValue()) : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) yield null;
                yield new org.bukkit.event.world.WorldLoadEvent(w);
            }
            case WORLD_UNLOAD -> {
                var ev = event.getWorldUnload();
                World w = ev.hasWorldUuid() ? getWorld(ev.getWorldUuid().getValue()) : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) yield null;
                yield new org.bukkit.event.world.WorldUnloadEvent(w);
            }
            case WORLD_SAVE -> {
                var ev = event.getWorldSave();
                World w = !ev.getWorldName().isEmpty() ? Bukkit.getWorld(ev.getWorldName()) : null;
                if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
                if (w == null) yield null;
                yield new org.bukkit.event.world.WorldSaveEvent(w);
            }
            case SERVER_LIST_PING -> {
                var ev = event.getServerListPing();
                Component motd = ev.getMotd().isEmpty() ? Component.empty() : parseJsonOrPlain(ev.getMotd());
                yield new org.bukkit.event.server.ServerListPingEvent(
                    "",
                    new java.net.InetSocketAddress(java.net.InetAddress.getLoopbackAddress(), 0),
                    motd,
                    ev.getNumPlayers(),
                    ev.getMaxPlayers()
                );
            }
            case MAP_INITIALIZE -> {
                var ev = event.getMapInitialize();
                yield createGenericBukkitEvent("org.bukkit.event.server.MapInitializeEvent", ev);
            }
            case PACKET_RECEIVED -> {
                var ev = event.getPacketReceived();
                if (ev.hasPlayerUuid()) {
                    java.util.UUID uuid = java.util.UUID.fromString(ev.getPlayerUuid().getValue());
                    org.patchbukkit.network.VirtualChannelManager.getInstance().handlePacketReceived(uuid, ev.getPacketId(), ev.getPayload().toByteArray());
                }
                yield null;
            }
            case PACKET_SENT -> {
                var ev = event.getPacketSent();
                if (ev.hasPlayerUuid()) {
                    java.util.UUID uuid = java.util.UUID.fromString(ev.getPlayerUuid().getValue());
                    org.patchbukkit.network.VirtualChannelManager.getInstance().handlePacketSent(uuid, ev.getPacketId(), ev.getPayload().toByteArray());
                }
                yield null;
            }
            case PLUGIN_DISABLE -> {
                var ev = event.getPluginDisable();
                var plugin = Bukkit.getPluginManager().getPlugin(ev.getPluginName());
                yield plugin != null ? new org.bukkit.event.server.PluginDisableEvent(plugin) : null;
            }
            case PLUGIN_ENABLE -> {
                var ev = event.getPluginEnable();
                var plugin = Bukkit.getPluginManager().getPlugin(ev.getPluginName());
                yield plugin != null ? new org.bukkit.event.server.PluginEnableEvent(plugin) : null;
            }
            case REMOTE_SERVER_COMMAND -> {
                var ev = event.getRemoteServerCommand();
                yield createGenericBukkitEvent("org.bukkit.event.server.RemoteServerCommandEvent", ev);
            }
            case SERVER_BROADCAST -> {
                var ev = event.getServerBroadcast();
                yield createGenericBukkitEvent("org.bukkit.event.server.BroadcastMessageEvent", ev);
            }
            case SERVER_COMMAND -> {
                var ev = event.getServerCommand();
                yield new org.bukkit.event.server.ServerCommandEvent(Bukkit.getConsoleSender(), ev.getCommand());
            }
            case SERVER_LOAD -> {
                var ev = event.getServerLoad();
                org.bukkit.event.server.ServerLoadEvent.LoadType loadType =
                    ev.getLoadType().toUpperCase().contains("RELOAD")
                        ? org.bukkit.event.server.ServerLoadEvent.LoadType.RELOAD
                        : org.bukkit.event.server.ServerLoadEvent.LoadType.STARTUP;
                yield new org.bukkit.event.server.ServerLoadEvent(loadType);
            }
            case SERVER_TICK_END -> {
                var ev = event.getServerTickEnd();
                double durationMs = ev.getDurationNanos() / 1_000_000.0;
                yield new com.destroystokyo.paper.event.server.ServerTickEndEvent(
                    (int) ev.getTick(),
                    durationMs,
                    50_000_000L - ev.getDurationNanos()
                );
            }
            case SERVER_TICK_START -> {
                var ev = event.getServerTickStart();
                yield new com.destroystokyo.paper.event.server.ServerTickStartEvent((int) ev.getTick());
            }
            case SERVICE_REGISTER -> {
                var ev = event.getServiceRegister();
                yield createGenericBukkitEvent("org.bukkit.event.server.ServiceRegisterEvent", ev);
            }
            case SERVICE_UNREGISTER -> {
                var ev = event.getServiceUnregister();
                yield createGenericBukkitEvent("org.bukkit.event.server.ServiceUnregisterEvent", ev);
            }
            case TAB_COMPLETE -> {
                var ev = event.getTabComplete();
                yield new org.bukkit.event.server.TabCompleteEvent(
                    Bukkit.getConsoleSender(),
                    ev.getBuffer(),
                    new java.util.ArrayList<>(ev.getCompletionsList())
                );
            }
            case VEHICLE_BLOCK_COLLISION -> {
                var ev = event.getVehicleBlockCollision();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleBlockCollisionEvent", ev);
            }
            case VEHICLE_COLLISION -> {
                var ev = event.getVehicleCollision();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleCollisionEvent", ev);
            }
            case VEHICLE_CREATE -> {
                var ev = event.getVehicleCreate();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleCreateEvent", ev);
            }
            case VEHICLE_DAMAGE -> {
                var ev = event.getVehicleDamage();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleDamageEvent", ev);
            }
            case VEHICLE_DESTROY -> {
                var ev = event.getVehicleDestroy();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleDestroyEvent", ev);
            }
            case VEHICLE_ENTER -> {
                var ev = event.getVehicleEnter();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleEnterEvent", ev);
            }
            case VEHICLE_ENTITY_COLLISION -> {
                var ev = event.getVehicleEntityCollision();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleEntityCollisionEvent", ev);
            }
            case VEHICLE_EXIT -> {
                var ev = event.getVehicleExit();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleExitEvent", ev);
            }
            case VEHICLE_MOVE -> {
                var ev = event.getVehicleMove();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleMoveEvent", ev);
            }
            case VEHICLE_UPDATE -> {
                var ev = event.getVehicleUpdate();
                yield createGenericBukkitEvent("org.bukkit.event.vehicle.VehicleUpdateEvent", ev);
            }
            case BREW -> {
                var ev = event.getBrew();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.BrewEvent", ev);
            }
            case BREWING_STAND_FUEL -> {
                var ev = event.getBrewingStandFuel();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.BrewingStandFuelEvent", ev);
            }
            case CRAFT_ITEM -> {
                var ev = event.getCraftItem();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.CraftItemEvent", ev);
            }
            case FURNACE_BURN -> {
                var ev = event.getFurnaceBurn();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.FurnaceBurnEvent", ev);
            }
            case FURNACE_EXTRACT -> {
                var ev = event.getFurnaceExtract();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.FurnaceExtractEvent", ev);
            }
            case FURNACE_SMELT -> {
                var ev = event.getFurnaceSmelt();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.FurnaceSmeltEvent", ev);
            }
            case FURNACE_START_SMELT -> {
                var ev = event.getFurnaceStartSmelt();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.FurnaceStartSmeltEvent", ev);
            }
            case HOPPER_INVENTORY_SEARCH -> {
                var ev = event.getHopperInventorySearch();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.HopperInventorySearchEvent", ev);
            }
            case INVENTORY_CREATIVE -> {
                var ev = event.getInventoryCreative();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                org.bukkit.inventory.InventoryView view = player.getOpenInventory();
                int slot = ev.getSlot();
                org.bukkit.event.inventory.InventoryType.SlotType slotType =
                    slot < 0 ? org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE : view.getSlotType(slot);
                org.bukkit.Material mat = org.bukkit.Material.matchMaterial(ev.getItemId());
                ItemStack cursor = (mat != null && ev.getItemCount() > 0)
                    ? new ItemStack(mat, ev.getItemCount())
                    : new ItemStack(org.bukkit.Material.AIR);
                yield new org.bukkit.event.inventory.InventoryCreativeEvent(view, slotType, slot, cursor);
            }
            case INVENTORY_DRAG -> {
                var ev = event.getInventoryDrag();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.inventory.InventoryDragEvent(
                    player.getOpenInventory(),
                    null,
                    new ItemStack(org.bukkit.Material.AIR),
                    false,
                    java.util.Collections.emptyMap()
                );
            }
            case INVENTORY_INTERACT -> {
                var ev = event.getInventoryInteract();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryInteractEvent", ev);
            }
            case INVENTORY_MOVE_ITEM -> {
                var ev = event.getInventoryMoveItem();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryMoveItemEvent", ev);
            }
            case INVENTORY_OPEN -> {
                var ev = event.getInventoryOpen();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.inventory.InventoryOpenEvent(player.getOpenInventory());
            }
            case INVENTORY_PICKUP_ITEM -> {
                var ev = event.getInventoryPickupItem();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryPickupItemEvent", ev);
            }
            case PREPARE_ANVIL -> {
                var ev = event.getPrepareAnvil();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.PrepareAnvilEvent", ev);
            }
            case PREPARE_GRINDSTONE -> {
                var ev = event.getPrepareGrindstone();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.PrepareGrindstoneEvent", ev);
            }
            case PREPARE_INVENTORY_RESULT -> {
                var ev = event.getPrepareInventoryResult();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.PrepareInventoryResultEvent", ev);
            }
            case PREPARE_ITEM_CRAFT -> {
                var ev = event.getPrepareItemCraft();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.PrepareItemCraftEvent", ev);
            }
            case PREPARE_SMITHING -> {
                var ev = event.getPrepareSmithing();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.PrepareSmithingEvent", ev);
            }
            case SMITH_ITEM -> {
                var ev = event.getSmithItem();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.SmithItemEvent", ev);
            }
            case TRADE_SELECT -> {
                var ev = event.getTradeSelect();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.TradeSelectEvent", ev);
            }
            case BELL_RESONATE -> {
                var ev = event.getBellResonate();
                yield createGenericBukkitEvent("org.bukkit.event.block.BellResonateEvent", ev);
            }
            case BELL_RING -> {
                var ev = event.getBellRing();
                yield createGenericBukkitEvent("org.bukkit.event.block.BellRingEvent", ev);
            }
            case BLOCK_BREAK -> {
                var ev = event.getBlockBreak();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Block b = player.getWorld().getBlockAt(ev.getBlockX(), ev.getBlockY(), ev.getBlockZ());
                var bbe = new org.bukkit.event.block.BlockBreakEvent(b, player);
                bbe.setExpToDrop(ev.getExp());
                bbe.setDropItems(ev.getDrop());
                yield bbe;
            }
            case BLOCK_BRUSH -> null;
            case BLOCK_BURN -> {
                var ev = event.getBlockBurn();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockBurnEvent", ev);
            }
            case BLOCK_CAN_BUILD -> {
                var ev = event.getBlockCanBuild();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockCanBuildEvent", ev);
            }
            case BLOCK_COOK -> {
                var ev = event.getBlockCook();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockCookEvent", ev);
            }
            case BLOCK_DAMAGE -> {
                var ev = event.getBlockDamage();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Block b = player.getWorld().getBlockAt(ev.getBlockX(), ev.getBlockY(), ev.getBlockZ());
                yield new org.bukkit.event.block.BlockDamageEvent(player, b, player.getInventory().getItemInMainHand(), ev.getInstaBreak());
            }
            case BLOCK_DAMAGE_ABORT -> {
                var ev = event.getBlockDamageAbort();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockDamageAbortEvent", ev);
            }
            case BLOCK_DISPENSE -> {
                var ev = event.getBlockDispense();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockDispenseEvent", ev);
            }
            case BLOCK_DISPENSE_ARMOR -> {
                var ev = event.getBlockDispenseArmor();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockDispenseArmorEvent", ev);
            }
            case BLOCK_DISPENSE_LOOT -> {
                var ev = event.getBlockDispenseLoot();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockDispenseLootEvent", ev);
            }
            case BLOCK_DROP_ITEM -> {
                var ev = event.getBlockDropItem();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockDropItemEvent", ev);
            }
            case BLOCK_EXP -> {
                var ev = event.getBlockExp();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockExpEvent", ev);
            }
            case BLOCK_EXPLODE -> {
                var ev = event.getBlockExplode();
                World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                if (w == null) yield null;
                Block b = w.getBlockAt(ev.getBlockX(), ev.getBlockY(), ev.getBlockZ());
                yield new org.bukkit.event.block.BlockExplodeEvent(
                    b,
                    b.getState(),
                    new java.util.ArrayList<>(),
                    1.0f,
                    org.bukkit.ExplosionResult.DESTROY
                );
            }
            case BLOCK_FADE -> {
                var ev = event.getBlockFade();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockFadeEvent", ev);
            }
            case BLOCK_FERTILIZE -> {
                var ev = event.getBlockFertilize();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockFertilizeEvent", ev);
            }
            case BLOCK_FORM -> {
                var ev = event.getBlockForm();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockFormEvent", ev);
            }
            case BLOCK_FROM_TO -> {
                var ev = event.getBlockFromTo();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockFromToEvent", ev);
            }
            case BLOCK_GROW -> {
                var ev = event.getBlockGrow();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockGrowEvent", ev);
            }
            case BLOCK_IGNITE -> {
                var ev = event.getBlockIgnite();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockIgniteEvent", ev);
            }
            case BLOCK_MULTI_PLACE -> {
                var ev = event.getBlockMultiPlace();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockMultiPlaceEvent", ev);
            }
            case BLOCK_PHYSICS -> {
                var ev = event.getBlockPhysics();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockPhysicsEvent", ev);
            }
            case BLOCK_PISTON_EXTEND -> {
                var ev = event.getBlockPistonExtend();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockPistonExtendEvent", ev);
            }
            case BLOCK_PISTON_RETRACT -> {
                var ev = event.getBlockPistonRetract();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockPistonRetractEvent", ev);
            }
            case BLOCK_PLACE -> {
                var ev = event.getBlockPlace();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Block b = player.getWorld().getBlockAt(ev.getBlockX(), ev.getBlockY(), ev.getBlockZ());
                yield new org.bukkit.event.block.BlockPlaceEvent(b, b.getState(), b, player.getInventory().getItemInMainHand(), player, ev.getCanBuild(), org.bukkit.inventory.EquipmentSlot.HAND);
            }
            case BLOCK_RECEIVE_GAME -> {
                var ev = event.getBlockReceiveGame();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockReceiveGameEvent", ev);
            }
            case BLOCK_REDSTONE -> {
                var ev = event.getBlockRedstone();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockRedstoneEvent", ev);
            }
            case BLOCK_SHEAR_ENTITY -> {
                var ev = event.getBlockShearEntity();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockShearEntityEvent", ev);
            }
            case BLOCK_SPREAD -> {
                var ev = event.getBlockSpread();
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockSpreadEvent", ev);
            }
            case BREWING_START -> {
                var ev = event.getBrewingStart();
                yield createGenericBukkitEvent("org.bukkit.event.block.BrewingStartEvent", ev);
            }
            case CAMPFIRE_START -> {
                var ev = event.getCampfireStart();
                yield createGenericBukkitEvent("org.bukkit.event.block.CampfireStartEvent", ev);
            }
            case CAULDRON_LEVEL_CHANGE -> {
                var ev = event.getCauldronLevelChange();
                yield createGenericBukkitEvent("org.bukkit.event.block.CauldronLevelChangeEvent", ev);
            }
            case CRAFTER_CRAFT -> {
                var ev = event.getCrafterCraft();
                yield createGenericBukkitEvent("org.bukkit.event.block.CrafterCraftEvent", ev);
            }
            case ENTITY_BLOCK_FORM -> {
                var ev = event.getEntityBlockForm();
                yield createGenericBukkitEvent("org.bukkit.event.block.EntityBlockFormEvent", ev);
            }
            case FLUID_LEVEL_CHANGE -> {
                var ev = event.getFluidLevelChange();
                yield createGenericBukkitEvent("org.bukkit.event.block.FluidLevelChangeEvent", ev);
            }
            case INVENTORY_BLOCK_START -> {
                var ev = event.getInventoryBlockStart();
                yield createGenericBukkitEvent("org.bukkit.event.block.InventoryBlockStartEvent", ev);
            }
            case LEAVES_DECAY -> {
                var ev = event.getLeavesDecay();
                yield createGenericBukkitEvent("org.bukkit.event.block.LeavesDecayEvent", ev);
            }
            case MOISTURE_CHANGE -> {
                var ev = event.getMoistureChange();
                yield createGenericBukkitEvent("org.bukkit.event.block.MoistureChangeEvent", ev);
            }
            case NOTE_PLAY -> {
                var ev = event.getNotePlay();
                yield createGenericBukkitEvent("org.bukkit.event.block.NotePlayEvent", ev);
            }
            case SCULK_BLOOM -> {
                var ev = event.getSculkBloom();
                yield createGenericBukkitEvent("org.bukkit.event.block.SculkBloomEvent", ev);
            }
            case SIGN_CHANGE -> {
                var ev = event.getSignChange();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Block b = player.getWorld().getBlockAt(ev.getBlockX(), ev.getBlockY(), ev.getBlockZ());
                yield new org.bukkit.event.block.SignChangeEvent(b, player, ev.getLinesList().toArray(new String[0]));
            }
            case SPONGE_ABSORB -> {
                var ev = event.getSpongeAbsorb();
                yield createGenericBukkitEvent("org.bukkit.event.block.SpongeAbsorbEvent", ev);
            }
            case TNT_PRIME -> {
                var ev = event.getTntPrime();
                yield createGenericBukkitEvent("org.bukkit.event.block.TNTPrimeEvent", ev);
            }
            case VAULT_DISPLAY_ITEM -> {
                var ev = event.getVaultDisplayItem();
                yield createGenericBukkitEvent("org.bukkit.event.block.VaultDisplayItemEvent", ev);
            }
            case DIALOG_CLEAR -> null;
            case DIALOG_CLICK_ACTION -> null;
            case DIALOG_SHOW -> null;
            case ASYNC_PLAYER_CHAT -> {
                var ev = event.getAsyncPlayerChat();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new org.bukkit.event.player.AsyncPlayerChatEvent(true, player, ev.getMessage(), new java.util.HashSet<>(Bukkit.getOnlinePlayers()));
            }
            case ASYNC_PLAYER_PRE_LOGIN -> {
                var ev = event.getAsyncPlayerPreLogin();
                java.util.UUID uuid = java.util.UUID.fromString(ev.getPlayerUuid().getValue());
                String name = ev.getPlayerName().isEmpty() ? "Player" : ev.getPlayerName();
                PRELOGIN_PLAYER_NAMES.put(uuid, name);
                java.net.InetAddress addr = java.net.InetAddress.getLoopbackAddress();
                try {
                    if (!ev.getIpAddress().isEmpty()) {
                        String ip = ev.getIpAddress();
                        if (ip.startsWith("/")) ip = ip.substring(1);
                        int colonIdx = ip.indexOf(':');
                        if (colonIdx > 0) ip = ip.substring(0, colonIdx);
                        addr = java.net.InetAddress.getByName(ip);
                    }
                } catch (Throwable ignored) {}
                yield new org.bukkit.event.player.AsyncPlayerPreLoginEvent(name, addr, uuid);
            }
            case BEDROCK_FORM_RESPONSE -> null;
            case PLAYER_CHANGED_MAIN_HAND -> {
                var ev = event.getPlayerChangedMainHand();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerChangedMainHandEvent", ev);
            }
            case PLAYER_EGG_THROW -> {
                var ev = event.getPlayerEggThrow();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerEggThrowEvent", ev);
            }
            case PLAYER_EXP_CHANGE -> {
                var ev = event.getPlayerExpChange();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerExpChangeEvent(player, ev.getAmount());
            }
            case PLAYER_FISH -> {
                var ev = event.getPlayerFish();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerFishEvent", ev);
            }
            case INVENTORY_CLOSE -> {
                var ev = event.getInventoryClose();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.inventory.InventoryCloseEvent(player.getOpenInventory());
            }
            case INVENTORY_CLICK -> {
                var ev = event.getInventoryClick();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                org.bukkit.inventory.InventoryView view = player.getOpenInventory();
                int rawSlot = ev.getRawSlot();
                org.bukkit.event.inventory.InventoryType.SlotType slotType;
                try {
                    slotType = rawSlot < 0
                        ? org.bukkit.event.inventory.InventoryType.SlotType.OUTSIDE
                        : view.getSlotType(rawSlot);
                } catch (Throwable ignored) {
                    slotType = org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER;
                }
                String rawClick = ev.getClickType();
                org.bukkit.event.inventory.ClickType clickType = switch (rawClick) {
                    case "Right" -> org.bukkit.event.inventory.ClickType.RIGHT;
                    case "ShiftLeft" -> org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
                    case "ShiftRight" -> org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
                    case "Middle" -> org.bukkit.event.inventory.ClickType.MIDDLE;
                    case "Drop" -> org.bukkit.event.inventory.ClickType.DROP;
                    case "ControlDrop" -> org.bukkit.event.inventory.ClickType.CONTROL_DROP;
                    case "DoubleClick" -> org.bukkit.event.inventory.ClickType.DOUBLE_CLICK;
                    case "SwapOffhand" -> org.bukkit.event.inventory.ClickType.SWAP_OFFHAND;
                    default -> rawClick.startsWith("NumberKey")
                        ? org.bukkit.event.inventory.ClickType.NUMBER_KEY
                        : org.bukkit.event.inventory.ClickType.LEFT;
                };
                org.bukkit.event.inventory.InventoryAction action = switch (clickType) {
                    case RIGHT -> org.bukkit.event.inventory.InventoryAction.PICKUP_HALF;
                    case SHIFT_LEFT, SHIFT_RIGHT -> org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY;
                    case NUMBER_KEY, SWAP_OFFHAND -> org.bukkit.event.inventory.InventoryAction.HOTBAR_SWAP;
                    case MIDDLE -> org.bukkit.event.inventory.InventoryAction.CLONE_STACK;
                    case DROP -> org.bukkit.event.inventory.InventoryAction.DROP_ONE_SLOT;
                    case CONTROL_DROP -> org.bukkit.event.inventory.InventoryAction.DROP_ALL_SLOT;
                    case DOUBLE_CLICK -> org.bukkit.event.inventory.InventoryAction.COLLECT_TO_CURSOR;
                    default -> org.bukkit.event.inventory.InventoryAction.PICKUP_ALL;
                };
                var clickEvent = new org.bukkit.event.inventory.InventoryClickEvent(
                    view,
                    slotType,
                    rawSlot,
                    clickType,
                    action,
                    ev.getHotbarButton()
                );
                if (!ev.getClickedItemName().isEmpty() && ev.getClickedItemCount() > 0) {
                    ItemStack existing = clickEvent.getCurrentItem();
                    if (existing == null || existing.getType().isAir()) {
                        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(ev.getClickedItemName());
                        if (mat != null) {
                            clickEvent.setCurrentItem(new ItemStack(mat, ev.getClickedItemCount()));
                        }
                    }
                }
                yield clickEvent;
            }
            case PLAYER_ITEM_HELD -> {
                var ev = event.getPlayerItemHeld();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerItemHeldEvent(player, ev.getPreviousSlot(), ev.getNewSlot());
            }
            case PLAYER_ADVANCEMENT_DONE -> {
                var ev = event.getPlayerAdvancementDone();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerAdvancementDoneEvent", ev);
            }
            case PLAYER_ANIMATION -> {
                var ev = event.getPlayerAnimation();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                org.bukkit.event.player.PlayerAnimationType animType =
                    ev.getAnimationType().toUpperCase().contains("OFF")
                        ? org.bukkit.event.player.PlayerAnimationType.OFF_ARM_SWING
                        : org.bukkit.event.player.PlayerAnimationType.ARM_SWING;
                yield new org.bukkit.event.player.PlayerAnimationEvent(player, animType);
            }
            case PLAYER_ARMOR_STAND_MANIPULATE -> {
                var ev = event.getPlayerArmorStandManipulate();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerArmorStandManipulateEvent", ev);
            }
            case PLAYER_BED_ENTER -> {
                var ev = event.getPlayerBedEnter();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerBedEnterEvent", ev);
            }
            case PLAYER_BED_LEAVE -> {
                var ev = event.getPlayerBedLeave();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerBedLeaveEvent", ev);
            }
            case PLAYER_BUCKET_EMPTY -> {
                var ev = event.getPlayerBucketEmpty();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerBucketEmptyEvent", ev);
            }
            case PLAYER_BUCKET_FILL -> {
                var ev = event.getPlayerBucketFill();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerBucketFillEvent", ev);
            }
            case PLAYER_BUCKET_ENTITY -> {
                var ev = event.getPlayerBucketEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerBucketEntityEvent", ev);
            }
            case PLAYER_CHANGE_WORLD -> {
                var ev = event.getPlayerChangeWorld();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerChangedWorldEvent", ev);
            }
            case PLAYER_CHANGED_WORLD -> {
                var ev = event.getPlayerChangedWorld();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerChangedWorldEvent", ev);
            }
            case PLAYER_CHANNEL -> {
                var ev = event.getPlayerChannel();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerChannelEvent", ev);
            }
            case PLAYER_CHAT -> {
                var ev = event.getPlayerChat();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new org.bukkit.event.player.AsyncPlayerChatEvent(true, player, ev.getMessage(), new java.util.HashSet<>(Bukkit.getOnlinePlayers()));
            }
            case PLAYER_COMMAND_PREPROCESS -> {
                var ev = event.getPlayerCommandPreprocess();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, ev.getCommand());
            }
            case PLAYER_COMMAND_SEND -> {
                var ev = event.getPlayerCommandSend();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                String msg = ev.getCommand().startsWith("/") ? ev.getCommand() : "/" + ev.getCommand();
                yield new org.bukkit.event.player.PlayerCommandPreprocessEvent(player, msg);
            }
            case PLAYER_CUSTOM_PAYLOAD -> {
                var ev = event.getPlayerCustomPayload();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player != null && !ev.getChannel().isEmpty()) {
                    try {
                        if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
                            cp.addChannel(ev.getChannel());
                        }
                        if (Bukkit.getServer() != null) {
                            Bukkit.getMessenger().dispatchIncomingMessage(player, ev.getChannel(), ev.getData().toByteArray());
                        }
                    } catch (Throwable ignored) {}
                }
                yield null;
            }
            case PLAYER_DROP_ITEM -> {
                var ev = event.getPlayerDropItem();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerDropItemEvent", ev);
            }
            case PLAYER_EDIT_BOOK -> {
                var ev = event.getPlayerEditBook();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerEditBookEvent", ev);
            }
            case PLAYER_ELYTRA_BOOST -> {
                var ev = event.getPlayerElytraBoost();
                yield createGenericBukkitEvent("com.destroystokyo.paper.event.player.PlayerElytraBoostEvent", ev);
            }
            case PLAYER_EXP_COOLDOWN_CHANGE -> {
                var ev = event.getPlayerExpCooldownChange();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerExpCooldownChangeEvent", ev);
            }
            case PLAYER_GAMEMODE_CHANGE -> {
                var ev = event.getPlayerGamemodeChange();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                org.bukkit.GameMode gm;
                try {
                    gm = org.bukkit.GameMode.valueOf(ev.getNewGamemode().toUpperCase());
                } catch (Exception ex) {
                    gm = org.bukkit.GameMode.SURVIVAL;
                }
                yield new org.bukkit.event.player.PlayerGameModeChangeEvent(player, gm);
            }
            case PLAYER_HARVEST_BLOCK -> {
                var ev = event.getPlayerHarvestBlock();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerHarvestBlockEvent", ev);
            }
            case PLAYER_HIDE_ENTITY -> {
                var ev = event.getPlayerHideEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerHideEntityEvent", ev);
            }
            case PLAYER_INPUT -> {
                var ev = event.getPlayerInput();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerInputEvent", ev);
            }
            case PLAYER_INTERACT_AT_ENTITY -> {
                var ev = event.getPlayerInteractAtEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerInteractAtEntityEvent", ev);
            }
            case PLAYER_INTERACT_ENTITY -> {
                var ev = event.getPlayerInteractEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerInteractEntityEvent", ev);
            }
            case PLAYER_INTERACT -> {
                var ev = event.getPlayerInteract();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                org.bukkit.event.block.Action act;
                try {
                    act = org.bukkit.event.block.Action.valueOf(ev.getAction());
                } catch (Exception ex) {
                    act = org.bukkit.event.block.Action.RIGHT_CLICK_AIR;
                }
                org.bukkit.block.Block b = (act == org.bukkit.event.block.Action.LEFT_CLICK_AIR
                        || act == org.bukkit.event.block.Action.RIGHT_CLICK_AIR)
                        ? null
                        : player.getWorld().getBlockAt(ev.getClickedPosX(), ev.getClickedPosY(), ev.getClickedPosZ());
                yield new org.bukkit.event.player.PlayerInteractEvent(player, act, player.getInventory().getItemInMainHand(), b, org.bukkit.block.BlockFace.UP, org.bukkit.inventory.EquipmentSlot.HAND);
            }
            case PLAYER_INTERACT_UNKNOWN_ENTITY -> {
                var ev = event.getPlayerInteractUnknownEntity();
                yield createGenericBukkitEvent("com.destroystokyo.paper.event.player.PlayerUseUnknownEntityEvent", ev);
            }
            case PLAYER_ITEM_BREAK -> {
                var ev = event.getPlayerItemBreak();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerItemBreakEvent", ev);
            }
            case PLAYER_ITEM_CONSUME -> {
                var ev = event.getPlayerItemConsume();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerItemConsumeEvent", ev);
            }
            case PLAYER_ITEM_DAMAGE -> {
                var ev = event.getPlayerItemDamage();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerItemDamageEvent", ev);
            }
            case PLAYER_ITEM_MEND -> {
                var ev = event.getPlayerItemMend();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerItemMendEvent", ev);
            }
            case PLAYER_JOIN -> {
                var ev = event.getPlayerJoin();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()
                        && ev.getEntityId() > 0
                        && player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
                    cp.getHandle().setId(ev.getEntityId());
                }
                Component msg = parseJsonOrPlain(ev.getJoinMessage());
                yield new org.bukkit.event.player.PlayerJoinEvent(player, msg);
            }
            case PLAYER_KICK -> {
                var ev = event.getPlayerKick();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerKickEvent(
                    player,
                    Component.text(ev.getReason()),
                    Component.empty()
                );
            }
            case PLAYER_LEASH_ENTITY -> {
                var ev = event.getPlayerLeashEntity();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PlayerLeashEntityEvent", ev);
            }
            case PLAYER_LEAVE -> {
                var ev = event.getPlayerLeave();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                Component msg = parseJsonOrPlain(ev.getLeaveMessage());
                yield new org.bukkit.event.player.PlayerQuitEvent(player, msg);
            }
            case PLAYER_LEVEL_CHANGE -> {
                var ev = event.getPlayerLevelChange();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerLevelChangeEvent", ev);
            }
            case PLAYER_LINKS_SEND -> {
                var ev = event.getPlayerLinksSend();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerLinksSendEvent", ev);
            }
            case PLAYER_LOCALE_CHANGE -> {
                var ev = event.getPlayerLocaleChange();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerLocaleChangeEvent", ev);
            }
            case PLAYER_LOGIN -> {
                var ev = event.getPlayerLogin();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.net.InetAddress addr = java.net.InetAddress.getLoopbackAddress();
                yield new org.bukkit.event.player.PlayerLoginEvent(player, "localhost", addr);
            }
            case PLAYER_MOVE -> {
                var ev = event.getPlayerMove();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp) {
                    cp.getHandle().setPosRaw(ev.getToX(), ev.getToY(), ev.getToZ());
                }
                Location from = new Location(player.getWorld(), ev.getFromX(), ev.getFromY(), ev.getFromZ());
                Location to = new Location(player.getWorld(), ev.getToX(), ev.getToY(), ev.getToZ());
                yield new org.bukkit.event.player.PlayerMoveEvent(player, from, to);
            }
            case PLAYER_NAME_ENTITY -> {
                var ev = event.getPlayerNameEntity();
                yield createGenericBukkitEvent("io.papermc.paper.event.player.PlayerNameEntityEvent", ev);
            }
            case PLAYER_OPEN_SIGN -> {
                var ev = event.getPlayerOpenSign();
                yield createGenericBukkitEvent("io.papermc.paper.event.player.PlayerOpenSignEvent", ev);
            }
            case PLAYER_PERMISSION_CHECK -> null;
            case PLAYER_PICKUP_ARROW -> {
                var ev = event.getPlayerPickupArrow();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerPickupArrowEvent", ev);
            }
            case PLAYER_PORTAL -> {
                var ev = event.getPlayerPortal();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerPortalEvent", ev);
            }
            case PLAYER_PRE_LOGIN -> {
                var ev = event.getPlayerPreLogin();
                java.util.UUID uuid = java.util.UUID.fromString(ev.getPlayerUuid().getValue());
                String name = ev.getPlayerName().isEmpty() ? "Player" : ev.getPlayerName();
                java.net.InetAddress addr = java.net.InetAddress.getLoopbackAddress();
                try {
                    if (!ev.getIpAddress().isEmpty()) {
                        String ip = ev.getIpAddress();
                        if (ip.startsWith("/")) ip = ip.substring(1);
                        int colonIdx = ip.indexOf(':');
                        if (colonIdx > 0) ip = ip.substring(0, colonIdx);
                        addr = java.net.InetAddress.getByName(ip);
                    }
                } catch (Throwable ignored) {}
                yield new org.bukkit.event.player.PlayerPreLoginEvent(name, addr, uuid);
            }
            case PLAYER_RECIPE_BOOK_CLICK -> {
                var ev = event.getPlayerRecipeBookClick();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRecipeBookClickEvent", ev);
            }
            case PLAYER_RECIPE_BOOK_SETTINGS_CHANGE -> {
                var ev = event.getPlayerRecipeBookSettingsChange();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRecipeBookSettingsChangeEvent", ev);
            }
            case PLAYER_RECIPE_DISCOVER -> {
                var ev = event.getPlayerRecipeDiscover();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRecipeDiscoverEvent", ev);
            }
            case PLAYER_REGISTER_CHANNEL -> {
                var ev = event.getPlayerRegisterChannel();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp && !ev.getChannel().isEmpty()) {
                    try {
                        cp.addChannel(ev.getChannel());
                    } catch (Throwable ignored) {}
                }
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRegisterChannelEvent", ev);
            }
            case PLAYER_RESOURCE_PACK_STATUS -> {
                var ev = event.getPlayerResourcePackStatus();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerResourcePackStatusEvent", ev);
            }
            case PLAYER_RESPAWN -> {
                var ev = event.getPlayerRespawn();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Location respawnLoc = new Location(player.getWorld(), ev.getPosX(), ev.getPosY(), ev.getPosZ(), ev.getYaw(), ev.getPitch());
                yield new org.bukkit.event.player.PlayerRespawnEvent(
                    player,
                    respawnLoc,
                    false,
                    false,
                    false,
                    org.bukkit.event.player.PlayerRespawnEvent.RespawnReason.DEATH
                );
            }
            case PLAYER_RIPTIDE -> {
                var ev = event.getPlayerRiptide();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRiptideEvent", ev);
            }
            case PLAYER_SHEAR_ENTITY -> {
                var ev = event.getPlayerShearEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerShearEntityEvent", ev);
            }
            case PLAYER_SHOW_ENTITY -> {
                var ev = event.getPlayerShowEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerShowEntityEvent", ev);
            }
            case PLAYER_SPAWN_CHANGE -> {
                var ev = event.getPlayerSpawnChange();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerSpawnChangeEvent", ev);
            }
            case PLAYER_SPAWN_LOCATION -> {
                var ev = event.getPlayerSpawnLocation();
                yield createGenericBukkitEvent("org.spigotmc.event.player.PlayerSpawnLocationEvent", ev);
            }
            case PLAYER_STATISTIC_INCREMENT -> {
                var ev = event.getPlayerStatisticIncrement();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerStatisticIncrementEvent", ev);
            }
            case PLAYER_SWAP_HAND_ITEMS -> {
                var ev = event.getPlayerSwapHandItems();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerSwapHandItemsEvent", ev);
            }
            case PLAYER_TAKE_LECTERN_BOOK -> {
                var ev = event.getPlayerTakeLecternBook();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerTakeLecternBookEvent", ev);
            }
            case PLAYER_TELEPORT -> {
                var ev = event.getPlayerTeleport();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Location from = new Location(player.getWorld(), ev.getFromX(), ev.getFromY(), ev.getFromZ());
                Location to = new Location(player.getWorld(), ev.getToX(), ev.getToY(), ev.getToZ());
                yield new org.bukkit.event.player.PlayerTeleportEvent(player, from, to);
            }
            case PLAYER_TOGGLE_FLIGHT -> {
                var ev = event.getPlayerToggleFlight();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerToggleFlightEvent(player, ev.getIsFlying());
            }
            case PLAYER_TOGGLE_SNEAK -> {
                var ev = event.getPlayerToggleSneak();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerToggleSneakEvent(player, ev.getIsSneaking());
            }
            case PLAYER_TOGGLE_SPRINT -> {
                var ev = event.getPlayerToggleSprint();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new org.bukkit.event.player.PlayerToggleSprintEvent(player, ev.getIsSprinting());
            }
            case PLAYER_UNLEASH_ENTITY -> {
                var ev = event.getPlayerUnleashEntity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerUnleashEntityEvent", ev);
            }
            case PLAYER_UNREGISTER_CHANNEL -> {
                var ev = event.getPlayerUnregisterChannel();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player instanceof org.bukkit.craftbukkit.entity.CraftPlayer cp && !ev.getChannel().isEmpty()) {
                    try {
                        cp.removeChannel(ev.getChannel());
                    } catch (Throwable ignored) {}
                }
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerUnregisterChannelEvent", ev);
            }
            case PLAYER_VELOCITY -> {
                var ev = event.getPlayerVelocity();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerVelocityEvent", ev);
            }
            case HANGING_BREAK -> {
                var ev = event.getHangingBreak();
                yield createGenericBukkitEvent("org.bukkit.event.hanging.HangingBreakEvent", ev);
            }
            case HANGING_BREAK_BY_ENTITY -> {
                var ev = event.getHangingBreakByEntity();
                yield createGenericBukkitEvent("org.bukkit.event.hanging.HangingBreakByEntityEvent", ev);
            }
            case HANGING_PLACE -> {
                var ev = event.getHangingPlace();
                yield createGenericBukkitEvent("org.bukkit.event.hanging.HangingPlaceEvent", ev);
            }
            case AREA_EFFECT_CLOUD_APPLY -> {
                var ev = event.getAreaEffectCloudApply();
                yield createGenericBukkitEvent("org.bukkit.event.entity.AreaEffectCloudApplyEvent", ev);
            }
            case ARROW_BODY_COUNT_CHANGE -> {
                var ev = event.getArrowBodyCountChange();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ArrowBodyCountChangeEvent", ev);
            }
            case BAT_TOGGLE_SLEEP -> {
                var ev = event.getBatToggleSleep();
                yield createGenericBukkitEvent("org.bukkit.event.entity.BatToggleSleepEvent", ev);
            }
            case CREATURE_SPAWN -> {
                var ev = event.getCreatureSpawn();
                Entity ent = getEntity(ev.getEntityId());
                if (ent instanceof org.bukkit.entity.LivingEntity le) {
                    org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason reason =
                        org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.DEFAULT;
                    try {
                        if (!ev.getSpawnReason().isEmpty()) {
                            reason = org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.valueOf(ev.getSpawnReason().toUpperCase());
                        }
                    } catch (Throwable ignored) {}
                    yield new org.bukkit.event.entity.CreatureSpawnEvent(le, reason);
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.CreatureSpawnEvent", ev);
            }
            case CREEPER_POWER -> {
                var ev = event.getCreeperPower();
                yield createGenericBukkitEvent("org.bukkit.event.entity.CreeperPowerEvent", ev);
            }
            case ENDER_DRAGON_CHANGE_PHASE -> {
                var ev = event.getEnderDragonChangePhase();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EnderDragonChangePhaseEvent", ev);
            }
            case ENTITY_AIR_CHANGE -> {
                var ev = event.getEntityAirChange();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityAirChangeEvent", ev);
            }
            case ENTITY_BREAK_DOOR -> {
                var ev = event.getEntityBreakDoor();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityBreakDoorEvent", ev);
            }
            case ENTITY_BREED -> {
                var ev = event.getEntityBreed();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityBreedEvent", ev);
            }
            case ENTITY_CHANGE_BLOCK -> {
                var ev = event.getEntityChangeBlock();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityChangeBlockEvent", ev);
            }
            case ENTITY_COMBUST -> {
                var ev = event.getEntityCombust();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityCombustEvent", ev);
            }
            case ENTITY_COMBUST_BY_BLOCK -> {
                var ev = event.getEntityCombustByBlock();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityCombustByBlockEvent", ev);
            }
            case ENTITY_COMBUST_BY_ENTITY -> {
                var ev = event.getEntityCombustByEntity();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityCombustByEntityEvent", ev);
            }
            case ENTITY_DAMAGE -> {
                var ev = event.getEntityDamage();
                Entity ent = getEntity(ev.getEntityId());
                if (ent != null) {
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause cause =
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.CUSTOM;
                    try {
                        if (!ev.getDamageType().isEmpty()) {
                            cause = org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(ev.getDamageType().toUpperCase());
                        }
                    } catch (Throwable ignored) {}
                    yield new org.bukkit.event.entity.EntityDamageEvent(
                        ent,
                        cause,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                        ev.getDamage()
                    );
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDamageEvent", ev);
            }
            case ENTITY_DAMAGE_BY_BLOCK -> {
                var ev = event.getEntityDamageByBlock();
                Entity ent = getEntity(ev.getEntityId());
                if (ent != null) {
                    Block damager = ent.getWorld().getBlockAt(ev.getDamagerPosX(), ev.getDamagerPosY(), ev.getDamagerPosZ());
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause cause =
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.CONTACT;
                    try {
                        if (!ev.getCause().isEmpty()) {
                            cause = org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(ev.getCause().toUpperCase());
                        }
                    } catch (Throwable ignored) {}
                    yield new org.bukkit.event.entity.EntityDamageByBlockEvent(
                        damager,
                        damager.getState(),
                        ent,
                        cause,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                        ev.getDamage()
                    );
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDamageByBlockEvent", ev);
            }
            case ENTITY_DAMAGE_BY_ENTITY -> {
                var ev = event.getEntityDamageByEntity();
                Entity ent = getEntity(ev.getEntityId());
                Entity damager = getEntity(ev.getDamagerId());
                if (ent != null && damager != null) {
                    org.bukkit.event.entity.EntityDamageEvent.DamageCause cause =
                        org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK;
                    try {
                        if (!ev.getCause().isEmpty()) {
                            cause = org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(ev.getCause().toUpperCase());
                        }
                    } catch (Throwable ignored) {}
                    yield new org.bukkit.event.entity.EntityDamageByEntityEvent(
                        damager,
                        ent,
                        cause,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                        ev.getDamage()
                    );
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDamageByEntityEvent", ev);
            }
            case ENTITY_DEATH -> {
                var ev = event.getEntityDeath();
                Entity ent = getEntity(ev.getEntityId());
                if (ent instanceof org.bukkit.entity.LivingEntity le) {
                    yield new org.bukkit.event.entity.EntityDeathEvent(
                        le,
                        org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                        new java.util.ArrayList<>(),
                        ev.getDroppedExp()
                    );
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDeathEvent", ev);
            }
            case PLAYER_DEATH -> {
                var ev = event.getPlayerDeath();
                Player player = ev.hasPlayerUuid() ? getPlayer(ev.getPlayerUuid().getValue()) : null;
                if (player == null) yield null;
                Component deathMsg = ev.getDeathMessage().isEmpty() ? Component.empty() : parseJsonOrPlain(ev.getDeathMessage());
                yield new org.bukkit.event.entity.PlayerDeathEvent(
                    player,
                    org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),
                    new java.util.ArrayList<>(),
                    ev.getDroppedExp(),
                    0,
                    0,
                    0,
                    deathMsg,
                    true
                );
            }
            case ENTITY_DISMOUNT -> {
                var ev = event.getEntityDismount();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDismountEvent", ev);
            }
            case ENTITY_DROP_ITEM -> {
                var ev = event.getEntityDropItem();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDropItemEvent", ev);
            }
            case ENTITY_DYE -> {
                var ev = event.getEntityDye();
                yield createGenericBukkitEvent("io.papermc.paper.event.entity.EntityDyeEvent", ev);
            }
            case ENTITY_ENTER_BLOCK -> {
                var ev = event.getEntityEnterBlock();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityEnterBlockEvent", ev);
            }
            case ENTITY_ENTER_LOVE_MODE -> {
                var ev = event.getEntityEnterLoveMode();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityEnterLoveModeEvent", ev);
            }
            case ENTITY_EXHAUSTION -> {
                var ev = event.getEntityExhaustion();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityExhaustionEvent", ev);
            }
            case ENTITY_EXPLODE -> {
                var ev = event.getEntityExplode();
                Entity ent = getEntity(ev.getEntityId());
                World w = ent != null ? ent.getWorld() : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
                if (w == null) yield null;
                Location loc = new Location(w, ev.getPosX(), ev.getPosY(), ev.getPosZ());
                yield new org.bukkit.event.entity.EntityExplodeEvent(
                    ent,
                    loc,
                    new java.util.ArrayList<>(),
                    ev.getYieldRate(),
                    org.bukkit.ExplosionResult.DESTROY
                );
            }
            case ENTITY_INTERACT -> {
                var ev = event.getEntityInteract();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityInteractEvent", ev);
            }
            case ENTITY_KNOCKBACK -> {
                var ev = event.getEntityKnockback();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityKnockbackEvent", ev);
            }
            case ENTITY_KNOCKBACK_BY_ENTITY -> {
                var ev = event.getEntityKnockbackByEntity();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityKnockbackByEntityEvent", ev);
            }
            case ENTITY_MOUNT -> {
                var ev = event.getEntityMount();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityMountEvent", ev);
            }
            case ENTITY_PICKUP_ITEM -> {
                var ev = event.getEntityPickupItem();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPickupItemEvent", ev);
            }
            case ENTITY_PLACE -> {
                var ev = event.getEntityPlace();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPlaceEvent", ev);
            }
            case ENTITY_PORTAL -> {
                var ev = event.getEntityPortal();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPortalEvent", ev);
            }
            case ENTITY_PORTAL_ENTER -> {
                var ev = event.getEntityPortalEnter();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPortalEnterEvent", ev);
            }
            case ENTITY_PORTAL_EXIT -> {
                var ev = event.getEntityPortalExit();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPortalExitEvent", ev);
            }
            case ENTITY_POSE_CHANGE -> {
                var ev = event.getEntityPoseChange();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPoseChangeEvent", ev);
            }
            case ENTITY_POTION_EFFECT -> {
                var ev = event.getEntityPotionEffect();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityPotionEffectEvent", ev);
            }
            case ENTITY_REGAIN_HEALTH -> {
                var ev = event.getEntityRegainHealth();
                Entity ent = getEntity(ev.getEntityId());
                if (ent != null) {
                    yield new org.bukkit.event.entity.EntityRegainHealthEvent(
                        ent,
                        ev.getAmount(),
                        org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.CUSTOM
                    );
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityRegainHealthEvent", ev);
            }
            case ENTITY_REMOVE -> {
                var ev = event.getEntityRemove();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityRemoveEvent", ev);
            }
            case ENTITY_RESURRECT -> {
                var ev = event.getEntityResurrect();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityResurrectEvent", ev);
            }
            case ENTITY_SHOOT_BOW -> {
                var ev = event.getEntityShootBow();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityShootBowEvent", ev);
            }
            case ENTITY_SPAWN -> {
                var ev = event.getEntitySpawn();
                Entity ent = getEntity(ev.getEntityId());
                if (ent != null) {
                    yield new org.bukkit.event.entity.EntitySpawnEvent(ent);
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntitySpawnEvent", ev);
            }
            case ENTITY_SPELL_CAST -> {
                var ev = event.getEntitySpellCast();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntitySpellCastEvent", ev);
            }
            case ENTITY_TAME -> {
                var ev = event.getEntityTame();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityTameEvent", ev);
            }
            case ENTITY_TARGET -> {
                var ev = event.getEntityTarget();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityTargetEvent", ev);
            }
            case ENTITY_TARGET_BLOCK -> {
                var ev = event.getEntityTargetBlock();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityTargetEvent", ev);
            }
            case ENTITY_TARGET_LIVING_ENTITY -> {
                var ev = event.getEntityTargetLivingEntity();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityTargetLivingEntityEvent", ev);
            }
            case ENTITY_TELEPORT -> {
                var ev = event.getEntityTeleport();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityTeleportEvent", ev);
            }
            case ENTITY_TOGGLE_GLIDE -> {
                var ev = event.getEntityToggleGlide();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityToggleGlideEvent", ev);
            }
            case ENTITY_TOGGLE_SWIM -> {
                var ev = event.getEntityToggleSwim();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityToggleSwimEvent", ev);
            }
            case ENTITY_TRANSFORM -> {
                var ev = event.getEntityTransform();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityTransformEvent", ev);
            }
            case ENTITY_UNLEASH -> {
                var ev = event.getEntityUnleash();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityUnleashEvent", ev);
            }
            case EXP_BOTTLE -> {
                var ev = event.getExpBottle();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ExpBottleEvent", ev);
            }
            case EXPLOSION_PRIME -> {
                var ev = event.getExplosionPrime();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ExplosionPrimeEvent", ev);
            }
            case FIREWORK_EXPLODE -> {
                var ev = event.getFireworkExplode();
                yield createGenericBukkitEvent("org.bukkit.event.entity.FireworkExplodeEvent", ev);
            }
            case FOOD_LEVEL_CHANGE -> {
                var ev = event.getFoodLevelChange();
                Entity ent = getEntity(ev.getEntityId());
                org.bukkit.entity.HumanEntity human = ent instanceof org.bukkit.entity.HumanEntity h
                    ? h
                    : getPlayer("00000000-0000-0000-0000-000000000001");
                if (human != null) {
                    yield new org.bukkit.event.entity.FoodLevelChangeEvent(human, ev.getFoodLevel());
                }
                yield createGenericBukkitEvent("org.bukkit.event.entity.FoodLevelChangeEvent", ev);
            }
            case HORSE_JUMP -> {
                var ev = event.getHorseJump();
                yield createGenericBukkitEvent("org.bukkit.event.entity.HorseJumpEvent", ev);
            }
            case ITEM_DESPAWN -> {
                var ev = event.getItemDespawn();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ItemDespawnEvent", ev);
            }
            case ITEM_MERGE -> {
                var ev = event.getItemMerge();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ItemMergeEvent", ev);
            }
            case ITEM_SPAWN -> {
                var ev = event.getItemSpawn();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ItemSpawnEvent", ev);
            }
            case LINGERING_POTION_SPLASH -> {
                var ev = event.getLingeringPotionSplash();
                yield createGenericBukkitEvent("org.bukkit.event.entity.LingeringPotionSplashEvent", ev);
            }
            case PIG_ZAP -> {
                var ev = event.getPigZap();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PigZapEvent", ev);
            }
            case PIG_ZOMBIE_ANGER -> {
                var ev = event.getPigZombieAnger();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PigZombieAngerEvent", ev);
            }
            case PIGLIN_BARTER -> {
                var ev = event.getPiglinBarter();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PiglinBarterEvent", ev);
            }
            case POTION_SPLASH -> {
                var ev = event.getPotionSplash();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PotionSplashEvent", ev);
            }
            case PROJECTILE_HIT -> {
                var ev = event.getProjectileHit();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ProjectileHitEvent", ev);
            }
            case PROJECTILE_LAUNCH -> {
                var ev = event.getProjectileLaunch();
                yield createGenericBukkitEvent("org.bukkit.event.entity.ProjectileLaunchEvent", ev);
            }
            case SHEEP_DYE_WOOL -> {
                var ev = event.getSheepDyeWool();
                yield createGenericBukkitEvent("org.bukkit.event.entity.SheepDyeWoolEvent", ev);
            }
            case SHEEP_REGROW_WOOL -> {
                var ev = event.getSheepRegrowWool();
                yield createGenericBukkitEvent("org.bukkit.event.entity.SheepRegrowWoolEvent", ev);
            }
            case SLIME_SPLIT -> {
                var ev = event.getSlimeSplit();
                yield createGenericBukkitEvent("org.bukkit.event.entity.SlimeSplitEvent", ev);
            }
            case SPAWNER_SPAWN -> {
                var ev = event.getSpawnerSpawn();
                yield createGenericBukkitEvent("org.bukkit.event.entity.SpawnerSpawnEvent", ev);
            }
            case STRIDER_TEMPERATURE_CHANGE -> {
                var ev = event.getStriderTemperatureChange();
                yield createGenericBukkitEvent("org.bukkit.event.entity.StriderTemperatureChangeEvent", ev);
            }
            case TRIAL_SPAWNER_SPAWN -> {
                var ev = event.getTrialSpawnerSpawn();
                yield createGenericBukkitEvent("org.bukkit.event.entity.TrialSpawnerSpawnEvent", ev);
            }
            case VILLAGER_ACQUIRE_TRADE -> {
                var ev = event.getVillagerAcquireTrade();
                yield createGenericBukkitEvent("org.bukkit.event.entity.VillagerAcquireTradeEvent", ev);
            }
            case VILLAGER_CAREER_CHANGE -> {
                var ev = event.getVillagerCareerChange();
                yield createGenericBukkitEvent("org.bukkit.event.entity.VillagerCareerChangeEvent", ev);
            }
            case VILLAGER_REPLENISH_TRADE -> {
                var ev = event.getVillagerReplenishTrade();
                yield createGenericBukkitEvent("org.bukkit.event.entity.VillagerReplenishTradeEvent", ev);
            }
            case VILLAGER_REPUTATION_CHANGE -> null;
            case WARDEN_ANGER_CHANGE -> {
                var ev = event.getWardenAngerChange();
                yield createGenericBukkitEvent("io.papermc.paper.event.entity.WardenAngerChangeEvent", ev);
            }
            case RAID_FINISH -> {
                var ev = event.getRaidFinish();
                yield createGenericBukkitEvent("org.bukkit.event.raid.RaidFinishEvent", ev);
            }
            case RAID_SPAWN_WAVE -> {
                var ev = event.getRaidSpawnWave();
                yield createGenericBukkitEvent("org.bukkit.event.raid.RaidSpawnWaveEvent", ev);
            }
            case RAID_STOP -> {
                var ev = event.getRaidStop();
                yield createGenericBukkitEvent("org.bukkit.event.raid.RaidStopEvent", ev);
            }
            case RAID_TRIGGER -> {
                var ev = event.getRaidTrigger();
                yield createGenericBukkitEvent("org.bukkit.event.raid.RaidTriggerEvent", ev);
            }
            case ENCHANT_ITEM -> {
                var ev = event.getEnchantItem();
                yield createGenericBukkitEvent("org.bukkit.event.enchantment.EnchantItemEvent", ev);
            }
            case PREPARE_ITEM_ENCHANT -> {
                var ev = event.getPrepareItemEnchant();
                yield createGenericBukkitEvent("org.bukkit.event.enchantment.PrepareItemEnchantEvent", ev);
            }
            case DATA_NOT_SET -> null;
            default -> null;
        };
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Exception in createEvent for " + event.getDataCase() + ": " + t.getMessage(), t);
            return null;
        }
    }

    @NotNull
    public static byte[] toFireEventResponse(@NotNull org.bukkit.event.Event event) {
        try {
            boolean cancelled = event instanceof org.bukkit.event.Cancellable c && c.isCancelled();
            if (event instanceof org.bukkit.event.player.PlayerInteractEvent pie) {
                cancelled = pie.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
                        || pie.useItemInHand() == org.bukkit.event.Event.Result.DENY;
            }
            FireEventResponse.Builder resp = FireEventResponse.newBuilder().setCancelled(cancelled);

            switch (event) {
                case org.bukkit.event.block.BlockBreakEvent bbe -> resp.setData(
                    Event.newBuilder().setBlockBreak(
                        BlockBreakEvent.newBuilder()
                            .setExp(bbe.getExpToDrop())
                            .setDrop(bbe.isDropItems())
                    )
                );
                case org.bukkit.event.block.BlockPlaceEvent bpe -> resp.setData(
                    Event.newBuilder().setBlockPlace(
                        BlockPlaceEvent.newBuilder()
                            .setCanBuild(bpe.canBuild())
                    )
                );
                case org.bukkit.event.block.SignChangeEvent sce -> resp.setData(
                    Event.newBuilder().setSignChange(
                        SignChangeEvent.newBuilder()
                            .addAllLines(java.util.Arrays.asList(sce.getLines()))
                    )
                );
                case org.bukkit.event.player.AsyncPlayerChatEvent apce -> resp.setData(
                    Event.newBuilder().setAsyncPlayerChat(
                        AsyncPlayerChatEvent.newBuilder()
                            .setMessage(apce.getMessage())
                    )
                );
                case org.bukkit.event.player.PlayerJoinEvent pje -> resp.setData(
                    Event.newBuilder().setPlayerJoin(
                        PlayerJoinEvent.newBuilder()
                            .setJoinMessage(pje.joinMessage() != null
                                ? GsonComponentSerializer.gson().serialize(pje.joinMessage())
                                : "")
                    )
                );
                case org.bukkit.event.player.PlayerQuitEvent pqe -> resp.setData(
                    Event.newBuilder().setPlayerLeave(
                        PlayerLeaveEvent.newBuilder()
                            .setLeaveMessage(pqe.quitMessage() != null
                                ? GsonComponentSerializer.gson().serialize(pqe.quitMessage())
                                : "")
                    )
                );
                case org.bukkit.event.player.AsyncPlayerPreLoginEvent apple -> {
                    if (apple.getLoginResult() != org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                        resp.setCancelled(true);
                    }
                    resp.setData(
                        Event.newBuilder().setAsyncPlayerPreLogin(
                            AsyncPlayerPreLoginEvent.newBuilder()
                                .setKickMessage(apple.kickMessage() != null
                                    ? GsonComponentSerializer.gson().serialize(apple.kickMessage())
                                    : "")
                        )
                    );
                }
                case org.bukkit.event.player.PlayerPreLoginEvent pple -> {
                    if (pple.getResult() != org.bukkit.event.player.PlayerPreLoginEvent.Result.ALLOWED) {
                        resp.setCancelled(true);
                    }
                    resp.setData(
                        Event.newBuilder().setPlayerPreLogin(
                            PlayerPreLoginEvent.newBuilder()
                                .setKickMessage(pple.getKickMessage() != null
                                    ? GsonComponentSerializer.gson().serialize(Component.text(pple.getKickMessage()))
                                    : "")
                        )
                    );
                }
                case org.bukkit.event.player.PlayerLoginEvent ple -> {
                    if (ple.getResult() != org.bukkit.event.player.PlayerLoginEvent.Result.ALLOWED) {
                        resp.setCancelled(true);
                    }
                    resp.setData(
                        Event.newBuilder().setPlayerLogin(
                            PlayerLoginEvent.newBuilder()
                                .setKickMessage(ple.kickMessage() != null
                                    ? GsonComponentSerializer.gson().serialize(ple.kickMessage())
                                    : "")
                        )
                    );
                }
                case org.bukkit.event.player.PlayerKickEvent pke -> resp.setData(
                    Event.newBuilder().setPlayerKick(
                        PlayerKickEvent.newBuilder()
                            .setReason(pke.reason() != null
                                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(pke.reason())
                                : "")
                    )
                );
                case org.bukkit.event.player.PlayerTeleportEvent pte -> {
                    Location to = pte.getTo();
                    if (to != null) {
                        resp.setData(
                            Event.newBuilder().setPlayerTeleport(
                                PlayerTeleportEvent.newBuilder()
                                    .setToX(to.getX())
                                    .setToY(to.getY())
                                    .setToZ(to.getZ())
                            )
                        );
                    }
                }
                case org.bukkit.event.player.PlayerMoveEvent pme -> {
                    Location to = pme.getTo();
                    if (to != null) {
                        resp.setData(
                            Event.newBuilder().setPlayerMove(
                                PlayerMoveEvent.newBuilder()
                                    .setToX(to.getX())
                                    .setToY(to.getY())
                                    .setToZ(to.getZ())
                            )
                        );
                    }
                }
                case org.bukkit.event.player.PlayerRespawnEvent pre -> {
                    Location loc = pre.getRespawnLocation();
                    if (loc != null) {
                        resp.setData(
                            Event.newBuilder().setPlayerRespawn(
                                PlayerRespawnEvent.newBuilder()
                                    .setPosX(loc.getX())
                                    .setPosY(loc.getY())
                                    .setPosZ(loc.getZ())
                                    .setYaw(loc.getYaw())
                                    .setPitch(loc.getPitch())
                            )
                        );
                    }
                }
                case org.bukkit.event.player.PlayerExpChangeEvent pece -> resp.setData(
                    Event.newBuilder().setPlayerExpChange(
                        PlayerExpChangeEvent.newBuilder()
                            .setAmount(pece.getAmount())
                    )
                );
                case org.bukkit.event.player.PlayerCommandPreprocessEvent pcpe -> resp.setData(
                    Event.newBuilder().setPlayerCommandPreprocess(
                        PlayerCommandPreprocessEvent.newBuilder()
                            .setCommand(pcpe.getMessage())
                    )
                );
                case org.bukkit.event.server.ServerCommandEvent sce -> resp.setData(
                    Event.newBuilder().setServerCommand(
                        ServerCommandEvent.newBuilder()
                            .setCommand(sce.getCommand())
                    )
                );
                case org.bukkit.event.server.TabCompleteEvent tce -> resp.setData(
                    Event.newBuilder().setTabComplete(
                        TabCompleteEvent.newBuilder()
                            .setBuffer(tce.getBuffer())
                            .addAllCompletions(tce.getCompletions())
                    )
                );
                case org.bukkit.event.server.ServerListPingEvent slpe -> resp.setData(
                    Event.newBuilder().setServerListPing(
                        ServerListPingEvent.newBuilder()
                            .setMotd(slpe.motd() != null
                                ? GsonComponentSerializer.gson().serialize(slpe.motd())
                                : "")
                            .setMaxPlayers(slpe.getMaxPlayers())
                            .setNumPlayers(slpe.getNumPlayers())
                    )
                );
                case org.bukkit.event.entity.PlayerDeathEvent pde -> resp.setData(
                    Event.newBuilder().setPlayerDeath(
                        PlayerDeathEvent.newBuilder()
                            .setDroppedExp(pde.getDroppedExp())
                            .setKeepInventory(pde.getKeepInventory())
                            .setDeathMessage(pde.deathMessage() != null
                                ? GsonComponentSerializer.gson().serialize(pde.deathMessage())
                                : "")
                    )
                );
                case org.bukkit.event.entity.EntityDeathEvent ede -> resp.setData(
                    Event.newBuilder().setEntityDeath(
                        EntityDeathEvent.newBuilder()
                            .setDroppedExp(ede.getDroppedExp())
                    )
                );
                case org.bukkit.event.entity.EntityRegainHealthEvent erhe -> resp.setData(
                    Event.newBuilder().setEntityRegainHealth(
                        EntityRegainHealthEvent.newBuilder()
                            .setAmount((float) erhe.getAmount())
                    )
                );
                case org.bukkit.event.entity.EntityDamageEvent ede -> resp.setData(
                    Event.newBuilder().setEntityDamage(
                        EntityDamageEvent.newBuilder()
                            .setDamage((float) ede.getDamage())
                    )
                );
                case org.bukkit.event.entity.FoodLevelChangeEvent flce -> resp.setData(
                    Event.newBuilder().setFoodLevelChange(
                        FoodLevelChangeEvent.newBuilder()
                            .setFoodLevel(Math.max(0, flce.getFoodLevel()))
                    )
                );
                default -> {}
            }

            return resp.build().toByteArray();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Exception serializing event response: " + t.getMessage(), t);
            return FireEventResponse.newBuilder().setCancelled(false).build().toByteArray();
        }
    }

    @NotNull
    private static Component parseJsonOrPlain(@NotNull String raw) {
        if (raw.isEmpty()) return Component.empty();
        try {
            return GsonComponentSerializer.gson().deserialize(raw);
        } catch (Throwable ignored) {
            return Component.text(raw);
        }
    }

    @Nullable
    private static org.bukkit.event.Event createGenericBukkitEvent(String className, com.google.protobuf.Message protoMsg) {
        try {
            Class<?> clazz = Class.forName(className);
            java.util.List<Object> argsList = new java.util.ArrayList<>();
            World defaultWorld = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);

            for (var desc : protoMsg.getDescriptorForType().getFields()) {
                Object val = protoMsg.getField(desc);
                String name = desc.getName();
                if (val instanceof patchbukkit.common.UUID u) {
                    String uuidStr = u.getValue();
                    if (name.contains("world")) {
                        World w = getWorld(uuidStr);
                        if (w != null) argsList.add(w);
                    } else if (name.contains("player") || name.contains("owner")) {
                        Player p = getPlayer(uuidStr.isEmpty() ? "00000000-0000-0000-0000-000000000001" : uuidStr);
                        if (p != null) argsList.add(p);
                    } else {
                        Entity ent = getEntity(uuidStr.isEmpty() ? 1 : Math.abs(uuidStr.hashCode()));
                        if (ent != null) argsList.add(ent);
                    }
                } else if ((name.endsWith("_id") || name.equals("id"))
                        && val instanceof Integer eid
                        && !name.equals("map_id")
                        && !name.equals("packet_id")) {
                    Entity ent = getEntity(eid);
                    if (ent != null) argsList.add(ent);
                    argsList.add(val);
                } else if (name.equals("chunk_x") && val instanceof Number xNum) {
                    int cx = xNum.intValue();
                    int cz = 0;
                    var zDesc = protoMsg.getDescriptorForType().findFieldByName("chunk_z");
                    if (zDesc != null && protoMsg.getField(zDesc) instanceof Number zn) cz = zn.intValue();
                    if (defaultWorld != null) {
                        argsList.add(defaultWorld.getChunkAt(cx, cz));
                    }
                    argsList.add(val);
                } else if (name.endsWith("_x") && val instanceof Number xNum) {
                    double dx = xNum.doubleValue();
                    double dy = 64.0;
                    double dz = 0.0;
                    String prefix = name.substring(0, name.length() - 2);
                    var yDesc = protoMsg.getDescriptorForType().findFieldByName(prefix + "_y");
                    var zDesc = protoMsg.getDescriptorForType().findFieldByName(prefix + "_z");
                    if (yDesc != null && protoMsg.getField(yDesc) instanceof Number yn) dy = yn.doubleValue();
                    if (zDesc != null && protoMsg.getField(zDesc) instanceof Number zn) dz = zn.doubleValue();
                    if (defaultWorld != null) {
                        Block b = defaultWorld.getBlockAt((int) dx, (int) dy, (int) dz);
                        argsList.add(b);
                        argsList.add(b.getState());
                        argsList.add(new Location(defaultWorld, dx, dy, dz));
                    }
                    argsList.add(new org.bukkit.util.Vector(dx, dy, dz));
                    argsList.add(val);
                } else if ((name.contains("item") || name.contains("book") || name.contains("weapon")) && val instanceof String itemName) {
                    org.bukkit.Material mat = org.bukkit.Material.matchMaterial(itemName);
                    if (mat == null || mat.isAir()) mat = org.bukkit.Material.STONE;
                    argsList.add(new ItemStack(mat, 1));
                    argsList.add(val);
                } else {
                    argsList.add(val);
                }
            }
            if (defaultWorld != null) {
                argsList.add(defaultWorld);
            }
            return instantiateBukkitEvent(clazz, argsList.toArray());
        } catch (Throwable t) {
            return null;
        }
    }

    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static org.bukkit.event.Event instantiateBukkitEvent(Class<?> clazz, Object... args) {
        Constructor<?>[] ctors = clazz.getConstructors();
        for (Constructor<?> ctor : ctors) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            Object[] matchedArgs = new Object[paramTypes.length];
            boolean[] used = new boolean[args.length];
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> pt = paramTypes[i];
                Object found = null;
                for (int j = 0; j < args.length; j++) {
                    if (used[j]) continue;
                    Object a = args[j];
                    if (a != null && isParamCompatible(pt, a)) {
                        found = convertNumberIfNeeded(pt, a);
                        used[j] = true;
                        break;
                    }
                }
                if (found == null && pt.isEnum()) {
                    Object[] constants = pt.getEnumConstants();
                    for (int j = 0; j < args.length; j++) {
                        if (args[j] instanceof String s && !s.isEmpty()) {
                            for (Object c : constants) {
                                if (((Enum<?>) c).name().equalsIgnoreCase(s)
                                        || ((Enum<?>) c).name().replace("_", "").equalsIgnoreCase(s.replace("_", ""))) {
                                    found = c;
                                    break;
                                }
                            }
                            if (found != null) break;
                        }
                    }
                    if (found == null && constants != null && constants.length > 0) {
                        found = constants[0];
                    }
                }
                if (found == null && Entity.class.isAssignableFrom(pt) && !Player.class.isAssignableFrom(pt)) {
                    for (int j = 0; j < args.length; j++) {
                        if (args[j] instanceof Entity ent && pt.isAssignableFrom(ent.getClass())) {
                            found = ent;
                            break;
                        }
                    }
                    if (found == null) {
                        found = getEntityForClass(pt, i + 1);
                    }
                }
                if (found == null) {
                    for (Object a : args) {
                        if (a != null && isParamCompatible(pt, a)) {
                            found = convertNumberIfNeeded(pt, a);
                            break;
                        }
                    }
                }
                if (found != null) {
                    matchedArgs[i] = found;
                } else if (pt == boolean.class || pt == Boolean.class) {
                    matchedArgs[i] = false;
                } else if (pt == int.class || pt == Integer.class) {
                    matchedArgs[i] = 0;
                } else if (pt == double.class || pt == Double.class) {
                    matchedArgs[i] = 0.0;
                } else if (pt == float.class || pt == Float.class) {
                    matchedArgs[i] = 0.0f;
                } else if (pt == long.class || pt == Long.class) {
                    matchedArgs[i] = 0L;
                } else if (pt == short.class || pt == Short.class) {
                    matchedArgs[i] = (short) 0;
                } else if (pt == byte.class || pt == Byte.class) {
                    matchedArgs[i] = (byte) 0;
                } else if (pt == String.class || pt == CharSequence.class) {
                    matchedArgs[i] = "";
                } else if (pt == Component.class) {
                    matchedArgs[i] = Component.empty();
                } else if (pt == ItemStack.class) {
                    matchedArgs[i] = new ItemStack(org.bukkit.Material.STONE, 1);
                } else if (pt == org.bukkit.util.Vector.class) {
                    matchedArgs[i] = new org.bukkit.util.Vector(0, 0, 0);
                } else if (pt == org.bukkit.block.BlockFace.class) {
                    matchedArgs[i] = org.bukkit.block.BlockFace.NORTH;
                } else if (pt == org.bukkit.inventory.EquipmentSlot.class) {
                    matchedArgs[i] = org.bukkit.inventory.EquipmentSlot.HAND;
                } else if (pt == org.bukkit.damage.DamageSource.class) {
                    matchedArgs[i] = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build();
                } else if (pt == java.util.List.class || pt == java.util.Collection.class || pt == Iterable.class) {
                    matchedArgs[i] = new java.util.ArrayList<>();
                } else if (pt == java.util.Set.class) {
                    matchedArgs[i] = new java.util.HashSet<>();
                } else if (pt == java.util.Map.class) {
                    matchedArgs[i] = new java.util.HashMap<>();
                } else if (pt == org.bukkit.NamespacedKey.class || pt == net.kyori.adventure.key.Key.class) {
                    matchedArgs[i] = org.bukkit.NamespacedKey.minecraft("default");
                } else if (World.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
                } else if (Location.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = Bukkit.getWorlds().isEmpty() ? null : new Location(Bukkit.getWorlds().get(0), 0, 64, 0);
                } else if (Block.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getBlockAt(0, 64, 0);
                } else if (org.bukkit.block.BlockState.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getBlockAt(0, 64, 0).getState();
                } else if (org.bukkit.block.data.BlockData.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = Bukkit.createBlockData(org.bukkit.Material.STONE);
                } else if (org.bukkit.Chunk.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getChunkAt(0, 0);
                } else if (Player.class.isAssignableFrom(pt)
                        || org.bukkit.entity.HumanEntity.class.isAssignableFrom(pt)
                        || org.bukkit.command.CommandSender.class.isAssignableFrom(pt)) {
                    matchedArgs[i] = getPlayer("00000000-0000-0000-0000-000000000001");
                } else {
                    matchedArgs[i] = null;
                }
            }
            try {
                return (org.bukkit.event.Event) ctor.newInstance(matchedArgs);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean isParamCompatible(Class<?> pt, Object arg) {
        if (pt.isAssignableFrom(arg.getClass())) return true;
        if ((pt == boolean.class || pt == Boolean.class) && arg instanceof Boolean) return true;
        if ((pt == int.class || pt == Integer.class) && arg instanceof Number) return true;
        if ((pt == double.class || pt == Double.class) && arg instanceof Number) return true;
        if ((pt == float.class || pt == Float.class) && arg instanceof Number) return true;
        if ((pt == long.class || pt == Long.class) && arg instanceof Number) return true;
        if ((pt == short.class || pt == Short.class) && arg instanceof Number) return true;
        if ((pt == byte.class || pt == Byte.class) && arg instanceof Number) return true;
        return false;
    }

    private static Object convertNumberIfNeeded(Class<?> pt, Object arg) {
        if (arg instanceof Number n) {
            if (pt == int.class || pt == Integer.class) return n.intValue();
            if (pt == double.class || pt == Double.class) return n.doubleValue();
            if (pt == float.class || pt == Float.class) return n.floatValue();
            if (pt == long.class || pt == Long.class) return n.longValue();
            if (pt == short.class || pt == Short.class) return n.shortValue();
            if (pt == byte.class || pt == Byte.class) return n.byteValue();
        }
        return arg;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Entity getEntityForClass(Class<?> targetClass, int fallbackId) {
        if (!org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) return null;
        net.minecraft.server.level.ServerLevel overworld = org.patchbukkit.bootstrap.HeadlessPaperServer.get().overworld();
        if (overworld == null) return null;

        net.minecraft.world.entity.EntityType<?> nmsType = null;
        if (targetClass.isAssignableFrom(org.bukkit.entity.Pig.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.PIG;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Minecart.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.MINECART;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Horse.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.HORSE;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Strider.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.STRIDER;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.ItemFrame.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.ITEM_FRAME;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.AreaEffectCloud.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.AREA_EFFECT_CLOUD;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Bat.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.BAT;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Creeper.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.CREEPER;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.EnderDragon.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.ENDER_DRAGON;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.ThrownExpBottle.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.EXPERIENCE_BOTTLE;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Firework.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.FIREWORK_ROCKET;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.SplashPotion.class)
                || targetClass.isAssignableFrom(org.bukkit.entity.LingeringPotion.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.SPLASH_POTION;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Item.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.ITEM;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.LightningStrike.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.PigZombie.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.ZOMBIFIED_PIGLIN;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Piglin.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.PIGLIN;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Arrow.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.ARROW;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Sheep.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.SHEEP;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Slime.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.SLIME;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Evoker.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.EVOKER;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Villager.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.VILLAGER;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Warden.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.WARDEN;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.ArmorStand.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.ARMOR_STAND;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.FishHook.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.FISHING_BOBBER;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Egg.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.EGG;
        } else if (targetClass.isAssignableFrom(org.bukkit.entity.Wolf.class)) {
            nmsType = net.minecraft.world.entity.EntityTypes.WOLF;
        }

        if (nmsType != null) {
            try {
                net.minecraft.world.entity.Entity created =
                    nmsType.create(overworld, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (created != null) {
                    created.setId(fallbackId);
                    created.setUUID(new java.util.UUID(0L, fallbackId));
                    return created.getBukkitEntity();
                }
            } catch (Throwable ignored) {}
        }

        try {
            World w = overworld.getWorld();
            if (w != null && Entity.class.isAssignableFrom(targetClass)) {
                return w.createEntity(new Location(w, 0, 64, 0), (Class<? extends Entity>) targetClass);
            }
        } catch (Throwable ignored) {}

        return getEntity(fallbackId);
    }

    @Nullable
    public static Player getPlayer(@NotNull String uuidStr) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            Player player = Bukkit.getServer() != null ? Bukkit.getServer().getPlayer(uuid) : null;
            if (player == null && org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                var playerList = org.patchbukkit.bootstrap.HeadlessPaperServer.get().getPlayerList();
                if (playerList != null) {
                    var serverPlayer = playerList.getPlayer(uuid);
                    if (serverPlayer != null) {
                        return serverPlayer.getBukkitEntity();
                    }
                }
                String fallbackName = PRELOGIN_PLAYER_NAMES.getOrDefault(uuid, "Player");
                var sp = org.patchbukkit.network.VirtualChannelManager.getInstance()
                    .getOrCreateServerPlayer(uuid, fallbackName, -1);
                if (sp != null) {
                    return sp.getBukkitEntity();
                }
            }
            return player;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public static Entity getEntity(int entityId) {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            for (net.minecraft.server.level.ServerLevel level : org.patchbukkit.bootstrap.HeadlessPaperServer.get().getAllLevels()) {
                net.minecraft.world.entity.Entity nms = level.getEntity(entityId);
                if (nms != null) return nms.getBukkitEntity();
            }
            java.util.UUID resolvedUuid = new java.util.UUID(0L, entityId);
            if (org.patchbukkit.bridge.NativeFastPathFfi.isInitialized()) {
                try {
                    patchbukkit.entity.GetEntityUuidResponse uuidRes = patchbukkit.bridge.NativeBridgeFfi.getEntityUuid(
                        patchbukkit.entity.GetEntityUuidRequest.newBuilder().setEntityId(entityId).build()
                    );
                    if (uuidRes != null && uuidRes.hasUuid() && !uuidRes.getUuid().getValue().isEmpty()) {
                        resolvedUuid = org.patchbukkit.bridge.BridgeUtils.convertUuid(uuidRes.getUuid());
                    }
                } catch (Throwable ignored) {}
            }
            net.minecraft.server.level.ServerLevel overworld = org.patchbukkit.bootstrap.HeadlessPaperServer.get().overworld();
            if (overworld != null) {
                net.minecraft.world.entity.Entity fallback =
                    net.minecraft.world.entity.EntityTypes.PIG.create(overworld, net.minecraft.world.entity.EntitySpawnReason.LOAD);
                if (fallback != null) {
                    fallback.setId(entityId);
                    fallback.setUUID(resolvedUuid);
                    return fallback.getBukkitEntity();
                }
            }
        }
        return null;
    }

    @Nullable
    public static World getWorld(@NotNull String uuidStr) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                World w = org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getWorld(uuid);
                if (w != null) return w;
            }
            return Bukkit.getWorld(uuid);
        } catch (Exception e) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
    }
}
