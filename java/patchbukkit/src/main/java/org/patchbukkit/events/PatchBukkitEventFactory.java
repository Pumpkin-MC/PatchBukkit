package org.patchbukkit.events;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.logging.Logger;

public class PatchBukkitEventFactory {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");

    @Nullable
    public static PlayerJoinEvent createPlayerJoinEvent(@NotNull String playerUuid, @NotNull String joinMessage) {
        Player player = getPlayer(playerUuid);
        if (player == null) return null;

        Component joinMessageComponent = Component.translatable("multiplayer.player.joined",
            Component.text(joinMessage));

        return new PlayerJoinEvent(player, joinMessageComponent);
    }

    @Nullable
    public static PlayerQuitEvent createPlayerQuitEvent(@NotNull String playerUuid) {
        Player player = getPlayer(playerUuid);
        if (player == null) return null;

        Component quitMessage = Component.translatable("multiplayer.player.left",
            Component.text(player.getName()));

        return new PlayerQuitEvent(player, quitMessage, PlayerQuitEvent.QuitReason.DISCONNECTED);
    }

    /**
     * Check if an event implements Cancellable.
     */
    public static boolean isCancellable(@NotNull Event event) {
        return event instanceof org.bukkit.event.Cancellable;
    }

    /**
     * Look up a player by UUID string. Returns null with a warning if not found.
     */
    @Nullable
    private static Player getPlayer(@NotNull String uuidStr) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
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
