package org.patchbukkit.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.block.data.BlockData;
import org.bukkit.SoundGroup;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PatchBukkitBlock implements Block {
    private final World world;
    private final int x;
    private final int y;
    private final int z;
    // Lazily initialised: World#getBlockAt allocates a fresh PatchBukkitBlock per call,
    // and block metadata is rarely used, so an eager HashMap per Block was pure garbage
    // (~80 bytes/allocation on the hottest read path).
    private Map<String, List<MetadataValue>> metadataMap;

    public PatchBukkitBlock(World world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    private Map<String, List<MetadataValue>> metadata() {
        if (this.metadataMap == null) {
            this.metadataMap = new HashMap<>();
        }
        return this.metadataMap;
    }

    @Override
    public @NotNull World getWorld() {
        return this.world;
    }

    @Override
    public int getX() {
        return this.x;
    }

    @Override
    public int getY() {
        return this.y;
    }

    @Override
    public int getZ() {
        return this.z;
    }

    @Override
    public @NotNull Location getLocation() {
        return new Location(this.world, this.x, this.y, this.z);
    }

    @Override
    public @Nullable Location getLocation(@Nullable Location loc) {
        if (loc != null) {
            loc.setWorld(this.world);
            loc.setX(this.x);
            loc.setY(this.y);
            loc.setZ(this.z);
            return loc;
        }
        return getLocation();
    }

    @Override
    public @NotNull BlockData getBlockData() {
        return this.world.getBlockData(this.x, this.y, this.z);
    }

    @Override
    public void setBlockData(@NotNull BlockData data) {
        this.world.setBlockData(this.x, this.y, this.z, data);
    }

    @Override
    public void setBlockData(@NotNull BlockData data, boolean applyPhysics) {
        this.world.setBlockData(this.x, this.y, this.z, data);
    }

    @Override
    public boolean isEmpty() {
        return getType().isAir();
    }

    @Override
    public boolean isLiquid() {
        Material type = getType();
        return type == Material.WATER || type == Material.LAVA;
    }

    @Override
    public boolean isPassable() {
        return isEmpty() || isLiquid();
    }

    @Override
    public boolean isSolid() {
        return getType().isSolid();
    }

    @Override
    public boolean isReplaceable() {
        return isEmpty() || getType() == Material.TALL_GRASS || getType() == Material.SHORT_GRASS;
    }

    @Override
    public boolean isBuildable() {
        return !isEmpty();
    }

    @Override
    public int getBlockPower(@NotNull BlockFace face) {
        return 0;
    }

    @Override
    public int getBlockPower() {
        return 0;
    }

    @Override
    public @NotNull BlockState getState() {
        return getState(true);
    }

    @Override
    public @NotNull BlockState getState(boolean useSnapshot) {
        return new PatchBukkitBlockState(this);
    }

    @Override
    public boolean isBlockPowered() {
        return false;
    }

    @Override
    public boolean isBlockIndirectlyPowered() {
        return false;
    }

    @Override
    public boolean isBlockFacePowered(@NotNull BlockFace face) {
        return false;
    }

    @Override
    public boolean isBlockFaceIndirectlyPowered(@NotNull BlockFace face) {
        return false;
    }

    @Override
    public boolean isSuffocating() {
        return !isPassable();
    }

    @Override
    public boolean isBurnable() {
        return getType().isBurnable();
    }

    @Override
    public boolean isCollidable() {
        return !isPassable();
    }

    @Override
    public @NotNull String getTranslationKey() {
        return getType().getTranslationKey();
    }

    @Override
    public @NotNull com.destroystokyo.paper.block.BlockSoundGroup getSoundGroup() {
        return (com.destroystokyo.paper.block.BlockSoundGroup) (Object) getBlockData().getSoundGroup();
    }

    @Override
    public @NotNull SoundGroup getBlockSoundGroup() {
        return getBlockData().getSoundGroup();
    }

    @Override
    public boolean canPlace(@NotNull BlockData data) {
        return true;
    }

    @Override
    public @NotNull BoundingBox getBoundingBox() {
        return BoundingBox.of(this, this);
    }

    @Override
    public float getBreakSpeed(@NotNull Player player) {
        return 1.0f;
    }

    @Override
    public boolean isPreferredTool(@NotNull ItemStack tool) {
        return false;
    }

    @Override
    public @NotNull Collection<ItemStack> getDrops() {
        return getDrops(null);
    }

    @Override
    public @NotNull Collection<ItemStack> getDrops(@Nullable ItemStack tool) {
        return getDrops(tool, null);
    }

    @Override
    public @NotNull Collection<ItemStack> getDrops(@Nullable ItemStack tool, @Nullable Entity entity) {
        return List.of();
    }

    @Override
    public boolean applyBoneMeal(@NotNull BlockFace targetFace) {
        return false;
    }

    @Override
    public void randomTick() {
    }

    @Override
    public boolean breakNaturally() {
        return breakNaturally(null);
    }

    @Override
    public boolean breakNaturally(@Nullable ItemStack tool) {
        return breakNaturally(tool, true);
    }

    @Override
    public boolean breakNaturally(boolean triggerEffect) {
        return breakNaturally(null, triggerEffect);
    }

    @Override
    public boolean breakNaturally(boolean triggerEffect, boolean dropExperience) {
        return breakNaturally(null, triggerEffect, dropExperience);
    }

    @Override
    public boolean breakNaturally(@Nullable ItemStack tool, boolean triggerEffect) {
        return breakNaturally(tool, triggerEffect, true);
    }

    @Override
    public boolean breakNaturally(@Nullable ItemStack tool, boolean triggerEffect, boolean dropExperience) {
        return breakNaturally(tool, triggerEffect, dropExperience, true);
    }

    public boolean breakNaturally(@Nullable ItemStack tool, boolean triggerEffect, boolean dropExperience, boolean dropAsItem) {
        setType(Material.AIR);
        return true;
    }

    @Override
    public @NotNull Biome getBiome() {
        return Biome.PLAINS;
    }

    @Override
    public @NotNull Biome getComputedBiome() {
        return getBiome();
    }

    @Override
    public void setBiome(@NotNull Biome bio) {
    }

    @Override
    public double getTemperature() {
        return 0.8;
    }

    @Override
    public double getHumidity() {
        return 0.4;
    }

    @Override
    public @NotNull PistonMoveReaction getPistonMoveReaction() {
        return PistonMoveReaction.MOVE;
    }

    @Override
    public void fluidTick() {
    }

    @Override
    public void tick() {
    }

    @Override
    public @Nullable RayTraceResult rayTrace(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode) {
        return getBoundingBox().rayTrace(start.toVector(), direction, maxDistance);
    }

    @Override
    public @NotNull VoxelShape getCollisionShape() {
        return getBlockData().getCollisionShape(getLocation());
    }

    @Override
    public @NotNull Material getType() {
        return getBlockData().getMaterial();
    }

    @Override
    public void setType(@NotNull Material type) {
        setBlockData(org.bukkit.Bukkit.createBlockData(type));
    }

    @Override
    public void setType(@NotNull Material type, boolean applyPhysics) {
        setBlockData(org.bukkit.Bukkit.createBlockData(type), applyPhysics);
    }

    @Override
    public @NotNull Block getRelative(int modX, int modY, int modZ) {
        return this.world.getBlockAt(this.x + modX, this.y + modY, this.z + modZ);
    }

    @Override
    public @NotNull Block getRelative(@NotNull BlockFace face) {
        return getRelative(face.getModX(), face.getModY(), face.getModZ());
    }

    @Override
    public @NotNull Block getRelative(@NotNull BlockFace face, int distance) {
        return getRelative(face.getModX() * distance, face.getModY() * distance, face.getModZ() * distance);
    }

    @Override
    public @Nullable BlockFace getFace(@NotNull Block block) {
        int modX = block.getX() - this.x;
        int modY = block.getY() - this.y;
        int modZ = block.getZ() - this.z;
        for (BlockFace face : BlockFace.values()) {
            if (face.getModX() == modX && face.getModY() == modY && face.getModZ() == modZ) {
                return face;
            }
        }
        return null;
    }

    @Override
    public void setMetadata(@NotNull String metadataKey, @NotNull MetadataValue newMetadataValue) {
        metadataMap.computeIfAbsent(metadataKey, k -> new ArrayList<>()).add(newMetadataValue);
    }

    @Override
    public @NotNull List<MetadataValue> getMetadata(@NotNull String metadataKey) {
        return metadataMap.getOrDefault(metadataKey, List.of());
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        return metadataMap.containsKey(metadataKey);
    }

    @Override
    public void removeMetadata(@NotNull String metadataKey, @NotNull Plugin owningPlugin) {
        List<MetadataValue> list = metadataMap.get(metadataKey);
        if (list != null) {
            list.removeIf(v -> v.getOwningPlugin() == owningPlugin);
            if (list.isEmpty()) {
                metadataMap.remove(metadataKey);
            }
        }
    }

    @Override
    public String translationKey() {
        return getType().getTranslationKey();
    }

    @Override
    public byte getData() {
        return 0;
    }

    @Override
    public byte getLightLevel() {
        return 15;
    }

    @Override
    public byte getLightFromSky() {
        return 15;
    }

    @Override
    public byte getLightFromBlocks() {
        return 0;
    }

    @Override
    public boolean isValidTool(@NotNull ItemStack tool) {
        return true;
    }

    @Override
    public @NotNull Chunk getChunk() {
        return this.world.getChunkAt(this);
    }

    public net.minecraft.core.BlockPos getPosition() {
        return new net.minecraft.core.BlockPos(this.x, this.y, this.z);
    }

    public org.patchbukkit.CraftWorld getCraftWorld() {
        World w = getWorld();
        if (w instanceof org.patchbukkit.CraftWorld cw) {
            return cw;
        }
        return (org.patchbukkit.CraftWorld) org.patchbukkit.world.PatchBukkitWorld.getOrCreate(w != null ? w.getUID() : java.util.UUID.randomUUID());
    }

    public net.minecraft.world.level.block.state.BlockState getBlockState() {
        var bd = getBlockData();
        if (bd instanceof org.bukkit.craftbukkit.block.data.CraftBlockData cbd) {
            return cbd.getState();
        }
        return org.bukkit.craftbukkit.util.CraftMagicNumbers.getBlock(getType()).defaultBlockState();
    }

    public net.minecraft.world.level.block.state.BlockState getNMS() {
        return getBlockState();
    }

    public net.minecraft.world.level.LevelAccessor getLevel() {
        if (this.world instanceof org.patchbukkit.CraftWorld cw && cw.getHandle() != null) {
            return cw.getHandle();
        }
        if (this.world instanceof org.bukkit.craftbukkit.CraftWorld cw) {
            return cw.getHandle();
        }
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            var ds = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (ds != null && ds.overworld() != null) {
                return ds.overworld();
            }
        }
        return (net.minecraft.world.level.LevelAccessor) java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{net.minecraft.world.level.LevelAccessor.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getBlockState") && args != null && args.length == 1 && args[0] instanceof net.minecraft.core.BlockPos p) {
                    Block b = getWorld().getBlockAt(p.getX(), p.getY(), p.getZ());
                    if (b instanceof PatchBukkitBlock pbb) {
                        return pbb.getBlockState();
                    }
                    return org.bukkit.craftbukkit.util.CraftMagicNumbers.getBlock(b.getType()).defaultBlockState();
                }
                if (method.getName().equals("getServer")) {
                    return org.patchbukkit.PatchBukkitServer.getInstance().getDedicatedServer();
                }
                if (method.getName().equals("getMinecraftWorld")) {
                    return getCraftWorld() != null ? getCraftWorld().getHandle() : null;
                }
                return null;
            }
        );
    }

    public static PatchBukkitBlock at(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos position) {
        World world = level != null && level.getMinecraftWorld() != null ? level.getMinecraftWorld().getWorld() : org.bukkit.Bukkit.getWorlds().get(0);
        return new PatchBukkitBlock(world, position.getX(), position.getY(), position.getZ());
    }

    public static boolean setBlockState(net.minecraft.world.level.LevelAccessor level, net.minecraft.core.BlockPos position, net.minecraft.world.level.block.state.BlockState state, boolean applyPhysics) {
        if (level != null && level.getMinecraftWorld() != null) {
            World world = level.getMinecraftWorld().getWorld();
            Block block = world.getBlockAt(position.getX(), position.getY(), position.getZ());
            org.bukkit.block.data.BlockData data = org.bukkit.craftbukkit.block.data.CraftBlockData.createData(state);
            block.setBlockData(data, applyPhysics);
            return true;
        }
        return false;
    }
}
