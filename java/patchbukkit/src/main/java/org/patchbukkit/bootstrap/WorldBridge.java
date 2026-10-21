package org.patchbukkit.bootstrap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftWorldBorder;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.world.CreateWorldExplosionRequest;
import patchbukkit.world.GetBlockDataRequest;
import patchbukkit.world.GetBlockDataResponse;
import patchbukkit.world.GetWorldBorderRequest;
import patchbukkit.world.GetWorldGamerulesRequest;
import patchbukkit.world.GetWorldGamerulesResponse;
import patchbukkit.world.GetWorldInfoRequest;
import patchbukkit.world.GetWorldInfoResponse;
import patchbukkit.world.PlayWorldSoundRequest;
import patchbukkit.world.SaveWorldRequest;
import patchbukkit.world.SetBlockDataRequest;
import patchbukkit.world.SetChunkForceLoadedRequest;
import patchbukkit.world.SetWorldBorderRequest;
import patchbukkit.world.SetWorldDifficultyRequest;
import patchbukkit.world.SetWorldGameruleRequest;
import patchbukkit.world.SetWorldPvpRequest;
import patchbukkit.world.SetWorldSpawnRequest;
import patchbukkit.world.SetWorldTimeRequest;
import patchbukkit.world.SetWorldWeatherRequest;
import patchbukkit.world.SpawnParticleRequest;
import patchbukkit.world.WorldBorderData;

/**
 * Bridges Paper's NMS world and chunk system to Pumpkin.
 *
 * <p>Paper's DedicatedServer is headless (tick loop never runs and chunks are not ticked
 * by Moonrise). This class intercepts block and chunk queries on {@link Level},
 * {@link ServerChunkCache}, and {@link EmptyLevelChunk}, delegating block reads and writes
 * directly to Pumpkin's world state via FFI.
 */
