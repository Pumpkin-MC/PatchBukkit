package org.patchbukkit.entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.event.entity.EntityRemoveEvent;
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
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.world.PatchBukkitWorld;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentType.Valued;
import io.papermc.paper.entity.LookAnchor;
import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.util.TriState;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.SetEntityVelocityRequest;
import patchbukkit.entity.TeleportEntityRequest;

public class PatchBukkitEntity implements Entity {

    @Override
    public boolean isInWaterOrRainOrBubbleColumn() {
        return false;
    }

    @Override
    public boolean isInWaterOrBubbleColumn() {
        return false;
    }

    protected final UUID uuid;
    protected final String name;
    private PermissibleBase perm;
    private boolean visibleByDefault = true;
    private final Map<String, List<MetadataValue>> metadataMap = new HashMap<>();
    private volatile boolean removed = false;
    private final PersistentDataContainer persistentDataContainer =
        new org.patchbukkit.persistence.PatchBukkitPersistentDataContainer();
    private final EntityScheduler entityScheduler =
        new org.patchbukkit.scheduler.PatchBukkitEntityScheduler(() -> this.removed);

    private PermissibleBase getPermissible() {
        if (this.perm == null) {
            this.perm = new PermissibleBase(this);
        }
        return this.perm;
    }

    private Location cachedLocation;
    private EntityType entityType = EntityType.UNKNOWN;

    public static Entity create(UUID uuid, EntityType type, Location loc, int entityId) {
        PatchBukkitEntity entity = new PatchBukkitEntity(uuid, type != null ? type.name() : "entity", entityId);
        entity.entityType = type != null ? type : EntityType.UNKNOWN;
        entity.cachedLocation = loc != null ? loc.clone() : new Location(null, 0, 0, 0);
        return entity;
    }

    public static Entity create(UUID uuid, EntityType type, Location loc) {
        return create(uuid, type, loc, -1);
    }

    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ENTITY_ID = new java.util.concurrent.atomic.AtomicInteger(1);
    protected int entityId;

    public PatchBukkitEntity(
        UUID uuid,
        String name,
        int entityId
    ) {
        this.uuid = uuid;
        this.name = name;
        this.entityId = entityId > 0 ? entityId : -1;
    }

    public PatchBukkitEntity(
        UUID uuid,
        String name
    ) {
        this(uuid, name, -1);
    }

    public void setEntityId(int entityId) {
        if (entityId > 0) {
            this.entityId = entityId;
        }
    }

    @Override
    public void setMetadata(@NotNull String metadataKey, @NotNull MetadataValue newMetadataValue) {
        List<MetadataValue> list = metadataMap.computeIfAbsent(metadataKey, k -> new ArrayList<>());
        list.removeIf(v -> v.getOwningPlugin() == newMetadataValue.getOwningPlugin());
        list.add(newMetadataValue);
    }

