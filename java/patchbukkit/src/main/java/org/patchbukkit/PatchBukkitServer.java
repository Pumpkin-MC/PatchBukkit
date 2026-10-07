package org.patchbukkit;

import com.destroystokyo.paper.entity.ai.MobGoals;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.common.base.Preconditions;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.datapack.DatapackManager;
import io.papermc.paper.math.Position;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.BanList;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.ServerLinks;
import org.bukkit.ServerTickManager;
import org.bukkit.StructureType;
import org.bukkit.Tag;
import org.bukkit.UnsafeValues;
import org.bukkit.Warning.WarningState;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityFactory;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.help.HelpMap;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.Recipe;
import org.bukkit.loot.LootTable;
import org.bukkit.map.MapCursor.Type;
import org.bukkit.map.MapView;
import org.bukkit.packs.ResourcePack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.potion.PotionBrewer;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.CachedServerIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.ban.PatchBukkitBanList;
import org.patchbukkit.PatchBukkitBlockData;
import org.patchbukkit.boss.PatchBukkitBossBar;
import org.patchbukkit.boss.PatchBukkitKeyedBossBar;
import org.patchbukkit.command.PatchBukkitCommandMap;
import org.patchbukkit.command.PatchBukkitConsoleCommandSender;
import org.patchbukkit.datapack.PatchBukkitDatapackManager;
import org.patchbukkit.entity.PatchBukkitEntityFactory;
import org.patchbukkit.entity.PatchBukkitMobGoals;
import org.patchbukkit.entity.PatchBukkitOfflinePlayer;
import org.patchbukkit.entity.PatchBukkitPlayer;
import org.patchbukkit.events.PatchBukkitEventManager;
import org.patchbukkit.help.PatchBukkitHelpMap;
import org.patchbukkit.inventory.PatchBukkitInventory;
import org.patchbukkit.inventory.PatchBukkitItemFactory;
import org.patchbukkit.inventory.PatchBukkitMerchant;
import org.patchbukkit.map.PatchBukkitMapView;
import org.patchbukkit.messaging.PatchBukkitMessenger;
import org.patchbukkit.potion.PatchBukkitPotionBrewer;
import org.patchbukkit.profile.PatchBukkitPlayerProfile;
import org.patchbukkit.scheduler.PatchBukkitAsyncScheduler;
import org.patchbukkit.scheduler.PatchBukkitGlobalRegionScheduler;
import org.patchbukkit.scheduler.PatchBukkitRegionScheduler;
import org.patchbukkit.scheduler.PatchBukkitScheduler;
import org.patchbukkit.scoreboard.PatchBukkitScoreboardManager;
import org.patchbukkit.structure.PatchBukkitStructureManager;
import org.patchbukkit.tick.PatchBukkitServerTickManager;
import org.patchbukkit.versioning.Versioning;
import org.patchbukkit.PatchBukkitLegacy;
import org.patchbukkit.world.PatchBukkitWorld;
import org.patchbukkit.world.PatchBukkitWorldBorder;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.common.EmptyRequest;
import patchbukkit.log.LogLevel;
import patchbukkit.log.SendLogRequest;
import patchbukkit.server.CreateWorldRequest;
import patchbukkit.server.GetOperatorsResponse;
import patchbukkit.server.GetWhitelistResponse;
import patchbukkit.server.OperatorEntryProto;
import patchbukkit.server.ServerInfoResponse;
import patchbukkit.server.ServerTickInfoResponse;
import patchbukkit.server.SetOperatorRequest;
import patchbukkit.server.SetServerDefaultGamemodeRequest;
import patchbukkit.server.SetServerIdleTimeoutRequest;
import patchbukkit.server.SetServerMaxPlayersRequest;
import patchbukkit.server.SetServerMotdRequest;
import patchbukkit.server.SetServerWhitelistEnforcedRequest;
import patchbukkit.server.SetServerWhitelistRequest;
import patchbukkit.server.SetWhitelistPlayerRequest;
import patchbukkit.server.ShutdownServerRequest;
import patchbukkit.server.UnloadWorldRequest;
import patchbukkit.server.WhitelistEntryProto;

@SuppressWarnings({ "deprecation", "removal", "unchecked" })
public class PatchBukkitServer implements Server {
    private static final Logger logger = Logger.getLogger("Minecraft");
    private static volatile PatchBukkitServer INSTANCE;

    public PatchBukkitServer() {
        INSTANCE = this;
        try {
            this.serverName = io.papermc.paper.ServerBuildInfo.buildInfo().brandName();
        } catch (Throwable ignored) {}
        syncServerInfo();
    }

    public static PatchBukkitServer getInstance() {
        if (INSTANCE == null) {
            synchronized (PatchBukkitServer.class) {
                if (INSTANCE == null) {
                    new PatchBukkitServer();
                }
            }
        }
        return INSTANCE;
    }

