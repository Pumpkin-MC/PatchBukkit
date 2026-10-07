package org.patchbukkit.bootstrap;

import com.mojang.datafixers.DataFixer;
import io.papermc.paper.world.PaperWorldLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import joptsimple.OptionSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.common.EmptyRequest;
import patchbukkit.world.GetWorldInfoRequest;
import patchbukkit.world.GetWorldInfoResponse;
import patchbukkit.world.GetWorldsResponse;
import net.minecraft.server.Services;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.WorldStem;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.flat.FlatLevelGeneratorSettings;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelDataAndDimensions;
import net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess;
import org.bukkit.World;
import org.spigotmc.SpigotConfig;

/**
 * A real Paper {@link DedicatedServer} that is constructed but never run.
 *
 * <p>Pumpkin owns the game loop, networking and worlds. This server only exists so
 * that the real CraftBukkit / NMS object graph (registries, {@code CraftServer},
 * {@code PlayerList}, configs, ...) is available to plugins. Its tick loop
 * ({@code runServer}) and the vanilla {@link #initServer()} are never invoked.
 */
public final class PumpkinDedicatedServer extends DedicatedServer {

    PumpkinDedicatedServer(
        OptionSet options,
        WorldLoader.DataLoadContext worldLoader,
        Thread serverThread,
        LevelStorageAccess levelStorageSource,
        PackRepository packRepository,
        WorldStem worldStem,
        DedicatedServerSettings settings,
        DataFixer fixerUpper,
        Services services,
        NotificationManager notificationManager
    ) {
        super(
            options,
            worldLoader,
            serverThread,
            levelStorageSource,
            packRepository,
            worldStem,
            Optional.empty(),
            settings,
            fixerUpper,
            services,
            null,
            notificationManager
        );
    }

    /**
     * The headless subset of {@link DedicatedServer#initServer()}: everything needed
     * for the Bukkit/NMS object graph, nothing that binds sockets, starts threads,
     * loads worlds or writes files into the Pumpkin server root.
     */
    void initHeadless() throws org.spongepowered.configurate.ConfigurateException {
        DedicatedServerSettings settings = this.settings;
        this.setUsesAuthentication(settings.getProperties().onlineMode);
        this.setPreventProxyConnections(settings.getProperties().preventProxyConnections);

        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setDispatcher(
            this.getCommands(),
            net.minecraft.commands.Commands.createValidationContext(this.registryAccess())
        );
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid();

        // Creates the real CraftServer (PlayerList's constructor does
        // `server.server = new CraftServer(...)`, which also calls Bukkit.setServer).
        this.setPlayerList(new DedicatedPlayerList(this, this.registries(), this.playerDataStorage));
        try {
            java.lang.reflect.Field field = org.bukkit.craftbukkit.CraftServer.class.getDeclaredField("bukkitVersion");
            field.setAccessible(true);
            field.set(this.server, "26.3-R0.1-SNAPSHOT");
        } catch (Throwable ignored) {
        }

        SpigotConfig.init((java.io.File) this.options.valueOf("spigot-settings"));
        this.paperConfigurations.initializeGlobalConfiguration(this.registryAccess());
        this.paperConfigurations.initializeWorldDefaultsConfiguration(this.registryAccess());

        // Intentionally skipped from initServer(): console thread, watchdog, metrics,
        // ban/op/whitelist files (Pumpkin owns those), TCP listener, plugin loading
        // (PatchBukkit's loader handles plugins) and level loading (bridged to Pumpkin).
    }

    @Override
    public String getServerVersion() {
        return "26.3";
    }

    /**
     * Creates the "shadow" overworld: a real {@link ServerLevel} with a void generator that
     * is never ticked, so it never loads or generates chunks.
     *
     * <p>Real {@code ServerPlayer}s need a level to be constructed in, {@code PlayerList}
     * looks up the level keyed {@code OVERWORLD} (scoreboard, join/quit), and only the
     * overworld branch of {@code createLevel} sets up CraftServer's scoreboard manager.
     * The actual blocks and chunks live in Pumpkin.
     */
    public void createPumpkinLevel(
        String name,
        UUID uuid,
        World.Environment env,
        ResourceKey<LevelStem> stemKey,
        ResourceKey<Level> dimKey,
        Holder<DimensionType> dimType,
        int spawnX,
        int spawnY,
        int spawnZ,
        float spawnYaw
    ) {
        RegistryAccess.Frozen registries = this.registryAccess();
        Holder<Biome> voidBiome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        FlatLevelGeneratorSettings voidSettings = new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of());
        LevelStem stem = new LevelStem(dimType, new FlatLevelSource(voidSettings));

        PaperWorldLoader.LoadedWorldData rawData = PaperWorldLoader.loadWorldData(this, dimKey, name);
        PaperWorldLoader.LoadedWorldData data = new PaperWorldLoader.LoadedWorldData(name, uuid, rawData.pdc(), rawData.levelOverrides());
        data.levelOverrides().setSpawn(LevelData.RespawnData.of(dimKey, new BlockPos(spawnX, spawnY, spawnZ), spawnYaw, 0.0F));
        data.levelOverrides().setInitialized(true);

