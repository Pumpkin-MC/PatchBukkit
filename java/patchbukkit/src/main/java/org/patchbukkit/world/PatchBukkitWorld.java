package org.patchbukkit.world;

import io.papermc.paper.math.Position;
import io.papermc.paper.raytracing.PositionedRayTraceConfigurationBuilder;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

public class PatchBukkitWorld
    extends PatchBukkitRegionAccessor
    implements World
{
    private static final Map<UUID, PatchBukkitWorld> instances = new HashMap<>();
    private UUID uuid;

    private PatchBukkitWorld(UUID uuid) {
        this.uuid = uuid;
    }

    public static PatchBukkitWorld getOrCreate(UUID uuid) {
        return instances.computeIfAbsent(uuid, PatchBukkitWorld::new);
    }

    public static PatchBukkitWorld getOrCreate(String uuid) {
        return getOrCreate(UUID.fromString(uuid));
    }

    @Override
    public boolean isVoidDamageEnabled() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isVoidDamageEnabled'"
        );
    }

    @Override
    public void setVoidDamageEnabled(boolean enabled) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setVoidDamageEnabled'"
        );
    }

    @Override
    public float getVoidDamageAmount() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getVoidDamageAmount'"
        );
    }

    @Override
    public void setVoidDamageAmount(float voidDamageAmount) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setVoidDamageAmount'"
        );
    }

    @Override
    public double getVoidDamageMinBuildHeightOffset() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getVoidDamageMinBuildHeightOffset'"
        );
    }

    @Override
    public void setVoidDamageMinBuildHeightOffset(double minBuildHeightOffset) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setVoidDamageMinBuildHeightOffset'"
        );
    }

    @Override
    public int getEntityCount() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntityCount'"
        );
    }

    @Override
    public int getTileEntityCount() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTileEntityCount'"
        );
    }

    @Override
    public int getTickableTileEntityCount() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTickableTileEntityCount'"
        );
    }

    @Override
    public int getChunkCount() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getChunkCount'"
        );
    }

    @Override
    public int getPlayerCount() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlayerCount'"
        );
    }

    @Override
    public boolean hasStructureAt(
        @NotNull Position position,
        @NotNull Structure structure
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasStructureAt'"
        );
    }

    @Override
    public @NotNull Block getBlockAt(int x, int y, int z) {
        var request = patchbukkit.block.GetBlockRequest.newBuilder()
            .setWorld(patchbukkit.common.World.newBuilder()
                .setUuid(org.patchbukkit.bridge.BridgeUtils.convertUuid(this.uuid))
                .build())
            .setX(x)
            .setY(y)
            .setZ(z)
            .build();
        var response = patchbukkit.bridge.NativeBridgeFfi.getBlock(request);
        String blockKey = response != null ? response.getBlockKey() : "minecraft:air";
        return PatchBukkitBlock.create(this, x, y, z, blockKey);
    }

    @Override
    public @NotNull Chunk getChunkAt(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getChunkAt'"
        );
    }

    @Override
    public @NotNull Chunk getChunkAt(int x, int z, boolean generate) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getChunkAt'"
        );
    }

    @Override
    public @NotNull Chunk getChunkAt(@NotNull Block block) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getChunkAt'"
        );
    }

    @Override
    public boolean isChunkLoaded(@NotNull Chunk chunk) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isChunkLoaded'"
        );
    }

    @Override
    public @NotNull Chunk@NotNull [] getLoadedChunks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getLoadedChunks'"
        );
    }

    @Override
    public void loadChunk(@NotNull Chunk chunk) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadChunk'"
        );
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isChunkLoaded'"
        );
    }

    @Override
    public boolean isChunkGenerated(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isChunkGenerated'"
        );
    }

    @Override
    public boolean isChunkInUse(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isChunkInUse'"
        );
    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadChunk'"
        );
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'unloadChunk'"
        );
    }

    @Override
    public boolean unloadChunkRequest(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'unloadChunkRequest'"
        );
    }

    @Override
    public boolean refreshChunk(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'refreshChunk'"
        );
    }

    @Override
    public @NotNull Collection<Player> getPlayersSeeingChunk(
        @NotNull Chunk chunk
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlayersSeeingChunk'"
        );
    }

    @Override
    public @NotNull Collection<Player> getPlayersSeeingChunk(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlayersSeeingChunk'"
        );
    }

    @Override
    public boolean isChunkForceLoaded(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isChunkForceLoaded'"
        );
    }

    @Override
    public void setChunkForceLoaded(int x, int z, boolean forced) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setChunkForceLoaded'"
        );
    }

    @Override
    public @NotNull Collection<Chunk> getForceLoadedChunks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getForceLoadedChunks'"
        );
    }

    @Override
    public boolean addPluginChunkTicket(int x, int z, @NotNull Plugin plugin) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'addPluginChunkTicket'"
        );
    }

    @Override
    public boolean removePluginChunkTicket(
        int x,
        int z,
        @NotNull Plugin plugin
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'removePluginChunkTicket'"
        );
    }

    @Override
    public void removePluginChunkTickets(@NotNull Plugin plugin) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'removePluginChunkTickets'"
        );
    }

    @Override
    public @NotNull Collection<Plugin> getPluginChunkTickets(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPluginChunkTickets'"
        );
    }

    @Override
    public @NotNull Map<Plugin, Collection<Chunk>> getPluginChunkTickets() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPluginChunkTickets'"
        );
    }

    @Override
    public @NotNull Collection<Chunk> getIntersectingChunks(
        @NotNull BoundingBox box
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getIntersectingChunks'"
        );
    }

    @Override
    public @NotNull Item dropItem(
        @NotNull Location location,
        @NotNull ItemStack item,
        @Nullable Consumer<? super Item> function
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'dropItem'"
        );
    }

    @Override
    public @NotNull Item dropItemNaturally(
        @NotNull Location location,
        @NotNull ItemStack item,
        @Nullable Consumer<? super Item> function
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'dropItemNaturally'"
        );
    }

    @Override
    public @NonNull <T extends AbstractArrow> T spawnArrow(
        @NotNull Location location,
        @NotNull Vector direction,
        float speed,
        float spread,
        @NotNull Class<T> clazz
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spawnArrow'"
        );
    }

    @Override
    public boolean generateTree(
        @NotNull Location location,
        @NotNull TreeType type
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'generateTree'"
        );
    }

    @Override
    public boolean generateTree(
        @NotNull Location loc,
        @NotNull TreeType type,
        @NotNull BlockChangeDelegate delegate
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'generateTree'"
        );
    }

    @Override
    public @NotNull LightningStrike strikeLightning(@NotNull Location loc) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'strikeLightning'"
        );
    }

    @Override
    public @NotNull LightningStrike strikeLightningEffect(
        @NotNull Location loc
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'strikeLightningEffect'"
        );
    }

    @Override
    public @Nullable Location findLightningRod(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'findLightningRod'"
        );
    }

    @Override
    public @Nullable Location findLightningTarget(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'findLightningTarget'"
        );
    }

    @Override
    public @NotNull <T extends Entity> Collection<T> getEntitiesByClass(
        @NonNull @NotNull Class<T>... classes
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntitiesByClass'"
        );
    }

    @Override
    public void getChunkAtAsync(
        int x,
        int z,
        boolean gen,
        boolean urgent,
        @NotNull Consumer<? super Chunk> cb
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getChunkAtAsync'"
        );
    }

    @Override
    public void getChunksAtAsync(
        int minX,
        int minZ,
        int maxX,
        int maxZ,
        boolean urgent,
        @NotNull Runnable cb
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getChunksAtAsync'"
        );
    }

    @Override
    public @NotNull List<Player> getPlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlayers'"
        );
    }

    @Override
    public @Nullable Entity getEntity(@NotNull UUID uuid) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntity'"
        );
    }

    @Override
    public @NotNull Collection<Entity> getNearbyEntities(
        @NotNull Location location,
        double x,
        double y,
        double z,
        @Nullable Predicate<? super Entity> filter
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getNearbyEntities'"
        );
    }

    @Override
    public @NotNull Collection<Entity> getNearbyEntities(
        @NotNull BoundingBox boundingBox,
        @Nullable Predicate<? super Entity> filter
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getNearbyEntities'"
        );
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(
        @NotNull Position start,
        @NotNull Vector direction,
        double maxDistance,
        double raySize,
        @Nullable Predicate<? super Entity> filter
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'rayTraceEntities'"
        );
    }

    @Override
    public @Nullable RayTraceResult rayTraceBlocks(
        @NotNull Position start,
        @NotNull Vector direction,
        double maxDistance,
        @NotNull FluidCollisionMode fluidCollisionMode,
        boolean ignorePassableBlocks,
        @Nullable Predicate<? super Block> canCollide
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'rayTraceBlocks'"
        );
    }

    @Override
    public @Nullable RayTraceResult rayTrace(
        @NotNull Position start,
        @NotNull Vector direction,
        double maxDistance,
        @NotNull FluidCollisionMode fluidCollisionMode,
        boolean ignorePassableBlocks,
        double raySize,
        @Nullable Predicate<? super Entity> filter,
        @Nullable Predicate<? super Block> canCollide
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'rayTrace'"
        );
    }

    @Override
    public @Nullable RayTraceResult rayTrace(
        @NotNull Consumer<
            PositionedRayTraceConfigurationBuilder
        > builderConsumer
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'rayTrace'"
        );
    }

    @Override
    public @NotNull Location getSpawnLocation() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSpawnLocation'"
        );
    }

    @Override
    public boolean setSpawnLocation(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSpawnLocation'"
        );
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float yaw) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSpawnLocation'"
        );
    }

    @Override
    public long getTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTime'"
        );
    }

    @Override
    public void setTime(long time) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setTime'"
        );
    }

    @Override
    public long getFullTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getFullTime'"
        );
    }

    @Override
    public void setFullTime(long time) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setFullTime'"
        );
    }

    @Override
    public boolean isDayTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isDayTime'"
        );
    }

    @Override
    public long getGameTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGameTime'"
        );
    }

    @Override
    public boolean hasStorm() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasStorm'"
        );
    }

    @Override
    public void setStorm(boolean hasStorm) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setStorm'"
        );
    }

    @Override
    public int getWeatherDuration() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWeatherDuration'"
        );
    }

    @Override
    public void setWeatherDuration(int duration) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setWeatherDuration'"
        );
    }

    @Override
    public boolean isThundering() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isThundering'"
        );
    }

    @Override
    public void setThundering(boolean thundering) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setThundering'"
        );
    }

    @Override
    public int getThunderDuration() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getThunderDuration'"
        );
    }

    @Override
    public void setThunderDuration(int duration) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setThunderDuration'"
        );
    }

    @Override
    public boolean isClearWeather() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isClearWeather'"
        );
    }

    @Override
    public void setClearWeatherDuration(int duration) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setClearWeatherDuration'"
        );
    }

    @Override
    public int getClearWeatherDuration() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getClearWeatherDuration'"
        );
    }

    @Override
    public boolean createExplosion(
        double x,
        double y,
        double z,
        float power,
        boolean setFire,
        boolean breakBlocks,
        @Nullable Entity source
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createExplosion'"
        );
    }

    @Override
    public boolean createExplosion(
        @Nullable Entity source,
        @NotNull Location loc,
        float power,
        boolean setFire,
        boolean breakBlocks,
        boolean excludeSourceFromDamage
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createExplosion'"
        );
    }

    @Override
    public boolean createExplosion(
        @NotNull Location loc,
        float power,
        boolean setFire,
        boolean breakBlocks,
        @Nullable Entity source
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createExplosion'"
        );
    }

    @Override
    public boolean getPVP() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPVP'"
        );
    }

    @Override
    public void setPVP(boolean pvp) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setPVP'"
        );
    }

    @Override
    public @Nullable ChunkGenerator getGenerator() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGenerator'"
        );
    }

    @Override
    public @Nullable BiomeProvider getBiomeProvider() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBiomeProvider'"
        );
    }

    @Override
    public void save(boolean flush) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'save'");
    }

    @Override
    public @NotNull List<BlockPopulator> getPopulators() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPopulators'"
        );
    }

    @Override
    public @NonNull <T extends LivingEntity> T spawn(
        @NotNull Location location,
        @NotNull Class<T> clazz,
        CreatureSpawnEvent.@NotNull SpawnReason spawnReason,
        boolean randomizeData,
        @Nullable Consumer<? super T> function
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spawn'");
    }

    @Override
    public @NotNull FallingBlock spawnFallingBlock(
        @NotNull Location location,
        @NotNull MaterialData data
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spawnFallingBlock'"
        );
    }

    @Override
    public @NotNull FallingBlock spawnFallingBlock(
        @NotNull Location location,
        @NotNull BlockData data
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spawnFallingBlock'"
        );
    }

    @Override
    public @NotNull FallingBlock spawnFallingBlock(
        @NotNull Location location,
        @NotNull Material material,
        byte data
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spawnFallingBlock'"
        );
    }

    @Override
    public void playEffect(
        @NotNull Location location,
        @NotNull Effect effect,
        int data,
        int radius
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playEffect'"
        );
    }

    @Override
    public <T> void playEffect(
        @NotNull Location location,
        @NotNull Effect effect,
        @org.jspecify.annotations.Nullable T data,
        int radius
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playEffect'"
        );
    }

    @Override
    public @NotNull ChunkSnapshot getEmptyChunkSnapshot(
        int x,
        int z,
        boolean includeBiome,
        boolean includeBiomeTemp
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEmptyChunkSnapshot'"
        );
    }

    @Override
    public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSpawnFlags'"
        );
    }

    @Override
    public boolean getAllowAnimals() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAllowAnimals'"
        );
    }

    @Override
    public boolean getAllowMonsters() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAllowMonsters'"
        );
    }

    @Override
    public void setBiome(int x, int z, @NotNull Biome bio) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setBiome'"
        );
    }

    @Override
    public double getTemperature(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTemperature'"
        );
    }

    @Override
    public double getHumidity(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHumidity'"
        );
    }

    @Override
    public int getLogicalHeight() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getLogicalHeight'"
        );
    }

    @Override
    public boolean isNatural() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isNatural'"
        );
    }

    @Override
    public boolean isBedWorks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isBedWorks'"
        );
    }

    @Override
    public boolean hasSkyLight() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasSkyLight'"
        );
    }

    @Override
    public boolean hasCeiling() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasCeiling'"
        );
    }

    @Override
    public boolean isPiglinSafe() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isPiglinSafe'"
        );
    }

    @Override
    public boolean isRespawnAnchorWorks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isRespawnAnchorWorks'"
        );
    }

    @Override
    public boolean hasRaids() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasRaids'"
        );
    }

    @Override
    public boolean isUltraWarm() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isUltraWarm'"
        );
    }

    @Override
    public int getSeaLevel() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSeaLevel'"
        );
    }

    @Override
    public boolean isAutoSave() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isAutoSave'"
        );
    }

    @Override
    public void setAutoSave(boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setAutoSave'"
        );
    }

    @Override
    public void setDifficulty(@NotNull Difficulty difficulty) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setDifficulty'"
        );
    }

    @Override
    public @NotNull Difficulty getDifficulty() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getDifficulty'"
        );
    }

    @Override
    public int getViewDistance() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getViewDistance'"
        );
    }

    @Override
    public int getSimulationDistance() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSimulationDistance'"
        );
    }

    @Override
    public @NotNull Path getWorldPath() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorldPath'"
        );
    }

    @Override
    public @Nullable WorldType getWorldType() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorldType'"
        );
    }

    @Override
    public boolean canGenerateStructures() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'canGenerateStructures'"
        );
    }

    @Override
    public boolean hasBonusChest() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasBonusChest'"
        );
    }

    @Override
    public boolean isHardcore() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isHardcore'"
        );
    }

    @Override
    public void setHardcore(boolean hardcore) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setHardcore'"
        );
    }

    @Override
    public long getTicksPerSpawns(@NotNull SpawnCategory spawnCategory) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTicksPerSpawns'"
        );
    }

    @Override
    public void setTicksPerSpawns(
        @NotNull SpawnCategory spawnCategory,
        int ticksPerCategorySpawn
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setTicksPerSpawns'"
        );
    }

    @Override
    public int getSpawnLimit(@NotNull SpawnCategory spawnCategory) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSpawnLimit'"
        );
    }

    @Override
    public void setSpawnLimit(@NotNull SpawnCategory spawnCategory, int limit) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSpawnLimit'"
        );
    }

    @Override
    public void playSound(
        @NotNull Location location,
        @NotNull Sound sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Location location,
        @NotNull String sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Location location,
        @NotNull Sound sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Location location,
        @NotNull String sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Entity entity,
        @NotNull Sound sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Entity entity,
        @NotNull String sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Entity entity,
        @NotNull Sound sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public void playSound(
        @NotNull Entity entity,
        @NotNull String sound,
        @NotNull SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'playSound'"
        );
    }

    @Override
    public @NotNull String@NotNull [] getGameRules() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGameRules'"
        );
    }

    @Override
    public @Nullable String getGameRuleValue(@Nullable String rule) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGameRuleValue'"
        );
    }

    @Override
    public boolean setGameRuleValue(
        @NotNull String rule,
        @NotNull String value
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setGameRuleValue'"
        );
    }

    @Override
    public boolean isGameRule(@NotNull String rule) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isGameRule'"
        );
    }

    @Override
    public @org.jspecify.annotations.Nullable <T> T getGameRuleValue(
        @NotNull GameRule<T> rule
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGameRuleValue'"
        );
    }

    @Override
    public @org.jspecify.annotations.Nullable <T> T getGameRuleDefault(
        @NotNull GameRule<T> rule
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGameRuleDefault'"
        );
    }

    @Override
    public <T> boolean setGameRule(
        @NotNull GameRule<T> rule,
        @NonNull T newValue
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setGameRule'"
        );
    }

    @Override
    public @NotNull WorldBorder getWorldBorder() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorldBorder'"
        );
    }

    @Override
    public <T> void spawnParticle(
        @NotNull Particle particle,
        @Nullable List<Player> receivers,
        @Nullable Player source,
        double x,
        double y,
        double z,
        int count,
        double offsetX,
        double offsetY,
        double offsetZ,
        double extra,
        @org.jspecify.annotations.Nullable T data,
        boolean force
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spawnParticle'"
        );
    }

    @Override
    public @Nullable Location locateNearestStructure(
        @NotNull Location origin,
        @NotNull StructureType structureType,
        int radius,
        boolean findUnexplored
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'locateNearestStructure'"
        );
    }

    @Override
    public @Nullable StructureSearchResult locateNearestStructure(
        @NotNull Location origin,
        org.bukkit.generator.structure.@NotNull StructureType structureType,
        int radius,
        boolean findUnexplored
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'locateNearestStructure'"
        );
    }

    @Override
    public @Nullable StructureSearchResult locateNearestStructure(
        @NotNull Location origin,
        @NotNull Structure structure,
        int radius,
        boolean findUnexplored
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'locateNearestStructure'"
        );
    }

    @Override
    public double getCoordinateScale() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getCoordinateScale'"
        );
    }

    @Override
    public boolean isFixedTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isFixedTime'"
        );
    }

    @Override
    public @NotNull Collection<Material> getInfiniburn() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getInfiniburn'"
        );
    }

    @Override
    public void sendGameEvent(
        @Nullable Entity sourceEntity,
        @NotNull GameEvent gameEvent,
        @NotNull Vector position
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'sendGameEvent'"
        );
    }

    @Override
    public @NotNull Spigot spigot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spigot'"
        );
    }

    @Override
    public @Nullable BiomeSearchResult locateNearestBiome(
        @NotNull Location origin,
        int radius,
        int horizontalInterval,
        int verticalInterval,
        @NonNull @NotNull Biome... biomes
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'locateNearestBiome'"
        );
    }

    @Override
    public @Nullable Raid locateNearestRaid(
        @NotNull Location location,
        int radius
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'locateNearestRaid'"
        );
    }

    @Override
    public @Nullable Raid getRaid(int id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRaid'"
        );
    }

    @Override
    public @NotNull List<Raid> getRaids() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRaids'"
        );
    }

    @Override
    public @Nullable DragonBattle getEnderDragonBattle() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEnderDragonBattle'"
        );
    }

    @Override
    public void setViewDistance(int viewDistance) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setViewDistance'"
        );
    }

    @Override
    public void setSimulationDistance(int simulationDistance) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSimulationDistance'"
        );
    }

    @Override
    public int getSendViewDistance() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSendViewDistance'"
        );
    }

    @Override
    public void setSendViewDistance(int viewDistance) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSendViewDistance'"
        );
    }

    @Override
    public @NotNull Collection<GeneratedStructure> getStructures(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getStructures'"
        );
    }

    @Override
    public @NotNull Collection<GeneratedStructure> getStructures(
        int x,
        int z,
        @NotNull Structure structure
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getStructures'"
        );
    }

    @Override
    public @NotNull String getName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getName'"
        );
    }

    @Override
    public @NotNull UUID getUID() {
        return this.uuid;
    }

    @Override
    public @NotNull Environment getEnvironment() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEnvironment'"
        );
    }

    @Override
    public long getSeed() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSeed'"
        );
    }

    @Override
    public int getMinHeight() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMinHeight'"
        );
    }

    @Override
    public int getMaxHeight() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMaxHeight'"
        );
    }

    @Override
    public @NotNull BiomeProvider vanillaBiomeProvider() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'vanillaBiomeProvider'"
        );
    }

    @Override
    public void setMetadata(
        @NotNull String metadataKey,
        @NotNull MetadataValue newMetadataValue
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setMetadata'"
        );
    }

    @Override
    public @NotNull List<MetadataValue> getMetadata(
        @NotNull String metadataKey
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMetadata'"
        );
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasMetadata'"
        );
    }

    @Override
    public void removeMetadata(
        @NotNull String metadataKey,
        @NotNull Plugin owningPlugin
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'removeMetadata'"
        );
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPersistentDataContainer'"
        );
    }

    @Override
    public void sendPluginMessage(
        @NotNull Plugin source,
        @NotNull String channel,
        byte@NotNull [] message
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'sendPluginMessage'"
        );
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getListeningPluginChannels'"
        );
    }
}