    @Override
    public @NotNull List<MetadataValue> getMetadata(@NotNull String metadataKey) {
        List<MetadataValue> list = metadataMap.get(metadataKey);
        return list != null ? Collections.unmodifiableList(new ArrayList<>(list)) : Collections.emptyList();
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        List<MetadataValue> list = metadataMap.get(metadataKey);
        return list != null && !list.isEmpty();
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
    public void sendMessage(String message) {
    }

    @Override
    public void sendMessage(String... messages) {
    }

    @Override
    public void sendMessage(UUID sender, String message) {
        this.sendMessage(message);
    }

    @Override
    public void sendMessage(UUID sender, String... messages) {
        this.sendMessage(messages);
    }

    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public @NotNull Component name() {
        return Component.text(this.name);
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        return getPermissible().isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        return getPermissible().isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(String name) {
        return getPermissible().hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return getPermissible().hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        return getPermissible().addAttachment(plugin, name, value);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return getPermissible().addAttachment(plugin);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value, int ticks) {
        return getPermissible().addAttachment(plugin, name, value, ticks);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        return getPermissible().addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        getPermissible().removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        getPermissible().recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return getPermissible().getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return getPermissible().isOp();
    }

    @Override
    public void setOp(boolean value) {
        getPermissible().setOp(value);
    }

    @Override
    public @Nullable Component customName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void customName(@Nullable Component customName) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public @Nullable String getCustomName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setCustomName(@Nullable String name) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        return persistentDataContainer;
    }

    public <T> @org.jspecify.annotations.Nullable T getData(Valued<T> type) {
        // TODO Auto-generated method stub
        return null;
    }

    public <T> @org.jspecify.annotations.Nullable T getDataOrDefault(Valued<? extends T> type,
            @org.jspecify.annotations.Nullable T fallback) {
        return fallback;
    }

    @Override
    public boolean hasData(DataComponentType type) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull Location getLocation() {
        try {
            var location = NativeBridgeFfi.getLocation(BridgeUtils.convertUuid(this.uuid));
            if (location != null && location.hasWorld() && location.hasPosition() && location.getWorld().hasUuid()) {
                var world = PatchBukkitWorld.getOrCreate(BridgeUtils.convertUuid(location.getWorld().getUuid()));
                var position = location.getPosition();
                return new Location(world, position.getX(), position.getY(), position.getZ(), location.getYaw(), location.getPitch());
            }
        } catch (Throwable ignored) {}
        Location fallback = this.cachedLocation != null ? this.cachedLocation.clone() : new Location(null, 0, 0, 0);
        if (fallback.getWorld() == null && !Bukkit.getWorlds().isEmpty()) {
            fallback.setWorld(Bukkit.getWorlds().get(0));
        }
        return fallback;
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

    private Vector velocity = new Vector(0, 0, 0);

    @Override
    public void setVelocity(@NotNull Vector velocity) {
        this.velocity = velocity.clone();
        var request = SetEntityVelocityRequest.newBuilder()
            .setUuid(BridgeUtils.convertUuid(this.uuid))
            .setX(velocity.getX())
            .setY(velocity.getY())
            .setZ(velocity.getZ())
            .build();
        NativeBridgeFfi.setEntityVelocity(request);
    }

    @Override
    public @NotNull Vector getVelocity() {
        var resp = NativeBridgeFfi.getEntityVelocity(BridgeUtils.convertUuid(this.uuid));
        if (resp != null) {
            return new Vector(resp.getX(), resp.getY(), resp.getZ());
        }
        return this.velocity.clone();
    }

    @Override
    public boolean teleport(@NotNull Location location) {
        var request = TeleportEntityRequest.newBuilder()
            .setUuid(BridgeUtils.convertUuid(this.uuid))
            .setLocation(BridgeUtils.convertLocation(location))
            .build();
        NativeBridgeFfi.teleportEntity(request);
        return true;
    }

    @Override
    public boolean teleport(@NotNull Location location, @NotNull TeleportCause cause) {
        return teleport(location);
    }

    @Override
    public double getHeight() {
        // TODO Auto-generated method stub
        return 0.0;
    }

    @Override
    public double getWidth() {
        // TODO Auto-generated method stub
        return 0.0;
    }

    @Override
    public @NotNull BoundingBox getBoundingBox() {
        return new BoundingBox(0, 0, 0, 0, 0, 0);
    }

    @Override
    public boolean isOnGround() {
        var resp = NativeBridgeFfi.isOnGround(BridgeUtils.convertUuid(this.uuid));
        return resp != null && resp.getOnGround();
    }

    @Override
    public boolean isInWater() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull World getWorld() {
        try {
            var location = NativeBridgeFfi.getLocation(BridgeUtils.convertUuid(this.uuid));
            if (location != null && location.hasWorld() && location.getWorld().hasUuid()) {
                return PatchBukkitWorld.getOrCreate(BridgeUtils.convertUuid(location.getWorld().getUuid()));
            }
        } catch (Throwable ignored) {}
        if (this.cachedLocation != null && this.cachedLocation.getWorld() != null) {
            return this.cachedLocation.getWorld();
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    @Override
    public void setRotation(float yaw, float pitch) {
        Location loc = getLocation();
        teleport(new Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), yaw, pitch));
    }

    @Override
    public void setRotation(@NotNull io.papermc.paper.math.Angle yaw, @NotNull io.papermc.paper.math.Angle pitch) {
        setRotation(yaw.degrees(), pitch.degrees());
    }

    @Override
    public void lookAt(double x, double y, double z, @NotNull LookAnchor entityAnchor) {
        Location loc = getLocation();
        double dx = x - loc.getX();
        double dy = y - loc.getY();
        double dz = z - loc.getZ();
        double r = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(-dy, r));
        teleport(new Location(loc.getWorld(), loc.getX(), loc.getY(), loc.getZ(), yaw, pitch));
    }

    @Override
    public boolean teleport(@NotNull Location location, @NotNull TeleportCause cause,
            @NotNull TeleportFlag @NotNull... teleportFlags) {
        return teleport(location, cause);
    }

    @Override
    public boolean teleport(@NotNull Entity destination) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean teleport(@NotNull Entity destination, @NotNull TeleportCause cause) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull CompletableFuture<Boolean> teleportAsync(@NotNull Location loc, @NotNull TeleportCause cause,
            @NotNull TeleportFlag @NotNull... teleportFlags) {
        return CompletableFuture.completedFuture(false);
    }

    @Override
    public @NotNull List<Entity> getNearbyEntities(double x, double y, double z) {
        return Collections.emptyList();
    }

    @Override
    public int getEntityId() {
        if (this.entityId <= 0) {
            try {
                var resp = NativeBridgeFfi.getEntityId(BridgeUtils.convertUuid(this.uuid));
                if (resp != null && resp.getEntityId() > 0) {
                    this.entityId = resp.getEntityId();
                }
            } catch (Throwable ignored) {}
        }
        if (this.entityId <= 0) {
            this.entityId = NEXT_ENTITY_ID.incrementAndGet();
        }
        return this.entityId;
    }

    @Override
    public int getFireTicks() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMaxFireTicks() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setFireTicks(int ticks) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public void setVisualFire(boolean fire) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public void setVisualFire(@NotNull TriState fire) {
        // TODO Auto-generated method stub
        return;
    }

    public boolean isVisualFire() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull TriState getVisualFire() {
        return TriState.FALSE;
    }

    @Override
    public int getFreezeTicks() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public int getMaxFreezeTicks() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setFreezeTicks(int ticks) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean isFrozen() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setInvisible(boolean invisible) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean isInvisible() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setNoPhysics(boolean noPhysics) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean hasNoPhysics() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isFreezeTickingLocked() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void lockFreezeTicks(boolean locked) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public void remove() {
        this.removed = true;
    }

    @Override
    public boolean isDead() {
        return this.removed;
    }

    @Override
    public boolean isValid() {
        // A player is valid while the server still tracks it as online. Non-player entities
        // are considered valid until they are removed.
        if (this instanceof Player) {
            return Bukkit.getPlayer(this.uuid) != null;
        }
        return !this.removed;
    }

    @Override
    public @NotNull Server getServer() {
        return Bukkit.getServer();
    }

    @Override
    public boolean isPersistent() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setPersistent(boolean persistent) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public @Nullable Entity getPassenger() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean setPassenger(@NotNull Entity passenger) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull List<Entity> getPassengers() {
        return Collections.emptyList();
    }

    @Override
    public boolean addPassenger(@NotNull Entity passenger) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean removePassenger(@NotNull Entity passenger) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isEmpty() {
        return getPassengers().isEmpty();
    }

    @Override
    public boolean eject() {
        // TODO Auto-generated method stub
        return false;
    }

    public @NotNull ItemStack getPickItemStack() {
        return new ItemStack(org.bukkit.Material.AIR);
    }

    private float fallDistance = 0.0f;

    @Override
    public float getFallDistance() {
        return this.fallDistance;
    }

    @Override
    public void setFallDistance(float distance) {
        this.fallDistance = distance;
    }

    @Override
    public void setLastDamageCause(@Nullable EntityDamageEvent event) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public @Nullable EntityDamageEvent getLastDamageCause() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return this.uuid;
    }