    static {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);
            Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
        } catch (Throwable ignored) {}
        ensureGlobalConfiguration();
        ensureCraftRegistry();
    }

    public static void ensureGlobalConfiguration() {
        try {
            Class<?> globalConfigClass = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            java.lang.reflect.Method getMethod = globalConfigClass.getMethod("get");
            if (getMethod.invoke(null) != null) {
                return;
            }

            // Ensure Minecraft Bootstrap is executed before touching any Paper/Mojang configurations
            try {
                Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
                sharedConstants.getMethod("tryDetectVersion").invoke(null);
                Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
                bootstrap.getMethod("bootStrap").invoke(null);
            } catch (Throwable ignored) {}

            // 1. Try initializing with PaperConfigurations
            try {
                Class<?> paperConfigsClass = Class.forName("io.papermc.paper.configuration.PaperConfigurations");
                java.nio.file.Path configDir = java.nio.file.Path.of("config");
                if (!java.nio.file.Files.exists(configDir)) {
                    java.nio.file.Files.createDirectories(configDir);
                }
                Object configs = paperConfigsClass.getConstructor(java.nio.file.Path.class).newInstance(configDir);
                Class<?> registryAccessClass = Class.forName("net.minecraft.core.RegistryAccess");
                java.lang.reflect.Field emptyField = registryAccessClass.getField("EMPTY");
                Object emptyRegistry = emptyField.get(null);

                java.lang.reflect.Method initMethod = paperConfigsClass.getMethod("initializeGlobalConfiguration", registryAccessClass);
                initMethod.invoke(configs, emptyRegistry);
            } catch (Throwable t) {
                // 2. Fallback: instantiate GlobalConfiguration and populate inner configuration parts
                try {
                    Object cfg = globalConfigClass.getConstructor().newInstance();
                    for (java.lang.reflect.Field field : globalConfigClass.getFields()) {
                        if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && field.get(cfg) == null) {
                            Class<?> fieldType = field.getType();
                            try {
                                java.lang.reflect.Constructor<?> ctor = fieldType.getDeclaredConstructor(globalConfigClass);
                                ctor.setAccessible(true);
                                field.set(cfg, ctor.newInstance(cfg));
                            } catch (NoSuchMethodException e) {
                                try {
                                    java.lang.reflect.Constructor<?> ctor = fieldType.getDeclaredConstructor();
                                    ctor.setAccessible(true);
                                    field.set(cfg, ctor.newInstance());
                                } catch (Throwable ignored) {}
                            }
                        }
                    }
                    java.lang.reflect.Method setMethod = globalConfigClass.getDeclaredMethod("set", globalConfigClass);
                    setMethod.setAccessible(true);
                    setMethod.invoke(null, cfg);
                } catch (Throwable t2) {
                    logger.log(Level.WARNING, "[PatchBukkit] Failed to fallback-initialize GlobalConfiguration", t2);
                }
            }
        } catch (ClassNotFoundException ignored) {
            // Not a Paper environment
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PatchBukkit] Unexpected error initializing GlobalConfiguration", t);
        }
    }

    public static synchronized void ensureCraftRegistry() {
        try {
            if (org.bukkit.craftbukkit.CraftRegistry.getMinecraftRegistry() != null) {
                return;
            }
            net.minecraft.core.RegistryAccess.Frozen frozenAccess =
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
            org.bukkit.craftbukkit.CraftRegistry.setMinecraftRegistry(frozenAccess);
            return;
        } catch (IllegalStateException ignored) {
            return;
        } catch (Throwable ignored) {}

        try {
            Class<?> craftRegistryClass = Class.forName("org.bukkit.craftbukkit.CraftRegistry");
            java.lang.reflect.Method getMinecraftRegistryMethod = craftRegistryClass.getMethod("getMinecraftRegistry");
            if (getMinecraftRegistryMethod.invoke(null) != null) {
                return;
            }

            Class<?> regAccessClass = Class.forName("net.minecraft.core.RegistryAccess");
            Class<?> builtInRegsClass = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
            Object rootRegistry = builtInRegsClass.getField("REGISTRY").get(null);
            Object frozenAccess = regAccessClass.getMethod("fromRegistryOfRegistries", Class.forName("net.minecraft.core.Registry")).invoke(null, rootRegistry);
            craftRegistryClass.getMethod("setMinecraftRegistry", regAccessClass).invoke(null, frozenAccess);
        } catch (IllegalStateException ignored) {
            // Already set by concurrent thread
        } catch (Throwable t) {
            if (logger != null) {
                logger.log(Level.WARNING, "[PatchBukkit] Failed to initialize CraftRegistry", t);
            } else {
                System.err.println("[PatchBukkit] Failed to initialize CraftRegistry: " + t.getMessage());
            }
        }
    }

    private static volatile net.minecraft.server.network.ServerConnectionListener SERVER_CONNECTION;
    private static volatile net.minecraft.server.dedicated.DedicatedServer DEDICATED_SERVER;
    private static volatile net.minecraft.server.dedicated.DedicatedPlayerList DEDICATED_PLAYER_LIST;
    private static volatile org.bukkit.craftbukkit.CraftServer CRAFT_SERVER;

    public static net.minecraft.server.network.ServerConnectionListener getServerConnection() {
        if (SERVER_CONNECTION == null) {
            initMinecraftServerInstances();
        }
        return SERVER_CONNECTION;
    }

    public static net.minecraft.server.dedicated.DedicatedServer getDedicatedServer() {
        if (DEDICATED_SERVER == null) {
            initMinecraftServerInstances();
        }
        return DEDICATED_SERVER;
    }

    public static net.minecraft.server.dedicated.DedicatedPlayerList getDedicatedPlayerList() {
        if (DEDICATED_PLAYER_LIST == null) {
            initMinecraftServerInstances();
        }
        return DEDICATED_PLAYER_LIST;
    }

    public static org.bukkit.craftbukkit.CraftServer getCraftServer() {
        if (CRAFT_SERVER == null) {
            initMinecraftServerInstances();
        }
        return CRAFT_SERVER;
    }

    private static synchronized void initMinecraftServerInstances() {
        if (SERVER_CONNECTION != null) return;

        // Preferred path: the real Paper server booted headlessly (see HeadlessPaperServer).
        net.minecraft.server.dedicated.DedicatedServer headless = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
        if (headless != null) {
            DEDICATED_SERVER = headless;
            DEDICATED_PLAYER_LIST = (net.minecraft.server.dedicated.DedicatedPlayerList) headless.getPlayerList();
            CRAFT_SERVER = headless.server;
            SERVER_CONNECTION = headless.getConnection();
            return;
        }

        // Fallback (e.g. unit tests without a headless boot): hand-assembled stubs.
        try {
            ensureGlobalConfiguration();
            ensureCraftRegistry();

            Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            unsafeField.setAccessible(true);
            sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);

            Class<?> dsClass = Class.forName("net.minecraft.server.dedicated.DedicatedServer");
            DEDICATED_SERVER = (net.minecraft.server.dedicated.DedicatedServer) unsafe.allocateInstance(dsClass);
            SERVER_CONNECTION = new net.minecraft.server.network.ServerConnectionListener(DEDICATED_SERVER);

            for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
                if (f.getType().equals(net.minecraft.server.network.ServerConnectionListener.class)) {
                    f.setAccessible(true);
                    f.set(DEDICATED_SERVER, SERVER_CONNECTION);
                    break;
                }
            }

            // Set static MinecraftServer.SERVER singleton
            for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && net.minecraft.server.MinecraftServer.class.isAssignableFrom(f.getType())) {
                    try {
                        unsafe.putObject(unsafe.staticFieldBase(f), unsafe.staticFieldOffset(f), DEDICATED_SERVER);
                    } catch (Throwable t) {
                        try {
                            f.setAccessible(true);
                            f.set(null, DEDICATED_SERVER);
                        } catch (Throwable ignored) {}
                    }
                }
            }

            Class<?> plClass = Class.forName("net.minecraft.server.dedicated.DedicatedPlayerList");
            DEDICATED_PLAYER_LIST = (net.minecraft.server.dedicated.DedicatedPlayerList) unsafe.allocateInstance(plClass);

            Field playersField = null;
            Field playersByUUIDField = null;
            Class<?> cur = DEDICATED_PLAYER_LIST.getClass();
            while (cur != null) {
                for (Field f : cur.getDeclaredFields()) {
                    if (f.getName().equals("players")) {
                        playersField = f;
                    } else if (f.getName().equals("playersByUUID")) {
                        playersByUUIDField = f;
                    }
                }
                cur = cur.getSuperclass();
            }
            if (playersField != null) {
                playersField.setAccessible(true);
                playersField.set(DEDICATED_PLAYER_LIST, new java.util.concurrent.CopyOnWriteArrayList<>());
            }
            if (playersByUUIDField != null) {
                playersByUUIDField.setAccessible(true);
                playersByUUIDField.set(DEDICATED_PLAYER_LIST, new java.util.concurrent.ConcurrentHashMap<>());
            }

            for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
                if (net.minecraft.server.players.PlayerList.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    f.set(DEDICATED_SERVER, DEDICATED_PLAYER_LIST);
                    break;
                }
            }

            Class<?> pldClass = Class.forName("net.minecraft.world.level.storage.PrimaryLevelData");
            net.minecraft.world.level.storage.PrimaryLevelData pld = (net.minecraft.world.level.storage.PrimaryLevelData) unsafe.allocateInstance(pldClass);
            for (Field f : pldClass.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    if (f.getName().equals("respawnDimension")) {
                        f.set(pld, net.minecraft.world.level.Level.OVERWORLD);
                    } else if (f.getName().equals("respawnData")) {
                        f.set(pld, net.minecraft.world.level.storage.LevelData.RespawnData.DEFAULT);
                    }
                } catch (Throwable ignored) {}
            }

            for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    if (f.getName().equals("worldData") || net.minecraft.world.level.storage.WorldData.class.isAssignableFrom(f.getType())) {
                        f.set(DEDICATED_SERVER, pld);
                    } else if (f.getName().equals("effectiveRespawnData") || f.getType().equals(net.minecraft.world.level.storage.LevelData.RespawnData.class)) {
                        f.set(DEDICATED_SERVER, net.minecraft.world.level.storage.LevelData.RespawnData.DEFAULT);
                    } else if (f.getName().equals("levels")) {
                        f.set(DEDICATED_SERVER, new java.util.HashMap<>());
                    }
                } catch (Throwable ignored) {}
            }

            Class<?> csClass = Class.forName("org.bukkit.craftbukkit.CraftServer");
            CRAFT_SERVER = (org.bukkit.craftbukkit.CraftServer) unsafe.allocateInstance(csClass);
            for (Field f : csClass.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    if (f.getName().equals("console")) {
                        f.set(CRAFT_SERVER, DEDICATED_SERVER);
                    } else if (f.getName().equals("playerList")) {
                        f.set(CRAFT_SERVER, DEDICATED_PLAYER_LIST);
                    } else if (f.getName().equals("serverName")) {
                        f.set(CRAFT_SERVER, "Paper");
                    } else if (f.getName().equals("serverVersion")) {
                        f.set(CRAFT_SERVER, org.patchbukkit.versioning.Versioning.getBukkitVersion());
                    } else if (f.getName().equals("bukkitVersion")) {
                        f.set(CRAFT_SERVER, org.patchbukkit.versioning.Versioning.getBukkitVersion());
                    } else if (f.getName().equals("logger")) {
                        f.set(CRAFT_SERVER, logger);
                    } else if (f.getName().equals("worlds")) {
                        f.set(CRAFT_SERVER, new java.util.concurrent.ConcurrentHashMap<>());
                    } else if (f.getName().equals("offlinePlayers")) {
                        f.set(CRAFT_SERVER, new java.util.concurrent.ConcurrentHashMap<>());
                    }
                } catch (Throwable ignored) {}
            }

            for (Field f : net.minecraft.server.MinecraftServer.class.getDeclaredFields()) {
                if (f.getName().equals("server") || f.getType().getName().contains("CraftServer")) {
                    f.setAccessible(true);
                    f.set(DEDICATED_SERVER, CRAFT_SERVER);
                    break;
                }
            }
        } catch (Throwable t) {
            logger.log(Level.WARNING, "[PatchBukkit] Failed to initialize DedicatedServer instances", t);
        }
    }

    public static PatchBukkitServer initServer() {
        ensureGlobalConfiguration();
        ensureCraftRegistry();
        getServerConnection();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            logger.log(Level.SEVERE, "Uncaught exception in thread " + thread.getName(), throwable);
        });
        PatchBukkitServer server = getInstance();
        if (org.bukkit.Bukkit.getServer() == null) {
            org.bukkit.Bukkit.setServer(server);
        }
        return server;
    }

    public final net.minecraft.server.dedicated.DedicatedServer console = getDedicatedServer();

    public net.minecraft.server.dedicated.DedicatedServer getServer() {
        return console;
    }

    public net.minecraft.server.dedicated.DedicatedPlayerList getHandle() {
        return getDedicatedPlayerList();
    }

    public net.minecraft.server.network.ServerConnectionListener getConnection() {
        return getServerConnection();
    }

    private String serverName = "Pumpkin";
    private String bukkitVersion = Versioning.getBukkitVersion();
    private String minecraftVersion = Versioning.getCurrentApiVersion();
    private String serverVersion = "git-Paper-117 (MC: " + this.minecraftVersion + ")";
    public SimpleCommandMap commandMap = new PatchBukkitCommandMap(this);
    public BukkitScheduler scheduler = new PatchBukkitScheduler();
    public PatchBukkitPluginManager pluginManager = new PatchBukkitPluginManager(this);
    public ServicesManager servicesManager = new PatchBukkitServicesManager();

    private final Map<UUID, Player> onlinePlayers = new ConcurrentHashMap<>();
    private final Map<String, Player> onlinePlayersByName = new ConcurrentHashMap<>();

    private final Set<UUID> operatorUuids = Collections.synchronizedSet(new HashSet<>());
    private final Set<String> operatorNames = Collections.synchronizedSet(new HashSet<>());

    private final ServerTickManager tickManager = new PatchBukkitServerTickManager();
    private final PatchBukkitBanList<String> nameBanList = new PatchBukkitBanList<>("NAME");
    private final PatchBukkitBanList<InetAddress> ipBanList = new PatchBukkitBanList<>("IP");
    private final PatchBukkitBanList<PlayerProfile> profileBanList = new PatchBukkitBanList<>("PROFILE");
    private final ScoreboardManager scoreboardManager = new PatchBukkitScoreboardManager();
    private final DatapackManager datapackManager = new PatchBukkitDatapackManager();
    private final StructureManager structureManager = new PatchBukkitStructureManager();
    private final PotionBrewer potionBrewer = PatchBukkitPotionBrewer.INSTANCE;
    private final MobGoals mobGoals = PatchBukkitMobGoals.INSTANCE;
    private final EntityFactory entityFactory = PatchBukkitEntityFactory.INSTANCE;
    private final RegionScheduler regionScheduler = new PatchBukkitRegionScheduler();
    private final AsyncScheduler asyncScheduler = new PatchBukkitAsyncScheduler();
    private final GlobalRegionScheduler globalRegionScheduler = new PatchBukkitGlobalRegionScheduler();

    private final Map<NamespacedKey, KeyedBossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<Integer, MapView> maps = new ConcurrentHashMap<>();
    private final AtomicInteger mapCounter = new AtomicInteger(0);
    private final List<Recipe> recipes = new CopyOnWriteArrayList<>();

    private int spawnRadius = 16;
    private int idleTimeout = 0;
    private int pauseWhenEmptyTime = 60;
    private GameMode defaultGameMode = GameMode.SURVIVAL;
    private boolean whitelistEnforced = false;
    private boolean whitelistEnabled = false;
    private String motd = "A PatchBukkit Server";
    private int maxPlayers = 20;
    private int port = 25565;
    private String ip = "127.0.0.1";
    private int viewDistance = 10;
    private int simulationDistance = 10;
    private boolean allowFlight = true;
    private boolean allowNether = true;
    private boolean allowEnd = true;
    private boolean onlineMode = true;
    private boolean hardcore = false;
    private boolean pvp = true;
    private String shutdownMessage = "Server closed";
    private boolean isStopping = false;

    private final Spigot spigot = new Spigot() {
        @Override
        public void restart() {
            PatchBukkitServer.this.restart();
        }
    };

    private final ServerLinks serverLinks = new ServerLinks() {
        private final List<ServerLink> links = new ArrayList<>();

        @Override
        public @Nullable ServerLink getLink(@NotNull Type type) {
            return null;
        }

        @Override
        public @NotNull List<ServerLink> getLinks() {
            return Collections.unmodifiableList(this.links);
        }

        @Override
        public @NotNull ServerLink setLink(@NotNull Type type, @NotNull URI uri) {
            return addLink(type.name(), uri);
        }

        @Override
        public @NotNull ServerLink addLink(@NotNull Type type, @NotNull URI uri) {
            return addLink(type.name(), uri);
        }

        @Override
        public @NotNull ServerLink addLink(@NotNull Component component, @NotNull URI uri) {
            return addLink(PlainTextComponentSerializer.plainText().serialize(component), uri);
        }

        @Override
        public @NotNull ServerLink addLink(@NotNull String string, @NotNull URI uri) {
            ServerLink link = new ServerLink() {
                @Override public @Nullable Type getType() { return null; }
                @Override public @NotNull Component displayName() { return Component.text(string); }
                @Override public @NotNull String getDisplayName() { return string; }
                @Override public @NotNull URI getUrl() { return uri; }
            };
            this.links.add(link);
            return link;
        }

        @Override
        public boolean removeLink(@NotNull ServerLink serverLink) {
            return this.links.remove(serverLink);
        }

        @Override
        public @NotNull ServerLinks copy() {
            return this;
        }
    };

    public void syncServerInfo() {
        try {
            ServerInfoResponse info = NativeBridgeFfi.getServerInfo(EmptyRequest.getDefaultInstance());
            if (info != null) {
                if (info.getServerName() != null && !info.getServerName().isEmpty()) {
                    this.serverName = info.getServerName();
                }
                if (info.getBukkitVersion() != null && !info.getBukkitVersion().isEmpty()) {
                    this.bukkitVersion = info.getBukkitVersion();
                }
                if (info.getMinecraftVersion() != null && !info.getMinecraftVersion().isEmpty()) {
                    this.minecraftVersion = info.getMinecraftVersion();
                }
                if (info.getVersion() != null && !info.getVersion().isEmpty()) {
                    this.serverVersion = "git-Paper-" + info.getVersion() + " (MC: " + this.minecraftVersion + ")";
                }
                this.motd = info.getMotd();
                this.ip = info.getIp();
                this.port = info.getPort();
                this.maxPlayers = info.getMaxPlayers();
                this.viewDistance = info.getViewDistance();
                this.simulationDistance = info.getSimulationDistance();
                this.allowFlight = info.getAllowFlight();
                this.allowNether = info.getAllowNether();
                this.allowEnd = info.getAllowEnd();
                this.onlineMode = info.getOnlineMode();
                this.hardcore = info.getHardcore();
                this.pvp = info.getPvp();
                this.whitelistEnabled = info.getHasWhitelist();
                this.whitelistEnforced = info.getIsWhitelistEnforced();
                this.idleTimeout = info.getIdleTimeout();
                try {
                    this.defaultGameMode = GameMode.valueOf(info.getDefaultGamemode().toUpperCase());
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    public boolean isOp(UUID uuid, String name) {
        if (uuid != null && operatorUuids.contains(uuid)) return true;
        if (name != null && operatorNames.contains(name.toLowerCase())) return true;
        try {
            GetOperatorsResponse ops = NativeBridgeFfi.getOperators(EmptyRequest.getDefaultInstance());
            if (ops != null) {
                for (OperatorEntryProto op : ops.getOperatorsList()) {
                    if (uuid != null && op.hasUuid() && op.getUuid().getValue().equalsIgnoreCase(uuid.toString())) {
                        operatorUuids.add(uuid);
                        return true;
                    }
                    if (name != null && op.getName().equalsIgnoreCase(name)) {
                        operatorNames.add(name.toLowerCase());
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public void setOperator(UUID uuid, String name, boolean value) {
        if (uuid != null) {
            if (value) operatorUuids.add(uuid); else operatorUuids.remove(uuid);
        }
        if (name != null) {
            if (value) operatorNames.add(name.toLowerCase()); else operatorNames.remove(name.toLowerCase());
        }
        try {
            var req = SetOperatorRequest.newBuilder()
                .setIsOp(value)
                .setLevel(4);
            if (uuid != null) {
                req.setUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()).build());
            }
            if (name != null) {
                req.setName(name);
            }
            NativeBridgeFfi.setOperator(req.build());
        } catch (Throwable ignored) {}
    }

    public static final PrintStream ORIGINAL_OUT = System.out;
    public static final PrintStream ORIGINAL_ERR = System.err;
    private static final ThreadLocal<Boolean> IN_LOGGING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static boolean isLogging() {
        return IN_LOGGING.get();
    }

    public static void setLogging(boolean logging) {
        IN_LOGGING.set(logging);
    }

    static {
        configureLogging();
    }

    public static final Map<Level, LogLevel> LEVEL_MAP = Map.of(
            Level.SEVERE,  LogLevel.SEVERE,
            Level.WARNING, LogLevel.WARNING,
            Level.INFO,    LogLevel.INFO,
            Level.CONFIG,  LogLevel.CONFIG,
            Level.FINE,    LogLevel.FINE,
            Level.FINER,   LogLevel.FINER,
            Level.FINEST,  LogLevel.FINEST
    );

    private static void configureLogging() {
        configureLog4j();
        configureJulLogger();
        redirectSystemStreams();
    }

    private static void configureLog4j() {
        try {
            org.apache.logging.log4j.core.LoggerContext context =
                    (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

            org.patchbukkit.log.NativeBridgeLog4jAppender appender =
                    org.patchbukkit.log.NativeBridgeLog4jAppender.create("NativeBridge");
            appender.start();
            config.addAppender(appender);

            org.apache.logging.log4j.core.config.LoggerConfig rootConfig = config.getRootLogger();
            rootConfig.removeAppender("TerminalConsole");
            rootConfig.removeAppender("ServerGuiConsole");
            rootConfig.removeAppender("Async");

            org.apache.logging.log4j.Level targetLevel = getEffectiveLog4jLevel();
            rootConfig.addAppender(appender, targetLevel, null);
            rootConfig.setLevel(targetLevel);
            context.updateLoggers();
        } catch (Throwable t) {
            ORIGINAL_ERR.println("[PatchBukkit] Failed to initialize Log4j native bridge appender: " + t.getMessage());
        }
    }

    public static org.apache.logging.log4j.Level getEffectiveLog4jLevel() {
        try {
            var cfg = NativeBridgeFfi.getPatchBukkitConfig(patchbukkit.common.EmptyRequest.newBuilder().build());
            if (cfg != null) {
                if (cfg.getDebugBridge()) {
                    return org.apache.logging.log4j.Level.ALL;
                }
                String level = cfg.getLogLevel();
                if (level != null && !level.isBlank()) {
                    return switch (level.toLowerCase(Locale.ENGLISH).trim()) {
                        case "trace" -> org.apache.logging.log4j.Level.TRACE;
                        case "debug" -> org.apache.logging.log4j.Level.DEBUG;
                        case "warn", "warning" -> org.apache.logging.log4j.Level.WARN;
                        case "error", "severe" -> org.apache.logging.log4j.Level.ERROR;
                        case "off" -> org.apache.logging.log4j.Level.OFF;
                        case "all" -> org.apache.logging.log4j.Level.ALL;
                        default -> org.apache.logging.log4j.Level.INFO;
                    };
                }
            }
        } catch (Throwable ignored) {}
        return org.apache.logging.log4j.Level.INFO;
    }

    public static Level getEffectiveJulLevel() {
        try {
            var cfg = NativeBridgeFfi.getPatchBukkitConfig(patchbukkit.common.EmptyRequest.newBuilder().build());
            if (cfg != null) {
                if (cfg.getDebugBridge()) {
                    return Level.ALL;
                }
                String level = cfg.getLogLevel();
                if (level != null && !level.isBlank()) {
                    return switch (level.toLowerCase(Locale.ENGLISH).trim()) {
                        case "trace" -> Level.FINEST;
                        case "debug" -> Level.FINE;
                        case "warn", "warning" -> Level.WARNING;
                        case "error", "severe" -> Level.SEVERE;
                        case "off" -> Level.OFF;
                        case "all" -> Level.ALL;
                        default -> Level.INFO;
                    };
                }
            }
        } catch (Throwable ignored) {}
        return Level.INFO;
    }

    private static void configureJulLogger() {
        Logger root = Logger.getLogger("");
        root.setUseParentHandlers(false);
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        root.setLevel(getEffectiveJulLevel());
        try {
            root.addHandler(new org.bukkit.craftbukkit.util.ForwardLogHandler());
        } catch (Throwable t) {
            ORIGINAL_ERR.println("[PatchBukkit] Failed to install ForwardLogHandler, using fallback: " + t.getMessage());
            root.addHandler(new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord record) {
                    if (record == null) return;
                    if (IN_LOGGING.get()) return;
                    IN_LOGGING.set(true);
                    try {
                        LogLevel logLevel = LEVEL_MAP.getOrDefault(record.getLevel(), LogLevel.INFO);
                        String message = formatLogRecord(record);
                        String loggerName = record.getLoggerName() != null ? record.getLoggerName() : "";
                        NativeBridgeFfi.sendLog(
                                SendLogRequest.newBuilder()
                                        .setLevel(logLevel)
                                        .setMessage(message)
                                        .setLoggerName(loggerName)
                                        .build()
                        );
                    } catch (Throwable ignored) {
                    } finally {
                        IN_LOGGING.set(false);
                    }
                }
                @Override public void flush() {}
                @Override public void close() {}
            });
        }
    }

    public static String formatLogRecord(java.util.logging.LogRecord record) {
        String msg = record.getMessage();
        if (msg == null) {
            msg = "";
        } else if (record.getResourceBundle() != null) {
            try {
                msg = record.getResourceBundle().getString(msg);
            } catch (Exception ignored) {}
        }
        Object[] params = record.getParameters();
        if (params != null && params.length > 0 && msg.contains("{0}")) {
            try {
                msg = MessageFormat.format(msg, params);
            } catch (Exception ignored) {}
        }
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            if (!msg.isEmpty()) {
                pw.println(msg);
            }
            record.getThrown().printStackTrace(pw);
            pw.flush();
            msg = sw.toString();
        }
        return msg;
    }

    private static void redirectSystemStreams() {
        try {
            System.setOut(new LoggingPrintStream(ORIGINAL_OUT, Level.INFO, "STDOUT"));
            System.setErr(new LoggingPrintStream(ORIGINAL_ERR, Level.SEVERE, "STDERR"));
        } catch (Throwable t) {
            ORIGINAL_ERR.println("[PatchBukkit] Failed to redirect System streams: " + t.getMessage());
        }
    }

    private static class LoggingPrintStream extends PrintStream {
        private final PrintStream delegate;
        private final Level level;
        private final String loggerName;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public LoggingPrintStream(PrintStream delegate, Level level, String loggerName) {
            super(delegate, true);
            this.delegate = delegate;
            this.level = level;
            this.loggerName = loggerName;
        }

        @Override
        public void write(int b) {
            delegate.write(b);
            if (b == '\n') {
                flushBuffer();
            } else if (b != '\r') {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            delegate.write(buf, off, len);
            for (int i = off; i < off + len; i++) {
                byte b = buf[i];
                if (b == '\n') {
                    flushBuffer();
                } else if (b != '\r') {
                    buffer.write(b);
                }
            }
        }

        private synchronized void flushBuffer() {
            if (buffer.size() == 0) return;
            String line = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            if (line.isEmpty() || IN_LOGGING.get()) return;
            IN_LOGGING.set(true);
            try {
                org.apache.logging.log4j.Logger log4jLogger = org.apache.logging.log4j.LogManager.getLogger(loggerName);
                if (level == Level.SEVERE) {
                    log4jLogger.error(line);
                } else if (level == Level.WARNING) {
                    log4jLogger.warn(line);
                } else {
                    log4jLogger.info(line);
                }
            } catch (Throwable t) {
                try {
                    LogLevel logLevel = LEVEL_MAP.getOrDefault(level, LogLevel.INFO);
                    NativeBridgeFfi.sendLog(
                            SendLogRequest.newBuilder()
                                    .setLevel(logLevel)
                                    .setMessage(line)
                                    .setLoggerName(loggerName)
                                    .build()
                    );
                } catch (Throwable ignored) {}
            } finally {
                IN_LOGGING.set(false);
            }
        }
    }

    public void registerPlayer(Player player) {
        this.onlinePlayers.put(player.getUniqueId(), player);
        this.onlinePlayersByName.put(player.getName().toLowerCase(), player);
    }

    public static void registerPlayer(String uuidStr, String name, boolean isOp) {
        registerPlayer(uuidStr, name, isOp, -1);
    }

    public static void registerPlayer(String uuidStr, String name, boolean isOp, int entityId) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            PatchBukkitPlayer player = new org.patchbukkit.entity.CraftPlayer(uuid, name, entityId);
            if (isOp) {
                player.setOp(true);
            }
            if (org.bukkit.Bukkit.getServer() instanceof PatchBukkitServer server) {
                server.registerPlayer(player);
            }
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to register player: " + name, t);
        }
    }

    public PatchBukkitEventManager getEventManager() {
        return this.pluginManager.getEventManager();
    }

    public void unregisterPlayer(UUID uuid) {
        Player p = this.onlinePlayers.remove(uuid);
        if (p != null) {
            this.onlinePlayersByName.remove(p.getName().toLowerCase());
        }
    }

    public void registerPlugin(@NotNull Plugin plugin) {
        this.pluginManager.registerPlugin(plugin);
    }

    private final Messenger messenger = new PatchBukkitMessenger();
    private final HelpMap helpMap = new PatchBukkitHelpMap();
    private File pluginsFolder = new File("plugins");

    private static void validateChannel(String channel) {
        if (channel == null) throw new IllegalArgumentException("Channel cannot be null");
        if (channel.length() > 64) {
            throw new IllegalArgumentException("Channel '" + channel + "' is invalid");
        }
    }

    @Override
    public void sendPluginMessage(
        @NotNull Plugin source,
        @NotNull String channel,
        byte@NotNull [] message
    ) {
        if (source == null) throw new IllegalArgumentException("Plugin source cannot be null");
        validateChannel(channel);
        if (message == null) throw new IllegalArgumentException("Message cannot be null");

        if (!messenger.isOutgoingChannelRegistered(source, channel)) {
            throw new IllegalArgumentException("Plugin " + source.getDescription().getFullName() + " has not registered outgoing channel '" + channel + "'");
        }

        for (Player player : getOnlinePlayers()) {
            player.sendPluginMessage(source, channel, message);
        }
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
        Set<String> channels = new HashSet<>();
        for (Player player : getOnlinePlayers()) {
            channels.addAll(player.getListeningPluginChannels());
        }
        return Collections.unmodifiableSet(channels);
    }

    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        return (Collection<? extends Audience>) (Collection<?>) getOnlinePlayers();
    }

    @Override
    public @NotNull File getPluginsFolder() {
        if (!pluginsFolder.exists()) {
            pluginsFolder.mkdirs();
        }
        return this.pluginsFolder;
    }

    @Override
    public @NotNull String getName() {
        return this.serverName;
    }

    @Override
    public @NotNull String getVersion() {
        return this.serverVersion;
    }

    @Override
    public @NotNull String getBukkitVersion() {
        return this.bukkitVersion;
    }

    @Override
    public @NotNull String getMinecraftVersion() {
        return this.minecraftVersion;
    }

    @Override
    public @NotNull Collection<? extends Player> getOnlinePlayers() {
        return Collections.unmodifiableCollection(onlinePlayers.values());
    }

    @Override
    public int getMaxPlayers() {
        return this.maxPlayers;
    }

    @Override
    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
        try {
            NativeBridgeFfi.setServerMaxPlayers(SetServerMaxPlayersRequest.newBuilder()
                .setMaxPlayers(maxPlayers)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public int getPort() {
        return this.port;
    }

    @Override
    public int getViewDistance() {
        return this.viewDistance;
    }

    @Override
    public int getSimulationDistance() {
        return this.simulationDistance;
    }

    @Override
    public @NotNull String getIp() {
        return this.ip;
    }

    @Override
    public @NotNull String getWorldType() {
        return "DEFAULT";
    }

    @Override
    public boolean getGenerateStructures() {
        return true;
    }

    @Override
    public int getMaxWorldSize() {
        return 29999984;
    }

    @Override
    public boolean getAllowEnd() {
        return this.allowEnd;
    }

    @Override
    public boolean getAllowNether() {
        return this.allowNether;
    }

    @Override
    public boolean isLoggingIPs() {
        return true;
    }

    @Override
    public @NotNull List<String> getInitialEnabledPacks() {
        return List.of("vanilla");
    }

    @Override
    public @NotNull List<String> getInitialDisabledPacks() {
        return List.of();
    }

    @Override
    public @NotNull ServerTickManager getServerTickManager() {
        return this.tickManager;
    }

    @Override
    public @Nullable ResourcePack getServerResourcePack() {
        return null;
    }

    @Override
    public @NotNull String getResourcePack() {
        return "";
    }

    @Override
    public @NotNull String getResourcePackHash() {
        return "";
    }

    @Override
    public @NotNull String getResourcePackPrompt() {
        return "";
    }

    @Override
    public boolean isResourcePackRequired() {
        return false;
    }

    @Override
    public boolean hasWhitelist() {
        return this.whitelistEnabled;
    }

    @Override
    public void setWhitelist(boolean value) {
        this.whitelistEnabled = value;
        try {
            NativeBridgeFfi.setServerWhitelist(SetServerWhitelistRequest.newBuilder()
                .setEnabled(value)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isWhitelistEnforced() {
        return this.whitelistEnforced;
    }

    @Override
    public void setWhitelistEnforced(boolean value) {
        this.whitelistEnforced = value;
        try {
            NativeBridgeFfi.setServerWhitelistEnforced(SetServerWhitelistEnforcedRequest.newBuilder()
                .setEnforced(value)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull Set<OfflinePlayer> getWhitelistedPlayers() {
        Set<OfflinePlayer> list = new HashSet<>();
        try {
            GetWhitelistResponse res = NativeBridgeFfi.getWhitelist(EmptyRequest.getDefaultInstance());
            if (res != null) {
                for (WhitelistEntryProto entry : res.getPlayersList()) {
                    if (entry.hasUuid()) {
                        list.add(getOfflinePlayer(UUID.fromString(entry.getUuid().getValue())));
                    } else {
                        list.add(getOfflinePlayer(entry.getName()));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    @Override
    public void reloadWhitelist() {
        syncServerInfo();
    }

    @Override
    public @NotNull String getUpdateFolder() {
        return "update";
    }

    @Override
    public @NotNull File getUpdateFolderFile() {
        return new File("update");
    }

    @Override
    public long getConnectionThrottle() {
        return 0;
    }

    @Override
    public int getTicksPerSpawns(@NotNull SpawnCategory spawnCategory) {
        return 1;
    }

    @Override
    public @Nullable Player getPlayer(@NotNull String name) {
        return onlinePlayersByName.get(name.toLowerCase());
    }

    @Override
    public @Nullable Player getPlayerExact(@NotNull String name) {
        return onlinePlayersByName.get(name.toLowerCase());
    }

    @Override
    public @NotNull List<Player> matchPlayer(@NotNull String name) {
        List<Player> matches = new ArrayList<>();
        String query = name.toLowerCase();
        for (Player p : getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(query)) {
                matches.add(p);
            }
        }
        return matches;
    }

    @Override
    public @Nullable Player getPlayer(@NotNull UUID id) {
        return onlinePlayers.get(id);
    }

    @Override
    public @Nullable UUID getPlayerUniqueId(@NotNull String playerName) {
        Player player = getPlayer(playerName);
        if (player != null) return player.getUniqueId();
        return getOfflinePlayer(playerName).getUniqueId();
    }

    @Override
    public @NotNull PluginManager getPluginManager() {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            return org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getPluginManager();
        }
        return this.pluginManager;
    }

    @Override
    public @NotNull BukkitScheduler getScheduler() {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            return org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getScheduler();
        }
        return this.scheduler;
    }

    @Override
    public @NotNull ServicesManager getServicesManager() {
        return this.servicesManager;
    }

    @Override
    public @NotNull List<World> getWorlds() {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            net.minecraft.server.dedicated.DedicatedServer s = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (s != null && s.server != null) {
                return s.server.getWorlds();
            }
        }
        var response = NativeBridgeFfi.getWorlds(EmptyRequest.getDefaultInstance());
        if (response == null) return List.of();
        List<World> list = new ArrayList<>();
        for (patchbukkit.common.UUID u : response.getWorldUuidsList()) {
            list.add(PatchBukkitWorld.getOrCreate(u.getValue()));
        }
        return list;
    }

    @Override
    public boolean isTickingWorlds() {
        return true;
    }

    @Override
    public @Nullable World createWorld(@NotNull WorldCreator creator) {
        Preconditions.checkArgument(creator != null, "WorldCreator cannot be null");
        try {
            var dim = switch (creator.environment()) {
                case NETHER -> "minecraft:the_nether";
                case THE_END -> "minecraft:the_end";
                default -> "minecraft:overworld";
            };
            var res = NativeBridgeFfi.createWorld(CreateWorldRequest.newBuilder()
                .setName(creator.name())
                .setDimension(dim)
                .setSeed(creator.seed())
                .build());
            if (res != null && res.hasWorldUuid()) {
                if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    net.minecraft.server.dedicated.DedicatedServer s = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
                    if (s instanceof org.patchbukkit.bootstrap.PumpkinDedicatedServer pds) {
                        UUID uuid = org.patchbukkit.bridge.BridgeUtils.convertUuid(res.getWorldUuid());
                        net.minecraft.core.RegistryAccess.Frozen registries = s.registryAccess();
                        var overworldType = registries.lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.OVERWORLD);
                        var netherType = registries.lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.NETHER);
                        var endType = registries.lookupOrThrow(net.minecraft.core.registries.Registries.DIMENSION_TYPE).getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.END);

                        var dimType = switch (creator.environment()) {
                            case NETHER -> netherType;
                            case THE_END -> endType;
                            default -> overworldType;
                        };
                        var stemKey = switch (creator.environment()) {
                            case NETHER -> net.minecraft.world.level.dimension.LevelStem.NETHER;
                            case THE_END -> net.minecraft.world.level.dimension.LevelStem.END;
                            default -> net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LEVEL_STEM, net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", creator.name().toLowerCase().replace(' ', '_')));
                        };
                        var dimKey = switch (creator.environment()) {
                            case NETHER -> net.minecraft.world.level.Level.NETHER;
                            case THE_END -> net.minecraft.world.level.Level.END;
                            default -> net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", creator.name().toLowerCase().replace(' ', '_')));
                        };

                        pds.createPumpkinLevel(
                            creator.name(),
                            uuid,
                            creator.environment(),
                            stemKey,
                            dimKey,
                            dimType,
                            0,
                            64,
                            0,
                            0.0F
                        );
                        return s.server.getWorld(uuid);
                    }
                }
                return PatchBukkitWorld.getOrCreate(res.getWorldUuid().getValue());
            }
        } catch (Throwable ignored) {}
        return getWorld(creator.name());
    }

    @Override
    public boolean unloadWorld(@NotNull String name, boolean save) {
        World world = getWorld(name);
        if (world != null) {
            return unloadWorld(world, save);
        }
        return false;
    }

    @Override
    public boolean unloadWorld(@NotNull World world, boolean save) {
        try {
            var res = NativeBridgeFfi.unloadWorld(UnloadWorldRequest.newBuilder()
                .setWorldUuid(patchbukkit.common.UUID.newBuilder().setValue(world.getUID().toString()).build())
                .setWorldName(world.getName())
                .setSave(save)
                .build());
            return res != null && res.getSuccess();
        } catch (Throwable ignored) {}
        return false;
    }

    public @NotNull World getRespawnWorld() {
        var worlds = getWorlds();
        if (!worlds.isEmpty()) {
            return worlds.get(0);
        }
        return PatchBukkitWorld.getOrCreate(UUID.randomUUID().toString());
    }

    public void setRespawnWorld(@NotNull World world) {
    }

    @Override
    public @Nullable World getWorld(@NotNull String name) {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            net.minecraft.server.dedicated.DedicatedServer s = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (s != null && s.server != null) {
                World w = s.server.getWorld(name);
                if (w != null) return w;
            }
        }
        for (World world : getWorlds()) {
            if (world.getName().equalsIgnoreCase(name)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public @Nullable World getWorld(@NotNull UUID uid) {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            net.minecraft.server.dedicated.DedicatedServer s = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (s != null && s.server != null) {
                World w = s.server.getWorld(uid);
                if (w != null) return w;
            }
        }
        for (World world : getWorlds()) {
            if (world.getUID().equals(uid)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public @Nullable World getWorld(@NotNull Key worldKey) {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            net.minecraft.server.dedicated.DedicatedServer s = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (s != null && s.server != null) {
                World w = s.server.getWorld(worldKey);
                if (w != null) return w;
            }
        }
        for (World world : getWorlds()) {
            if (world.getKey().equals(worldKey)) {
                return world;
            }
        }
        return null;
    }

    @Override
    public @NotNull WorldBorder createWorldBorder() {
        return new PatchBukkitWorldBorder(null);
    }

    @Override
    public @Nullable MapView getMap(int id) {
        return this.maps.get(id);
    }

    @Override
    public @NotNull MapView createMap(@NotNull World world) {
        int id = mapCounter.incrementAndGet();
        MapView map = new PatchBukkitMapView(id, world);
        this.maps.put(id, map);
        return map;
    }

    @Override
    public @NotNull ItemStack createExplorerMap(
        @NotNull World world,
        @NotNull Location location,
        @NotNull StructureType structureType,
        int radius,
        boolean findUnexplored
    ) {
        return new ItemStack(Material.FILLED_MAP);
    }

    @Override
    public @Nullable ItemStack createExplorerMap(
        @NotNull World world,
        @NotNull Location location,
        org.bukkit.generator.structure.@NotNull StructureType structureType,
        @NotNull Type mapIcon,
        int radius,
        boolean findUnexplored
    ) {
        return new ItemStack(Material.FILLED_MAP);
    }

    @Override
    public void reload() {
        reloadData();
    }

    public int getReloadCount() {
        return 0;
    }

    @Override
    public void reloadData() {
        syncServerInfo();
    }

    @Override
    public void updateResources() {
    }

    @Override
    public void updateRecipes() {
    }

    @Override
    public @NotNull Logger getLogger() {
        return logger;
    }

    @Override
    public @Nullable PluginCommand getPluginCommand(@NotNull String name) {
        Command cmd = this.commandMap.getCommand(name);
        return (cmd instanceof PluginCommand) ? (PluginCommand) cmd : null;
    }

    @Override
    public void savePlayers() {
        try {
            NativeBridgeFfi.saveAllWorlds(EmptyRequest.getDefaultInstance());
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean dispatchCommand(
        @NotNull CommandSender sender,
        @NotNull String commandLine
    ) throws CommandException {
        if (sender == null) throw new IllegalArgumentException("Sender cannot be null");
        if (commandLine == null) throw new IllegalArgumentException("CommandLine cannot be null");

        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            String cmd = commandLine.trim();
            if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
            return org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.dispatchCommand(sender, cmd);
        }
        return this.commandMap.dispatch(sender, commandLine);
    }

    @Override
    public boolean addRecipe(@Nullable Recipe recipe, boolean resendRecipes) {
        if (recipe != null) {
            this.recipes.add(recipe);
            return true;
        }
        return false;
    }

    @Override
    public @NotNull List<Recipe> getRecipesFor(@NotNull ItemStack result) {
        List<Recipe> matches = new ArrayList<>();
        for (Recipe r : this.recipes) {
            if (r.getResult().isSimilar(result)) {
                matches.add(r);
            }
        }
        return matches;
    }

    @Override
    public @Nullable Recipe getRecipe(@NotNull NamespacedKey recipeKey) {
        for (Recipe r : this.recipes) {
            if (r instanceof Keyed keyed && keyed.getKey().equals(recipeKey)) {
                return r;
            }
        }
        return null;
    }

    @Override
    public @Nullable Recipe getCraftingRecipe(
        @NotNull ItemStack@NotNull [] craftingMatrix,
        @NotNull World world
    ) {
        return null;
    }

    @Override
    public @NotNull ItemCraftResult craftItemResult(
        @NotNull ItemStack@NotNull [] craftingMatrix,
        @NotNull World world,
        @NotNull Player player
    ) {
        return craftItemResult(craftingMatrix, world);
    }

    @Override
    public @NotNull ItemCraftResult craftItemResult(
        @NotNull ItemStack@NotNull [] craftingMatrix,
        @NotNull World world
    ) {
        return new ItemCraftResult() {
            @Override public @NotNull ItemStack getResult() { return ItemStack.empty(); }
            @Override public @NotNull ItemStack[] getResultingMatrix() { return craftingMatrix; }
            @Override public @NotNull List<ItemStack> getOverflowItems() { return Collections.emptyList(); }
        };
    }

    @Override
    public @NotNull Iterator<Recipe> recipeIterator() {
        return this.recipes.iterator();
    }

    @Override
    public void clearRecipes() {
        this.recipes.clear();
    }

    @Override
    public void resetRecipes() {
        this.recipes.clear();
    }

    @Override
    public boolean removeRecipe(
        @NotNull NamespacedKey key,
        boolean resendRecipes
    ) {
        return this.recipes.removeIf(r -> r instanceof Keyed keyed && keyed.getKey().equals(key));
    }

    @Override
    public @NotNull Map<String, String[]> getCommandAliases() {
        return Collections.emptyMap();
    }

    @Override
    public int getSpawnRadius() {
        return this.spawnRadius;
    }

    @Override
    public void setSpawnRadius(int value) {
        this.spawnRadius = value;
    }

    @Override
    public boolean getHideOnlinePlayers() {
        return false;
    }

    @Override
    public boolean getOnlineMode() {
        return this.onlineMode;
    }

    @Override
    public boolean getAllowFlight() {
        return this.allowFlight;
    }

    @Override
    public boolean isHardcore() {
        return this.hardcore;
    }

    @Override
    public boolean isAcceptingTransfers() {
        return true;
    }

    @Override
    public void shutdown() {
        this.isStopping = true;
        try {
            NativeBridgeFfi.shutdownServer(ShutdownServerRequest.newBuilder()
                .setSave(true)
                .setMessage(this.shutdownMessage)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public int broadcast(@NotNull Component message, @NotNull String permission) {
        int count = 0;
        for (Player player : getOnlinePlayers()) {
            if (permission.isEmpty() || player.hasPermission(permission)) {
                player.sendMessage(message);
                count++;
            }
        }
        ConsoleCommandSender console = getConsoleSender();
        if (permission.isEmpty() || console.hasPermission(permission)) {
            console.sendMessage(message);
            count++;
        }
        return count;
    }

    @Override
    public int broadcast(@NotNull String message, @NotNull String permission) {
        return broadcast(LegacyComponentSerializer.legacySection().deserialize(message), permission);
    }

    @Override
    public int broadcastMessage(@NotNull String message) {
        return broadcast(message, Server.BROADCAST_CHANNEL_USERS);
    }

    @Override
    public @NotNull OfflinePlayer getOfflinePlayer(@NotNull String name) {
        Player player = getPlayerExact(name);
        if (player != null) return player;
        return new PatchBukkitOfflinePlayer(name);
    }

    @Override
    public @NotNull OfflinePlayer getOfflinePlayer(@NotNull UUID id) {
        Player player = getPlayer(id);
        if (player != null) return player;
        return new PatchBukkitOfflinePlayer(id);
    }

    @Override
    public @Nullable OfflinePlayer getOfflinePlayerIfCached(@NotNull String name) {
        return getOfflinePlayer(name);
    }

    @Override
    public boolean isEnforcingSecureProfiles() {
        return false;
    }

    @Override
    public org.bukkit.profile.@NotNull PlayerProfile createPlayerProfile(@NotNull UUID id) {
        return new PatchBukkitPlayerProfile(id, null);
    }

    @Override
    public org.bukkit.profile.@NotNull PlayerProfile createPlayerProfile(@NotNull String name) {
        return new PatchBukkitPlayerProfile(null, name);
    }

    @Override
    public org.bukkit.profile.@NotNull PlayerProfile createPlayerProfile(
        @Nullable UUID id,
        @Nullable String name
    ) {
        return new PatchBukkitPlayerProfile(id, name);
    }

    @Override
    public @NotNull Set<String> getIPBans() {
        Set<String> set = new HashSet<>();
        for (org.bukkit.BanEntry entry : this.ipBanList.getBanEntries()) {
            set.add(entry.getTarget());
        }
        return set;
    }

    @Override
    public void banIP(@NotNull String address) {
        this.ipBanList.addBan(address, "Banned by operator", (Date) null, "Server");
    }

    @Override
    public void unbanIP(@NotNull String address) {
        this.ipBanList.pardon(address);
    }

    @Override
    public void banIP(@NotNull InetAddress address) {
        this.ipBanList.addBan(address, "Banned by operator", (Date) null, "Server");
    }

    @Override
    public void unbanIP(@NotNull InetAddress address) {
        this.ipBanList.pardon(address);
    }

    @Override
    public @NotNull Set<OfflinePlayer> getBannedPlayers() {
        Set<OfflinePlayer> list = new HashSet<>();
        for (org.bukkit.BanEntry entry : this.nameBanList.getBanEntries()) {
            list.add(getOfflinePlayer(entry.getTarget()));
        }
        return list;
    }

    @Override
    public <T extends BanList<?>> @NotNull T getBanList(
        org.bukkit.BanList.@NotNull Type type
    ) {
        return switch (type) {
            case IP -> (T) this.ipBanList;
            case PROFILE -> (T) this.profileBanList;
            default -> (T) this.nameBanList;
        };
    }

    @Override
    public <B extends BanList<E>, E> @NotNull B getBanList(
        @NotNull BanListType<B> type
    ) {
        if (type == BanListType.IP) return (B) this.ipBanList;
        if (type == BanListType.PROFILE) return (B) this.profileBanList;
        return (B) this.nameBanList;
    }

    @Override
    public @NotNull Set<OfflinePlayer> getOperators() {
        Set<OfflinePlayer> ops = new HashSet<>();
        for (UUID uuid : operatorUuids) {
            ops.add(getOfflinePlayer(uuid));
        }
        for (String name : operatorNames) {
            ops.add(getOfflinePlayer(name));
        }
        try {
            GetOperatorsResponse res = NativeBridgeFfi.getOperators(EmptyRequest.getDefaultInstance());
            if (res != null) {
                for (OperatorEntryProto op : res.getOperatorsList()) {
                    if (op.hasUuid()) {
                        ops.add(getOfflinePlayer(UUID.fromString(op.getUuid().getValue())));
                    } else {
                        ops.add(getOfflinePlayer(op.getName()));
                    }
                }
            }
        } catch (Throwable ignored) {}
        return ops;
    }

    @Override
    public @NotNull GameMode getDefaultGameMode() {
        return this.defaultGameMode;
    }

    @Override
    public void setDefaultGameMode(@NotNull GameMode mode) {
        this.defaultGameMode = mode;
        try {
            NativeBridgeFfi.setServerDefaultGamemode(SetServerDefaultGamemodeRequest.newBuilder()
                .setGamemode(mode.name())
                .build());
        } catch (Throwable ignored) {}
    }

    public boolean forcesDefaultGameMode() {
        return false;
    }

    @Override
    public @NotNull ConsoleCommandSender getConsoleSender() {
        return new PatchBukkitConsoleCommandSender();
    }

    @Override
    public @NotNull CommandSender createCommandSender(
        @NotNull Consumer<? super Component> feedback
    ) {
        return new PatchBukkitConsoleCommandSender() {
            @Override
            public void sendMessage(@NotNull Component message) {
                feedback.accept(message);
            }
            @Override
            public void sendMessage(@NotNull String message) {
                feedback.accept(LegacyComponentSerializer.legacySection().deserialize(message));
            }
        };
    }

    @Override
    public @NotNull File getWorldContainer() {
        return new File(".");
    }

    @Override
    public @NotNull OfflinePlayer@NotNull [] getOfflinePlayers() {
        Set<OfflinePlayer> players = new HashSet<>(getOnlinePlayers());
        players.addAll(getOperators());
        players.addAll(getWhitelistedPlayers());
        players.addAll(getBannedPlayers());
        return players.toArray(new OfflinePlayer[0]);
    }

    @Override
    public @NotNull Messenger getMessenger() {
        return this.messenger;
    }

    @Override
    public @NotNull HelpMap getHelpMap() {
        return this.helpMap;
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        @NotNull InventoryType type
    ) {
        return new PatchBukkitInventory(owner, type);
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        @NotNull InventoryType type,
        @NotNull Component title
    ) {
        return new PatchBukkitInventory(owner, type, title);
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        @NotNull InventoryType type,
        @NotNull String title
    ) {
        return new PatchBukkitInventory(owner, type, title);
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        int size
    ) throws IllegalArgumentException {
        return new PatchBukkitInventory(owner, size);
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        int size,
        @NotNull Component title
    ) throws IllegalArgumentException {
        return new PatchBukkitInventory(owner, size, title);
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        int size,
        @NotNull String title
    ) throws IllegalArgumentException {
        return new PatchBukkitInventory(owner, size, title);
    }

    @Override
    public @NotNull Merchant createMerchant(@Nullable Component title) {
        return new PatchBukkitMerchant(title);
    }

    @Override
    public @NotNull Merchant createMerchant(@Nullable String title) {
        return new PatchBukkitMerchant(title != null ? Component.text(title) : null);
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        return 1000000;
    }

    @Override
    public @NotNull Merchant createMerchant() {
        return new PatchBukkitMerchant(null);
    }

    @Override
    public int getSpawnLimit(@NotNull SpawnCategory spawnCategory) {
        return switch (spawnCategory) {
            case MONSTER -> 70;
            case ANIMAL -> 10;
            case WATER_ANIMAL -> 5;
            case WATER_AMBIENT -> 20;
            case WATER_UNDERGROUND_CREATURE -> 5;
            case AMBIENT -> 15;
            case AXOLOTL -> 5;
            default -> 10;
        };
    }

    @Override
    public boolean isPrimaryThread() {
        return true;
    }

    @Override
    public @NotNull Component motd() {
        return Component.text(this.motd);
    }

    @Override
    public void motd(@NotNull Component motd) {
        this.motd = PlainTextComponentSerializer.plainText().serialize(motd);
        try {
            NativeBridgeFfi.setServerMotd(SetServerMotdRequest.newBuilder().setMotd(this.motd).build());
        } catch (Throwable ignored) {}
    }

    @Override
    public @Nullable Component shutdownMessage() {
        return Component.text(this.shutdownMessage);
    }

    @Override
    public @NotNull String getMotd() {
        return this.motd;
    }

    @Override
    public void setMotd(@NotNull String motd) {
        this.motd = motd;
        try {
            NativeBridgeFfi.setServerMotd(SetServerMotdRequest.newBuilder().setMotd(motd).build());
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull ServerLinks getServerLinks() {
        return this.serverLinks;
    }

    @Override
    public @Nullable String getShutdownMessage() {
        return this.shutdownMessage;
    }

    @Override
    public @NotNull WarningState getWarningState() {
        return WarningState.DEFAULT;
    }

    @Override
    public @NotNull ItemFactory getItemFactory() {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            // Real CraftBukkit item stacks/meta (enchantments, components, ...) backed by NMS.
            return org.bukkit.craftbukkit.inventory.CraftItemFactory.instance();
        }
        return PatchBukkitItemFactory.INSTANCE;
    }

    @Override
    public @NotNull EntityFactory getEntityFactory() {
        return this.entityFactory;
    }

    @Override
    public @NotNull ScoreboardManager getScoreboardManager() {
        return this.scoreboardManager;
    }

    @Override
    public @NotNull Criteria getScoreboardCriteria(@NotNull String name) {
        return Criteria.create(name);
    }

    @Override
    public @Nullable CachedServerIcon getServerIcon() {
        return null;
    }

    @Override
    public @NotNull CachedServerIcon loadServerIcon(@NotNull File file)
        throws IllegalArgumentException, Exception {
        return () -> "";
    }

    @Override
    public @NotNull CachedServerIcon loadServerIcon(
        @NotNull BufferedImage image
    ) throws IllegalArgumentException, Exception {
        return () -> "";
    }

    @Override
    public void setIdleTimeout(int threshold) {
        this.idleTimeout = threshold;
        try {
            NativeBridgeFfi.setServerIdleTimeout(SetServerIdleTimeoutRequest.newBuilder().setTimeoutMinutes(threshold).build());
        } catch (Throwable ignored) {}
    }

    @Override
    public int getIdleTimeout() {
        return this.idleTimeout;
    }

    @Override
    public int getPauseWhenEmptyTime() {
        return this.pauseWhenEmptyTime;
    }

    @Override
    public void setPauseWhenEmptyTime(int seconds) {
        this.pauseWhenEmptyTime = seconds;
    }

    @Override
    public @NotNull ChunkData createChunkData(@NotNull World world) {
        return (ChunkData) java.lang.reflect.Proxy.newProxyInstance(
            ChunkData.class.getClassLoader(),
            new Class<?>[] { ChunkData.class },
            (proxy, method, args) -> {
                if ("getMinHeight".equals(method.getName())) return world.getMinHeight();
                if ("getMaxHeight".equals(method.getName())) return world.getMaxHeight();
                if ("getBlockData".equals(method.getName())) return Material.AIR.createBlockData();
                if ("getType".equals(method.getName())) return Material.AIR;
                return null;
            }
        );
    }

    @Override
    public @NotNull BossBar createBossBar(
        @Nullable String title,
        @NotNull BarColor color,
        @NotNull BarStyle style,
        @NotNull BarFlag... flags
    ) {
        return new PatchBukkitBossBar(title, color, style, flags);
    }

    @Override
    public @NotNull KeyedBossBar createBossBar(
        @NotNull NamespacedKey key,
        @Nullable String title,
        @NotNull BarColor color,
        @NotNull BarStyle style,
        @NotNull BarFlag... flags
    ) {
        KeyedBossBar bar = new PatchBukkitKeyedBossBar(key, title, color, style, flags);
        this.bossBars.put(key, bar);
        return bar;
    }

    @Override
    public @NotNull Iterator<KeyedBossBar> getBossBars() {
        return this.bossBars.values().iterator();
    }

    @Override
    public @Nullable KeyedBossBar getBossBar(@NotNull NamespacedKey key) {
        return this.bossBars.get(key);
    }

    @Override
    public boolean removeBossBar(@NotNull NamespacedKey key) {
        return this.bossBars.remove(key) != null;
    }

    @Override
    public @Nullable Entity getEntity(@NotNull UUID uuid) {
        for (World world : getWorlds()) {
            Entity e = world.getEntity(uuid);
            if (e != null) return e;
        }
        return null;
    }

    @Override
    public double@NotNull [] getTPS() {
        try {
            ServerTickInfoResponse info = NativeBridgeFfi.getServerTickInfo(EmptyRequest.getDefaultInstance());
            if (info != null && info.getTpsCount() > 0) {
                double[] arr = new double[info.getTpsCount()];
                for (int i = 0; i < info.getTpsCount(); i++) {
                    arr[i] = info.getTps(i);
                }
                return arr;
            }
        } catch (Throwable ignored) {}
        return new double[] { 20.0, 20.0, 20.0 };
    }

    @Override
    public long@NotNull [] getTickTimes() {
        try {
            ServerTickInfoResponse info = NativeBridgeFfi.getServerTickInfo(EmptyRequest.getDefaultInstance());
            if (info != null && info.getTickTimesNanosCount() > 0) {
                long[] arr = new long[info.getTickTimesNanosCount()];
                for (int i = 0; i < info.getTickTimesNanosCount(); i++) {
                    arr[i] = info.getTickTimesNanos(i);
                }
                return arr;
            }
        } catch (Throwable ignored) {}
        return new long[100];
    }

    @Override
    public double getAverageTickTime() {
        try {
            ServerTickInfoResponse info = NativeBridgeFfi.getServerTickInfo(EmptyRequest.getDefaultInstance());
            if (info != null) {
                return info.getAverageTickTime();
            }
        } catch (Throwable ignored) {}
        return 50.0;
    }

    @Override
    public @NotNull CommandMap getCommandMap() {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            return org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getCommandMap();
        }
        return commandMap;
    }

    @Override
    public @Nullable Advancement getAdvancement(@NotNull NamespacedKey key) {
        return null;
    }

    @Override
    public @NotNull Iterator<Advancement> advancementIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull Material material) {
        Preconditions.checkArgument(material != null, "Material cannot be null");
        return this.createBlockData(material, (String) null);
    }

    @Override
    public @NotNull BlockData createBlockData(
        @NotNull Material material,
        @Nullable Consumer<? super BlockData> consumer
    ) {
        BlockData data = this.createBlockData(material);

        if (consumer != null) {
            consumer.accept(data);
        }

        return data;
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull String data) {
        Preconditions.checkArgument(data != null, "data cannot be null");

        return this.createBlockData(null, data);
    }

    @Override
    public @NotNull BlockData createBlockData(
        @Nullable Material material,
        @Nullable String data
    ) throws IllegalArgumentException {
        Preconditions.checkArgument(material != null || data != null, "Must provide one of material or data");
        BlockType type = null;
        if (material != null) {
            type = material.asBlockType();
            if (type == null && material.isBlock()) {
                type = org.patchbukkit.registry.PatchBukkitBlockType.create(material);
            }
            Preconditions.checkArgument(type != null, "Provided material must be a block");
        } else if (data != null) {
            String matName = data.trim();
            int stateIndex = matName.indexOf('[');
            if (stateIndex != -1) {
                matName = matName.substring(0, stateIndex).trim();
            }
            if (matName.startsWith("minecraft:")) {
                matName = matName.substring("minecraft:".length());
            }
            Material matched = null;
            try {
                matched = Material.valueOf(matName.toUpperCase(java.util.Locale.ROOT));
            } catch (Throwable ignored) {}

            if (matched == null || matched.isLegacy()) {
                Material m = Material.matchMaterial(matName, false);
                if (m != null && !m.isLegacy()) {
                    matched = m;
                }
            }

            if (matched == null) {
                matched = Material.matchMaterial(matName);
            }

            if (matched != null && matched.isLegacy()) {
                matched = PatchBukkitLegacy.fromLegacy(matched);
            }

            if (matched != null && (matched.isBlock() || (!matched.isLegacy() && matched.getKey() != null))) {
                material = matched;
                type = matched.asBlockType();
                if (type == null) {
                    type = org.patchbukkit.registry.PatchBukkitBlockType.create(matched);
                }
            } else {
                throw new IllegalArgumentException("Block name " + matName + " was not recognized");
            }
        }

        return PatchBukkitBlockData.newData(material, type, data);
    }

    @Override
    public <T extends Keyed> @Nullable Tag<T> getTag(
        @NotNull String registry,
        @NotNull NamespacedKey tag,
        @NotNull Class<T> clazz
    ) {
        if (registry == null || tag == null || clazz == null) {
            return null;
        }
        try {
            for (java.lang.reflect.Field field : Tag.class.getFields()) {
                if (Tag.class.isAssignableFrom(field.getType())) {
                    Tag<?> val = (Tag<?>) field.get(null);
                    if (val != null && val.getKey().equals(tag)) {
                        return (Tag<T>) val;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return new org.patchbukkit.tag.PatchBukkitTag<>(tag);
    }

    @Override
    public <T extends Keyed> @NotNull Iterable<Tag<T>> getTags(
        @NotNull String registry,
        @NotNull Class<T> clazz
    ) {
        List<Tag<T>> result = new ArrayList<>();
        try {
            for (java.lang.reflect.Field field : Tag.class.getFields()) {
                if (Tag.class.isAssignableFrom(field.getType())) {
                    Tag<?> val = (Tag<?>) field.get(null);
                    if (val != null) {
                        result.add((Tag<T>) val);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    @Override
    public @Nullable LootTable getLootTable(@NotNull NamespacedKey key) {
        return null;
    }

    @Override
    public @NotNull List<Entity> selectEntities(
        @NotNull CommandSender sender,
        @NotNull String selector
    ) throws IllegalArgumentException {
        List<Entity> list = new ArrayList<>();
        if ("@a".equalsIgnoreCase(selector) || "@e".equalsIgnoreCase(selector)) {
            list.addAll(getOnlinePlayers());
        } else if ("@s".equalsIgnoreCase(selector) || "@p".equalsIgnoreCase(selector)) {
            if (sender instanceof Entity e) {
                list.add(e);
            }
        } else {
            Player p = getPlayer(selector);
            if (p != null) list.add(p);
        }
        return list;
    }

    @Override
    public @NotNull StructureManager getStructureManager() {
        return this.structureManager;
    }

    @Override
    public <T extends Keyed> @Nullable Registry<T> getRegistry(
        @NotNull Class<T> tClass
    ) {
        return io.papermc.paper.registry.RegistryAccess.registryAccess().getRegistry(tClass);
    }

    @Override
    public @NotNull UnsafeValues getUnsafe() {
        return PatchBukkitUnsafeValues.INSTANCE;
    }

    @Override
    public @NotNull Spigot spigot() {
        return this.spigot;
    }

    @Override
    public void restart() {
        shutdown();
    }

    @Override
    public void reloadPermissions() {
    }

    @Override
    public boolean reloadCommandAliases() {
        return true;
    }

    @Override
    public boolean suggestPlayerNamesWhenNullTabCompletions() {
        return true;
    }

    @Override
    public @NotNull String getPermissionMessage() {
        return "I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.";
    }

    @Override
    public @NotNull Component permissionMessage() {
        return Component.text(getPermissionMessage());
    }

    @Override
    public PlayerProfile createProfile(@NotNull UUID uuid) {
        return new PatchBukkitPlayerProfile(uuid, null);
    }

    @Override
    public PlayerProfile createProfile(@NotNull String name) {
        return new PatchBukkitPlayerProfile(null, name);
    }

    @Override
    public PlayerProfile createProfile(@Nullable UUID uuid, @Nullable String name) {
        return new PatchBukkitPlayerProfile(uuid, name);
    }

    @Override
    public PlayerProfile createProfileExact(@Nullable UUID uuid, @Nullable String name) {
        return new PatchBukkitPlayerProfile(uuid, name);
    }

    @Override
    public int getCurrentTick() {
        try {
            ServerTickInfoResponse info = NativeBridgeFfi.getServerTickInfo(EmptyRequest.getDefaultInstance());
            if (info != null && info.getTickCount() > 0) {
                return (int) info.getTickCount();
            }
        } catch (Throwable ignored) {}
        return (int) (System.currentTimeMillis() / 50);
    }

    @Override
    public boolean isStopping() {
        return this.isStopping;
    }

    @Override
    public @NotNull MobGoals getMobGoals() {
        return this.mobGoals;
    }

    @Override
    public @NotNull DatapackManager getDatapackManager() {
        return this.datapackManager;
    }

    @Override
    public @NotNull PotionBrewer getPotionBrewer() {
        return this.potionBrewer;
    }

    @Override
    public @NotNull RegionScheduler getRegionScheduler() {
        return this.regionScheduler;
    }

    @Override
    public @NotNull AsyncScheduler getAsyncScheduler() {
        return this.asyncScheduler;
    }

    @Override
    public @NotNull GlobalRegionScheduler getGlobalRegionScheduler() {
        return this.globalRegionScheduler;
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        @NotNull Position position
    ) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        @NotNull Position position,
        int squareRadiusChunks
    ) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull Location location,
        int squareRadiusChunks
    ) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        int chunkX,
        int chunkZ
    ) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        int chunkX,
        int chunkZ,
        int squareRadiusChunks
    ) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ
    ) {
        return true;
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        return true;
    }

    @Override
    public boolean isGlobalTickThread() {
        return isPrimaryThread();
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public void allowPausing(@NotNull Plugin plugin, boolean value) {
    }

    public @NotNull Path getLevelDirectory() {
        return Path.of(".");
    }

    @Override
    public int getAmbientSpawnLimit() {
        return 15;
    }

    @Override
    public int getWaterUndergroundCreatureSpawnLimit() {
        return 5;
    }

    @Override
    public int getWaterAmbientSpawnLimit() {
        return 20;
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        return 5;
    }

    @Override
    public @NotNull io.papermc.paper.configuration.ServerConfiguration getServerConfig() {
        return new io.papermc.paper.configuration.ServerConfiguration() {
            @Override
            public boolean isProxyOnlineMode() {
                return false;
            }

            @Override
            public boolean isProxyEnabled() {
                return false;
            }
        };
    }
}
