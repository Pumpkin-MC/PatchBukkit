package org.patchbukkit.events;

import com.google.protobuf.InvalidProtocolBufferException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageAbortEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockMultiPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.block.TNTPrimeEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.block.SpongeAbsorbEvent;
import org.bukkit.event.block.FluidLevelChangeEvent;
import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.event.world.SpawnChangeEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
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
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
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
import org.patchbukkit.entity.PatchBukkitAbstractArrow;
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
import org.bukkit.Statistic;
import org.bukkit.entity.EntityType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
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
                    String plainKickMessage = PlainTextComponentSerializer.plainText().serialize(kickMessage);
                    applyLoginResult(login, plainKickMessage);
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
                org.bukkit.event.Event created = createPlayerBedEnterEvent(player, bedLocation.getBlock());
                if (created instanceof PlayerBedEnterEvent) {
                    yield created;
                }
                yield null;
            }
            case PLAYER_BED_LEAVE -> {
                patchbukkit.events.PlayerBedLeaveEvent bedEvent = event.getPlayerBedLeave();
                Player player = getPlayer(bedEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location bedLocation = BridgeUtils.convertLocation(bedEvent.getBedLocation());
                if (bedLocation == null) yield null;
                if (bedLocation.getWorld() == null) yield null;
                org.bukkit.event.Event created = createPlayerBedLeaveEvent(player, bedLocation.getBlock());
                if (created instanceof PlayerBedLeaveEvent) {
                    yield created;
                }
                yield null;
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
            case PLAYER_LEVEL_CHANGE -> {
                patchbukkit.events.PlayerLevelChangeEvent levelEvent = event.getPlayerLevelChange();
                Player player = getPlayer(levelEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerLevelChangeEvent(player, levelEvent.getOldLevel(), levelEvent.getNewLevel());
            }
            case PLAYER_KICK -> {
                patchbukkit.events.PlayerKickEvent kickEvent = event.getPlayerKick();
                Player player = getPlayer(kickEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Component reason = kickEvent.getReason().isEmpty()
                    ? Component.text("")
                    : GsonComponentSerializer.gson().deserialize(kickEvent.getReason());
                Component leaveMessage = kickEvent.getLeaveMessage().isEmpty()
                    ? Component.text("")
                    : GsonComponentSerializer.gson().deserialize(kickEvent.getLeaveMessage());
                PlayerKickEvent.Cause cause = PlayerKickEvent.Cause.UNKNOWN;
                if (!kickEvent.getCause().isEmpty()) {
                    try {
                        cause = PlayerKickEvent.Cause.valueOf(kickEvent.getCause());
                    } catch (IllegalArgumentException ignored) {
                        cause = PlayerKickEvent.Cause.UNKNOWN;
                    }
                }
                yield new PlayerKickEvent(player, reason, leaveMessage, cause);
            }
            case PLAYER_TOGGLE_SNEAK -> {
                patchbukkit.events.PlayerToggleSneakEvent toggleEvent = event.getPlayerToggleSneak();
                Player player = getPlayer(toggleEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerToggleSneakEvent(player, toggleEvent.getIsSneaking());
            }
            case PLAYER_TOGGLE_SPRINT -> {
                patchbukkit.events.PlayerToggleSprintEvent toggleEvent = event.getPlayerToggleSprint();
                Player player = getPlayer(toggleEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerToggleSprintEvent(player, toggleEvent.getIsSprinting());
            }
            case PLAYER_TOGGLE_FLIGHT -> {
                patchbukkit.events.PlayerToggleFlightEvent toggleEvent = event.getPlayerToggleFlight();
                Player player = getPlayer(toggleEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                yield new PlayerToggleFlightEvent(player, toggleEvent.getIsFlying());
            }
            case PLAYER_SWAP_HAND_ITEMS -> {
                patchbukkit.events.PlayerSwapHandItemsEvent swapEvent = event.getPlayerSwapHandItems();
                Player player = getPlayer(swapEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                ItemStack mainHand = materialToItem(swapEvent.getMainHandItemKey(), swapEvent.getMainHandItemAmount());
                ItemStack offHand = materialToItem(swapEvent.getOffHandItemKey(), swapEvent.getOffHandItemAmount());
                yield new PlayerSwapHandItemsEvent(player, mainHand, offHand);
            }
            case PLAYER_RESOURCE_PACK_STATUS -> {
                patchbukkit.events.PlayerResourcePackStatusEvent packEvent = event.getPlayerResourcePackStatus();
                Player player = getPlayer(packEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID packUuid;
                try {
                    packUuid = java.util.UUID.fromString(packEvent.getPackUuid().getValue());
                } catch (IllegalArgumentException e) {
                    packUuid = java.util.UUID.randomUUID();
                }
                PlayerResourcePackStatusEvent.Status status = PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD;
                if (!packEvent.getStatus().isEmpty()) {
                    try {
                        status = PlayerResourcePackStatusEvent.Status.valueOf(packEvent.getStatus());
                    } catch (IllegalArgumentException ignored) {
                        status = PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD;
                    }
                }
                PlayerResourcePackStatusEvent statusEvent =
                    new PlayerResourcePackStatusEvent(player, packUuid, status);
                if (!packEvent.getHash().isEmpty()) {
                    try {
                        java.lang.reflect.Method method = statusEvent.getClass().getMethod("setHash", String.class);
                        method.invoke(statusEvent, packEvent.getHash());
                    } catch (ReflectiveOperationException ignored) {
                        // ignore
                    }
                }
                yield statusEvent;
            }
            case PLAYER_RESPAWN -> {
                patchbukkit.events.PlayerRespawnEvent respawnEvent = event.getPlayerRespawn();
                Player player = getPlayer(respawnEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location location = BridgeUtils.convertLocation(respawnEvent.getRespawnLocation());
                if (location == null) yield null;
                PlayerRespawnEvent.RespawnReason reason = PlayerRespawnEvent.RespawnReason.DEATH;
                if (!respawnEvent.getRespawnReason().isEmpty()) {
                    try {
                        reason = PlayerRespawnEvent.RespawnReason.valueOf(respawnEvent.getRespawnReason());
                    } catch (IllegalArgumentException ignored) {
                        reason = PlayerRespawnEvent.RespawnReason.DEATH;
                    }
                }
                yield new PlayerRespawnEvent(
                    player,
                    location,
                    respawnEvent.getIsBedSpawn(),
                    respawnEvent.getIsAnchorSpawn(),
                    respawnEvent.getIsMissingRespawnBlock(),
                    reason
                );
            }
            case PLAYER_PICKUP_ARROW -> {
                patchbukkit.events.PlayerPickupArrowEvent pickupEvent = event.getPlayerPickupArrow();
                Player player = getPlayer(pickupEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID arrowUuid;
                try {
                    arrowUuid = java.util.UUID.fromString(pickupEvent.getArrowUuid().getValue());
                } catch (IllegalArgumentException e) {
                    arrowUuid = java.util.UUID.randomUUID();
                }
                java.util.UUID itemUuid;
                try {
                    itemUuid = java.util.UUID.fromString(pickupEvent.getItemUuid().getValue());
                } catch (IllegalArgumentException e) {
                    itemUuid = java.util.UUID.randomUUID();
                }
                ItemStack stack = materialToItem(pickupEvent.getItemKey(), pickupEvent.getItemAmount());
                PatchBukkitItem item = new PatchBukkitItem(itemUuid, stack);
                PatchBukkitAbstractArrow arrow = new PatchBukkitAbstractArrow(arrowUuid, "Arrow");
                yield new PlayerPickupArrowEvent(player, item, arrow);
            }
            case PLAYER_PORTAL -> {
                patchbukkit.events.PlayerPortalEvent portalEvent = event.getPlayerPortal();
                Player player = getPlayer(portalEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location from = BridgeUtils.convertLocation(portalEvent.getFrom());
                Location to = BridgeUtils.convertLocation(portalEvent.getTo());
                if (from == null || to == null) yield null;
                PlayerTeleportEvent.TeleportCause cause = PlayerTeleportEvent.TeleportCause.UNKNOWN;
                if (!portalEvent.getCause().isEmpty()) {
                    try {
                        cause = PlayerTeleportEvent.TeleportCause.valueOf(portalEvent.getCause());
                    } catch (IllegalArgumentException ignored) {
                        cause = PlayerTeleportEvent.TeleportCause.UNKNOWN;
                    }
                }
                PlayerPortalEvent eventObj = new PlayerPortalEvent(player, from, to, cause);
                try {
                    var method = PlayerPortalEvent.class.getMethod("setSearchRadius", int.class);
                    method.invoke(eventObj, portalEvent.getSearchRadius());
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                try {
                    var method = PlayerPortalEvent.class.getMethod("setCanCreatePortal", boolean.class);
                    method.invoke(eventObj, portalEvent.getCanCreatePortal());
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                try {
                    var method = PlayerPortalEvent.class.getMethod("setCreationRadius", int.class);
                    method.invoke(eventObj, portalEvent.getCreationRadius());
                } catch (ReflectiveOperationException ignored) {
                    // ignore
                }
                yield eventObj;
            }
            case PLAYER_RECIPE_DISCOVER -> {
                patchbukkit.events.PlayerRecipeDiscoverEvent recipeEvent = event.getPlayerRecipeDiscover();
                Player player = getPlayer(recipeEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                if (recipeEvent.getRecipeKey().isEmpty()) yield null;
                NamespacedKey key = NamespacedKey.fromString(recipeEvent.getRecipeKey());
                if (key == null) yield null;
                org.bukkit.event.Event created = createPlayerRecipeDiscoverEvent(player, key);
                if (created instanceof PlayerRecipeDiscoverEvent) {
                    yield created;
                }
                yield null;
            }
            case PLAYER_RIPTIDE -> {
                patchbukkit.events.PlayerRiptideEvent riptideEvent = event.getPlayerRiptide();
                Player player = getPlayer(riptideEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                ItemStack item = materialToItem(riptideEvent.getItemKey(), riptideEvent.getItemAmount());
                Vector velocity = new Vector();
                if (riptideEvent.getVelocity() != null) {
                    velocity = new Vector(
                        riptideEvent.getVelocity().getX(),
                        riptideEvent.getVelocity().getY(),
                        riptideEvent.getVelocity().getZ()
                    );
                }
                yield new PlayerRiptideEvent(player, item, velocity);
            }
            case PLAYER_SHEAR_ENTITY -> {
                patchbukkit.events.PlayerShearEntityEvent shearEvent = event.getPlayerShearEntity();
                Player player = getPlayer(shearEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                java.util.UUID entityUuid;
                try {
                    entityUuid = java.util.UUID.fromString(shearEvent.getEntityUuid().getValue());
                } catch (IllegalArgumentException e) {
                    entityUuid = java.util.UUID.randomUUID();
                }
                PatchBukkitEntity entity = new PatchBukkitEntity(entityUuid, shearEvent.getEntityType());
                ItemStack item = materialToItem(shearEvent.getItemKey(), shearEvent.getItemAmount());
                EquipmentSlot slot = EquipmentSlot.HAND;
                if (!shearEvent.getHand().isEmpty()) {
                    try {
                        slot = EquipmentSlot.valueOf(shearEvent.getHand());
                    } catch (IllegalArgumentException ignored) {
                        slot = EquipmentSlot.HAND;
                    }
                }
                org.bukkit.event.Event created = createPlayerShearEntityEvent(player, entity, item, slot);
                if (created instanceof PlayerShearEntityEvent) {
                    yield created;
                }
                yield null;
            }
            case PLAYER_SPAWN_LOCATION -> {
                patchbukkit.events.PlayerSpawnLocationEvent spawnEvent = event.getPlayerSpawnLocation();
                Player player = getPlayer(spawnEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location location = BridgeUtils.convertLocation(spawnEvent.getSpawnLocation());
                if (location == null) yield null;
                yield new PlayerSpawnLocationEvent(player, location);
            }
            case PLAYER_STATISTIC_INCREMENT -> {
                patchbukkit.events.PlayerStatisticIncrementEvent statEvent = event.getPlayerStatisticIncrement();
                Player player = getPlayer(statEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Statistic statistic;
                try {
                    statistic = Statistic.valueOf(statEvent.getStatistic());
                } catch (IllegalArgumentException e) {
                    yield null;
                }
                if (!statEvent.getEntityType().isEmpty()) {
                    try {
                        EntityType entityType = EntityType.valueOf(statEvent.getEntityType());
                        yield new PlayerStatisticIncrementEvent(
                            player,
                            statistic,
                            statEvent.getInitialValue(),
                            statEvent.getNewValue(),
                            entityType
                        );
                    } catch (IllegalArgumentException ignored) {
                        yield null;
                    }
                }
                if (!statEvent.getMaterialKey().isEmpty()) {
                    Material material = Material.matchMaterial(statEvent.getMaterialKey());
                    if (material == null) {
                        material = Material.matchMaterial("minecraft:" + statEvent.getMaterialKey());
                    }
                    if (material == null) yield null;
                    yield new PlayerStatisticIncrementEvent(
                        player,
                        statistic,
                        statEvent.getInitialValue(),
                        statEvent.getNewValue(),
                        material
                    );
                }
                yield new PlayerStatisticIncrementEvent(
                    player,
                    statistic,
                    statEvent.getInitialValue(),
                    statEvent.getNewValue()
                );
            }
            case PLAYER_VELOCITY -> {
                patchbukkit.events.PlayerVelocityEvent velocityEvent = event.getPlayerVelocity();
                Player player = getPlayer(velocityEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Vector velocity = new Vector();
                if (velocityEvent.getVelocity() != null) {
                    velocity = new Vector(
                        velocityEvent.getVelocity().getX(),
                        velocityEvent.getVelocity().getY(),
                        velocityEvent.getVelocity().getZ()
                    );
                }
                yield new PlayerVelocityEvent(player, velocity);
            }
            case PLAYER_HARVEST_BLOCK -> {
                patchbukkit.events.PlayerHarvestBlockEvent harvestEvent = event.getPlayerHarvestBlock();
                Player player = getPlayer(harvestEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location location = BridgeUtils.convertLocation(harvestEvent.getBlockLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    harvestEvent.getBlockKey()
                );
                ItemStack tool = materialToItem(harvestEvent.getItemKey(), harvestEvent.getItemAmount());
                org.bukkit.event.Event constructed = createHarvestBlockEvent(player, block, tool);
                if (constructed instanceof PlayerHarvestBlockEvent harvest) {
                    yield harvest;
                }
                yield null;
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
            case BLOCK_DAMAGE -> {
                patchbukkit.events.BlockDamageEvent damageEvent = event.getBlockDamage();
                Player player = getPlayer(damageEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(damageEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    damageEvent.getBlockKey()
                );

                ItemStack item = null;
                if (!damageEvent.getItemKey().isEmpty()) {
                    Material material = Material.matchMaterial(damageEvent.getItemKey());
                    if (material == null) {
                        material = Material.matchMaterial("minecraft:" + damageEvent.getItemKey());
                    }
                    if (material != null) {
                        item = new ItemStack(material);
                    }
                }

                org.bukkit.event.Event created = createBlockDamageEvent(block, player, item, damageEvent.getInstaBreak());
                if (created instanceof BlockDamageEvent) {
                    yield created;
                }
                yield null;
            }
            case BLOCK_DAMAGE_ABORT -> {
                patchbukkit.events.BlockDamageAbortEvent damageAbortEvent = event.getBlockDamageAbort();
                Player player = getPlayer(damageAbortEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(damageAbortEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    damageAbortEvent.getBlockKey()
                );

                ItemStack item = null;
                if (!damageAbortEvent.getItemKey().isEmpty()) {
                    Material material = Material.matchMaterial(damageAbortEvent.getItemKey());
                    if (material == null) {
                        material = Material.matchMaterial("minecraft:" + damageAbortEvent.getItemKey());
                    }
                    if (material != null) {
                        item = new ItemStack(material);
                    }
                }

                org.bukkit.event.Event created = createBlockDamageAbortEvent(block, player, item);
                if (created instanceof BlockDamageAbortEvent) {
                    yield created;
                }
                yield null;
            }
            case BLOCK_DISPENSE -> {
                patchbukkit.events.BlockDispenseEvent dispenseEvent = event.getBlockDispense();
                Location location = BridgeUtils.convertLocation(dispenseEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    dispenseEvent.getBlockKey()
                );

                ItemStack item = null;
                if (!dispenseEvent.getItemKey().isEmpty()) {
                    Material material = Material.matchMaterial(dispenseEvent.getItemKey());
                    if (material == null) {
                        material = Material.matchMaterial("minecraft:" + dispenseEvent.getItemKey());
                    }
                    if (material != null) {
                        int amount = Math.max(1, dispenseEvent.getItemAmount());
                        item = new ItemStack(material, amount);
                    }
                }

                Vector velocity = new Vector();
                if (dispenseEvent.hasVelocity()) {
                    velocity.setX(dispenseEvent.getVelocity().getX());
                    velocity.setY(dispenseEvent.getVelocity().getY());
                    velocity.setZ(dispenseEvent.getVelocity().getZ());
                }

                yield new BlockDispenseEvent(block, item, velocity);
            }
            case BLOCK_DROP_ITEM -> {
                patchbukkit.events.BlockDropItemEvent dropItemEvent = event.getBlockDropItem();
                Player player = getPlayer(dropItemEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(dropItemEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    dropItemEvent.getBlockKey()
                );

                java.util.List<org.bukkit.entity.Item> items = new java.util.ArrayList<>();
                for (patchbukkit.events.BlockDropItemEntry entry : dropItemEvent.getItemsList()) {
                    if (entry.getItemKey().isEmpty() || entry.getItemAmount() <= 0) continue;
                    Material material = Material.matchMaterial(entry.getItemKey());
                    if (material == null) {
                        material = Material.matchMaterial("minecraft:" + entry.getItemKey());
                    }
                    if (material == null) continue;
                    ItemStack stack = new ItemStack(material, entry.getItemAmount());
                    org.patchbukkit.entity.PatchBukkitItem itemEntity =
                        new org.patchbukkit.entity.PatchBukkitItem(java.util.UUID.randomUUID(), stack);
                    items.add(itemEntity);
                }

                org.bukkit.event.Event created = createBlockDropItemEvent(block, player, items);
                if (created instanceof BlockDropItemEvent) {
                    yield created;
                }
                yield null;
            }
            case BLOCK_EXPLODE -> {
                patchbukkit.events.BlockExplodeEvent explodeEvent = event.getBlockExplode();
                Location location = BridgeUtils.convertLocation(explodeEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    explodeEvent.getBlockKey()
                );

                java.util.List<org.bukkit.block.Block> blockList = new java.util.ArrayList<>();
                for (patchbukkit.events.BlockExplodeBlockEntry entry : explodeEvent.getBlocksList()) {
                    Location entryLoc = BridgeUtils.convertLocation(entry.getLocation());
                    if (entryLoc == null || !(entryLoc.getWorld() instanceof PatchBukkitWorld entryWorld)) {
                        continue;
                    }
                    String key = entry.getBlockKey().isEmpty()
                        ? "minecraft:air"
                        : entry.getBlockKey();
                    org.bukkit.block.Block entryBlock = PatchBukkitBlock.create(
                        entryWorld,
                        entryLoc.getBlockX(),
                        entryLoc.getBlockY(),
                        entryLoc.getBlockZ(),
                        key
                    );
                    blockList.add(entryBlock);
                }

                org.bukkit.event.Event created = createBlockExplodeEvent(block, blockList, explodeEvent.getYield());
                if (created instanceof BlockExplodeEvent) {
                    yield created;
                }
                yield null;
            }
            case BLOCK_FADE -> {
                patchbukkit.events.BlockFadeEvent fadeEvent = event.getBlockFade();
                Location location = BridgeUtils.convertLocation(fadeEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    fadeEvent.getBlockKey()
                );
                String newKey = fadeEvent.getNewBlockKey().isEmpty()
                    ? "minecraft:air"
                    : fadeEvent.getNewBlockKey();
                org.bukkit.block.Block newBlock = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    newKey
                );
                org.bukkit.block.BlockState newState = newBlock.getState();
                yield new BlockFadeEvent(block, newState);
            }
            case BLOCK_FERTILIZE -> {
                patchbukkit.events.BlockFertilizeEvent fertilizeEvent = event.getBlockFertilize();
                Player player = getPlayer(fertilizeEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(fertilizeEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    fertilizeEvent.getBlockKey()
                );

                java.util.List<org.bukkit.block.BlockState> states = new java.util.ArrayList<>();
                for (patchbukkit.events.BlockFertilizeBlockEntry entry : fertilizeEvent.getBlocksList()) {
                    Location entryLoc = BridgeUtils.convertLocation(entry.getLocation());
                    if (entryLoc == null || !(entryLoc.getWorld() instanceof PatchBukkitWorld entryWorld)) {
                        continue;
                    }
                    String key = entry.getBlockKey().isEmpty()
                        ? "minecraft:air"
                        : entry.getBlockKey();
                    org.bukkit.block.Block entryBlock = PatchBukkitBlock.create(
                        entryWorld,
                        entryLoc.getBlockX(),
                        entryLoc.getBlockY(),
                        entryLoc.getBlockZ(),
                        key
                    );
                    states.add(entryBlock.getState());
                }

                yield new BlockFertilizeEvent(block, player, states);
            }
            case BLOCK_FORM -> {
                patchbukkit.events.BlockFormEvent formEvent = event.getBlockForm();
                Location location = BridgeUtils.convertLocation(formEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    formEvent.getBlockKey()
                );
                String newKey = formEvent.getNewBlockKey().isEmpty()
                    ? "minecraft:air"
                    : formEvent.getNewBlockKey();
                org.bukkit.block.Block newBlock = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    newKey
                );
                org.bukkit.block.BlockState newState = newBlock.getState();
                yield new BlockFormEvent(block, newState);
            }
            case BLOCK_FROM_TO -> {
                patchbukkit.events.BlockFromToEvent fromToEvent = event.getBlockFromTo();
                Location location = BridgeUtils.convertLocation(fromToEvent.getLocation());
                Location toLocation = BridgeUtils.convertLocation(fromToEvent.getToLocation());
                if (location == null || toLocation == null) yield null;
                if (!(location.getWorld() instanceof PatchBukkitWorld world)) yield null;
                if (!(toLocation.getWorld() instanceof PatchBukkitWorld toWorld)) yield null;

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    fromToEvent.getBlockKey()
                );
                String toKey = fromToEvent.getToBlockKey().isEmpty()
                    ? "minecraft:air"
                    : fromToEvent.getToBlockKey();
                org.bukkit.block.Block toBlock = PatchBukkitBlock.create(
                    toWorld,
                    toLocation.getBlockX(),
                    toLocation.getBlockY(),
                    toLocation.getBlockZ(),
                    toKey
                );
                yield new BlockFromToEvent(block, toBlock);
            }
            case BLOCK_GROW -> {
                patchbukkit.events.BlockGrowEvent growEvent = event.getBlockGrow();
                Location location = BridgeUtils.convertLocation(growEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    growEvent.getBlockKey()
                );
                String newKey = growEvent.getNewBlockKey().isEmpty()
                    ? "minecraft:air"
                    : growEvent.getNewBlockKey();
                org.bukkit.block.Block newBlock = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    newKey
                );
                org.bukkit.block.BlockState newState = newBlock.getState();
                yield new BlockGrowEvent(block, newState);
            }
            case BLOCK_PISTON_EXTEND -> {
                patchbukkit.events.BlockPistonExtendEvent extendEvent = event.getBlockPistonExtend();
                Location location = BridgeUtils.convertLocation(extendEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    extendEvent.getBlockKey()
                );
                java.util.List<org.bukkit.block.Block> blocks = new java.util.ArrayList<>();
                for (patchbukkit.events.BlockPistonBlockEntry entry : extendEvent.getBlocksList()) {
                    Location entryLoc = BridgeUtils.convertLocation(entry.getLocation());
                    if (entryLoc == null || !(entryLoc.getWorld() instanceof PatchBukkitWorld entryWorld)) {
                        continue;
                    }
                    String key = entry.getBlockKey().isEmpty()
                        ? "minecraft:air"
                        : entry.getBlockKey();
                    org.bukkit.block.Block entryBlock = PatchBukkitBlock.create(
                        entryWorld,
                        entryLoc.getBlockX(),
                        entryLoc.getBlockY(),
                        entryLoc.getBlockZ(),
                        key
                    );
                    blocks.add(entryBlock);
                }
                BlockFace face = BlockFace.SELF;
                if (!extendEvent.getDirection().isEmpty()) {
                    try {
                        face = BlockFace.valueOf(extendEvent.getDirection());
                    } catch (IllegalArgumentException ignored) {
                        face = BlockFace.SELF;
                    }
                }
                org.bukkit.event.Event created = createBlockPistonExtendEvent(
                    block,
                    blocks,
                    extendEvent.getLength(),
                    face
                );
                if (created instanceof BlockPistonExtendEvent) {
                    yield created;
                }
                yield null;
            }
            case BLOCK_PISTON_RETRACT -> {
                patchbukkit.events.BlockPistonRetractEvent retractEvent = event.getBlockPistonRetract();
                Location location = BridgeUtils.convertLocation(retractEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    retractEvent.getBlockKey()
                );
                java.util.List<org.bukkit.block.Block> blocks = new java.util.ArrayList<>();
                for (patchbukkit.events.BlockPistonBlockEntry entry : retractEvent.getBlocksList()) {
                    Location entryLoc = BridgeUtils.convertLocation(entry.getLocation());
                    if (entryLoc == null || !(entryLoc.getWorld() instanceof PatchBukkitWorld entryWorld)) {
                        continue;
                    }
                    String key = entry.getBlockKey().isEmpty()
                        ? "minecraft:air"
                        : entry.getBlockKey();
                    org.bukkit.block.Block entryBlock = PatchBukkitBlock.create(
                        entryWorld,
                        entryLoc.getBlockX(),
                        entryLoc.getBlockY(),
                        entryLoc.getBlockZ(),
                        key
                    );
                    blocks.add(entryBlock);
                }
                BlockFace face = BlockFace.SELF;
                if (!retractEvent.getDirection().isEmpty()) {
                    try {
                        face = BlockFace.valueOf(retractEvent.getDirection());
                    } catch (IllegalArgumentException ignored) {
                        face = BlockFace.SELF;
                    }
                }
                org.bukkit.event.Event created = createBlockPistonRetractEvent(
                    block,
                    blocks,
                    retractEvent.getLength(),
                    face
                );
                if (created instanceof BlockPistonRetractEvent) {
                    yield created;
                }
                yield null;
            }
            case BLOCK_REDSTONE -> {
                patchbukkit.events.BlockRedstoneEvent redstoneEvent = event.getBlockRedstone();
                Location location = BridgeUtils.convertLocation(redstoneEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    redstoneEvent.getBlockKey()
                );
                yield new BlockRedstoneEvent(block, redstoneEvent.getOldCurrent(), redstoneEvent.getNewCurrent());
            }
            case BLOCK_MULTI_PLACE -> {
                patchbukkit.events.BlockMultiPlaceEvent multiEvent = event.getBlockMultiPlace();
                Player player = getPlayer(multiEvent.getPlayerUuid().getValue());
                if (player == null) yield null;
                Location location = BridgeUtils.convertLocation(multiEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    multiEvent.getBlockKey()
                );
                java.util.List<org.bukkit.block.BlockState> states = new java.util.ArrayList<>();
                for (patchbukkit.events.BlockMultiPlaceBlockEntry entry : multiEvent.getBlocksList()) {
                    Location entryLoc = BridgeUtils.convertLocation(entry.getLocation());
                    if (entryLoc == null || !(entryLoc.getWorld() instanceof PatchBukkitWorld entryWorld)) {
                        continue;
                    }
                    String key = entry.getBlockKey().isEmpty()
                        ? "minecraft:air"
                        : entry.getBlockKey();
                    org.bukkit.block.Block entryBlock = PatchBukkitBlock.create(
                        entryWorld,
                        entryLoc.getBlockX(),
                        entryLoc.getBlockY(),
                        entryLoc.getBlockZ(),
                        key
                    );
                    states.add(entryBlock.getState());
                }
                yield new BlockMultiPlaceEvent(states, block, null, player, true);
            }
            case BLOCK_PHYSICS -> {
                patchbukkit.events.BlockPhysicsEvent physicsEvent = event.getBlockPhysics();
                Location location = BridgeUtils.convertLocation(physicsEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    physicsEvent.getBlockKey()
                );
                Material material = Material.matchMaterial(physicsEvent.getSourceBlockKey());
                if (material == null) {
                    material = Material.matchMaterial("minecraft:" + physicsEvent.getSourceBlockKey());
                }
                org.bukkit.block.data.BlockData data = material != null
                    ? material.createBlockData()
                    : block.getBlockData();
                yield new BlockPhysicsEvent(block, data);
            }
            case NOTE_PLAY -> {
                patchbukkit.events.NotePlayEvent noteEvent = event.getNotePlay();
                Location location = BridgeUtils.convertLocation(noteEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    noteEvent.getBlockKey()
                );
                Instrument instrument = Instrument.PIANO;
                if (!noteEvent.getInstrument().isEmpty()) {
                    try {
                        instrument = Instrument.valueOf(noteEvent.getInstrument());
                    } catch (IllegalArgumentException ignored) {
                        instrument = Instrument.PIANO;
                    }
                }
                int rawNote = Math.max(0, Math.min(24, noteEvent.getNote()));
                Note note = new Note((byte) rawNote);
                yield new NotePlayEvent(block, instrument, note);
            }
            case SIGN_CHANGE -> {
                patchbukkit.events.SignChangeEvent signEvent = event.getSignChange();
                Player player = getPlayer(signEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(signEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    signEvent.getBlockKey()
                );
                String[] lines = new String[4];
                java.util.List<String> linesList = signEvent.getLinesList();
                for (int i = 0; i < lines.length; i++) {
                    lines[i] = i < linesList.size() ? linesList.get(i) : "";
                }
                org.bukkit.event.Event constructed =
                    createSignChangeEvent(block, player, lines, signEvent.getIsFrontText());
                if (constructed instanceof SignChangeEvent signChange) {
                    yield signChange;
                }
                yield null;
            }
            case TNT_PRIME -> {
                patchbukkit.events.TNTPrimeEvent tntEvent = event.getTntPrime();
                Location location = BridgeUtils.convertLocation(tntEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    tntEvent.getBlockKey()
                );
                TNTPrimeEvent.PrimeCause cause = TNTPrimeEvent.PrimeCause.PLAYER;
                if (!tntEvent.getCause().isEmpty()) {
                    try {
                        cause = TNTPrimeEvent.PrimeCause.valueOf(tntEvent.getCause());
                    } catch (IllegalArgumentException ignored) {
                        cause = TNTPrimeEvent.PrimeCause.PLAYER;
                    }
                }
                Player player = null;
                if (tntEvent.hasPlayerUuid()) {
                    player = getPlayer(tntEvent.getPlayerUuid().getValue());
                }
                org.bukkit.event.Event constructed = createTntPrimeEvent(block, cause, player);
                if (constructed instanceof TNTPrimeEvent tntPrime) {
                    yield tntPrime;
                }
                yield null;
            }
            case MOISTURE_CHANGE -> {
                patchbukkit.events.MoistureChangeEvent moistureEvent = event.getMoistureChange();
                Location location = BridgeUtils.convertLocation(moistureEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    moistureEvent.getBlockKey()
                );
                String newKey = moistureEvent.getNewBlockKey().isEmpty()
                    ? "minecraft:air"
                    : moistureEvent.getNewBlockKey();
                Block newBlock = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    newKey
                );
                yield new MoistureChangeEvent(block, newBlock.getState());
            }
            case SPONGE_ABSORB -> {
                patchbukkit.events.SpongeAbsorbEvent spongeEvent = event.getSpongeAbsorb();
                Location location = BridgeUtils.convertLocation(spongeEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    spongeEvent.getBlockKey()
                );
                java.util.List<org.bukkit.block.BlockState> states = new java.util.ArrayList<>();
                for (patchbukkit.events.SpongeAbsorbBlockEntry entry : spongeEvent.getBlocksList()) {
                    Location entryLoc = BridgeUtils.convertLocation(entry.getLocation());
                    if (entryLoc == null || !(entryLoc.getWorld() instanceof PatchBukkitWorld entryWorld)) {
                        continue;
                    }
                    String key = entry.getBlockKey().isEmpty()
                        ? "minecraft:air"
                        : entry.getBlockKey();
                    Block entryBlock = PatchBukkitBlock.create(
                        entryWorld,
                        entryLoc.getBlockX(),
                        entryLoc.getBlockY(),
                        entryLoc.getBlockZ(),
                        key
                    );
                    states.add(entryBlock.getState());
                }
                yield new SpongeAbsorbEvent(block, states);
            }
            case FLUID_LEVEL_CHANGE -> {
                patchbukkit.events.FluidLevelChangeEvent fluidEvent = event.getFluidLevelChange();
                Location location = BridgeUtils.convertLocation(fluidEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    fluidEvent.getBlockKey()
                );
                String newKey = fluidEvent.getNewBlockKey().isEmpty()
                    ? "minecraft:air"
                    : fluidEvent.getNewBlockKey();
                Block newBlock = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    newKey
                );
                org.bukkit.event.Event created = createFluidLevelChangeEvent(block, newBlock);
                if (created instanceof FluidLevelChangeEvent) {
                    yield created;
                }
                yield null;
            }
            case SPAWN_CHANGE -> {
                patchbukkit.events.SpawnChangeEvent spawnEvent = event.getSpawnChange();
                Location location = BridgeUtils.convertLocation(spawnEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Location previous = BridgeUtils.convertLocation(spawnEvent.getPreviousLocation());
                if (previous == null) {
                    previous = location;
                }
                org.bukkit.event.Event constructed =
                    createSpawnChangeEvent(world, location, previous);
                if (constructed instanceof SpawnChangeEvent spawnChange) {
                    yield spawnChange;
                }
                yield null;
            }
            case BLOCK_CAN_BUILD -> {
                patchbukkit.events.BlockCanBuildEvent canBuildEvent = event.getBlockCanBuild();
                Player player = getPlayer(canBuildEvent.getPlayerUuid().getValue());
                if (player == null) yield null;

                Location location = BridgeUtils.convertLocation(canBuildEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    canBuildEvent.getBlockKey()
                );
                org.bukkit.block.data.BlockData data = block.getBlockData();
                yield createBlockCanBuildEvent(block, data, canBuildEvent.getCanBuild());
            }
            case BLOCK_BURN -> {
                patchbukkit.events.BlockBurnEvent burnEvent = event.getBlockBurn();
                Location location = BridgeUtils.convertLocation(burnEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }

                org.bukkit.block.Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    burnEvent.getBlockKey()
                );
                BlockBurnEvent eventObj = new BlockBurnEvent(block);
                setIgnitingBlock(eventObj, burnEvent.getIgnitingBlockKey());
                yield eventObj;
            }
            case BLOCK_IGNITE -> {
                patchbukkit.events.BlockIgniteEvent igniteEvent = event.getBlockIgnite();
                Player player = null;
                if (igniteEvent.hasPlayerUuid()
                    && igniteEvent.getPlayerUuid() != null
                    && !igniteEvent.getPlayerUuid().getValue().isEmpty()) {
                    player = getPlayer(igniteEvent.getPlayerUuid().getValue());
                }
                Location location = BridgeUtils.convertLocation(igniteEvent.getLocation());
                if (location == null || !(location.getWorld() instanceof PatchBukkitWorld world)) {
                    yield null;
                }
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    igniteEvent.getBlockKey()
                );
                BlockIgniteEvent.IgniteCause cause = BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL;
                if (!igniteEvent.getCause().isEmpty()) {
                    try {
                        cause = BlockIgniteEvent.IgniteCause.valueOf(igniteEvent.getCause());
                    } catch (IllegalArgumentException ignored) {
                        cause = BlockIgniteEvent.IgniteCause.FLINT_AND_STEEL;
                    }
                }
                BlockIgniteEvent eventObj = new BlockIgniteEvent(block, cause, player);
                yield eventObj;
            }
            case BLOCK_SPREAD -> {
                patchbukkit.events.BlockSpreadEvent spreadEvent = event.getBlockSpread();
                Location sourceLocation = BridgeUtils.convertLocation(spreadEvent.getSourceLocation());
                Location location = BridgeUtils.convertLocation(spreadEvent.getLocation());
                if (sourceLocation == null || location == null) yield null;
                if (!(sourceLocation.getWorld() instanceof PatchBukkitWorld sourceWorld)) yield null;
                if (!(location.getWorld() instanceof PatchBukkitWorld world)) yield null;
                Block sourceBlock = PatchBukkitBlock.create(
                    sourceWorld,
                    sourceLocation.getBlockX(),
                    sourceLocation.getBlockY(),
                    sourceLocation.getBlockZ(),
                    spreadEvent.getSourceBlockKey()
                );
                Block block = PatchBukkitBlock.create(
                    world,
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ(),
                    spreadEvent.getBlockKey()
                );
                org.bukkit.block.BlockState newState = block.getState();
                yield new BlockSpreadEvent(block, sourceBlock, newState);
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
                org.bukkit.event.Event created = createEntityDeathEvent(entity);
                if (created instanceof EntityDeathEvent) {
                    yield created;
                }
                yield null;
            }
            case SERVER_COMMAND -> {
                patchbukkit.events.ServerCommandEvent commandEvent = event.getServerCommand();
                yield new ServerCommandEvent(new PatchBukkitConsoleCommandSender(), commandEvent.getCommand());
            }
            case SERVER_BROADCAST -> {
                patchbukkit.events.ServerBroadcastEvent broadcastEvent = event.getServerBroadcast();
                Component messageComponent = GsonComponentSerializer.gson().deserialize(broadcastEvent.getMessage());
                String message = PlainTextComponentSerializer.plainText().serialize(messageComponent);
                org.bukkit.event.Event created = createBroadcastMessageEvent(messageComponent, message);
                if (created instanceof BroadcastMessageEvent) {
                    yield created;
                }
                yield null;
            }
            case SERVER_LIST_PING -> {
                patchbukkit.events.ServerListPingEvent pingEvent = event.getServerListPing();
                String motd = pingEvent.getMotd();
                int maxPlayers = pingEvent.getMaxPlayers();
                int onlinePlayers = pingEvent.getOnlinePlayers();
                String favicon = pingEvent.getFavicon();
                org.bukkit.event.Event constructed =
                    createServerListPingEvent(motd, maxPlayers, onlinePlayers, favicon);
                if (constructed instanceof ServerListPingEvent serverListPing) {
                    yield serverListPing;
                }
                yield null;
            }
            case PLUGIN_ENABLE -> {
                patchbukkit.events.PluginEnableEvent enableEvent = event.getPluginEnable();
                String pluginName = enableEvent.getPluginName();
                org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (plugin == null) yield null;
                yield new PluginEnableEvent(plugin);
            }
            case PLUGIN_DISABLE -> {
                patchbukkit.events.PluginDisableEvent disableEvent = event.getPluginDisable();
                String pluginName = disableEvent.getPluginName();
                org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
                if (plugin == null) yield null;
                yield new PluginDisableEvent(plugin);
            }
            case SERVICE_REGISTER -> {
                patchbukkit.events.ServiceRegisterEvent registerEvent = event.getServiceRegister();
                RegisteredServiceProvider<?> provider = resolveServiceProvider(
                    registerEvent.getServiceName(),
                    registerEvent.getPluginName()
                );
                if (provider == null) yield null;
                yield new ServiceRegisterEvent(provider);
            }
            case SERVICE_UNREGISTER -> {
                patchbukkit.events.ServiceUnregisterEvent unregisterEvent = event.getServiceUnregister();
                RegisteredServiceProvider<?> provider = resolveServiceProvider(
                    unregisterEvent.getServiceName(),
                    unregisterEvent.getPluginName()
                );
                if (provider == null) yield null;
                yield new ServiceUnregisterEvent(provider);
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
            var eventBuilderLocal = patchbukkit.events.PlayerItemMendEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(mendEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setItemKey(itemKey)
                .setItemAmount(itemAmount)
                .setSlot(slot)
                .setRepairAmount(mendEvent.getRepairAmount());
            if (mendEvent.getExperienceOrb() != null) {
                eventBuilderLocal.setOrbUuid(UUID.newBuilder()
                    .setValue(mendEvent.getExperienceOrb().getUniqueId().toString())
                    .build());
            }
            eventBuilder.setPlayerItemMend(eventBuilderLocal.build());
        } else if (event instanceof PlayerLevelChangeEvent levelEvent) {
            eventBuilder.setPlayerLevelChange(
                patchbukkit.events.PlayerLevelChangeEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(levelEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setOldLevel(levelEvent.getOldLevel())
                    .setNewLevel(levelEvent.getNewLevel())
                    .build()
            );
        } else if (event instanceof PlayerKickEvent kickEvent) {
            String reason = GsonComponentSerializer.gson().serialize(kickEvent.reason());
            String leaveMessage = GsonComponentSerializer.gson().serialize(kickEvent.leaveMessage());
            String cause = kickEvent.getCause() != null ? kickEvent.getCause().name() : "UNKNOWN";
            eventBuilder.setPlayerKick(
                patchbukkit.events.PlayerKickEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(kickEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setReason(reason)
                    .setLeaveMessage(leaveMessage)
                    .setCause(cause)
                    .build()
            );
        } else if (event instanceof PlayerToggleSneakEvent toggleEvent) {
            eventBuilder.setPlayerToggleSneak(
                patchbukkit.events.PlayerToggleSneakEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(toggleEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setIsSneaking(toggleEvent.isSneaking())
                    .build()
            );
        } else if (event instanceof PlayerToggleSprintEvent toggleEvent) {
            eventBuilder.setPlayerToggleSprint(
                patchbukkit.events.PlayerToggleSprintEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(toggleEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setIsSprinting(toggleEvent.isSprinting())
                    .build()
            );
        } else if (event instanceof PlayerToggleFlightEvent toggleEvent) {
            eventBuilder.setPlayerToggleFlight(
                patchbukkit.events.PlayerToggleFlightEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(toggleEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setIsFlying(toggleEvent.isFlying())
                    .build()
            );
        } else if (event instanceof PlayerSwapHandItemsEvent swapEvent) {
            ItemStack mainHand = swapEvent.getMainHandItem();
            ItemStack offHand = swapEvent.getOffHandItem();
            String mainKey = mainHand != null ? mainHand.getType().getKey().toString() : "minecraft:air";
            int mainAmount = mainHand != null ? mainHand.getAmount() : 0;
            String offKey = offHand != null ? offHand.getType().getKey().toString() : "minecraft:air";
            int offAmount = offHand != null ? offHand.getAmount() : 0;
            eventBuilder.setPlayerSwapHandItems(
                patchbukkit.events.PlayerSwapHandItemsEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(swapEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setMainHandItemKey(mainKey)
                    .setMainHandItemAmount(mainAmount)
                    .setOffHandItemKey(offKey)
                    .setOffHandItemAmount(offAmount)
                    .build()
            );
        } else if (event instanceof PlayerResourcePackStatusEvent packEvent) {
            java.util.UUID packUuid = resolveResourcePackId(packEvent);
            String status = packEvent.getStatus() != null ? packEvent.getStatus().name() : "FAILED_DOWNLOAD";
            String hash = packEvent.getHash() != null ? packEvent.getHash() : "";
            eventBuilder.setPlayerResourcePackStatus(
                patchbukkit.events.PlayerResourcePackStatusEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(packEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setPackUuid(UUID.newBuilder()
                        .setValue(packUuid.toString())
                        .build())
                    .setStatus(status)
                    .setHash(hash)
                    .build()
            );
        } else if (event instanceof PlayerRespawnEvent respawnEvent) {
            Location location = respawnEvent.getRespawnLocation();
            String reason = respawnEvent.getRespawnReason() != null
                ? respawnEvent.getRespawnReason().name()
                : "DEATH";
            eventBuilder.setPlayerRespawn(
                patchbukkit.events.PlayerRespawnEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(respawnEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setRespawnLocation(BridgeUtils.convertLocation(location))
                    .setIsBedSpawn(respawnEvent.isBedSpawn())
                    .setIsAnchorSpawn(respawnEvent.isAnchorSpawn())
                    .setIsMissingRespawnBlock(respawnEvent.isMissingRespawnBlock())
                    .setRespawnReason(reason)
                    .build()
            );
        } else if (event instanceof PlayerPickupArrowEvent pickupEvent) {
            var item = pickupEvent.getItem();
            String itemKey = "minecraft:air";
            int itemAmount = 0;
            if (item != null && item.getItemStack() != null) {
                itemKey = item.getItemStack().getType().getKey().toString();
                itemAmount = item.getItemStack().getAmount();
            }
            eventBuilder.setPlayerPickupArrow(
                patchbukkit.events.PlayerPickupArrowEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(pickupEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setArrowUuid(UUID.newBuilder()
                        .setValue(pickupEvent.getArrow().getUniqueId().toString())
                        .build())
                    .setItemUuid(UUID.newBuilder()
                        .setValue(item != null ? item.getUniqueId().toString() : java.util.UUID.randomUUID().toString())
                        .build())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .setRemaining(pickupEvent.getRemaining())
                    .build()
            );
        } else if (event instanceof PlayerPortalEvent portalEvent) {
            String cause = portalEvent.getCause() != null ? portalEvent.getCause().name() : "";
            int searchRadius = 0;
            boolean canCreatePortal = false;
            int creationRadius = 0;
            try {
                var method = PlayerPortalEvent.class.getMethod("getSearchRadius");
                Object value = method.invoke(portalEvent);
                if (value instanceof Integer i) {
                    searchRadius = i;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
            try {
                var method = PlayerPortalEvent.class.getMethod("getCanCreatePortal");
                Object value = method.invoke(portalEvent);
                if (value instanceof Boolean b) {
                    canCreatePortal = b;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
            try {
                var method = PlayerPortalEvent.class.getMethod("getCreationRadius");
                Object value = method.invoke(portalEvent);
                if (value instanceof Integer i) {
                    creationRadius = i;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
            eventBuilder.setPlayerPortal(
                patchbukkit.events.PlayerPortalEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(portalEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setFrom(BridgeUtils.convertLocation(portalEvent.getFrom()))
                    .setTo(BridgeUtils.convertLocation(portalEvent.getTo()))
                    .setCause(cause)
                    .setSearchRadius(searchRadius)
                    .setCanCreatePortal(canCreatePortal)
                    .setCreationRadius(creationRadius)
                    .build()
            );
        } else if (event instanceof PlayerRecipeDiscoverEvent recipeEvent) {
            String key = recipeEvent.getRecipe() != null
                ? recipeEvent.getRecipe().toString()
                : "";
            eventBuilder.setPlayerRecipeDiscover(
                patchbukkit.events.PlayerRecipeDiscoverEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(recipeEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setRecipeKey(key)
                    .build()
            );
        } else if (event instanceof PlayerRiptideEvent riptideEvent) {
            ItemStack item = riptideEvent.getItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int itemAmount = item != null ? item.getAmount() : 0;
            Vector velocity = riptideEvent.getVelocity();
            patchbukkit.common.Vec3 vec = patchbukkit.common.Vec3.newBuilder()
                .setX(velocity.getX())
                .setY(velocity.getY())
                .setZ(velocity.getZ())
                .build();
            eventBuilder.setPlayerRiptide(
                patchbukkit.events.PlayerRiptideEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(riptideEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .setVelocity(vec)
                    .build()
            );
        } else if (event instanceof PlayerShearEntityEvent shearEvent) {
            ItemStack item = shearEvent.getItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int itemAmount = item != null ? item.getAmount() : 0;
            String hand = shearEvent.getHand() != null ? shearEvent.getHand().name() : "HAND";
            eventBuilder.setPlayerShearEntity(
                patchbukkit.events.PlayerShearEntityEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(shearEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setEntityUuid(UUID.newBuilder()
                        .setValue(shearEvent.getEntity().getUniqueId().toString())
                        .build())
                    .setEntityType(shearEvent.getEntity().getType().name())
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .setHand(hand)
                    .build()
            );
        } else if (event instanceof PlayerSpawnLocationEvent spawnEvent) {
            eventBuilder.setPlayerSpawnLocation(
                patchbukkit.events.PlayerSpawnLocationEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(spawnEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setSpawnLocation(BridgeUtils.convertLocation(spawnEvent.getSpawnLocation()))
                    .build()
            );
        } else if (event instanceof PlayerStatisticIncrementEvent statEvent) {
            String statistic = statEvent.getStatistic().name();
            String entityType = statEvent.getEntityType() != null ? statEvent.getEntityType().name() : "";
            String materialKey = statEvent.getMaterial() != null ? statEvent.getMaterial().getKey().toString() : "";
            eventBuilder.setPlayerStatisticIncrement(
                patchbukkit.events.PlayerStatisticIncrementEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(statEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setStatistic(statistic)
                    .setInitialValue(statEvent.getPreviousValue())
                    .setNewValue(statEvent.getNewValue())
                    .setEntityType(entityType)
                    .setMaterialKey(materialKey)
                    .build()
            );
        } else if (event instanceof PlayerVelocityEvent velocityEvent) {
            Vector velocity = velocityEvent.getVelocity();
            patchbukkit.common.Vec3 vec = patchbukkit.common.Vec3.newBuilder()
                .setX(velocity.getX())
                .setY(velocity.getY())
                .setZ(velocity.getZ())
                .build();
            eventBuilder.setPlayerVelocity(
                patchbukkit.events.PlayerVelocityEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(velocityEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setVelocity(vec)
                    .build()
            );
        } else if (event instanceof PlayerHarvestBlockEvent harvestEvent) {
            Block harvestedBlock = resolveHarvestedBlock(harvestEvent);
            ItemStack tool = resolveHarvestTool(harvestEvent);
            Location location = harvestedBlock != null ? harvestedBlock.getLocation() : null;
            String blockKey = harvestedBlock != null
                ? harvestedBlock.getType().getKey().toString()
                : "minecraft:air";
            String itemKey = tool != null ? tool.getType().getKey().toString() : "minecraft:air";
            int itemAmount = tool != null ? tool.getAmount() : 0;
            eventBuilder.setPlayerHarvestBlock(
                patchbukkit.events.PlayerHarvestBlockEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(harvestEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBlockLocation(BridgeUtils.convertLocation(location))
                    .setBlockKey(blockKey)
                    .setItemKey(itemKey)
                    .setItemAmount(itemAmount)
                    .build()
            );
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
            var eventBuilderLocal = patchbukkit.events.PlayerCommandSendEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(commandSendEvent.getPlayer().getUniqueId().toString())
                    .build());
            eventBuilderLocal.addAllCommands(commandSendEvent.getCommands());
            eventBuilder.setPlayerCommandSend(eventBuilderLocal.build());
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
            var eventBuilderLocal = patchbukkit.events.PlayerInteractAtEntityEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(interactEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setEntityUuid(UUID.newBuilder()
                    .setValue(entity.getUniqueId().toString())
                    .build())
                .setEntityType(entity.getType().name());
            if (interactEvent.getHand() != null) {
                eventBuilderLocal.setHand(interactEvent.getHand().name());
            }
            if (pos != null) {
                eventBuilderLocal.setClickedPosition(patchbukkit.common.Vec3.newBuilder()
                    .setX(pos.getX())
                    .setY(pos.getY())
                    .setZ(pos.getZ())
                    .build());
            }
            eventBuilder.setPlayerInteractAtEntity(eventBuilderLocal.build());
        } else if (event instanceof PlayerInteractEntityEvent interactEvent) {
            var entity = interactEvent.getRightClicked();
            var eventBuilderLocal = patchbukkit.events.PlayerInteractEntityEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(interactEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setEntityUuid(UUID.newBuilder()
                    .setValue(entity.getUniqueId().toString())
                    .build())
                .setEntityType(entity.getType().name());
            if (interactEvent.getHand() != null) {
                eventBuilderLocal.setHand(interactEvent.getHand().name());
            }
            eventBuilder.setPlayerInteractEntity(eventBuilderLocal.build());
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
        } else if (event instanceof BlockDamageEvent damageEvent) {
            var block = damageEvent.getBlock();
            ItemStack item = damageEvent.getItemInHand();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            eventBuilder.setBlockDamage(
                patchbukkit.events.BlockDamageEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(damageEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setItemKey(itemKey)
                    .setInstaBreak(resolveInstaBreak(damageEvent))
                    .build()
            );
        } else if (event instanceof BlockDamageAbortEvent damageAbortEvent) {
            var block = damageAbortEvent.getBlock();
            ItemStack item = damageAbortEvent.getItemInHand();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            eventBuilder.setBlockDamageAbort(
                patchbukkit.events.BlockDamageAbortEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(damageAbortEvent.getPlayer().getUniqueId().toString())
                        .build())
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setItemKey(itemKey)
                    .build()
            );
        } else if (event instanceof BlockDispenseEvent dispenseEvent) {
            var block = dispenseEvent.getBlock();
            ItemStack item = dispenseEvent.getItem();
            String itemKey = item != null ? item.getType().getKey().toString() : "minecraft:air";
            int amount = item != null ? item.getAmount() : 0;
            Vector velocity = dispenseEvent.getVelocity();
            eventBuilder.setBlockDispense(
                patchbukkit.events.BlockDispenseEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setItemKey(itemKey)
                    .setItemAmount(amount)
                    .setVelocity(patchbukkit.common.Vec3.newBuilder()
                        .setX(velocity.getX())
                        .setY(velocity.getY())
                        .setZ(velocity.getZ())
                        .build())
                    .build()
            );
        } else if (event instanceof BlockDropItemEvent dropItemEvent) {
            var block = dropItemEvent.getBlock();
            var eventBuilderLocal = patchbukkit.events.BlockDropItemEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(dropItemEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()));

            for (org.bukkit.entity.Item item : dropItemEvent.getItems()) {
                ItemStack stack = item.getItemStack();
                if (stack == null) continue;
                eventBuilderLocal.addItems(
                    patchbukkit.events.BlockDropItemEntry.newBuilder()
                        .setItemKey(stack.getType().getKey().toString())
                        .setItemAmount(stack.getAmount())
                        .build()
                );
            }

            eventBuilder.setBlockDropItem(eventBuilderLocal.build());
        } else if (event instanceof BlockExplodeEvent explodeEvent) {
            var block = explodeEvent.getBlock();
            var eventBuilderLocal = patchbukkit.events.BlockExplodeEvent.newBuilder()
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                .setYield(explodeEvent.getYield());
            for (Block b : explodeEvent.blockList()) {
                eventBuilderLocal.addBlocks(
                    patchbukkit.events.BlockExplodeBlockEntry.newBuilder()
                        .setBlockKey(b.getType().getKey().toString())
                        .setLocation(BridgeUtils.convertLocation(b.getLocation()))
                        .build()
                );
            }
            eventBuilder.setBlockExplode(eventBuilderLocal.build());
        } else if (event instanceof BlockFadeEvent fadeEvent) {
            var block = fadeEvent.getBlock();
            var newState = fadeEvent.getNewState();
            eventBuilder.setBlockFade(
                patchbukkit.events.BlockFadeEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setNewBlockKey(newState.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .build()
            );
        } else if (event instanceof BlockFertilizeEvent fertilizeEvent) {
            var block = fertilizeEvent.getBlock();
            var eventBuilderLocal = patchbukkit.events.BlockFertilizeEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(fertilizeEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()));
            for (org.bukkit.block.BlockState state : fertilizeEvent.getBlocks()) {
                eventBuilderLocal.addBlocks(
                    patchbukkit.events.BlockFertilizeBlockEntry.newBuilder()
                        .setBlockKey(state.getType().getKey().toString())
                        .setLocation(BridgeUtils.convertLocation(state.getLocation()))
                        .build()
                );
            }
            eventBuilder.setBlockFertilize(eventBuilderLocal.build());
        } else if (event instanceof BlockFormEvent formEvent) {
            var block = formEvent.getBlock();
            var newState = formEvent.getNewState();
            eventBuilder.setBlockForm(
                patchbukkit.events.BlockFormEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setNewBlockKey(newState.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .build()
            );
        } else if (event instanceof BlockFromToEvent fromToEvent) {
            var block = fromToEvent.getBlock();
            var toBlock = fromToEvent.getToBlock();
            String face = fromToEvent.getFace() != null ? fromToEvent.getFace().name() : "";
            eventBuilder.setBlockFromTo(
                patchbukkit.events.BlockFromToEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setToBlockKey(toBlock.getType().getKey().toString())
                    .setToLocation(BridgeUtils.convertLocation(toBlock.getLocation()))
                    .setFace(face)
                    .build()
            );
        } else if (event instanceof BlockGrowEvent growEvent) {
            var block = growEvent.getBlock();
            var newState = growEvent.getNewState();
            eventBuilder.setBlockGrow(
                patchbukkit.events.BlockGrowEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setNewBlockKey(newState.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .build()
            );
        } else if (event instanceof BlockPistonExtendEvent extendEvent) {
            var block = extendEvent.getBlock();
            String direction = extendEvent.getDirection() != null ? extendEvent.getDirection().name() : "";
            var eventBuilderLocal = patchbukkit.events.BlockPistonExtendEvent.newBuilder()
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                .setDirection(direction)
                .setLength(resolvePistonLength(extendEvent));
            for (Block b : extendEvent.getBlocks()) {
                eventBuilderLocal.addBlocks(
                    patchbukkit.events.BlockPistonBlockEntry.newBuilder()
                        .setBlockKey(b.getType().getKey().toString())
                        .setLocation(BridgeUtils.convertLocation(b.getLocation()))
                        .build()
                );
            }
            eventBuilder.setBlockPistonExtend(eventBuilderLocal.build());
        } else if (event instanceof BlockPistonRetractEvent retractEvent) {
            var block = retractEvent.getBlock();
            String direction = retractEvent.getDirection() != null ? retractEvent.getDirection().name() : "";
            var eventBuilderLocal = patchbukkit.events.BlockPistonRetractEvent.newBuilder()
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                .setDirection(direction)
                .setLength(resolvePistonLength(retractEvent));
            for (Block b : retractEvent.getBlocks()) {
                eventBuilderLocal.addBlocks(
                    patchbukkit.events.BlockPistonBlockEntry.newBuilder()
                        .setBlockKey(b.getType().getKey().toString())
                        .setLocation(BridgeUtils.convertLocation(b.getLocation()))
                        .build()
                );
            }
            eventBuilder.setBlockPistonRetract(eventBuilderLocal.build());
        } else if (event instanceof BlockRedstoneEvent redstoneEvent) {
            var block = redstoneEvent.getBlock();
            eventBuilder.setBlockRedstone(
                patchbukkit.events.BlockRedstoneEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setOldCurrent(redstoneEvent.getOldCurrent())
                    .setNewCurrent(redstoneEvent.getNewCurrent())
                    .build()
            );
        } else if (event instanceof BlockMultiPlaceEvent multiEvent) {
            var block = multiEvent.getBlock();
            var eventBuilderLocal = patchbukkit.events.BlockMultiPlaceEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(multiEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()));
            for (org.bukkit.block.BlockState state : resolveMultiPlaceStates(multiEvent)) {
                eventBuilderLocal.addBlocks(
                    patchbukkit.events.BlockMultiPlaceBlockEntry.newBuilder()
                        .setBlockKey(state.getType().getKey().toString())
                        .setLocation(BridgeUtils.convertLocation(state.getLocation()))
                        .build()
                );
            }
            eventBuilder.setBlockMultiPlace(eventBuilderLocal.build());
        } else if (event instanceof BlockPhysicsEvent physicsEvent) {
            var block = physicsEvent.getBlock();
            Material sourceMaterial = resolvePhysicsChangedMaterial(physicsEvent);
            eventBuilder.setBlockPhysics(
                patchbukkit.events.BlockPhysicsEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setSourceBlockKey(sourceMaterial.getKey().toString())
                    .setSourceLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .build()
            );
        } else if (event instanceof NotePlayEvent noteEvent) {
            Block block = noteEvent.getBlock();
            eventBuilder.setNotePlay(
                patchbukkit.events.NotePlayEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setInstrument(noteEvent.getInstrument().name())
                    .setNote(noteEvent.getNote().getId())
                    .build()
            );
        } else if (event instanceof SignChangeEvent signEvent) {
            Block block = signEvent.getBlock();
            String[] lines = signEvent.getLines();
            var eventBuilderLocal = patchbukkit.events.SignChangeEvent.newBuilder()
                .setPlayerUuid(UUID.newBuilder()
                    .setValue(signEvent.getPlayer().getUniqueId().toString())
                    .build())
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()));
            for (String line : lines) {
                eventBuilderLocal.addLines(line != null ? line : "");
            }
            try {
                java.lang.reflect.Method method = signEvent.getClass().getMethod("isFrontText");
                Object value = method.invoke(signEvent);
                if (value instanceof Boolean b) {
                    eventBuilderLocal.setIsFrontText(b);
                }
            } catch (ReflectiveOperationException ignored) {
                eventBuilderLocal.setIsFrontText(true);
            }
            eventBuilder.setSignChange(eventBuilderLocal.build());
        } else if (event instanceof TNTPrimeEvent tntEvent) {
            Block block = tntEvent.getBlock();
            var eventBuilderLocal = patchbukkit.events.TNTPrimeEvent.newBuilder()
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                .setCause(tntEvent.getCause().name());
            if (tntEvent.getPrimingEntity() instanceof Player player) {
                eventBuilderLocal.setPlayerUuid(UUID.newBuilder()
                    .setValue(player.getUniqueId().toString())
                    .build());
            }
            eventBuilder.setTntPrime(eventBuilderLocal.build());
        } else if (event instanceof MoistureChangeEvent moistureEvent) {
            Block block = moistureEvent.getBlock();
            org.bukkit.block.BlockState newState = moistureEvent.getNewState();
            eventBuilder.setMoistureChange(
                patchbukkit.events.MoistureChangeEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setNewBlockKey(newState.getType().getKey().toString())
                    .build()
            );
        } else if (event instanceof SpongeAbsorbEvent spongeEvent) {
            Block block = spongeEvent.getBlock();
            var eventBuilderLocal = patchbukkit.events.SpongeAbsorbEvent.newBuilder()
                .setBlockKey(block.getType().getKey().toString())
                .setLocation(BridgeUtils.convertLocation(block.getLocation()));
            for (org.bukkit.block.BlockState state : spongeEvent.getBlocks()) {
                eventBuilderLocal.addBlocks(
                    patchbukkit.events.SpongeAbsorbBlockEntry.newBuilder()
                        .setBlockKey(state.getType().getKey().toString())
                        .setLocation(BridgeUtils.convertLocation(state.getLocation()))
                        .build()
                );
            }
            eventBuilder.setSpongeAbsorb(eventBuilderLocal.build());
        } else if (event instanceof FluidLevelChangeEvent fluidEvent) {
            Block block = fluidEvent.getBlock();
            String newBlockKey = resolveFluidNewBlockKey(fluidEvent);
            eventBuilder.setFluidLevelChange(
                patchbukkit.events.FluidLevelChangeEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setNewBlockKey(newBlockKey)
                    .build()
            );
        } else if (event instanceof SpawnChangeEvent spawnEvent) {
            Location location = resolveSpawnChangeLocation(spawnEvent);
            Location previous = resolveSpawnChangePreviousLocation(spawnEvent, location);
            if (previous == null) {
                previous = location;
            }
            eventBuilder.setSpawnChange(
                patchbukkit.events.SpawnChangeEvent.newBuilder()
                    .setPreviousLocation(BridgeUtils.convertLocation(previous))
                    .setLocation(BridgeUtils.convertLocation(location))
                    .build()
            );
        } else if (event instanceof BlockCanBuildEvent canBuildEvent) {
            Block block = canBuildEvent.getBlock();
            boolean canBuild = canBuildEvent.isBuildable();
            java.util.UUID playerUuid = resolveEventPlayerUuid(canBuildEvent);
            eventBuilder.setBlockCanBuild(
                patchbukkit.events.BlockCanBuildEvent.newBuilder()
                    .setPlayerUuid(UUID.newBuilder()
                        .setValue(playerUuid.toString())
                        .build())
                    .setBlockKey(block.getType().getKey().toString())
                    .setBlockAgainstKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .setCanBuild(canBuild)
                    .build()
            );
        } else if (event instanceof BlockBurnEvent burnEvent) {
            Block block = burnEvent.getBlock();
            Block ignitingBlock = resolveIgnitingBlock(burnEvent);
            String ignitingKey = ignitingBlock != null
                ? ignitingBlock.getType().getKey().toString()
                : "minecraft:fire";
            eventBuilder.setBlockBurn(
                patchbukkit.events.BlockBurnEvent.newBuilder()
                    .setBlockKey(block.getType().getKey().toString())
                    .setIgnitingBlockKey(ignitingKey)
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                    .build()
            );
        } else if (event instanceof BlockIgniteEvent igniteEvent) {
            Block block = igniteEvent.getBlock();
            String cause = igniteEvent.getCause() != null ? igniteEvent.getCause().name() : "";
            Block ignitingBlock = resolveIgnitingBlock(igniteEvent);
            String ignitingKey = ignitingBlock != null
                ? ignitingBlock.getType().getKey().toString()
                : "minecraft:fire";
            var igniteBuilder = patchbukkit.events.BlockIgniteEvent.newBuilder()
                .setBlockKey(block.getType().getKey().toString())
                .setIgnitingBlockKey(ignitingKey)
                .setLocation(BridgeUtils.convertLocation(block.getLocation()))
                .setCause(cause);
            if (igniteEvent.getPlayer() != null) {
                igniteBuilder.setPlayerUuid(
                    UUID.newBuilder()
                        .setValue(igniteEvent.getPlayer().getUniqueId().toString())
                        .build()
                );
            }
            eventBuilder.setBlockIgnite(
                igniteBuilder.build()
            );
        } else if (event instanceof BlockSpreadEvent spreadEvent) {
            Block block = spreadEvent.getBlock();
            Block sourceBlock = spreadEvent.getSource();
            eventBuilder.setBlockSpread(
                patchbukkit.events.BlockSpreadEvent.newBuilder()
                    .setSourceBlockKey(sourceBlock.getType().getKey().toString())
                    .setSourceLocation(BridgeUtils.convertLocation(sourceBlock.getLocation()))
                    .setBlockKey(block.getType().getKey().toString())
                    .setLocation(BridgeUtils.convertLocation(block.getLocation()))
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
        } else if (event instanceof ServerListPingEvent pingEvent) {
            String motd = pingEvent.getMotd();
            int maxPlayers = pingEvent.getMaxPlayers();
            int onlinePlayers = 0;
            try {
                java.lang.reflect.Method method = pingEvent.getClass().getMethod("getNumPlayers");
                Object value = method.invoke(pingEvent);
                if (value instanceof Integer count) {
                    onlinePlayers = count;
                }
            } catch (ReflectiveOperationException ignored) {
                onlinePlayers = Bukkit.getOnlinePlayers().size();
            }
            String favicon = "";
            try {
                java.lang.reflect.Method method = pingEvent.getClass().getMethod("getServerIcon");
                Object icon = method.invoke(pingEvent);
                if (icon != null) {
                    java.lang.reflect.Method dataMethod = icon.getClass().getMethod("getData");
                    Object data = dataMethod.invoke(icon);
                    if (data instanceof String dataString) {
                        favicon = dataString;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }

            eventBuilder.setServerListPing(
                patchbukkit.events.ServerListPingEvent.newBuilder()
                    .setMotd(motd)
                    .setMaxPlayers(maxPlayers)
                    .setOnlinePlayers(onlinePlayers)
                    .setFavicon(favicon)
                    .build()
            );
        } else if (event instanceof PluginEnableEvent enableEvent) {
            eventBuilder.setPluginEnable(
                patchbukkit.events.PluginEnableEvent.newBuilder()
                    .setPluginName(enableEvent.getPlugin().getName())
                    .build()
            );
        } else if (event instanceof PluginDisableEvent disableEvent) {
            eventBuilder.setPluginDisable(
                patchbukkit.events.PluginDisableEvent.newBuilder()
                    .setPluginName(disableEvent.getPlugin().getName())
                    .build()
            );
        } else if (event instanceof ServiceRegisterEvent registerEvent) {
            RegisteredServiceProvider<?> provider = registerEvent.getProvider();
            String pluginName = provider != null && provider.getPlugin() != null
                ? provider.getPlugin().getName()
                : "";
            String serviceName = provider != null && provider.getService() != null
                ? provider.getService().getName()
                : "";
            eventBuilder.setServiceRegister(
                patchbukkit.events.ServiceRegisterEvent.newBuilder()
                    .setPluginName(pluginName)
                    .setServiceName(serviceName)
                    .build()
            );
        } else if (event instanceof ServiceUnregisterEvent unregisterEvent) {
            RegisteredServiceProvider<?> provider = unregisterEvent.getProvider();
            String pluginName = provider != null && provider.getPlugin() != null
                ? provider.getPlugin().getName()
                : "";
            String serviceName = provider != null && provider.getService() != null
                ? provider.getService().getName()
                : "";
            eventBuilder.setServiceUnregister(
                patchbukkit.events.ServiceUnregisterEvent.newBuilder()
                    .setPluginName(pluginName)
                    .setServiceName(serviceName)
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

    @NotNull
    static java.util.UUID resolveResourcePackId(@NotNull PlayerResourcePackStatusEvent event) {
        java.util.UUID packUuid = null;
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getPackId");
            Object value = method.invoke(event);
            if (value instanceof java.util.UUID uuid) {
                packUuid = uuid;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fallback to alternative method names
        }
        if (packUuid == null) {
            try {
                java.lang.reflect.Method method = event.getClass().getMethod("getID");
                Object value = method.invoke(event);
                if (value instanceof java.util.UUID uuid) {
                    packUuid = uuid;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
        }
        if (packUuid == null) {
            try {
                java.lang.reflect.Method method = event.getClass().getMethod("getId");
                Object value = method.invoke(event);
                if (value instanceof java.util.UUID uuid) {
                    packUuid = uuid;
                }
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
        }
        return packUuid != null ? packUuid : java.util.UUID.randomUUID();
    }

    @Nullable
    private static Block resolveHarvestedBlock(@NotNull PlayerHarvestBlockEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getHarvestedBlock");
            Object value = method.invoke(event);
            if (value instanceof Block block) {
                return block;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getBlock");
            Object value = method.invoke(event);
            if (value instanceof Block block) {
                return block;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static ItemStack resolveHarvestTool(@NotNull PlayerHarvestBlockEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getHarvestingTool");
            Object value = method.invoke(event);
            if (value instanceof ItemStack stack) {
                return stack;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getTool");
            Object value = method.invoke(event);
            if (value instanceof ItemStack stack) {
                return stack;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @NotNull
    static java.util.UUID resolveEventPlayerUuid(@NotNull org.bukkit.event.Event event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getPlayer");
            Object value = method.invoke(event);
            if (value instanceof Player player) {
                return player.getUniqueId();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getPlayerUUID");
            Object value = method.invoke(event);
            if (value instanceof java.util.UUID uuid) {
                return uuid;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return java.util.UUID.randomUUID();
    }

    @Nullable
    static Block resolveIgnitingBlock(@NotNull BlockBurnEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getIgnitingBlock");
            Object value = method.invoke(event);
            if (value instanceof Block block) {
                return block;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    static Block resolveIgnitingBlock(@NotNull BlockIgniteEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getIgnitingBlock");
            Object value = method.invoke(event);
            if (value instanceof Block block) {
                return block;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    private static boolean resolveInstaBreak(@NotNull BlockDamageEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("isInstaBreak");
            Object value = method.invoke(event);
            if (value instanceof Boolean bool) {
                return bool;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return false;
    }

    private static int resolvePistonLength(@NotNull Object pistonEvent) {
        try {
            java.lang.reflect.Method method = pistonEvent.getClass().getMethod("getLength");
            Object value = method.invoke(pistonEvent);
            if (value instanceof Integer len) {
                return len;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = pistonEvent.getClass().getMethod("getBlocks");
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
    private static java.util.List<org.bukkit.block.BlockState> resolveMultiPlaceStates(@NotNull BlockMultiPlaceEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getBlockStates");
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
            java.lang.reflect.Method method = event.getClass().getMethod("getReplacedBlockStates");
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
    private static Material resolvePhysicsChangedMaterial(@NotNull BlockPhysicsEvent event) {
        try {
            Object changed = event.getChangedType();
            if (changed instanceof Material material) {
                return material;
            }
            if (changed != null) {
                java.lang.reflect.Method method = changed.getClass().getMethod("getMaterial");
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
    private static String resolveFluidNewBlockKey(@NotNull FluidLevelChangeEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getNewState");
            Object value = method.invoke(event);
            if (value instanceof org.bukkit.block.BlockState state) {
                return state.getType().getKey().toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getNewData");
            Object value = method.invoke(event);
            if (value instanceof org.bukkit.block.data.BlockData data) {
                return data.getMaterial().getKey().toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getNewBlockData");
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
    private static Location resolveSpawnChangeLocation(@NotNull SpawnChangeEvent event) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getLocation");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getNewLocation");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getNewSpawn");
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
        @NotNull SpawnChangeEvent event,
        @Nullable Location fallback
    ) {
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getPreviousLocation");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("getPreviousSpawn");
            Object value = method.invoke(event);
            if (value instanceof Location location) {
                return location;
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return fallback;
    }

    @Nullable
    private static org.bukkit.event.Event createHarvestBlockEvent(
        @NotNull Player player,
        @NotNull Block block,
        @NotNull ItemStack tool
    ) {
        try {
            java.lang.reflect.Constructor<?> ctor = PlayerHarvestBlockEvent.class.getConstructor(
                Player.class,
                Block.class,
                ItemStack.class,
                java.util.List.class
            );
            return (org.bukkit.event.Event) ctor.newInstance(
                player,
                block,
                tool,
                java.util.Collections.emptyList()
            );
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<?> ctor = PlayerHarvestBlockEvent.class.getConstructor(
                Player.class,
                Block.class,
                ItemStack.class
            );
            return (org.bukkit.event.Event) ctor.newInstance(player, block, tool);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static BlockCanBuildEvent createBlockCanBuildEvent(
        @NotNull Block block,
        @NotNull org.bukkit.block.data.BlockData data,
        boolean canBuild
    ) {
        try {
            java.lang.reflect.Constructor<BlockCanBuildEvent> ctor =
                BlockCanBuildEvent.class.getConstructor(Block.class, org.bukkit.block.data.BlockData.class, boolean.class);
            return ctor.newInstance(block, data, canBuild);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockCanBuildEvent> ctor =
                BlockCanBuildEvent.class.getConstructor(Block.class, Material.class, boolean.class);
            return ctor.newInstance(block, block.getType(), canBuild);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockCanBuildEvent> ctor =
                BlockCanBuildEvent.class.getConstructor(Block.class, boolean.class);
            return ctor.newInstance(block, canBuild);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createSignChangeEvent(
        @NotNull Block block,
        @NotNull Player player,
        @NotNull String[] lines,
        boolean isFrontText
    ) {
        try {
            java.lang.reflect.Constructor<SignChangeEvent> ctor =
                SignChangeEvent.class.getConstructor(Block.class, Player.class, String[].class);
            return ctor.newInstance(block, player, lines);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<SignChangeEvent> ctor =
                SignChangeEvent.class.getConstructor(Block.class, Player.class, String[].class, boolean.class);
            return ctor.newInstance(block, player, lines, isFrontText);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Class<?> sideClass = Class.forName("org.bukkit.block.sign.SignSide");
            @SuppressWarnings("unchecked")
            Object side = Enum.valueOf((Class<Enum>) sideClass, isFrontText ? "FRONT" : "BACK");
            java.lang.reflect.Constructor<?> ctor =
                SignChangeEvent.class.getConstructor(Block.class, Player.class, String[].class, sideClass);
            return (org.bukkit.event.Event) ctor.newInstance(block, player, lines, side);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Class<?> sideClass = Class.forName("org.bukkit.block.sign.Side");
            @SuppressWarnings("unchecked")
            Object side = Enum.valueOf((Class<Enum>) sideClass, isFrontText ? "FRONT" : "BACK");
            java.lang.reflect.Constructor<?> ctor =
                SignChangeEvent.class.getConstructor(Block.class, Player.class, String[].class, sideClass);
            return (org.bukkit.event.Event) ctor.newInstance(block, player, lines, side);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createTntPrimeEvent(
        @NotNull Block block,
        @NotNull TNTPrimeEvent.PrimeCause cause,
        @Nullable Entity primer
    ) {
        try {
            java.lang.reflect.Constructor<TNTPrimeEvent> ctor =
                TNTPrimeEvent.class.getConstructor(Block.class, TNTPrimeEvent.PrimeCause.class, Entity.class);
            return ctor.newInstance(block, cause, primer);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<TNTPrimeEvent> ctor =
                TNTPrimeEvent.class.getConstructor(Block.class, TNTPrimeEvent.PrimeCause.class);
            return ctor.newInstance(block, cause);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<TNTPrimeEvent> ctor =
                TNTPrimeEvent.class.getConstructor(Block.class, TNTPrimeEvent.PrimeCause.class, Entity.class, Block.class);
            return ctor.newInstance(block, cause, primer, block);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createSpawnChangeEvent(
        @NotNull org.bukkit.World world,
        @NotNull Location location,
        @NotNull Location previous
    ) {
        try {
            java.lang.reflect.Constructor<SpawnChangeEvent> ctor =
                SpawnChangeEvent.class.getConstructor(org.bukkit.World.class, Location.class, Location.class);
            return ctor.newInstance(world, location, previous);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<SpawnChangeEvent> ctor =
                SpawnChangeEvent.class.getConstructor(org.bukkit.World.class, Location.class);
            return ctor.newInstance(world, location);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createServerListPingEvent(
        @NotNull String motd,
        int maxPlayers,
        int onlinePlayers,
        @NotNull String favicon
    ) {
        java.net.InetAddress address = java.net.InetAddress.getLoopbackAddress();
        try {
            java.lang.reflect.Constructor<ServerListPingEvent> ctor =
                ServerListPingEvent.class.getConstructor(java.net.InetAddress.class, String.class, int.class, int.class);
            ServerListPingEvent event = ctor.newInstance(address, motd, maxPlayers, onlinePlayers);
            return event;
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<ServerListPingEvent> ctor =
                ServerListPingEvent.class.getConstructor(java.net.InetAddress.class, String.class, int.class);
            ServerListPingEvent event = ctor.newInstance(address, motd, maxPlayers);
            try {
                java.lang.reflect.Method method = event.getClass().getMethod("setNumPlayers", int.class);
                method.invoke(event, onlinePlayers);
            } catch (ReflectiveOperationException ignored) {
                // ignore
            }
            return event;
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createPlayerBedEnterEvent(
        @NotNull Player player,
        @NotNull Block bed
    ) {
        try {
            java.lang.reflect.Constructor<PlayerBedEnterEvent> ctor =
                PlayerBedEnterEvent.class.getConstructor(Player.class, Block.class);
            return ctor.newInstance(player, bed);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Class<?> resultClass = Class.forName("org.bukkit.event.player.PlayerBedEnterEvent$BedEnterResult");
            Class<?> actionClass = Class.forName("org.bukkit.event.player.PlayerBedEnterEvent$BedEnterAction");
            Object result = firstEnumConstant(resultClass, "OK");
            Object action = firstEnumConstant(actionClass, "RIGHT_CLICK");
            java.lang.reflect.Constructor<PlayerBedEnterEvent> ctor =
                PlayerBedEnterEvent.class.getConstructor(Player.class, Block.class, resultClass, actionClass);
            return ctor.newInstance(player, bed, result, action);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createPlayerBedLeaveEvent(
        @NotNull Player player,
        @NotNull Block bed
    ) {
        try {
            java.lang.reflect.Constructor<PlayerBedLeaveEvent> ctor =
                PlayerBedLeaveEvent.class.getConstructor(Player.class, Block.class, boolean.class);
            return ctor.newInstance(player, bed, false);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<PlayerBedLeaveEvent> ctor =
                PlayerBedLeaveEvent.class.getConstructor(Player.class, Block.class);
            return ctor.newInstance(player, bed);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createPlayerRecipeDiscoverEvent(
        @NotNull Player player,
        @NotNull NamespacedKey key
    ) {
        try {
            java.lang.reflect.Constructor<PlayerRecipeDiscoverEvent> ctor =
                PlayerRecipeDiscoverEvent.class.getConstructor(Player.class, NamespacedKey.class, boolean.class);
            return ctor.newInstance(player, key, false);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<PlayerRecipeDiscoverEvent> ctor =
                PlayerRecipeDiscoverEvent.class.getConstructor(Player.class, NamespacedKey.class);
            return ctor.newInstance(player, key);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createPlayerShearEntityEvent(
        @NotNull Player player,
        @NotNull Entity entity,
        @NotNull ItemStack item,
        @NotNull EquipmentSlot slot
    ) {
        try {
            java.lang.reflect.Constructor<PlayerShearEntityEvent> ctor =
                PlayerShearEntityEvent.class.getConstructor(
                    Player.class,
                    Entity.class,
                    ItemStack.class,
                    EquipmentSlot.class,
                    java.util.List.class
                );
            return ctor.newInstance(player, entity, item, slot, java.util.Collections.emptyList());
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<PlayerShearEntityEvent> ctor =
                PlayerShearEntityEvent.class.getConstructor(Player.class, Entity.class, ItemStack.class, EquipmentSlot.class);
            return ctor.newInstance(player, entity, item, slot);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<PlayerShearEntityEvent> ctor =
                PlayerShearEntityEvent.class.getConstructor(Player.class, Entity.class);
            return ctor.newInstance(player, entity);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBlockDamageEvent(
        @NotNull Block block,
        @NotNull Player player,
        @Nullable ItemStack item,
        boolean instaBreak
    ) {
        try {
            java.lang.reflect.Constructor<BlockDamageEvent> ctor =
                BlockDamageEvent.class.getConstructor(Player.class, Block.class, ItemStack.class, boolean.class);
            return ctor.newInstance(player, block, item, instaBreak);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockDamageEvent> ctor =
                BlockDamageEvent.class.getConstructor(Block.class, Player.class, ItemStack.class, boolean.class);
            return ctor.newInstance(block, player, item, instaBreak);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockDamageEvent> ctor =
                BlockDamageEvent.class.getConstructor(Block.class, Player.class, ItemStack.class);
            return ctor.newInstance(block, player, item);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBlockDamageAbortEvent(
        @NotNull Block block,
        @NotNull Player player,
        @Nullable ItemStack item
    ) {
        try {
            java.lang.reflect.Constructor<BlockDamageAbortEvent> ctor =
                BlockDamageAbortEvent.class.getConstructor(Player.class, Block.class, ItemStack.class);
            return ctor.newInstance(player, block, item);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockDamageAbortEvent> ctor =
                BlockDamageAbortEvent.class.getConstructor(Block.class, Player.class, ItemStack.class);
            return ctor.newInstance(block, player, item);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBlockDropItemEvent(
        @NotNull Block block,
        @NotNull Player player,
        @NotNull java.util.List<org.bukkit.entity.Item> items
    ) {
        try {
            java.lang.reflect.Constructor<BlockDropItemEvent> ctor =
                BlockDropItemEvent.class.getConstructor(Block.class, org.bukkit.block.BlockState.class, Player.class, java.util.List.class);
            return ctor.newInstance(block, block.getState(), player, items);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockDropItemEvent> ctor =
                BlockDropItemEvent.class.getConstructor(Block.class, Player.class, java.util.List.class);
            return ctor.newInstance(block, player, items);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBlockExplodeEvent(
        @NotNull Block block,
        @NotNull java.util.List<Block> blocks,
        float yield
    ) {
        try {
            java.lang.reflect.Constructor<BlockExplodeEvent> ctor =
                BlockExplodeEvent.class.getConstructor(Block.class, org.bukkit.block.BlockState.class, java.util.List.class, float.class);
            return ctor.newInstance(block, block.getState(), blocks, yield);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Class<?> resultClass = Class.forName("org.bukkit.event.block.BlockExplodeEvent$ExplosionResult");
            Object result = firstEnumConstant(resultClass, "DESTROY");
            java.lang.reflect.Constructor<BlockExplodeEvent> ctor =
                BlockExplodeEvent.class.getConstructor(Block.class, org.bukkit.block.BlockState.class, java.util.List.class, float.class, resultClass);
            return ctor.newInstance(block, block.getState(), blocks, yield, result);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockExplodeEvent> ctor =
                BlockExplodeEvent.class.getConstructor(Block.class, java.util.List.class, float.class);
            return ctor.newInstance(block, blocks, yield);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBlockPistonExtendEvent(
        @NotNull Block block,
        @NotNull java.util.List<Block> blocks,
        int length,
        @NotNull BlockFace face
    ) {
        try {
            java.lang.reflect.Constructor<BlockPistonExtendEvent> ctor =
                BlockPistonExtendEvent.class.getConstructor(Block.class, java.util.List.class, BlockFace.class);
            return ctor.newInstance(block, blocks, face);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockPistonExtendEvent> ctor =
                BlockPistonExtendEvent.class.getConstructor(Block.class, int.class, BlockFace.class);
            return ctor.newInstance(block, length, face);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockPistonExtendEvent> ctor =
                BlockPistonExtendEvent.class.getConstructor(Block.class, java.util.List.class, int.class);
            return ctor.newInstance(block, blocks, length);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBlockPistonRetractEvent(
        @NotNull Block block,
        @NotNull java.util.List<Block> blocks,
        int length,
        @NotNull BlockFace face
    ) {
        try {
            java.lang.reflect.Constructor<BlockPistonRetractEvent> ctor =
                BlockPistonRetractEvent.class.getConstructor(Block.class, java.util.List.class, BlockFace.class);
            return ctor.newInstance(block, blocks, face);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockPistonRetractEvent> ctor =
                BlockPistonRetractEvent.class.getConstructor(Block.class, int.class, BlockFace.class);
            return ctor.newInstance(block, length, face);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BlockPistonRetractEvent> ctor =
                BlockPistonRetractEvent.class.getConstructor(Block.class, java.util.List.class, int.class);
            return ctor.newInstance(block, blocks, length);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createFluidLevelChangeEvent(
        @NotNull Block block,
        @NotNull Block newBlock
    ) {
        try {
            java.lang.reflect.Constructor<FluidLevelChangeEvent> ctor =
                FluidLevelChangeEvent.class.getConstructor(Block.class, org.bukkit.block.data.BlockData.class);
            return ctor.newInstance(block, newBlock.getBlockData());
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<FluidLevelChangeEvent> ctor =
                FluidLevelChangeEvent.class.getConstructor(Block.class, org.bukkit.block.BlockState.class);
            return ctor.newInstance(block, newBlock.getState());
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createEntityDeathEvent(@NotNull org.bukkit.entity.LivingEntity entity) {
        java.util.List<ItemStack> drops = java.util.Collections.emptyList();
        int exp = 0;
        try {
            java.lang.reflect.Constructor<EntityDeathEvent> ctor =
                EntityDeathEvent.class.getConstructor(org.bukkit.entity.LivingEntity.class, java.util.List.class, int.class);
            return ctor.newInstance(entity, drops, exp);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            Class<?> damageSourceClass = Class.forName("org.bukkit.damage.DamageSource");
            java.lang.reflect.Constructor<EntityDeathEvent> ctor =
                EntityDeathEvent.class.getConstructor(
                    org.bukkit.entity.LivingEntity.class,
                    java.util.List.class,
                    int.class,
                    damageSourceClass
                );
            return ctor.newInstance(entity, drops, exp, null);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static org.bukkit.event.Event createBroadcastMessageEvent(
        @NotNull Component messageComponent,
        @NotNull String message
    ) {
        java.util.Set<org.bukkit.command.CommandSender> recipients = java.util.Collections.emptySet();
        try {
            java.lang.reflect.Constructor<BroadcastMessageEvent> ctor =
                BroadcastMessageEvent.class.getConstructor(String.class, java.util.Set.class);
            return ctor.newInstance(message, recipients);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BroadcastMessageEvent> ctor =
                BroadcastMessageEvent.class.getConstructor(boolean.class, String.class, java.util.Set.class);
            return ctor.newInstance(false, message, recipients);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BroadcastMessageEvent> ctor =
                BroadcastMessageEvent.class.getConstructor(Component.class, java.util.Set.class);
            return ctor.newInstance(messageComponent, recipients);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        try {
            java.lang.reflect.Constructor<BroadcastMessageEvent> ctor =
                BroadcastMessageEvent.class.getConstructor(boolean.class, Component.class, java.util.Set.class);
            return ctor.newInstance(false, messageComponent, recipients);
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static Object firstEnumConstant(@NotNull Class<?> enumClass, @Nullable String preferredName) {
        if (!enumClass.isEnum()) {
            return null;
        }
        Object[] values = enumClass.getEnumConstants();
        if (values == null || values.length == 0) {
            return null;
        }
        if (preferredName != null) {
            try {
                @SuppressWarnings("unchecked")
                Object value = Enum.valueOf((Class<Enum>) enumClass, preferredName);
                return value;
            } catch (IllegalArgumentException ignored) {
                // ignore
            }
        }
        return values[0];
    }

    @Nullable
    private static RegisteredServiceProvider<?> resolveServiceProvider(
        @NotNull String serviceName,
        @NotNull String pluginName
    ) {
        Class<?> serviceClass = resolveServiceClass(serviceName);
        if (serviceClass == null) {
            return null;
        }

        RegisteredServiceProvider<?> provider = Bukkit.getServicesManager().getRegistration(serviceClass);
        if (provider != null) {
            if (pluginName.isEmpty() || provider.getPlugin().getName().equals(pluginName)) {
                return provider;
            }
        }

        org.bukkit.plugin.Plugin plugin = Bukkit.getPluginManager().getPlugin(pluginName);
        if (plugin == null) {
            return provider;
        }
        return createFallbackServiceProvider(serviceClass, plugin);
    }

    @Nullable
    private static Class<?> resolveServiceClass(@NotNull String serviceName) {
        if (serviceName.isEmpty()) {
            return null;
        }
        try {
            return Class.forName(serviceName);
        } catch (ClassNotFoundException ignored) {
            // ignore
        }
        for (Class<?> known : Bukkit.getServicesManager().getKnownServices()) {
            if (known.getName().equals(serviceName) || known.getSimpleName().equals(serviceName)) {
                return known;
            }
        }
        return null;
    }

    @Nullable
    private static RegisteredServiceProvider<?> createFallbackServiceProvider(
        @NotNull Class<?> serviceClass,
        @NotNull org.bukkit.plugin.Plugin plugin
    ) {
        Object instance = createServiceInstance(serviceClass);
        if (instance == null) {
            return null;
        }
        try {
            java.lang.reflect.Constructor<?>[] ctors = RegisteredServiceProvider.class.getConstructors();
            for (java.lang.reflect.Constructor<?> ctor : ctors) {
                Class<?>[] params = ctor.getParameterTypes();
                if (params.length != 4) {
                    continue;
                }
                if (!Class.class.isAssignableFrom(params[0])) {
                    continue;
                }
                if (!ServicePriority.class.isAssignableFrom(params[2])) {
                    continue;
                }
                if (!org.bukkit.plugin.Plugin.class.isAssignableFrom(params[3])) {
                    continue;
                }
                try {
                    ServicePriority priority = ServicePriority.values().length > 0
                        ? ServicePriority.values()[0]
                        : null;
                    Object created = ctor.newInstance(serviceClass, instance, priority, plugin);
                    if (created instanceof RegisteredServiceProvider<?> provider) {
                        return provider;
                    }
                } catch (ReflectiveOperationException ignored) {
                    // try next constructor
                }
            }
        } catch (SecurityException ignored) {
            // ignore
        }
        return null;
    }

    @Nullable
    private static Object createServiceInstance(@NotNull Class<?> serviceClass) {
        if (serviceClass.isInterface()) {
            return java.lang.reflect.Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class<?>[] { serviceClass },
                (proxy, method, args) -> defaultValue(method.getReturnType())
            );
        }
        try {
            return serviceClass.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    @Nullable
    private static Object defaultValue(@NotNull Class<?> returnType) {
        if (!returnType.isPrimitive()) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class) {
            return (short) 0;
        }
        if (returnType == int.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0.0f;
        }
        if (returnType == double.class) {
            return 0.0d;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
    }

    private static void setIgnitingBlock(@NotNull BlockBurnEvent event, @Nullable String ignitingKey) {
        if (ignitingKey == null || ignitingKey.isEmpty()) {
            return;
        }
        Material material = Material.matchMaterial(ignitingKey);
        if (material == null) {
            material = Material.matchMaterial("minecraft:" + ignitingKey);
        }
        if (material == null) {
            return;
        }
        try {
            java.lang.reflect.Method method = event.getClass().getMethod("setIgnitingBlock", Block.class);
            Block block = event.getBlock().getWorld().getBlockAt(event.getBlock().getLocation());
            if (block != null) {
                method.invoke(event, block);
            }
        } catch (ReflectiveOperationException ignored) {
            // ignore
        }
    }

    @Nullable
    private static PlayerLoginEvent createLoginEvent(@NotNull Player player) {
        InetAddress playerAddress = resolvePlayerAddress(player);
        try {
            return PlayerLoginEvent.class.getConstructor(Player.class, String.class, InetAddress.class)
                .newInstance(player, "", playerAddress);
        } catch (ReflectiveOperationException ignored) {
            try {
                return PlayerLoginEvent.class.getConstructor(Player.class, String.class, java.net.InetAddress.class,
                        PlayerLoginEvent.Result.class, String.class)
                    .newInstance(player, "", playerAddress, PlayerLoginEvent.Result.ALLOWED, "");
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    @Nullable
    private static InetAddress resolvePlayerAddress(@NotNull Player player) {
        try {
            InetSocketAddress address = player.getAddress();
            return address != null ? address.getAddress() : null;
        } catch (UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static void applyLoginResult(@NotNull PlayerLoginEvent event, @NotNull String kickMessage) {
        if (kickMessage.isEmpty()) {
            try {
                event.allow();
            } catch (UnsupportedOperationException ignored) {
                // Older API variants may not expose allow/disallow in the same way.
            }
            return;
        }

        try {
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMessage);
        } catch (UnsupportedOperationException ignored) {
            event.setKickMessage(kickMessage);
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
