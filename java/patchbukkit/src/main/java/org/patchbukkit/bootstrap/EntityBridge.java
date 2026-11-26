package org.patchbukkit.bootstrap;

import ca.spottedleaf.moonrise.patches.chunk_system.level.entity.EntityLookup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.Argument;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.matcher.ElementMatchers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.entity.Visibility;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.attribute.CraftAttributeInstance;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.AddPotionEffectRequest;
import patchbukkit.entity.AttributeModifierProto;
import patchbukkit.entity.DamageEntityRequest;
import patchbukkit.entity.EntityHealthResponse;
import patchbukkit.entity.EntityInfoResponse;
import patchbukkit.entity.EntityMetadataResponse;
import patchbukkit.entity.EntityPassengersResponse;
import patchbukkit.entity.EntityVelocityResponse;
import patchbukkit.entity.GetEntityAttributeRequest;
import patchbukkit.entity.GetEntityAttributeResponse;
import patchbukkit.entity.GetEntityIdResponse;
import patchbukkit.entity.GetEntityInfoRequest;
import patchbukkit.entity.GetEntityUuidRequest;
import patchbukkit.entity.GetEntityUuidResponse;
import patchbukkit.entity.GetPotionEffectsResponse;
import patchbukkit.entity.IsOnGroundResponse;
import patchbukkit.entity.ModifyEntityAttributeModifierRequest;
import patchbukkit.entity.ModifyEntityPassengerRequest;
import patchbukkit.entity.ModifyScoreboardTagRequest;
import patchbukkit.entity.PotionEffectProto;
import patchbukkit.entity.RemovePotionEffectRequest;
import patchbukkit.entity.SetEntityAbsorptionRequest;
import patchbukkit.entity.SetEntityAttributeBaseRequest;
import patchbukkit.entity.SetEntityHealthRequest;
import patchbukkit.entity.SetEntityMetadataRequest;
import patchbukkit.entity.SetEntityPoseRequest;
import patchbukkit.entity.SetEntityVelocityRequest;
import patchbukkit.entity.SwingHandRequest;
import patchbukkit.entity.TeleportEntityRequest;
import patchbukkit.world.EntitySummaryProto;
import patchbukkit.world.GetWorldEntitiesRequest;
import patchbukkit.world.GetWorldEntitiesResponse;
import patchbukkit.world.SpawnWorldEntityRequest;
import patchbukkit.world.SpawnWorldEntityResponse;

/**
 * Bridges Paper's NMS entity tracking, lifecycle, metadata, living health/damage/effects/attributes,
 * and passenger relationships between Bukkit/NMS and Pumpkin via FFI.
 */
