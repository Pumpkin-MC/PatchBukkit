package org.patchbukkit.bootstrap;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.entity.Visibility;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Pose;
import org.bukkit.util.Vector;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.DamageEntityRequest;
import patchbukkit.entity.EntityHealthResponse;
import patchbukkit.entity.EntityVelocityResponse;
import patchbukkit.entity.GetEntityIdResponse;
import patchbukkit.entity.IsOnGroundResponse;
import patchbukkit.entity.SetEntityHealthRequest;
import patchbukkit.entity.SetEntityPoseRequest;
import patchbukkit.entity.SetEntityVelocityRequest;
import patchbukkit.entity.TeleportEntityRequest;
import patchbukkit.world.EntitySummaryProto;
import patchbukkit.world.GetWorldEntitiesRequest;
import patchbukkit.world.GetWorldEntitiesResponse;
import patchbukkit.world.SpawnWorldEntityRequest;
import patchbukkit.world.SpawnWorldEntityResponse;

/**
 * Bridges Paper's NMS entity tracking, lifecycle, and state mutations to headless mode and Pumpkin.
 *
 * <p>In Paper's Moonrise chunk system, entity accessibility and ticking normally depend
 * on active chunk ticking threads. In headless mode, we intercept {@link EntityLookup#getEntityStatus(Entity)}
 * to always treat tracked entities as {@link Visibility#TICKING}, ensuring that entities
 * added to {@link ServerLevel} are immediately accessible to {@link org.bukkit.World#getEntities()}
 * and {@link org.bukkit.World#getEntity(UUID)}.
 *
 * <p>Additionally bridges entity spawn, removal, teleportation, and velocity between Bukkit/NMS
 * and Pumpkin's Rust-native entity system via FFI.
 */
