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
import org.bukkit.util.Vector;
import org.patchbukkit.bridge.BridgeUtils;
import patchbukkit.bridge.NativeBridgeFfi;
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

            // 3. Intercept CraftEntity mutations (teleport and velocity)
            try {
                byteBuddy
                    .redefine(CraftEntity.class)
                    .visit(Advice.to(OnTeleportAdvice.class).on(ElementMatchers.named("teleport0")))
                    .visit(Advice.to(OnSetVelocityAdvice.class).on(ElementMatchers.named("setVelocity").and(ElementMatchers.takesArguments(Vector.class))))
                    .make()
                    .load(CraftEntity.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftEntity mutations", t);
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
                    SpawnWorldEntityRequest req = SpawnWorldEntityRequest.newBuilder()
                        .setWorldUuid(BridgeUtils.convertUuid(worldUuid))
                        .setEntityType(typeName)
                        .setX(entity.getX())
                        .setY(entity.getY())
                        .setZ(entity.getZ())
                        .setYaw(entity.getYRot())
                        .setPitch(entity.getXRot())
                        .setEntityUuid(BridgeUtils.convertUuid(uuid))
                        .setEntityId(entity.getId())
                        .build();
                    SpawnWorldEntityResponse resp = NativeBridgeFfi.spawnWorldEntity(req);
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
        if (handle == null || handle instanceof ServerPlayer) return;
        try {
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
        if (handle == null || handle instanceof ServerPlayer) return;
        try {
            SetEntityVelocityRequest req = SetEntityVelocityRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                .setX(velocity.getX())
                .setY(velocity.getY())
                .setZ(velocity.getZ())
                .build();
            NativeBridgeFfi.setEntityVelocity(req);
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
}
