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
import java.util.Optional;
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
    private static volatile DedicatedServer SERVER;
    private static volatile boolean realRegistries;
    private static volatile Thread MAIN_THREAD;
    private static final ThreadLocal<Boolean> PRIMARY_THREAD_OVERRIDE = new ThreadLocal<>();

    private HeadlessPaperServer() {}

    public static void setMainThread(Thread thread) {
        MAIN_THREAD = thread;
    }

    public static Thread getMainThread() {
        return MAIN_THREAD;
    }

    public static Boolean enterPrimaryThread() {
        Boolean prev = PRIMARY_THREAD_OVERRIDE.get();
        PRIMARY_THREAD_OVERRIDE.set(Boolean.TRUE);
        MAIN_THREAD = Thread.currentThread();
        return prev;
    }

    public static Boolean enterAsyncThread() {
        Boolean prev = PRIMARY_THREAD_OVERRIDE.get();
        PRIMARY_THREAD_OVERRIDE.set(Boolean.FALSE);
        return prev;
    }

    public static void restoreThreadState(Boolean prev) {
        if (prev == null) {
            PRIMARY_THREAD_OVERRIDE.remove();
        } else {
            PRIMARY_THREAD_OVERRIDE.set(prev);
        }
    }

    public static boolean isMainThread(Thread thread) {
        if (thread == null) return false;
        if (thread == Thread.currentThread()) {
            Boolean override = PRIMARY_THREAD_OVERRIDE.get();
            if (override != null) {
                return override;
            }
        }
        if (thread == MAIN_THREAD) return true;
        if (SERVER != null && thread == SERVER.getRunningThread()) return true;
        String name = thread.getName();
        return name.equals("patchbukkit-jvm-worker")
            || name.equals("Server-Ticker")
            || name.startsWith("Test worker");
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

    private static boolean isInternalHostVmArg(String arg) {
        if (arg == null) return false;
        return arg.startsWith("-javaagent:")
            || arg.startsWith("-agentlib:")
            || arg.startsWith("-agentpath:")
            || arg.startsWith("-XX:+EnableDynamicAgentLoading")
            || arg.startsWith("-XX:+StartAttachListener")
            || arg.startsWith("-XX:+IgnoreUnrecognizedVMOptions")
            || arg.startsWith("-Djdk.attach.allowAttachSelf")
            || arg.startsWith("-Dnet.bytebuddy.")
            || arg.startsWith("-Dcom.google.protobuf.useUnsafe")
            || arg.startsWith("--enable-native-access")
            || arg.startsWith("--enable-final-field-mutation")
            || arg.startsWith("--add-opens")
            || arg.startsWith("--add-exports");
    }

    private static void sanitizeRuntimeEnvironment() {
        try {
            System.clearProperty("jdk.attach.allowAttachSelf");
            System.clearProperty("net.bytebuddy.experimental");
        } catch (Throwable ignored) {}

        try {
            java.lang.management.RuntimeMXBean runtimeBean = java.lang.management.ManagementFactory.getRuntimeMXBean();
            java.util.List<String> currentArgs = runtimeBean.getInputArguments();
            java.util.List<String> filteredArgs = currentArgs.stream()
                .filter(arg -> !isInternalHostVmArg(arg))
                .toList();

            java.lang.reflect.Field jvmField = runtimeBean.getClass().getDeclaredField("jvm");
            jvmField.setAccessible(true);
            Object vmManagement = jvmField.get(runtimeBean);
            if (vmManagement != null) {
                java.lang.reflect.Field vmArgsField = vmManagement.getClass().getDeclaredField("vmArgs");
                vmArgsField.setAccessible(true);
                vmArgsField.set(vmManagement, java.util.Collections.unmodifiableList(filteredArgs));
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[PatchBukkit] Could not sanitize RuntimeMXBean vmArgs", t);
        }

        try {
            Class<?> vmSupportClass = Class.forName("jdk.internal.vm.VMSupport");
            java.lang.reflect.Method getAgentProps = vmSupportClass.getDeclaredMethod("getAgentProperties");
            getAgentProps.setAccessible(true);
            java.util.Properties agentProps = (java.util.Properties) getAgentProps.invoke(null);
            if (agentProps != null) {
                agentProps.remove("sun.jvm.args");
                agentProps.remove("sun.jvm.flags");
            }
        } catch (Throwable ignored) {}
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

    /** Entry point for the Rust side (JNI) with explicit plugins directory. Returns {@code true} on success. */
    public static boolean boot(String rootDir, String pluginsDir) {
        try {
            boot(Path.of(rootDir), Path.of(pluginsDir));
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to boot headless Paper server, falling back", t);
            return false;
        }
    }

    /** Disables all loaded plugins in reverse order via Paper's native plugin manager. */
    public static boolean disablePlugins() {
        Boolean prevThreadState = enterPrimaryThread();
        try {
            if (SERVER != null && SERVER.server != null) {
                SERVER.server.disablePlugins();
            }
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to disable plugins", t);
            return false;
        } finally {
            restoreThreadState(prevThreadState);
        }
    }

    /**
     * Checks whether a plugin JAR at {@code path} should be skipped because its plugin name
     * is listed in {@code patchbukkit.config.toml}'s {@code [plugins] disabled} list.
     */
    public static boolean shouldSkipPluginJar(Path path) {
        if (path == null) return false;
        java.util.Set<String> disabledPlugins = new java.util.HashSet<>();
        try {
            var config = patchbukkit.bridge.NativeBridgeFfi.getPatchBukkitConfig(
                patchbukkit.common.EmptyRequest.getDefaultInstance()
            );
            if (config != null) {
                for (String disabled : config.getDisabledPluginsList()) {
                    disabledPlugins.add(disabled.toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[PatchBukkit] Could not fetch PatchBukkitConfig for disabled plugins check", t);
        }
        if (disabledPlugins.isEmpty()) {
            return false;
        }
        try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(
                path.toFile(), true, java.util.zip.ZipFile.OPEN_READ, java.util.jar.JarFile.runtimeVersion())) {
            io.papermc.paper.plugin.provider.type.PluginFileType<?, ?> type =
                io.papermc.paper.plugin.provider.type.PluginFileType.guessType(jarFile);
            if (type != null) {
                String name = type.getConfig(jarFile).getName();
                if (name != null && disabledPlugins.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                    LOGGER.info("[PatchBukkit] Skipping plugin '" + name + "' (" + path.getFileName() + ") because it is disabled in patchbukkit.config.toml");
                    return true;
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[PatchBukkit] Could not inspect plugin jar for disabled check: " + path, t);
        }
        return false;
    }

    public static synchronized DedicatedServer boot(Path root) throws Exception {
        return boot(root, root.toAbsolutePath().resolve("plugins"));
    }

    public static synchronized DedicatedServer boot(Path root, Path pluginsDir) throws Exception {
        if (SERVER != null) {
            return SERVER;
        }
        // Ensure LogBridge captures ORIGINAL_OUT / ORIGINAL_ERR before DedicatedServer/CraftServer redirects System.out
        @SuppressWarnings("unused")
        var origOut = org.patchbukkit.log.LogBridge.ORIGINAL_OUT;
        setMainThread(Thread.currentThread());
        patchTickThread();
        WorldBridge.patch();
        EntityBridge.patch();
        PlayerBridge.patch();
        InventoryBridge.patch();
        ServerBridge.patch();
        EventBridge.patch();
        CommandBridge.patch();
        sanitizeRuntimeEnvironment();
        root = root.toAbsolutePath();
        pluginsDir = pluginsDir.toAbsolutePath();
        Files.createDirectories(root);
        Files.createDirectories(pluginsDir);
        long start = System.nanoTime();
        realRegistries = true;
        try {
            return doBoot(root, pluginsDir, start);
        } catch (Throwable t) {
            realRegistries = false;
            throw t;
        }
    }

    private static DedicatedServer doBoot(Path root, Path pluginsDir, long start) throws Exception {

        OptionSet options = createOptions(root, pluginsDir);

        org.bukkit.craftbukkit.Main.useConsole = false;
        org.bukkit.craftbukkit.Main.useJline = false;
        SharedConstants.tryDetectVersion();
        PluginInitializerManager.load(options);
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

        DedicatedServer server = new DedicatedServer(
            options,
            loadContext.get(),
            Thread.currentThread(),
            access,
            packRepository,
            worldStem,
            Optional.empty(),
            settings,
            DataFixers.getDataFixer(),
            services,
            null,
            notificationManager
        );
        notificationManager.setServer(server);
        removeShutdownHook();

        PumpkinDedicatedServer.initHeadless(server);
        SERVER = server;
        org.patchbukkit.log.LogBridge.init();
        installInertWatchdog();
        EventBridge.init(server);
        CommandBridge.captureVanillaCommands(server);
        SchedulerBridge.startFallbackTicker();

        Boolean prevThreadState = enterPrimaryThread();
        SchedulerBridge.setInPluginBootstrap(true);
        try {
            server.server.loadPlugins();
            server.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.STARTUP);
            PumpkinDedicatedServer.initWorlds(server);
            server.server.enablePlugins(org.bukkit.plugin.PluginLoadOrder.POSTWORLD);

            io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid();
            io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(
                io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                io.papermc.paper.command.brigadier.PaperCommands.INSTANCE,
                org.bukkit.plugin.Plugin.class,
                io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL
            );
            server.server.getCommandMap().registerServerAliases();
            if (server.server.getHelpMap() instanceof org.bukkit.craftbukkit.help.SimpleHelpMap simpleHelpMap) {
                simpleHelpMap.initializeCommands();
            }
            server.server.getPluginManager().callEvent(
                new org.bukkit.event.server.ServerLoadEvent(org.bukkit.event.server.ServerLoadEvent.LoadType.STARTUP)
            );
            CommandBridge.syncAll(server);
            org.patchbukkit.network.VirtualChannelManager.getInstance().refreshPacketInterceptionState();
        } finally {
            SchedulerBridge.setInPluginBootstrap(false);
            restoreThreadState(prevThreadState);
            SchedulerBridge.heartbeat();
        }

        LOGGER.info(String.format(
            "[PatchBukkit] Booted headless Paper server (%s) in %.2fs",
            server.server.getVersion(),
            (System.nanoTime() - start) / 1e9
        ));
        return server;
    }

    /**
     * Plugins such as WorldEdit tick Spigot's watchdog reflectively and expect
     * {@code WatchdogThread.instance} to exist. Installs an instance that is never started, so
     * ticking it works but nothing ever monitors it. Starting the real watchdog is not an option:
     * it halts the JVM when the tick it expects stalls, and Pumpkin owns the tick loop.
     */
    private static void installInertWatchdog() {
        try {
            Class<?> cls = org.spigotmc.WatchdogThread.class;
            Field instance = cls.getDeclaredField("instance");
            instance.setAccessible(true);
            if (instance.get(null) != null) return;
            var ctor = cls.getDeclaredConstructor(long.class, boolean.class);
            ctor.setAccessible(true);
            instance.set(null, ctor.newInstance(Long.MAX_VALUE / 4, false));
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Could not install inert watchdog", t);
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
        return createOptions(root, root.resolve("plugins"));
    }

    static OptionSet createOptions(Path root, Path pluginsDir) {
        OptionParser parser = new OptionParser();
        parser.acceptsAll(List.of("c", "config")).withRequiredArg().ofType(File.class)
            .defaultsTo(root.resolve("server.properties").toFile());
        parser.acceptsAll(List.of("P", "plugins")).withRequiredArg().ofType(File.class)
            .defaultsTo(pluginsDir.toFile());
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
