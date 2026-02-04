package org.patchbukkit.events;

import com.google.protobuf.InvalidProtocolBufferException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerBucketEntityEvent;
import org.bukkit.event.player.PlayerChangedMainHandEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.event.player.PlayerUnregisterChannelEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerEggThrowEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.command.PatchBukkitConsoleCommandSender;
import org.patchbukkit.world.PatchBukkitBlock;
import org.patchbukkit.world.PatchBukkitWorld;
import org.patchbukkit.entity.PatchBukkitEntity;
import org.patchbukkit.entity.PatchBukkitLivingEntity;
import org.patchbukkit.entity.PatchBukkitArmorStand;
import org.patchbukkit.entity.PatchBukkitItem;
import org.patchbukkit.entity.PatchBukkitEgg;
import org.patchbukkit.entity.PatchBukkitFishHook;
import org.patchbukkit.entity.PatchBukkitExperienceOrb;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import java.net.InetAddress;
import patchbukkit.common.UUID;
import patchbukkit.events.Event;
import patchbukkit.events.FireEventResponse;
import patchbukkit.events.PlayerChatEvent;
import patchbukkit.events.PlayerCommandEvent;
import patchbukkit.events.PlayerJoinEvent;
import patchbukkit.events.PlayerLeaveEvent;

import java.util.HashSet;
import java.util.logging.Logger;

public class PatchBukkitEventFactory {
    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");

