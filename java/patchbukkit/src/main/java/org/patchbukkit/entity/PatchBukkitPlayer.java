package org.patchbukkit.entity;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.DyeColor;
import org.bukkit.Effect;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.Particle;
import org.bukkit.GameMode;
import org.bukkit.ServerLinks;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Statistic;
import org.bukkit.WeatherType;
import org.bukkit.WorldBorder;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerKickEvent.Cause;
import org.bukkit.event.player.PlayerResourcePackStatusEvent.Status;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapView;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.StandardMessenger;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.PatchBukkitServer;
import org.patchbukkit.bridge.BridgeUtils;

import com.destroystokyo.paper.Title;

import io.papermc.paper.math.Position;
import io.papermc.paper.ban.BanListType;import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import net.md_5.bungee.api.chat.BaseComponent;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.KickPlayerRequest;
import patchbukkit.entity.SendActionBarRequest;
import patchbukkit.entity.SendBlockChangeRequest;
import patchbukkit.entity.SendGameEventRequest;
import patchbukkit.entity.SendResourcePackRequest;
import patchbukkit.entity.SendTitleRequest;
import patchbukkit.entity.SetCompassTargetRequest;
import patchbukkit.entity.SetDisplayNameRequest;
import patchbukkit.entity.SetExperienceRequest;
import patchbukkit.entity.SetPlayerListHeaderFooterRequest;
import patchbukkit.entity.SetPlayerListNameRequest;
import patchbukkit.entity.SetPlayerTimeRequest;
import patchbukkit.entity.SetPlayerWeatherRequest;
import patchbukkit.entity.SetRespawnPointRequest;
import patchbukkit.entity.StopSoundRequest;
import patchbukkit.world.SpawnParticleRequest;

@SuppressWarnings({ "deprecation", "removal" })
public class PatchBukkitPlayer extends PatchBukkitHumanEntity implements Player {

    private String displayName;
    private String playerListName;
    private String playerListHeader = "";
    private String playerListFooter = "";
    private int playerListOrder = 0;
    private long playerTime = 0;
    private boolean playerTimeRelative = true;
    private WeatherType playerWeather = null;
    private Location compassTarget;
    private double healthScale = 20.0;
    private boolean healthScaled = false;
    private boolean affectsSpawning = true;
    private int viewDistance = -1;
    private int simulationDistance = -1;
    private int sendViewDistance = -1;
    private Scoreboard scoreboard;
    private final Set<BossBar> bossBars = new HashSet<>();
    private final Set<String> listeningChannels = new HashSet<>();
    private final Set<Player> hiddenPlayers = new HashSet<>();
    private final Map<UUID, Set<Plugin>> hiddenPlayersPlugins = new HashMap<>();
    private final Map<Statistic, Integer> statistics = new EnumMap<>(Statistic.class);
    private final Map<Statistic, Map<Material, Integer>> materialStatistics = new EnumMap<>(Statistic.class);
    private final Map<Statistic, Map<EntityType, Integer>> entityStatistics = new EnumMap<>(Statistic.class);
    private final Player.Spigot spigot;
    private long firstPlayed;
    private long lastLogin;
    private int wardenWarningLevel = 0;
    private int wardenWarningCooldown = 0;
    private int wardenTimeSinceLastWarning = 0;
    private boolean hasSeenWinScreen = false;
    private int expCooldown = 0;
    private TriState flyingFallDamage = TriState.NOT_SET;

    public PatchBukkitPlayer(UUID uuid, String name) {
        super(uuid, name);
        this.displayName = name;
        this.playerListName = name;
        this.firstPlayed = System.currentTimeMillis();
        this.lastLogin = System.currentTimeMillis();
        this.spigot = new Player.Spigot() {
            @Override
            public void sendMessage(@NotNull BaseComponent component) {
                PatchBukkitPlayer.this.sendMessage(BaseComponent.toLegacyText(component));
            }

            @Override
            public void sendMessage(@NotNull BaseComponent... components) {
                PatchBukkitPlayer.this.sendMessage(BaseComponent.toLegacyText(components));
            }

            @Override
            public void sendMessage(@NotNull net.md_5.bungee.api.ChatMessageType position, @NotNull BaseComponent... components) {
                if (position == net.md_5.bungee.api.ChatMessageType.ACTION_BAR) {
                    PatchBukkitPlayer.this.sendActionBar(BaseComponent.toLegacyText(components));
                } else {
                    PatchBukkitPlayer.this.sendMessage(BaseComponent.toLegacyText(components));
                }
            }

            @Override
            public void sendMessage(@Nullable UUID sender, @NotNull BaseComponent component) {
                sendMessage(component);
            }

            @Override
            public void sendMessage(@Nullable UUID sender, @NotNull BaseComponent... components) {
                sendMessage(components);
            }

            @Override
            public @NotNull InetSocketAddress getRawAddress() {
                return PatchBukkitPlayer.this.getAddress();
            }

            public @NotNull String getLocale() {
                return PatchBukkitPlayer.this.getLocale();
            }

            @Override
            public @NotNull Set<Player> getHiddenPlayers() {
                return Collections.unmodifiableSet(PatchBukkitPlayer.this.hiddenPlayers);
            }

            @Override
            public void respawn() {
                Location loc = PatchBukkitPlayer.this.getBedSpawnLocation();
                if (loc != null) {
                    PatchBukkitPlayer.this.teleport(loc);
                }
            }
        };
    }

    @Override
    public Player.Spigot spigot() {
        return this.spigot;
    }

    // --- Identity, Online & OfflinePlayer ---