public final class EntityBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final Set<UUID> MIRRORED_FROM_PUMPKIN = ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);
    private static final Map<org.bukkit.attribute.AttributeInstance, UUID> ATTRIBUTE_OWNERS =
        Collections.synchronizedMap(new WeakHashMap<>());
    private static final java.lang.reflect.Method INTERNAL_SET_ABSORPTION_METHOD = resolveInternalSetAbsorptionMethod();

    private static java.lang.reflect.Method resolveInternalSetAbsorptionMethod() {
        try {
            java.lang.reflect.Method m = LivingEntity.class.getDeclaredMethod("internalSetAbsorptionAmount", float.class);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void setAbsorptionBypassClamp(LivingEntity handle, float amount) {
        if (handle == null) return;
        float safe = Float.isFinite(amount) && amount >= 0.0f ? amount : 0.0f;
        if (INTERNAL_SET_ABSORPTION_METHOD != null) {
            try {
                INTERNAL_SET_ABSORPTION_METHOD.invoke(handle, safe);
                return;
            } catch (Throwable ignored) {}
        }
        handle.setAbsorptionAmount(safe);
    }

    private EntityBridge() {}

    public static void patch() {
        try {
            // 1. Intercept EntityLookup.getEntityStatus
            BridgePatcher.register(EntityLookup.class, "EntityBridge", builder -> builder
                .method(ElementMatchers.named("getEntityStatus").and(ElementMatchers.takesArguments(Entity.class)))
                .intercept(MethodDelegation.to(EntityLookupInterceptor.class))
            );

            // 2. Intercept ServerLevel$EntityCallbacks for tracking start/end
            BridgePatcher.registerByName(
                "net.minecraft.server.level.ServerLevel$EntityCallbacks",
                "EntityBridge",
                builder -> builder
                    .visit(Advice.to(OnTrackingStartAdvice.class).on(
                        ElementMatchers.named("onTrackingStart").and(ElementMatchers.takesArguments(Entity.class))
                    ))
                    .visit(Advice.to(OnTrackingEndAdvice.class).on(
                        ElementMatchers.named("onTrackingEnd").and(ElementMatchers.takesArguments(Entity.class))
                    ))
            );

            // 3. Intercept CraftEntity state, metadata, scoreboard tags, rotation, and passengers
            BridgePatcher.register(CraftEntity.class, "EntityBridge", builder -> builder
                .visit(Advice.to(OnTeleportAdvice.class).on(ElementMatchers.named("teleport0")))
                .visit(Advice.to(OnSetVelocityAdvice.class).on(
                    ElementMatchers.named("setVelocity").and(ElementMatchers.takesArguments(Vector.class))
                ))
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
                .visit(Advice.to(OnSetRotationAdvice.class).on(
                    ElementMatchers.named("setRotation").and(ElementMatchers.takesArguments(float.class, float.class))
                ))
                .visit(Advice.to(OnSyncMetadataAdvice.class).on(
                    ElementMatchers.named("customName").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("getCustomName").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isCustomNameVisible").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getFireTicks").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isVisualFire").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getVisualFire").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getFreezeTicks").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isGlowing").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isInvisible").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isSilent").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("hasGravity").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isMarkedInvulnerable").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getFallDistance").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getScoreboardTags").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnSetCustomNameComponentAdvice.class).on(
                    ElementMatchers.named("customName").and(ElementMatchers.takesArguments(Component.class))
                ))
                .visit(Advice.to(OnSetCustomNameStringAdvice.class).on(
                    ElementMatchers.named("setCustomName").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnSetCustomNameVisibleAdvice.class).on(
                    ElementMatchers.named("setCustomNameVisible").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetFireTicksAdvice.class).on(
                    ElementMatchers.named("setFireTicks").and(ElementMatchers.takesArguments(int.class))
                ))
                .visit(Advice.to(OnSetVisualFireAdvice.class).on(
                    ElementMatchers.named("setVisualFire").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetFreezeTicksAdvice.class).on(
                    ElementMatchers.named("setFreezeTicks").and(ElementMatchers.takesArguments(int.class))
                ))
                .visit(Advice.to(OnSetGlowingAdvice.class).on(
                    ElementMatchers.named("setGlowing").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetInvisibleAdvice.class).on(
                    ElementMatchers.named("setInvisible").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetSilentAdvice.class).on(
                    ElementMatchers.named("setSilent").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetGravityAdvice.class).on(
                    ElementMatchers.named("setGravity").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetInvulnerableAdvice.class).on(
                    ElementMatchers.named("setInvulnerable").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetFallDistanceAdvice.class).on(
                    ElementMatchers.named("setFallDistance").and(ElementMatchers.takesArguments(float.class))
                ))
                .visit(Advice.to(OnAddScoreboardTagAdvice.class).on(
                    ElementMatchers.named("addScoreboardTag").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnRemoveScoreboardTagAdvice.class).on(
                    ElementMatchers.named("removeScoreboardTag").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnGetPassengersAdvice.class).on(
                    ElementMatchers.named("getPassengers").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetVehicleAdvice.class).on(
                    ElementMatchers.named("getVehicle").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnAddPassengerAdvice.class).on(
                    ElementMatchers.named("addPassenger").and(ElementMatchers.takesArguments(org.bukkit.entity.Entity.class))
                ))
                .visit(Advice.to(OnRemovePassengerAdvice.class).on(
                    ElementMatchers.named("removePassenger").and(ElementMatchers.takesArguments(org.bukkit.entity.Entity.class))
                ))
                .visit(Advice.to(OnEjectAdvice.class).on(
                    ElementMatchers.named("eject").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnLeaveVehicleAdvice.class).on(
                    ElementMatchers.named("leaveVehicle").and(ElementMatchers.takesNoArguments())
                ))
            );

            // 4. Intercept CraftLivingEntity (sole owner for BOTH players and non-player living entities)
            BridgePatcher.register(CraftLivingEntity.class, "EntityBridge", builder -> builder
                .visit(Advice.to(OnLivingGetHealthAdvice.class).on(
                    ElementMatchers.named("getHealth").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("getMaxHealth").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getAbsorptionAmount").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnLivingSetHealthAdvice.class).on(
                    ElementMatchers.named("setHealth").and(ElementMatchers.takesArguments(double.class))
                ))
                .visit(Advice.to(OnLivingSetMaxHealthAdvice.class).on(
                    ElementMatchers.named("setMaxHealth").and(ElementMatchers.takesArguments(double.class))
                        .or(ElementMatchers.named("resetMaxHealth").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnLivingSetAbsorptionAdvice.class).on(
                    ElementMatchers.named("setAbsorptionAmount").and(ElementMatchers.takesArguments(double.class))
                ))
                // Hook ONLY canonical private damage(double, net.minecraft.world.damagesource.DamageSource) to avoid double damage
                .visit(Advice.to(OnLivingDamageAdvice.class).on(
                    ElementMatchers.named("damage").and(ElementMatchers.takesArguments(double.class, DamageSource.class))
                ))
                .visit(Advice.to(OnLivingGetAirAdvice.class).on(
                    ElementMatchers.named("getRemainingAir").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnLivingSetAirAdvice.class).on(
                    ElementMatchers.named("setRemainingAir").and(ElementMatchers.takesArguments(int.class))
                ))
                .visit(Advice.to(OnLivingSyncPoseAdvice.class).on(
                    ElementMatchers.named("isSwimming").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("isGliding").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnLivingSetSwimmingAdvice.class).on(
                    ElementMatchers.named("setSwimming").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnLivingSetGlidingAdvice.class).on(
                    ElementMatchers.named("setGliding").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSwingMainHandAdvice.class).on(
                    ElementMatchers.named("swingMainHand").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSwingOffHandAdvice.class).on(
                    ElementMatchers.named("swingOffHand").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnLivingSyncPotionEffectsAdvice.class).on(
                    ElementMatchers.named("getActivePotionEffects").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("hasPotionEffect").and(ElementMatchers.takesArguments(PotionEffectType.class)))
                        .or(ElementMatchers.named("getPotionEffect").and(ElementMatchers.takesArguments(PotionEffectType.class)))
                ))
                .visit(Advice.to(OnLivingAddPotionEffectAdvice.class).on(
                    ElementMatchers.named("addPotionEffect").and(ElementMatchers.takesArguments(PotionEffect.class))
                ))
                .visit(Advice.to(OnLivingRemovePotionEffectAdvice.class).on(
                    ElementMatchers.named("removePotionEffect").and(ElementMatchers.takesArguments(PotionEffectType.class))
                ))
                .visit(Advice.to(OnLivingClearPotionEffectsAdvice.class).on(
                    ElementMatchers.named("clearActivePotionEffects").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnLivingGetAttributeAdvice.class).on(
                    ElementMatchers.named("getAttribute").and(ElementMatchers.takesArguments(Attribute.class))
                ))
                .visit(Advice.to(OnLivingDamageItemStackAdvice.class).on(
                    ElementMatchers.named("damageItemStack")
                ))
            );

            // 5. Intercept CraftAttributeInstance reads & writes
            BridgePatcher.register(CraftAttributeInstance.class, "EntityBridge", builder -> builder
                .visit(Advice.to(OnSyncAttributeAdvice.class).on(
                    ElementMatchers.named("getBaseValue").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("getValue").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getModifiers").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnSetAttributeBaseAdvice.class).on(
                    ElementMatchers.named("setBaseValue").and(ElementMatchers.takesArguments(double.class))
                ))
                .visit(Advice.to(OnAddAttributeModifierAdvice.class).on(
                    ElementMatchers.named("addModifier").and(ElementMatchers.takesArguments(AttributeModifier.class))
                        .or(ElementMatchers.named("addTransientModifier").and(ElementMatchers.takesArguments(AttributeModifier.class)))
                ))
                .visit(Advice.to(OnRemoveAttributeModifierAdvice.class).on(
                    ElementMatchers.named("removeModifier").and(ElementMatchers.takesArguments(AttributeModifier.class))
                ))
                .visit(Advice.to(OnRemoveAttributeModifierKeyAdvice.class).on(
                    ElementMatchers.named("removeModifier").and(ElementMatchers.takesArguments(Key.class))
                ))
                .visit(Advice.to(OnRemoveAttributeModifierUuidAdvice.class).on(
                    ElementMatchers.named("removeModifier").and(ElementMatchers.takesArguments(UUID.class))
                ))
            );

            LOGGER.info("[PatchBukkit] Registered EntityBridge hooks with BridgePatcher");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register EntityBridge hooks", t);
        }
    }

    // ---- Entity mirroring & lookup helpers ----

    public static ServerLevel findServerLevelByUuid(UUID worldUuid) {
        MinecraftServer server = HeadlessPaperServer.get();
        if (server == null) {
            server = MinecraftServer.getServer();
        }
        if (server == null) return null;
        if (worldUuid != null) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getWorld() != null && worldUuid.equals(level.getWorld().getUID())) {
                    return level;
                }
            }
            World bukkitWorld = Bukkit.getWorld(worldUuid);
            if (bukkitWorld instanceof CraftWorld cw) {
                return cw.getHandle();
            }
        }
        return server.overworld();
    }

    public static EntityType<?> resolveEntityType(String rawType) {
        if (rawType == null || rawType.isEmpty()) {
            return net.minecraft.world.entity.EntityTypes.PIG;
        }
        String norm = rawType.toLowerCase(Locale.ROOT).trim();
        if (norm.startsWith("minecraft:")) {
            norm = norm.substring("minecraft:".length());
        }
        norm = switch (norm) {
            case "dropped_item" -> "item";
            case "minecart_chest" -> "chest_minecart";
            case "minecart_furnace" -> "furnace_minecart";
            case "minecart_tnt" -> "tnt_minecart";
            case "minecart_hopper" -> "hopper_minecart";
            case "minecart_mob_spawner" -> "spawner_minecart";
            case "minecart_command" -> "command_block_minecart";
            case "primed_tnt" -> "tnt";
            case "snowman" -> "snow_golem";
            case "mushroom_cow" -> "mooshroom";
            case "ender_crystal" -> "end_crystal";
            case "lightning" -> "lightning_bolt";
            case "thrown_exp_bottle" -> "experience_bottle";
            case "splash_potion" -> "potion";
            case "fishing_hook" -> "fishing_bobber";
            default -> norm;
        };
        try {
            Identifier key = Identifier.fromNamespaceAndPath("minecraft", norm);
            if (BuiltInRegistries.ENTITY_TYPE.containsKey(key)) {
                return BuiltInRegistries.ENTITY_TYPE.getValue(key);
            }
        } catch (Throwable ignored) {}
        return net.minecraft.world.entity.EntityTypes.PIG;
    }

    public static org.bukkit.entity.Entity resolveOrMirrorEntity(
        UUID entityUuid,
        int entityId,
        String entityTypeStr,
        UUID worldUuid,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String customName,
        boolean isPlayer
    ) {
        if (entityUuid == null) return null;
        Player online = Bukkit.getPlayer(entityUuid);
        if (online != null) {
            return online;
        }
        MinecraftServer server = HeadlessPaperServer.get();
        if (server == null) server = MinecraftServer.getServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity existing = level.getEntity(entityUuid);
                if (existing != null) {
                    if (entityId != 0 && existing.getId() != entityId) {
                        existing.setId(entityId);
                    }
                    return existing.getBukkitEntity();
                }
            }
        }
        if (isPlayer) {
            return null;
        }
        ServerLevel targetLevel = findServerLevelByUuid(worldUuid);
        if (targetLevel == null) return null;

        EntityType<?> entityType = resolveEntityType(entityTypeStr);
        Entity entity = entityType.create(targetLevel, EntitySpawnReason.LOAD);
        if (entity == null) return null;
        if (entityId != 0) {
            entity.setId(entityId);
        }
        entity.setUUID(entityUuid);
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.setXRot(pitch);
        if (customName != null && !customName.isEmpty()) {
            entity.setCustomName(io.papermc.paper.adventure.PaperAdventure.asVanilla(
                LegacyComponentSerializer.legacySection().deserialize(customName)
            ));
        }
        MIRRORED_FROM_PUMPKIN.add(entityUuid);
        targetLevel.addWithUUID(entity);
        return entity.getBukkitEntity();
    }

    public static org.bukkit.entity.Entity getOrMirrorEntityByUuid(UUID uuid) {
        if (uuid == null) return null;
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online;

        MinecraftServer server = HeadlessPaperServer.get();
        if (server == null) server = MinecraftServer.getServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity existing = level.getEntity(uuid);
                if (existing != null) {
                    return existing.getBukkitEntity();
                }
            }
        }
        if (!NativeFastPathFfi.isInitialized()) return null;
        try {
            EntityInfoResponse info = NativeBridgeFfi.getEntityInfo(
                GetEntityInfoRequest.newBuilder().setUuid(BridgeUtils.convertUuid(uuid)).build()
            );
            if (info != null && info.getFound() && info.hasUuid()) {
                return resolveOrMirrorEntity(
                    BridgeUtils.convertUuid(info.getUuid()),
                    info.getEntityId(),
                    info.getEntityType(),
                    info.hasWorldUuid() ? BridgeUtils.convertUuid(info.getWorldUuid()) : null,
                    info.getX(),
                    info.getY(),
                    info.getZ(),
                    info.getYaw(),
                    info.getPitch(),
                    info.getCustomName(),
                    info.getIsPlayer()
                );
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static org.bukkit.entity.Entity getOrMirrorEntityById(int entityId) {
        if (entityId == 0) return null;
        MinecraftServer server = HeadlessPaperServer.get();
        if (server == null) server = MinecraftServer.getServer();
        if (server != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Entity existing = level.getEntity(entityId);
                if (existing != null) {
                    return existing.getBukkitEntity();
                }
            }
        }
        if (!NativeFastPathFfi.isInitialized()) return null;
        try {
            GetEntityUuidResponse res = NativeBridgeFfi.getEntityUuid(
                GetEntityUuidRequest.newBuilder().setEntityId(entityId).build()
            );
            if (res != null && res.hasUuid()) {
                UUID entityUuid = BridgeUtils.convertUuid(res.getUuid());
                if (entityUuid != null) {
                    return resolveOrMirrorEntity(
                        entityUuid,
                        entityId,
                        res.getEntityType(),
                        res.hasWorldUuid() ? BridgeUtils.convertUuid(res.getWorldUuid()) : null,
                        res.getX(),
                        res.getY(),
                        res.getZ(),
                        res.getYaw(),
                        res.getPitch(),
                        res.getCustomName(),
                        res.getIsPlayer()
                    );
                }
            }
        } catch (Throwable ignored) {}
        return null;
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
                    resolveOrMirrorEntity(
                        entityUuid,
                        proto.getEntityId(),
                        proto.getEntityType(),
                        worldUuid,
                        proto.getX(),
                        proto.getY(),
                        proto.getZ(),
                        proto.getYaw(),
                        proto.getPitch(),
                        proto.getCustomName(),
                        false
                    );
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void handleEntityAdded(Entity entity) {
        if (entity == null || entity instanceof ServerPlayer || entity instanceof LightningBolt) return;
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
        if (entity == null || entity instanceof ServerPlayer || entity instanceof LightningBolt) return;
        try {
            NativeBridgeFfi.removeEntity(BridgeUtils.convertUuid(entity.getUUID()));
        } catch (Throwable ignored) {}
    }

    public static void handleEntityTeleported(CraftEntity craftEntity, Location location) {
        if (craftEntity == null || location == null || SYNCING.get()) return;
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
        if (craftEntity == null || velocity == null || SYNCING.get()) return;
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
        if (craftEntity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            if (!NativeFastPathFfi.syncNmsEntityState(handle)) {
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
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static boolean isOnGroundFromPumpkin(CraftEntity craftEntity, boolean fallback) {
        if (craftEntity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
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
        if (craftEntity == null || pose == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
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
        if (craftEntity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
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

    // ---- Entity Metadata & Scoreboard Tags ----

    public static void syncEntityMetadataFromPumpkin(CraftEntity craftEntity) {
        if (craftEntity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            EntityMetadataResponse res = NativeBridgeFfi.getEntityMetadata(BridgeUtils.convertUuid(handle.getUUID()));
            if (res == null) return;
            if (!res.getCustomName().isEmpty()) {
                handle.setCustomName(io.papermc.paper.adventure.PaperAdventure.asVanilla(
                    LegacyComponentSerializer.legacySection().deserialize(res.getCustomName())
                ));
            } else {
                handle.setCustomName(null);
            }
            handle.setCustomNameVisible(res.getCustomNameVisible());
            handle.setRemainingFireTicks(res.getFireTicks());
            handle.visualFire = res.getVisualFire()
                ? net.kyori.adventure.util.TriState.TRUE
                : net.kyori.adventure.util.TriState.NOT_SET;
            handle.setTicksFrozen(res.getFreezeTicks());
            handle.setAirSupply(res.getAirSupply());
            handle.setGlowingTag(res.getGlowing());
            handle.setInvisible(res.getInvisible());
            handle.setSilent(res.getSilent());
            handle.setNoGravity(res.getNoGravity());
            handle.setPermanentlyInvulnerable(res.getInvulnerable());
            handle.fallDistance = res.getFallDistance();
            if (handle instanceof LivingEntity living) {
                living.setSwimming(res.getSwimming());
                living.setSharedFlag(7, res.getGliding());
            }
            Set<String> tags = handle.entityTags();
            tags.clear();
            tags.addAll(res.getScoreboardTagsList());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    private static void pushMetadataPatch(CraftEntity craftEntity, Consumer<SetEntityMetadataRequest.Builder> patcher) {
        if (craftEntity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            SetEntityMetadataRequest.Builder b = SetEntityMetadataRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(handle.getUUID()));
            patcher.accept(b);
            NativeBridgeFfi.setEntityMetadata(b.build());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushRotationToPumpkin(CraftEntity craftEntity, float yaw, float pitch) {
        pushMetadataPatch(craftEntity, b -> b.setYaw(yaw).setPitch(pitch));
    }

    public static void pushCustomNameComponentToPumpkin(CraftEntity craftEntity, Component name) {
        if (name == null) {
            pushMetadataPatch(craftEntity, b -> b.setClearCustomName(true));
        } else {
            String legacy = LegacyComponentSerializer.legacySection().serialize(name);
            pushMetadataPatch(craftEntity, b -> b.setCustomName(legacy));
        }
    }

    public static void pushCustomNameStringToPumpkin(CraftEntity craftEntity, String name) {
        if (name == null) {
            pushMetadataPatch(craftEntity, b -> b.setClearCustomName(true));
        } else {
            pushMetadataPatch(craftEntity, b -> b.setCustomName(name));
        }
    }

    public static void pushCustomNameVisibleToPumpkin(CraftEntity craftEntity, boolean visible) {
        pushMetadataPatch(craftEntity, b -> b.setCustomNameVisible(visible));
    }

    public static void pushFireTicksToPumpkin(CraftEntity craftEntity, int ticks) {
        pushMetadataPatch(craftEntity, b -> b.setFireTicks(ticks));
    }

    public static void pushVisualFireToPumpkin(CraftEntity craftEntity, boolean visualFire) {
        pushMetadataPatch(craftEntity, b -> b.setVisualFire(visualFire));
    }

    public static void pushFreezeTicksToPumpkin(CraftEntity craftEntity, int ticks) {
        pushMetadataPatch(craftEntity, b -> b.setFreezeTicks(ticks));
    }

    public static void pushAirSupplyToPumpkin(CraftEntity craftEntity, int air) {
        pushMetadataPatch(craftEntity, b -> b.setAirSupply(air));
    }

    public static void pushGlowingToPumpkin(CraftEntity craftEntity, boolean glowing) {
        pushMetadataPatch(craftEntity, b -> b.setGlowing(glowing));
    }

    public static void pushInvisibleToPumpkin(CraftEntity craftEntity, boolean invisible) {
        pushMetadataPatch(craftEntity, b -> b.setInvisible(invisible));
    }

    public static void pushSilentToPumpkin(CraftEntity craftEntity, boolean silent) {
        pushMetadataPatch(craftEntity, b -> b.setSilent(silent));
    }

    public static void pushGravityToPumpkin(CraftEntity craftEntity, boolean gravity) {
        pushMetadataPatch(craftEntity, b -> b.setNoGravity(!gravity));
    }

    public static void pushInvulnerableToPumpkin(CraftEntity craftEntity, boolean invulnerable) {
        pushMetadataPatch(craftEntity, b -> b.setInvulnerable(invulnerable));
    }

    public static void pushFallDistanceToPumpkin(CraftEntity craftEntity, float fallDistance) {
        pushMetadataPatch(craftEntity, b -> b.setFallDistance(fallDistance));
    }

    public static void pushSwimmingToPumpkin(CraftEntity craftEntity, boolean swimming) {
        pushMetadataPatch(craftEntity, b -> b.setSwimming(swimming));
    }

    public static void pushGlidingToPumpkin(CraftEntity craftEntity, boolean gliding) {
        pushMetadataPatch(craftEntity, b -> b.setGliding(gliding));
    }

    public static void pushScoreboardTagToPumpkin(CraftEntity craftEntity, String tag, boolean add) {
        if (craftEntity == null || tag == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Entity handle = craftEntity.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.modifyScoreboardTag(
                ModifyScoreboardTagRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setTag(tag)
                    .setAdd(add)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Passengers & Vehicles ----

    public static List<org.bukkit.entity.Entity> getPassengersFromPumpkin(
        CraftEntity vehicle,
        List<org.bukkit.entity.Entity> fallback
    ) {
        if (vehicle == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        Entity handle = vehicle.getHandle();
        if (handle == null) return fallback;
        SYNCING.set(true);
        try {
            EntityPassengersResponse res = NativeBridgeFfi.getEntityPassengers(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null) {
                List<org.bukkit.entity.Entity> list = new ArrayList<>(res.getPassengerUuidsCount());
                for (patchbukkit.common.UUID pUuid : res.getPassengerUuidsList()) {
                    UUID u = BridgeUtils.convertUuid(pUuid);
                    org.bukkit.entity.Entity resolved = getOrMirrorEntityByUuid(u);
                    if (resolved != null) {
                        list.add(resolved);
                    }
                }
                return Collections.unmodifiableList(list);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static org.bukkit.entity.Entity getVehicleFromPumpkin(
        CraftEntity passenger,
        org.bukkit.entity.Entity fallback
    ) {
        if (passenger == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        Entity handle = passenger.getHandle();
        if (handle == null) return fallback;
        SYNCING.set(true);
        try {
            EntityPassengersResponse res = NativeBridgeFfi.getEntityPassengers(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null) {
                if (res.hasVehicleUuid()) {
                    return getOrMirrorEntityByUuid(BridgeUtils.convertUuid(res.getVehicleUuid()));
                }
                return null;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushPassengerToPumpkin(CraftEntity vehicle, org.bukkit.entity.Entity passenger, boolean add) {
        if (vehicle == null || passenger == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.modifyEntityPassenger(
                ModifyEntityPassengerRequest.newBuilder()
                    .setVehicleUuid(BridgeUtils.convertUuid(vehicle.getUniqueId()))
                    .setPassengerUuid(BridgeUtils.convertUuid(passenger.getUniqueId()))
                    .setAdd(add)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushEjectAllPassengersToPumpkin(CraftEntity vehicle) {
        if (vehicle == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            patchbukkit.common.UUID vUuid = BridgeUtils.convertUuid(vehicle.getUniqueId());
            EntityPassengersResponse res = NativeBridgeFfi.getEntityPassengers(vUuid);
            if (res != null) {
                for (patchbukkit.common.UUID pUuid : res.getPassengerUuidsList()) {
                    NativeBridgeFfi.modifyEntityPassenger(
                        ModifyEntityPassengerRequest.newBuilder()
                            .setVehicleUuid(vUuid)
                            .setPassengerUuid(pUuid)
                            .setAdd(false)
                            .build()
                    );
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushLeaveVehicleToPumpkin(CraftEntity passenger) {
        if (passenger == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            patchbukkit.common.UUID pUuid = BridgeUtils.convertUuid(passenger.getUniqueId());
            EntityPassengersResponse res = NativeBridgeFfi.getEntityPassengers(pUuid);
            if (res != null && res.hasVehicleUuid()) {
                NativeBridgeFfi.modifyEntityPassenger(
                    ModifyEntityPassengerRequest.newBuilder()
                        .setVehicleUuid(res.getVehicleUuid())
                        .setPassengerUuid(pUuid)
                        .setAdd(false)
                        .build()
                );
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- LivingEntity Health, MaxHealth, Absorption, Damage, Hand Swing ----

    public static void syncLivingHealthFromPumpkin(CraftLivingEntity living) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            EntityHealthResponse res = NativeBridgeFfi.getEntityHealth(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null) {
                if (res.getMaxHealth() > 0.0) {
                    AttributeInstance maxHpAttr = handle.getAttribute(Attributes.MAX_HEALTH);
                    if (maxHpAttr != null && Math.abs(maxHpAttr.getBaseValue() - res.getMaxHealth()) > 1e-4) {
                        maxHpAttr.setBaseValue(res.getMaxHealth());
                    }
                }
                if (res.getHealth() >= 0.0) {
                    handle.setHealth((float) res.getHealth());
                }
                setAbsorptionBypassClamp(handle, (float) Math.max(0.0, res.getAbsorption()));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushLivingHealthToPumpkin(CraftLivingEntity living, double health) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setEntityHealth(
                SetEntityHealthRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setHealth(health)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushMaxHealthToPumpkin(CraftLivingEntity living) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            double maxHealth = handle.getMaxHealth();
            NativeBridgeFfi.setEntityAttributeBase(
                SetEntityAttributeBaseRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setAttribute("minecraft:max_health")
                    .setBaseValue(maxHealth)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAbsorptionToPumpkin(CraftLivingEntity living, double absorption) {
        if (living == null || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        setAbsorptionBypassClamp(handle, (float) Math.max(0.0, absorption));
        if (!NativeFastPathFfi.isInitialized()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setEntityAbsorption(
                SetEntityAbsorptionRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setAbsorption(absorption)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushLivingDamageToPumpkin(CraftLivingEntity living, double amount, DamageSource source) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            DamageEntityRequest.Builder req = DamageEntityRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                .setAmount(amount);
            if (source != null) {
                if (source.getEntity() != null) {
                    req.setDamagerUuid(BridgeUtils.convertUuid(source.getEntity().getUUID()));
                }
                if (source.getMsgId() != null && !source.getMsgId().isEmpty()) {
                    req.setDamageType(source.getMsgId());
                }
            }
            NativeBridgeFfi.damageEntity(req.build());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSwingHandToPumpkin(CraftLivingEntity living, boolean offHand) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.swingEntityHand(
                SwingHandRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setOffHand(offHand)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Potion Effects ----

    public static void syncPotionEffectsFromPumpkin(CraftLivingEntity living) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            GetPotionEffectsResponse res = NativeBridgeFfi.getPotionEffects(BridgeUtils.convertUuid(handle.getUUID()));
            if (res == null) return;
            handle.removeAllEffects();
            for (PotionEffectProto proto : res.getEffectsList()) {
                String keyStr = proto.getEffectType().contains(":")
                    ? proto.getEffectType()
                    : "minecraft:" + proto.getEffectType();
                var holderOpt = BuiltInRegistries.MOB_EFFECT.get(Identifier.parse(keyStr));
                if (holderOpt.isPresent()) {
                    Holder<MobEffect> holder = holderOpt.get();
                    handle.addEffect(new MobEffectInstance(
                        holder,
                        proto.getDuration(),
                        proto.getAmplifier(),
                        proto.getAmbient(),
                        proto.getShowParticles(),
                        proto.getShowIcon()
                    ));
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAddPotionEffectToPumpkin(CraftLivingEntity living, PotionEffect effect, boolean added) {
        if (!added || living == null || effect == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.addPotionEffect(
                AddPotionEffectRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setEffect(
                        PotionEffectProto.newBuilder()
                            .setEffectType(effect.getType().getKey().asString())
                            .setDuration(effect.getDuration())
                            .setAmplifier(effect.getAmplifier())
                            .setAmbient(effect.isAmbient())
                            .setShowParticles(effect.hasParticles())
                            .setShowIcon(effect.hasIcon())
                            .build()
                    )
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushRemovePotionEffectToPumpkin(CraftLivingEntity living, PotionEffectType type) {
        if (living == null || type == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.removePotionEffect(
                RemovePotionEffectRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setEffectType(type.getKey().asString())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushClearPotionEffectsToPumpkin(CraftLivingEntity living) {
        if (living == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = living.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.clearPotionEffects(BridgeUtils.convertUuid(handle.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Attributes & Modifiers ----

    public static void registerAttributeOwner(CraftLivingEntity living, org.bukkit.attribute.AttributeInstance inst) {
        if (living == null || inst == null) return;
        ATTRIBUTE_OWNERS.put(inst, living.getUniqueId());
        if (inst instanceof CraftAttributeInstance craftInst) {
            syncAttributeFromPumpkin(craftInst);
        }
    }

    private static int operationToProto(AttributeModifier.Operation op) {
        if (op == null) return 0;
        return switch (op) {
            case ADD_NUMBER -> 0;
            case ADD_SCALAR -> 1;
            case MULTIPLY_SCALAR_1 -> 2;
        };
    }

    private static AttributeModifier.Operation protoToOperation(int op) {
        return switch (op) {
            case 1 -> AttributeModifier.Operation.ADD_SCALAR;
            case 2 -> AttributeModifier.Operation.MULTIPLY_SCALAR_1;
            default -> AttributeModifier.Operation.ADD_NUMBER;
        };
    }

    public static void syncAttributeFromPumpkin(CraftAttributeInstance inst) {
        if (inst == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID ownerUuid = ATTRIBUTE_OWNERS.get(inst);
        if (ownerUuid == null) return;
        Attribute attr = inst.getAttribute();
        if (attr == null) return;
        SYNCING.set(true);
        try {
            GetEntityAttributeResponse res = NativeBridgeFfi.getEntityAttribute(
                GetEntityAttributeRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(ownerUuid))
                    .setAttribute(attr.getKey().asString())
                    .build()
            );
            if (res == null || !res.getFound()) return;
            inst.setBaseValue(res.getBaseValue());
            for (AttributeModifier existing : new ArrayList<>(inst.getModifiers())) {
                inst.removeModifier(existing);
            }
            for (AttributeModifierProto mod : res.getModifiersList()) {
                NamespacedKey key = NamespacedKey.fromString(mod.getId());
                if (key == null) {
                    key = new NamespacedKey("patchbukkit", mod.getId().replaceAll("[^a-z0-9/._-]", "_"));
                }
                inst.addTransientModifier(new AttributeModifier(key, mod.getAmount(), protoToOperation(mod.getOperation())));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAttributeBaseToPumpkin(CraftAttributeInstance inst, double baseValue) {
        if (inst == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID ownerUuid = ATTRIBUTE_OWNERS.get(inst);
        if (ownerUuid == null) return;
        Attribute attr = inst.getAttribute();
        if (attr == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setEntityAttributeBase(
                SetEntityAttributeBaseRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(ownerUuid))
                    .setAttribute(attr.getKey().asString())
                    .setBaseValue(baseValue)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAttributeModifierToPumpkin(
        CraftAttributeInstance inst,
        AttributeModifier modifier,
        boolean remove
    ) {
        if (inst == null || modifier == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID ownerUuid = ATTRIBUTE_OWNERS.get(inst);
        if (ownerUuid == null) return;
        Attribute attr = inst.getAttribute();
        if (attr == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.modifyEntityAttributeModifier(
                ModifyEntityAttributeModifierRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(ownerUuid))
                    .setAttribute(attr.getKey().asString())
                    .setModifier(
                        AttributeModifierProto.newBuilder()
                            .setId(modifier.getKey().asString())
                            .setAmount(modifier.getAmount())
                            .setOperation(operationToProto(modifier.getOperation()))
                            .build()
                    )
                    .setRemove(remove)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushRemoveAttributeModifierIdToPumpkin(CraftAttributeInstance inst, String id) {
        if (inst == null || id == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID ownerUuid = ATTRIBUTE_OWNERS.get(inst);
        if (ownerUuid == null) return;
        Attribute attr = inst.getAttribute();
        if (attr == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.modifyEntityAttributeModifier(
                ModifyEntityAttributeModifierRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(ownerUuid))
                    .setAttribute(attr.getKey().asString())
                    .setModifier(AttributeModifierProto.newBuilder().setId(id).build())
                    .setRemove(true)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Interceptors & Advice classes ----

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
        public static void onExit(@Advice.This CraftEntity craftEntity, @Advice.Argument(0) Vector velocity) {
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

    public static final class OnSetRotationAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) float yaw, @Advice.Argument(1) float pitch) {
            EntityBridge.pushRotationToPumpkin(entity, yaw, pitch);
        }
    }

    public static final class OnSyncMetadataAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftEntity entity) {
            EntityBridge.syncEntityMetadataFromPumpkin(entity);
        }
    }

    public static final class OnSetCustomNameComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) Component name) {
            EntityBridge.pushCustomNameComponentToPumpkin(entity, name);
        }
    }

    public static final class OnSetCustomNameStringAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) String name) {
            EntityBridge.pushCustomNameStringToPumpkin(entity, name);
        }
    }

    public static final class OnSetCustomNameVisibleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean visible) {
            EntityBridge.pushCustomNameVisibleToPumpkin(entity, visible);
        }
    }

    public static final class OnSetFireTicksAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) int ticks) {
            EntityBridge.pushFireTicksToPumpkin(entity, ticks);
        }
    }

    public static final class OnSetVisualFireAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean visualFire) {
            EntityBridge.pushVisualFireToPumpkin(entity, visualFire);
        }
    }

    public static final class OnSetFreezeTicksAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) int ticks) {
            EntityBridge.pushFreezeTicksToPumpkin(entity, ticks);
        }
    }

    public static final class OnSetGlowingAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean glowing) {
            EntityBridge.pushGlowingToPumpkin(entity, glowing);
        }
    }

    public static final class OnSetInvisibleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean invisible) {
            EntityBridge.pushInvisibleToPumpkin(entity, invisible);
        }
    }

    public static final class OnSetSilentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean silent) {
            EntityBridge.pushSilentToPumpkin(entity, silent);
        }
    }

    public static final class OnSetGravityAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean gravity) {
            EntityBridge.pushGravityToPumpkin(entity, gravity);
        }
    }

    public static final class OnSetInvulnerableAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) boolean invulnerable) {
            EntityBridge.pushInvulnerableToPumpkin(entity, invulnerable);
        }
    }

    public static final class OnSetFallDistanceAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) float fallDistance) {
            EntityBridge.pushFallDistanceToPumpkin(entity, fallDistance);
        }
    }

    public static final class OnAddScoreboardTagAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) String tag, @Advice.Return boolean added) {
            if (added) {
                EntityBridge.pushScoreboardTagToPumpkin(entity, tag, true);
            }
        }
    }

    public static final class OnRemoveScoreboardTagAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntity entity, @Advice.Argument(0) String tag, @Advice.Return boolean removed) {
            if (removed) {
                EntityBridge.pushScoreboardTagToPumpkin(entity, tag, false);
            }
        }
    }

    public static final class OnGetPassengersAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntity vehicle,
            @Advice.Return(readOnly = false) List<org.bukkit.entity.Entity> passengers
        ) {
            passengers = EntityBridge.getPassengersFromPumpkin(vehicle, passengers);
        }
    }

    public static final class OnGetVehicleAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntity passenger,
            @Advice.Return(readOnly = false) org.bukkit.entity.Entity vehicle
        ) {
            vehicle = EntityBridge.getVehicleFromPumpkin(passenger, vehicle);
        }
    }

    public static final class OnAddPassengerAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntity vehicle,
            @Advice.Argument(0) org.bukkit.entity.Entity passenger,
            @Advice.Return boolean ok
        ) {
            if (ok) {
                EntityBridge.pushPassengerToPumpkin(vehicle, passenger, true);
            }
        }
    }

    public static final class OnRemovePassengerAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntity vehicle,
            @Advice.Argument(0) org.bukkit.entity.Entity passenger,
            @Advice.Return boolean ok
        ) {
            if (ok) {
                EntityBridge.pushPassengerToPumpkin(vehicle, passenger, false);
            }
        }
    }

    public static final class OnEjectAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftEntity vehicle) {
            EntityBridge.pushEjectAllPassengersToPumpkin(vehicle);
        }
    }

    public static final class OnLeaveVehicleAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftEntity passenger) {
            EntityBridge.pushLeaveVehicleToPumpkin(passenger);
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

    public static final class OnLivingSetMaxHealthAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living) {
            EntityBridge.pushMaxHealthToPumpkin(living);
        }
    }

    public static final class OnLivingSetAbsorptionAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) double absorption) {
            EntityBridge.pushAbsorptionToPumpkin(living, absorption);
        }
    }

    public static final class OnLivingDamageAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftLivingEntity living,
            @Advice.Argument(0) double amount,
            @Advice.Argument(1) DamageSource source
        ) {
            EntityBridge.pushLivingDamageToPumpkin(living, amount, source);
        }
    }

    public static final class OnLivingGetAirAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftLivingEntity living) {
            EntityBridge.syncEntityMetadataFromPumpkin(living);
        }
    }

    public static final class OnLivingSetAirAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) int air) {
            EntityBridge.pushAirSupplyToPumpkin(living, air);
        }
    }

    public static final class OnLivingSyncPoseAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftLivingEntity living) {
            if (living instanceof CraftPlayer cp) {
                PlayerBridge.syncPoseFromPumpkin(cp);
            } else {
                EntityBridge.syncEntityMetadataFromPumpkin(living);
            }
        }
    }

    public static final class OnLivingSetSwimmingAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) boolean swimming) {
            EntityBridge.pushSwimmingToPumpkin(living, swimming);
        }
    }

    public static final class OnLivingSetGlidingAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) boolean gliding) {
            EntityBridge.pushGlidingToPumpkin(living, gliding);
        }
    }

    public static final class OnSwingMainHandAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living) {
            EntityBridge.pushSwingHandToPumpkin(living, false);
        }
    }

    public static final class OnSwingOffHandAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living) {
            EntityBridge.pushSwingHandToPumpkin(living, true);
        }
    }

    public static final class OnLivingSyncPotionEffectsAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftLivingEntity living) {
            EntityBridge.syncPotionEffectsFromPumpkin(living);
        }
    }

    public static final class OnLivingAddPotionEffectAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftLivingEntity living,
            @Advice.Argument(0) PotionEffect effect,
            @Advice.Return boolean added
        ) {
            EntityBridge.pushAddPotionEffectToPumpkin(living, effect, added);
        }
    }

    public static final class OnLivingRemovePotionEffectAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living, @Advice.Argument(0) PotionEffectType type) {
            EntityBridge.pushRemovePotionEffectToPumpkin(living, type);
        }
    }

    public static final class OnLivingClearPotionEffectsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living) {
            EntityBridge.pushClearPotionEffectsToPumpkin(living);
        }
    }

    public static final class OnLivingGetAttributeAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftLivingEntity living,
            @Advice.Return org.bukkit.attribute.AttributeInstance inst
        ) {
            EntityBridge.registerAttributeOwner(living, inst);
        }
    }

    public static final class OnSyncAttributeAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftAttributeInstance inst) {
            EntityBridge.syncAttributeFromPumpkin(inst);
        }
    }

    public static final class OnSetAttributeBaseAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftAttributeInstance inst, @Advice.Argument(0) double baseValue) {
            EntityBridge.pushAttributeBaseToPumpkin(inst, baseValue);
        }
    }

    public static final class OnAddAttributeModifierAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftAttributeInstance inst, @Advice.Argument(0) AttributeModifier modifier) {
            EntityBridge.pushAttributeModifierToPumpkin(inst, modifier, false);
        }
    }

    public static final class OnRemoveAttributeModifierAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftAttributeInstance inst, @Advice.Argument(0) AttributeModifier modifier) {
            EntityBridge.pushAttributeModifierToPumpkin(inst, modifier, true);
        }
    }

    public static final class OnRemoveAttributeModifierKeyAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftAttributeInstance inst, @Advice.Argument(0) Key key) {
            if (key != null) {
                EntityBridge.pushRemoveAttributeModifierIdToPumpkin(inst, key.asString());
            }
        }
    }

    public static final class OnRemoveAttributeModifierUuidAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftAttributeInstance inst, @Advice.Argument(0) UUID uuid) {
            if (uuid != null) {
                EntityBridge.pushRemoveAttributeModifierIdToPumpkin(inst, uuid.toString());
            }
        }
    }

    public static final class OnLivingDamageItemStackAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity living) {
            if (living instanceof CraftHumanEntity human && human.getHandle() != null) {
                InventoryBridge.pushAllToPumpkin(human.getHandle().getInventory());
            }
        }
    }
}
