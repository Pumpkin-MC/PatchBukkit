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
import org.patchbukkit.bridge.BridgeUtils;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.events.*;

import java.lang.reflect.Constructor;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PatchBukkitEventFactory {
    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");

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
        org.bukkit.event.Event event = createEventFromBytes(data);
        if (event == null) {
            return FireEventResponse.newBuilder().setCancelled(false).build().toByteArray();
        }
        if (Bukkit.getServer() instanceof org.patchbukkit.PatchBukkitServer server) {
            server.getEventManager().fireEvent(event, pluginName);
            // Unregister disconnecting players only after listeners ran, so they
            // still resolve during the event. Kicks disconnect too; if Pumpkin
            // emits leave afterwards, the second removal is a harmless no-op.
            // (Null guards: generically constructed events may carry null players.)
            if (event instanceof org.bukkit.event.player.PlayerQuitEvent quit
                    && quit.getPlayer() != null) {
                server.unregisterPlayer(quit.getPlayer().getUniqueId());
            } else if (event instanceof org.bukkit.event.player.PlayerKickEvent kick
                    && kick.getPlayer() != null) {
                server.unregisterPlayer(kick.getPlayer().getUniqueId());
            }
        }
        return toFireEventResponse(event);
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
                LOGGER.log(Level.FINE, "Dropping CHUNK_SAVE: no Bukkit equivalent (ChunkSave is Pumpkin-internal)");
                yield null;
            }
            case CHUNK_SEND -> {
                LOGGER.log(Level.FINE, "Dropping CHUNK_SEND: no Bukkit equivalent (ChunkSend is Pumpkin-internal)");
                yield null;
            }
            case CHUNK_UNLOAD -> {
                var ev = event.getChunkUnload();
                // The bridge only carries chunk coords (no world); attribute to the
                // first world like the other world fallbacks in this factory.
                // getChunkAt is a pure local wrapper here, so this cannot reload
                // the chunk being unloaded.
                if (Bukkit.getWorlds().isEmpty()) yield null;
                World world = Bukkit.getWorlds().get(0);
                org.bukkit.Chunk chunk = world.getChunkAt(ev.getChunkX(), ev.getChunkZ());
                yield new org.bukkit.event.world.ChunkUnloadEvent(chunk, true);
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
                yield createGenericBukkitEvent("org.bukkit.event.weather.WeatherChangeEvent", ev);
            }
            case THUNDER_CHANGE -> {
                var ev = event.getThunderChange();
                yield createGenericBukkitEvent("org.bukkit.event.weather.ThunderChangeEvent", ev);
            }
            case WORLD_INIT -> {
                var ev = event.getWorldInit();
                yield createGenericBukkitEvent("org.bukkit.event.world.WorldInitEvent", ev);
            }
            case WORLD_LOAD -> {
                var ev = event.getWorldLoad();
                yield createGenericBukkitEvent("org.bukkit.event.world.WorldLoadEvent", ev);
            }
            case WORLD_UNLOAD -> {
                var ev = event.getWorldUnload();
                yield createGenericBukkitEvent("org.bukkit.event.world.WorldUnloadEvent", ev);
            }
            case WORLD_SAVE -> {
                var ev = event.getWorldSave();
                yield createGenericBukkitEvent("org.bukkit.event.world.WorldSaveEvent", ev);
            }
            case SERVER_LIST_PING -> {
                var ev = event.getServerListPing();
                yield createGenericBukkitEvent("org.bukkit.event.server.ServerListPingEvent", ev);
            }
            case MAP_INITIALIZE -> {
                var ev = event.getMapInitialize();
                yield createGenericBukkitEvent("org.bukkit.event.server.MapInitializeEvent", ev);
            }
            case PACKET_RECEIVED -> {
                LOGGER.log(Level.FINE, "Dropping PACKET_RECEIVED: no Bukkit equivalent");
                yield null;
            }
            case PACKET_SENT -> {
                LOGGER.log(Level.FINE, "Dropping PACKET_SENT: no Bukkit equivalent");
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
                yield createGenericBukkitEvent("org.bukkit.event.server.ServerLoadEvent", ev);
            }
            case SERVER_TICK_END -> {
                var ev = event.getServerTickEnd();
                yield createGenericBukkitEvent("com.destroystokyo.paper.event.server.ServerTickEndEvent", ev);
            }
            case SERVER_TICK_START -> {
                var ev = event.getServerTickStart();
                yield createGenericBukkitEvent("com.destroystokyo.paper.event.server.ServerTickStartEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.server.TabCompleteEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryCreativeEvent", ev);
            }
            case INVENTORY_DRAG -> {
                var ev = event.getInventoryDrag();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryDragEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryOpenEvent", ev);
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
                yield new org.bukkit.event.block.BlockBreakEvent(b, player);
            }
            case BLOCK_BRUSH -> {
                LOGGER.log(Level.FINE, "Dropping BLOCK_BRUSH: no Bukkit equivalent");
                yield null;
            }
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
                yield createGenericBukkitEvent("org.bukkit.event.block.BlockExplodeEvent", ev);
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
            case DIALOG_CLEAR -> {
                LOGGER.log(Level.FINE, "Dropping DIALOG_CLEAR: no Bukkit equivalent (Paper dialog)");
                yield null;
            }
            case DIALOG_CLICK_ACTION -> {
                LOGGER.log(Level.FINE, "Dropping DIALOG_CLICK_ACTION: no Bukkit equivalent (Paper dialog)");
                yield null;
            }
            case DIALOG_SHOW -> {
                LOGGER.log(Level.FINE, "Dropping DIALOG_SHOW: no Bukkit equivalent (Paper dialog)");
                yield null;
            }
            case ASYNC_PLAYER_CHAT -> {
                var ev = event.getAsyncPlayerChat();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new org.bukkit.event.player.AsyncPlayerChatEvent(true, player, ev.getMessage(), new java.util.HashSet<>(Bukkit.getOnlinePlayers()));
            }
            case ASYNC_PLAYER_PRE_LOGIN -> {
                var ev = event.getAsyncPlayerPreLogin();
                yield createGenericBukkitEvent("org.bukkit.event.player.AsyncPlayerPreLoginEvent", ev);
            }
            case BEDROCK_FORM_RESPONSE -> {
                LOGGER.log(Level.FINE, "Dropping BEDROCK_FORM_RESPONSE: no Bukkit equivalent");
                yield null;
            }
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
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerExpChangeEvent", ev);
            }
            case PLAYER_FISH -> {
                var ev = event.getPlayerFish();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerFishEvent", ev);
            }
            case INVENTORY_CLOSE -> {
                var ev = event.getInventoryClose();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryCloseEvent", ev);
            }
            case INVENTORY_CLICK -> {
                var ev = event.getInventoryClick();
                yield createGenericBukkitEvent("org.bukkit.event.inventory.InventoryClickEvent", ev);
            }
            case PLAYER_ITEM_HELD -> {
                var ev = event.getPlayerItemHeld();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerItemHeldEvent", ev);
            }
            case PLAYER_ADVANCEMENT_DONE -> {
                var ev = event.getPlayerAdvancementDone();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerAdvancementDoneEvent", ev);
            }
            case PLAYER_ANIMATION -> {
                var ev = event.getPlayerAnimation();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerAnimationEvent", ev);
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
                LOGGER.log(Level.FINE, "Dropping PLAYER_CHANGE_WORLD: no Bukkit equivalent, use PLAYER_CHANGED_WORLD");
                yield null;
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
                // Bukkit ordering: async listeners first, then the sync event.
                // Pumpkin only fires the sync chat event, so the async stage
                // runs here before the sync event is handed to listeners.
                org.bukkit.event.player.AsyncPlayerChatEvent async =
                    new org.bukkit.event.player.AsyncPlayerChatEvent(true, player,
                        ev.getMessage(), new java.util.HashSet<>(Bukkit.getOnlinePlayers()));
                try {
                    Bukkit.getPluginManager().callEvent(async);
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "AsyncPlayerChatEvent listener failed: " + t.getMessage());
                }
                org.bukkit.event.player.PlayerChatEvent sync =
                    new org.bukkit.event.player.PlayerChatEvent(player, async.getMessage(),
                        async.getFormat(), async.getRecipients());
                sync.setCancelled(async.isCancelled());
                yield sync;
            }
            case PLAYER_COMMAND_PREPROCESS -> {
                var ev = event.getPlayerCommandPreprocess();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerCommandPreprocessEvent", ev);
            }
            case PLAYER_COMMAND_SEND -> {
                var ev = event.getPlayerCommandSend();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerCommandSendEvent", ev);
            }
            case PLAYER_CUSTOM_PAYLOAD -> {
                LOGGER.log(Level.FINE, "Dropping PLAYER_CUSTOM_PAYLOAD: no Bukkit equivalent");
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
                org.bukkit.block.Block b = player.getWorld().getBlockAt(ev.getClickedPosX(), ev.getClickedPosY(), ev.getClickedPosZ());
                yield new org.bukkit.event.player.PlayerInteractEvent(player, act, player.getInventory().getItemInMainHand(), b, org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
            }
            case PLAYER_INTERACT_UNKNOWN_ENTITY -> {
                LOGGER.log(Level.FINE, "Dropping PLAYER_INTERACT_UNKNOWN_ENTITY: no Bukkit equivalent");
                yield null;
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
                Player player = getPlayer(ev.getPlayerUuid().getValue(), ev.getPlayerName());
                if (player == null) yield null;
                Component msg = ev.getJoinMessage().isEmpty() ? Component.empty() : GsonComponentSerializer.gson().deserialize(ev.getJoinMessage());
                yield new org.bukkit.event.player.PlayerJoinEvent(player, msg);
            }
            case PLAYER_KICK -> {
                var ev = event.getPlayerKick();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerKickEvent", ev);
            }
            case PLAYER_LEASH_ENTITY -> {
                var ev = event.getPlayerLeashEntity();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PlayerLeashEntityEvent", ev);
            }
            case PLAYER_LEAVE -> {
                var ev = event.getPlayerLeave();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
                Component msg = ev.getLeaveMessage().isEmpty() ? Component.empty() : GsonComponentSerializer.gson().deserialize(ev.getLeaveMessage());
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
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerLoginEvent", ev);
            }
            case PLAYER_MOVE -> {
                var ev = event.getPlayerMove();
                Player player = getPlayer(ev.getPlayerUuid().getValue());
                if (player == null) yield null;
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
            case PLAYER_PERMISSION_CHECK -> {
                LOGGER.log(Level.FINE, "Dropping PLAYER_PERMISSION_CHECK: no Bukkit equivalent");
                yield null;
            }
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
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerPreLoginEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRegisterChannelEvent", ev);
            }
            case PLAYER_RESOURCE_PACK_STATUS -> {
                var ev = event.getPlayerResourcePackStatus();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerResourcePackStatusEvent", ev);
            }
            case PLAYER_RESPAWN -> {
                var ev = event.getPlayerRespawn();
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerRespawnEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.player.PlayerTeleportEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDamageEvent", ev);
            }
            case ENTITY_DAMAGE_BY_BLOCK -> {
                var ev = event.getEntityDamageByBlock();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDamageByBlockEvent", ev);
            }
            case ENTITY_DAMAGE_BY_ENTITY -> {
                var ev = event.getEntityDamageByEntity();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDamageByEntityEvent", ev);
            }
            case ENTITY_DEATH -> {
                var ev = event.getEntityDeath();
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityDeathEvent", ev);
            }
            case PLAYER_DEATH -> {
                var ev = event.getPlayerDeath();
                yield createGenericBukkitEvent("org.bukkit.event.entity.PlayerDeathEvent", ev);
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
                yield createGenericBukkitEvent("org.bukkit.event.entity.EntityExplodeEvent", ev);
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
                LOGGER.log(Level.FINE, "Dropping ENTITY_TARGET_BLOCK: no Bukkit equivalent");
                yield null;
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
            case VILLAGER_REPUTATION_CHANGE -> {
                LOGGER.log(Level.FINE, "Dropping VILLAGER_REPUTATION_CHANGE: no Bukkit equivalent");
                yield null;
            }
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
            case DATA_NOT_SET -> {
                LOGGER.log(Level.FINE, "Dropping event with DATA_NOT_SET");
                yield null;
            }
            default -> {
                LOGGER.log(Level.FINE, "Dropping unknown event type: {0}", event.getDataCase());
                yield null;
            }
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
            return FireEventResponse.newBuilder()
                .setCancelled(cancelled)
                .build()
                .toByteArray();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Exception serializing event response: " + t.getMessage(), t);
            return FireEventResponse.newBuilder().setCancelled(false).build().toByteArray();
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
                if (name.contains("player_uuid") && val instanceof patchbukkit.common.UUID u) {
                    Player p = getPlayer(u.getValue());
                    if (p != null) argsList.add(p);
                } else if (name.contains("entity_id") && val instanceof Integer eid) {
                    Entity ent = getEntity(eid);
                    if (ent != null) argsList.add(ent);
                } else if (name.contains("world_uuid") && val instanceof patchbukkit.common.UUID u) {
                    World w = getWorld(u.getValue());
                    if (w != null) argsList.add(w);
                } else if (name.endsWith("_x") && val instanceof Number xNum) {
                    int x = xNum.intValue();
                    int y = 64;
                    int z = 0;
                    var yDesc = protoMsg.getDescriptorForType().findFieldByName(name.substring(0, name.length() - 2) + "_y");
                    var zDesc = protoMsg.getDescriptorForType().findFieldByName(name.substring(0, name.length() - 2) + "_z");
                    if (yDesc != null && protoMsg.getField(yDesc) instanceof Number yn) y = yn.intValue();
                    if (zDesc != null && protoMsg.getField(zDesc) instanceof Number zn) z = zn.intValue();
                    if (defaultWorld != null) {
                        Block b = defaultWorld.getBlockAt(x, y, z);
                        argsList.add(b);
                        argsList.add(b.getState());
                        argsList.add(new Location(defaultWorld, x, y, z));
                    }
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
    private static org.bukkit.event.Event instantiateBukkitEvent(Class<?> clazz, Object... args) {
        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            Object[] matchedArgs = new Object[paramTypes.length];
            boolean match = true;
            for (int i = 0; i < paramTypes.length; i++) {
                Class<?> pt = paramTypes[i];
                Object found = null;
                for (Object a : args) {
                    if (a != null && pt.isAssignableFrom(a.getClass())) {
                        found = a;
                        break;
                    }
                }
                if (found != null) {
                    matchedArgs[i] = found;
                } else if (pt == boolean.class) {
                    matchedArgs[i] = false;
                } else if (pt == int.class) {
                    matchedArgs[i] = 0;
                } else if (pt == double.class) {
                    matchedArgs[i] = 0.0;
                } else if (pt == float.class) {
                    matchedArgs[i] = 0.0f;
                } else if (pt == long.class) {
                    matchedArgs[i] = 0L;
                } else if (pt == String.class) {
                    matchedArgs[i] = "";
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

    @Nullable
    public static Player getPlayer(@NotNull String uuidStr) {
        return getPlayer(uuidStr, "Player");
    }

    @Nullable
    public static Player getPlayer(@NotNull String uuidStr, @NotNull String defaultName) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            Player player = Bukkit.getServer().getPlayer(uuid);
            if (player == null) {
                String name = defaultName;
                // Last resort before the "Player" placeholder: ask the server for
                // the real name. This covers players that joined without any join
                // listener registered (or before JVM init completed).
                try {
                    var info = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(uuid));
                    if (info != null && !info.getPlayerName().isEmpty()) {
                        name = info.getPlayerName();
                    }
                } catch (Throwable ignored) {}
                if (name == null || name.isEmpty()) {
                    name = "Player";
                }
                player = new org.patchbukkit.entity.PatchBukkitPlayer(uuid, name);
                if (Bukkit.getServer() instanceof org.patchbukkit.PatchBukkitServer server) {
                    server.registerPlayer(player);
                }
            }
            return player;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NotNull
    public static Entity getEntity(int entityId) {
        return new org.patchbukkit.entity.PatchBukkitEntity(new java.util.UUID(0, entityId), "Entity-" + entityId);
    }

    @Nullable
    public static World getWorld(@NotNull String uuidStr) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            return Bukkit.getWorld(uuid);
        } catch (Exception e) {
            return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
    }
}
