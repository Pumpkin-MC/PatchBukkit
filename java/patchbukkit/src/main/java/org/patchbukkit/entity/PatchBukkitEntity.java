package org.patchbukkit.entity;

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

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.Material;
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
import patchbukkit.entity.SetEntityPoseRequest;
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

    private PermissibleBase getPermissible() {
        if (this.perm == null) {
            this.perm = new PermissibleBase(this);
        }
        return this.perm;
    }

    private Location cachedLocation;
    private EntityType entityType = EntityType.UNKNOWN;

    // Local entity state (degradation zone: kept in memory, synced to the
    // server via FFI where a bridge call exists, otherwise query-consistent).
    private Component customName;
    private String customNameString;
    private boolean customNameVisible;
    private final org.patchbukkit.persistence.PatchBukkitPersistentDataContainer persistentDataContainer =
        new org.patchbukkit.persistence.PatchBukkitPersistentDataContainer();
    private int fireTicks;
    private TriState visualFire = TriState.NOT_SET;
    private int freezeTicks;
    private boolean freezeTickingLocked;
    private boolean invisible;
    private boolean noPhysics;
    private boolean removed;
    private boolean persistent;
    private boolean glowing;
    private boolean invulnerable;
    private boolean silent;
    private boolean gravity = true;
    private int portalCooldown;
    private final Set<String> scoreboardTags = new HashSet<>();
    private EntityDamageEvent lastDamageCause;
    private int ticksLived;
    private boolean fixedPose;
    private boolean fromMobSpawner;

    public static Entity create(UUID uuid, EntityType type, Location loc) {
        PatchBukkitEntity entity = new PatchBukkitEntity(uuid, type != null ? type.name() : "entity");
        entity.entityType = type != null ? type : EntityType.UNKNOWN;
        entity.cachedLocation = loc != null ? loc.clone() : new Location(null, 0, 0, 0);
        return entity;
    }

    public PatchBukkitEntity(
        UUID uuid,
        String name
    ) {
        this.uuid = uuid;
        this.name = name;
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
        if (getPermissible().isPermissionSet(name)) {
            return getPermissible().hasPermission(name);
        }
        // Not set locally: ask the server (vanilla nodes, plugin defaults
        // and mirrored attachments live there).
        try {
            var resp = patchbukkit.bridge.NativeBridgeFfi.hasPlayerPermission(
                patchbukkit.permission.HasPlayerPermissionRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                    .setNode(name != null ? name : "")
                    .build());
            if (resp != null) {
                return resp.getHas();
            }
        } catch (Throwable ignored) {}
        return getPermissible().hasPermission(name);
    }

    @Override
    public boolean hasPermission(Permission perm) {
        return perm != null ? hasPermission(perm.getName()) : false;
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        PermissionAttachment attachment = getPermissible().addAttachment(plugin, name, value);
        // Mirror explicit grants so server-side checks see them too.
        try {
            patchbukkit.bridge.NativeBridgeFfi.setPlayerPermission(
                patchbukkit.permission.SetPlayerPermissionRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                    .setNode(name)
                    .setValue(value)
                    .build());
        } catch (Throwable ignored) {}
        return attachment;
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
        try {
            if (attachment != null && attachment.getPermissions() != null) {
                for (String node : attachment.getPermissions().keySet()) {
                    patchbukkit.bridge.NativeBridgeFfi.unsetPlayerPermission(
                        patchbukkit.permission.UnsetPlayerPermissionRequest.newBuilder()
                            .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                            .setNode(node)
                            .build());
                }
            }
        } catch (Throwable ignored) {}
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
        return this.customName;
    }

    @Override
    public void customName(@Nullable Component customName) {
        this.customName = customName;
        this.customNameString = customName != null
            ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName)
            : null;
        pushDisplayName();
    }

    @Override
    public @Nullable String getCustomName() {
        return this.customNameString;
    }

    @Override
    public void setCustomName(@Nullable String name) {
        this.customNameString = name;
        this.customName = name != null ? Component.text(name) : null;
        pushDisplayName();
    }

    private void pushDisplayName() {
        try {
            var request = patchbukkit.entity.SetDisplayNameRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(this.uuid))
                .setDisplayName(this.customNameString != null ? this.customNameString : "")
                .build();
            NativeBridgeFfi.setDisplayName(request);
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        return this.persistentDataContainer;
    }

    public <T> @org.jspecify.annotations.Nullable T getData(Valued<T> type) {
        return null;
    }

    public <T> @org.jspecify.annotations.Nullable T getDataOrDefault(Valued<? extends T> type,
            @org.jspecify.annotations.Nullable T fallback) {
        return fallback;
    }

    @Override
    public boolean hasData(DataComponentType type) {
        return false;
    }

    @Override
    public @NotNull Location getLocation() {
        try {
            var location = NativeBridgeFfi.getLocation(BridgeUtils.convertUuid(this.uuid));
            if (location != null && location.hasWorld() && location.hasPosition()) {
                var world = PatchBukkitWorld.getOrCreate(BridgeUtils.convertUuid(location.getWorld().getUuid()));
                var position = location.getPosition();
                return new Location(world, position.getX(), position.getY(), position.getZ(), location.getYaw(), location.getPitch());
            }
        } catch (Throwable ignored) {}
        return this.cachedLocation != null ? this.cachedLocation.clone() : new Location(null, 0, 0, 0);
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
        if (this.entityType == null) return 1.8;
        return switch (this.entityType) {
            case PLAYER -> 1.8;
            case ITEM, EGG, SNOWBALL, ENDER_PEARL, ARROW, SPECTRAL_ARROW -> 0.25;
            case CHICKEN, BAT, PARROT, RABBIT, FROG -> 0.6;
            case COW, PIG, SHEEP, WOLF, OCELOT, CAT, FOX -> 0.9;
            case ZOMBIE, SKELETON, CREEPER, SPIDER, ENDERMAN, VILLAGER -> 1.8;
            case HORSE, DONKEY, MULE -> 1.6;
            case IRON_GOLEM -> 2.7;
            case ENDER_DRAGON -> 8.0;
            case WITHER -> 3.5;
            default -> 1.8;
        };
    }

    @Override
    public double getWidth() {
        if (this.entityType == null) return 0.6;
        return switch (this.entityType) {
            case PLAYER -> 0.6;
            case ITEM, EGG, SNOWBALL, ENDER_PEARL, ARROW, SPECTRAL_ARROW -> 0.25;
            case CHICKEN, BAT, PARROT, RABBIT, FROG -> 0.4;
            case COW, PIG, SHEEP, WOLF, OCELOT, CAT, FOX -> 0.6;
            case ZOMBIE, SKELETON, CREEPER, ENDERMAN, VILLAGER -> 0.6;
            case SPIDER -> 1.4;
            case HORSE, DONKEY, MULE -> 1.4;
            case IRON_GOLEM -> 1.4;
            case ENDER_DRAGON -> 16.0;
            case WITHER -> 0.9;
            default -> 0.6;
        };
    }

    @Override
    public @NotNull BoundingBox getBoundingBox() {
        Location loc = getLocation();
        double w = getWidth() / 2.0;
        double h = getHeight();
        return new BoundingBox(
            loc.getX() - w, loc.getY(), loc.getZ() - w,
            loc.getX() + w, loc.getY() + h, loc.getZ() + w);
    }

    @Override
    public boolean isOnGround() {
        var resp = NativeBridgeFfi.isOnGround(BridgeUtils.convertUuid(this.uuid));
        return resp != null && resp.getOnGround();
    }

    @Override
    public boolean isInWater() {
        return false;
    }

    @Override
    public @NotNull World getWorld() {
        // The Rust side returns null for entities it does not know (e.g. synthetic
        // players created for events). Fall back to the first loaded world instead
        // of throwing NPE so event construction can proceed.
        try {
            var location = NativeBridgeFfi.getLocation(BridgeUtils.convertUuid(this.uuid));
            if (location != null && location.hasWorld()
                    && location.getWorld().getUuid() != null
                    && !location.getWorld().getUuid().getValue().isEmpty()) {
                PatchBukkitWorld world = PatchBukkitWorld.getOrCreate(
                    BridgeUtils.convertUuid(location.getWorld().getUuid()));
                if (world != null) {
                    return world;
                }
            }
        } catch (Throwable ignored) {}
        java.util.List<World> worlds = org.bukkit.Bukkit.getWorlds();
        if (!worlds.isEmpty()) {
            return worlds.get(0);
        }
        throw new IllegalStateException("No world available for entity " + this.uuid);
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
        return teleport(destination.getLocation());
    }

    @Override
    public boolean teleport(@NotNull Entity destination, @NotNull TeleportCause cause) {
        return teleport(destination.getLocation(), cause);
    }

    @Override
    public @NotNull CompletableFuture<Boolean> teleportAsync(@NotNull Location loc, @NotNull TeleportCause cause,
            @NotNull TeleportFlag @NotNull... teleportFlags) {
        return CompletableFuture.completedFuture(teleport(loc, cause));
    }

    @Override
    public @NotNull List<Entity> getNearbyEntities(double x, double y, double z) {
        try {
            return new ArrayList<>(getWorld().getNearbyEntities(getLocation(), x, y, z));
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    @Override
    public int getEntityId() {
        return this.uuid.hashCode();
    }

    @Override
    public int getFireTicks() {
        return this.fireTicks;
    }

    @Override
    public int getMaxFireTicks() {
        return 20;
    }

    @Override
    public void setFireTicks(int ticks) {
        this.fireTicks = Math.max(0, ticks);
    }

    @Override
    public void setVisualFire(boolean fire) {
        this.visualFire = fire ? TriState.TRUE : TriState.FALSE;
    }

    @Override
    public void setVisualFire(@NotNull TriState fire) {
        this.visualFire = fire != null ? fire : TriState.NOT_SET;
    }

    public boolean isVisualFire() {
        return this.visualFire == TriState.TRUE;
    }

    @Override
    public @NotNull TriState getVisualFire() {
        return this.visualFire;
    }

    @Override
    public int getFreezeTicks() {
        return this.freezeTicks;
    }

    @Override
    public int getMaxFreezeTicks() {
        return 140;
    }

    @Override
    public void setFreezeTicks(int ticks) {
        if (!this.freezeTickingLocked) {
            this.freezeTicks = Math.max(0, ticks);
        }
    }

    @Override
    public boolean isFrozen() {
        return this.freezeTicks > 0;
    }

    @Override
    public void setInvisible(boolean invisible) {
        this.invisible = invisible;
    }

    @Override
    public boolean isInvisible() {
        return this.invisible;
    }

    @Override
    public void setNoPhysics(boolean noPhysics) {
        this.noPhysics = noPhysics;
    }

    @Override
    public boolean hasNoPhysics() {
        return this.noPhysics;
    }

    @Override
    public boolean isFreezeTickingLocked() {
        return this.freezeTickingLocked;
    }

    @Override
    public void lockFreezeTicks(boolean locked) {
        this.freezeTickingLocked = locked;
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
        return !this.removed;
    }

    @Override
    public @NotNull Server getServer() {
        return Bukkit.getServer();
    }

    @Override
    public boolean isPersistent() {
        return this.persistent;
    }

    @Override
    public void setPersistent(boolean persistent) {
        this.persistent = persistent;
    }

    @Override
    public @Nullable Entity getPassenger() {
        return null;
    }

    @Override
    public boolean setPassenger(@NotNull Entity passenger) {
        return false;
    }

    @Override
    public @NotNull List<Entity> getPassengers() {
        return List.of();
    }

    @Override
    public boolean addPassenger(@NotNull Entity passenger) {
        return false;
    }

    @Override
    public boolean removePassenger(@NotNull Entity passenger) {
        return false;
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public boolean eject() {
        return false;
    }

    public @NotNull ItemStack getPickItemStack() {
        Material mat = Material.AIR;
        if (this.entityType != null) {
            mat = switch (this.entityType) {
                case COW -> Material.BEEF;
                case PIG -> Material.PORKCHOP;
                case SHEEP -> Material.WHITE_WOOL;
                case CHICKEN -> Material.CHICKEN;
                case HORSE -> Material.LEATHER;
                case WOLF -> Material.BONE;
                case OCELOT, CAT -> Material.COD;
                case CREEPER -> Material.GUNPOWDER;
                case ZOMBIE -> Material.ROTTEN_FLESH;
                case SKELETON -> Material.BONE;
                case SPIDER -> Material.STRING;
                case ENDERMAN -> Material.ENDER_PEARL;
                case PIGLIN -> Material.GOLD_INGOT;
                default -> Material.AIR;
            };
        }
        return new ItemStack(mat);
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
        this.lastDamageCause = event;
    }

    @Override
    public @Nullable EntityDamageEvent getLastDamageCause() {
        return this.lastDamageCause;
    }

    @Override
    public @NotNull UUID getUniqueId() {
        return this.uuid;
    }

    @Override
    public int getTicksLived() {
        return this.ticksLived;
    }

    @Override
    public void setTicksLived(int value) {
        this.ticksLived = Math.max(1, value);
    }

    @Override
    public void playEffect(@NotNull EntityEffect effect) {
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
        return false;
    }

    @Override
    public boolean leaveVehicle() {
        return false;
    }

    @Override
    public @Nullable Entity getVehicle() {
        return null;
    }

    @Override
    public void setCustomNameVisible(boolean flag) {
        this.customNameVisible = flag;
    }

    @Override
    public boolean isCustomNameVisible() {
        return this.customNameVisible;
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
        return Set.of();
    }

    @Override
    public boolean isTrackedBy(@NotNull Player player) {
        return false;
    }

    @Override
    public void setGlowing(boolean flag) {
        this.glowing = flag;
    }

    @Override
    public boolean isGlowing() {
        return this.glowing;
    }

    @Override
    public void setInvulnerable(boolean flag) {
        this.invulnerable = flag;
    }

    @Override
    public boolean isInvulnerable() {
        return this.invulnerable;
    }

    @Override
    public boolean isSilent() {
        return this.silent;
    }

    @Override
    public void setSilent(boolean flag) {
        this.silent = flag;
    }

    @Override
    public boolean hasGravity() {
        return this.gravity;
    }

    @Override
    public void setGravity(boolean gravity) {
        this.gravity = gravity;
    }

    @Override
    public int getPortalCooldown() {
        return this.portalCooldown;
    }

    @Override
    public void setPortalCooldown(int cooldown) {
        this.portalCooldown = Math.max(0, cooldown);
    }

    @Override
    public @NotNull Set<String> getScoreboardTags() {
        return Collections.unmodifiableSet(this.scoreboardTags);
    }

    @Override
    public boolean addScoreboardTag(@NotNull String tag) {
        return this.scoreboardTags.add(tag);
    }

    @Override
    public boolean removeScoreboardTag(@NotNull String tag) {
        return this.scoreboardTags.remove(tag);
    }

    @Override
    public @NotNull PistonMoveReaction getPistonMoveReaction() {
        return PistonMoveReaction.MOVE;
    }

    @Override
    public @NotNull BlockFace getFacing() {
        float yaw = getLocation().getYaw() % 360.0f;
        if (yaw < 0) yaw += 360.0f;
        if (yaw < 22.5f || yaw >= 337.5f) return BlockFace.SOUTH;
        if (yaw < 67.5f) return BlockFace.SOUTH_WEST;
        if (yaw < 112.5f) return BlockFace.WEST;
        if (yaw < 157.5f) return BlockFace.NORTH_WEST;
        if (yaw < 202.5f) return BlockFace.NORTH;
        if (yaw < 247.5f) return BlockFace.NORTH_EAST;
        if (yaw < 292.5f) return BlockFace.EAST;
        return BlockFace.SOUTH_EAST;
    }

    @Override
    public @NotNull Pose getPose() {
        try {
            var resp = NativeBridgeFfi.getPlayerPoseState(BridgeUtils.convertUuid(getUniqueId()));
            if (resp != null) {
                if (resp.getIsSleeping()) return Pose.SLEEPING;
                if (resp.getIsSwimming()) return Pose.SWIMMING;
                if (resp.getIsGliding()) return Pose.FALL_FLYING;
                if (resp.getIsSneaking()) return Pose.SNEAKING;
            }
        } catch (Throwable ignored) {}
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
        this.fixedPose = fixed;
        try {
            var request = SetEntityPoseRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(getUniqueId()))
                .setPose(pose != null ? pose.name() : Pose.STANDING.name())
                .build();
            NativeBridgeFfi.setEntityPose(request);
        } catch (Throwable ignored) {}
    }

    public boolean hasFixedPose() {
        return this.fixedPose;
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
        if (this.entityType == null) return SpawnCategory.MISC;
        return switch (this.entityType) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, ENDERMAN, WITCH, SLIME, PHANTOM -> SpawnCategory.MONSTER;
            case COW, PIG, SHEEP, CHICKEN, HORSE, WOLF, CAT, VILLAGER -> SpawnCategory.ANIMAL;
            case SQUID, DOLPHIN, TURTLE, COD, SALMON -> SpawnCategory.WATER_ANIMAL;
            case BAT -> SpawnCategory.AMBIENT;
            default -> SpawnCategory.MISC;
        };
    }

    @Override
    public boolean isInWorld() {
        return !this.removed;
    }

    @Override
    public @Nullable String getAsString() {
        return getType().name() + "[" + this.uuid + "]";
    }

    @Override
    public @Nullable EntitySnapshot createSnapshot() {
        return new PatchBukkitEntitySnapshot(
            getType(),
            getLocation().clone(),
            this.customName,
            this.customNameVisible);
    }

    @Override
    public @NotNull Entity copy() {
        return copy(getLocation().clone());
    }

    @Override
    public @NotNull Entity copy(@NotNull Location to) {
        PatchBukkitEntity copy = new PatchBukkitEntity(UUID.randomUUID(), this.name);
        copy.entityType = this.entityType;
        copy.cachedLocation = to.clone();
        copy.copyLocalStateFrom(this);
        return copy;
    }

    /**
    * Copies local (non-FFI) state into another instance of the same class.
    * Server-side state stays with the original; the copy starts detached.
    */
    protected void copyLocalStateFrom(@NotNull PatchBukkitEntity other) {
        this.customName = other.customName;
        this.customNameString = other.customNameString;
        this.customNameVisible = other.customNameVisible;
        other.persistentDataContainer.copyTo(this.persistentDataContainer, false);
        this.fireTicks = other.fireTicks;
        this.visualFire = other.visualFire;
        this.freezeTicks = other.freezeTicks;
        this.freezeTickingLocked = other.freezeTickingLocked;
        this.invisible = other.invisible;
        this.noPhysics = other.noPhysics;
        this.persistent = other.persistent;
        this.glowing = other.glowing;
        this.invulnerable = other.invulnerable;
        this.silent = other.silent;
        this.gravity = other.gravity;
        this.portalCooldown = other.portalCooldown;
        this.scoreboardTags.clear();
        this.scoreboardTags.addAll(other.scoreboardTags);
        this.lastDamageCause = other.lastDamageCause;
        this.ticksLived = other.ticksLived;
        this.fixedPose = other.fixedPose;
        this.fromMobSpawner = other.fromMobSpawner;
    }

    @Override
    public @NotNull Spigot spigot() {
        return new Spigot();
    }

    @Override
    public @NotNull Component teamDisplayName() {
        Component custom = customName();
        return custom != null ? custom : name();
    }

    @Override
    public @Nullable Location getOrigin() {
        Location base = this.cachedLocation != null ? this.cachedLocation : getLocation();
        return base != null ? base.clone() : null;
    }

    @Override
    public boolean fromMobSpawner() {
        return this.fromMobSpawner;
    }

    @Override
    public @NotNull SpawnReason getEntitySpawnReason() {
        return SpawnReason.NATURAL;
    }

    @Override
    public boolean isUnderWater() {
        return false;
    }

    @Override
    public boolean isInRain() {
        return false;
    }

    @Override
    public boolean isInLava() {
        return false;
    }

    @Override
    public boolean isTicking() {
        return !this.removed;
    }

    @Override
    public @NotNull Set<Player> getTrackedPlayers() {
        return Set.of();
    }

    @Override
    public boolean spawnAt(@NotNull Location location, @NotNull SpawnReason reason) {
        return false;
    }

    @Override
    public boolean isInPowderedSnow() {
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
        double halfWidth = getWidth() / 2.0;
        double height = getHeight();
        BoundingBox box = new BoundingBox(
            location.getX() - halfWidth, location.getY(), location.getZ() - halfWidth,
            location.getX() + halfWidth, location.getY() + height, location.getZ() + halfWidth);
        return collides(box, location.getWorld());
    }

    @Override
    public boolean wouldCollideUsing(@NotNull BoundingBox boundingBox) {
        return collides(boundingBox, getWorld());
    }

    /**
    * Tests a world-space box against solid blocks and other entities.
    * Block solidity comes from local material data; entities come from the
    * world's nearby lookup. The entity itself is excluded.
    */
    private boolean collides(@NotNull BoundingBox box, @Nullable World world) {
        if (world != null) {
            int minX = (int) Math.floor(box.getMinX());
            int maxX = (int) Math.floor(box.getMaxX() - 1e-7);
            int minY = (int) Math.floor(box.getMinY());
            int maxY = (int) Math.floor(box.getMaxY() - 1e-7);
            int minZ = (int) Math.floor(box.getMinZ());
            int maxZ = (int) Math.floor(box.getMaxZ() - 1e-7);
            try {
                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            if (world.getBlockAt(x, y, z).getType().isSolid()) {
                                return true;
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            try {
                double cx = (box.getMinX() + box.getMaxX()) / 2.0;
                double cy = (box.getMinY() + box.getMaxY()) / 2.0;
                double cz = (box.getMinZ() + box.getMaxZ()) / 2.0;
                double rx = (box.getMaxX() - box.getMinX()) / 2.0 + 1.0;
                double ry = (box.getMaxY() - box.getMinY()) / 2.0 + 1.0;
                double rz = (box.getMaxZ() - box.getMinZ()) / 2.0 + 1.0;
                for (Entity other : world.getNearbyEntities(new Location(world, cx, cy, cz), rx, ry, rz)) {
                    if (other == null || other.getUniqueId().equals(getUniqueId())) {
                        continue;
                    }
                    try {
                        if (other.getBoundingBox().overlaps(box)) {
                            return true;
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        return false;
    }

    private volatile io.papermc.paper.threadedregions.scheduler.EntityScheduler entityScheduler;

    @Override
    public @NotNull io.papermc.paper.threadedregions.scheduler.EntityScheduler getScheduler() {
        io.papermc.paper.threadedregions.scheduler.EntityScheduler scheduler = this.entityScheduler;
        if (scheduler == null) {
            scheduler = new org.patchbukkit.scheduler.PatchBukkitEntityScheduler(this);
            this.entityScheduler = scheduler;
        }
        return scheduler;
    }

    @Override
    public @NotNull String getScoreboardEntryName() {
        return this.uuid.toString();
    }

    public void broadcastHurtAnimation(@NotNull Collection<Player> players) {
    }

    public Source soundSource() {
        return Source.NEUTRAL;
    }

    @Override
    public @NotNull SoundCategory getSoundCategory() {
        return SoundCategory.NEUTRAL;
    }
}
