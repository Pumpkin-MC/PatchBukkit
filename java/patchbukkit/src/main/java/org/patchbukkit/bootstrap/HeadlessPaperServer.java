package org.patchbukkit.bootstrap;

import com.destroystokyo.paper.profile.PaperServicesDiscoveryService;
import io.papermc.paper.datapack.DynamicBuiltinPacks;
import io.papermc.paper.plugin.PluginInitializerManager;
import io.papermc.paper.world.migration.WorldFolderMigration;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.SharedConstants;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.Main;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerProperties;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.Util;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.craftbukkit.CraftServer;

/**
 * Boots the real Paper server headlessly, following the same path as Paper's own
 * {@code net.minecraft.server.Main}, but without ever starting the server thread.
 *
 * <p>Afterwards the full NMS registry stack (including datapack registries such as
 * enchantments, biomes and damage types), a real {@link DedicatedServer}, its
 * {@code PlayerList} and a real {@link CraftServer} exist.
 *
 * <p>All files Paper creates (server.properties, bukkit.yml, spigot.yml, config/,
 * usercache.json, the shadow level folder) live under the given root directory, so
 * nothing is written into the Pumpkin server root.
 *
 * <p>This class must be booted before {@code PatchBukkitServer} is initialized and
 * must not reference it, because that class sets up fallback registries in its
 * static initializer.
 */
public final class HeadlessPaperServer {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static volatile PumpkinDedicatedServer SERVER;
    private static volatile boolean realRegistries;
    private static volatile Thread MAIN_THREAD;

    private HeadlessPaperServer() {}

    public static void setMainThread(Thread thread) {
        MAIN_THREAD = thread;
    }

    public static boolean isMainThread(Thread thread) {
        if (thread == null) return false;
        if (thread == MAIN_THREAD) return true;
        if (SERVER != null && thread == SERVER.getRunningThread()) return true;
        String name = thread.getName();
        return name.equals("patchbukkit-jvm-worker") || name.startsWith("Test worker");
    }

    private static void patchTickThread() {
        try {
            ByteBuddyAgent.install();
            new ByteBuddy()
                .redefine(ca.spottedleaf.moonrise.common.util.TickThread.class)
                .method(ElementMatchers.named("isTickThread"))
                .intercept(MethodDelegation.to(TickThreadInterceptor.class))
                .make()
                .load(
                    ca.spottedleaf.moonrise.common.util.TickThread.class.getClassLoader(),
                    ClassReloadingStrategy.fromInstalledAgent()
                );
            LOGGER.info("[PatchBukkit] Successfully patched TickThread.isTickThread()");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch TickThread with ByteBuddyAgent", t);
        }
    }

    /** @return the booted server, or {@code null} if {@link #boot} was not (successfully) called. */
    public static DedicatedServer get() {
        return SERVER;
    }

    public static boolean isBooted() {
        return SERVER != null;
    }

    /**
     * Whether Bukkit registries must come from the real Paper registries. True from the
     * start of {@link #boot}: Bukkit's {@code Registry.*} constants are captured once, and
     * that already happens while the server is being constructed.
     */
    public static boolean usesRealRegistries() {
        return realRegistries;
    }

    /** Entry point for the Rust side (JNI). Returns {@code true} on success. */
    public static boolean boot(String rootDir) {
        try {
            boot(Path.of(rootDir));
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to boot headless Paper server, falling back", t);
            return false;
        }
    }

    public static synchronized DedicatedServer boot(Path root) throws Exception {
        if (SERVER != null) {
            return SERVER;
        }
        setMainThread(Thread.currentThread());
        patchTickThread();
        WorldBridge.patch();
        EntityBridge.patch();
        EventBridge.patch();
        CommandBridge.patch();
        root = root.toAbsolutePath();
        Files.createDirectories(root);
        long start = System.nanoTime();
        realRegistries = true;
        try {
            return doBoot(root, start);
        } catch (Throwable t) {
            realRegistries = false;
            throw t;
        }
    }

    private static PumpkinDedicatedServer doBoot(Path root, long start) throws Exception {

        OptionSet options = createOptions(root);

        org.bukkit.craftbukkit.Main.useConsole = false;
        org.bukkit.craftbukkit.Main.useJline = false;
        SharedConstants.tryDetectVersion();
        PluginInitializerManager.init(options);
        Bootstrap.bootStrap();
        Bootstrap.validate();

        DedicatedServerSettings settings = new DedicatedServerSettings(options);
        settings.forceSave();
        DedicatedServerProperties properties = settings.getProperties();

        File universe = (File) options.valueOf("world-dir");
        Services services = Services.create(
            PaperServicesDiscoveryService.create(Proxy.NO_PROXY),
            universe,
            root.resolve("usercache.json").toFile(),
            options
        );
        NotificationManager notificationManager = new NotificationManager();

        LevelStorageSource levelStorageSource = LevelStorageSource.createDefault(universe.toPath());
        LevelStorageAccess access = levelStorageSource.validateAndCreateAccess(properties.levelName);
        WorldFolderMigration.didInitialLoad = true;

        PackRepository packRepository = ServerPacksSource.createPackRepository(access);
        DynamicBuiltinPacks.refreshAllMetadata(access);

        // Always "new world" data: the shadow level only provides registries and
        // world settings, the actual worlds live in Pumpkin.
        WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(
            new WorldLoader.PackConfig(
                packRepository,
                new WorldDataConfiguration(properties.initialDataPackConfiguration, FeatureFlags.DEFAULT_FLAGS),
                false,
                true
            ),
            Commands.CommandSelection.DEDICATED,
            properties.functionPermissions
        );

        AtomicReference<WorldLoader.DataLoadContext> loadContext = new AtomicReference<>();
        WorldStem worldStem = Util.blockUntilDone(
            executor -> WorldLoader.load(
                initConfig,
                context -> {
                    loadContext.set(context);
                    return Main.createNewWorldData(
                        settings,
                        context,
                        context.datapackDimensions().lookupOrThrow(Registries.LEVEL_STEM),
                        false,
                        false
                    );
                },
                WorldStem::new,
                Util.backgroundExecutor(),
                executor
            )
        ).get();

        PumpkinDedicatedServer server = new PumpkinDedicatedServer(
            options,
            loadContext.get(),
            Thread.currentThread(),
            access,
            packRepository,
            worldStem,
            settings,
            DataFixers.getDataFixer(),
            services,
            notificationManager
        );
        notificationManager.setServer(server);
        removeShutdownHook();

        server.initHeadless();
        SERVER = server;
        server.initWorlds();
        EventBridge.init(server);
        CommandBridge.syncAll(server);

        LOGGER.info(String.format(
            "[PatchBukkit] Booted headless Paper server (%s) in %.2fs",
            server.server.getVersion(),
            (System.nanoTime() - start) / 1e9
        ));
        return server;
    }

