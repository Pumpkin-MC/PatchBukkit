package org.patchbukkit.events;

import com.google.protobuf.InvalidProtocolBufferException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.BroadcastMessageEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.command.PatchBukkitConsoleCommandSender;
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
}
