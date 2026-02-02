package org.patchbukkit.entity;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.PistonMoveReaction;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.bridge.NativePatchBukkit;
import org.patchbukkit.world.PatchBukkitWorld;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentType.Valued;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;

public class PatchBukkitEntity implements Entity {

    protected final UUID uuid;
    private final String name;
    private static PermissibleBase perm;
    private boolean visibleByDefault = true;

    public PatchBukkitEntity(
        UUID uuid,
        String name
    ) {
        this.uuid = uuid;
        this.name = name;
    }

    private static PermissibleBase getPermissibleBase() {
        if (PatchBukkitEntity.perm == null) {
            PatchBukkitEntity.perm = new PermissibleBase(new ServerOperator() {

                @Override
                public boolean isOp() {
                    return false;
                }

                @Override
                public void setOp(boolean value) {

                }
            });
        }
        return PatchBukkitEntity.perm;
    }

    @Override
    public void setMetadata(@NotNull String metadataKey, @NotNull MetadataValue newMetadataValue) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setMetadata'");
    }

    @Override
    public @NotNull List<MetadataValue> getMetadata(@NotNull String metadataKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMetadata'");
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasMetadata'");
    }

    @Override
    public void removeMetadata(@NotNull String metadataKey, @NotNull Plugin owningPlugin) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeMetadata'");
    }

    @Override
    public void sendMessage(String message) {

    }

    @Override
    public void sendMessage(String... messages) {

    }

    @Override
    public void sendMessage(UUID sender, String message) {
        this.sendMessage(message); // Most entities don't know about senders
    }

    @Override
    public void sendMessage(UUID sender, String... messages) {
        this.sendMessage(messages); // Most entities don't know about senders
    }

    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public @NotNull Component name() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'name'");
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isPermissionSet'");
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isPermissionSet'");
    }

    @Override
    public boolean hasPermission(String name) {
        return PatchBukkitEntity.getPermissibleBase().hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return PatchBukkitEntity.getPermissibleBase().hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value,
            int ticks) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeAttachment'");
    }

    @Override
    public void recalculatePermissions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'recalculatePermissions'");
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEffectivePermissions'");
    }

    @Override
    public boolean isOp() {
        return PatchBukkitEntity.getPermissibleBase().isOp();
    }

    @Override
    public void setOp(boolean value) {
        PatchBukkitEntity.getPermissibleBase().setOp(value);
    }

    @Override
    public @Nullable Component customName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'customName'");
    }

    @Override
    public void customName(@Nullable Component customName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'customName'");
    }

    @Override
    public @Nullable String getCustomName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCustomName'");
    }

    @Override
    public void setCustomName(@Nullable String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setCustomName'");
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPersistentDataContainer'");
    }

    @Override
    public <T> @org.jspecify.annotations.Nullable T getData(Valued<T> type) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getData'");
    }

    @Override
    public <T> @org.jspecify.annotations.Nullable T getDataOrDefault(Valued<? extends T> type,
            @org.jspecify.annotations.Nullable T fallback) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getDataOrDefault'");
    }

    @Override
    public boolean hasData(DataComponentType type) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasData'");
    }

    @Override
    public @NotNull Location getLocation() {
        var loc = NativePatchBukkit.getLocation(this.uuid);
        return new Location(this.getWorld(), loc.x(), loc.y(), loc.z());
    }

    @Override
    public @Nullable Location getLocation(@Nullable Location loc) {
        if (loc == null) {
            return this.getLocation();
        }

        var newLoc = this.getLocation();
        loc.set(newLoc.x(), newLoc.y(), newLoc.z());
        return newLoc;
    }

    @Override
    public void setVelocity(@NotNull Vector velocity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setVelocity'");
    }

    @Override
    public @NotNull Vector getVelocity() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVelocity'");
    }

    @Override
    public double getHeight() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getHeight'");
    }

    @Override
    public double getWidth() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getWidth'");
    }

    @Override
    public @NotNull BoundingBox getBoundingBox() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBoundingBox'");
    }

    @Override
    public boolean isOnGround() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isOnGround'");
    }

    @Override
    public boolean isInWater() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInWater'");
    }

    @Override
    public @NotNull World getWorld() {
        var world_uuid_str = NativePatchBukkit.getWorld(this.uuid);
        UUID world_uuid = UUID.fromString(world_uuid_str);
        var world = PatchBukkitWorld.getOrCreate(world_uuid);

        return world;
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setRotation'");
    }

    @Override
    public boolean teleport(@NotNull Location location, @NotNull TeleportCause cause,
            @NotNull TeleportFlag @NotNull... teleportFlags) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teleport'");
    }

    @Override
    public void lookAt(double x, double y, double z, @NotNull LookAnchor entityAnchor) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lookAt'");
    }

    @Override
    public boolean teleport(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teleport'");
    }

    @Override
    public boolean teleport(@NotNull Location location, @NotNull TeleportCause cause) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teleport'");
    }

    @Override
    public boolean teleport(@NotNull Entity destination) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teleport'");
    }

    @Override
    public boolean teleport(@NotNull Entity destination, @NotNull TeleportCause cause) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teleport'");
    }

    @Override
    public @NotNull CompletableFuture<Boolean> teleportAsync(@NotNull Location loc, @NotNull TeleportCause cause,
            @NotNull TeleportFlag @NotNull... teleportFlags) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teleportAsync'");
    }

    @Override
    public @NotNull List<Entity> getNearbyEntities(double x, double y, double z) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getNearbyEntities'");
    }

    @Override
    public int getEntityId() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntityId'");
    }

    @Override
    public int getFireTicks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFireTicks'");
    }

    @Override
    public int getMaxFireTicks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMaxFireTicks'");
    }

    @Override
    public void setFireTicks(int ticks) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFireTicks'");
    }

    @Override
    public void setVisualFire(boolean fire) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setVisualFire'");
    }

    @Override
    public void setVisualFire(@NotNull TriState fire) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setVisualFire'");
    }

    @Override
    public boolean isVisualFire() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isVisualFire'");
    }

    @Override
    public @NotNull TriState getVisualFire() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVisualFire'");
    }

    @Override
    public int getFreezeTicks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFreezeTicks'");
    }

    @Override
    public int getMaxFreezeTicks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getMaxFreezeTicks'");
    }

    @Override
    public void setFreezeTicks(int ticks) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFreezeTicks'");
    }

    @Override
    public boolean isFrozen() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isFrozen'");
    }

    @Override
    public void setInvisible(boolean invisible) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setInvisible'");
    }

    @Override
    public boolean isInvisible() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInvisible'");
    }

    @Override
    public void setNoPhysics(boolean noPhysics) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setNoPhysics'");
    }

    @Override
    public boolean hasNoPhysics() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasNoPhysics'");
    }

    @Override
    public boolean isFreezeTickingLocked() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isFreezeTickingLocked'");
    }

    @Override
    public void lockFreezeTicks(boolean locked) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'lockFreezeTicks'");
    }

    @Override
    public void remove() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'remove'");
    }

    @Override
    public boolean isDead() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isDead'");
    }

    @Override
    public boolean isValid() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isValid'");
    }

    @Override
    public @NotNull Server getServer() {
        return Bukkit.getServer();
    }

    @Override
    public boolean isPersistent() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isPersistent'");
    }

    @Override
    public void setPersistent(boolean persistent) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setPersistent'");
    }

    @Override
    public @Nullable Entity getPassenger() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPassenger'");
    }

    @Override
    public boolean setPassenger(@NotNull Entity passenger) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setPassenger'");
    }

    @Override
    public @NotNull List<Entity> getPassengers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPassengers'");
    }

    @Override
    public boolean addPassenger(@NotNull Entity passenger) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addPassenger'");
    }

    @Override
    public boolean removePassenger(@NotNull Entity passenger) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removePassenger'");
    }

    @Override
    public boolean isEmpty() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isEmpty'");
    }

    @Override
    public boolean eject() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'eject'");
    }

    @Override
    public @NotNull ItemStack getPickItemStack() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPickItemStack'");
    }

    @Override
    public float getFallDistance() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFallDistance'");
    }

    @Override
    public void setFallDistance(float distance) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setFallDistance'");
    }

    @Override
    public void setLastDamageCause(@Nullable EntityDamageEvent event) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setLastDamageCause'");
    }

    @Override
    public @Nullable EntityDamageEvent getLastDamageCause() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getLastDamageCause'");
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return this.uuid;
    }

    @Override
    public int getTicksLived() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTicksLived'");
    }

    @Override
    public void setTicksLived(int value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setTicksLived'");
    }

    @Override
    public void playEffect(@NotNull EntityEffect effect) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'playEffect'");
    }

    @Override
    public @NotNull EntityType getType() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getType'");
    }

    @Override
    public @NotNull Sound getSwimSound() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSwimSound'");
    }

    @Override
    public @NotNull Sound getSwimSplashSound() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSwimSplashSound'");
    }

    @Override
    public @NotNull Sound getSwimHighSpeedSplashSound() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSwimHighSpeedSplashSound'");
    }

    @Override
    public boolean isInsideVehicle() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInsideVehicle'");
    }

    @Override
    public boolean leaveVehicle() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'leaveVehicle'");
    }

    @Override
    public @Nullable Entity getVehicle() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getVehicle'");
    }

    @Override
    public void setCustomNameVisible(boolean flag) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setCustomNameVisible'");
    }

    @Override
    public boolean isCustomNameVisible() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isCustomNameVisible'");
    }

    @Override
    public void setVisibleByDefault(boolean visible) {
        this.visibleByDefault = visible;
    }

    @Override
    public boolean isVisibleByDefault() {
        return this.visibleByDefault;
    }

    @Override
    public @NotNull Set<Player> getTrackedBy() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTrackedBy'");
    }

    @Override
    public boolean isTrackedBy(@NotNull Player player) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isTrackedBy'");
    }

    @Override
    public void setGlowing(boolean flag) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setGlowing'");
    }

    @Override
    public boolean isGlowing() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isGlowing'");
    }

    @Override
    public void setInvulnerable(boolean flag) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setInvulnerable'");
    }

    @Override
    public boolean isInvulnerable() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInvulnerable'");
    }

    @Override
    public boolean isSilent() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isSilent'");
    }

    @Override
    public void setSilent(boolean flag) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setSilent'");
    }

    @Override
    public boolean hasGravity() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasGravity'");
    }

    @Override
    public void setGravity(boolean gravity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setGravity'");
    }

    @Override
    public int getPortalCooldown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPortalCooldown'");
    }

    @Override
    public void setPortalCooldown(int cooldown) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setPortalCooldown'");
    }

    @Override
    public @NotNull Set<String> getScoreboardTags() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getScoreboardTags'");
    }

    @Override
    public boolean addScoreboardTag(@NotNull String tag) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addScoreboardTag'");
    }

    @Override
    public boolean removeScoreboardTag(@NotNull String tag) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeScoreboardTag'");
    }

    @Override
    public @NotNull PistonMoveReaction getPistonMoveReaction() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPistonMoveReaction'");
    }

    @Override
    public @NotNull BlockFace getFacing() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getFacing'");
    }

    @Override
    public @NotNull Pose getPose() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPose'");
    }

    @Override
    public boolean isSneaking() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isSneaking'");
    }

    @Override
    public void setSneaking(boolean sneak) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setSneaking'");
    }

    @Override
    public void setPose(@NotNull Pose pose, boolean fixed) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setPose'");
    }

    @Override
    public boolean hasFixedPose() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasFixedPose'");
    }

    @Override
    public @NotNull SpawnCategory getSpawnCategory() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSpawnCategory'");
    }

    @Override
    public boolean isInWorld() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInWorld'");
    }

    @Override
    public @Nullable String getAsString() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getAsString'");
    }

    @Override
    public @Nullable EntitySnapshot createSnapshot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'createSnapshot'");
    }

    @Override
    public @NotNull Entity copy() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'copy'");
    }

    @Override
    public @NotNull Entity copy(@NotNull Location to) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'copy'");
    }

    @Override
    public @NotNull Spigot spigot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spigot'");
    }

    @Override
    public @NotNull Component teamDisplayName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'teamDisplayName'");
    }

    @Override
    public @Nullable Location getOrigin() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getOrigin'");
    }

    @Override
    public boolean fromMobSpawner() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'fromMobSpawner'");
    }

    @Override
    public @NotNull SpawnReason getEntitySpawnReason() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEntitySpawnReason'");
    }

    @Override
    public boolean isUnderWater() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isUnderWater'");
    }

    @Override
    public boolean isInRain() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInRain'");
    }

    @Override
    public boolean isInLava() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInLava'");
    }

    @Override
    public boolean isTicking() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isTicking'");
    }

    @Override
    public @NotNull Set<Player> getTrackedPlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getTrackedPlayers'");
    }

    @Override
    public boolean spawnAt(@NotNull Location location, @NotNull SpawnReason reason) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spawnAt'");
    }

    @Override
    public boolean isInPowderedSnow() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isInPowderedSnow'");
    }

    @Override
    public double getX() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getX'");
    }

    @Override
    public double getY() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getY'");
    }

    @Override
    public double getZ() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getZ'");
    }

    @Override
    public float getPitch() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getPitch'");
    }

    @Override
    public float getYaw() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getYaw'");
    }

    @Override
    public boolean collidesAt(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'collidesAt'");
    }

    @Override
    public boolean wouldCollideUsing(@NotNull BoundingBox boundingBox) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'wouldCollideUsing'");
    }

    @Override
    public @NotNull EntityScheduler getScheduler() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getScheduler'");
    }

    @Override
    public @NotNull String getScoreboardEntryName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getScoreboardEntryName'");
    }

    @Override
    public void broadcastHurtAnimation(@NotNull Collection<Player> players) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'broadcastHurtAnimation'");
    }
}
