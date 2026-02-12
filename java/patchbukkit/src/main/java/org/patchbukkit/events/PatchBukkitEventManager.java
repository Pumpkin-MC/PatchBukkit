package org.patchbukkit.events;

import org.bukkit.Server;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Warning;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.AuthorNagException;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.patchbukkit.bridge.BridgeUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;

import com.google.common.collect.Sets;

import co.aikar.timings.TimedEventExecutor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.events.CallEventRequest;
import patchbukkit.events.PlayerChatEvent;
import patchbukkit.events.PlayerCommandEvent;
import patchbukkit.events.PlayerJoinEvent;
import patchbukkit.events.PlayerLeaveEvent;
import patchbukkit.events.PlayerMoveEvent;
import patchbukkit.events.ServerBroadcastEvent;
import patchbukkit.events.ServerCommandEvent;
import patchbukkit.events.BlockBreakEvent;
import patchbukkit.events.BlockDamageAbortEvent;
import patchbukkit.events.BlockDamageEvent;
import patchbukkit.events.BlockDispenseEvent;
import patchbukkit.events.BlockDropItemEntry;
import patchbukkit.events.BlockDropItemEvent;
import patchbukkit.events.BlockExplodeBlockEntry;
import patchbukkit.events.BlockExplodeEvent;
import patchbukkit.events.BlockFadeEvent;
import patchbukkit.events.BlockFertilizeBlockEntry;
import patchbukkit.events.BlockFertilizeEvent;
import patchbukkit.events.BlockFormEvent;
import patchbukkit.events.BlockFromToEvent;
import patchbukkit.events.BlockGrowEvent;
import patchbukkit.events.BlockPistonBlockEntry;
import patchbukkit.events.BlockPistonExtendEvent;
import patchbukkit.events.BlockPistonRetractEvent;
import patchbukkit.events.BlockRedstoneEvent;
import patchbukkit.events.BlockMultiPlaceBlockEntry;
import patchbukkit.events.BlockMultiPlaceEvent;
import patchbukkit.events.BlockPhysicsEvent;
import patchbukkit.events.BlockPlaceEvent;
import patchbukkit.events.NotePlayEvent;
import patchbukkit.events.SignChangeEvent;
import patchbukkit.events.TNTPrimeEvent;
import patchbukkit.events.MoistureChangeEvent;
import patchbukkit.events.SpongeAbsorbEvent;
import patchbukkit.events.SpongeAbsorbBlockEntry;
import patchbukkit.events.FluidLevelChangeEvent;
import patchbukkit.events.SpawnChangeEvent;
import patchbukkit.events.ServerListPingEvent;
import patchbukkit.events.PluginEnableEvent;
import patchbukkit.events.PluginDisableEvent;
import patchbukkit.events.ServiceRegisterEvent;
import patchbukkit.events.ServiceUnregisterEvent;
import patchbukkit.events.PlayerInteractEvent;
import patchbukkit.events.EntitySpawnEvent;
import patchbukkit.events.EntityDamageEvent;
import patchbukkit.events.EntityDeathEvent;
import patchbukkit.events.RegisterEventRequest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class PatchBukkitEventManager {

    private final Server server;

    public PatchBukkitEventManager(Server server) {
        this.server = server;
    }

    public void callEvent(@NotNull Event event) throws IllegalStateException {
        if (event.isAsynchronous() && this.server.isPrimaryThread()) {
            throw new IllegalStateException(event.getEventName() + " may only be triggered asynchronously.");
        } else if (!event.isAsynchronous() && !this.server.isPrimaryThread() && !this.server.isStopping()) {
            throw new IllegalStateException(event.getEventName() + " may only be triggered synchronously.");
        }

        var request = CallEventRequest.newBuilder();
        switch (event.getEventName()) {
            case "org.bukkit.event.player.PlayerJoinEvent":
                var castedEvent = (org.bukkit.event.player.PlayerJoinEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerJoin(
                        PlayerJoinEvent.newBuilder()
                            .setJoinMessage(castedEvent.joinMessage().toString())
                            .setPlayerUuid(BridgeUtils.convertUuid(castedEvent.getPlayer().getUniqueId())).build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerLoginEvent":
                var loginEvent = (org.bukkit.event.player.PlayerLoginEvent) event;
                String loginKick = loginEvent.getKickMessage();
                if (loginKick == null) {
                    loginKick = "";
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerLogin(
                        patchbukkit.events.PlayerLoginEvent.newBuilder()
                            .setKickMessage(loginKick)
                            .setPlayerUuid(BridgeUtils.convertUuid(loginEvent.getPlayer().getUniqueId()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.AsyncPlayerPreLoginEvent":
                var preLoginEvent = (org.bukkit.event.player.AsyncPlayerPreLoginEvent) event;
                String address = preLoginEvent.getAddress() != null
                    ? preLoginEvent.getAddress().getHostAddress()
                    : "";
                String result = preLoginEvent.getLoginResult() != null
                    ? preLoginEvent.getLoginResult().name()
                    : "ALLOWED";
                String kickMessage = preLoginEvent.getKickMessage() != null
                    ? preLoginEvent.getKickMessage()
                    : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setAsyncPlayerPreLogin(
                        patchbukkit.events.AsyncPlayerPreLoginEvent.newBuilder()
                            .setName(preLoginEvent.getName())
                            .setPlayerUuid(BridgeUtils.convertUuid(preLoginEvent.getUniqueId()))
                            .setAddress(address)
                            .setResult(result)
                            .setKickMessage(kickMessage)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerQuitEvent":
                var quitEvent = (org.bukkit.event.player.PlayerQuitEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerLeave(
                        PlayerLeaveEvent.newBuilder()
                            .setLeaveMessage(quitEvent.quitMessage().toString())
                            .setPlayerUuid(BridgeUtils.convertUuid(quitEvent.getPlayer().getUniqueId())).build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerMoveEvent":
                var moveEvent = (org.bukkit.event.player.PlayerMoveEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerMove(
                        PlayerMoveEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(moveEvent.getPlayer().getUniqueId()))
                            .setFrom(BridgeUtils.convertLocation(moveEvent.getFrom()))
                            .setTo(BridgeUtils.convertLocation(moveEvent.getTo()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerTeleportEvent":
                var teleportEvent = (org.bukkit.event.player.PlayerTeleportEvent) event;
                String cause = teleportEvent.getCause() != null ? teleportEvent.getCause().name() : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerTeleport(
                        patchbukkit.events.PlayerTeleportEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(teleportEvent.getPlayer().getUniqueId()))
                            .setFrom(BridgeUtils.convertLocation(teleportEvent.getFrom()))
                            .setTo(BridgeUtils.convertLocation(teleportEvent.getTo()))
                            .setCause(cause)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerChangedWorldEvent":
                var changeEvent = (org.bukkit.event.player.PlayerChangedWorldEvent) event;
                var previousWorld = changeEvent.getFrom();
                var currentWorld = changeEvent.getPlayer().getWorld();
                var location = changeEvent.getPlayer().getLocation();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerChangeWorld(
                        patchbukkit.events.PlayerChangeWorldEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(changeEvent.getPlayer().getUniqueId()))
                            .setPreviousWorld(BridgeUtils.convertWorld(previousWorld))
                            .setNewWorld(BridgeUtils.convertWorld(currentWorld))
                            .setPosition(BridgeUtils.convertLocation(location))
                            .setYaw(location.getYaw())
                            .setPitch(location.getPitch())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerGameModeChangeEvent":
                var gameModeChangeEvent = (org.bukkit.event.player.PlayerGameModeChangeEvent) event;
                var previousMode = gameModeChangeEvent.getPlayer().getGameMode();
                var nextMode = gameModeChangeEvent.getNewGameMode();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerGamemodeChange(
                        patchbukkit.events.PlayerGamemodeChangeEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(gameModeChangeEvent.getPlayer().getUniqueId()))
                            .setPreviousGamemode(previousMode != null ? previousMode.name() : "")
                            .setNewGamemode(nextMode != null ? nextMode.name() : "")
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerAdvancementDoneEvent":
                var advancementEvent = (org.bukkit.event.player.PlayerAdvancementDoneEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerAdvancementDone(
                        patchbukkit.events.PlayerAdvancementDoneEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(advancementEvent.getPlayer().getUniqueId()))
                            .setAdvancementKey(advancementEvent.getAdvancement().getKey().toString())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerAnimationEvent":
                var animationEvent = (org.bukkit.event.player.PlayerAnimationEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerAnimation(
                        patchbukkit.events.PlayerAnimationEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(animationEvent.getPlayer().getUniqueId()))
                            .setAnimationType(animationEvent.getAnimationType().name())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerArmorStandManipulateEvent":
                var armorEvent = (org.bukkit.event.player.PlayerArmorStandManipulateEvent) event;
                String playerKey = armorEvent.getPlayerItem() != null
                    ? armorEvent.getPlayerItem().getType().getKey().toString()
                    : "minecraft:air";
                String standKey = armorEvent.getArmorStandItem() != null
                    ? armorEvent.getArmorStandItem().getType().getKey().toString()
                    : "minecraft:air";
                String slot = armorEvent.getSlot() != null ? armorEvent.getSlot().name() : "HAND";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerArmorStandManipulate(
                        patchbukkit.events.PlayerArmorStandManipulateEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(armorEvent.getPlayer().getUniqueId()))
                            .setArmorStandUuid(BridgeUtils.convertUuid(armorEvent.getRightClicked().getUniqueId()))
                            .setItemKey(playerKey)
                            .setArmorStandItemKey(standKey)
                            .setSlot(slot)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerBedEnterEvent":
                var bedEnterEvent = (org.bukkit.event.player.PlayerBedEnterEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerBedEnter(
                        patchbukkit.events.PlayerBedEnterEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(bedEnterEvent.getPlayer().getUniqueId()))
                            .setBedLocation(BridgeUtils.convertLocation(bedEnterEvent.getBed().getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerBedLeaveEvent":
                var bedLeaveEvent = (org.bukkit.event.player.PlayerBedLeaveEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerBedLeave(
                        patchbukkit.events.PlayerBedLeaveEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(bedLeaveEvent.getPlayer().getUniqueId()))
                            .setBedLocation(BridgeUtils.convertLocation(bedLeaveEvent.getBed().getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerBucketEmptyEvent":
                var bucketEmptyEvent = (org.bukkit.event.player.PlayerBucketEmptyEvent) event;
                String emptyFace = bucketEmptyEvent.getBlockFace() != null
                    ? bucketEmptyEvent.getBlockFace().name()
                    : "";
                String emptyBucket = bucketEmptyEvent.getItemStack() != null
                    ? bucketEmptyEvent.getItemStack().getType().getKey().toString()
                    : "minecraft:air";
                String emptyHand = bucketEmptyEvent.getHand() != null
                    ? bucketEmptyEvent.getHand().name()
                    : "HAND";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerBucketEmpty(
                        patchbukkit.events.PlayerBucketEmptyEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(bucketEmptyEvent.getPlayer().getUniqueId()))
                            .setLocation(BridgeUtils.convertLocation(bucketEmptyEvent.getBlock().getLocation()))
                            .setBlockKey(bucketEmptyEvent.getBlock().getType().getKey().toString())
                            .setBlockFace(emptyFace)
                            .setBucketItemKey(emptyBucket)
                            .setHand(emptyHand)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerBucketFillEvent":
                var bucketFillEvent = (org.bukkit.event.player.PlayerBucketFillEvent) event;
                String fillFace = bucketFillEvent.getBlockFace() != null
                    ? bucketFillEvent.getBlockFace().name()
                    : "";
                String fillBucket = bucketFillEvent.getItemStack() != null
                    ? bucketFillEvent.getItemStack().getType().getKey().toString()
                    : "minecraft:air";
                String fillHand = bucketFillEvent.getHand() != null
                    ? bucketFillEvent.getHand().name()
                    : "HAND";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerBucketFill(
                        patchbukkit.events.PlayerBucketFillEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(bucketFillEvent.getPlayer().getUniqueId()))
                            .setLocation(BridgeUtils.convertLocation(bucketFillEvent.getBlock().getLocation()))
                            .setBlockKey(bucketFillEvent.getBlock().getType().getKey().toString())
                            .setBlockFace(fillFace)
                            .setBucketItemKey(fillBucket)
                            .setHand(fillHand)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerBucketEntityEvent":
                var bucketEntityEvent = (org.bukkit.event.player.PlayerBucketEntityEvent) event;
                String originalBucket = bucketEntityEvent.getOriginalBucket() != null
                    ? bucketEntityEvent.getOriginalBucket().getType().getKey().toString()
                    : "minecraft:air";
                String entityBucket = bucketEntityEvent.getEntityBucket() != null
                    ? bucketEntityEvent.getEntityBucket().getType().getKey().toString()
                    : "minecraft:air";
                String entityHand = bucketEntityEvent.getHand() != null
                    ? bucketEntityEvent.getHand().name()
                    : "HAND";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerBucketEntity(
                        patchbukkit.events.PlayerBucketEntityEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(bucketEntityEvent.getPlayer().getUniqueId()))
                            .setEntityUuid(BridgeUtils.convertUuid(bucketEntityEvent.getEntity().getUniqueId()))
                            .setEntityType(bucketEntityEvent.getEntity().getType().name())
                            .setOriginalBucketKey(originalBucket)
                            .setEntityBucketKey(entityBucket)
                            .setHand(entityHand)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerChangedMainHandEvent":
                var mainHandEvent = (org.bukkit.event.player.PlayerChangedMainHandEvent) event;
                String mainHand = mainHandEvent.getMainHand() != null
                    ? mainHandEvent.getMainHand().name()
                    : "RIGHT";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerChangedMainHand(
                        patchbukkit.events.PlayerChangedMainHandEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(mainHandEvent.getPlayer().getUniqueId()))
                            .setMainHand(mainHand)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerRegisterChannelEvent":
                var registerChannelEvent = (org.bukkit.event.player.PlayerRegisterChannelEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerRegisterChannel(
                        patchbukkit.events.PlayerRegisterChannelEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(registerChannelEvent.getPlayer().getUniqueId()))
                            .setChannel(registerChannelEvent.getChannel())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerUnregisterChannelEvent":
                var unregisterChannelEvent = (org.bukkit.event.player.PlayerUnregisterChannelEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerUnregisterChannel(
                        patchbukkit.events.PlayerUnregisterChannelEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(unregisterChannelEvent.getPlayer().getUniqueId()))
                            .setChannel(unregisterChannelEvent.getChannel())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.AsyncPlayerChatEvent":
                var chatEvent = (org.bukkit.event.player.AsyncPlayerChatEvent) event;
                var chatBuilder = PlayerChatEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(chatEvent.getPlayer().getUniqueId()))
                    .setMessage(chatEvent.getMessage());
                for (var recipient : chatEvent.getRecipients()) {
                    chatBuilder.addRecipients(BridgeUtils.convertUuid(recipient.getUniqueId()));
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerChat(chatBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.player.PlayerCommandPreprocessEvent":
                var commandEvent = (org.bukkit.event.player.PlayerCommandPreprocessEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerCommand(
                        PlayerCommandEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(commandEvent.getPlayer().getUniqueId()))
                            .setCommand(commandEvent.getMessage())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerCommandSendEvent":
                var commandSendEvent = (org.bukkit.event.player.PlayerCommandSendEvent) event;
                var commandSendBuilder = patchbukkit.events.PlayerCommandSendEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(commandSendEvent.getPlayer().getUniqueId()));
                commandSendBuilder.addAllCommands(commandSendEvent.getCommands());
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerCommandSend(
                        commandSendBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerDropItemEvent":
                var dropEvent = (org.bukkit.event.player.PlayerDropItemEvent) event;
                var item = dropEvent.getItemDrop();
                String dropKey = "minecraft:air";
                int dropAmount = 0;
                java.util.UUID itemUuid = java.util.UUID.randomUUID();
                if (item != null && item.getItemStack() != null) {
                    dropKey = item.getItemStack().getType().getKey().toString();
                    dropAmount = item.getItemStack().getAmount();
                    itemUuid = item.getUniqueId();
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerDropItem(
                        patchbukkit.events.PlayerDropItemEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(dropEvent.getPlayer().getUniqueId()))
                            .setItemUuid(BridgeUtils.convertUuid(itemUuid))
                            .setItemKey(dropKey)
                            .setItemAmount(dropAmount)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerEditBookEvent":
                var editEvent = (org.bukkit.event.player.PlayerEditBookEvent) event;
                var editBuilder = patchbukkit.events.PlayerEditBookEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(editEvent.getPlayer().getUniqueId()))
                    .setSlot(editEvent.getSlot())
                    .setIsSigning(editEvent.isSigning());
                var newMeta = editEvent.getNewBookMeta();
                if (newMeta != null) {
                    editBuilder.addAllPages(newMeta.getPages());
                    if (newMeta.hasTitle()) {
                        editBuilder.setTitle(newMeta.getTitle());
                    }
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerEditBook(
                        editBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerEggThrowEvent":
                var eggEvent = (org.bukkit.event.player.PlayerEggThrowEvent) event;
                var egg = eggEvent.getEgg();
                var eggUuid = egg != null ? egg.getUniqueId() : java.util.UUID.randomUUID();
                var hatchingType = eggEvent.getHatchingType() != null
                    ? eggEvent.getHatchingType().name()
                    : "CHICKEN";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerEggThrow(
                        patchbukkit.events.PlayerEggThrowEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(eggEvent.getPlayer().getUniqueId()))
                            .setEggUuid(BridgeUtils.convertUuid(eggUuid))
                            .setHatching(eggEvent.isHatching())
                            .setNumHatches(eggEvent.getNumHatches())
                            .setHatchingType(hatchingType)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerExpChangeEvent":
                var expEvent = (org.bukkit.event.player.PlayerExpChangeEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerExpChange(
                        patchbukkit.events.PlayerExpChangeEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(expEvent.getPlayer().getUniqueId()))
                            .setAmount(expEvent.getAmount())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerFishEvent":
                var fishEvent = (org.bukkit.event.player.PlayerFishEvent) event;
                var fishBuilder = patchbukkit.events.PlayerFishEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(fishEvent.getPlayer().getUniqueId()))
                    .setState(fishEvent.getState().name())
                    .setExpToDrop(fishEvent.getExpToDrop());
                if (fishEvent.getHand() != null) {
                    fishBuilder.setHand(fishEvent.getHand().name());
                }
                if (fishEvent.getHook() != null) {
                    fishBuilder.setHookUuid(BridgeUtils.convertUuid(fishEvent.getHook().getUniqueId()));
                }
                if (fishEvent.getCaught() != null) {
                    fishBuilder.setCaughtUuid(BridgeUtils.convertUuid(fishEvent.getCaught().getUniqueId()));
                    fishBuilder.setCaughtType(fishEvent.getCaught().getType().name());
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerFish(
                        fishBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerInteractAtEntityEvent":
                var interactAtEvent = (org.bukkit.event.player.PlayerInteractAtEntityEvent) event;
                var clickedEntity = interactAtEvent.getRightClicked();
                var pos = interactAtEvent.getClickedPosition();
                var interactAtBuilder = patchbukkit.events.PlayerInteractAtEntityEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(interactAtEvent.getPlayer().getUniqueId()))
                    .setEntityUuid(BridgeUtils.convertUuid(clickedEntity.getUniqueId()))
                    .setEntityType(clickedEntity.getType().name());
                if (interactAtEvent.getHand() != null) {
                    interactAtBuilder.setHand(interactAtEvent.getHand().name());
                }
                if (pos != null) {
                    interactAtBuilder.setClickedPosition(patchbukkit.common.Vec3.newBuilder()
                        .setX(pos.getX())
                        .setY(pos.getY())
                        .setZ(pos.getZ())
                        .build());
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerInteractAtEntity(
                        interactAtBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerInteractEntityEvent":
                var interactEntityEvent = (org.bukkit.event.player.PlayerInteractEntityEvent) event;
                var rightClicked = interactEntityEvent.getRightClicked();
                var interactEntityBuilder = patchbukkit.events.PlayerInteractEntityEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(interactEntityEvent.getPlayer().getUniqueId()))
                    .setEntityUuid(BridgeUtils.convertUuid(rightClicked.getUniqueId()))
                    .setEntityType(rightClicked.getType().name());
                if (interactEntityEvent.getHand() != null) {
                    interactEntityBuilder.setHand(interactEntityEvent.getHand().name());
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerInteractEntity(
                        interactEntityBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerItemHeldEvent":
                var heldEvent = (org.bukkit.event.player.PlayerItemHeldEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerItemHeld(
                        patchbukkit.events.PlayerItemHeldEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(heldEvent.getPlayer().getUniqueId()))
                            .setPreviousSlot(heldEvent.getPreviousSlot())
                            .setNewSlot(heldEvent.getNewSlot())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerItemDamageEvent":
                var playerItemDamageEvent = (org.bukkit.event.player.PlayerItemDamageEvent) event;
                var damageItem = playerItemDamageEvent.getItem();
                String damageKey = damageItem != null
                    ? damageItem.getType().getKey().toString()
                    : "minecraft:air";
                int damageAmount = damageItem != null ? damageItem.getAmount() : 0;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerItemDamage(
                        patchbukkit.events.PlayerItemDamageEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(playerItemDamageEvent.getPlayer().getUniqueId()))
                            .setItemKey(damageKey)
                            .setItemAmount(damageAmount)
                            .setDamage(playerItemDamageEvent.getDamage())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerItemBreakEvent":
                var playerItemBreakEvent = (org.bukkit.event.player.PlayerItemBreakEvent) event;
                var brokenItem = playerItemBreakEvent.getBrokenItem();
                String breakKey = brokenItem != null
                    ? brokenItem.getType().getKey().toString()
                    : "minecraft:air";
                int breakAmount = brokenItem != null ? brokenItem.getAmount() : 0;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerItemBreak(
                        patchbukkit.events.PlayerItemBreakEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(playerItemBreakEvent.getPlayer().getUniqueId()))
                            .setItemKey(breakKey)
                            .setItemAmount(breakAmount)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerItemConsumeEvent":
                var consumeEvent = (org.bukkit.event.player.PlayerItemConsumeEvent) event;
                var consumeItem = consumeEvent.getItem();
                String consumeKey = consumeItem != null
                    ? consumeItem.getType().getKey().toString()
                    : "minecraft:air";
                int consumeAmount = consumeItem != null ? consumeItem.getAmount() : 0;
                String consumeHand = consumeEvent.getHand() != null
                    ? consumeEvent.getHand().name()
                    : "HAND";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerItemConsume(
                        patchbukkit.events.PlayerItemConsumeEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(consumeEvent.getPlayer().getUniqueId()))
                            .setItemKey(consumeKey)
                            .setItemAmount(consumeAmount)
                            .setHand(consumeHand)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerItemMendEvent":
                var mendEvent = (org.bukkit.event.player.PlayerItemMendEvent) event;
                var mendItem = mendEvent.getItem();
                String mendKey = mendItem != null
                    ? mendItem.getType().getKey().toString()
                    : "minecraft:air";
                int mendAmount = mendItem != null ? mendItem.getAmount() : 0;
                String mendSlot = mendEvent.getSlot() != null
                    ? mendEvent.getSlot().name()
                    : "HAND";
                var mendBuilder = patchbukkit.events.PlayerItemMendEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(mendEvent.getPlayer().getUniqueId()))
                    .setItemKey(mendKey)
                    .setItemAmount(mendAmount)
                    .setSlot(mendSlot)
                    .setRepairAmount(mendEvent.getRepairAmount());
                if (mendEvent.getExperienceOrb() != null) {
                    mendBuilder.setOrbUuid(
                        BridgeUtils.convertUuid(mendEvent.getExperienceOrb().getUniqueId())
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerItemMend(
                        mendBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerLevelChangeEvent":
                var levelEvent = (org.bukkit.event.player.PlayerLevelChangeEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerLevelChange(
                        patchbukkit.events.PlayerLevelChangeEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(levelEvent.getPlayer().getUniqueId()))
                            .setOldLevel(levelEvent.getOldLevel())
                            .setNewLevel(levelEvent.getNewLevel())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerKickEvent":
                var kickEvent = (org.bukkit.event.player.PlayerKickEvent) event;
                String kickReason = GsonComponentSerializer.gson().serialize(kickEvent.reason());
                String leaveMessage = GsonComponentSerializer.gson().serialize(kickEvent.leaveMessage());
                String kickCause = kickEvent.getCause() != null ? kickEvent.getCause().name() : "UNKNOWN";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerKick(
                        patchbukkit.events.PlayerKickEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(kickEvent.getPlayer().getUniqueId()))
                            .setReason(kickReason)
                            .setLeaveMessage(leaveMessage)
                            .setCause(kickCause)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerToggleSneakEvent":
                var toggleSneakEvent = (org.bukkit.event.player.PlayerToggleSneakEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerToggleSneak(
                        patchbukkit.events.PlayerToggleSneakEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(toggleSneakEvent.getPlayer().getUniqueId()))
                            .setIsSneaking(toggleSneakEvent.isSneaking())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerToggleSprintEvent":
                var toggleSprintEvent = (org.bukkit.event.player.PlayerToggleSprintEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerToggleSprint(
                        patchbukkit.events.PlayerToggleSprintEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(toggleSprintEvent.getPlayer().getUniqueId()))
                            .setIsSprinting(toggleSprintEvent.isSprinting())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerToggleFlightEvent":
                var toggleFlightEvent = (org.bukkit.event.player.PlayerToggleFlightEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerToggleFlight(
                        patchbukkit.events.PlayerToggleFlightEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(toggleFlightEvent.getPlayer().getUniqueId()))
                            .setIsFlying(toggleFlightEvent.isFlying())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerSwapHandItemsEvent":
                var swapEvent = (org.bukkit.event.player.PlayerSwapHandItemsEvent) event;
                var swapMainHandItem = swapEvent.getMainHandItem();
                var offHand = swapEvent.getOffHandItem();
                String mainKey = swapMainHandItem != null ? swapMainHandItem.getType().getKey().toString() : "minecraft:air";
                int mainAmount = swapMainHandItem != null ? swapMainHandItem.getAmount() : 0;
                String offKey = offHand != null ? offHand.getType().getKey().toString() : "minecraft:air";
                int offAmount = offHand != null ? offHand.getAmount() : 0;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerSwapHandItems(
                        patchbukkit.events.PlayerSwapHandItemsEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(swapEvent.getPlayer().getUniqueId()))
                            .setMainHandItemKey(mainKey)
                            .setMainHandItemAmount(mainAmount)
                            .setOffHandItemKey(offKey)
                            .setOffHandItemAmount(offAmount)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerResourcePackStatusEvent":
                var packEvent = (org.bukkit.event.player.PlayerResourcePackStatusEvent) event;
                var packUuid = PatchBukkitEventFactory.resolveResourcePackId(packEvent);
                String status = packEvent.getStatus() != null ? packEvent.getStatus().name() : "FAILED_DOWNLOAD";
                String hash = packEvent.getHash() != null ? packEvent.getHash() : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerResourcePackStatus(
                        patchbukkit.events.PlayerResourcePackStatusEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(packEvent.getPlayer().getUniqueId()))
                            .setPackUuid(BridgeUtils.convertUuid(packUuid))
                            .setStatus(status)
                            .setHash(hash)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerRespawnEvent":
                var respawnEvent = (org.bukkit.event.player.PlayerRespawnEvent) event;
                String respawnReason = respawnEvent.getRespawnReason() != null
                    ? respawnEvent.getRespawnReason().name()
                    : "DEATH";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerRespawn(
                        patchbukkit.events.PlayerRespawnEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(respawnEvent.getPlayer().getUniqueId()))
                            .setRespawnLocation(BridgeUtils.convertLocation(respawnEvent.getRespawnLocation()))
                            .setIsBedSpawn(respawnEvent.isBedSpawn())
                            .setIsAnchorSpawn(respawnEvent.isAnchorSpawn())
                            .setIsMissingRespawnBlock(respawnEvent.isMissingRespawnBlock())
                            .setRespawnReason(respawnReason)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerPickupArrowEvent":
                var pickupEvent = (org.bukkit.event.player.PlayerPickupArrowEvent) event;
                var pickupItem = pickupEvent.getItem();
                String pickupItemKey = "minecraft:air";
                int itemAmount = 0;
                if (pickupItem != null && pickupItem.getItemStack() != null) {
                    pickupItemKey = pickupItem.getItemStack().getType().getKey().toString();
                    itemAmount = pickupItem.getItemStack().getAmount();
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerPickupArrow(
                        patchbukkit.events.PlayerPickupArrowEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(pickupEvent.getPlayer().getUniqueId()))
                            .setArrowUuid(BridgeUtils.convertUuid(pickupEvent.getArrow().getUniqueId()))
                            .setItemUuid(BridgeUtils.convertUuid(pickupItem != null ? pickupItem.getUniqueId() : java.util.UUID.randomUUID()))
                            .setItemKey(pickupItemKey)
                            .setItemAmount(itemAmount)
                            .setRemaining(pickupEvent.getRemaining())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerPortalEvent":
                var portalEvent = (org.bukkit.event.player.PlayerPortalEvent) event;
                String portalCause = portalEvent.getCause() != null ? portalEvent.getCause().name() : "";
                int searchRadius = 0;
                boolean canCreatePortal = false;
                int creationRadius = 0;
                try {
                    var method = org.bukkit.event.player.PlayerPortalEvent.class.getMethod("getSearchRadius");
                    Object value = method.invoke(portalEvent);
                    if (value instanceof Integer i) {
                        searchRadius = i;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                try {
                    var method = org.bukkit.event.player.PlayerPortalEvent.class.getMethod("getCanCreatePortal");
                    Object value = method.invoke(portalEvent);
                    if (value instanceof Boolean b) {
                        canCreatePortal = b;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                try {
                    var method = org.bukkit.event.player.PlayerPortalEvent.class.getMethod("getCreationRadius");
                    Object value = method.invoke(portalEvent);
                    if (value instanceof Integer i) {
                        creationRadius = i;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerPortal(
                        patchbukkit.events.PlayerPortalEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(portalEvent.getPlayer().getUniqueId()))
                            .setFrom(BridgeUtils.convertLocation(portalEvent.getFrom()))
                            .setTo(BridgeUtils.convertLocation(portalEvent.getTo()))
                            .setCause(portalCause)
                            .setSearchRadius(searchRadius)
                            .setCanCreatePortal(canCreatePortal)
                            .setCreationRadius(creationRadius)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerRecipeDiscoverEvent":
                var recipeEvent = (org.bukkit.event.player.PlayerRecipeDiscoverEvent) event;
                String recipeKey = recipeEvent.getRecipe() != null
                    ? recipeEvent.getRecipe().toString()
                    : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerRecipeDiscover(
                        patchbukkit.events.PlayerRecipeDiscoverEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(recipeEvent.getPlayer().getUniqueId()))
                            .setRecipeKey(recipeKey)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerRiptideEvent":
                var riptideEvent = (org.bukkit.event.player.PlayerRiptideEvent) event;
                var riptideItem = riptideEvent.getItem();
                String riptideKey = riptideItem != null ? riptideItem.getType().getKey().toString() : "minecraft:air";
                int riptideAmount = riptideItem != null ? riptideItem.getAmount() : 0;
                Vector riptideVelocity = riptideEvent.getVelocity();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerRiptide(
                        patchbukkit.events.PlayerRiptideEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(riptideEvent.getPlayer().getUniqueId()))
                            .setItemKey(riptideKey)
                            .setItemAmount(riptideAmount)
                            .setVelocity(patchbukkit.common.Vec3.newBuilder()
                                .setX(riptideVelocity.getX())
                                .setY(riptideVelocity.getY())
                                .setZ(riptideVelocity.getZ())
                                .build())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerShearEntityEvent":
                var shearEvent = (org.bukkit.event.player.PlayerShearEntityEvent) event;
                ItemStack shearItem = shearEvent.getItem();
                String shearKey = shearItem != null ? shearItem.getType().getKey().toString() : "minecraft:air";
                int shearAmount = shearItem != null ? shearItem.getAmount() : 0;
                String shearHand = shearEvent.getHand() != null ? shearEvent.getHand().name() : "HAND";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerShearEntity(
                        patchbukkit.events.PlayerShearEntityEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(shearEvent.getPlayer().getUniqueId()))
                            .setEntityUuid(BridgeUtils.convertUuid(shearEvent.getEntity().getUniqueId()))
                            .setEntityType(shearEvent.getEntity().getType().name())
                            .setItemKey(shearKey)
                            .setItemAmount(shearAmount)
                            .setHand(shearHand)
                            .build()
                    ).build()
                );
                break;
            case "org.spigotmc.event.player.PlayerSpawnLocationEvent":
                var spawnEvent = (org.spigotmc.event.player.PlayerSpawnLocationEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerSpawnLocation(
                        patchbukkit.events.PlayerSpawnLocationEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(spawnEvent.getPlayer().getUniqueId()))
                            .setSpawnLocation(BridgeUtils.convertLocation(spawnEvent.getSpawnLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerStatisticIncrementEvent":
                var statEvent = (org.bukkit.event.player.PlayerStatisticIncrementEvent) event;
                String statEntityType = statEvent.getEntityType() != null ? statEvent.getEntityType().name() : "";
                String statMaterial = statEvent.getMaterial() != null ? statEvent.getMaterial().getKey().toString() : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerStatisticIncrement(
                        patchbukkit.events.PlayerStatisticIncrementEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(statEvent.getPlayer().getUniqueId()))
                            .setStatistic(statEvent.getStatistic().name())
                            .setInitialValue(statEvent.getPreviousValue())
                            .setNewValue(statEvent.getNewValue())
                            .setEntityType(statEntityType)
                            .setMaterialKey(statMaterial)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerVelocityEvent":
                var velocityEvent = (org.bukkit.event.player.PlayerVelocityEvent) event;
                Vector velocity = velocityEvent.getVelocity();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerVelocity(
                        patchbukkit.events.PlayerVelocityEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(velocityEvent.getPlayer().getUniqueId()))
                            .setVelocity(patchbukkit.common.Vec3.newBuilder()
                                .setX(velocity.getX())
                                .setY(velocity.getY())
                                .setZ(velocity.getZ())
                                .build())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerHarvestBlockEvent":
                var harvestEvent = (org.bukkit.event.player.PlayerHarvestBlockEvent) event;
                Block harvestedBlock = null;
                ItemStack harvestTool = null;
                try {
                    var method = harvestEvent.getClass().getMethod("getHarvestedBlock");
                    Object value = method.invoke(harvestEvent);
                    if (value instanceof Block block) {
                        harvestedBlock = block;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                if (harvestedBlock == null) {
                    try {
                        var method = harvestEvent.getClass().getMethod("getBlock");
                        Object value = method.invoke(harvestEvent);
                        if (value instanceof Block block) {
                            harvestedBlock = block;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // ignore
                    }
                }
                try {
                    var method = harvestEvent.getClass().getMethod("getHarvestingTool");
                    Object value = method.invoke(harvestEvent);
                    if (value instanceof ItemStack stack) {
                        harvestTool = stack;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                if (harvestTool == null) {
                    try {
                        var method = harvestEvent.getClass().getMethod("getTool");
                        Object value = method.invoke(harvestEvent);
                        if (value instanceof ItemStack stack) {
                            harvestTool = stack;
                        }
                    } catch (ReflectiveOperationException ignored) {
                        // ignore
                    }
                }
                String harvestBlockKey = harvestedBlock != null
                    ? harvestedBlock.getType().getKey().toString()
                    : "minecraft:air";
                String harvestItemKey = harvestTool != null
                    ? harvestTool.getType().getKey().toString()
                    : "minecraft:air";
                int harvestItemAmount = harvestTool != null ? harvestTool.getAmount() : 0;
                Location harvestLocation = harvestedBlock != null ? harvestedBlock.getLocation() : null;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerHarvestBlock(
                        patchbukkit.events.PlayerHarvestBlockEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(harvestEvent.getPlayer().getUniqueId()))
                            .setBlockLocation(BridgeUtils.convertLocation(harvestLocation))
                            .setBlockKey(harvestBlockKey)
                            .setItemKey(harvestItemKey)
                            .setItemAmount(harvestItemAmount)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.player.PlayerInteractEvent":
                var interactEvent = (org.bukkit.event.player.PlayerInteractEvent) event;
                var clicked = interactEvent.getClickedBlock();
                String interactItemKey = interactEvent.getItem() != null
                    ? interactEvent.getItem().getType().getKey().toString()
                    : "minecraft:air";
                String interactFace = interactEvent.getBlockFace() != null
                    ? interactEvent.getBlockFace().name()
                    : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPlayerInteract(
                        PlayerInteractEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(interactEvent.getPlayer().getUniqueId()))
                            .setAction(interactEvent.getAction().name())
                            .setBlockKey(clicked != null ? clicked.getType().getKey().toString() : "minecraft:air")
                            .setClicked(BridgeUtils.convertLocation(clicked != null ? clicked.getLocation() : null))
                            .setItemKey(interactItemKey)
                            .setBlockFace(interactFace)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockBreakEvent":
                var blockBreakEvent = (org.bukkit.event.block.BlockBreakEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockBreak(
                        BlockBreakEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(blockBreakEvent.getPlayer().getUniqueId()))
                            .setBlockKey(blockBreakEvent.getBlock().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(blockBreakEvent.getBlock().getLocation()))
                            .setExp(blockBreakEvent.getExpToDrop())
                            .setDrop(blockBreakEvent.isDropItems())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockDamageEvent":
                var blockDamageEvent = (org.bukkit.event.block.BlockDamageEvent) event;
                String damageItemKey = blockDamageEvent.getItemInHand() != null
                    ? blockDamageEvent.getItemInHand().getType().getKey().toString()
                    : "minecraft:air";
                boolean instaBreak = resolveInstaBreak(blockDamageEvent);
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockDamage(
                        BlockDamageEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(blockDamageEvent.getPlayer().getUniqueId()))
                            .setBlockKey(blockDamageEvent.getBlock().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(blockDamageEvent.getBlock().getLocation()))
                            .setItemKey(damageItemKey)
                            .setInstaBreak(instaBreak)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockDamageAbortEvent":
                var damageAbortEvent = (org.bukkit.event.block.BlockDamageAbortEvent) event;
                String abortItemKey = damageAbortEvent.getItemInHand() != null
                    ? damageAbortEvent.getItemInHand().getType().getKey().toString()
                    : "minecraft:air";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockDamageAbort(
                        BlockDamageAbortEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(damageAbortEvent.getPlayer().getUniqueId()))
                            .setBlockKey(damageAbortEvent.getBlock().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(damageAbortEvent.getBlock().getLocation()))
                            .setItemKey(abortItemKey)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockDispenseEvent":
                var dispenseEvent = (org.bukkit.event.block.BlockDispenseEvent) event;
                String dispenseItemKey = dispenseEvent.getItem() != null
                    ? dispenseEvent.getItem().getType().getKey().toString()
                    : "minecraft:air";
                int dispenseAmount = dispenseEvent.getItem() != null
                    ? dispenseEvent.getItem().getAmount()
                    : 0;
                var dispenseVelocity = dispenseEvent.getVelocity();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockDispense(
                        BlockDispenseEvent.newBuilder()
                            .setBlockKey(dispenseEvent.getBlock().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(dispenseEvent.getBlock().getLocation()))
                            .setItemKey(dispenseItemKey)
                            .setItemAmount(dispenseAmount)
                            .setVelocity(patchbukkit.common.Vec3.newBuilder()
                                .setX(dispenseVelocity.getX())
                                .setY(dispenseVelocity.getY())
                                .setZ(dispenseVelocity.getZ())
                            .build())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockDropItemEvent":
                var dropItemEvent = (org.bukkit.event.block.BlockDropItemEvent) event;
                Block dropBlock = dropItemEvent.getBlock();
                var dropBuilder = BlockDropItemEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(dropItemEvent.getPlayer().getUniqueId()))
                    .setBlockKey(dropBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(dropBlock.getLocation()));
                for (org.bukkit.entity.Item dropItem : dropItemEvent.getItems()) {
                    if (dropItem.getItemStack() == null) continue;
                    dropBuilder.addItems(
                        BlockDropItemEntry.newBuilder()
                            .setItemKey(dropItem.getItemStack().getType().getKey().toString())
                            .setItemAmount(dropItem.getItemStack().getAmount())
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockDropItem(dropBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.BlockExplodeEvent":
                var explodeEvent = (org.bukkit.event.block.BlockExplodeEvent) event;
                Block explodeBlock = explodeEvent.getBlock();
                var explodeBuilder = BlockExplodeEvent.newBuilder()
                    .setBlockKey(explodeBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(explodeBlock.getLocation()))
                    .setYield(explodeEvent.getYield());
                for (Block b : explodeEvent.blockList()) {
                    explodeBuilder.addBlocks(
                        BlockExplodeBlockEntry.newBuilder()
                            .setBlockKey(b.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(b.getLocation()))
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockExplode(explodeBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.BlockFadeEvent":
                var fadeEvent = (org.bukkit.event.block.BlockFadeEvent) event;
                Block fadeBlock = fadeEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockFade(
                        BlockFadeEvent.newBuilder()
                            .setBlockKey(fadeBlock.getType().getKey().toString())
                            .setNewBlockKey(fadeEvent.getNewState().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(fadeBlock.getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockFertilizeEvent":
                var fertilizeEvent = (org.bukkit.event.block.BlockFertilizeEvent) event;
                Block fertilizeBlock = fertilizeEvent.getBlock();
                var fertilizeBuilder = BlockFertilizeEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(fertilizeEvent.getPlayer().getUniqueId()))
                    .setBlockKey(fertilizeBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(fertilizeBlock.getLocation()));
                for (org.bukkit.block.BlockState state : fertilizeEvent.getBlocks()) {
                    fertilizeBuilder.addBlocks(
                        BlockFertilizeBlockEntry.newBuilder()
                            .setBlockKey(state.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(state.getLocation()))
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockFertilize(fertilizeBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.BlockFormEvent":
                var formEvent = (org.bukkit.event.block.BlockFormEvent) event;
                Block formBlock = formEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockForm(
                        BlockFormEvent.newBuilder()
                            .setBlockKey(formBlock.getType().getKey().toString())
                            .setNewBlockKey(formEvent.getNewState().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(formBlock.getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockFromToEvent":
                var fromToEvent = (org.bukkit.event.block.BlockFromToEvent) event;
                Block fromBlock = fromToEvent.getBlock();
                Block toBlock = fromToEvent.getToBlock();
                String fromToFace = fromToEvent.getFace() != null ? fromToEvent.getFace().name() : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockFromTo(
                        BlockFromToEvent.newBuilder()
                            .setBlockKey(fromBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(fromBlock.getLocation()))
                            .setToBlockKey(toBlock.getType().getKey().toString())
                            .setToLocation(BridgeUtils.convertLocation(toBlock.getLocation()))
                            .setFace(fromToFace)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockGrowEvent":
                var growEvent = (org.bukkit.event.block.BlockGrowEvent) event;
                Block growBlock = growEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockGrow(
                        BlockGrowEvent.newBuilder()
                            .setBlockKey(growBlock.getType().getKey().toString())
                            .setNewBlockKey(growEvent.getNewState().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(growBlock.getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockPistonExtendEvent":
                var extendEvent = (org.bukkit.event.block.BlockPistonExtendEvent) event;
                Block extendBlock = extendEvent.getBlock();
                String extendDir = extendEvent.getDirection() != null ? extendEvent.getDirection().name() : "";
                var extendBuilder = BlockPistonExtendEvent.newBuilder()
                    .setBlockKey(extendBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(extendBlock.getLocation()))
                    .setDirection(extendDir)
                    .setLength(resolvePistonLength(extendEvent));
                for (Block b : extendEvent.getBlocks()) {
                    extendBuilder.addBlocks(
                        BlockPistonBlockEntry.newBuilder()
                            .setBlockKey(b.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(b.getLocation()))
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockPistonExtend(extendBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.BlockPistonRetractEvent":
                var retractEvent = (org.bukkit.event.block.BlockPistonRetractEvent) event;
                Block retractBlock = retractEvent.getBlock();
                String retractDir = retractEvent.getDirection() != null ? retractEvent.getDirection().name() : "";
                var retractBuilder = BlockPistonRetractEvent.newBuilder()
                    .setBlockKey(retractBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(retractBlock.getLocation()))
                    .setDirection(retractDir)
                    .setLength(resolvePistonLength(retractEvent));
                for (Block b : retractEvent.getBlocks()) {
                    retractBuilder.addBlocks(
                        BlockPistonBlockEntry.newBuilder()
                            .setBlockKey(b.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(b.getLocation()))
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockPistonRetract(retractBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.BlockRedstoneEvent":
                var redstoneEvent = (org.bukkit.event.block.BlockRedstoneEvent) event;
                Block redstoneBlock = redstoneEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockRedstone(
                        BlockRedstoneEvent.newBuilder()
                            .setBlockKey(redstoneBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(redstoneBlock.getLocation()))
                            .setOldCurrent(redstoneEvent.getOldCurrent())
                            .setNewCurrent(redstoneEvent.getNewCurrent())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockMultiPlaceEvent":
                var multiEvent = (org.bukkit.event.block.BlockMultiPlaceEvent) event;
                Block multiBlock = multiEvent.getBlock();
                var multiBuilder = BlockMultiPlaceEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(multiEvent.getPlayer().getUniqueId()))
                    .setBlockKey(multiBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(multiBlock.getLocation()));
                for (org.bukkit.block.BlockState state : resolveMultiPlaceStates(multiEvent)) {
                    multiBuilder.addBlocks(
                        BlockMultiPlaceBlockEntry.newBuilder()
                            .setBlockKey(state.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(state.getLocation()))
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockMultiPlace(multiBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.BlockPhysicsEvent":
                var physicsEvent = (org.bukkit.event.block.BlockPhysicsEvent) event;
                Block physicsBlock = physicsEvent.getBlock();
                Material physicsSourceMaterial = resolvePhysicsChangedMaterial(physicsEvent);
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockPhysics(
                        BlockPhysicsEvent.newBuilder()
                            .setBlockKey(physicsBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(physicsBlock.getLocation()))
                            .setSourceBlockKey(physicsSourceMaterial.getKey().toString())
                            .setSourceLocation(BridgeUtils.convertLocation(physicsBlock.getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.NotePlayEvent":
                var noteEvent = (org.bukkit.event.block.NotePlayEvent) event;
                Block noteBlock = noteEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setNotePlay(
                        NotePlayEvent.newBuilder()
                            .setBlockKey(noteBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(noteBlock.getLocation()))
                            .setInstrument(noteEvent.getInstrument().name())
                            .setNote(noteEvent.getNote().getId())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.SignChangeEvent":
                var signEvent = (org.bukkit.event.block.SignChangeEvent) event;
                Block signBlock = signEvent.getBlock();
                var signBuilder = SignChangeEvent.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(signEvent.getPlayer().getUniqueId()))
                    .setBlockKey(signBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(signBlock.getLocation()));
                for (String line : signEvent.getLines()) {
                    signBuilder.addLines(line != null ? line : "");
                }
                try {
                    Method method = signEvent.getClass().getMethod("isFrontText");
                    Object value = method.invoke(signEvent);
                    if (value instanceof Boolean b) {
                        signBuilder.setIsFrontText(b);
                    }
                } catch (ReflectiveOperationException ignored) {
                    signBuilder.setIsFrontText(true);
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setSignChange(signBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.TNTPrimeEvent":
                var tntEvent = (org.bukkit.event.block.TNTPrimeEvent) event;
                Block tntBlock = tntEvent.getBlock();
                var tntBuilder = TNTPrimeEvent.newBuilder()
                    .setBlockKey(tntBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(tntBlock.getLocation()))
                    .setCause(tntEvent.getCause().name());
                if (tntEvent.getPrimingEntity() instanceof org.bukkit.entity.Player player) {
                    tntBuilder.setPlayerUuid(BridgeUtils.convertUuid(player.getUniqueId()));
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setTntPrime(tntBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.MoistureChangeEvent":
                var moistureEvent = (org.bukkit.event.block.MoistureChangeEvent) event;
                Block moistureBlock = moistureEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setMoistureChange(
                        MoistureChangeEvent.newBuilder()
                            .setBlockKey(moistureBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(moistureBlock.getLocation()))
                            .setNewBlockKey(moistureEvent.getNewState().getType().getKey().toString())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.SpongeAbsorbEvent":
                var spongeEvent = (org.bukkit.event.block.SpongeAbsorbEvent) event;
                Block spongeBlock = spongeEvent.getBlock();
                var spongeBuilder = SpongeAbsorbEvent.newBuilder()
                    .setBlockKey(spongeBlock.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(spongeBlock.getLocation()));
                for (org.bukkit.block.BlockState state : spongeEvent.getBlocks()) {
                    spongeBuilder.addBlocks(
                        SpongeAbsorbBlockEntry.newBuilder()
                            .setBlockKey(state.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(state.getLocation()))
                            .build()
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setSpongeAbsorb(spongeBuilder.build()).build()
                );
                break;
            case "org.bukkit.event.block.FluidLevelChangeEvent":
                var fluidEvent = (org.bukkit.event.block.FluidLevelChangeEvent) event;
                Block fluidBlock = fluidEvent.getBlock();
                String newBlockKey = resolveFluidNewBlockKey(fluidEvent);
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setFluidLevelChange(
                        FluidLevelChangeEvent.newBuilder()
                            .setBlockKey(fluidBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(fluidBlock.getLocation()))
                            .setNewBlockKey(newBlockKey)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.world.SpawnChangeEvent":
                var worldSpawnChangeEvent = (org.bukkit.event.world.SpawnChangeEvent) event;
                Location spawnLocation = resolveSpawnChangeLocation(worldSpawnChangeEvent);
                Location previous = resolveSpawnChangePreviousLocation(worldSpawnChangeEvent, spawnLocation);
                if (previous == null) {
                    previous = spawnLocation;
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setSpawnChange(
                        SpawnChangeEvent.newBuilder()
                            .setPreviousLocation(BridgeUtils.convertLocation(previous))
                            .setLocation(BridgeUtils.convertLocation(spawnLocation))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.ServerListPingEvent":
                var pingEvent = (org.bukkit.event.server.ServerListPingEvent) event;
                int onlinePlayers = 0;
                try {
                    Method method = pingEvent.getClass().getMethod("getNumPlayers");
                    Object value = method.invoke(pingEvent);
                    if (value instanceof Integer count) {
                        onlinePlayers = count;
                    }
                } catch (ReflectiveOperationException ignored) {
                    onlinePlayers = Bukkit.getOnlinePlayers().size();
                }
                String favicon = "";
                try {
                    Method method = pingEvent.getClass().getMethod("getServerIcon");
                    Object icon = method.invoke(pingEvent);
                    if (icon != null) {
                        Method dataMethod = icon.getClass().getMethod("getData");
                        Object data = dataMethod.invoke(icon);
                        if (data instanceof String dataString) {
                            favicon = dataString;
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setServerListPing(
                        ServerListPingEvent.newBuilder()
                            .setMotd(pingEvent.getMotd())
                            .setMaxPlayers(pingEvent.getMaxPlayers())
                            .setOnlinePlayers(onlinePlayers)
                            .setFavicon(favicon)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockCanBuildEvent":
                var canBuildEvent = (org.bukkit.event.block.BlockCanBuildEvent) event;
                Block canBuildBlock = canBuildEvent.getBlock();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockCanBuild(
                        patchbukkit.events.BlockCanBuildEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(PatchBukkitEventFactory.resolveEventPlayerUuid(canBuildEvent)))
                            .setBlockKey(canBuildBlock.getType().getKey().toString())
                            .setBlockAgainstKey(canBuildBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(canBuildBlock.getLocation()))
                            .setCanBuild(canBuildEvent.isBuildable())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockBurnEvent":
                var burnEvent = (org.bukkit.event.block.BlockBurnEvent) event;
                Block burnedBlock = burnEvent.getBlock();
                Block ignitingBlock = PatchBukkitEventFactory.resolveIgnitingBlock(burnEvent);
                String ignitingKey = ignitingBlock != null
                    ? ignitingBlock.getType().getKey().toString()
                    : "minecraft:fire";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockBurn(
                        patchbukkit.events.BlockBurnEvent.newBuilder()
                            .setBlockKey(burnedBlock.getType().getKey().toString())
                            .setIgnitingBlockKey(ignitingKey)
                            .setLocation(BridgeUtils.convertLocation(burnedBlock.getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockIgniteEvent":
                var igniteEvent = (org.bukkit.event.block.BlockIgniteEvent) event;
                Block igniteBlock = igniteEvent.getBlock();
                String igniteCause = igniteEvent.getCause() != null ? igniteEvent.getCause().name() : "";
                Block igniteSource = PatchBukkitEventFactory.resolveIgnitingBlock(igniteEvent);
                String igniteKey = igniteSource != null
                    ? igniteSource.getType().getKey().toString()
                    : "minecraft:fire";
                var igniteBuilder = patchbukkit.events.BlockIgniteEvent.newBuilder()
                    .setBlockKey(igniteBlock.getType().getKey().toString())
                    .setIgnitingBlockKey(igniteKey)
                    .setLocation(BridgeUtils.convertLocation(igniteBlock.getLocation()))
                    .setCause(igniteCause);
                if (igniteEvent.getPlayer() != null) {
                    igniteBuilder.setPlayerUuid(
                        BridgeUtils.convertUuid(igniteEvent.getPlayer().getUniqueId())
                    );
                }
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockIgnite(
                        igniteBuilder.build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockSpreadEvent":
                var spreadEvent = (org.bukkit.event.block.BlockSpreadEvent) event;
                Block spreadBlock = spreadEvent.getBlock();
                Block spreadSource = spreadEvent.getSource();
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockSpread(
                        patchbukkit.events.BlockSpreadEvent.newBuilder()
                            .setSourceBlockKey(spreadSource.getType().getKey().toString())
                            .setSourceLocation(BridgeUtils.convertLocation(spreadSource.getLocation()))
                            .setBlockKey(spreadBlock.getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(spreadBlock.getLocation()))
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.block.BlockPlaceEvent":
                var placeEvent = (org.bukkit.event.block.BlockPlaceEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setBlockPlace(
                        BlockPlaceEvent.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(placeEvent.getPlayer().getUniqueId()))
                            .setBlockKey(placeEvent.getBlockPlaced().getType().getKey().toString())
                            .setBlockAgainstKey(placeEvent.getBlockAgainst().getType().getKey().toString())
                            .setLocation(BridgeUtils.convertLocation(placeEvent.getBlockPlaced().getLocation()))
                            .setCanBuild(placeEvent.canBuild())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.entity.EntitySpawnEvent":
                var entitySpawnEvent = (org.bukkit.event.entity.EntitySpawnEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setEntitySpawn(
                        EntitySpawnEvent.newBuilder()
                            .setEntityUuid(BridgeUtils.convertUuid(entitySpawnEvent.getEntity().getUniqueId()))
                            .setEntityType(entitySpawnEvent.getEntity().getType().name())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.entity.EntityDamageEvent":
                var entityDamageEvent = (org.bukkit.event.entity.EntityDamageEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setEntityDamage(
                        EntityDamageEvent.newBuilder()
                            .setEntityUuid(BridgeUtils.convertUuid(entityDamageEvent.getEntity().getUniqueId()))
                            .setDamage((float) entityDamageEvent.getFinalDamage())
                            .setDamageType(entityDamageEvent.getCause().name())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.entity.EntityDeathEvent":
                var deathEvent = (org.bukkit.event.entity.EntityDeathEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setEntityDeath(
                        EntityDeathEvent.newBuilder()
                            .setEntityUuid(BridgeUtils.convertUuid(deathEvent.getEntity().getUniqueId()))
                            .setDamageType("CUSTOM")
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.ServerCommandEvent":
                var serverCommandEvent = (org.bukkit.event.server.ServerCommandEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setServerCommand(
                        ServerCommandEvent.newBuilder()
                            .setCommand(serverCommandEvent.getCommand())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.BroadcastMessageEvent":
                var broadcastEvent = (org.bukkit.event.server.BroadcastMessageEvent) event;
                String messageJson = GsonComponentSerializer.gson().serialize(Component.text(broadcastEvent.getMessage()));
                String senderJson = GsonComponentSerializer.gson().serialize(Component.text(""));
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setServerBroadcast(
                        ServerBroadcastEvent.newBuilder()
                            .setMessage(messageJson)
                            .setSender(senderJson)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.PluginEnableEvent":
                var pluginEnableEvent = (org.bukkit.event.server.PluginEnableEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPluginEnable(
                        PluginEnableEvent.newBuilder()
                            .setPluginName(pluginEnableEvent.getPlugin().getName())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.PluginDisableEvent":
                var pluginDisableEvent = (org.bukkit.event.server.PluginDisableEvent) event;
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setPluginDisable(
                        PluginDisableEvent.newBuilder()
                            .setPluginName(pluginDisableEvent.getPlugin().getName())
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.ServiceRegisterEvent":
                var serviceRegisterEvent = (org.bukkit.event.server.ServiceRegisterEvent) event;
                var registerProvider = serviceRegisterEvent.getProvider();
                String registerPluginName = registerProvider != null && registerProvider.getPlugin() != null
                    ? registerProvider.getPlugin().getName()
                    : "";
                String registerServiceName = registerProvider != null && registerProvider.getService() != null
                    ? registerProvider.getService().getName()
                    : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setServiceRegister(
                        ServiceRegisterEvent.newBuilder()
                            .setPluginName(registerPluginName)
                            .setServiceName(registerServiceName)
                            .build()
                    ).build()
                );
                break;
            case "org.bukkit.event.server.ServiceUnregisterEvent":
                var serviceUnregisterEvent = (org.bukkit.event.server.ServiceUnregisterEvent) event;
                var unregisterProvider = serviceUnregisterEvent.getProvider();
                String unregisterPluginName = unregisterProvider != null && unregisterProvider.getPlugin() != null
                    ? unregisterProvider.getPlugin().getName()
                    : "";
                String unregisterServiceName = unregisterProvider != null && unregisterProvider.getService() != null
                    ? unregisterProvider.getService().getName()
                    : "";
                request.setEvent(
                    patchbukkit.events.Event.newBuilder().setServiceUnregister(
                        ServiceUnregisterEvent.newBuilder()
                            .setPluginName(unregisterPluginName)
                            .setServiceName(unregisterServiceName)
                            .build()
                    ).build()
                );
                break;
        }
        var response = NativeBridgeFfi.callEvent(request.build());

        boolean handledByPumpkin;
        if (response == null) handledByPumpkin = false;
        else handledByPumpkin = response.getHandled();

        if (!handledByPumpkin) {
            // Pumpkin doesn't know this event type, dispatch Java-only
            callEventJavaOnly(event);
        }
    }

    /**
    * Java-only event dispatch for events that don't have Pumpkin equivalents.
    * Used for custom plugin events or unsupported Bukkit events.
    */
    private void callEventJavaOnly(@NotNull Event event) {
        HandlerList handlers = event.getHandlers();
        RegisteredListener[] listeners = handlers.getRegisteredListeners();

        for (RegisteredListener registration : listeners) {
            if (!registration.getPlugin().isEnabled()) {
                continue;
            }

            try {
                registration.callEvent(event);
            } catch (AuthorNagException ex) {
                Plugin plugin = registration.getPlugin();

                if (plugin.isNaggable()) {
                    plugin.setNaggable(false);

                    this.server.getLogger().log(Level.SEVERE, String.format(
                        "Nag author(s): '%s' of '%s' about the following: %s",
                        plugin.getPluginMeta().getAuthors(),
                        plugin.getPluginMeta().getDisplayName(),
                        ex.getMessage()
                    ));
                }
            } catch (Throwable ex) {
                this.server.getLogger().log(
                    Level.SEVERE,
                    "Could not pass event " + event.getEventName()
                        + " to " + registration.getPlugin().getPluginMeta().getDisplayName(),
                    ex
                );
            }
        }
    }

    /**
     * Called from Rust (via j4rs) when a Pumpkin event fires for a specific plugin.
     *
     * Iterates PatchBukkitEvent's HandlerList, filters to the target plugin
     * by name, and invokes its executors. Cancellation state is set on the
     * event and read back by Rust after this returns.
     *
     * @param event      The PatchBukkitEvent populated by Rust
     * @param pluginName The plugin whose handlers should execute
     */
    public void fireEvent(@NotNull Event event, @NotNull String pluginName) {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if (!listener.getPlugin().getName().equals(pluginName)) continue;
            if (!listener.getPlugin().isEnabled()) continue;

            try {
                listener.callEvent(event);
            } catch (Throwable ex) {
                this.server.getLogger().log(
                    Level.SEVERE,
                    "Could not pass event " + event.getEventName()
                        + " to " + listener.getPlugin().getPluginMeta().getDisplayName(),
                    ex
                );
            }
        }
    }

    public void registerEvents(@NotNull Listener listener, @NotNull Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register " + listener + " while not enabled");
        }

        for (Map.Entry<Class<? extends Event>, Set<RegisteredListener>> entry : this.createRegisteredListeners(listener, plugin).entrySet()) {
            this.getEventListeners(this.getRegistrationClass(entry.getKey())).registerAll(entry.getValue());

            for (RegisteredListener rl : entry.getValue()) {
                int priorityOrdinal = Math.min(rl.getPriority().ordinal(), 4);
                var request = RegisterEventRequest.newBuilder().setEventType(entry.getKey().getName()).setPluginName(plugin.getName()).setPriority(priorityOrdinal).setBlocking(true).build();
                NativeBridgeFfi.registerEvent(request);
            }
        }
    }

    public void registerEvent(@NotNull Class<? extends Event> event, @NotNull Listener listener, @NotNull EventPriority priority, @NotNull EventExecutor executor, @NotNull Plugin plugin) {
        this.registerEvent(event, listener, priority, executor, plugin, false);
    }

    public void registerEvent(@NotNull Class<? extends Event> event, @NotNull Listener listener, @NotNull EventPriority priority, @NotNull EventExecutor executor, @NotNull Plugin plugin, boolean ignoreCancelled) {
        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register " + event + " while not enabled");
        }

        executor = new TimedEventExecutor(executor, plugin, null, event);
        this.getEventListeners(event).register(new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled));

        int priorityOrdinal = Math.min(priority.ordinal(), 4);
        var request = RegisterEventRequest.newBuilder().setEventType(event.getName()).setPluginName(plugin.getName()).setPriority(priorityOrdinal).setBlocking(true).build();
        NativeBridgeFfi.registerEvent(request);

    }

    @NotNull
    private HandlerList getEventListeners(@NotNull Class<? extends Event> type) {
        try {
            Method method = this.getRegistrationClass(type).getDeclaredMethod("getHandlerList");
            method.setAccessible(true);
            return (HandlerList) method.invoke(null);
        } catch (Exception e) {
            throw new IllegalPluginAccessException(e.toString());
        }
    }

    @NotNull
    private Class<? extends Event> getRegistrationClass(@NotNull Class<? extends Event> clazz) {
        try {
            clazz.getDeclaredMethod("getHandlerList");
            return clazz;
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null
                && !clazz.getSuperclass().equals(Event.class)
                && Event.class.isAssignableFrom(clazz.getSuperclass())) {
                return this.getRegistrationClass(clazz.getSuperclass().asSubclass(Event.class));
            } else {
                throw new IllegalPluginAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlerList method required!");
            }
        }
    }

    @NotNull
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(@NotNull Listener listener, @NotNull final Plugin plugin) {
        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<>();

        Set<Method> methods;
        try {
            Class<?> listenerClazz = listener.getClass();
            methods = Sets.union(
                Set.of(listenerClazz.getMethods()),
                Set.of(listenerClazz.getDeclaredMethods())
            );
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().severe("Failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return ret;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            // Do not register bridge or synthetic methods to avoid event duplication
            // Fixes SPIGOT-893
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().severe(plugin.getPluginMeta().getDisplayName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            Set<RegisteredListener> eventSet = ret.computeIfAbsent(eventClass, k -> new HashSet<>());

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                // This loop checks for extending deprecated events
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    Warning warning = clazz.getAnnotation(Warning.class);
                    Warning.WarningState warningState = this.server.getWarningState();
                    if (!warningState.printFor(warning)) {
                        break;
                    }
                    plugin.getLogger().log(
                        Level.WARNING,
                        String.format(
                            "\"%s\" has registered a listener for %s on method \"%s\", but the event is Deprecated. \"%s\"; please notify the authors %s.",
                            plugin.getPluginMeta().getDisplayName(),
                            clazz.getName(),
                            method.toGenericString(),
                            (warning != null && warning.reason().length() != 0) ? warning.reason() : "Server performance will be affected",
                            Arrays.toString(plugin.getPluginMeta().getAuthors().toArray())),
                        warningState == Warning.WarningState.ON ? new AuthorNagException(null) : null);
                    break;
                }
            }

            EventExecutor executor = new TimedEventExecutor(EventExecutor.create(method, eventClass), plugin, method, eventClass);
            eventSet.add(new RegisteredListener(listener, executor, eh.priority(), plugin, eh.ignoreCancelled()));
        }
        return ret;
    }

    public void clearEvents() {
        HandlerList.unregisterAll();
    }

    private static boolean resolveInstaBreak(@NotNull org.bukkit.event.block.BlockDamageEvent event) {
        try {
            Method method = event.getClass().getMethod("isInstaBreak");
            Object value = method.invoke(event);
            if (value instanceof Boolean b) {
                return b;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return false;
    }

    private static int resolvePistonLength(@NotNull Object pistonEvent) {
        try {
            Method method = pistonEvent.getClass().getMethod("getLength");
            Object value = method.invoke(pistonEvent);
            if (value instanceof Integer len) {
                return len;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = pistonEvent.getClass().getMethod("getBlocks");
            Object value = method.invoke(pistonEvent);
            if (value instanceof java.util.List<?> list) {
                return list.size();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return 0;
    }

    @NotNull
    private static java.util.List<org.bukkit.block.BlockState> resolveMultiPlaceStates(
        @NotNull org.bukkit.event.block.BlockMultiPlaceEvent event
    ) {
        try {
            Method method = event.getClass().getMethod("getBlockStates");
            Object value = method.invoke(event);
            if (value instanceof java.util.List<?> list) {
                java.util.List<org.bukkit.block.BlockState> states = new java.util.ArrayList<>();
                for (Object entry : list) {
                    if (entry instanceof org.bukkit.block.BlockState state) {
                        states.add(state);
                    }
                }
                return states;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = event.getClass().getMethod("getReplacedBlockStates");
            Object value = method.invoke(event);
            if (value instanceof java.util.List<?> list) {
                java.util.List<org.bukkit.block.BlockState> states = new java.util.ArrayList<>();
                for (Object entry : list) {
                    if (entry instanceof org.bukkit.block.BlockState state) {
                        states.add(state);
                    }
                }
                return states;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return java.util.Collections.emptyList();
    }

    @NotNull
    private static Material resolvePhysicsChangedMaterial(@NotNull org.bukkit.event.block.BlockPhysicsEvent event) {
        try {
            Object changed = event.getChangedType();
            if (changed instanceof Material material) {
                return material;
            }
            if (changed != null) {
                Method method = changed.getClass().getMethod("getMaterial");
                Object value = method.invoke(changed);
                if (value instanceof Material material) {
                    return material;
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return event.getBlock().getType();
    }

    @NotNull
    private static String resolveFluidNewBlockKey(@NotNull org.bukkit.event.block.FluidLevelChangeEvent event) {
        try {
            Method method = event.getClass().getMethod("getNewState");
            Object value = method.invoke(event);
            if (value instanceof org.bukkit.block.BlockState state) {
                return state.getType().getKey().toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = event.getClass().getMethod("getNewData");
            Object value = method.invoke(event);
            if (value instanceof org.bukkit.block.data.BlockData data) {
                return data.getMaterial().getKey().toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = event.getClass().getMethod("getNewBlockData");
            Object value = method.invoke(event);
            if (value instanceof org.bukkit.block.data.BlockData data) {
                return data.getMaterial().getKey().toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return event.getBlock().getType().getKey().toString();
    }

    @Nullable
    private static Location resolveSpawnChangeLocation(@NotNull org.bukkit.event.world.SpawnChangeEvent event) {
        try {
            Method method = event.getClass().getMethod("getLocation");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = event.getClass().getMethod("getNewLocation");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = event.getClass().getMethod("getNewSpawn");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return event.getWorld() != null ? event.getWorld().getSpawnLocation() : null;
    }

    @Nullable
    private static Location resolveSpawnChangePreviousLocation(
        @NotNull org.bukkit.event.world.SpawnChangeEvent event,
        @Nullable Location fallback
    ) {
        try {
            Method method = event.getClass().getMethod("getPreviousLocation");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Method method = event.getClass().getMethod("getPreviousSpawn");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return fallback;
    }
}