        this.createLevel(
            stem,
            new PaperWorldLoader.WorldLoadingInfoAndData(
                new PaperWorldLoader.WorldLoadingInfo(env, stemKey, dimKey, true),
                data
            ),
            new LevelDataAndDimensions.WorldDataAndGenSettings(this.getWorldData(), this.getWorldGenSettings())
        );
    }

    void initWorlds() {
        GetWorldsResponse response = null;
        try {
            response = NativeBridgeFfi.getWorlds(EmptyRequest.getDefaultInstance());
        } catch (Throwable ignored) {}

        if (response == null || response.getWorldUuidsList().isEmpty()) {
            createShadowOverworld();
            return;
        }

        List<PumpkinWorldInfo> worldInfos = new ArrayList<>();
        for (patchbukkit.common.UUID u : response.getWorldUuidsList()) {
            try {
                GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(
                    GetWorldInfoRequest.newBuilder().setWorldUuid(u).build()
                );
                if (res != null) {
                    worldInfos.add(new PumpkinWorldInfo(UUID.fromString(u.getValue()), res));
                }
            } catch (Throwable ignored) {}
        }

        if (worldInfos.isEmpty()) {
            createShadowOverworld();
            return;
        }

        RegistryAccess.Frozen registries = this.registryAccess();
        Holder<DimensionType> overworldType =
            registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        Holder<DimensionType> netherType =
            registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.NETHER);
        Holder<DimensionType> endType =
            registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.END);

        // Scoreboard & CraftScoreboardManager require LevelStem.OVERWORLD on first createLevel call
        PumpkinWorldInfo overworld = null;
        for (PumpkinWorldInfo info : worldInfos) {
            String dim = info.res.getDimension();
            if (!dim.contains("nether") && !dim.contains("end")) {
                overworld = info;
                break;
            }
        }

        if (overworld != null) {
            String name = overworld.res.getName().isEmpty() ? this.settings.getProperties().levelName : overworld.res.getName();
            createPumpkinLevel(
                name,
                overworld.uuid,
                World.Environment.NORMAL,
                LevelStem.OVERWORLD,
                Level.OVERWORLD,
                overworldType,
                overworld.res.getSpawnX(),
                overworld.res.getSpawnY(),
                overworld.res.getSpawnZ(),
                overworld.res.getSpawnAngle()
            );
        } else {
            createShadowOverworld();
        }

        // Create remaining dimensions
        for (PumpkinWorldInfo info : worldInfos) {
            if (info == overworld) continue;
            String dim = info.res.getDimension();
            World.Environment env;
            ResourceKey<LevelStem> stemKey;
            ResourceKey<Level> dimKey;
            Holder<DimensionType> dimType;

            if (dim.contains("nether")) {
                env = World.Environment.NETHER;
                stemKey = LevelStem.NETHER;
                dimKey = Level.NETHER;
                dimType = netherType;
            } else if (dim.contains("end")) {
                env = World.Environment.THE_END;
                stemKey = LevelStem.END;
                dimKey = Level.END;
                dimType = endType;
            } else {
                env = World.Environment.NORMAL;
                String safeName = info.res.getName().toLowerCase().replace(' ', '_');
                stemKey = ResourceKey.create(Registries.LEVEL_STEM, Identifier.fromNamespaceAndPath("minecraft", safeName));
                dimKey = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("minecraft", safeName));
                dimType = overworldType;
            }

            String name = info.res.getName().isEmpty() ? "world_" + env.name().toLowerCase() : info.res.getName();
            createPumpkinLevel(
                name,
                info.uuid,
                env,
                stemKey,
                dimKey,
                dimType,
                info.res.getSpawnX(),
                info.res.getSpawnY(),
                info.res.getSpawnZ(),
                info.res.getSpawnAngle()
            );
        }

        // Synchronize entities from Pumpkin for each level
        for (ServerLevel level : this.getAllLevels()) {
            UUID worldUid = (level.getWorld() != null) ? level.getWorld().getUID() : null;
            if (worldUid != null) {
                EntityBridge.syncEntitiesFromPumpkin(level, worldUid);
            }
        }
    }

    private record PumpkinWorldInfo(UUID uuid, GetWorldInfoResponse res) {}

    void createShadowOverworld() {
        RegistryAccess.Frozen registries = this.registryAccess();
        Holder<DimensionType> overworldType =
            registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        String levelName = this.settings.getProperties().levelName;
        PaperWorldLoader.LoadedWorldData data = PaperWorldLoader.loadWorldData(this, Level.OVERWORLD, levelName);
        createPumpkinLevel(
            levelName,
            data.uuid(),
            World.Environment.NORMAL,
            LevelStem.OVERWORLD,
            Level.OVERWORLD,
            overworldType,
            0,
            64,
            0,
            0.0F
        );
    }

    @Override
    protected boolean initServer() {
        throw new UnsupportedOperationException(
            "PumpkinDedicatedServer is headless; Pumpkin runs the server, use HeadlessPaperServer.boot"
        );
    }

    /**
     * The tick loop never runs, so there is nothing to halt. Paper's ServerShutdownThread
     * (a JVM shutdown hook) calls this and then waits for {@code hasFullyShutdown}, which
     * would otherwise block JVM exit forever. Pumpkin owns the real shutdown.
     */
    @Override
    public void halt(boolean waitForShutdown, boolean isRestarting) {
        this.hasFullyShutdown = true;
    }

    /** Never save/stop vanilla worlds or players: they live in Pumpkin. */
    @Override
    protected void stopServer() {
        this.hasFullyShutdown = true;
    }
}