    @Override
    public boolean isOnline() {
        return true;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public Player getPlayer() {
        return this;
    }

    @Override
    public long getFirstPlayed() {
        return this.firstPlayed > 0 ? this.firstPlayed : System.currentTimeMillis();
    }

    @Override
    public long getLastPlayed() {
        return System.currentTimeMillis();
    }

    @Override
    public boolean hasPlayedBefore() {
        return true;
    }

    @Override
    public long getLastLogin() {
        return this.lastLogin > 0 ? this.lastLogin : System.currentTimeMillis();
    }

    @Override
    public long getLastSeen() {
        return System.currentTimeMillis();
    }

    @Override
    public boolean isBanned() {
        return PatchBukkitServer.getInstance().getBanList(BanList.Type.NAME).isBanned(getName());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Date expires, @Nullable String source) {
        return ban(reason, expires, source, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Instant expires, @Nullable String source) {
        return ban(reason, expires, source, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Duration duration, @Nullable String source) {
        return ban(reason, duration, source, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Date expires, @Nullable String source, boolean kickPlayer) {
        E entry = null;
        try {
            entry = (E) PatchBukkitServer.getInstance().getBanList(BanList.Type.NAME)
                .addBan(getName(), reason, expires, source);
        } catch (Throwable ignored) {}
        if (kickPlayer) {
            kickPlayer(reason != null ? reason : "Banned by operator");
        }
        return entry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Instant expires, @Nullable String source, boolean kickPlayer) {
        E entry = null;
        try {
            entry = (E) PatchBukkitServer.getInstance().getBanList(BanList.Type.NAME)
                .addBan(getName(), reason, expires != null ? Date.from(expires) : null, source);
        } catch (Throwable ignored) {}
        if (kickPlayer) {
            kickPlayer(reason != null ? reason : "Banned by operator");
        }
        return entry;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E extends BanEntry<? super com.destroystokyo.paper.profile.PlayerProfile>> @Nullable E ban(@Nullable String reason, @Nullable Duration duration, @Nullable String source, boolean kickPlayer) {
        E entry = null;
        try {
            entry = (E) PatchBukkitServer.getInstance().getBanList(BanList.Type.NAME)
                .addBan(getName(), reason, duration != null ? Date.from(Instant.now().plus(duration)) : null, source);
        } catch (Throwable ignored) {}
        if (kickPlayer) {
            kickPlayer(reason != null ? reason : "Banned by operator");
        }
        return entry;
    }

    @Override
    public @Nullable BanEntry<InetAddress> banIp(@Nullable String reason, @Nullable Date expires, @Nullable String source, boolean kickPlayer) {
        BanEntry<InetAddress> entry = null;
        try {
            java.net.InetSocketAddress addr = getAddress();
            if (addr != null && addr.getAddress() != null) {
                entry = PatchBukkitServer.getInstance().getBanList(BanListType.IP)
                    .addBan(addr.getAddress(), reason, expires, source);
            }
        } catch (Throwable ignored) {}
        if (kickPlayer) {
            kickPlayer(reason != null ? reason : "Banned IP by operator");
        }
        return entry;
    }

    @Override
    public @Nullable BanEntry<InetAddress> banIp(@Nullable String reason, @Nullable Instant expires, @Nullable String source, boolean kickPlayer) {
        BanEntry<InetAddress> entry = null;
        try {
            java.net.InetSocketAddress addr = getAddress();
            if (addr != null && addr.getAddress() != null) {
                entry = PatchBukkitServer.getInstance().getBanList(BanListType.IP)
                    .addBan(addr.getAddress(), reason, expires != null ? Date.from(expires) : null, source);
            }
        } catch (Throwable ignored) {}
        if (kickPlayer) {
            kickPlayer(reason != null ? reason : "Banned IP by operator");
        }
        return entry;
    }

    @Override
    public @Nullable BanEntry<InetAddress> banIp(@Nullable String reason, @Nullable Duration duration, @Nullable String source, boolean kickPlayer) {
        BanEntry<InetAddress> entry = null;
        try {
            java.net.InetSocketAddress addr = getAddress();
            if (addr != null && addr.getAddress() != null) {
                entry = PatchBukkitServer.getInstance().getBanList(BanListType.IP)
                    .addBan(addr.getAddress(), reason, duration != null ? Date.from(Instant.now().plus(duration)) : null, source);
            }
        } catch (Throwable ignored) {}
        if (kickPlayer) {
            kickPlayer(reason != null ? reason : "Banned IP by operator");
        }
        return entry;
    }

    private boolean whitelisted = false;

    @Override
    public boolean isWhitelisted() {
        return this.whitelisted;
    }

    @Override
    public void setWhitelisted(boolean value) {
        this.whitelisted = value;
    }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("uuid", getUniqueId().toString());
        map.put("name", getName());
        return map;
    }

    // --- Messaging & Chat ---

    /**
    * Sends a component with its click/hover interactions intact. The request
    * carries both the Adventure JSON form and a legacy fallback; the server
    * prefers JSON and degrades to legacy when it cannot parse it.
    */
    public void sendRichMessage(@NotNull Component message) {
        if (message == null) return;
        try {
            var request = patchbukkit.message.SendMessageRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(this.getUniqueId()))
                .setMessage(LegacyComponentSerializer.legacySection().serialize(message))
                .setMessageJson(net.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(message))
                .build();
            NativeBridgeFfi.sendMessage(request);
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        sendRichMessage(message);
    }

    @Override
    public void sendMessage(@NotNull net.kyori.adventure.text.ComponentLike message) {
        sendRichMessage(message.asComponent());
    }

    @Override
    public void sendMessage(@NotNull String message) {
        if (message == null) return;
        try {
            var request = patchbukkit.message.SendMessageRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(this.getUniqueId()))
                .setMessage(message)
                .build();
            NativeBridgeFfi.sendMessage(request);
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendMessage(@NotNull String... messages) {
        if (messages == null) return;
        for (String msg : messages) {
            sendMessage(msg);
        }
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        sendMessage(message);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
        sendMessage(messages);
    }

    @Override
    public void sendRawMessage(@NotNull String message) {
        sendMessage(message);
    }

    @Override
    public void sendRawMessage(@Nullable UUID sender, @NotNull String message) {
        sendMessage(message);
    }

    @Override
    public void sendActionBar(@NotNull String message) {
        if (message == null) return;
        try {
            var req = SendActionBarRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setMessage(message)
                .build();
            NativeBridgeFfi.sendActionBar(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendActionBar(char alternateChar, @NotNull String message) {
        sendActionBar(org.bukkit.ChatColor.translateAlternateColorCodes(alternateChar, message));
    }

    @Override
    public void sendActionBar(@NotNull BaseComponent... message) {
        sendActionBar(BaseComponent.toLegacyText(message));
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        sendActionBar(LegacyComponentSerializer.legacySection().serialize(message));
    }

    @Override
    public void chat(@NotNull String msg) {
        if (msg == null) return;
        if (msg.startsWith("/")) {
            performCommand(msg.substring(1));
        } else {
            sendMessage("<" + getDisplayName() + "> " + msg);
        }
    }

    @Override
    public boolean performCommand(@NotNull String command) {
        if (command == null) return false;
        return PatchBukkitServer.getInstance().dispatchCommand(this, command);
    }

    // --- Display & Tab List ---

    @Override
    public @NotNull String getDisplayName() {
        return this.displayName != null ? this.displayName : getName();
    }

    @Override
    public void setDisplayName(@Nullable String name) {
        this.displayName = name != null ? name : getName();
        try {
            var req = SetDisplayNameRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setDisplayName(this.displayName)
                .build();
            NativeBridgeFfi.setDisplayName(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull Component displayName() {
        return LegacyComponentSerializer.legacySection().deserialize(getDisplayName());
    }

    @Override
    public void displayName(@Nullable Component displayName) {
        setDisplayName(displayName != null ? LegacyComponentSerializer.legacySection().serialize(displayName) : null);
    }

    @Override
    public @NotNull String getPlayerListName() {
        return this.playerListName != null ? this.playerListName : getName();
    }

    @Override
    public void setPlayerListName(@Nullable String name) {
        this.playerListName = name != null ? name : getName();
        try {
            var req = SetPlayerListNameRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setListName(this.playerListName)
                .build();
            NativeBridgeFfi.setPlayerListName(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull Component playerListName() {
        return LegacyComponentSerializer.legacySection().deserialize(getPlayerListName());
    }

    @Override
    public void playerListName(@Nullable Component name) {
        setPlayerListName(name != null ? LegacyComponentSerializer.legacySection().serialize(name) : null);
    }

    @Override
    public @Nullable String getPlayerListHeader() {
        return this.playerListHeader;
    }

    @Override
    public @Nullable String getPlayerListFooter() {
        return this.playerListFooter;
    }

    @Override
    public void setPlayerListHeader(@Nullable String header) {
        setPlayerListHeaderFooter(header, this.playerListFooter);
    }

    @Override
    public void setPlayerListFooter(@Nullable String footer) {
        setPlayerListHeaderFooter(this.playerListHeader, footer);
    }

    @Override
    public void setPlayerListHeaderFooter(@Nullable String header, @Nullable String footer) {
        this.playerListHeader = header != null ? header : "";
        this.playerListFooter = footer != null ? footer : "";
        try {
            var req = SetPlayerListHeaderFooterRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setHeader(this.playerListHeader)
                .setFooter(this.playerListFooter)
                .build();
            NativeBridgeFfi.setPlayerListHeaderFooter(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void setPlayerListHeaderFooter(@Nullable BaseComponent header, @Nullable BaseComponent footer) {
        setPlayerListHeaderFooter(
            header != null ? BaseComponent.toLegacyText(header) : "",
            footer != null ? BaseComponent.toLegacyText(footer) : ""
        );
    }

    public void setPlayerListHeaderFooter(@Nullable BaseComponent[] header, @Nullable BaseComponent[] footer) {
        setPlayerListHeaderFooter(
            header != null ? BaseComponent.toLegacyText(header) : "",
            footer != null ? BaseComponent.toLegacyText(footer) : ""
        );
    }

    @Override
    public @Nullable Component playerListHeader() {
        return LegacyComponentSerializer.legacySection().deserialize(this.playerListHeader);
    }

    @Override
    public @Nullable Component playerListFooter() {
        return LegacyComponentSerializer.legacySection().deserialize(this.playerListFooter);
    }

    @Override
    public void sendPlayerListHeader(@NotNull Component header) {
        setPlayerListHeader(LegacyComponentSerializer.legacySection().serialize(header));
    }

    @Override
    public void sendPlayerListFooter(@NotNull Component footer) {
        setPlayerListFooter(LegacyComponentSerializer.legacySection().serialize(footer));
    }

    @Override
    public void sendPlayerListHeaderAndFooter(@NotNull Component header, @NotNull Component footer) {
        setPlayerListHeaderFooter(LegacyComponentSerializer.legacySection().serialize(header), LegacyComponentSerializer.legacySection().serialize(footer));
    }

    @Override
    public int getPlayerListOrder() {
        return this.playerListOrder;
    }

    @Override
    public void setPlayerListOrder(int order) {
        this.playerListOrder = order;
    }

    // --- Kick ---

    @Override
    public void kickPlayer(@Nullable String message) {
        String kickMsg = (message != null && !message.isEmpty()) ? message : "Kicked from server";
        try {
            var req = KickPlayerRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setMessage(kickMsg)
                .build();
            NativeBridgeFfi.kickPlayer(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void kick() {
        kickPlayer(null);
    }

    @Override
    public void kick(@Nullable Component message) {
        kickPlayer(message != null ? LegacyComponentSerializer.legacySection().serialize(message) : null);
    }

    @Override
    public void kick(@Nullable Component message, @NotNull Cause cause) {
        kick(message);
    }

    // --- Titles ---

    @Override
    public void sendTitle(@Nullable String title, @Nullable String subtitle) {
        sendTitle(title, subtitle, 10, 70, 20);
    }

    @Override
    public void sendTitle(@Nullable String title, @Nullable String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            var req = SendTitleRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setTitle(title != null ? title : "")
                .setSubtitle(subtitle != null ? subtitle : "")
                .setFadeIn(fadeIn)
                .setStay(stay)
                .setFadeOut(fadeOut)
                .build();
            NativeBridgeFfi.sendTitle(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendTitle(@NotNull Title title) {
        sendTitle(
            title.getTitle() != null ? BaseComponent.toLegacyText(title.getTitle()) : "",
            title.getSubtitle() != null ? BaseComponent.toLegacyText(title.getSubtitle()) : "",
            title.getFadeIn(),
            title.getStay(),
            title.getFadeOut()
        );
    }

    @Override
    public void showTitle(@NotNull net.kyori.adventure.title.Title title) {
        String mainTitle = LegacyComponentSerializer.legacySection().serialize(title.title());
        String subTitle = LegacyComponentSerializer.legacySection().serialize(title.subtitle());
        net.kyori.adventure.title.Title.Times times = title.times();
        int fadeIn = (times != null && times.fadeIn() != null) ? (int) (times.fadeIn().toMillis() / 50) : 10;
        int stay = (times != null && times.stay() != null) ? (int) (times.stay().toMillis() / 50) : 70;
        int fadeOut = (times != null && times.fadeOut() != null) ? (int) (times.fadeOut().toMillis() / 50) : 20;
        sendTitle(mainTitle, subTitle, fadeIn, stay, fadeOut);
    }

    public void showTitle(@NotNull Title title) {
        sendTitle(title);
    }

    @Override
    public void showTitle(@Nullable BaseComponent title) {
        sendTitle(title != null ? BaseComponent.toLegacyText(title) : "", "");
    }

    public void showTitle(@NotNull BaseComponent... title) {
        sendTitle(BaseComponent.toLegacyText(title), "");
    }

    public void showTitle(@Nullable BaseComponent[] title, @Nullable BaseComponent[] subtitle, int fadeIn, int stay, int fadeOut) {
        sendTitle(
            title != null ? BaseComponent.toLegacyText(title) : "",
            subtitle != null ? BaseComponent.toLegacyText(subtitle) : "",
            fadeIn, stay, fadeOut
        );
    }

    @Override
    public void showTitle(@Nullable BaseComponent title, @Nullable BaseComponent subtitle, int fadeIn, int stay, int fadeOut) {
        sendTitle(
            title != null ? BaseComponent.toLegacyText(title) : "",
            subtitle != null ? BaseComponent.toLegacyText(subtitle) : "",
            fadeIn, stay, fadeOut
        );
    }

    @Override
    public void setTitleTimes(int fadeIn, int stay, int fadeOut) {
        sendTitle("", "", fadeIn, stay, fadeOut);
    }

    @Override
    public void setSubtitle(@NotNull BaseComponent... subtitle) {
        sendTitle("", BaseComponent.toLegacyText(subtitle), 10, 70, 20);
    }

    @Override
    public void setSubtitle(@NotNull BaseComponent subtitle) {
        sendTitle("", BaseComponent.toLegacyText(subtitle), 10, 70, 20);
    }

    @Override
    public void resetTitle() {
        try {
            NativeBridgeFfi.resetTitle(BridgeUtils.convertUuid(getUniqueId()));
        } catch (Throwable ignored) {}
    }

    @Override
    public void hideTitle() {
        resetTitle();
    }

    @Override
    public void updateTitle(@NotNull Title title) {
        sendTitle(title);
    }

    // --- Compass & Respawn ---

    @Override
    public void setCompassTarget(@NotNull Location loc) {
        if (loc == null) return;
        this.compassTarget = loc.clone();
        try {
            var req = SetCompassTargetRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setPosition(patchbukkit.common.Vec3.newBuilder()
                    .setX(loc.getX())
                    .setY(loc.getY())
                    .setZ(loc.getZ())
                    .build())
                .build();
            NativeBridgeFfi.setCompassTarget(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull Location getCompassTarget() {
        if (this.compassTarget != null) return this.compassTarget.clone();
        try {
            var resp = NativeBridgeFfi.getCompassTarget(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return new Location(getWorld(), resp.getX(), resp.getY(), resp.getZ());
            }
        } catch (Throwable ignored) {}
        return getWorld().getSpawnLocation();
    }

    @Override
    public @Nullable Location getBedSpawnLocation() {
        try {
            var resp = NativeBridgeFfi.getRespawnPoint(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return BridgeUtils.convertLocation(resp);
            }
        } catch (Throwable ignored) {}
        return getWorld().getSpawnLocation();
    }

    @Override
    public @Nullable Location getRespawnLocation() {
        return getBedSpawnLocation();
    }

    public @Nullable Location getRespawnLocation(boolean checkBed) {
        return getBedSpawnLocation();
    }

    @Override
    public void setRespawnLocation(@Nullable Location location) {
        setRespawnLocation(location, false);
    }

    @Override
    public void setRespawnLocation(@Nullable Location location, boolean force) {
        if (location == null) return;
        try {
            var req = SetRespawnPointRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setPosition(patchbukkit.common.Vec3.newBuilder()
                    .setX(location.getX())
                    .setY(location.getY())
                    .setZ(location.getZ())
                    .build())
                .setYaw(location.getYaw())
                .setForce(force)
                .build();
            NativeBridgeFfi.setRespawnPoint(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void setBedSpawnLocation(@Nullable Location location) {
        setRespawnLocation(location, false);
    }

    @Override
    public void setBedSpawnLocation(@Nullable Location location, boolean force) {
        setRespawnLocation(location, force);
    }

    // --- Player Time & Weather ---

    @Override
    public void setPlayerTime(long time, boolean relative) {
        this.playerTime = time;
        this.playerTimeRelative = relative;
        try {
            var req = SetPlayerTimeRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setTime(time)
                .setRelative(relative)
                .build();
            NativeBridgeFfi.setPlayerTime(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public long getPlayerTime() {
        if (this.playerTimeRelative) {
            return getWorld().getTime() + this.playerTime;
        }
        return this.playerTime;
    }

    @Override
    public long getPlayerTimeOffset() {
        return this.playerTime;
    }

    @Override
    public boolean isPlayerTimeRelative() {
        return this.playerTimeRelative;
    }

    @Override
    public void resetPlayerTime() {
        this.playerTime = 0;
        this.playerTimeRelative = true;
        try {
            NativeBridgeFfi.resetPlayerTime(BridgeUtils.convertUuid(getUniqueId()));
        } catch (Throwable ignored) {}
    }

    @Override
    public void setPlayerWeather(@NotNull WeatherType type) {
        this.playerWeather = type;
        try {
            var req = SetPlayerWeatherRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setWeather(type == WeatherType.DOWNFALL ? 1 : 0)
                .build();
            NativeBridgeFfi.setPlayerWeather(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public @Nullable WeatherType getPlayerWeather() {
        return this.playerWeather;
    }

    @Override
    public void resetPlayerWeather() {
        this.playerWeather = null;
        try {
            NativeBridgeFfi.resetPlayerWeather(BridgeUtils.convertUuid(getUniqueId()));
        } catch (Throwable ignored) {}
    }

    // --- Sounds ---

    @Override
    public void stopSound(@NotNull String sound) {
        stopSound(sound, null);
    }

    @Override
    public void stopSound(@NotNull Sound sound) {
        stopSound(sound, null);
    }

    @Override
    public void stopSound(@NotNull Sound sound, @Nullable SoundCategory category) {
        stopSound(sound.getKey().asString(), category);
    }

    @Override
    public void stopSound(@NotNull String sound, @Nullable SoundCategory category) {
        try {
            var req = StopSoundRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setSound(sound != null ? sound : "")
                .setCategory(category != null ? category.name().toLowerCase() : "")
                .build();
            NativeBridgeFfi.stopSound(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void stopSound(@NotNull SoundCategory category) {
        stopSound("", category);
    }

    @Override
    public void stopAllSounds() {
        stopSound("", null);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {
        playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, float volume, float pitch) {
        playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(location, sound.getKey().asString(), category, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(location, sound, category, volume, pitch, 0L);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        playSound(location, sound.getKey().asString(), category, volume, pitch, seed);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        try {
            var req = patchbukkit.sound.PlayerPlaySoundRequest.newBuilder()
                .setPlayerUuid(BridgeUtils.convertUuid(this.getUniqueId()))
                .setLocation(BridgeUtils.convertLocation(location))
                .setSound(patchbukkit.sound.Sound.newBuilder()
                    .setName(sound)
                    .setCategory(category.name())
                    .build())
                .setVolume(volume)
                .setPitch(pitch)
                .setSeed(seed)
                .build();
            NativeBridgeFfi.playerPlaySound(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, float volume, float pitch) {
        playSound(entity, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, float volume, float pitch) {
        playSound(entity, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(entity, sound.getKey().asString(), category, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(entity, sound, category, volume, pitch, 0L);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        playSound(entity, sound.getKey().asString(), category, volume, pitch, seed);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        try {
            var req = patchbukkit.sound.PlayerEntityPlaySoundRequest.newBuilder()
                .setPlayerUuid(BridgeUtils.convertUuid(this.getUniqueId()))
                .setEntityUuid(BridgeUtils.convertUuid(entity.getUniqueId()))
                .setSound(patchbukkit.sound.Sound.newBuilder()
                    .setName(sound)
                    .setCategory(category.name())
                    .build())
                .setVolume(volume)
                .setPitch(pitch)
                .setSeed(seed)
                .build();
            NativeBridgeFfi.playerEntityPlaySound(req);
        } catch (Throwable ignored) {}
    }

    // --- Block Changes & Updates ---

    @Override
    public void sendBlockChange(@NotNull Location loc, @NotNull Material material, byte data) {
        sendBlockChange(loc, material.createBlockData());
    }

    @Override
    public void sendBlockChange(@NotNull Location loc, @NotNull BlockData block) {
        if (loc == null || block == null) return;
        try {
            var req = SendBlockChangeRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setX(loc.getBlockX())
                .setY(loc.getBlockY())
                .setZ(loc.getBlockZ())
                .setBlockState(block.getAsString())
                .build();
            NativeBridgeFfi.sendBlockChange(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void sendBlockChanges(@NotNull Collection<BlockState> blocks) {
        if (blocks == null) return;
        for (BlockState b : blocks) {
            sendBlockChange(b.getLocation(), b.getBlockData());
        }
    }

    @Override
    public void sendMultiBlockChange(@NotNull Map<? extends Position, BlockData> blockChanges) {
        if (blockChanges == null) return;
        for (Map.Entry<? extends Position, BlockData> entry : blockChanges.entrySet()) {
            Position pos = entry.getKey();
            Location loc = new Location(getWorld(), pos.x(), pos.y(), pos.z());
            sendBlockChange(loc, entry.getValue());
        }
    }

    @Override
    public void sendBlockDamage(@NotNull Location loc, float progress, @NotNull Entity source) {
    }

    @Override
    public void sendBlockDamage(@NotNull Location loc, float progress, int sourceId) {
    }

    @Override
    public void sendSignChange(@NotNull Location loc, @Nullable String[] lines) {
        sendSignChange(loc, lines, DyeColor.BLACK);
    }

    @Override
    public void sendSignChange(@NotNull Location loc, @Nullable String[] lines, @NotNull DyeColor dyeColor) {
        sendSignChange(loc, lines, dyeColor, false);
    }

    @Override
    public void sendSignChange(@NotNull Location loc, @Nullable String[] lines, @NotNull DyeColor dyeColor, boolean hasGlowingText) {
    }

    @Override
    public void sendSignChange(@NotNull Location loc, @Nullable List<? extends Component> lines, @NotNull DyeColor dyeColor, boolean hasGlowingText) {
    }

    @Override
    public void sendBlockUpdate(@NotNull Location loc, @NotNull TileState tileState) {
        sendBlockChange(loc, tileState.getBlockData());
    }

    // --- Resource Packs ---

    @Override
    public void setResourcePack(@NotNull String url) {
        setResourcePack(url, (byte[]) null);
    }

    @Override
    public void setResourcePack(@NotNull String url, @Nullable byte[] hash) {
        setResourcePack(url, hash, "");
    }

    @Override
    public void setResourcePack(@NotNull String url, @Nullable byte[] hash, @Nullable String prompt) {
        setResourcePack(url, hash, prompt, false);
    }

    @Override
    public void setResourcePack(@NotNull String url, @Nullable byte[] hash, boolean required) {
        setResourcePack(url, hash, "", required);
    }

    @Override
    public void setResourcePack(@NotNull String url, @Nullable byte[] hash, @Nullable String prompt, boolean required) {
        try {
            var req = SendResourcePackRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setUrl(url)
                .setPrompt(prompt != null ? prompt : "")
                .setRequired(required)
                .build();
            NativeBridgeFfi.sendResourcePack(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void setResourcePack(@NotNull String url, @Nullable byte[] hash, @Nullable Component prompt, boolean required) {
        setResourcePack(url, hash, prompt != null ? LegacyComponentSerializer.legacySection().serialize(prompt) : "", required);
    }

    @Override
    public void setResourcePack(@NotNull UUID id, @NotNull String url, @Nullable byte[] hash, @Nullable Component prompt, boolean required) {
        setResourcePack(url, hash, prompt, required);
    }

    @Override
    public void setResourcePack(@NotNull UUID id, @NotNull String url, @Nullable byte[] hash, @Nullable String prompt, boolean required) {
        setResourcePack(url, hash, prompt, required);
    }

    public void addResourcePack(@NotNull UUID id, @NotNull String url, @Nullable byte[] hash, @Nullable Component prompt, boolean required) {
        setResourcePack(url, hash, prompt, required);
    }

    @Override
    public void addResourcePack(@NotNull UUID id, @NotNull String url, @Nullable byte[] hash, @Nullable String prompt, boolean required) {
        setResourcePack(url, hash, prompt, required);
    }

    @Override
    public void removeResourcePack(@NotNull UUID id) {
    }

    @Override
    public void removeResourcePacks() {
    }

    @Override
    public @Nullable Status getResourcePackStatus() {
        return Status.SUCCESSFULLY_LOADED;
    }

    // --- Connection & Network ---

    @Override
    public @Nullable InetSocketAddress getAddress() {
        try {
            var resp = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return new InetSocketAddress(resp.getAddress(), resp.getPort());
            }
        } catch (Throwable ignored) {}
        return new InetSocketAddress("127.0.0.1", 25565);
    }

    @Override
    public @Nullable InetSocketAddress getHAProxyAddress() {
        return getAddress();
    }

    @Override
    public int getPing() {
        try {
            var resp = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return resp.getPing();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    @Override
    public @NotNull String getClientBrandName() {
        try {
            var resp = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return resp.getClientBrand();
            }
        } catch (Throwable ignored) {}
        return "vanilla";
    }

    @Override
    public int getProtocolVersion() {
        return 769; // 1.21.4
    }

    @Override
    public @Nullable InetSocketAddress getVirtualHost() {
        return new InetSocketAddress("127.0.0.1", 25565);
    }

    @Override
    public boolean isTransferred() {
        return false;
    }

    @Override
    public CompletableFuture<byte[]> retrieveCookie(@NotNull NamespacedKey key) {
        return CompletableFuture.completedFuture(new byte[0]);
    }

    @Override
    public void storeCookie(@NotNull NamespacedKey key, @NotNull byte[] value) {
    }

    @Override
    public void transfer(@NotNull String host, int port) {
    }

    // --- Flying & Movement ---

    @Override
    public boolean getAllowFlight() {
        try {
            var resp = NativeBridgeFfi.getAbilities(BridgeUtils.convertUuid(this.getUniqueId()));
            if (resp != null) {
                return resp.getAllowFlying();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void setAllowFlight(boolean flight) {
        try {
            var uuid = BridgeUtils.convertUuid(this.getUniqueId());
            var current = NativeBridgeFfi.getAbilities(uuid);
            var builder = current != null ? current.toBuilder() : patchbukkit.abilities.Abilities.newBuilder();
            builder.setAllowFlying(flight);
            NativeBridgeFfi.setAbilities(patchbukkit.abilities.SetAbilitiesRequest.newBuilder()
                .setUuid(uuid)
                .setAbilities(builder.build())
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isFlying() {
        try {
            var resp = NativeBridgeFfi.getAbilities(BridgeUtils.convertUuid(this.getUniqueId()));
            if (resp != null) {
                return resp.getFlying();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void setFlying(boolean value) {
        try {
            var uuid = BridgeUtils.convertUuid(this.getUniqueId());
            var current = NativeBridgeFfi.getAbilities(uuid);
            var builder = current != null ? current.toBuilder() : patchbukkit.abilities.Abilities.newBuilder();
            builder.setFlying(value);
            NativeBridgeFfi.setAbilities(patchbukkit.abilities.SetAbilitiesRequest.newBuilder()
                .setUuid(uuid)
                .setAbilities(builder.build())
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public float getFlySpeed() {
        try {
            var resp = NativeBridgeFfi.getAbilities(BridgeUtils.convertUuid(this.getUniqueId()));
            if (resp != null) {
                return resp.getFlySpeed();
            }
        } catch (Throwable ignored) {}
        return 0.1f;
    }

    @Override
    public void setFlySpeed(float value) throws IllegalArgumentException {
        if (value < -1.0f || value > 1.0f) {
            throw new IllegalArgumentException("Fly speed must be between -1.0 and 1.0");
        }
        try {
            var uuid = BridgeUtils.convertUuid(this.getUniqueId());
            var current = NativeBridgeFfi.getAbilities(uuid);
            var builder = current != null ? current.toBuilder() : patchbukkit.abilities.Abilities.newBuilder();
            builder.setFlySpeed(value);
            NativeBridgeFfi.setAbilities(patchbukkit.abilities.SetAbilitiesRequest.newBuilder()
                .setUuid(uuid)
                .setAbilities(builder.build())
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public float getWalkSpeed() {
        try {
            var resp = NativeBridgeFfi.getAbilities(BridgeUtils.convertUuid(this.getUniqueId()));
            if (resp != null) {
                return resp.getWalkSpeed();
            }
        } catch (Throwable ignored) {}
        return 0.2f;
    }

    @Override
    public void setWalkSpeed(float value) throws IllegalArgumentException {
        if (value < -1.0f || value > 1.0f) {
            throw new IllegalArgumentException("Walk speed must be between -1.0 and 1.0");
        }
        try {
            var uuid = BridgeUtils.convertUuid(this.getUniqueId());
            var current = NativeBridgeFfi.getAbilities(uuid);
            var builder = current != null ? current.toBuilder() : patchbukkit.abilities.Abilities.newBuilder();
            builder.setWalkSpeed(value);
            NativeBridgeFfi.setAbilities(patchbukkit.abilities.SetAbilitiesRequest.newBuilder()
                .setUuid(uuid)
                .setAbilities(builder.build())
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public void setFlyingFallDamage(@NotNull TriState state) {
        this.flyingFallDamage = state;
    }

    @Override
    public @NotNull TriState hasFlyingFallDamage() {
        return this.flyingFallDamage;
    }

    @Override
    public void unsetFixedPose() {
    }

    @Override
    public void resetFlyingTicks() {
    }

    // --- Experience & Level ---

    @Override
    public int getLevel() {
        try {
            var resp = NativeBridgeFfi.getExperience(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return resp.getLevel();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    @Override
    public void setLevel(int level) {
        try {
            var req = SetExperienceRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setLevel(level)
                .setProgress(getExp())
                .setTotalExperience(getTotalExperience())
                .build();
            NativeBridgeFfi.setExperience(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public float getExp() {
        try {
            var resp = NativeBridgeFfi.getExperience(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return resp.getProgress();
            }
        } catch (Throwable ignored) {}
        return 0.0f;
    }

    @Override
    public void setExp(float exp) {
        try {
            var req = SetExperienceRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setLevel(getLevel())
                .setProgress(exp)
                .setTotalExperience(getTotalExperience())
                .build();
            NativeBridgeFfi.setExperience(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public int getTotalExperience() {
        try {
            var resp = NativeBridgeFfi.getExperience(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                return resp.getTotalExperience();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    @Override
    public void setTotalExperience(int exp) {
        try {
            var req = SetExperienceRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setLevel(getLevel())
                .setProgress(getExp())
                .setTotalExperience(exp)
                .build();
            NativeBridgeFfi.setExperience(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void giveExp(int amount) {
        giveExp(amount, false);
    }

    @Override
    public void giveExp(int amount, boolean applyMending) {
        int newTotal = Math.max(0, getTotalExperience() + amount);
        setTotalExperience(newTotal);
    }

    @Override
    public void giveExpLevels(int amount) {
        setLevel(Math.max(0, getLevel() + amount));
    }

    @Override
    public int calculateTotalExperiencePoints() {
        return getTotalExperience();
    }

    @Override
    public void setExperienceLevelAndProgress(int totalExperience) {
        setTotalExperience(totalExperience);
    }

    @Override
    public int getExperiencePointsNeededForNextLevel() {
        int level = getLevel();
        if (level >= 30) return 112 + (level - 30) * 9;
        if (level >= 15) return 37 + (level - 15) * 5;
        return 7 + level * 2;
    }

    @Override
    public void sendExperienceChange(float progress) {
        sendExperienceChange(progress, getLevel());
    }

    @Override
    public void sendExperienceChange(float progress, int level) {
        try {
            var req = SetExperienceRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setLevel(level)
                .setProgress(progress)
                .setTotalExperience(getTotalExperience())
                .build();
            NativeBridgeFfi.setExperience(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public int applyMending(int amount) {
        return amount;
    }

    @Override
    public int getExpCooldown() {
        return this.expCooldown;
    }

    @Override
    public void setExpCooldown(int ticks) {
        this.expCooldown = ticks;
    }

    // --- Game Events & Screens ---

    @Override
    public void showDemoScreen() {
        try {
            var req = SendGameEventRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setEventType(5) // Demo
                .setValue(0.0f)
                .build();
            NativeBridgeFfi.sendGameEvent(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public void showWinScreen() {
        this.hasSeenWinScreen = true;
        try {
            var req = SendGameEventRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setEventType(4) // WinGame
                .setValue(1.0f)
                .build();
            NativeBridgeFfi.sendGameEvent(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean hasSeenWinScreen() {
        return this.hasSeenWinScreen;
    }

    @Override
    public void setHasSeenWinScreen(boolean hasSeenWinScreen) {
        this.hasSeenWinScreen = hasSeenWinScreen;
    }

    @Override
    public void showElderGuardian(boolean silent) {
        try {
            var req = SendGameEventRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setEventType(10) // ElderGuardian
                .setValue(0.0f)
                .build();
            NativeBridgeFfi.sendGameEvent(req);
        } catch (Throwable ignored) {}
    }

    @Override
    public int getWardenWarningLevel() {
        return this.wardenWarningLevel;
    }

    @Override
    public void setWardenWarningLevel(int wardenWarningLevel) {
        this.wardenWarningLevel = wardenWarningLevel;
    }

    @Override
    public void increaseWardenWarningLevel() {
        this.wardenWarningLevel++;
    }

    @Override
    public int getWardenWarningCooldown() {
        return this.wardenWarningCooldown;
    }

    @Override
    public void setWardenWarningCooldown(int ticks) {
        this.wardenWarningCooldown = ticks;
    }

    @Override
    public int getWardenTimeSinceLastWarning() {
        return this.wardenTimeSinceLastWarning;
    }

    @Override
    public void setWardenTimeSinceLastWarning(int ticks) {
        this.wardenTimeSinceLastWarning = ticks;
    }

    @Override
    public void sendHurtAnimation(float yaw) {
    }

    // --- Health Scale & Updates ---

    @Override
    public boolean isHealthScaled() {
        return this.healthScaled;
    }

    @Override
    public void setHealthScaled(boolean scale) {
        this.healthScaled = scale;
    }

    @Override
    public double getHealthScale() {
        return this.healthScale;
    }

    @Override
    public void setHealthScale(double scale) throws IllegalArgumentException {
        if (scale <= 0) {
            throw new IllegalArgumentException("Health scale must be greater than 0");
        }
        this.healthScale = scale;
        this.healthScaled = true;
    }

    @Override
    public void sendHealthUpdate(double health, int foodLevel, float saturation) {
        setHealth(health);
        setFoodLevel(foodLevel);
        setSaturation(saturation);
    }

    @Override
    public void sendHealthUpdate() {
        sendHealthUpdate(getHealth(), getFoodLevel(), getSaturation());
    }

    // --- Scoreboards & BossBars & WorldBorder ---

    @Override
    public @NotNull Scoreboard getScoreboard() {
        return this.scoreboard != null ? this.scoreboard : PatchBukkitServer.getInstance().getScoreboardManager().getMainScoreboard();
    }

    @Override
    public void setScoreboard(@NotNull Scoreboard scoreboard) throws IllegalArgumentException, IllegalStateException {
        this.scoreboard = scoreboard;
    }

    @Override
    public @NotNull Iterable<? extends BossBar> activeBossBars() {
        return Collections.unmodifiableSet(this.bossBars);
    }

    @Override
    public void showBossBar(@NotNull BossBar bar) {
        this.bossBars.add(bar);
    }

    @Override
    public void hideBossBar(@NotNull BossBar bar) {
        this.bossBars.remove(bar);
    }

    @Override
    public @Nullable WorldBorder getWorldBorder() {
        return getWorld().getWorldBorder();
    }

    @Override
    public void setWorldBorder(@Nullable WorldBorder border) {
    }

    // --- Statistics ---

    @Override
    public void incrementStatistic(@NotNull Statistic statistic) throws IllegalArgumentException {
        incrementStatistic(statistic, 1);
    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic) throws IllegalArgumentException {
        decrementStatistic(statistic, 1);
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, int amount) throws IllegalArgumentException {
        setStatistic(statistic, getStatistic(statistic) + amount);
    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, int amount) throws IllegalArgumentException {
        setStatistic(statistic, Math.max(0, getStatistic(statistic) - amount));
    }

    @Override
    public void setStatistic(@NotNull Statistic statistic, int newValue) throws IllegalArgumentException {
        this.statistics.put(statistic, Math.max(0, newValue));
    }

    @Override
    public int getStatistic(@NotNull Statistic statistic) throws IllegalArgumentException {
        return this.statistics.getOrDefault(statistic, 0);
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull Material material) throws IllegalArgumentException {
        incrementStatistic(statistic, material, 1);
    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull Material material) throws IllegalArgumentException {
        decrementStatistic(statistic, material, 1);
    }

    @Override
    public int getStatistic(@NotNull Statistic statistic, @NotNull Material material) throws IllegalArgumentException {
        return this.materialStatistics.computeIfAbsent(statistic, k -> new EnumMap<>(Material.class)).getOrDefault(material, 0);
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull Material material, int amount) throws IllegalArgumentException {
        setStatistic(statistic, material, getStatistic(statistic, material) + amount);
    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull Material material, int amount) throws IllegalArgumentException {
        setStatistic(statistic, material, Math.max(0, getStatistic(statistic, material) - amount));
    }

    @Override
    public void setStatistic(@NotNull Statistic statistic, @NotNull Material material, int newValue) throws IllegalArgumentException {
        this.materialStatistics.computeIfAbsent(statistic, k -> new EnumMap<>(Material.class)).put(material, Math.max(0, newValue));
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) throws IllegalArgumentException {
        incrementStatistic(statistic, entityType, 1);
    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) throws IllegalArgumentException {
        decrementStatistic(statistic, entityType, 1);
    }

    @Override
    public int getStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType) throws IllegalArgumentException {
        return this.entityStatistics.computeIfAbsent(statistic, k -> new EnumMap<>(EntityType.class)).getOrDefault(entityType, 0);
    }

    @Override
    public void incrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int amount) throws IllegalArgumentException {
        setStatistic(statistic, entityType, getStatistic(statistic, entityType) + amount);
    }

    @Override
    public void decrementStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int amount) throws IllegalArgumentException {
        setStatistic(statistic, entityType, Math.max(0, getStatistic(statistic, entityType) - amount));
    }

    @Override
    public void setStatistic(@NotNull Statistic statistic, @NotNull EntityType entityType, int newValue) throws IllegalArgumentException {
        this.entityStatistics.computeIfAbsent(statistic, k -> new EnumMap<>(EntityType.class)).put(entityType, Math.max(0, newValue));
    }

    // --- Player Visibility & Hiding ---

    @Override
    public void hidePlayer(@NotNull Player player) {
        this.hiddenPlayers.add(player);
    }

    @Override
    public void hidePlayer(@NotNull Plugin plugin, @NotNull Player player) {
        this.hiddenPlayersPlugins.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(plugin);
        hidePlayer(player);
    }

    @Override
    public void showPlayer(@NotNull Player player) {
        this.hiddenPlayers.remove(player);
    }

    @Override
    public void showPlayer(@NotNull Plugin plugin, @NotNull Player player) {
        Set<Plugin> set = this.hiddenPlayersPlugins.get(player.getUniqueId());
        if (set != null) {
            set.remove(plugin);
            if (set.isEmpty()) {
                this.hiddenPlayersPlugins.remove(player.getUniqueId());
                showPlayer(player);
            }
        } else {
            showPlayer(player);
        }
    }

    @Override
    public boolean canSee(@NotNull Player player) {
        return !this.hiddenPlayers.contains(player);
    }

    @Override
    public boolean canSee(@NotNull Entity entity) {
        if (entity instanceof Player player) {
            return canSee(player);
        }
        return true;
    }

    @Override
    public void hideEntity(@NotNull Plugin plugin, @NotNull Entity entity) {
        if (entity instanceof Player player) {
            hidePlayer(plugin, player);
        }
    }

    @Override
    public void showEntity(@NotNull Plugin plugin, @NotNull Entity entity) {
        if (entity instanceof Player player) {
            showPlayer(plugin, player);
        }
    }

    private final Set<UUID> unlistedPlayers = new HashSet<>();

    @Override
    public boolean isListed(@NotNull Player player) {
        return !this.unlistedPlayers.contains(player.getUniqueId());
    }

    @Override
    public boolean unlistPlayer(@NotNull Player player) {
        return this.unlistedPlayers.add(player.getUniqueId());
    }

    @Override
    public boolean listPlayer(@NotNull Player player) {
        return this.unlistedPlayers.remove(player.getUniqueId());
    }

    // --- Inventory & Items & Give ---

    @Override
    public void updateInventory() {
        try {
            NativeBridgeFfi.updateInventory(BridgeUtils.convertUuid(getUniqueId()));
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull io.papermc.paper.entity.PlayerGiveResult give(@NotNull Collection<ItemStack> items, boolean dropIfFull) {
        if (items != null) {
            for (ItemStack item : items) {
                if (item == null || item.isEmpty()) continue;
                HashMap<Integer, ItemStack> leftover = getInventory().addItem(item);
                if (dropIfFull && !leftover.isEmpty()) {
                    for (ItemStack drop : leftover.values()) {
                        getWorld().dropItem(getLocation(), drop);
                    }
                }
            }
        }
        return new io.papermc.paper.entity.PlayerGiveResult() {
            @Override
            public @NotNull Collection<org.bukkit.entity.Item> drops() {
                return Collections.emptyList();
            }

            @Override
            public @NotNull Collection<ItemStack> leftovers() {
                return Collections.emptyList();
            }
        };
    }

    @Override
    public void openBook(@NotNull ItemStack book) {
    }

    @Override
    public void openSign(@NotNull Sign sign) {
        openSign(sign, Side.FRONT);
    }

    @Override
    public void openSign(@NotNull Sign sign, @NotNull Side side) {
    }

    @Override
    public void openVirtualSign(@NotNull Position position, @NotNull Side side) {
    }

    // --- View & Simulation Distance ---

    @Override
    public int getViewDistance() {
        return this.viewDistance > 0 ? this.viewDistance : PatchBukkitServer.getInstance().getViewDistance();
    }

    @Override
    public void setViewDistance(int viewDistance) {
        this.viewDistance = viewDistance;
    }

    @Override
    public int getSimulationDistance() {
        return this.simulationDistance > 0 ? this.simulationDistance : PatchBukkitServer.getInstance().getSimulationDistance();
    }

    @Override
    public void setSimulationDistance(int simulationDistance) {
        this.simulationDistance = simulationDistance;
    }

    @Override
    public int getSendViewDistance() {
        return this.sendViewDistance > 0 ? this.sendViewDistance : getViewDistance();
    }

    @Override
    public void setSendViewDistance(int viewDistance) {
        this.sendViewDistance = viewDistance;
    }

    @Override
    public int getClientViewDistance() {
        return getViewDistance();
    }

    @Override
    public boolean getAffectsSpawning() {
        return this.affectsSpawning;
    }

    @Override
    public void setAffectsSpawning(boolean affects) {
        this.affectsSpawning = affects;
    }

    // --- Profile & Chunks & Plugin Channels ---

    @Override
    public @NotNull com.destroystokyo.paper.profile.PlayerProfile getPlayerProfile() {
        return PatchBukkitServer.getInstance().createProfile(getUniqueId(), getName());
    }

    @Override
    public void setPlayerProfile(@NotNull com.destroystokyo.paper.profile.PlayerProfile profile) {
    }

    @Override
    public @NotNull Set<Long> getSentChunkKeys() {
        return Collections.emptySet();
    }

    @Override
    public @NotNull Set<org.bukkit.Chunk> getSentChunks() {
        return Collections.emptySet();
    }

    @Override
    public boolean isChunkSent(long chunkKey) {
        return true;
    }

    @Override
    public void sendPluginMessage(@NotNull Plugin source, @NotNull String channel, @NotNull byte[] message) {
        StandardMessenger.validatePluginMessage(PatchBukkitServer.getInstance().getMessenger(), source, channel, message);
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
        return Collections.unmodifiableSet(this.listeningChannels);
    }

    // --- Conversations ---

    @Override
    public boolean isConversing() {
        return false;
    }

    @Override
    public void acceptConversationInput(@NotNull String input) {
    }

    @Override
    public boolean beginConversation(@NotNull Conversation conversation) {
        return false;
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation) {
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation, @NotNull ConversationAbandonedEvent details) {
    }

    // --- Attack Cooldown & Period ---

    @Override
    public float getCooldownPeriod() {
        return 1.0f;
    }

    @Override
    public float getCooledAttackStrength(float adjustTicks) {
        return 1.0f;
    }

    @Override
    public void resetCooldown() {
    }

    // --- Chat completions ---

    @Override
    public void addCustomChatCompletions(@NotNull Collection<String> completions) {
    }

    @Override
    public void removeCustomChatCompletions(@NotNull Collection<String> completions) {
    }

    @Override
    public void setCustomChatCompletions(@NotNull Collection<String> completions) {
    }

    @Override
    public void addAdditionalChatCompletions(@NotNull Collection<String> completions) {
    }

    @Override
    public void removeAdditionalChatCompletions(@NotNull Collection<String> completions) {
    }

    // --- Locale ---

    @Override
    public @NotNull String getLocale() {
        return "en_us";
    }

    @Override
    public @Nullable Locale locale() {
        return Locale.US;
    }

    // --- Misc unimplemented/unsupported helpers ---

    @Override
    public void playEffect(@NotNull Location loc, @NotNull Effect effect, int data) {
        if (loc == null || effect == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playEffect(loc, effect, data);
    }

    @Override
    public <T> void playEffect(@NotNull Location loc, @NotNull Effect effect, @Nullable T data) {
        if (loc == null || effect == null || loc.getWorld() == null) {
            return;
        }
        loc.getWorld().playEffect(loc, effect, data);
    }

    @Override
    public void playNote(@NotNull Location loc, byte instrument, byte note) {
    }

    @Override
    public void playNote(@NotNull Location loc, @NotNull Instrument instrument, @NotNull Note note) {
    }

    @Override
    public void sendMap(@NotNull MapView map) {
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, 0, 0, 0, 0, null);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count) {
        spawnParticle(particle, x, y, z, count, 0, 0, 0, 0, null);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, @Nullable T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, 0, 0, 0, 0, data);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, @Nullable T data) {
        spawnParticle(particle, x, y, z, count, 0, 0, 0, 0, data);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, 0, null);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 0, null);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, @Nullable T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, 0, data);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, @Nullable T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 0, data);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, null);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data) {
        if (particle == null) {
            return;
        }
        try {
            NativeBridgeFfi.spawnParticle(SpawnParticleRequest.newBuilder()
                .setParticle(particle.name())
                .setX(x)
                .setY(y)
                .setZ(z)
                .setCount(count)
                .setOffsetX(offsetX)
                .setOffsetY(offsetY)
                .setOffsetZ(offsetZ)
                .setExtra(extra)
                .setPlayerUuid(BridgeUtils.convertUuid(getUniqueId()))
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data, boolean force) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
    }

    @Override
    public @NotNull AdvancementProgress getAdvancementProgress(@NotNull Advancement advancement) {
        String advancementId = advancement.getKey().asString();
        return new AdvancementProgress() {
            @Override
            public @NotNull Advancement getAdvancement() {
                return advancement;
            }

            private patchbukkit.advancement.AdvancementProgressResponse fetch() {
                try {
                    var resp = NativeBridgeFfi.getAdvancementProgress(
                        patchbukkit.advancement.AdvancementProgressRequest.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(getUniqueId()))
                            .setAdvancementId(advancementId)
                            .build());
                    if (resp != null && resp.getExists()) {
                        return resp;
                    }
                } catch (Throwable ignored) {}
                return null;
            }

            @Override
            public boolean isDone() {
                var resp = fetch();
                return resp != null && resp.getDone();
            }

            @Override
            public boolean awardCriteria(@NotNull String criteria) {
                try {
                    var resp = NativeBridgeFfi.awardAdvancementCriterion(
                        patchbukkit.advancement.AwardCriterionRequest.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(getUniqueId()))
                            .setAdvancementId(advancementId)
                            .setCriterion(criteria)
                            .build());
                    return resp != null && resp.getAwarded();
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public boolean revokeCriteria(@NotNull String criteria) {
                try {
                    var resp = NativeBridgeFfi.revokeAdvancementCriterion(
                        patchbukkit.advancement.RevokeCriterionRequest.newBuilder()
                            .setPlayerUuid(BridgeUtils.convertUuid(getUniqueId()))
                            .setAdvancementId(advancementId)
                            .setCriterion(criteria)
                            .build());
                    return resp != null && resp.getRevoked();
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public @Nullable Date getDateAwarded(@NotNull String criteria) {
                var resp = fetch();
                if (resp != null && resp.getAwardDatesMillisMap().containsKey(criteria)) {
                    return new Date(resp.getAwardDatesMillisMap().get(criteria));
                }
                return null;
            }

            @Override
            public @NotNull Collection<String> getRemainingCriteria() {
                var resp = fetch();
                return resp != null ? List.copyOf(resp.getRemainingCriteriaList()) : List.of();
            }

            @Override
            public @NotNull Collection<String> getAwardedCriteria() {
                var resp = fetch();
                return resp != null ? List.copyOf(resp.getAwardedCriteriaList()) : List.of();
            }
        };
    }

    @Override
    public io.papermc.paper.connection.PlayerGameConnection getConnection() {
        return null;
    }

    private int deathScreenScore = 0;

    @Override
    public int getDeathScreenScore() {
        return this.deathScreenScore;
    }

    @Override
    public void setDeathScreenScore(int score) {
        this.deathScreenScore = score;
    }

    @Override
    public void sendEntityEffect(@NotNull org.bukkit.EntityEffect effect, @NotNull Entity target) {
    }

    private long lastActionTime = System.currentTimeMillis();

    @Override
    public @NotNull java.time.Duration getIdleDuration() {
        return java.time.Duration.ofMillis(System.currentTimeMillis() - this.lastActionTime);
    }

    @Override
    public void resetIdleDuration() {
        this.lastActionTime = System.currentTimeMillis();
    }

    @Override
    public void lookAt(@NotNull Entity entity, @NotNull io.papermc.paper.entity.LookAnchor playerAnchor, @NotNull io.papermc.paper.entity.LookAnchor entityAnchor) {
        if (entity != null) {
            lookAt(entity.getLocation().getX(), entity.getLocation().getY(), entity.getLocation().getZ(), playerAnchor);
        }
    }

    @Override
    public void sendOpLevel(byte level) {
        setOp(level > 0);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @NotNull T getClientOption(@NotNull com.destroystokyo.paper.ClientOption<T> type) {
        if (type == com.destroystokyo.paper.ClientOption.SKIN_PARTS) {
            return (T) com.destroystokyo.paper.SkinParts.allParts();
        } else if (type == com.destroystokyo.paper.ClientOption.CHAT_COLORS_ENABLED) {
            return (T) Boolean.TRUE;
        } else if (type == com.destroystokyo.paper.ClientOption.CHAT_VISIBILITY) {
            return (T) com.destroystokyo.paper.ClientOption.ChatVisibility.FULL;
        } else if (type == com.destroystokyo.paper.ClientOption.MAIN_HAND) {
            return (T) getMainHand();
        } else if (type == com.destroystokyo.paper.ClientOption.VIEW_DISTANCE) {
            return (T) Integer.valueOf(getClientViewDistance());
        } else if (type == com.destroystokyo.paper.ClientOption.LOCALE) {
            return (T) getLocale();
        } else if (type == com.destroystokyo.paper.ClientOption.TEXT_FILTERING_ENABLED) {
            return (T) Boolean.FALSE;
        } else if (type == com.destroystokyo.paper.ClientOption.ALLOW_SERVER_LISTINGS) {
            return (T) Boolean.TRUE;
        }
        return (T) Boolean.FALSE;
    }

    @Override
    public boolean isAllowingServerListings() {
        return true;
    }

    @Override
    public void updateCommands() {
    }

    @Override
    public void setRotation(@NotNull io.papermc.paper.math.Angle yaw, @NotNull io.papermc.paper.math.Angle pitch) {
        setRotation(yaw.degrees(), pitch.degrees());
    }

    private Entity spectatorTarget = null;

    @Override
    public @Nullable Entity getSpectatorTarget() {
        return this.spectatorTarget;
    }

    @Override
    public void setSpectatorTarget(@Nullable Entity entity) {
        this.spectatorTarget = entity;
    }

    private GameMode previousGameMode = null;

    @Override
    public @Nullable GameMode getPreviousGameMode() {
        return this.previousGameMode;
    }

    @Override
    public void setGameMode(@NotNull GameMode mode) {
        this.previousGameMode = getGameMode();
        super.setGameMode(mode);
    }

    @Override
    public void sendLinks(@NotNull ServerLinks links) {
    }

    @Override
    public void sendPotionEffectChange(@NotNull org.bukkit.entity.LivingEntity entity, @NotNull org.bukkit.potion.PotionEffect effect) {
    }

    @Override
    public void sendPotionEffectChangeRemove(@NotNull LivingEntity entity, @NotNull org.bukkit.potion.PotionEffectType type) {
    }

    @Override
    public void sendEquipmentChange(@NotNull LivingEntity entity, @NotNull EquipmentSlot slot, @Nullable ItemStack item) {
    }

    @Override
    public void sendEquipmentChange(@NotNull LivingEntity entity, @NotNull Map<EquipmentSlot, @Nullable ItemStack> items) {
    }

    @Override
    public boolean breakBlock(@NotNull Block block) {
        return block.breakNaturally(getInventory().getItemInMainHand());
    }

    @Override
    public @NotNull org.bukkit.Input getCurrentInput() {
        return new org.bukkit.Input() {
            @Override
            public boolean isForward() { return false; }
            @Override
            public boolean isBackward() { return false; }
            @Override
            public boolean isLeft() { return false; }
            @Override
            public boolean isRight() { return false; }
            @Override
            public boolean isJump() { return false; }
            @Override
            public boolean isSneak() { return isSneaking(); }
            @Override
            public boolean isSprint() { return isSprinting(); }
        };
    }

    @Override
    public @NotNull Collection<EnderPearl> getEnderPearls() {
        return Collections.emptyList();
    }

    private boolean sleepingIgnored = false;

    @Override
    public boolean isSleepingIgnored() {
        return this.sleepingIgnored;
    }

    @Override
    public void setSleepingIgnored(boolean isSleeping) {
        this.sleepingIgnored = isSleeping;
    }

    @Override
    public void loadData() {
    }

    @Override
    public void saveData() {
    }
}