    @Override
    public int getTicksLived() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setTicksLived(int value) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public void playEffect(@NotNull EntityEffect effect) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public @NotNull EntityType getType() {
        return this.entityType != null ? this.entityType : EntityType.UNKNOWN;
    }

    @Override
    public @NotNull Sound getSwimSound() {
        return Sound.ENTITY_GENERIC_SWIM;
    }

    @Override
    public @NotNull Sound getSwimSplashSound() {
        return Sound.ENTITY_GENERIC_SPLASH;
    }

    @Override
    public @NotNull Sound getSwimHighSpeedSplashSound() {
        return Sound.ENTITY_GENERIC_SPLASH;
    }

    @Override
    public boolean isInsideVehicle() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean leaveVehicle() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @Nullable Entity getVehicle() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void setCustomNameVisible(boolean flag) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean isCustomNameVisible() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setVisibleByDefault(boolean visible) {
        this.visibleByDefault = visible;
    }

    @Override
    public boolean isVisibleByDefault() {
        return this.visibleByDefault;
    }

    public @NotNull Set<Player> getTrackedBy() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isTrackedBy(@NotNull Player player) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setGlowing(boolean flag) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean isGlowing() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setInvulnerable(boolean flag) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean isInvulnerable() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isSilent() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setSilent(boolean flag) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public boolean hasGravity() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void setGravity(boolean gravity) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public int getPortalCooldown() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setPortalCooldown(int cooldown) {
        // TODO Auto-generated method stub
        return;
    }

