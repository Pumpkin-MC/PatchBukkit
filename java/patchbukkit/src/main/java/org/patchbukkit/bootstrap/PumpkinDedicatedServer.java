package org.patchbukkit.bootstrap;

import com.mojang.datafixers.DataFixer;
import io.papermc.paper.world.PaperWorldLoader;
import java.lang.reflect.Field;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedServerSettings;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.bukkit.World;
import org.spigotmc.SpigotConfig;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.common.EmptyRequest;
import patchbukkit.world.GetWorldInfoRequest;
import patchbukkit.world.GetWorldInfoResponse;
import patchbukkit.world.GetWorldsResponse;

/**
 * Helper that initializes a real Paper {@link DedicatedServer} in headless mode.
 *
 * <p>Pumpkin owns the game loop, networking and worlds. The server instance is the
 * real {@link DedicatedServer} class so that plugin reflection and anticheat bytecode
 * see the authentic Paper server type rather than a wrapper subclass.
 */
public final class PumpkinDedicatedServer {

    private PumpkinDedicatedServer() {}

    /**
     * The headless subset of {@link DedicatedServer#initServer()}: everything needed
     * for the Bukkit/NMS object graph, nothing that binds sockets, starts threads,
     * loads worlds or writes files into the Pumpkin server root.
     */
    public static void initHeadless(DedicatedServer server) throws org.spongepowered.configurate.ConfigurateException {
        DedicatedServerSettings settings = server.settings;
        server.setUsesAuthentication(settings.getProperties().onlineMode);
        server.setPreventProxyConnections(settings.getProperties().preventProxyConnections);

        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setDispatcher(
            server.getCommands(),
            net.minecraft.commands.Commands.createValidationContext(server.registryAccess())
        );
        io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid();

        PlayerDataStorage pds = getPlayerDataStorage(server);
        // Creates the real CraftServer (PlayerList's constructor does
        // `server.server = new CraftServer(...)`, which also calls Bukkit.setServer).
        server.setPlayerList(new DedicatedPlayerList(server, server.registries(), pds));
        server.setPort(settings.getProperties().serverPort > 0 ? settings.getProperties().serverPort : 25565);
        server.setLocalIp(settings.getProperties().serverIp != null && !settings.getProperties().serverIp.isEmpty() ? settings.getProperties().serverIp : "127.0.0.1");
        try {
            Field field = org.bukkit.craftbukkit.CraftServer.class.getDeclaredField("bukkitVersion");
            field.setAccessible(true);
            field.set(server.server, "26.3-R0.1-SNAPSHOT");
            server.server.minimumAPI = org.bukkit.craftbukkit.util.ApiVersion.FLATTENING;
        } catch (Throwable ignored) {
        }

        SpigotConfig.init((java.io.File) server.options.valueOf("spigot-settings"));
        server.paperConfigurations.initializeGlobalConfiguration(server.registryAccess());
        server.paperConfigurations.initializeWorldDefaultsConfiguration(server.registryAccess());

        // Initialize ServerConnectionListener with a dummy bound channel so packet libraries
        // (such as PacketEvents / ProtocolLib) see isServerBound() == true and inject their
        // ServerChannelHandler into the server channel pipeline.
        try {
            net.minecraft.server.network.ServerConnectionListener scl = server.getConnection();
            if (scl != null) {
                Field channelsField =
                    net.minecraft.server.network.ServerConnectionListener.class.getDeclaredField("channels");
                channelsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<io.netty.channel.ChannelFuture> chList =
                    (java.util.List<io.netty.channel.ChannelFuture>) channelsField.get(scl);
                if (chList != null && chList.isEmpty()) {
                    io.netty.channel.embedded.EmbeddedChannel serverChannel =
                        new io.netty.channel.embedded.EmbeddedChannel();
                    chList.add(serverChannel.newSucceededFuture());
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static PlayerDataStorage getPlayerDataStorage(DedicatedServer server) {
        try {
            Field f = MinecraftServer.class.getDeclaredField("playerDataStorage");
            f.setAccessible(true);
            return (PlayerDataStorage) f.get(server);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to access playerDataStorage on MinecraftServer", t);
        }
    }

    /**
     * Creates the "shadow" overworld: a real {@link ServerLevel} with a void generator that
     * is never ticked, so it never loads or generates chunks.
     */
    public static void createPumpkinLevel(
        DedicatedServer server,
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
        RegistryAccess.Frozen registries = server.registryAccess();
        Holder<Biome> voidBiome = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        FlatLevelGeneratorSettings voidSettings = new FlatLevelGeneratorSettings(Optional.empty(), voidBiome, List.of());
        LevelStem stem = new LevelStem(dimType, new FlatLevelSource(voidSettings));

        PaperWorldLoader.LoadedWorldData rawData = PaperWorldLoader.loadWorldData(server, dimKey, name);
        PaperWorldLoader.LoadedWorldData data = new PaperWorldLoader.LoadedWorldData(name, uuid, rawData.pdc(), rawData.levelOverrides());
        data.levelOverrides().setSpawn(LevelData.RespawnData.of(dimKey, new BlockPos(spawnX, spawnY, spawnZ), spawnYaw, 0.0F));
        data.levelOverrides().setInitialized(true);

        server.createLevel(
            stem,
            new PaperWorldLoader.WorldLoadingInfoAndData(
                new PaperWorldLoader.WorldLoadingInfo(env, stemKey, dimKey, true),
                data
            ),
            new LevelDataAndDimensions.WorldDataAndGenSettings(server.getWorldData(), server.getWorldGenSettings())
        );
    }

    public static void initWorlds(DedicatedServer server) {
        GetWorldsResponse response = null;
        try {
            response = NativeBridgeFfi.getWorlds(EmptyRequest.getDefaultInstance());
        } catch (Throwable ignored) {}

        if (response == null || response.getWorldUuidsList().isEmpty()) {
            createShadowOverworld(server);
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
            createShadowOverworld(server);
            return;
        }

        RegistryAccess.Frozen registries = server.registryAccess();
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
            String name = overworld.res.getName().isEmpty() ? server.settings.getProperties().levelName : overworld.res.getName();
            createPumpkinLevel(
                server,
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
            createShadowOverworld(server);
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
                server,
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
        for (ServerLevel level : server.getAllLevels()) {
            UUID worldUid = (level.getWorld() != null) ? level.getWorld().getUID() : null;
            if (worldUid != null) {
                EntityBridge.syncEntitiesFromPumpkin(level, worldUid);
            }
        }
    }

    private record PumpkinWorldInfo(UUID uuid, GetWorldInfoResponse res) {}

    public static void createShadowOverworld(DedicatedServer server) {
        RegistryAccess.Frozen registries = server.registryAccess();
        Holder<DimensionType> overworldType =
            registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        String levelName = server.settings.getProperties().levelName;
        PaperWorldLoader.LoadedWorldData data = PaperWorldLoader.loadWorldData(server, Level.OVERWORLD, levelName);
        createPumpkinLevel(
            server,
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
}