    /**
     * Until players/worlds are bridged (phases 2-4), PatchBukkit's own server stays the
     * {@link Bukkit#getServer()} facade. The real {@link CraftServer} registers itself in its
     * constructor, so swap it back here. The real CraftServer remains reachable via
     * {@code get().server}.
     */
    public static void installBukkitFacade(Server facade) {
        try {
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, facade);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to install Bukkit server facade", e);
        }
    }

    /**
     * MinecraftServer registers a ServerShutdownThread that halts the server and then
     * waits for the tick loop to finish. The loop never runs here, so that would hang
     * JVM shutdown forever. Pumpkin drives shutdown instead.
     */
    @SuppressWarnings("unchecked")
    private static void removeShutdownHook() {
        try {
            Class<?> hooksClass = Class.forName("java.lang.ApplicationShutdownHooks");
            Field hooksField = hooksClass.getDeclaredField("hooks");
            hooksField.setAccessible(true);
            IdentityHashMap<Thread, Thread> hooks;
            synchronized (hooksClass) {
                hooks = new IdentityHashMap<>((IdentityHashMap<Thread, Thread>) hooksField.get(null));
            }
            for (Thread hook : hooks.keySet()) {
                if (hook instanceof org.bukkit.craftbukkit.util.ServerShutdownThread) {
                    Runtime.getRuntime().removeShutdownHook(hook);
                }
            }
        } catch (Throwable t) {
            // Needs --add-opens java.base/java.lang (set by the Rust launcher). Without it the
            // hook stays, but PumpkinDedicatedServer#halt makes it finish after ~1s anyway.
            LOGGER.log(Level.FINE, "[PatchBukkit] Could not remove Paper's shutdown hook", t);
        }
    }

    /** Same options as {@code org.bukkit.craftbukkit.Main}, with every path rooted in {@code root}. */
    static OptionSet createOptions(Path root) {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(List.of("c", "config")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("server.properties").toFile());
        parser.acceptsAll(List.of("P", "plugins")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("plugins").toFile());
        parser.acceptsAll(List.of("h", "host", "server-ip")).withRequiredArg().ofType(String.class);
        parser.acceptsAll(List.of("W", "world-dir", "universe", "world-container")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("worlds").toFile());
        parser.acceptsAll(List.of("w", "world", "level-name")).withRequiredArg().ofType(String.class);
        parser.acceptsAll(List.of("p", "port", "server-port")).withRequiredArg().ofType(Integer.class);
        parser.accepts("serverId").withRequiredArg();
        parser.accepts("jfrProfile");
        parser.accepts("pidFile").withRequiredArg();
        parser.acceptsAll(List.of("o", "online-mode")).withRequiredArg().ofType(Boolean.class);
        parser.acceptsAll(List.of("s", "size", "max-players")).withRequiredArg().ofType(Integer.class);
        parser.acceptsAll(List.of("b", "bukkit-settings")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("bukkit.yml").toFile());
        parser.acceptsAll(List.of("C", "commands-settings")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("commands.yml").toFile());
        parser.accepts("forceUpgrade");
        parser.accepts("eraseCache");
        parser.accepts("recreateRegionFiles");
        parser.accepts("safeMode");
        parser.accepts("nogui");
        parser.accepts("nojline");
        parser.accepts("noconsole");
        parser.acceptsAll(List.of("v", "version"));
        parser.accepts("demo");
        parser.accepts("bonusChest");
        parser.accepts("initSettings");
        parser.acceptsAll(List.of("S", "spigot-settings")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("spigot.yml").toFile());
        parser.acceptsAll(List.of("paper-dir", "paper-settings-directory")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("config").toFile());
        parser.acceptsAll(List.of("paper", "paper-settings")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("paper.yml").toFile());
        parser.acceptsAll(List.of("add-plugin", "add-extra-plugin-jar")).withRequiredArg().ofType(File.class)
            .defaultsTo(new File[0]);
        parser.acceptsAll(List.of("add-plugin-dir", "add-extra-plugin-dir")).withRequiredArg().ofType(File.class)
            .defaultsTo(new File[0]);
        parser.accepts("server-name").withRequiredArg().ofType(String.class).defaultsTo("Pumpkin");

        return parser.parse(Arrays.asList("--nogui", "--noconsole").toArray(new String[0]));
    }
}