    @Nullable
    public static org.bukkit.event.Event createEventFromBytes(byte[] data) {
        try {
            Event event = Event.parseFrom(data);
            return createEvent(event);
        } catch (InvalidProtocolBufferException e) {
            LOGGER.severe("Failed to parse Event: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    public static org.bukkit.event.Event createEvent(@NotNull Event event) {
        Event.DataCase dataCase = event.getDataCase();

        return switch (dataCase) {
            case PLAYER_JOIN -> {
                PlayerJoinEvent joinEvent = event.getPlayerJoin();
                Player player = getPlayer(joinEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Component joinMessage = GsonComponentSerializer.gson().deserialize(joinEvent.getJoinMessage());
                yield new org.bukkit.event.player.PlayerJoinEvent(player, joinMessage);
            }
            case PLAYER_LOGIN -> {
                patchbukkit.events.PlayerLoginEvent loginEvent = event.getPlayerLogin();
                Player player = getPlayer(loginEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Component kickMessage = GsonComponentSerializer.gson().deserialize(loginEvent.getKickMessage());
                PlayerLoginEvent login = createLoginEvent(player);
                if (login != null) {
                    login.setKickMessage(PlainTextComponentSerializer.plainText().serialize(kickMessage));
                    yield login;
                }
                yield null;
            }
            case ASYNC_PLAYER_PRE_LOGIN -> {
                patchbukkit.events.AsyncPlayerPreLoginEvent preLoginEvent = event.getAsyncPlayerPreLogin();
                String name = preLoginEvent.getName();
                String uuidStr = preLoginEvent.getPlayerUuid().getValue();
                java.util.UUID uuid;
                try {
                    uuid = java.util.UUID.fromString(uuidStr);
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                InetAddress address = null;
                if (!preLoginEvent.getAddress().isEmpty()) {
                    try {
                        address = InetAddress.getByName(preLoginEvent.getAddress());
                    } catch (Exception ignored) {
                        address = null;
                    }
                }
                AsyncPlayerPreLoginEvent.Result result = AsyncPlayerPreLoginEvent.Result.ALLOWED;
                if (!preLoginEvent.getResult().isEmpty()) {
                    try {
                        result = AsyncPlayerPreLoginEvent.Result.valueOf(preLoginEvent.getResult());
                    } catch (IllegalArgumentException ignored) {
                        result = AsyncPlayerPreLoginEvent.Result.ALLOWED;
                    }
                }
                AsyncPlayerPreLoginEvent eventObj = new AsyncPlayerPreLoginEvent(name, address, uuid);
                if (result != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                    eventObj.disallow(result, preLoginEvent.getKickMessage());
                }
                yield eventObj;
            }
            case PLAYER_LEAVE -> {
                PlayerLeaveEvent leaveEvent = event.getPlayerLeave();
                Player player = getPlayer(leaveEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Component leaveMessage = GsonComponentSerializer.gson().deserialize(leaveEvent.getLeaveMessage());
                yield new PlayerQuitEvent(player, leaveMessage);
            }
            case PLAYER_MOVE -> {
                patchbukkit.events.PlayerMoveEvent moveEvent = event.getPlayerMove();
                Player player = getPlayer(moveEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location from = BridgeUtils.convertLocation(moveEvent.getFrom());
                Location to = BridgeUtils.convertLocation(moveEvent.getTo());
                if (from == null || to == null) yield null;

                yield new PlayerMoveEvent(player, from, to);
            }
            case PLAYER_TELEPORT -> {
                patchbukkit.events.PlayerTeleportEvent teleportEvent = event.getPlayerTeleport();
                Player player = getPlayer(teleportEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location from = BridgeUtils.convertLocation(teleportEvent.getFrom());
                Location to = BridgeUtils.convertLocation(teleportEvent.getTo());
                if (from == null || to == null) yield null;
                PlayerTeleportEvent.TeleportCause cause = PlayerTeleportEvent.TeleportCause.UNKNOWN;
                if (!teleportEvent.getCause().isEmpty()) {
                    try {
                        cause = PlayerTeleportEvent.TeleportCause.valueOf(teleportEvent.getCause());
                    } catch (IllegalArgumentException ignored) {
                        cause = PlayerTeleportEvent.TeleportCause.UNKNOWN;
                    }
                }
                yield new PlayerTeleportEvent(player, from, to, cause);
            }
            case PLAYER_CHANGE_WORLD -> {
                patchbukkit.events.PlayerChangeWorldEvent changeEvent = event.getPlayerChangeWorld();
                Player player = getPlayer(changeEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                if (changeEvent.getPreviousWorld() == null || changeEvent.getPreviousWorld().getUuid() == null) {
                    yield null;
                }
                java.util.UUID prevUuid = java.util.UUID.fromString(changeEvent.getPreviousWorld().getUuid().getValue());
                yield new PlayerChangedWorldEvent(player, PatchBukkitWorld.getOrCreate(prevUuid));
            }
            case PLAYER_GAMEMODE_CHANGE -> {
                patchbukkit.events.PlayerGamemodeChangeEvent gmEvent = event.getPlayerGamemodeChange();
                Player player = getPlayer(gmEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                GameMode mode = GameMode.SURVIVAL;
                if (!gmEvent.getNewGamemode().isEmpty()) {
                    try {
                        mode = GameMode.valueOf(gmEvent.getNewGamemode());
                    } catch (IllegalArgumentException ignored) {
                        mode = GameMode.SURVIVAL;
                    }
                }
                yield new PlayerGameModeChangeEvent(player, mode);
            }
            case PLAYER_ADVANCEMENT_DONE -> {
                patchbukkit.events.PlayerAdvancementDoneEvent advancementEvent = event.getPlayerAdvancementDone();
                Player player = getPlayer(advancementEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                if (advancementEvent.getAdvancementKey().isEmpty()) yield null;
                NamespacedKey key = NamespacedKey.fromString(advancementEvent.getAdvancementKey());
                if (key == null) yield null;
                org.bukkit.advancement.Advancement advancement = Bukkit.getAdvancement(key);
                if (advancement == null) yield null;
                yield new PlayerAdvancementDoneEvent(player, advancement);
            }
            case PLAYER_ANIMATION -> {
                patchbukkit.events.PlayerAnimationEvent animationEvent = event.getPlayerAnimation();
                Player player = getPlayer(animationEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                PlayerAnimationType type = PlayerAnimationType.ARM_SWING;
                if (!animationEvent.getAnimationType().isEmpty()) {
                    try {
                        type = PlayerAnimationType.valueOf(animationEvent.getAnimationType());
                    } catch (IllegalArgumentException ignored) {
                        type = PlayerAnimationType.ARM_SWING;
                    }
                }
                yield new PlayerAnimationEvent(player, type);
            }
            case PLAYER_ARMOR_STAND_MANIPULATE -> {
                patchbukkit.events.PlayerArmorStandManipulateEvent armorEvent =
                    event.getPlayerArmorStandManipulate();
                Player player = getPlayer(armorEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID standUuid;
                try {
                    standUuid = java.util.UUID.fromString(armorEvent.getArmorStandUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                PatchBukkitArmorStand armorStand = new PatchBukkitArmorStand(standUuid, "ArmorStand");

                ItemStack playerItem = materialToItem(armorEvent.getItemKey());
                ItemStack standItem = materialToItem(armorEvent.getArmorStandItemKey());

                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!armorEvent.getSlot().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(armorEvent.getSlot());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                yield new PlayerArmorStandManipulateEvent(player, armorStand, playerItem, standItem, slot);
            }
            case PLAYER_BED_ENTER -> {
                patchbukkit.events.PlayerBedEnterEvent bedEvent = event.getPlayerBedEnter();
                Player player = getPlayer(bedEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location bedLocation = BridgeUtils.convertLocation(bedEvent.getBedLocation());
                if (bedLocation == null) yield null;
                if (bedLocation.getWorld() == null) yield null;
                yield new PlayerBedEnterEvent(player, bedLocation.getBlock());
            }
            case PLAYER_BED_LEAVE -> {
                patchbukkit.events.PlayerBedLeaveEvent bedEvent = event.getPlayerBedLeave();
                Player player = getPlayer(bedEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location bedLocation = BridgeUtils.convertLocation(bedEvent.getBedLocation());
                if (bedLocation == null) yield null;
                if (bedLocation.getWorld() == null) yield null;
                yield new PlayerBedLeaveEvent(player, bedLocation.getBlock());
            }
            case PLAYER_BUCKET_EMPTY -> {
                patchbukkit.events.PlayerBucketEmptyEvent bucketEvent = event.getPlayerBucketEmpty();
                Player player = getPlayer(bucketEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location location = BridgeUtils.convertLocation(bucketEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    bucketEvent.getBlockKey()
                );
                BlockFace face = BlockFace.SELF;
                if (!bucketEvent.getBlockFace().isEmpty()) {
                    try {
                        face = BlockFace.valueOf(bucketEvent.getBlockFace());
                    } catch (IllegalArgumentException ignored) {
                        face = BlockFace.SELF;
                    }
                }
                ItemStack item = materialToItem(bucketEvent.getBucketItemKey());
                Material bucket = item.getType();
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!bucketEvent.getHand().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(bucketEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                yield new PlayerBucketEmptyEvent(player, block, block, face, bucket, item, slot);
            }
            case PLAYER_BUCKET_FILL -> {
                patchbukkit.events.PlayerBucketFillEvent bucketEvent = event.getPlayerBucketFill();
                Player player = getPlayer(bucketEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location location = BridgeUtils.convertLocation(bucketEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    bucketEvent.getBlockKey()
                );
                BlockFace face = BlockFace.SELF;
                if (!bucketEvent.getBlockFace().isEmpty()) {
                    try {
                        face = BlockFace.valueOf(bucketEvent.getBlockFace());
                    } catch (IllegalArgumentException ignored) {
                        face = BlockFace.SELF;
                    }
                }
                ItemStack item = materialToItem(bucketEvent.getBucketItemKey());
                Material bucket = item.getType();
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!bucketEvent.getHand().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(bucketEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                yield new PlayerBucketFillEvent(player, block, block, face, bucket, item, slot);
            }
            case PLAYER_BUCKET_ENTITY -> {
                patchbukkit.events.PlayerBucketEntityEvent bucketEvent = event.getPlayerBucketEntity();
                Player player = getPlayer(bucketEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID entityUuid;
                try {
                    entityUuid = java.util.UUID.fromString(bucketEvent.getEntityUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                PatchBukkitEntity entity = new PatchBukkitEntity(entityUuid, bucketEvent.getEntityType());
                ItemStack originalBucket = materialToItem(bucketEvent.getOriginalBucketKey());
                ItemStack entityBucket = materialToItem(bucketEvent.getEntityBucketKey());
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!bucketEvent.getHand().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(bucketEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                yield new PlayerBucketEntityEvent(player, entity, originalBucket, entityBucket, slot);
            }
            case PLAYER_CHANGED_MAIN_HAND -> {
                patchbukkit.events.PlayerChangedMainHandEvent mainHandEvent = event.getPlayerChangedMainHand();
                Player player = getPlayer(mainHandEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                MainHand mainHand = MainHand.RIGHT;
                if (!mainHandEvent.getMainHand().isEmpty()) {
                    try {
                        mainHand = MainHand.valueOf(mainHandEvent.getMainHand());
                    } catch (IllegalArgumentException ignored) {
                        mainHand = MainHand.RIGHT;
                    }
                }
                yield new PlayerChangedMainHandEvent(player, mainHand);
            }
            case PLAYER_REGISTER_CHANNEL -> {
                patchbukkit.events.PlayerRegisterChannelEvent channelEvent = event.getPlayerRegisterChannel();
                Player player = getPlayer(channelEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerRegisterChannelEvent(player, channelEvent.getChannel());
            }
            case PLAYER_UNREGISTER_CHANNEL -> {
                patchbukkit.events.PlayerUnregisterChannelEvent channelEvent = event.getPlayerUnregisterChannel();
                Player player = getPlayer(channelEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerUnregisterChannelEvent(player, channelEvent.getChannel());
            }
            case PLAYER_CHAT -> {
                PlayerChatEvent chatEvent = event.getPlayerChat();
                Player player = getPlayer(chatEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                var recipients = new HashSet<Player>();
                if (chatEvent.getRecipientsList().isEmpty()) {
                    recipients.addAll(Bukkit.getOnlinePlayers());
                } else {
                    for (UUID recipient : chatEvent.getRecipientsList()) {
                        Player recipientPlayer = getPlayer(recipient.getValue());
                        if (recipientPlayer != null) {
                            recipients.add(recipientPlayer);
                        }
                    }
                }

                yield new AsyncPlayerChatEvent(false, player, chatEvent.getMessage(), recipients);
            }
            case PLAYER_COMMAND -> {
                PlayerCommandEvent commandEvent = event.getPlayerCommand();
                Player player = getPlayer(commandEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                yield new PlayerCommandPreprocessEvent(player, commandEvent.getCommand());
            }
            case PLAYER_COMMAND_SEND -> {
                patchbukkit.events.PlayerCommandSendEvent commandEvent = event.getPlayerCommandSend();
                Player player = getPlayer(commandEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerCommandSendEvent(player, new HashSet<>(commandEvent.getCommandsList()));
            }
            case PLAYER_DROP_ITEM -> {
                patchbukkit.events.PlayerDropItemEvent dropEvent = event.getPlayerDropItem();
                Player player = getPlayer(dropEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID itemUuid;
                try {
                    itemUuid = java.util.UUID.fromString(dropEvent.getItemUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                ItemStack stack = materialToItem(dropEvent.getItemKey(), dropEvent.getItemAmount());
                PatchBukkitItem item = new PatchBukkitItem(itemUuid, stack);
                yield new PlayerDropItemEvent(player, item);
            }
            case PLAYER_EDIT_BOOK -> {
                patchbukkit.events.PlayerEditBookEvent editEvent = event.getPlayerEditBook();
                Player player = getPlayer(editEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                boolean isSigning = editEvent.getIsSigning();
                ItemStack item = new ItemStack(isSigning ? Material.WRITTEN_BOOK : Material.WRITABLE_BOOK);
                BookMeta previousMeta = (BookMeta) item.getItemMeta();
                BookMeta newMeta = (BookMeta) item.getItemMeta();
                if (newMeta != null) {
                    newMeta.setPages(editEvent.getPagesList());
                    if (!editEvent.getTitle().isEmpty()) {
                        newMeta.setTitle(editEvent.getTitle());
                    }
                }
                yield new PlayerEditBookEvent(player, editEvent.getSlot(), previousMeta, newMeta, isSigning);
            }
            case PLAYER_EGG_THROW -> {
                patchbukkit.events.PlayerEggThrowEvent eggEvent = event.getPlayerEggThrow();
                Player player = getPlayer(eggEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID eggUuid;
                try {
                    eggUuid = java.util.UUID.fromString(eggEvent.getEggUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                PatchBukkitEgg egg = new PatchBukkitEgg(eggUuid, new ItemStack(Material.EGG));
                org.bukkit.entity.EntityType hatchingType = org.bukkit.entity.EntityType.CHICKEN;
                if (!eggEvent.getHatchingType().isEmpty()) {
                    try {
                        hatchingType = org.bukkit.entity.EntityType.valueOf(eggEvent.getHatchingType());
                    } catch (IllegalArgumentException ignored) {
                        hatchingType = org.bukkit.entity.EntityType.CHICKEN;
                    }
                }
                yield new PlayerEggThrowEvent(
                    player,
                    egg,
                    eggEvent.getHatching(),
                    (byte) Math.max(0, Math.min(eggEvent.getNumHatches(), 127)),
                    hatchingType
                );
            }
            case PLAYER_EXP_CHANGE -> {
                patchbukkit.events.PlayerExpChangeEvent expEvent = event.getPlayerExpChange();
                Player player = getPlayer(expEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerExpChangeEvent(player, expEvent.getAmount());
            }
            case PLAYER_FISH -> {
                patchbukkit.events.PlayerFishEvent fishEvent = event.getPlayerFish();
                Player player = getPlayer(fishEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID hookUuid;
                try {
                    hookUuid = java.util.UUID.fromString(fishEvent.getHookUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                PatchBukkitFishHook hook = new PatchBukkitFishHook(hookUuid);
                org.bukkit.entity.Entity caught = null;
                if (fishEvent.hasCaughtUuid()) {
                    try {
                        java.util.UUID caughtUuid = java.util.UUID.fromString(fishEvent.getCaughtUuid().getValue());
                        caught = new PatchBukkitEntity(caughtUuid, fishEvent.getCaughtType());
                    } catch (IllegalArgumentException ignored) {
                        caught = null;
                    }
                }
                PlayerFishEvent.State state = PlayerFishEvent.State.FISHING;
                if (!fishEvent.getState().isEmpty()) {
                    try {
                        state = PlayerFishEvent.State.valueOf(fishEvent.getState());
                    } catch (IllegalArgumentException ignored) {
                        state = PlayerFishEvent.State.FISHING;
                    }
                }
                EquipmentSlot hand = EquipmentSlot.HAND;
                if (!fishEvent.getHand().isEmpty()) {
                    try {
                        hand = EquipmentSlot.valueOf(fishEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        hand = EquipmentSlot.HAND;
                    }
                }
                PlayerFishEvent playerFishEvent = new PlayerFishEvent(player, caught, hook, hand, state);
                playerFishEvent.setExpToDrop(fishEvent.getExpToDrop());
                yield playerFishEvent;
            }
            case PLAYER_INTERACT_ENTITY -> {
                patchbukkit.events.PlayerInteractEntityEvent interactEvent = event.getPlayerInteractEntity();
                Player player = getPlayer(interactEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID entityUuid;
                try {
                    entityUuid = java.util.UUID.fromString(interactEvent.getEntityUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                PatchBukkitEntity entity = new PatchBukkitEntity(entityUuid, interactEvent.getEntityType());
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!interactEvent.getHand().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(interactEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                yield new PlayerInteractEntityEvent(player, entity, slot);
            }
            case PLAYER_INTERACT_AT_ENTITY -> {
                patchbukkit.events.PlayerInteractAtEntityEvent interactEvent = event.getPlayerInteractAtEntity();
                Player player = getPlayer(interactEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID entityUuid;
                try {
                    entityUuid = java.util.UUID.fromString(interactEvent.getEntityUuid().getValue());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                PatchBukkitEntity entity = new PatchBukkitEntity(entityUuid, interactEvent.getEntityType());
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!interactEvent.getHand().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(interactEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                var pos = interactEvent.getClickedPosition();
                Vector clickedPos = pos != null
                    ? new Vector(pos.getX(), pos.getY(), pos.getZ())
                    : new Vector();
                yield new PlayerInteractAtEntityEvent(player, entity, clickedPos, slot);
            }
            case PLAYER_ITEM_HELD -> {
                patchbukkit.events.PlayerItemHeldEvent heldEvent = event.getPlayerItemHeld();
                Player player = getPlayer(heldEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerItemHeldEvent(player, heldEvent.getPreviousSlot(), heldEvent.getNewSlot());
            }
            case PLAYER_ITEM_DAMAGE -> {
                patchbukkit.events.PlayerItemDamageEvent damageEvent = event.getPlayerItemDamage();
                Player player = getPlayer(damageEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                ItemStack item = materialToItem(damageEvent.getItemKey(), damageEvent.getItemAmount());
                yield new PlayerItemDamageEvent(player, item, damageEvent.getDamage());
            }
            case PLAYER_ITEM_BREAK -> {
                patchbukkit.events.PlayerItemBreakEvent breakEvent = event.getPlayerItemBreak();
                Player player = getPlayer(breakEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                ItemStack item = materialToItem(breakEvent.getItemKey(), breakEvent.getItemAmount());
                yield new PlayerItemBreakEvent(player, item);
            }
            case PLAYER_ITEM_CONSUME -> {
                patchbukkit.events.PlayerItemConsumeEvent consumeEvent = event.getPlayerItemConsume();
                Player player = getPlayer(consumeEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                ItemStack item = materialToItem(consumeEvent.getItemKey(), consumeEvent.getItemAmount());
                EquipmentSlot hand = EquipmentSlot.HAND;
                if (!consumeEvent.getHand().isEmpty()) {
                    try {
                        hand = EquipmentSlot.valueOf(consumeEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        hand = EquipmentSlot.HAND;
                    }
                }
                yield new PlayerItemConsumeEvent(player, item, hand);
            }
            case PLAYER_ITEM_MEND -> {
                patchbukkit.events.PlayerItemMendEvent mendEvent = event.getPlayerItemMend();
                Player player = getPlayer(mendEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                ItemStack item = materialToItem(mendEvent.getItemKey(), mendEvent.getItemAmount());
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!mendEvent.getSlot().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(mendEvent.getSlot());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                java.util.UUID orbUuid = java.util.UUID.randomUUID();
                if (mendEvent.hasOrbUuid()) {
                    try {
                        orbUuid = java.util.UUID.fromString(mendEvent.getOrbUuid().getValue());
                    } catch (IllegalArgumentException ignored) {
                        orbUuid = java.util.UUID.randomUUID();
                    }
                }
                PatchBukkitExperienceOrb orb = new PatchBukkitExperienceOrb(orbUuid);
                yield new PlayerItemMendEvent(player, item, slot, orb, mendEvent.getRepairAmount());
            }
            case PLAYER_INTERACT -> {
                patchbukkit.events.PlayerInteractEvent interactEvent = event.getPlayerInteract();
                Player player = getPlayer(interactEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location clicked = BridgeUtils.convertLocation(interactEvent.getClicked());
                org.bukkit.block.Block clickedBlock = null;
                if (clicked != null && clicked.getWorld() instanceof PatchBukkitWorld world) {
                    clickedBlock = PatchBukkitBlock.create(
                        world,
                        clicked.getBlockX(),
                        clicked.getBlockY(),
                        clicked.getBlockZ(),
                        interactEvent.getBlockKey()
                    );
                }

                org.bukkit.event.block.Action action =
                    org.bukkit.event.block.Action.valueOf(interactEvent.getAction());
                ItemStack item = null;
                if (!interactEvent.getItemKey().isEmpty()) {
                    Material material = Material.matchMaterial(interactEvent.getItemKey());
                    if (material == null) {
                        material = Material.matchMaterial("minecraft:" + interactEvent.getItemKey());
                    }
                    if (material != null) {
                        item = new ItemStack(material);
                    }
                }
                BlockFace face = null;
                if (!interactEvent.getBlockFace().isEmpty()) {
                    try {
                        face = BlockFace.valueOf(interactEvent.getBlockFace());
                    } catch (IllegalArgumentException ignored) {
                        face = null;
                    }
                }
                yield new PlayerInteractEvent(player, action, item, clickedBlock, face);
            }
            case BLOCK_BREAK -> {
                patchbukkit.events.BlockBreakEvent breakEvent = event.getBlockBreak();
                Player player = getPlayer(breakEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(breakEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    breakEvent.getBlockKey()
                );

                BlockBreakEvent eventObj = new BlockBreakEvent(block, player);
                eventObj.setExpToDrop(breakEvent.getExp());
                eventObj.setDropItems(breakEvent.getDrop());
                yield eventObj;
            }
            case BLOCK_PLACE -> {
                patchbukkit.events.BlockPlaceEvent placeEvent = event.getBlockPlace();
                Player player = getPlayer(placeEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(placeEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block placed = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    placeEvent.getBlockKey()
                );
                org.bukkit.block.Block against = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    placeEvent.getBlockAgainstKey()
                );

                BlockPlaceEvent eventObj = new BlockPlaceEvent(
                    placed,
                    placed.getState(),
                    against,
                    null,
                    player,
                    placeEvent.getCanBuild(),
                    null
                );
                yield eventObj;
            }
            case ENTITY_SPAWN -> {
                patchbukkit.events.EntitySpawnEvent spawnEvent = event.getEntitySpawn();
                java.util.UUID entityUuid = java.util.UUID.fromString(spawnEvent.getEntityUuid().getValue());
                PatchBukkitEntity entity = new PatchBukkitEntity(entityUuid, spawnEvent.getEntityType());
                yield new EntitySpawnEvent(entity);
            }
            case ENTITY_DAMAGE -> {
                patchbukkit.events.EntityDamageEvent damageEvent = event.getEntityDamage();
                java.util.UUID entityUuid = java.util.UUID.fromString(damageEvent.getEntityUuid().getValue());
                PatchBukkitEntity entity = new PatchBukkitEntity(entityUuid, "Entity");
                yield new EntityDamageEvent(entity, DamageCause.CUSTOM, damageEvent.getDamage());
            }
            case ENTITY_DEATH -> {
                patchbukkit.events.EntityDeathEvent deathEvent = event.getEntityDeath();
                java.util.UUID entityUuid = java.util.UUID.fromString(deathEvent.getEntityUuid().getValue());
                PatchBukkitLivingEntity entity = new PatchBukkitLivingEntity(entityUuid, "Entity");
                yield new EntityDeathEvent(entity, java.util.Collections.emptyList(), 0);
            }
            case SERVER_COMMAND -> {
                patchbukkit.events.ServerCommandEvent commandEvent = event.getServerCommand();
                yield new ServerCommandEvent(new PatchBukkitConsoleCommandSender(), commandEvent.getCommand());
            }
            case SERVER_BROADCAST -> {
                patchbukkit.events.ServerBroadcastEvent broadcastEvent = event.getServerBroadcast();
                Component messageComponent = GsonComponentSerializer.gson().deserialize(broadcastEvent.getMessage());
                String message = PlainTextComponentSerializer.plainText().serialize(messageComponent);
                yield new BroadcastMessageEvent(message);
            }
            case DATA_NOT_SET -> {
                LOGGER.warning("EventFactory: Received Event with no data");
                yield null;
            }
        };
    }

    @NotNull
    public static byte[] toFireEventResponse(@NotNull org.bukkit.event.Event event) {
        boolean cancelled = event instanceof org.bukkit.event.Cancellable c && c.isCancelled();

        FireEventResponse.Builder builder = FireEventResponse.newBuilder()
            .setCancelled(cancelled);

        Event.Builder eventBuilder = Event.newBuilder();

        if (event instanceof org.bukkit.event.player.PlayerJoinEvent joinEvent) {
            String joinMessage = GsonComponentSerializer.gson().serialize(joinEvent.joinMessage());

            eventBuilder.setPlayerJoin(
                PlayerJoinEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(joinEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setJoinMessage(joinMessage)
                    .build()
            );
        } else if (event instanceof PlayerQuitEvent quitEvent) {
            String quitMessage = GsonComponentSerializer.gson().serialize(quitEvent.quitMessage());

            eventBuilder.setPlayerLeave(
                PlayerLeaveEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(quitEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setLeaveMessage(quitMessage)
                    .build()
            );
        } else if (event instanceof PlayerLoginEvent loginEvent) {
            String kickMessage = loginEvent.getKickMessage();
            if (kickMessage == null) {
                kickMessage = "";
            }
            eventBuilder.setPlayerLogin(
                patchbukkit.events.PlayerLoginEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(loginEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setKickMessage(GsonComponentSerializer.gson().serialize(Component.text(kickMessage)))
                    .build()
            );
        } else if (event instanceof AsyncPlayerPreLoginEvent preLoginEvent) {
            String address = preLoginEvent.getAddress() != null
                ? preLoginEvent.getAddress().getHostAddress()
                : "";
            String result = preLoginEvent.getLoginResult() != null
                ? preLoginEvent.getLoginResult().name()
                : "ALLOWED";
            String kickMessage = preLoginEvent.getKickMessage() != null
                ? preLoginEvent.getKickMessage()
                : "";
            eventBuilder.setAsyncPlayerPreLogin(
                patchbukkit.events.AsyncPlayerPreLoginEvent.newBuilder()
                    .setName(preLoginEvent.getName())
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(preLoginEvent.getUniqueId().toString())
                        .build())
                    .setAddress(address)
                    .setResult(result)
                    .setKickMessage(kickMessage)
                    .build()
            );
        } else if (event instanceof PlayerMoveEvent moveEvent) {
            eventBuilder.setPlayerMove(
                patchbukkit.events.PlayerMoveEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(moveEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setFrom(BridgeUtils.convertLocation(moveEvent.getFrom()))
                    .setTo(BridgeUtils.convertLocation(moveEvent.getTo()))
                    .build()
            );
        } else if (event instanceof PlayerTeleportEvent teleportEvent) {
            String cause = teleportEvent.getCause() != null
                ? teleportEvent.getCause().name()
                : "";
            eventBuilder.setPlayerTeleport(
                patchbukkit.events.PlayerTeleportEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(teleportEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setFrom(BridgeUtils.convertLocation(teleportEvent.getFrom()))
                    .setTo(BridgeUtils.convertLocation(teleportEvent.getTo()))
                    .setCause(cause)
                    .build()
            );
        } else if (event instanceof PlayerChangedWorldEvent changeEvent) {
            var previousWorld = changeEvent.getFrom();
            var currentWorld = changeEvent.getPlayer().getWorld();
            var location = changeEvent.getPlayer().getLocation();
            eventBuilder.setPlayerChangeWorld(
                patchbukkit.events.PlayerChangeWorldEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(changeEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setPreviousWorld(BridgeUtils.convertWorld(previousWorld))
                    .setNewWorld(BridgeUtils.convertWorld(currentWorld))
                    .setPosition(BridgeUtils.convertLocation(location))
                    .setYaw(location.getYaw())
                    .setPitch(location.getPitch())
                    .build()
            );
        } else if (event instanceof PlayerGameModeChangeEvent gameModeChangeEvent) {
            GameMode previous = gameModeChangeEvent.getPlayer().getGameMode();
            GameMode next = gameModeChangeEvent.getNewGameMode();
            eventBuilder.setPlayerGamemodeChange(
                patchbukkit.events.PlayerGamemodeChangeEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(gameModeChangeEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setPreviousGamemode(previous != null ? previous.name() : "")
                    .setNewGamemode(next != null ? next.name() : "")
                    .build()
            );
        } else if (event instanceof PlayerAdvancementDoneEvent advancementEvent) {
            String key = advancementEvent.getAdvancement().getKey().toString();
            eventBuilder.setPlayerAdvancementDone(
                patchbukkit.events.PlayerAdvancementDoneEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(advancementEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setAdvancementKey(key)
                    .build()
            );
        } else if (event instanceof PlayerAnimationEvent animationEvent) {
            eventBuilder.setPlayerAnimation(
                patchbukkit.events.PlayerAnimationEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(animationEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setAnimationType(animationEvent.getAnimationType().name())
                    .build()
            );
        } else if (event instanceof PlayerArmorStandManipulateEvent armorEvent) {
            ItemStack playerItem = armorEvent.getPlayerItem();
            ItemStack standItem = armorEvent.getArmorStandItem();
            String playerKey = playerItem != null ? playerItem.getType().getKey().toString() : "minecraft:air";
            String standKey = standItem != null ? standItem.getType().getKey().toString() : "minecraft:air";
            String slot = armorEvent.getSlot() != null ? armorEvent.getSlot().name() : "HAND";
            eventBuilder.setPlayerArmorStandManipulate(
                patchbukkit.events.PlayerArmorStandManipulateEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(armorEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setArmorStandUuid(UUID.newBuilder()
                        .setValue(armorEvent.getRightClicked().getUniqueId().toString())
                        .build())
                    .setItemKey(playerKey)
                    .setArmorStandItemKey(standKey)
                    .setSlot(slot)
                    .build()
            );
        } else if (event instanceof PlayerBedEnterEvent bedEvent) {
            Location location = bedEvent.getBed().getLocation();
            eventBuilder.setPlayerBedEnter(
                patchbukkit.events.PlayerBedEnterEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(bedEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBedLocation(BridgeUtils.convertLocation(location))
                    .build()
            );
        } else if (event instanceof PlayerBedLeaveEvent bedEvent) {
            Location location = bedEvent.getBed().getLocation();
            eventBuilder.setPlayerBedLeave(
                patchbukkit.events.PlayerBedLeaveEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(bedEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBedLocation(BridgeUtils.convertLocation(location))
                    .build()
            );
        } else if (event instanceof PlayerBucketEmptyEvent bucketEvent) {
            Location location = bucketEvent.getBlock().getLocation();
            String blockKey = bucketEvent.getBlock().getType().getKey().toString();
            String face = bucketEvent.getBlockFace() != null ? bucketEvent.getBlockFace().name() : "";
            String bucketKey = bucketEvent.getItemStack() != null
                ? bucketEvent.getItemStack().getType().getKey().toString()
                : "minecraft:air";
            String hand = bucketEvent.getHand() != null ? bucketEvent.getHand().name() : "HAND";
            eventBuilder.setPlayerBucketEmpty(
                patchbukkit.events.PlayerBucketEmptyEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(bucketEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setLocation(BridgeUtils.convertLocation(location))
                    .setBlockKey(blockKey)
                    .setBlockFace(face)
                    .setBucketItemKey(bucketKey)
                    .setHand(hand)
                    .build()
            );
        } else if (event instanceof PlayerBucketFillEvent bucketEvent) {
            Location location = bucketEvent.getBlock().getLocation();
            String blockKey = bucketEvent.getBlock().getType().getKey().toString();
            String face = bucketEvent.getBlockFace() != null ? bucketEvent.getBlockFace().name() : "";
            String bucketKey = bucketEvent.getItemStack() != null
                ? bucketEvent.getItemStack().getType().getKey().toString()
                : "minecraft:air";
            String hand = bucketEvent.getHand() != null ? bucketEvent.getHand().name() : "HAND";
            eventBuilder.setPlayerBucketFill(
                patchbukkit.events.PlayerBucketFillEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(bucketEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setLocation(BridgeUtils.convertLocation(location))
                    .setBlockKey(blockKey)
                    .setBlockFace(face)
                    .setBucketItemKey(bucketKey)
                    .setHand(hand)
                    .build()
            );
        } else if (event instanceof PlayerBucketEntityEvent bucketEvent) {
            String originalBucket = bucketEvent.getOriginalBucket() != null
                ? bucketEvent.getOriginalBucket().getType().getKey().toString()
                : "minecraft:air";
            String entityBucket = bucketEvent.getEntityBucket() != null
                ? bucketEvent.getEntityBucket().getType().getKey().toString()
                : "minecraft:air";
            String hand = bucketEvent.getHand() != null ? bucketEvent.getHand().name() : "HAND";
            eventBuilder.setPlayerBucketEntity(
                patchbukkit.events.PlayerBucketEntityEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(bucketEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setEntityUuid(UUID.newBuilder()
                        .setValue(bucketEvent.getEntity().getUniqueId().toString())
                        .build())
                    .setEntityType(bucketEvent.getEntity().getType().name())
                    .setOriginalBucketKey(originalBucket)
                    .setEntityBucketKey(entityBucket)
                    .setHand(hand)
                    .build()
            );
        } else if (event instanceof PlayerChangedMainHandEvent mainHandEvent) {
            String mainHand = mainHandEvent.getMainHand() != null
                ? mainHandEvent.getMainHand().name()
                : "RIGHT";
            eventBuilder.setPlayerChangedMainHand(
                patchbukkit.events.PlayerChangedMainHandEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(mainHandEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setMainHand(mainHand)
                    .build()
            );
        } else if (event instanceof PlayerRegisterChannelEvent registerChannelEvent) {
            eventBuilder.setPlayerRegisterChannel(
                patchbukkit.events.PlayerRegisterChannelEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(registerChannelEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setChannel(registerChannelEvent.getChannel())
                    .build()
            );
        } else if (event instanceof PlayerUnregisterChannelEvent unregisterChannelEvent) {
            eventBuilder.setPlayerUnregisterChannel(
                patchbukkit.events.PlayerUnregisterChannelEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(unregisterChannelEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setChannel(unregisterChannelEvent.getChannel())
                    .build()
            );
        } else if (event instanceof PlayerItemHeldEvent heldEvent) {
            eventBuilder.setPlayerItemHeld(
                patchbukkit.events.PlayerItemHeldEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(heldEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setPreviousSlot(heldEvent.getPreviousSlot())
                    .setNewSlot(heldEvent.getNewSlot())
                    .build()
            );
        } else if (event instanceof PlayerItemDamageEvent damageEvent) {
            ItemStack item = damageEvent.getItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int itemAmount = item != null ? item.getAmount() : 0;
            eventBuilder.setPlayerItemDamage(
                patchbukkit.events.PlayerItemDamageEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(damageEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .setDamage(damageEvent.getDamage())
                    .build()
            );
        } else if (event instanceof PlayerItemBreakEvent breakEvent) {
            ItemStack item = breakEvent.getBrokenItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int itemAmount = item != null ? item.getAmount() : 0;
            eventBuilder.setPlayerItemBreak(
                patchbukkit.events.PlayerItemBreakEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(breakEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .build()
            );
        } else if (event instanceof PlayerItemConsumeEvent consumeEvent) {
            ItemStack item = consumeEvent.getItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int itemAmount = item != null ? item.getAmount() : 0;
            String hand = consumeEvent.getHand() != null ? consumeEvent.getHand().name() : "HAND";
            eventBuilder.setPlayerItemConsume(
                patchbukkit.events.PlayerItemConsumeEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(consumeEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .setHand(hand)
                    .build()
            );
        } else if (event instanceof PlayerItemMendEvent mendEvent) {
            ItemStack item = mendEvent.getItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int itemAmount = item != null ? item.getAmount() : 0;
            String slot = mendEvent.getSlot() != null ? mendEvent.getSlot().name() : "HAND";
            var builder = patchbukkit.events.PlayerItemMendEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(mendEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setItemKey(itemKey)
                .setItemAmount(itemAmount)
                .setSlot(slot)
                .setRepairAmount(mendEvent.getRepairAmount());
            if (mendEvent.getExperienceOrb() != null) {
                builder.setOrbUuid(UUID.newBuilder()
                    .setValue(mendEvent.getExperienceOrb().getUniqueId().toString())
                    .build());
            }
            eventBuilder.setPlayerItemMend(builder.build());
        } else if (event instanceof AsyncPlayerChatEvent chatEvent) {
            var playerEventBuilder = patchbukkit.events.PlayerChatEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(chatEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setMessage(chatEvent.getMessage());

            for (Player recipient : chatEvent.getRecipients()) {
                playerEventBuilder.addRecipients(
                    UUID.newBuilder()
                        .setValue(recipient.getUniqueId().toString())
                        .build()
                );
            }

            eventBuilder.setPlayerChat(playerEventBuilder.build());
        } else if (event instanceof PlayerCommandPreprocessEvent commandEvent) {
            eventBuilder.setPlayerCommand(
                PlayerCommandEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(commandEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setCommand(commandEvent.getMessage())
                    .build()
            );
        } else if (event instanceof PlayerCommandSendEvent commandSendEvent) {
            var builder = patchbukkit.events.PlayerCommandSendEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(commandSendEvent.getPlayer().getUniqueId().toString())
                    .build());
            builder.addAllCommands(commandSendEvent.getCommands());
            eventBuilder.setPlayerCommandSend(builder.build());
        } else if (event instanceof PlayerDropItemEvent dropEvent) {
            var item = dropEvent.getItemDrop();
            String itemKey = "minecraft:air";
            int itemAmount = 0;
            if (item != null && item.getItemStack() != null) {
                itemKey = item.getItemStack().getType().getKey().toString();
                itemAmount = item.getItemStack().getAmount();
            }
            eventBuilder.setPlayerDropItem(
                patchbukkit.events.PlayerDropItemEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(dropEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setItemUuid(UUID.newBuilder()
                        .setValue(item != null ? item.getUniqueId().toString() : java.util.UUID.randomUUID().toString())
                        .build())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .build()
            );
        } else if (event instanceof PlayerEditBookEvent editEvent) {
            BookMeta newMeta = editEvent.getNewBookMeta();
            String title = "";
            java.util.List<String> pages = java.util.Collections.emptyList();
            if (newMeta != null) {
                pages = newMeta.getPages();
                if (newMeta.hasTitle()) {
                    title = newMeta.getTitle();
                }
            }
            eventBuilder.setPlayerEditBook(
                patchbukkit.events.PlayerEditBookEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(editEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setSlot(editEvent.getSlot())
                    .addAllPages(pages)
                    .setTitle(title)
                    .setIsSigning(editEvent.isSigning())
                    .build()
            );
        } else if (event instanceof PlayerEggThrowEvent eggEvent) {
            var hatchingType = eggEvent.getHatchingType() != null
                ? eggEvent.getHatchingType().name()
                : "CHICKEN";
            var egg = eggEvent.getEgg();
            var eggUuid = egg != null ? egg.getUniqueId().toString() : java.util.UUID.randomUUID().toString();
            eventBuilder.setPlayerEggThrow(
                patchbukkit.events.PlayerEggThrowEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(eggEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setEggUuid(UUID.newBuilder()
                        .setValue(eggUuid)
                        .build())
                    .setHatching(eggEvent.isHatching())
                    .setNumHatches(eggEvent.getNumHatches())
                    .setHatchingType(hatchingType)
                    .build()
            );
        } else if (event instanceof PlayerExpChangeEvent expEvent) {
            eventBuilder.setPlayerExpChange(
                patchbukkit.events.PlayerExpChangeEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(expEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setAmount(expEvent.getAmount())
                    .build()
            );
        } else if (event instanceof PlayerFishEvent fishEvent) {
            var hook = fishEvent.getHook();
            var hookUuid = hook != null ? hook.getUniqueId().toString() : java.util.UUID.randomUUID().toString();
            var caught = fishEvent.getCaught();
            var fishBuilder = patchbukkit.events.PlayerFishEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(fishEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setHookUuid(UUID.newBuilder()
                    .setValue(hookUuid)
                    .build())
                .setState(fishEvent.getState().name())
                .setExpToDrop(fishEvent.getExpToDrop());
            if (fishEvent.getHand() != null) {
                fishBuilder.setHand(fishEvent.getHand().name());
            }
            if (caught != null) {
                fishBuilder.setCaughtUuid(UUID.newBuilder()
                    .setValue(caught.getUniqueId().toString())
                    .build());
                fishBuilder.setCaughtType(caught.getType().name());
            }
            eventBuilder.setPlayerFish(fishBuilder.build());
        } else if (event instanceof PlayerInteractAtEntityEvent interactEvent) {
            var entity = interactEvent.getRightClicked();
            var pos = interactEvent.getClickedPosition();
            var builder = patchbukkit.events.PlayerInteractAtEntityEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(interactEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setEntityUuid(UUID.newBuilder()
                    .setValue(entity.getUniqueId().toString())
                    .build())
                .setEntityType(entity.getType().name());
            if (interactEvent.getHand() != null) {
                builder.setHand(interactEvent.getHand().name());
            }
            if (pos != null) {
                builder.setClickedPosition(patchbukkit.common.Vec3.newBuilder()
                    .setX(pos.getX())
                    .setY(pos.getY())
                    .setZ(pos.getZ())
                    .build());
            }
            eventBuilder.setPlayerInteractAtEntity(builder.build());
        } else if (event instanceof PlayerInteractEntityEvent interactEvent) {
            var entity = interactEvent.getRightClicked();
            var builder = patchbukkit.events.PlayerInteractEntityEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(interactEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setEntityUuid(UUID.newBuilder()
                    .setValue(entity.getUniqueId().toString())
                    .build())
                .setEntityType(entity.getType().name());
            if (interactEvent.getHand() != null) {
                builder.setHand(interactEvent.getHand().name());
            }
            eventBuilder.setPlayerInteractEntity(builder.build());
        } else if (event instanceof PlayerInteractEvent interactEvent) {
            var block = interactEvent.getClickedBlock();
            var location = block != null ? block.getLocation() : null;
            String blockKey = block != null ? block.getType().getKey().toString() : "minecraft:air";
            String itemKey = interactEvent.getItem() != null
                ? interactEvent.getItem().getType().getKey().toString()
                : "minecraft:air";
            String face = interactEvent.getBlockFace() != null
                ? interactEvent.getBlockFace().name()
                : "";
            eventBuilder.setPlayerInteract(
                patchbukkit.events.PlayerInteractEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(interactEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setAction(interactEvent.getAction().name())
                    .setBlockKey(blockKey)
                    .setItemKey(itemKey)
                    .setBlockFace(face)
                    .setClicked(BridgeUtils.convertLocation(location))
                    .build()
            );
        } else if (event instanceof BlockBreakEvent breakEvent) {
            var block = breakEvent.getBlock();
            eventBuilder.setBlockBreak(
                patchbukkit.events.BlockBreakEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(breakEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setExp(breakEvent.getExpToDrop())
                    .setDrop(breakEvent.isDropItems())
                    .build()
            );
        } else if (event instanceof BlockPlaceEvent placeEvent) {
            var block = placeEvent.getBlockPlaced();
            var against = placeEvent.getBlockAgainst();
            eventBuilder.setBlockPlace(
                patchbukkit.events.BlockPlaceEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(placeEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBlockKey(block.getType().getKey().toString())
                    .setBlockAgainstKey(against.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setCanBuild(placeEvent.canBuild())
                    .build()
            );
        } else if (event instanceof EntitySpawnEvent spawnEvent) {
            eventBuilder.setEntitySpawn(
                patchbukkit.events.EntitySpawnEvent.newBuilder()
                    .setEntityUuid(UUID.newBuilder()
                        .setValue(spawnEvent.getEntity().getUniqueId().toString())
                        .build())
                    .setEntityType(spawnEvent.getEntity().getType().name())
                    .build()
            );
        } else if (event instanceof EntityDamageEvent damageEvent) {
            eventBuilder.setEntityDamage(
                patchbukkit.events.EntityDamageEvent.newBuilder()
                    .setEntityUuid(UUID.newBuilder()
                        .setValue(damageEvent.getEntity().getUniqueId().toString())
                        .build())
                    .setDamage((float) damageEvent.getFinalDamage())
                    .setDamageType(damageEvent.getCause().name())
                    .build()
            );
        } else if (event instanceof EntityDeathEvent deathEvent) {
            eventBuilder.setEntityDeath(
                patchbukkit.events.EntityDeathEvent.newBuilder()
                    .setEntityUuid(UUID.newBuilder()
                        .setValue(deathEvent.getEntity().getUniqueId().toString())
                        .build())
                    .setDamageType("CUSTOM")
                    .build()
            );
        } else if (event instanceof ServerCommandEvent commandEvent) {
            eventBuilder.setServerCommand(
                patchbukkit.events.ServerCommandEvent.newBuilder()
                    .setCommand(commandEvent.getCommand())
                    .build()
            );
        } else if (event instanceof BroadcastMessageEvent broadcastEvent) {
            String messageJson = GsonComponentSerializer.gson().serialize(
                net.kyori.adventure.text.Component.text(broadcastEvent.getMessage())
            );
            String senderJson = GsonComponentSerializer.gson().serialize(
                net.kyori.adventure.text.Component.text("")
            );
            eventBuilder.setServerBroadcast(
                patchbukkit.events.ServerBroadcastEvent.newBuilder()
                    .setMessage(messageJson)
                    .setSender(senderJson)
                    .build()
            );
        }

        builder.setData(eventBuilder.build());

        return builder.build().toByteArray();
    }

    public static boolean isCancellable(@NotNull org.bukkit.event.Event event) {
        return event instanceof org.bukkit.event.Cancellable;
    }

    @Nullable
    private static Player getPlayer(@NotNull String uuidStr) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            Player player = Bukkit.getServer().getPlayer(uuid);
            if (player == null) {
                LOGGER.warning("EventFactory: Player not found for UUID " + uuidStr);
            }
            return player;
        } catch (IllegalArgumentException e) {
            LOGGER.severe("EventFactory: Invalid UUID string: " + uuidStr);
            return null;
        }
    }

    @Nullable
    private static PlayerLoginEvent createLoginEvent(@NotNull Player player) {
        try {
            return PlayerLoginEvent.class.getConstructor(Player.class, String.class, InetAddress.class)
                .newInstance(player, "", null);
        } catch (ReflectiveOperationException ignored) {
            try {
                return PlayerLoginEvent.class.getConstructor(Player.class, String.class, java.net.InetAddress.class,
                        PlayerLoginEvent.Result.class, String.class)
                    .newInstance(player, "", null, PlayerLoginEvent.Result.ALLOWED, "");
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    @NotNull
    private static ItemStack materialToItem(@NotNull String key, int amount) {
        ItemStack stack = materialToItem(key);
        stack.setAmount(Math.max(amount, 0));
        return stack;
    }

    @NotNull
    private static ItemStack materialToItem(@NotNull String key) {
        if (key.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        Material material = Material.matchMaterial(key);
        if (material == null) {
            material = Material.matchMaterial("minecraft:" + key);
        }
        if (material == null) {
            material = Material.AIR;
        }
        return new ItemStack(material);
    }
}
