package org.patchbukkit.world;

import io.papermc.paper.block.fluid.FluidData;
import io.papermc.paper.world.MoonPhase;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;

public class PatchBukkitRegionAccessor implements RegionAccessor {

    @Override
    public @NotNull Biome getBiome(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBiome'"
        );
    }

    @Override
    public @NotNull Biome getComputedBiome(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getComputedBiome'"
        );
    }

    @Override
    public void setBiome(int x, int y, int z, @NotNull Biome biome) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setBiome'"
        );
    }

    @Override
    public @NotNull BlockState getBlockState(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBlockState'"
        );
    }

    @Override
    public @NotNull FluidData getFluidData(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getFluidData'"
        );
    }

    @Override
    public @NotNull BlockData getBlockData(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBlockData'"
        );
    }

    @Override
    public @NotNull Material getType(int x, int y, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getType'"
        );
    }

    @Override
    public void setBlockData(
        int x,
        int y,
        int z,
        @NotNull BlockData blockData
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setBlockData'"
        );
    }

    @Override
    public boolean generateTree(
        @NotNull Location location,
        @NotNull Random random,
        @NotNull TreeType type
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'generateTree'"
        );
    }

    @Override
    public boolean generateTree(
        @NotNull Location location,
        @NotNull Random random,
        @NotNull TreeType type,
        @Nullable Consumer<? super BlockState> stateConsumer
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'generateTree'"
        );
    }

    @Override
    public boolean generateTree(
        @NotNull Location location,
        @NotNull Random random,
        @NotNull TreeType type,
        @Nullable Predicate<? super BlockState> statePredicate
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'generateTree'"
        );
    }

    @Override
    public @NotNull Entity spawnEntity(
        @NotNull Location loc,
        @NotNull EntityType type,
        boolean randomizeData
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spawnEntity'"
        );
    }

    @Override
    public @NotNull List<Entity> getEntities() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntities'"
        );
    }

    @Override
    public @NotNull List<LivingEntity> getLivingEntities() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getLivingEntities'"
        );
    }

    @Override
    public @NotNull <T extends Entity> Collection<T> getEntitiesByClass(
        @NotNull Class<T> cls
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntitiesByClass'"
        );
    }

    @Override
    public @NotNull Collection<Entity> getEntitiesByClasses(
        @NonNull @NotNull Class<?>... classes
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntitiesByClasses'"
        );
    }

    @Override
    public @NonNull <T extends Entity> T createEntity(
        @NotNull Location location,
        @NotNull Class<T> clazz
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createEntity'"
        );
    }

    @Override
    public @NonNull <T extends Entity> T spawn(
        @NotNull Location location,
        @NotNull Class<T> clazz,
        @Nullable Consumer<? super T> function,
        CreatureSpawnEvent.@NotNull SpawnReason reason
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spawn'");
    }

    @Override
    public @NonNull <T extends Entity> T spawn(
        @NotNull Location location,
        @NotNull Class<T> clazz,
        boolean randomizeData,
        @Nullable Consumer<? super T> function
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spawn'");
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHighestBlockYAt'"
        );
    }

    @Override
    public int getHighestBlockYAt(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHighestBlockYAt'"
        );
    }

    @Override
    public int getHighestBlockYAt(int x, int z, @NotNull HeightMap heightMap) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHighestBlockYAt'"
        );
    }

    @Override
    public int getHighestBlockYAt(
        @NotNull Location location,
        @NotNull HeightMap heightMap
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHighestBlockYAt'"
        );
    }

    @Override
    public @NonNull <T extends Entity> T addEntity(@NonNull T entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'addEntity'"
        );
    }

    @Override
    public @NotNull MoonPhase getMoonPhase() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMoonPhase'"
        );
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getKey'"
        );
    }

    @Override
    public boolean lineOfSightExists(
        @NotNull Location from,
        @NotNull Location to
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'lineOfSightExists'"
        );
    }

    @Override
    public boolean hasCollisionsIn(@NotNull BoundingBox boundingBox) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasCollisionsIn'"
        );
    }

    @Override
    public @Unmodifiable Set<FeatureFlag> getFeatureFlags() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getFeatureFlags'"
        );
    }
}
