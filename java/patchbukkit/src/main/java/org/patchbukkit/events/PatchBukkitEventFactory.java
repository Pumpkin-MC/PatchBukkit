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
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
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
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
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
}
