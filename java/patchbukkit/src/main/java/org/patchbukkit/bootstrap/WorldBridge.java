package org.patchbukkit.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import io.papermc.paper.world.chunk.ChunkGenerationState;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.CraftRegionAccessor;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.CraftWorldBorder;
import org.bukkit.craftbukkit.block.CraftBlock;
import org.bukkit.craftbukkit.block.CraftBlockEntityState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.world.CreateWorldExplosionRequest;
import patchbukkit.world.GetBiomeRequest;
import patchbukkit.world.GetBiomeResponse;
import patchbukkit.world.GetBlockDataRequest;
import patchbukkit.world.GetBlockDataResponse;
import patchbukkit.world.GetBlockEntityNbtRequest;
import patchbukkit.world.GetBlockEntityNbtResponse;
import patchbukkit.world.GetHighestBlockYRequest;
import patchbukkit.world.GetHighestBlockYResponse;
import patchbukkit.world.GetLightLevelRequest;
import patchbukkit.world.GetLightLevelResponse;
import patchbukkit.world.GetWorldBorderRequest;
import patchbukkit.world.GetWorldGamerulesRequest;
import patchbukkit.world.GetWorldGamerulesResponse;
import patchbukkit.world.GetWorldInfoRequest;
import patchbukkit.world.GetWorldInfoResponse;
import patchbukkit.world.PlayWorldSoundRequest;
import patchbukkit.world.SaveWorldRequest;
import patchbukkit.world.SetBlockDataRequest;
import patchbukkit.world.SetBlockEntityNbtRequest;
import patchbukkit.world.SetChunkForceLoadedRequest;
import patchbukkit.world.SetWorldBorderRequest;
import patchbukkit.world.SetWorldDifficultyRequest;
import patchbukkit.world.SetWorldGameruleRequest;
import patchbukkit.world.SetWorldPvpRequest;
import patchbukkit.world.SetWorldSpawnRequest;
import patchbukkit.world.SetWorldTimeRequest;
import patchbukkit.world.SetWorldWeatherRequest;
import patchbukkit.world.SpawnParticleRequest;
import patchbukkit.world.StrikeLightningRequest;
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
    public static final Map<Level, Integer> VIEW_DISTANCES = new ConcurrentHashMap<>();
    public static final Map<Level, Integer> SIMULATION_DISTANCES = new ConcurrentHashMap<>();
    public static final Map<Level, Integer> SEND_VIEW_DISTANCES = new ConcurrentHashMap<>();
    public static final Map<Level, Map<Long, Set<Plugin>>> PLUGIN_CHUNK_TICKETS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private WorldBridge() {}

    public static void patch() {
        try {
            // 1. Intercept Level block, heightmap, and block entity queries/updates
            BridgePatcher.register(Level.class, "WorldBridge", builder -> builder
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
                .method(ElementMatchers.named("getHeight").and(ElementMatchers.takesArguments(Heightmap.Types.class, int.class, int.class)))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
                .method(ElementMatchers.named("getBlockEntity").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(LevelInterceptor.class))
            );

            // 2. Intercept ServerChunkCache to provide non-blocking chunks without Moonrise tick loop
            BridgePatcher.register(ServerChunkCache.class, "WorldBridge", builder -> builder
                .method(ElementMatchers.named("getChunk").and(ElementMatchers.takesArguments(4)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .method(ElementMatchers.named("getChunkNow").and(ElementMatchers.takesArguments(2)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .method(ElementMatchers.named("getChunkForLighting").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .method(ElementMatchers.named("isChunkLoaded").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(ChunkCacheInterceptor.class))
                .method(ElementMatchers.named("getChunkAtIfLoadedImmediately")
                    .or(ElementMatchers.named("getChunkAtIfCachedImmediately"))
                    .or(ElementMatchers.named("getChunkAtImmediately"))
                    .and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(ChunkIfLoadedInterceptor.class))
            );

            // 3. Intercept EmptyLevelChunk to route chunk-level block/biome/block-entity queries to Level/Pumpkin
            BridgePatcher.register(EmptyLevelChunk.class, "WorldBridge", builder -> builder
                .method(ElementMatchers.named("getBlockState").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("getBlockState").and(ElementMatchers.takesArguments(int.class, int.class, int.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("getFluidState").and(ElementMatchers.takesArguments(BlockPos.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("setBlockState").and(ElementMatchers.takesArguments(BlockPos.class, BlockState.class, int.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("getNoiseBiome").and(ElementMatchers.takesArguments(int.class, int.class, int.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
                .method(ElementMatchers.named("getBlockEntity").and(ElementMatchers.takesArguments(BlockPos.class, LevelChunk.EntityCreationType.class)))
                .intercept(MethodDelegation.to(EmptyChunkInterceptor.class))
            );

            // 4. Intercept CraftWorld metadata, chunk helpers, sounds, explosions, lightning, heightmaps, and gamerules
            BridgePatcher.register(CraftWorld.class, "WorldBridge", builder -> builder
                .method(ElementMatchers.named("isChunkGenerated").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("isChunkLoaded").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("isChunkLoaded").and(ElementMatchers.takesArguments(Chunk.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("isChunkInUse").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("refreshChunk").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getLoadedChunks").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getChunkCount").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getTileEntityCount").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getTickableTileEntityCount").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getPlayerCount").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getChunkGenerationState").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getChunkGenerationStateAsync").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getPlayersSeeingChunk").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getPlayersSeeingChunk").and(ElementMatchers.takesArguments(Chunk.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getViewDistance").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("setViewDistance").and(ElementMatchers.takesArguments(int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getSimulationDistance").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("setSimulationDistance").and(ElementMatchers.takesArguments(int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getSendViewDistance").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("setSendViewDistance").and(ElementMatchers.takesArguments(int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("addPluginChunkTicket").and(ElementMatchers.takesArguments(int.class, int.class, Plugin.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("removePluginChunkTicket").and(ElementMatchers.takesArguments(int.class, int.class, Plugin.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("removePluginChunkTickets").and(ElementMatchers.takesArguments(Plugin.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getPluginChunkTickets").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getPluginChunkTickets").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getForceLoadedChunks").and(ElementMatchers.takesArguments(0)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getChunkAtAsync").and(ElementMatchers.takesArguments(int.class, int.class, boolean.class, boolean.class, java.util.function.Consumer.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("getChunksAtAsync").and(ElementMatchers.takesArguments(int.class, int.class, int.class, int.class, boolean.class, Runnable.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("loadChunk").and(ElementMatchers.takesArguments(int.class, int.class, boolean.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("unloadChunk").and(ElementMatchers.takesArguments(int.class, int.class, boolean.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .method(ElementMatchers.named("unloadChunkRequest").and(ElementMatchers.takesArguments(int.class, int.class)))
                .intercept(MethodDelegation.to(CraftWorldChunkInterceptor.class))
                .visit(Advice.to(OnSyncWorldInfoAdvice.class).on(
                    ElementMatchers.named("getTime")
                        .or(ElementMatchers.named("getFullTime"))
                        .or(ElementMatchers.named("hasStorm"))
                        .or(ElementMatchers.named("isThundering"))
                ))
                .visit(Advice.to(OnGetWorldSeedAdvice.class).on(
                    ElementMatchers.named("getSeed").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetWorldPvpAdvice.class).on(
                    ElementMatchers.named("getPVP").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetWorldDifficultyAdvice.class).on(
                    ElementMatchers.named("getDifficulty").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetWorldSpawnAdvice.class).on(
                    ElementMatchers.named("getSpawnLocation").and(ElementMatchers.takesNoArguments())
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
                // Hook canonical 5-arg private setSpawnLocation(int, int, int, float, float) so setSpawnLocation(Location) is covered too
                .visit(Advice.to(OnPushWorldSpawnAdvice.class).on(
                    ElementMatchers.named("setSpawnLocation").and(
                        ElementMatchers.takesArguments(int.class, int.class, int.class, float.class, float.class)
                    )
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
                .visit(Advice.to(OnPlayWorldSoundStringAdvice.class).on(
                    ElementMatchers.named("playSound").and(ElementMatchers.takesArguments(Location.class, String.class, SoundCategory.class, float.class, float.class, long.class))
                ))
                .visit(Advice.to(OnPlayWorldSoundEnumAdvice.class).on(
                    ElementMatchers.named("playSound").and(ElementMatchers.takesArguments(Location.class, Sound.class, SoundCategory.class, float.class, float.class, long.class))
                ))
                .visit(Advice.to(OnSpawnWorldParticleAdvice.class).on(
                    ElementMatchers.named("spawnParticle").and(ElementMatchers.takesArgument(0, Particle.class)).and(ElementMatchers.takesArgument(3, double.class))
                ))
                // Hook ONLY canonical 8-arg private createExplosion to prevent duplicate explosions
                .visit(Advice.to(OnCreateExplosionAdvice.class).on(
                    ElementMatchers.named("createExplosion")
                        .and(ElementMatchers.takesArguments(8))
                        .and(ElementMatchers.takesArgument(0, double.class))
                        .and(ElementMatchers.takesArgument(3, float.class))
                ))
                // Hook canonical private strikeLightning0(Location, boolean) for both strikeLightning and strikeLightningEffect
                .visit(Advice.to(OnStrikeLightningAdvice.class).on(
                    ElementMatchers.named("strikeLightning0").and(ElementMatchers.takesArguments(Location.class, boolean.class))
                ))
                .visit(Advice.to(OnGetHighestBlockYAdvice.class).on(
                    ElementMatchers.named("getHighestBlockYAt").and(ElementMatchers.takesArguments(int.class, int.class, HeightMap.class))
                ))
                .visit(Advice.to(OnGetGameRuleValueStringAdvice.class).on(
                    ElementMatchers.named("getGameRuleValue").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnGetGameRuleValueTypedAdvice.class).on(
                    ElementMatchers.named("getGameRuleValue").and(ElementMatchers.takesArguments(GameRule.class))
                ))
                .visit(Advice.to(OnSetGameRuleValueStringAdvice.class).on(
                    ElementMatchers.named("setGameRuleValue").and(ElementMatchers.takesArguments(String.class, String.class))
                ))
                .visit(Advice.to(OnSetGameRuleAdvice.class).on(
                    ElementMatchers.named("setGameRule").and(ElementMatchers.takesArgument(0, GameRule.class))
                ))
            );

            // 5. Intercept CraftRegionAccessor.getBiome(int, int, int)
            BridgePatcher.register(CraftRegionAccessor.class, "WorldBridge", builder -> builder
                .visit(Advice.to(OnGetBiomeAdvice.class).on(
                    ElementMatchers.named("getBiome").and(ElementMatchers.takesArguments(int.class, int.class, int.class))
                ))
            );

            // 6. Intercept CraftBlock light queries
            BridgePatcher.register(CraftBlock.class, "WorldBridge", builder -> builder
                .visit(Advice.to(OnGetLightLevelAdvice.class).on(
                    ElementMatchers.named("getLightLevel").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetSkyLightAdvice.class).on(
                    ElementMatchers.named("getLightFromSky").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetBlockLightAdvice.class).on(
                    ElementMatchers.named("getLightFromBlocks").and(ElementMatchers.takesNoArguments())
                ))
            );

            // 7. Intercept CraftBlockEntityState.update(boolean, boolean) to persist sign/chest/block-entity NBT to Pumpkin
            BridgePatcher.register(CraftBlockEntityState.class, "WorldBridge", builder -> builder
                .visit(Advice.to(OnBlockEntityStateUpdateAdvice.class).on(
                    ElementMatchers.named("update").and(ElementMatchers.takesArguments(boolean.class, boolean.class))
                ))
            );

            // 8. Intercept CraftWorldBorder reads and writes
            BridgePatcher.register(CraftWorldBorder.class, "WorldBridge", builder -> builder
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
            );

            LOGGER.info("[PatchBukkit] Registered WorldBridge hooks with BridgePatcher");
        } catch (Throwable t) {
            LOGGER.log(java.util.logging.Level.WARNING, "[PatchBukkit] Failed to register WorldBridge hooks", t);
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
        return levelChunks.computeIfAbsent(key, k -> newChunk(level, x, z));
    }

    /** The tracked chunk, or an untracked one, so a lookup alone does not make CraftWorld report it as loaded. */
    public static LevelChunk peekChunk(ServerChunkCache chunkCache, int x, int z) {
        ServerLevel level = (ServerLevel) chunkCache.getLevel();
        Map<Long, LevelChunk> levelChunks = CHUNKS_BY_LEVEL.get(level);
        LevelChunk chunk = levelChunks == null ? null : levelChunks.get(ChunkPos.pack(x, z));
        return chunk != null ? chunk : newChunk(level, x, z);
    }

    private static LevelChunk newChunk(ServerLevel level, int x, int z) {
        Holder<Biome> voidBiome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.THE_VOID);
        return new EmptyLevelChunk(level, new ChunkPos(x, z), voidBiome);
    }

    public static final class LevelInterceptor {
        public static BlockState getBlockState(
            @This Level level,
            @Argument(0) BlockPos pos
        ) {
            return WorldBridge.getBlockState(level, pos);
        }

        public static boolean setBlock(
            @This Level level,
            @Argument(0) BlockPos pos,
            @Argument(1) BlockState state,
            @Argument(2) int flags,
            @Argument(3) int updateLimit
        ) {
            return WorldBridge.setBlock(level, pos, state, flags, updateLimit);
        }

        public static boolean hasChunk(
            @This Level level,
            @Argument(0) int chunkX,
            @Argument(1) int chunkZ
        ) {
            return true;
        }

        public static FluidState getFluidState(
            @This Level level,
            @Argument(0) BlockPos pos
        ) {
            BlockState state = WorldBridge.getBlockState(level, pos);
            return state != null ? state.getFluidState() : Fluids.EMPTY.defaultFluidState();
        }

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

        public static int getHeight(
            @This Level level,
            @Argument(0) Heightmap.Types heightmapType,
            @Argument(1) int x,
            @Argument(2) int z
        ) {
            int hm = 0;
            if (heightmapType == Heightmap.Types.MOTION_BLOCKING) {
                hm = 1;
            } else if (heightmapType == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES) {
                hm = 2;
            }
            int y = WorldBridge.getHighestBlockYFromPumpkin(level, x, z, hm, Integer.MIN_VALUE);
            return y != Integer.MIN_VALUE ? y : level.getMinY();
        }

        public static BlockEntity getBlockEntity(
            @This Level level,
            @Argument(0) BlockPos pos
        ) {
            return WorldBridge.getBlockEntityFromPumpkin(level, pos);
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
        public static LightChunk getChunkForLighting(
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

    public static final class ChunkIfLoadedInterceptor {
        @RuntimeType
        public static LevelChunk getChunkIfLoaded(
            @This ServerChunkCache chunkCache,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return WorldBridge.peekChunk(chunkCache, x, z);
        }
    }

    public static final class EmptyChunkInterceptor {
        public static BlockState getBlockState(
            @This EmptyLevelChunk chunk,
            @Argument(0) BlockPos pos
        ) {
            return chunk.getLevel().getBlockState(pos);
        }

        public static BlockState getBlockStateCoords(
            @This EmptyLevelChunk chunk,
            @Argument(0) int x,
            @Argument(1) int y,
            @Argument(2) int z
        ) {
            return chunk.getLevel().getBlockState(new BlockPos(x, y, z));
        }

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

        public static Holder<Biome> getNoiseBiome(
            @This EmptyLevelChunk chunk,
            @Argument(0) int quartX,
            @Argument(1) int quartY,
            @Argument(2) int quartZ
        ) {
            return WorldBridge.getNoiseBiomeFromPumpkin(chunk.getLevel(), quartX << 2, quartY << 2, quartZ << 2);
        }

        public static BlockEntity getBlockEntity(
            @This EmptyLevelChunk chunk,
            @Argument(0) BlockPos pos,
            @Argument(1) LevelChunk.EntityCreationType creationType
        ) {
            return WorldBridge.getBlockEntityFromPumpkin(chunk.getLevel(), pos);
        }
    }

    public static final class CraftWorldChunkInterceptor {

        @RuntimeType
        public static boolean isChunkGenerated(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            UUID uid = (world != null) ? world.getUID() : null;
            if (uid != null && NativeFastPathFfi.isInitialized()) {
                return NativeFastPathFfi.isChunkGenerated(
                    uid.getMostSignificantBits(),
                    uid.getLeastSignificantBits(),
                    x,
                    z
                ) != 0;
            }
            return false;
        }

        @RuntimeType
        public static void getChunkAtAsync(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z,
            @Argument(2) boolean gen,
            @Argument(3) boolean urgent,
            @Argument(4) java.util.function.Consumer<? super org.bukkit.Chunk> cb
        ) {
            UUID uid = (world != null) ? world.getUID() : null;
            long msb = uid != null ? uid.getMostSignificantBits() : 0;
            long lsb = uid != null ? uid.getLeastSignificantBits() : 0;

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                if (NativeFastPathFfi.isInitialized()) {
                    NativeFastPathFfi.loadChunk(msb, lsb, x, z, gen ? 1 : 0);
                }
            }).whenComplete((res, err) -> {
                org.bukkit.Chunk chunk = world.getChunkAt(x, z);
                if (cb != null) {
                    try {
                        cb.accept(chunk);
                    } catch (Throwable t) {
                        LOGGER.log(java.util.logging.Level.WARNING, "Error in getChunkAtAsync callback", t);
                    }
                }
            });
        }

        @RuntimeType
        public static void getChunksAtAsync(
            @This CraftWorld world,
            @Argument(0) int minX,
            @Argument(1) int minZ,
            @Argument(2) int maxX,
            @Argument(3) int maxZ,
            @Argument(4) boolean gen,
            @Argument(5) Runnable cb
        ) {
            UUID uid = (world != null) ? world.getUID() : null;
            long msb = uid != null ? uid.getMostSignificantBits() : 0;
            long lsb = uid != null ? uid.getLeastSignificantBits() : 0;

            java.util.concurrent.CompletableFuture.runAsync(() -> {
                if (NativeFastPathFfi.isInitialized()) {
                    for (int x = minX; x <= maxX; x++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            NativeFastPathFfi.loadChunk(msb, lsb, x, z, gen ? 1 : 0);
                        }
                    }
                }
            }).whenComplete((res, err) -> {
                if (cb != null) {
                    try {
                        cb.run();
                    } catch (Throwable t) {
                        LOGGER.log(java.util.logging.Level.WARNING, "Error in getChunksAtAsync callback", t);
                    }
                }
            });
        }

        @RuntimeType
        public static boolean loadChunk(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z,
            @Argument(2) boolean generate
        ) {
            UUID uid = (world != null) ? world.getUID() : null;
            if (uid != null && NativeFastPathFfi.isInitialized()) {
                int res = NativeFastPathFfi.loadChunk(
                    uid.getMostSignificantBits(),
                    uid.getLeastSignificantBits(),
                    x,
                    z,
                    generate ? 1 : 0
                );
                if (res != 0 || generate) {
                    world.getChunkAt(x, z);
                    return true;
                }
                return false;
            }
            world.getChunkAt(x, z);
            return true;
        }

        @RuntimeType
        public static boolean unloadChunk(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z,
            @Argument(2) boolean save
        ) {
            if (world != null && world.getHandle() != null) {
                Map<Long, LevelChunk> map = CHUNKS_BY_LEVEL.get(world.getHandle());
                if (map != null) {
                    map.remove(ChunkPos.pack(x, z));
                }
            }
            return true;
        }

        @RuntimeType
        public static boolean unloadChunkRequest(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            if (world != null && world.getHandle() != null) {
                Map<Long, LevelChunk> map = CHUNKS_BY_LEVEL.get(world.getHandle());
                if (map != null) {
                    map.remove(ChunkPos.pack(x, z));
                }
            }
            return true;
        }

        @RuntimeType
        public static boolean isChunkLoaded(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            if (world == null || world.getHandle() == null) return false;
            Map<Long, LevelChunk> map = CHUNKS_BY_LEVEL.get(world.getHandle());
            return map != null && map.containsKey(ChunkPos.pack(x, z));
        }

        @RuntimeType
        public static boolean isChunkLoaded(
            @This CraftWorld world,
            @Argument(0) Chunk chunk
        ) {
            if (chunk == null) {
                throw new IllegalArgumentException("null chunk");
            }
            return isChunkLoaded(world, chunk.getX(), chunk.getZ());
        }

        @RuntimeType
        public static boolean isChunkInUse(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return isChunkLoaded(world, x, z);
        }

        @RuntimeType
        public static boolean refreshChunk(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return world != null && isChunkLoaded(world, x, z);
        }

        @RuntimeType
        public static Chunk[] getLoadedChunks(@This CraftWorld world) {
            if (world == null || world.getHandle() == null) return new Chunk[0];
            Map<Long, LevelChunk> map = CHUNKS_BY_LEVEL.get(world.getHandle());
            if (map == null || map.isEmpty()) {
                return new Chunk[0];
            }
            return map.values().stream()
                .map(lc -> (Chunk) new CraftChunk(lc))
                .toArray(Chunk[]::new);
        }

        @RuntimeType
        public static int getChunkCount(@This CraftWorld world) {
            if (world == null || world.getHandle() == null) return 0;
            Map<Long, LevelChunk> map = CHUNKS_BY_LEVEL.get(world.getHandle());
            return map != null ? map.size() : 0;
        }

        @RuntimeType
        public static int getTileEntityCount(@This CraftWorld world) {
            if (world == null || world.getHandle() == null) return 0;
            Map<Long, LevelChunk> map = CHUNKS_BY_LEVEL.get(world.getHandle());
            if (map == null || map.isEmpty()) {
                return 0;
            }
            int size = 0;
            for (LevelChunk chunk : map.values()) {
                if (chunk != null && chunk.blockEntities != null) {
                    size += chunk.blockEntities.size();
                }
            }
            return size;
        }

        @RuntimeType
        public static int getTickableTileEntityCount(@This CraftWorld world) {
            if (world == null || world.getHandle() == null || world.getHandle().blockEntityTickers == null) {
                return 0;
            }
            return world.getHandle().blockEntityTickers.size();
        }

        @RuntimeType
        public static int getPlayerCount(@This CraftWorld world) {
            if (world == null) return 0;
            int count = 0;
            Collection<? extends Player> players = org.bukkit.Bukkit.getOnlinePlayers();
            if (players != null) {
                for (Player player : players) {
                    if (player != null && world.equals(player.getWorld())) {
                        count++;
                    }
                }
            }
            return count;
        }

        @RuntimeType
        public static ChunkGenerationState getChunkGenerationState(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return isChunkGenerated(world, x, z)
                ? ChunkGenerationState.GENERATED
                : ChunkGenerationState.NOT_GENERATED;
        }

        @RuntimeType
        public static CompletableFuture<ChunkGenerationState> getChunkGenerationStateAsync(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            return CompletableFuture.supplyAsync(() -> getChunkGenerationState(world, x, z));
        }

        @RuntimeType
        public static Collection<Player> getPlayersSeeingChunk(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            if (world == null || !isChunkLoaded(world, x, z)) {
                return Collections.emptySet();
            }
            int viewDistance = getViewDistance(world);
            List<Player> result = new ArrayList<>();
            Collection<? extends Player> players = org.bukkit.Bukkit.getOnlinePlayers();
            if (players != null) {
                for (Player player : players) {
                    if (player == null || !world.equals(player.getWorld())) continue;
                    Location loc = player.getLocation();
                    if (loc == null) continue;
                    int px = loc.getBlockX() >> 4;
                    int pz = loc.getBlockZ() >> 4;
                    if (Math.max(Math.abs(px - x), Math.abs(pz - z)) <= viewDistance) {
                        result.add(player);
                    }
                }
            }
            return Collections.unmodifiableCollection(result);
        }

        @RuntimeType
        public static Collection<Player> getPlayersSeeingChunk(
            @This CraftWorld world,
            @Argument(0) Chunk chunk
        ) {
            if (chunk == null) {
                throw new IllegalArgumentException("chunk cannot be null");
            }
            return getPlayersSeeingChunk(world, chunk.getX(), chunk.getZ());
        }

        @RuntimeType
        public static int getViewDistance(@This CraftWorld world) {
            if (world == null || world.getHandle() == null) return 10;
            Integer dist = VIEW_DISTANCES.get(world.getHandle());
            if (dist != null) return dist;
            try {
                if (org.bukkit.Bukkit.getServer() != null) {
                    return org.bukkit.Bukkit.getServer().getViewDistance();
                }
            } catch (Throwable ignored) {}
            return 10;
        }

        @RuntimeType
        public static void setViewDistance(@This CraftWorld world, @Argument(0) int viewDistance) {
            if (world != null && world.getHandle() != null) {
                VIEW_DISTANCES.put(world.getHandle(), Math.max(2, Math.min(32, viewDistance)));
            }
        }

        @RuntimeType
        public static int getSimulationDistance(@This CraftWorld world) {
            if (world == null || world.getHandle() == null) return 10;
            Integer dist = SIMULATION_DISTANCES.get(world.getHandle());
            if (dist != null) return dist;
            try {
                if (org.bukkit.Bukkit.getServer() != null) {
                    return org.bukkit.Bukkit.getServer().getSimulationDistance();
                }
            } catch (Throwable ignored) {}
            return 10;
        }

        @RuntimeType
        public static void setSimulationDistance(@This CraftWorld world, @Argument(0) int simulationDistance) {
            if (world != null && world.getHandle() != null) {
                SIMULATION_DISTANCES.put(world.getHandle(), Math.max(2, Math.min(32, simulationDistance)));
            }
        }

        @RuntimeType
        public static int getSendViewDistance(@This CraftWorld world) {
            if (world == null || world.getHandle() == null) return 10;
            Integer dist = SEND_VIEW_DISTANCES.get(world.getHandle());
            if (dist != null) return dist;
            return getViewDistance(world);
        }

        @RuntimeType
        public static void setSendViewDistance(@This CraftWorld world, @Argument(0) int sendViewDistance) {
            if (world != null && world.getHandle() != null) {
                SEND_VIEW_DISTANCES.put(world.getHandle(), sendViewDistance);
            }
        }

        @RuntimeType
        public static boolean addPluginChunkTicket(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z,
            @Argument(2) Plugin plugin
        ) {
            if (plugin == null) {
                throw new IllegalArgumentException("null plugin");
            }
            if (!plugin.isEnabled()) {
                throw new IllegalArgumentException("plugin is not enabled");
            }
            if (world == null || world.getHandle() == null) return false;
            long key = ChunkPos.pack(x, z);
            ServerLevel level = world.getHandle();
            Map<Long, Set<Plugin>> tickets = PLUGIN_CHUNK_TICKETS.computeIfAbsent(level, l -> new ConcurrentHashMap<>());
            Set<Plugin> plugins = tickets.computeIfAbsent(key, k -> ConcurrentHashMap.newKeySet());
            boolean added = plugins.add(plugin);
            if (added) {
                world.getChunkAt(x, z);
                return true;
            }
            return false;
        }

        @RuntimeType
        public static boolean removePluginChunkTicket(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z,
            @Argument(2) Plugin plugin
        ) {
            if (plugin == null) {
                throw new NullPointerException("null plugin");
            }
            if (world == null || world.getHandle() == null) return false;
            long key = ChunkPos.pack(x, z);
            ServerLevel level = world.getHandle();
            Map<Long, Set<Plugin>> tickets = PLUGIN_CHUNK_TICKETS.get(level);
            if (tickets == null) return false;
            Set<Plugin> plugins = tickets.get(key);
            if (plugins == null) return false;
            boolean removed = plugins.remove(plugin);
            if (plugins.isEmpty()) {
                tickets.remove(key);
            }
            return removed;
        }

        @RuntimeType
        public static void removePluginChunkTickets(
            @This CraftWorld world,
            @Argument(0) Plugin plugin
        ) {
            if (plugin == null) {
                throw new NullPointerException("null plugin");
            }
            if (world == null || world.getHandle() == null) return;
            ServerLevel level = world.getHandle();
            Map<Long, Set<Plugin>> tickets = PLUGIN_CHUNK_TICKETS.get(level);
            if (tickets == null) return;
            for (Map.Entry<Long, Set<Plugin>> entry : tickets.entrySet()) {
                entry.getValue().remove(plugin);
            }
        }

        @RuntimeType
        public static Collection<Plugin> getPluginChunkTickets(
            @This CraftWorld world,
            @Argument(0) int x,
            @Argument(1) int z
        ) {
            if (world == null || world.getHandle() == null) return Collections.emptyList();
            long key = ChunkPos.pack(x, z);
            ServerLevel level = world.getHandle();
            Map<Long, Set<Plugin>> tickets = PLUGIN_CHUNK_TICKETS.get(level);
            if (tickets == null) return Collections.emptyList();
            Set<Plugin> plugins = tickets.get(key);
            return plugins != null ? List.copyOf(plugins) : Collections.emptyList();
        }

        @RuntimeType
        public static Map<Plugin, Collection<Chunk>> getPluginChunkTickets(
            @This CraftWorld world
        ) {
            if (world == null || world.getHandle() == null) return Collections.emptyMap();
            ServerLevel level = world.getHandle();
            Map<Long, Set<Plugin>> tickets = PLUGIN_CHUNK_TICKETS.get(level);
            if (tickets == null || tickets.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<Plugin, Collection<Chunk>> ret = new HashMap<>();
            for (Map.Entry<Long, Set<Plugin>> entry : tickets.entrySet()) {
                long key = entry.getKey();
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                Chunk chunk = world.getChunkAt(cx, cz);
                for (Plugin plugin : entry.getValue()) {
                    ret.computeIfAbsent(plugin, p -> new ArrayList<>()).add(chunk);
                }
            }
            return ret;
        }

        @RuntimeType
        public static Collection<Chunk> getForceLoadedChunks(@This CraftWorld world) {
            if (world == null || !NativeFastPathFfi.isInitialized()) {
                return Collections.emptySet();
            }
            try {
                patchbukkit.world.GetForceLoadedChunksResponse res = NativeBridgeFfi.getForceLoadedChunks(
                    patchbukkit.world.GetForceLoadedChunksRequest.newBuilder()
                        .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                        .build()
                );
                if (res != null && res.getChunksCount() > 0) {
                    Set<Chunk> set = new HashSet<>();
                    for (patchbukkit.world.ChunkCoordProto coord : res.getChunksList()) {
                        set.add(world.getChunkAt(coord.getX(), coord.getZ()));
                    }
                    return Collections.unmodifiableCollection(set);
                }
            } catch (Throwable ignored) {}
            return Collections.emptySet();
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
                world.setPVP(res.getPvp());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static long getSeedFromPumpkin(CraftWorld world, long fallback) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(
                GetWorldInfoRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null) {
                return res.getSeed();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static boolean getPvpFromPumpkin(CraftWorld world, boolean fallback) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(
                GetWorldInfoRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null) {
                return res.getPvp();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static Difficulty getDifficultyFromPumpkin(CraftWorld world, Difficulty fallback) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(
                GetWorldInfoRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null && !res.getDifficulty().isEmpty()) {
                return Difficulty.valueOf(res.getDifficulty().toUpperCase(Locale.ROOT));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static Location getSpawnLocationFromPumpkin(CraftWorld world, Location fallback) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(
                GetWorldInfoRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .build()
            );
            if (res != null) {
                return new Location(world, res.getSpawnX(), res.getSpawnY(), res.getSpawnZ(), res.getSpawnAngle(), 0.0f);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
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

    public static final class OnGetWorldSeedAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Return(readOnly = false) long seed) {
            seed = WorldBridge.getSeedFromPumpkin(world, seed);
        }
    }

    public static final class OnGetWorldPvpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Return(readOnly = false) boolean pvp) {
            pvp = WorldBridge.getPvpFromPumpkin(world, pvp);
        }
    }

    public static final class OnGetWorldDifficultyAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Return(readOnly = false) Difficulty diff) {
            diff = WorldBridge.getDifficultyFromPumpkin(world, diff);
        }
    }

    public static final class OnGetWorldSpawnAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Return(readOnly = false) Location loc) {
            loc = WorldBridge.getSpawnLocationFromPumpkin(world, loc);
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
            @Advice.Argument(3) float angle,
            @Advice.Return boolean ok
        ) {
            if (ok) {
                WorldBridge.pushWorldSpawnToPumpkin(world, x, y, z, angle);
            }
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
            if (res != null) {
                Map<String, String> map = res.getGamerulesMap();
                String stripped = rule.startsWith("minecraft:") ? rule.substring("minecraft:".length()) : rule;
                if (map.containsKey(stripped)) {
                    return map.get(stripped);
                }
                if (map.containsKey(rule)) {
                    return map.get(rule);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getTypedGameRuleFromPumpkin(CraftWorld world, GameRule<T> rule, T fallback) {
        if (world == null || rule == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        String raw = getGameRuleFromPumpkin(world, rule.getKey().getKey(), null);
        if (raw == null) {
            raw = getGameRuleFromPumpkin(world, rule.getName(), null);
        }
        if (raw == null) return fallback;
        try {
            Class<T> type = rule.getType();
            if (type == Boolean.class) {
                return (T) Boolean.valueOf(Boolean.parseBoolean(raw));
            }
            if (type == Integer.class) {
                return (T) Integer.valueOf(Integer.parseInt(raw));
            }
        } catch (Throwable ignored) {}
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

    public static org.bukkit.block.Biome getBiomeFromPumpkin(
        CraftRegionAccessor accessor,
        int x,
        int y,
        int z,
        org.bukkit.block.Biome fallback
    ) {
        if (!(accessor instanceof CraftWorld world) || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return fallback;
        }
        SYNCING.set(true);
        try {
            GetBiomeResponse res = NativeBridgeFfi.getBiome(
                GetBiomeRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setX(x)
                    .setY(y)
                    .setZ(z)
                    .build()
            );
            if (res != null && !res.getBiome().isEmpty()) {
                String keyStr = res.getBiome().contains(":") ? res.getBiome() : "minecraft:" + res.getBiome();
                NamespacedKey key = NamespacedKey.fromString(keyStr);
                if (key != null) {
                    org.bukkit.block.Biome biome = Registry.BIOME.get(key);
                    if (biome != null) {
                        return biome;
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static Holder<Biome> getNoiseBiomeFromPumpkin(Level level, int x, int y, int z) {
        if (level != null && level.getWorld() != null && NativeFastPathFfi.isInitialized() && !SYNCING.get()) {
            SYNCING.set(true);
            try {
                GetBiomeResponse res = NativeBridgeFfi.getBiome(
                    GetBiomeRequest.newBuilder()
                        .setWorldUuid(BridgeUtils.convertUuid(level.getWorld().getUID()))
                        .setX(x)
                        .setY(y)
                        .setZ(z)
                        .build()
                );
                if (res != null && !res.getBiome().isEmpty()) {
                    String keyStr = res.getBiome().contains(":") ? res.getBiome() : "minecraft:" + res.getBiome();
                    ResourceKey<Biome> rk = ResourceKey.create(Registries.BIOME, Identifier.parse(keyStr));
                    var lookup = level.registryAccess().lookupOrThrow(Registries.BIOME);
                    var holderOpt = lookup.get(rk);
                    if (holderOpt.isPresent()) {
                        return holderOpt.get();
                    }
                }
            } catch (Throwable ignored) {
            } finally {
                SYNCING.set(false);
            }
        }
        if (level != null) {
            return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        }
        return null;
    }

    public static int getHighestBlockYFromPumpkin(Level level, int x, int z, int heightmap, int fallback) {
        if (level == null || level.getWorld() == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return fallback;
        }
        SYNCING.set(true);
        try {
            GetHighestBlockYResponse res = NativeBridgeFfi.getHighestBlockY(
                GetHighestBlockYRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(level.getWorld().getUID()))
                    .setX(x)
                    .setZ(z)
                    .setHeightmap(heightmap)
                    .build()
            );
            if (res != null) {
                return res.getY();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static int getHighestBlockYFromPumpkin(CraftWorld world, int x, int z, HeightMap heightMap, int fallback) {
        if (world == null || world.getHandle() == null) return fallback;
        int hm = 0;
        if (heightMap == HeightMap.MOTION_BLOCKING) {
            hm = 1;
        } else if (heightMap == HeightMap.MOTION_BLOCKING_NO_LEAVES) {
            hm = 2;
        }
        return getHighestBlockYFromPumpkin(world.getHandle(), x, z, hm, fallback);
    }

    public static byte getLightLevelFromPumpkin(CraftBlock block, int mode, byte fallback) {
        if (block == null || block.getWorld() == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return fallback;
        }
        SYNCING.set(true);
        try {
            GetLightLevelResponse res = NativeBridgeFfi.getLightLevel(
                GetLightLevelRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(block.getWorld().getUID()))
                    .setX(block.getX())
                    .setY(block.getY())
                    .setZ(block.getZ())
                    .build()
            );
            if (res != null) {
                int blockLight = Math.max(0, Math.min(15, res.getBlockLight()));
                int skyLight = Math.max(0, Math.min(15, res.getSkyLight()));
                return switch (mode) {
                    case 1 -> (byte) skyLight;
                    case 2 -> (byte) blockLight;
                    default -> (byte) Math.max(blockLight, skyLight);
                };
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushStrikeLightningToPumpkin(CraftWorld world, Location loc, boolean effectOnly) {
        if (world == null || loc == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.strikeLightning(
                StrikeLightningRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setX(loc.getX())
                    .setY(loc.getY())
                    .setZ(loc.getZ())
                    .setEffectOnly(effectOnly)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static BlockEntity getBlockEntityFromPumpkin(Level level, BlockPos pos) {
        if (level == null || pos == null || level.getWorld() == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return null;
        }
        SYNCING.set(true);
        try {
            BlockState state = getBlockState(level, pos);
            GetBlockEntityNbtResponse res = NativeBridgeFfi.getBlockEntityNbt(
                GetBlockEntityNbtRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(level.getWorld().getUID()))
                    .setX(pos.getX())
                    .setY(pos.getY())
                    .setZ(pos.getZ())
                    .build()
            );
            if (res != null && res.getExists() && !res.getNbt().isEmpty()) {
                try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(res.getNbt().toByteArray()))) {
                    CompoundTag tag = NbtIo.read(dis);
                    BlockEntity be = BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
                    if (be != null) {
                        be.setLevel(level);
                        return be;
                    }
                } catch (Throwable ignored) {}
            }
            if (state != null && state.getBlock() instanceof EntityBlock entityBlock) {
                BlockEntity be = entityBlock.newBlockEntity(pos, state);
                if (be != null) {
                    be.setLevel(level);
                    return be;
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return null;
    }

    public static void pushBlockEntityStateToPumpkin(CraftBlockEntityState<?> state) {
        if (state == null || !state.isPlaced() || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        World world = state.getWorld();
        if (world == null) return;
        SYNCING.set(true);
        try {
            CompoundTag nbt = state.getSnapshotNBT();
            if (nbt == null) return;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DataOutputStream dos = new DataOutputStream(baos)) {
                NbtIo.write(nbt, dos);
            }
            NativeBridgeFfi.setBlockEntityNbt(
                SetBlockEntityNbtRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setX(state.getX())
                    .setY(state.getY())
                    .setZ(state.getZ())
                    .setNbt(com.google.protobuf.ByteString.copyFrom(baos.toByteArray()))
                    .setRemove(false)
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
            net.minecraft.world.level.border.WorldBorder handle = border.getHandle();
            double size = border.getSize();
            double targetSize = handle != null ? handle.getLerpTarget() : size;
            long speed = handle != null ? handle.getLerpTime() : 0L;
            WorldBorderData data = WorldBorderData.newBuilder()
                .setCenterX(center != null ? center.getX() : 0.0)
                .setCenterZ(center != null ? center.getZ() : 0.0)
                .setSize(size)
                .setTargetSize(targetSize > 0.0 ? targetSize : size)
                .setSpeed(Math.max(0L, speed))
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

    public static final class OnPlayWorldSoundStringAdvice {
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

    public static final class OnPlayWorldSoundEnumAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) Location loc,
            @Advice.Argument(1) Sound sound,
            @Advice.Argument(2) SoundCategory category,
            @Advice.Argument(3) float volume,
            @Advice.Argument(4) float pitch,
            @Advice.Argument(5) long seed
        ) {
            if (sound != null) {
                WorldBridge.pushPlayWorldSoundToPumpkin(world, loc, sound.getKey().asString(), category, volume, pitch, seed);
            }
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
            @Advice.Argument(5) boolean breakBlocks,
            @Advice.Return boolean ok
        ) {
            if (ok) {
                WorldBridge.pushCreateExplosionToPumpkin(world, x, y, z, power, setFire, breakBlocks);
            }
        }
    }

    public static final class OnStrikeLightningAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) Location loc,
            @Advice.Argument(1) boolean effectOnly,
            @Advice.Return org.bukkit.entity.LightningStrike strike
        ) {
            if (strike != null) {
                WorldBridge.pushStrikeLightningToPumpkin(world, loc, effectOnly);
            }
        }
    }

    public static final class OnGetHighestBlockYAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) int x,
            @Advice.Argument(1) int z,
            @Advice.Argument(2) HeightMap heightMap,
            @Advice.Return(readOnly = false) int y
        ) {
            y = WorldBridge.getHighestBlockYFromPumpkin(world, x, z, heightMap, y);
        }
    }

    public static final class OnGetBiomeAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftRegionAccessor accessor,
            @Advice.Argument(0) int x,
            @Advice.Argument(1) int y,
            @Advice.Argument(2) int z,
            @Advice.Return(readOnly = false) org.bukkit.block.Biome biome
        ) {
            biome = WorldBridge.getBiomeFromPumpkin(accessor, x, y, z, biome);
        }
    }

    public static final class OnGetLightLevelAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftBlock block, @Advice.Return(readOnly = false) byte light) {
            light = WorldBridge.getLightLevelFromPumpkin(block, 0, light);
        }
    }

    public static final class OnGetSkyLightAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftBlock block, @Advice.Return(readOnly = false) byte light) {
            light = WorldBridge.getLightLevelFromPumpkin(block, 1, light);
        }
    }

    public static final class OnGetBlockLightAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftBlock block, @Advice.Return(readOnly = false) byte light) {
            light = WorldBridge.getLightLevelFromPumpkin(block, 2, light);
        }
    }

    public static final class OnBlockEntityStateUpdateAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftBlockEntityState<?> state, @Advice.Return boolean updated) {
            if (updated) {
                WorldBridge.pushBlockEntityStateToPumpkin(state);
            }
        }
    }

    public static final class OnGetGameRuleValueStringAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static String onEnter(@Advice.This CraftWorld world, @Advice.Argument(0) String rule) {
            return WorldBridge.getGameRuleFromPumpkin(world, rule, null);
        }

        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Enter String fromPumpkin,
            @Advice.Return(readOnly = false) String value
        ) {
            if (fromPumpkin != null) {
                value = fromPumpkin;
            }
        }
    }

    public static final class OnGetGameRuleValueTypedAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) GameRule<?> rule,
            @Advice.Return(readOnly = false, typing = net.bytebuddy.implementation.bytecode.assign.Assigner.Typing.DYNAMIC) Object value
        ) {
            @SuppressWarnings("unchecked")
            GameRule<Object> typed = (GameRule<Object>) rule;
            value = WorldBridge.getTypedGameRuleFromPumpkin(world, typed, value);
        }
    }

    public static final class OnSetGameRuleValueStringAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) String rule,
            @Advice.Argument(1) String value
        ) {
            if (rule != null && value != null && NativeFastPathFfi.isInitialized() && GameRule.getByName(rule) == null) {
                WorldBridge.pushGameRuleToPumpkin(world, rule, value);
                return true;
            }
            return false;
        }

        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftWorld world,
            @Advice.Argument(0) String rule,
            @Advice.Argument(1) String value,
            @Advice.Enter boolean handledByPumpkin,
            @Advice.Return(readOnly = false) boolean ok
        ) {
            if (handledByPumpkin) {
                ok = true;
            } else if (ok) {
                WorldBridge.pushGameRuleToPumpkin(world, rule, value);
            }
        }
    }

    public static final class OnSetGameRuleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftWorld world, @Advice.Argument(0) GameRule<?> rule, @Advice.Argument(1) Object value) {
            if (rule != null && value != null) {
                WorldBridge.pushGameRuleToPumpkin(world, rule.getKey().getKey(), String.valueOf(value));
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