    @Override
    public @NotNull Set<String> getScoreboardTags() {
        return Collections.emptySet();
    }

    @Override
    public boolean addScoreboardTag(@NotNull String tag) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean removeScoreboardTag(@NotNull String tag) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull PistonMoveReaction getPistonMoveReaction() {
        return PistonMoveReaction.BLOCK;
    }

    @Override
    public @NotNull BlockFace getFacing() {
        return BlockFace.NORTH;
    }

    @Override
    public @NotNull Pose getPose() {
        return Pose.STANDING;
    }

    private boolean sneaking = false;

    @Override
    public boolean isSneaking() {
        return this.sneaking;
    }

    @Override
    public void setSneaking(boolean sneak) {
        this.sneaking = sneak;
    }

    @Override
    public void setPose(@NotNull Pose pose, boolean fixed) {
        // TODO Auto-generated method stub
        return;
    }

    public boolean hasFixedPose() {
        // TODO Auto-generated method stub
        return false;
    }

    public EntityRemoveEvent.@Nullable Cause getRemoveEventCause() {
        return null;
    }

    @Override
    public io.papermc.paper.entity.@Nullable RemovalReason getRemovalReason() {
        return null;
    }

    @Override
    public @NotNull SpawnCategory getSpawnCategory() {
        return SpawnCategory.MISC;
    }

    @Override
    public boolean isInWorld() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @Nullable String getAsString() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @Nullable EntitySnapshot createSnapshot() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @NotNull Entity copy() {
        throw new UnsupportedOperationException("Entity#copy is not implemented");
    }

    @Override
    public @NotNull Entity copy(@NotNull Location to) {
        throw new UnsupportedOperationException("Entity#copy is not implemented");
    }

    @Override
    public @NotNull Spigot spigot() {
        throw new UnsupportedOperationException("Entity#spigot is not implemented");
    }

    @Override
    public @NotNull Component teamDisplayName() {
        return Component.empty();
    }

    @Override
    public @Nullable Location getOrigin() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean fromMobSpawner() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull SpawnReason getEntitySpawnReason() {
        return SpawnReason.CUSTOM;
    }

    @Override
    public boolean isUnderWater() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isInRain() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isInLava() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isTicking() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull Set<Player> getTrackedPlayers() {
        return Collections.emptySet();
    }

    @Override
    public boolean spawnAt(@NotNull Location location, @NotNull SpawnReason reason) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isInPowderedSnow() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public double getX() {
        return getLocation().getX();
    }

    @Override
    public double getY() {
        return getLocation().getY();
    }

    @Override
    public double getZ() {
        return getLocation().getZ();
    }

    @Override
    public float getPitch() {
        return getLocation().getPitch();
    }

    @Override
    public float getYaw() {
        return getLocation().getYaw();
    }

    @Override
    public boolean collidesAt(@NotNull Location location) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean wouldCollideUsing(@NotNull BoundingBox boundingBox) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public @NotNull EntityScheduler getScheduler() {
        return entityScheduler;
    }

    @Override
    public @NotNull String getScoreboardEntryName() {
        return this.name != null ? this.name : this.uuid.toString();
    }

    public void broadcastHurtAnimation(@NotNull Collection<Player> players) {
        // TODO Auto-generated method stub
        return;
    }

    public Source soundSource() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public @NotNull SoundCategory getSoundCategory() {
        return SoundCategory.NEUTRAL;
    }
}
