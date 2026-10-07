package org.patchbukkit.bootstrap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
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
import org.patchbukkit.bridge.BridgeUtils;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.world.GetBlockDataRequest;
import patchbukkit.world.GetBlockDataResponse;
import patchbukkit.world.SetBlockDataRequest;

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
    private static final Map<Level, Map<Long, LevelChunk>> CHUNKS_BY_LEVEL = new ConcurrentHashMap<>();

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
                .make()
                .load(EmptyLevelChunk.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

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
            String blockStateStr = serializeBlockState(state);
            boolean applyPhysics = (flags & 1) != 0;
            SetBlockDataRequest req = SetBlockDataRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(uuid))
                .setX(pos.getX())
                .setY(pos.getY())
                .setZ(pos.getZ())
                .setBlockState(blockStateStr)
                .setApplyPhysics(applyPhysics)
                .build();
            NativeBridgeFfi.setBlockData(req);
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
            return org.patchbukkit.PatchBukkitServer.getCraftServer();
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
    }
}