public final class EntityBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final Set<UUID> MIRRORED_FROM_PUMPKIN = ConcurrentHashMap.newKeySet();

    private EntityBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            ByteBuddy byteBuddy = new ByteBuddy();

            // 1. Intercept EntityLookup.getEntityStatus
            byteBuddy
                .redefine(EntityLookup.class)
                .method(ElementMatchers.named("getEntityStatus").and(ElementMatchers.takesArguments(Entity.class)))
                .intercept(MethodDelegation.to(EntityLookupInterceptor.class))
                .make()
                .load(EntityLookup.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            // 2. Intercept ServerLevel$EntityCallbacks for tracking start/end
            try {
                Class<?> callbacksClass = Class.forName("net.minecraft.server.level.ServerLevel$EntityCallbacks");
                byteBuddy
                    .redefine(callbacksClass)
                    .visit(Advice.to(OnTrackingStartAdvice.class).on(ElementMatchers.named("onTrackingStart").and(ElementMatchers.takesArguments(Entity.class))))
                    .visit(Advice.to(OnTrackingEndAdvice.class).on(ElementMatchers.named("onTrackingEnd").and(ElementMatchers.takesArguments(Entity.class))))
                    .make()
                    .load(callbacksClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook ServerLevel$EntityCallbacks", t);
            }

            // 3. Intercept CraftEntity mutations (teleport and velocity) and live state queries (getLocation / getVelocity)
            try {
                byteBuddy
                    .redefine(CraftEntity.class)
                    .visit(Advice.to(OnTeleportAdvice.class).on(ElementMatchers.named("teleport0")))
                    .visit(Advice.to(OnSetVelocityAdvice.class).on(ElementMatchers.named("setVelocity").and(ElementMatchers.takesArguments(Vector.class))))
                    .visit(Advice.to(OnSyncStateAdvice.class).on(
                        ElementMatchers.named("getLocation")
                            .or(ElementMatchers.named("getVelocity").and(ElementMatchers.takesNoArguments()))
                    ))
                    .visit(Advice.to(OnIsOnGroundAdvice.class).on(
                        ElementMatchers.named("isOnGround").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnSetPoseAdvice.class).on(
                        ElementMatchers.named("setPose").and(ElementMatchers.takesArguments(Pose.class, boolean.class))
                    ))
                    .visit(Advice.to(OnGetEntityIdAdvice.class).on(
                        ElementMatchers.named("getEntityId").and(ElementMatchers.takesNoArguments())
                    ))
                    .make()
                    .load(CraftEntity.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftEntity mutations", t);
            }

            // 4. Intercept CraftLivingEntity health & damage for non-player living entities
            try {
                byteBuddy
                    .redefine(CraftLivingEntity.class)
                    .visit(Advice.to(OnLivingGetHealthAdvice.class).on(
                        ElementMatchers.named("getHealth").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnLivingSetHealthAdvice.class).on(
                        ElementMatchers.named("setHealth").and(ElementMatchers.takesArguments(double.class))
                    ))
                    .visit(Advice.to(OnLivingDamageAdvice.class).on(
                        ElementMatchers.named("damage").and(ElementMatchers.takesArguments(double.class, org.bukkit.entity.Entity.class))
                    ))
                    .visit(Advice.to(OnLivingDamageItemStackAdvice.class).on(
                        ElementMatchers.named("damageItemStack")
                    ))
                    .make()
                    .load(CraftLivingEntity.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftLivingEntity mutations", t);
            }

            LOGGER.info("[PatchBukkit] Successfully patched EntityLookup and entity lifecycle with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch entity system with ByteBuddyAgent", t);
        }
    }

    public static void syncEntitiesFromPumpkin(ServerLevel level, UUID worldUuid) {
        if (level == null || worldUuid == null) return;
        try {
            GetWorldEntitiesRequest req = GetWorldEntitiesRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(worldUuid))
                .build();
            GetWorldEntitiesResponse res = NativeBridgeFfi.getWorldEntities(req);
            if (res != null && !res.getEntitiesList().isEmpty()) {
                for (EntitySummaryProto proto : res.getEntitiesList()) {
                    if (proto.getIsPlayer()) continue;
                    UUID entityUuid = BridgeUtils.convertUuid(proto.getUuid());
                    if (entityUuid == null || level.getEntity(entityUuid) != null) continue;

                    String typeStr = proto.getEntityType().toLowerCase(Locale.ROOT);
                    Identifier key = typeStr.contains(":") ? Identifier.parse(typeStr) : Identifier.fromNamespaceAndPath("minecraft", typeStr);
                    net.minecraft.world.entity.EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getValue(key);
                    if (entityType == null) {
                        entityType = net.minecraft.world.entity.EntityTypes.PIG;
                    }
                    Entity entity = entityType.create(level, EntitySpawnReason.LOAD);
                    if (entity != null) {
                        if (proto.getEntityId() != 0) {
                            entity.setId(proto.getEntityId());
                        }
                        entity.setUUID(entityUuid);
                        entity.setPos(proto.getX(), proto.getY(), proto.getZ());
                        entity.setYRot(proto.getYaw());
                        entity.setXRot(proto.getPitch());
                        if (!proto.getCustomName().isEmpty()) {
                            entity.setCustomName(io.papermc.paper.adventure.PaperAdventure.asVanilla(
                                net.kyori.adventure.text.Component.text(proto.getCustomName())
                            ));
                        }
                        MIRRORED_FROM_PUMPKIN.add(entityUuid);
                        level.addWithUUID(entity);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void handleEntityAdded(Entity entity) {
        if (entity == null || entity instanceof ServerPlayer) return;
        UUID uuid = entity.getUUID();
        if (MIRRORED_FROM_PUMPKIN.remove(uuid)) return;

        if (entity.level() instanceof ServerLevel serverLevel) {
            UUID worldUuid = serverLevel.getWorld() != null ? serverLevel.getWorld().getUID() : null;
            if (worldUuid != null) {
                try {
                    Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
                    String typeName = (key != null) ? key.getPath().toUpperCase(Locale.ROOT) : "PIG";
                    SpawnWorldEntityRequest.Builder reqBuilder = SpawnWorldEntityRequest.newBuilder()
                        .setWorldUuid(BridgeUtils.convertUuid(worldUuid))
                        .setEntityType(typeName)
                        .setX(entity.getX())
                        .setY(entity.getY())
                        .setZ(entity.getZ())
                        .setYaw(entity.getYRot())
                        .setPitch(entity.getXRot())
                        .setEntityUuid(BridgeUtils.convertUuid(uuid))
                        .setEntityId(entity.getId());
                    if (entity instanceof net.minecraft.world.entity.item.ItemEntity itemEntity) {
                        reqBuilder.setItemStack(InventoryBridge.nmsToProto(itemEntity.getItem()));
                    } else if (entity instanceof net.minecraft.world.entity.ExperienceOrb expOrb) {
                        reqBuilder.setExperience(expOrb.getValue());
                    }
                    SpawnWorldEntityResponse resp = NativeBridgeFfi.spawnWorldEntity(reqBuilder.build());
                    if (resp != null && resp.getEntityId() != 0 && resp.getEntityId() != entity.getId()) {
                        entity.setId(resp.getEntityId());
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    public static void handleEntityRemoved(Entity entity) {
        if (entity == null || entity instanceof ServerPlayer) return;
        try {
            NativeBridgeFfi.removeEntity(BridgeUtils.convertUuid(entity.getUUID()));
        } catch (Throwable ignored) {}
    }

    public static void handleEntityTeleported(CraftEntity craftEntity, Location location) {
        if (craftEntity == null || location == null) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        try {
            UUID worldUuid = location.getWorld() != null ? location.getWorld().getUID() : null;
            if (NativeFastPathFfi.isInitialized()) {
                NativeFastPathFfi.teleportEntity(
                    handle.getUUID(),
                    worldUuid,
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    location.getYaw(),
                    location.getPitch()
                );
                return;
            }
            TeleportEntityRequest req = TeleportEntityRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                .setLocation(BridgeUtils.convertLocation(location))
                .build();
            NativeBridgeFfi.teleportEntity(req);
        } catch (Throwable ignored) {}
    }

    public static void handleEntityVelocitySet(CraftEntity craftEntity, Vector velocity) {
        if (craftEntity == null || velocity == null) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        try {
            if (NativeFastPathFfi.isInitialized()) {
                NativeFastPathFfi.setEntityVelocity(
                    handle.getUUID(),
                    velocity.getX(),
                    velocity.getY(),
                    velocity.getZ()
                );
                return;
            }
            SetEntityVelocityRequest req = SetEntityVelocityRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                .setX(velocity.getX())
                .setY(velocity.getY())
                .setZ(velocity.getZ())
                .build();
            NativeBridgeFfi.setEntityVelocity(req);
        } catch (Throwable ignored) {}
    }

    public static void syncEntityStateFromPumpkin(CraftEntity craftEntity) {
        if (craftEntity == null || !NativeFastPathFfi.isInitialized()) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        if (!NativeFastPathFfi.syncNmsEntityState(handle)) {
            try {
                patchbukkit.common.UUID protoUuid = BridgeUtils.convertUuid(handle.getUUID());
                patchbukkit.common.Location loc = NativeBridgeFfi.getLocation(protoUuid);
                if (loc != null && loc.hasPosition()) {
                    handle.setPosRaw(loc.getPosition().getX(), loc.getPosition().getY(), loc.getPosition().getZ());
                    handle.setYRot(loc.getYaw());
                    handle.setXRot(loc.getPitch());
                }
                EntityVelocityResponse vel = NativeBridgeFfi.getEntityVelocity(protoUuid);
                if (vel != null) {
                    handle.setDeltaMovement(vel.getX(), vel.getY(), vel.getZ());
                }
            } catch (Throwable ignored) {}
        }
    }

    public static boolean isOnGroundFromPumpkin(CraftEntity craftEntity, boolean fallback) {
        if (craftEntity == null || !NativeFastPathFfi.isInitialized()) return fallback;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return fallback;
        try {
            IsOnGroundResponse res = NativeBridgeFfi.isOnGround(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null) {
                return res.getOnGround();
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    public static void pushPoseToPumpkin(CraftEntity craftEntity, Pose pose) {
        if (craftEntity == null || pose == null || !NativeFastPathFfi.isInitialized()) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        try {
            NativeBridgeFfi.setEntityPose(
                SetEntityPoseRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setPose(pose.name())
                    .build()
            );
        } catch (Throwable ignored) {}
    }

    public static int getEntityIdFromPumpkin(CraftEntity craftEntity, int fallback) {
        if (craftEntity == null || !NativeFastPathFfi.isInitialized()) return fallback;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return fallback;
        try {
            GetEntityIdResponse res = NativeBridgeFfi.getEntityId(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null && res.getEntityId() != 0) {
                return res.getEntityId();
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    public static void syncLivingHealthFromPumpkin(CraftLivingEntity living) {
        if (living == null || living instanceof CraftPlayer || !NativeFastPathFfi.isInitialized()) return;
        try {
            EntityHealthResponse res = NativeBridgeFfi.getEntityHealth(BridgeUtils.convertUuid(living.getUniqueId()));
            if (res != null && res.getMaxHealth() > 0) {
                living.getHandle().setHealth((float) res.getHealth());
            }
        } catch (Throwable ignored) {}
    }

    public static void pushLivingHealthToPumpkin(CraftLivingEntity living, double health) {
        if (living == null || living instanceof CraftPlayer || !NativeFastPathFfi.isInitialized()) return;
        try {
            NativeBridgeFfi.setEntityHealth(
                SetEntityHealthRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(living.getUniqueId()))
                    .setHealth(health)
                    .build()
            );
        } catch (Throwable ignored) {}
    }

    public static void pushLivingDamageToPumpkin(CraftLivingEntity living, double amount) {
        if (living == null || living instanceof CraftPlayer || !NativeFastPathFfi.isInitialized()) return;
        try {
            NativeBridgeFfi.damageEntity(
                DamageEntityRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(living.getUniqueId()))
                    .setAmount(amount)
                    .build()
            );
        } catch (Throwable ignored) {}
    }

    public static final class EntityLookupInterceptor {
        @RuntimeType
        public static Visibility getEntityStatus(@Argument(0) Entity entity) {
            return Visibility.TICKING;
        }
    }

    public static final class OnTrackingStartAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) Entity entity) {
            EntityBridge.handleEntityAdded(entity);
        }
    }

    public static final class OnTrackingEndAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) Entity entity) {
            EntityBridge.handleEntityRemoved(entity);
        }
    }

    public static final class OnTeleportAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntity craftEntity,
            @Advice.Argument(0) Location location,
            @Advice.Return boolean success
        ) {
            if (success) {
                EntityBridge.handleEntityTeleported(craftEntity, location);
            }
        }
    }

    public static final class OnSetVelocityAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntity craftEntity,
            @Advice.Argument(0) Vector velocity
        ) {
            EntityBridge.handleEntityVelocitySet(craftEntity, velocity);
        }
    }

    public static final class OnSyncStateAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftEntity craftEntity) {
            EntityBridge.syncEntityStateFromPumpkin(craftEntity);
        }
    }

    public static final class OnIsOnGroundAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity craftEntity, @Advice.Return(readOnly = false) boolean onGround) {
            onGround = EntityBridge.isOnGroundFromPumpkin(craftEntity, onGround);
        }
    }

    public static final class OnSetPoseAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity craftEntity, @Advice.Argument(0) Pose pose) {
            EntityBridge.pushPoseToPumpkin(craftEntity, pose);
        }
    }

    public static final class OnGetEntityIdAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity craftEntity, @Advice.Return(readOnly = false) int id) {
            id = EntityBridge.getEntityIdFromPumpkin(craftEntity, id);
        }
    }

    public static final class OnLivingGetHealthAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftLivingEntity living) {
            EntityBridge.syncLivingHealthFromPumpkin(living);
        }
    }

    public static final class OnLivingSetHealthAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) double health) {
            EntityBridge.pushLivingHealthToPumpkin(living, health);
        }
    }

    public static final class OnLivingDamageAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) double amount) {
            EntityBridge.pushLivingDamageToPumpkin(living, amount);
        }
    }

    public static final class OnLivingDamageItemStackAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living) {
            if (living instanceof org.bukkit.craftbukkit.entity.CraftHumanEntity human
                    && human.getHandle() != null) {
                InventoryBridge.pushAllToPumpkin(human.getHandle().getInventory());
            }
        }
    }
}