public final class WorldBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final Map<String, BlockState> STATE_PARSE_CACHE = new ConcurrentHashMap<>();
    private static final Map<BlockState, String> STATE_SERIALIZE_CACHE = new ConcurrentHashMap<>();
    private static final BlockState[] STATE_BY_PUMPKIN_ID = new BlockState[65536];
    private static final Map<BlockState, Integer> PUMPKIN_ID_BY_STATE = new ConcurrentHashMap<>();
    private static final Map<Level, Map<Long, LevelChunk>> CHUNKS_BY_LEVEL = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private WorldBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            ByteBuddy byteBuddy = new ByteBuddy();

            // Intercept Level block queries and updates
            byteBuddy
                .redefine(Level.class)
                .method(ElementMatchers.named("getBlockState").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
                .method(ElementMatchers.named("setBlock").and(ElementMatchers.takesArguments(4)))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
                .method(ElementMatchers.named("hasChunk").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
                .method(ElementMatchers.named("getFluidState").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
                .method(ElementMatchers.named("getCraftServer").and(ElementMatchers.takesNoArguments()))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
                .make()
                .load(Level.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            // Intercept ServerChunkCache to provide non-blocking chunks without Moonrise tick loop
            byteBuddy
                .redefine(ServerChunkCache.class)
                .method(ElementMatchers.named("getChunk").and(ElementMatchers.takesArguments(4)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .method(ElementMatchers.named("getChunkNow").and(ElementMatchers.takesArguments(2)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .method(ElementMatchers.named("isChunkLoaded").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .make()
                .load(ServerChunkCache.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            // Intercept EmptyLevelChunk to route chunk-level block queries to Level
            byteBuddy
                .redefine(EmptyLevelChunk.class)
                .method(ElementMatchers.named("getBlockState").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("getBlockState").and(ElementMatchers.takesArguments(int.class, int.class, int.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("getFluidState").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("setBlockState").and(ElementMatchers.takesArguments(BlockPos.class, BlockState.class, int.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .make()
                .load(EmptyLevelChunk.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            // Intercept CraftWorld metadata and lifecycle mutations (time, weather, spawn, difficulty, save, force-loaded chunks)
            try {
                byteBuddy
                    .redefine(CraftWorld.class)
                    .visit(Advice.to(OnSyncWorldInfoAdvice.class).on(
                        ElementMatchers.named("getTime")
                            .or(ElementMatchers.named("getFullTime"))
                            .or(ElementMatchers.named("hasStorm"))
                            .or(ElementMatchers.named("isThundering"))
                    ))
                    .visit(Advice.to(OnPushWorldTimeAdvice.class).on(
                        ElementMatchers.named("setTime").or(ElementMatchers.named("setFullTime"))
                    ))
                    .visit(Advice.to(OnPushWorldWeatherAdvice.class).on(
                        ElementMatchers.named("setStorm")
                            .or(ElementMatchers.named("setThundering"))
                            .or(ElementMatchers.named("setWeatherDuration"))
                            .or(ElementMatchers.named("setThunderDuration"))
                            .or(ElementMatchers.named("setClearWeatherDuration"))
                    ))
                    .visit(Advice.to(OnPushWorldSpawnAdvice.class).on(
                        ElementMatchers.named("setSpawnLocation").and(ElementMatchers.takesArguments(int.class, int.class, int.class, float.class))
                    ))
                    .visit(Advice.to(OnPushWorldDifficultyAdvice.class).on(
                        ElementMatchers.named("setDifficulty").and(ElementMatchers.takesArguments(Difficulty.class))
                    ))
                    .visit(Advice.to(OnPushChunkForceLoadedAdvice.class).on(
                        ElementMatchers.named("setChunkForceLoaded").and(ElementMatchers.takesArguments(int.class, int.class, boolean.class))
                    ))
                    .visit(Advice.to(OnIsChunkForceLoadedAdvice.class).on(
                        ElementMatchers.named("isChunkForceLoaded").and(ElementMatchers.takesArguments(int.class, int.class))
                    ))
                    .visit(Advice.to(OnPushWorldSaveAdvice.class).on(
                        ElementMatchers.named("save").and(ElementMatchers.takesArguments(boolean.class))
                    ))
                    .visit(Advice.to(OnPushWorldPvpAdvice.class).on(
                        ElementMatchers.named("setPVP").and(ElementMatchers.takesArguments(boolean.class))
                    ))
                    .visit(Advice.to(OnPlayWorldSoundAdvice.class).on(
                        ElementMatchers.named("playSound").and(ElementMatchers.takesArguments(Location.class, String.class, SoundCategory.class, float.class, float.class, long.class))
                    ))
                    .visit(Advice.to(OnSpawnWorldParticleAdvice.class).on(
                        ElementMatchers.named("spawnParticle").and(ElementMatchers.takesArgument(0, Particle.class)).and(ElementMatchers.takesArgument(3, double.class))
                    ))
                    .visit(Advice.to(OnCreateExplosionAdvice.class).on(
                        ElementMatchers.named("createExplosion").and(ElementMatchers.takesArgument(0, double.class)).and(ElementMatchers.takesArgument(3, float.class))
                    ))
                    .visit(Advice.to(OnGetGameRuleValueStringAdvice.class).on(
                        ElementMatchers.named("getGameRuleValue").and(ElementMatchers.takesArguments(String.class))
                    ))
                    .visit(Advice.to(OnSetGameRuleValueStringAdvice.class).on(
                        ElementMatchers.named("setGameRuleValue").and(ElementMatchers.takesArguments(String.class, String.class))
                    ))
                    .visit(Advice.to(OnSetGameRuleAdvice.class).on(
                        ElementMatchers.named("setGameRule").and(ElementMatchers.takesArgument(0, GameRule.class))
                    ))
                    .make()
                    .load(CraftWorld.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(java.util.logging.Level.WARNING, "[PatchBukkit] Failed to hook CraftWorld in WorldBridge", t);
            }

            // Intercept CraftWorldBorder reads and writes
            try {
                byteBuddy
                    .redefine(CraftWorldBorder.class)
                    .visit(Advice.to(OnSyncWorldBorderAdvice.class).on(
                        ElementMatchers.named("getSize")
                            .or(ElementMatchers.named("getCenter"))
                            .or(ElementMatchers.named("getDamageBuffer"))
                            .or(ElementMatchers.named("getDamageAmount"))
                            .or(ElementMatchers.named("getWarningTimeTicks"))
                            .or(ElementMatchers.named("getWarningDistance"))
                    ))
                    .visit(Advice.to(OnPushWorldBorderAdvice.class).on(
                        ElementMatchers.named("setSize")
                            .or(ElementMatchers.named("changeSize"))
                            .or(ElementMatchers.named("setCenter"))
                            .or(ElementMatchers.named("setDamageBuffer"))
                            .or(ElementMatchers.named("setDamageAmount"))
                            .or(ElementMatchers.named("setWarningTimeTicks"))
                            .or(ElementMatchers.named("setWarningDistance"))
                            .or(ElementMatchers.named("reset"))
                    ))
                    .make()
                    .load(CraftWorldBorder.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(java.util.logging.Level.WARNING, "[PatchBukkit] Failed to hook CraftWorldBorder in WorldBridge", t);
            }

            LOGGER.info("[PatchBukkit] Successfully patched Level and ServerChunkCache with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(java.util.logging.Level.WARNING, "[PatchBukkit] Failed to patch Level/ServerChunkCache with ByteBuddy", t);
        }
    }

    public static BlockState parseBlockState(String blockStateStr) {
        if (blockStateStr == null || blockStateStr.isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        BlockState cached = STATE_PARSE_CACHE.get(blockStateStr);
        if (cached != null) {
            return cached;
        }

        try {
            if (!blockStateStr.contains("[")) {
                String key = blockStateStr.startsWith("minecraft:") ? blockStateStr : "minecraft:" + blockStateStr;
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(key));
                if (block != null) {
                    BlockState def = block.defaultBlockState();
                    STATE_PARSE_CACHE.put(blockStateStr, def);
                    return def;
                }
            }
            BlockState parsed = BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, blockStateStr, true).blockState();
            STATE_PARSE_CACHE.put(blockStateStr, parsed);
            return parsed;
        } catch (Throwable t) {
            return Blocks.AIR.defaultBlockState();
        }
    }

    public static String serializeBlockState(BlockState state) {
        if (state == null) {
            return "minecraft:air";
        }
        String cached = STATE_SERIALIZE_CACHE.get(state);
        if (cached != null) {
            return cached;
        }
        try {
            String serialized = BlockStateParser.serialize(state);
            STATE_SERIALIZE_CACHE.put(state, serialized);
            return serialized;
        } catch (Throwable t) {
            return "minecraft:air";
        }
    }

    public static BlockState getBlockState(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return Blocks.AIR.defaultBlockState();
        }
        UUID uuid = (level.getWorld() != null) ? level.getWorld().getUID() : null;
        if (uuid == null) {
            return Blocks.AIR.defaultBlockState();
        }

        try {
            if (NativeFastPathFfi.isInitialized()) {
                int id = NativeFastPathFfi.getBlockStateId(uuid, pos.getX(), pos.getY(), pos.getZ());
                if (id >= 0 && id < STATE_BY_PUMPKIN_ID.length) {
                    BlockState cached = STATE_BY_PUMPKIN_ID[id];
                    if (cached != null) {
                        return cached;
                    }
                    GetBlockDataRequest req = GetBlockDataRequest.newBuilder()
                        .setWorldUuid(BridgeUtils.convertUuid(uuid))
                        .setX(pos.getX())
                        .setY(pos.getY())
                        .setZ(pos.getZ())
                        .build();
                    GetBlockDataResponse res = NativeBridgeFfi.getBlockData(req);
                    if (res != null && !res.getBlockState().isEmpty()) {
                        BlockState parsed = parseBlockState(res.getBlockState());
                        STATE_BY_PUMPKIN_ID[id] = parsed;
                        PUMPKIN_ID_BY_STATE.putIfAbsent(parsed, id);
                        return parsed;
                    }
                }
            }

            GetBlockDataRequest req = GetBlockDataRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(uuid))
                .setX(pos.getX())
                .setY(pos.getY())
                .setZ(pos.getZ())
                .build();
            GetBlockDataResponse res = NativeBridgeFfi.getBlockData(req);
            if (res != null && !res.getBlockState().isEmpty()) {
                return parseBlockState(res.getBlockState());
            }
        } catch (Throwable ignored) {}

        return Blocks.AIR.defaultBlockState();
    }

    public static boolean setBlock(Level level, BlockPos pos, BlockState state, int flags, int updateLimit) {
        if (level == null || pos == null || state == null) {
            return false;
        }
        UUID uuid = (level.getWorld() != null) ? level.getWorld().getUID() : null;
        if (uuid == null) {
            return false;
        }

        try {
            boolean applyPhysics = (flags & 1) != 0;
            if (NativeFastPathFfi.isInitialized()) {
                Integer cachedId = PUMPKIN_ID_BY_STATE.get(state);
                if (cachedId != null) {
                    return NativeFastPathFfi.setBlockStateId(uuid, pos.getX(), pos.getY(), pos.getZ(), cachedId, applyPhysics);
                }
            }

            String blockStateStr = serializeBlockState(state);
            SetBlockDataRequest req = SetBlockDataRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(uuid))
                .setX(pos.getX())
                .setY(pos.getY())
                .setZ(pos.getZ())
                .setBlockState(blockStateStr)
                .setApplyPhysics(applyPhysics)
                .build();
            NativeBridgeFfi.setBlockData(req);

            if (NativeFastPathFfi.isInitialized()) {
                int id = NativeFastPathFfi.getBlockStateId(uuid, pos.getX(), pos.getY(), pos.getZ());
                if (id >= 0 && id < STATE_BY_PUMPKIN_ID.length) {
                    STATE_BY_PUMPKIN_ID[id] = state;
                    PUMPKIN_ID_BY_STATE.putIfAbsent(state, id);
                }
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static LevelChunk getChunk(ServerChunkCache chunkCache, int x, int z) {
        ServerLevel level = (ServerLevel) chunkCache.getLevel();
        long key = ChunkPos.pack(x, z);
        Map<Long, LevelChunk> levelChunks = CHUNKS_BY_LEVEL.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
        return levelChunks.computeIfAbsent(key, k -> {
            Holder<Biome> voidBiome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
            return new EmptyLevelChunk(level, new ChunkPos(x, z), voidBiome);
        });
    }

    public static final class LevelInterceptor {
        @RuntimeType
        public static BlockState getBlockState(
            @This Level level,
            @Argument(0) BlockPos pos
        ) {
            return WorldBridge.getBlockState(level, pos);
        }

        @RuntimeType
        public static boolean setBlock(
            @This Level level,
            @Argument(0) BlockPos pos,
            @Argument(1) BlockState state,
            @Argument(2) int flags,
            @Argument(3) int updateLimit
        ) {
            return WorldBridge.setBlock(level, pos, state, flags, updateLimit);
        }

        @RuntimeType
        public static boolean hasChunk(
            @This Level level,
            @Argument(0) int chunkX,
            @Argument(1) int chunkZ
        ) {
            return true;
        }

        @RuntimeType
        public static FluidState getFluidState(
            @This Level level,
            @Argument(0) BlockPos pos
        ) {
            BlockState state = WorldBridge.getBlockState(level, pos);
            return state != null ? state.getFluidState() : Fluids.EMPTY.defaultFluidState();
        }

        @RuntimeType
        public static org.bukkit.craftbukkit.CraftServer getCraftServer(@This Level level) {
            net.minecraft.server.dedicated.DedicatedServer server = HeadlessPaperServer.get();
            if (server != null && server.server != null) {
                return server.server;
            }
            if (level instanceof ServerLevel serverLevel && serverLevel.getServer() != null && serverLevel.getServer().server != null) {
                return serverLevel.getServer().server;
            }
            return org.bukkit.Bukkit.getServer() instanceof org.bukkit.craftbukkit.CraftServer cs ? cs : null;
        }
    }

    public static final class ChunkCacheInterceptor {
        @RuntimeType
        public static ChunkAccess getChunk(
            @This ServerChunkCache chunkCache,
            @Argument(0) int x,
            @Argument(1) int z,
            @Argument(2) ChunkStatus targetStatus,
            @Argument(3) boolean loadOrGenerate
        ) {
            return WorldBridge.getChunk(chunkCache, x, z);
        }

        @RuntimeType
        public static LevelChunk getChunkNow(
            @This ServerChunkCache chunkCache,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return WorldBridge.getChunk(chunkCache, x, z);
        }

        @RuntimeType
        public static boolean isChunkLoaded(
            @This ServerChunkCache chunkCache,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return true;
        }
    }

    public static final class EmptyChunkInterceptor {
        @RuntimeType
        public static BlockState getBlockState(
            @This EmptyLevelChunk chunk,
            @Argument(0) BlockPos pos
        ) {
            return chunk.getLevel().getBlockState(pos);
        }

        @RuntimeType
        public static BlockState getBlockStateCoords(
            @This EmptyLevelChunk chunk,
            @Argument(0) int x,
            @Argument(1) int y,
            @Argument(2) int z
        ) {
            return chunk.getLevel().getBlockState(new BlockPos(x, y, z));
        }

        @RuntimeType
        public static FluidState getFluidState(
            @This EmptyLevelChunk chunk,
            @Argument(0) BlockPos pos
        ) {
            return chunk.getLevel().getFluidState(pos);
        }

        /**
         * Plugins such as WorldEdit write blocks straight into the chunk instead of going through
         * {@link Level#setBlock}. The shadow world only has empty chunks, which silently drop the
         * write, so route it to the level (and from there to Pumpkin).
         */
        @RuntimeType
        public static BlockState setBlockState(
            @This EmptyLevelChunk chunk,
            @Argument(0) BlockPos pos,
            @Argument(1) BlockState state,
            @Argument(2) int flags
        ) {
            Level level = chunk.getLevel();
            BlockState previous = level.getBlockState(pos);
            return level.setBlock(pos, state, flags) ? previous : null;
        }
    }

    public static void syncWorldInfoFromPumpkin(CraftWorld world) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(
                GetWorldInfoRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null) {
                world.setTime(res.getTime());
                world.setStorm(res.getIsStorm());
                world.setThundering(res.getIsThundering());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWorldTimeToPumpkin(CraftWorld world) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setWorldTime(
                SetWorldTimeRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setTime(world.getTime())
                    .setFullTime(world.getFullTime())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWorldWeatherToPumpkin(CraftWorld world) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setWorldWeather(
                SetWorldWeatherRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setStorm(world.hasStorm())
                    .setThundering(world.isThundering())
                    .setWeatherDuration(world.getWeatherDuration())
                    .setThunderDuration(world.getThunderDuration())
                    .setClearWeatherDuration(world.getClearWeatherDuration())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWorldSpawnToPumpkin(CraftWorld world, int x, int y, int z, float angle) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setWorldSpawn(
                SetWorldSpawnRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setX(x)
                    .setY(y)
                    .setZ(z)
                    .setAngle(angle)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWorldDifficultyToPumpkin(CraftWorld world, Difficulty difficulty) {
        if (world == null || difficulty == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setWorldDifficulty(
                SetWorldDifficultyRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setDifficulty(difficulty.name())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushChunkForceLoadedToPumpkin(CraftWorld world, int x, int z, boolean forced) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setChunkForceLoaded(
                SetChunkForceLoadedRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setX(x)
                    .setZ(z)
                    .setForced(forced)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWorldSaveToPumpkin(CraftWorld world) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.saveWorld(
                SaveWorldRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnSyncWorldInfoAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftWorld world) {
            WorldBridge.syncWorldInfoFromPumpkin(world);
        }
    }

    public static final class OnPushWorldTimeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world) {
            WorldBridge.pushWorldTimeToPumpkin(world);
        }
    }

    public static final class OnPushWorldWeatherAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world) {
            WorldBridge.pushWorldWeatherToPumpkin(world);
        }
    }

    public static final class OnPushWorldSpawnAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) int x,
            @Advice.Argument(1) int y,
            @Advice.Argument(2) int z,
            @Advice.Argument(3) float angle
        ) {
            WorldBridge.pushWorldSpawnToPumpkin(world, x, y, z, angle);
        }
    }

    public static final class OnPushWorldDifficultyAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Argument(0) Difficulty difficulty) {
            WorldBridge.pushWorldDifficultyToPumpkin(world, difficulty);
        }
    }

    public static final class OnPushChunkForceLoadedAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) int x,
            @Advice.Argument(1) int z,
            @Advice.Argument(2) boolean forced
        ) {
            WorldBridge.pushChunkForceLoadedToPumpkin(world, x, z, forced);
        }
    }

    public static final class OnPushWorldSaveAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(@Advice.This CraftWorld world) {
            WorldBridge.pushWorldSaveToPumpkin(world);
            // Skip vanilla level save in headless mode if backed by Pumpkin
            return NativeFastPathFfi.isInitialized();
        }
    }

    public static void pushWorldPvpToPumpkin(CraftWorld world, boolean pvp) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setWorldPvp(
                SetWorldPvpRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setPvp(pvp)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPlayWorldSoundToPumpkin(
        CraftWorld world,
        Location loc,
        String sound,
        SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        if (world == null || loc == null || sound == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.playWorldSound(
                PlayWorldSoundRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setSound(sound)
                    .setCategory(category != null ? category.name() : "MASTER")
                    .setX(loc.getX())
                    .setY(loc.getY())
                    .setZ(loc.getZ())
                    .setVolume(volume)
                    .setPitch(pitch)
                    .setSeed(seed)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static byte[] encodeParticleData(Particle particle, Object data) {
        if (particle == null || !HeadlessPaperServer.isBooted()) {
            return new byte[0];
        }
        try {
            Object converted = org.bukkit.craftbukkit.CraftParticle.convertLegacy(data);
            net.minecraft.core.particles.ParticleOptions options =
                org.bukkit.craftbukkit.CraftParticle.createParticleParam(particle, converted);
            if (options instanceof net.minecraft.core.particles.SimpleParticleType) {
                return new byte[0];
            }
            io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
            try {
                net.minecraft.network.RegistryFriendlyByteBuf buf =
                    new net.minecraft.network.RegistryFriendlyByteBuf(
                        raw,
                        HeadlessPaperServer.get().registryAccess()
                    );
                @SuppressWarnings({"unchecked", "rawtypes"})
                net.minecraft.network.codec.StreamCodec codec = options.getType().streamCodec();
                codec.encode(buf, options);
                byte[] bytes = new byte[raw.readableBytes()];
                raw.readBytes(bytes);
                return bytes;
            } finally {
                raw.release();
            }
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    public static void pushSpawnWorldParticleToPumpkin(
        CraftWorld world,
        Particle particle,
        double x,
        double y,
        double z,
        int count,
        double offsetX,
        double offsetY,
        double offsetZ,
        double extra,
        Object data,
        boolean force
    ) {
        if (world == null || particle == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            byte[] encodedData = encodeParticleData(particle, data);
            NativeBridgeFfi.spawnParticle(
                SpawnParticleRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setParticle(particle.getKey().asString())
                    .setX(x)
                    .setY(y)
                    .setZ(z)
                    .setCount(count)
                    .setOffsetX(offsetX)
                    .setOffsetY(offsetY)
                    .setOffsetZ(offsetZ)
                    .setExtra(extra)
                    .setForce(force)
                    .setData(com.google.protobuf.ByteString.copyFrom(encodedData))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushCreateExplosionToPumpkin(
        CraftWorld world,
        double x,
        double y,
        double z,
        float power,
        boolean setFire,
        boolean breakBlocks
    ) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.createWorldExplosion(
                CreateWorldExplosionRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setX(x)
                    .setY(y)
                    .setZ(z)
                    .setPower(power)
                    .setSetFire(setFire)
                    .setBreakBlocks(breakBlocks)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static String getGameRuleFromPumpkin(CraftWorld world, String rule, String fallback) {
        if (world == null || rule == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWorldGamerulesResponse res = NativeBridgeFfi.getWorldGamerules(
                GetWorldGamerulesRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null && res.getGamerulesMap().containsKey(rule)) {
                return res.getGamerulesMap().get(rule);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushGameRuleToPumpkin(CraftWorld world, String rule, String value) {
        if (world == null || rule == null || value == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setWorldGamerule(
                SetWorldGameruleRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setRule(rule)
                    .setValue(value)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void syncWorldBorderFromPumpkin(CraftWorldBorder border) {
        if (border == null || border.isVirtual() || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        World world = border.getWorld();
        if (world == null) return;
        SYNCING.set(true);
        try {
            WorldBorderData data = NativeBridgeFfi.getWorldBorder(
                GetWorldBorderRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (data != null && data.getSize() > 0.0) {
                border.setCenter(data.getCenterX(), data.getCenterZ());
                border.setSize(data.getSize());
                border.setDamageBuffer(data.getDamageBuffer());
                border.setDamageAmount(data.getDamagePerBlock());
                border.setWarningTimeTicks(data.getWarningTime());
                border.setWarningDistance(data.getWarningBlocks());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWorldBorderToPumpkin(CraftWorldBorder border) {
        if (border == null || border.isVirtual() || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        World world = border.getWorld();
        if (world == null) return;
        SYNCING.set(true);
        try {
            Location center = border.getCenter();
            WorldBorderData data = WorldBorderData.newBuilder()
                .setCenterX(center != null ? center.getX() : 0.0)
                .setCenterZ(center != null ? center.getZ() : 0.0)
                .setSize(border.getSize())
                .setTargetSize(border.getSize())
                .setDamageBuffer(border.getDamageBuffer())
                .setDamagePerBlock(border.getDamageAmount())
                .setWarningTime(border.getWarningTimeTicks())
                .setWarningBlocks(border.getWarningDistance())
                .build();
            NativeBridgeFfi.setWorldBorder(
                SetWorldBorderRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setBorder(data)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnPushWorldPvpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Argument(0) boolean pvp) {
            WorldBridge.pushWorldPvpToPumpkin(world, pvp);
        }
    }

    public static final class OnPlayWorldSoundAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) Location loc,
            @Advice.Argument(1) String sound,
            @Advice.Argument(2) SoundCategory category,
            @Advice.Argument(3) float volume,
            @Advice.Argument(4) float pitch,
            @Advice.Argument(5) long seed
        ) {
            WorldBridge.pushPlayWorldSoundToPumpkin(world, loc, sound, category, volume, pitch, seed);
        }
    }

    public static final class OnSpawnWorldParticleAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) Particle particle,
            @Advice.Argument(3) double x,
            @Advice.Argument(4) double y,
            @Advice.Argument(5) double z,
            @Advice.Argument(6) int count,
            @Advice.Argument(7) double offsetX,
            @Advice.Argument(8) double offsetY,
            @Advice.Argument(9) double offsetZ,
            @Advice.Argument(10) double extra,
            @Advice.Argument(13) Object data,
            @Advice.Argument(14) boolean force
        ) {
            WorldBridge.pushSpawnWorldParticleToPumpkin(world, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, force);
        }
    }

    public static final class OnCreateExplosionAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) double x,
            @Advice.Argument(1) double y,
            @Advice.Argument(2) double z,
            @Advice.Argument(3) float power,
            @Advice.Argument(4) boolean setFire,
            @Advice.Argument(5) boolean breakBlocks
        ) {
            WorldBridge.pushCreateExplosionToPumpkin(world, x, y, z, power, setFire, breakBlocks);
        }
    }

    public static final class OnGetGameRuleValueStringAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Argument(0) String rule, @Advice.Return(readOnly = false) String value) {
            value = WorldBridge.getGameRuleFromPumpkin(world, rule, value);
        }
    }

    public static final class OnSetGameRuleValueStringAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Argument(0) String rule, @Advice.Argument(1) String value) {
            WorldBridge.pushGameRuleToPumpkin(world, rule, value);
        }
    }

    public static final class OnSetGameRuleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Argument(0) GameRule<?> rule, @Advice.Argument(1) Object value) {
            if (rule != null && value != null) {
                WorldBridge.pushGameRuleToPumpkin(world, rule.getName(), String.valueOf(value));
            }
        }
    }

    public static final class OnSyncWorldBorderAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftWorldBorder border) {
            WorldBridge.syncWorldBorderFromPumpkin(border);
        }
    }

    public static final class OnPushWorldBorderAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorldBorder border) {
            WorldBridge.pushWorldBorderToPumpkin(border);
        }
    }

    public static boolean isChunkForceLoadedFromPumpkin(CraftWorld world, int x, int z, boolean fallback) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            patchbukkit.world.GetForceLoadedChunksResponse res = NativeBridgeFfi.getForceLoadedChunks(
                patchbukkit.world.GetForceLoadedChunksRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null) {
                for (patchbukkit.world.ChunkCoordProto coord : res.getChunksList()) {
                    if (coord.getX() == x && coord.getZ() == z) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static final class OnIsChunkForceLoadedAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) int x,
            @Advice.Argument(1) int z,
            @Advice.Return(readOnly = false) boolean forced
        ) {
            forced = WorldBridge.isChunkForceLoadedFromPumpkin(world, x, z, forced);
        }
    }
}
