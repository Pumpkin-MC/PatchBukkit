package org.patchbukkit.bootstrap;

import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftLivingEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.abilities.SetAbilitiesRequest;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.DamageEntityRequest;
import patchbukkit.entity.EntityHealthResponse;
import patchbukkit.entity.GetCooldownRequest;
import patchbukkit.entity.GetCooldownResponse;
import patchbukkit.entity.GetExperienceResponse;
import patchbukkit.entity.GetFoodLevelResponse;
import patchbukkit.entity.GetGamemodeResponse;
import patchbukkit.entity.GetPlayerLocaleResponse;
import patchbukkit.entity.GetPlayerPoseStateResponse;
import patchbukkit.entity.IsOnGroundResponse;
import patchbukkit.entity.IsOpResponse;
import patchbukkit.entity.KickPlayerRequest;
import patchbukkit.entity.PlayerConnectionInfoResponse;
import patchbukkit.entity.SendActionBarRequest;
import patchbukkit.entity.SendBlockChangeRequest;
import patchbukkit.entity.SendResourcePackRequest;
import patchbukkit.entity.SendTitleRequest;
import patchbukkit.entity.SetCompassTargetRequest;
import patchbukkit.entity.SetCooldownRequest;
import patchbukkit.entity.SetDisplayNameRequest;
import patchbukkit.entity.SetEntityHealthRequest;
import patchbukkit.entity.SetExhaustionRequest;
import patchbukkit.entity.SetExperienceRequest;
import patchbukkit.entity.SetFoodLevelRequest;
import patchbukkit.entity.SetGamemodeRequest;
import patchbukkit.entity.SetOpRequest;
import patchbukkit.entity.SetPlayerListHeaderFooterRequest;
import patchbukkit.entity.SetPlayerListNameRequest;
import patchbukkit.entity.SetPlayerTimeRequest;
import patchbukkit.entity.SetPlayerWeatherRequest;
import patchbukkit.entity.SetRespawnPointRequest;
import patchbukkit.entity.SetSaturationRequest;
import patchbukkit.entity.SetSneakingRequest;
import patchbukkit.entity.SetSprintingRequest;
import patchbukkit.entity.StopSoundRequest;
import patchbukkit.message.SendMessageRequest;
import patchbukkit.sound.PlayerEntityPlaySoundRequest;
import patchbukkit.sound.PlayerPlaySoundRequest;
import patchbukkit.sound.Sound;
import patchbukkit.world.SpawnParticleRequest;

/**
 * Bridges authentic Paper {@link CraftPlayer}, {@link CraftHumanEntity}, and {@link CraftLivingEntity}
 * state reads and mutations to Pumpkin's live player state via FFI.
 */
public final class PlayerBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private PlayerBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            ByteBuddy byteBuddy = new ByteBuddy();

            // 1. Hook CraftLivingEntity (getHealth, setHealth, damage, isSwimming, isGliding)
            try {
                byteBuddy
                    .redefine(CraftLivingEntity.class)
                    .visit(Advice.to(OnGetHealthAdvice.class).on(ElementMatchers.named("getHealth").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetHealthAdvice.class).on(ElementMatchers.named("setHealth").and(ElementMatchers.takesArguments(double.class))))
                    .visit(Advice.to(OnDamageAdvice.class).on(ElementMatchers.named("damage").and(ElementMatchers.takesArgument(0, double.class))))
                    .visit(Advice.to(OnLivingSyncPoseAdvice.class).on(
                        ElementMatchers.named("isSwimming").and(ElementMatchers.takesNoArguments())
                            .or(ElementMatchers.named("isGliding").and(ElementMatchers.takesNoArguments()))
                    ))
                    .make()
                    .load(CraftLivingEntity.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftLivingEntity in PlayerBridge", t);
            }

            // 2. Hook CraftHumanEntity (food, saturation, exhaustion, gamemode, op)
            try {
                byteBuddy
                    .redefine(CraftHumanEntity.class)
                    .visit(Advice.to(OnSyncFoodAdvice.class).on(
                        ElementMatchers.named("getFoodLevel")
                            .or(ElementMatchers.named("getSaturation"))
                            .or(ElementMatchers.named("getExhaustion"))
                    ))
                    .visit(Advice.to(OnSetFoodLevelAdvice.class).on(ElementMatchers.named("setFoodLevel").and(ElementMatchers.takesArguments(int.class))))
                    .visit(Advice.to(OnSetSaturationAdvice.class).on(ElementMatchers.named("setSaturation").and(ElementMatchers.takesArguments(float.class))))
                    .visit(Advice.to(OnSetExhaustionAdvice.class).on(ElementMatchers.named("setExhaustion").and(ElementMatchers.takesArguments(float.class))))
                    .visit(Advice.to(OnGetGameModeAdvice.class).on(ElementMatchers.named("getGameMode").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnIsOpAdvice.class).on(ElementMatchers.named("isOp").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetOpAdvice.class).on(ElementMatchers.named("setOp").and(ElementMatchers.takesArguments(boolean.class))))
                    .visit(Advice.to(OnGetCooldownAdvice.class).on(ElementMatchers.named("getCooldown").and(ElementMatchers.takesArguments(Key.class))))
                    .visit(Advice.to(OnSetCooldownAdvice.class).on(ElementMatchers.named("setCooldown").and(ElementMatchers.takesArguments(Key.class, int.class))))
                    .visit(Advice.to(OnGetEnderChestAdvice.class).on(ElementMatchers.named("getEnderChest").and(ElementMatchers.takesNoArguments())))
                    .make()
                    .load(CraftHumanEntity.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftHumanEntity in PlayerBridge", t);
            }

            // 3. Hook CraftPlayer (health, XP, gamemode, abilities/flight, sneaking/sprinting, ping, kick, messaging, time/weather, UI/sounds/particles)
            try {
                byteBuddy
                    .redefine(CraftPlayer.class)
                    .visit(Advice.to(OnGetHealthAdvice.class).on(ElementMatchers.named("getHealth").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetRealHealthAdvice.class).on(ElementMatchers.named("setRealHealth").and(ElementMatchers.takesArguments(double.class))))
                    .visit(Advice.to(OnGetGameModeAdvice.class).on(ElementMatchers.named("getGameMode").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetGameModeAdvice.class).on(ElementMatchers.named("setGameMode").and(ElementMatchers.takesArguments(GameMode.class))))
                    .visit(Advice.to(OnSyncExpAdvice.class).on(
                        ElementMatchers.named("getExp")
                            .or(ElementMatchers.named("getLevel"))
                            .or(ElementMatchers.named("getTotalExperience"))
                    ))
                    .visit(Advice.to(OnPushExpAdvice.class).on(
                        ElementMatchers.named("setExp")
                            .or(ElementMatchers.named("setLevel"))
                            .or(ElementMatchers.named("setTotalExperience"))
                            .or(ElementMatchers.named("giveExp"))
                            .or(ElementMatchers.named("giveExpLevels"))
                    ))
                    .visit(Advice.to(OnSyncAbilitiesAdvice.class).on(
                        ElementMatchers.named("getAllowFlight")
                            .or(ElementMatchers.named("isFlying"))
                            .or(ElementMatchers.named("getFlySpeed"))
                            .or(ElementMatchers.named("getWalkSpeed"))
                    ))
                    .visit(Advice.to(OnPushAbilitiesAdvice.class).on(
                        ElementMatchers.named("setAllowFlight")
                            .or(ElementMatchers.named("setFlying"))
                            .or(ElementMatchers.named("setFlySpeed"))
                            .or(ElementMatchers.named("setWalkSpeed"))
                    ))
                    .visit(Advice.to(OnSyncPoseAdvice.class).on(
                        ElementMatchers.named("isSneaking")
                            .or(ElementMatchers.named("isSprinting"))
                    ))
                    .visit(Advice.to(OnSetSneakingAdvice.class).on(ElementMatchers.named("setSneaking").and(ElementMatchers.takesArguments(boolean.class))))
                    .visit(Advice.to(OnSetSprintingAdvice.class).on(ElementMatchers.named("setSprinting").and(ElementMatchers.takesArguments(boolean.class))))
                    .visit(Advice.to(OnIsOpAdvice.class).on(ElementMatchers.named("isOp").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetOpAdvice.class).on(ElementMatchers.named("setOp").and(ElementMatchers.takesArguments(boolean.class))))
                    .visit(Advice.to(OnGetPingAdvice.class).on(ElementMatchers.named("getPing").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnKickPlayerAdvice.class).on(ElementMatchers.named("kickPlayer").and(ElementMatchers.takesArguments(String.class))))
                    .visit(Advice.to(OnKickComponentAdvice.class).on(ElementMatchers.named("kick").and(ElementMatchers.takesArgument(0, Component.class))))
                    .visit(Advice.to(OnSendRawMessageAdvice.class).on(ElementMatchers.named("sendRawMessage").and(ElementMatchers.takesArguments(UUID.class, String.class))))
                    .visit(Advice.to(OnGetCompassTargetAdvice.class).on(ElementMatchers.named("getCompassTarget").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetCompassTargetAdvice.class).on(ElementMatchers.named("setCompassTarget").and(ElementMatchers.takesArguments(Location.class))))
                    .visit(Advice.to(OnSetPlayerTimeAdvice.class).on(ElementMatchers.named("setPlayerTime").and(ElementMatchers.takesArguments(long.class, boolean.class))))
                    .visit(Advice.to(OnResetPlayerTimeAdvice.class).on(ElementMatchers.named("resetPlayerTime").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSetPlayerWeatherAdvice.class).on(ElementMatchers.named("setPlayerWeather").and(ElementMatchers.takesArguments(WeatherType.class))))
                    .visit(Advice.to(OnResetPlayerWeatherAdvice.class).on(ElementMatchers.named("resetPlayerWeather").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSendTitleAdvice.class).on(ElementMatchers.named("sendTitle").and(ElementMatchers.takesArguments(String.class, String.class, int.class, int.class, int.class))))
                    .visit(Advice.to(OnResetTitleAdvice.class).on(
                        ElementMatchers.named("resetTitle").and(ElementMatchers.takesNoArguments())
                            .or(ElementMatchers.named("clearTitle").and(ElementMatchers.takesNoArguments()))
                            .or(ElementMatchers.named("hideTitle").and(ElementMatchers.takesNoArguments()))
                    ))
                    .visit(Advice.to(OnSendActionBarStringAdvice.class).on(ElementMatchers.named("sendActionBar").and(ElementMatchers.takesArguments(String.class))))
                    .visit(Advice.to(OnSendActionBarComponentAdvice.class).on(ElementMatchers.named("sendActionBar").and(ElementMatchers.takesArguments(Component.class))))
                    .visit(Advice.to(OnSetDisplayNameAdvice.class).on(ElementMatchers.named("setDisplayName").and(ElementMatchers.takesArguments(String.class))))
                    .visit(Advice.to(OnSetPlayerListNameAdvice.class).on(ElementMatchers.named("setPlayerListName").and(ElementMatchers.takesArguments(String.class))))
                    .visit(Advice.to(OnSetPlayerListHeaderFooterAdvice.class).on(ElementMatchers.named("setPlayerListHeaderFooter").and(ElementMatchers.takesArguments(String.class, String.class))))
                    .visit(Advice.to(OnSendPlayerListHeaderAndFooterAdvice.class).on(ElementMatchers.named("sendPlayerListHeaderAndFooter").and(ElementMatchers.takesArguments(Component.class, Component.class))))
                    .visit(Advice.to(OnPlaySoundLocationAdvice.class).on(ElementMatchers.named("playSound").and(ElementMatchers.takesArguments(Location.class, String.class, SoundCategory.class, float.class, float.class, long.class))))
                    .visit(Advice.to(OnPlaySoundEntityAdvice.class).on(ElementMatchers.named("playSound").and(ElementMatchers.takesArguments(Entity.class, String.class, SoundCategory.class, float.class, float.class, long.class))))
                    .visit(Advice.to(OnStopSoundAdvice.class).on(ElementMatchers.named("stopSound").and(ElementMatchers.takesArguments(String.class, SoundCategory.class))))
                    .visit(Advice.to(OnStopAllSoundsAdvice.class).on(ElementMatchers.named("stopAllSounds").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSpawnParticleAdvice.class).on(ElementMatchers.named("spawnParticle").and(ElementMatchers.takesArgument(0, Particle.class)).and(ElementMatchers.takesArgument(1, double.class))))
                    .visit(Advice.to(OnSendBlockChangeAdvice.class).on(ElementMatchers.named("sendBlockChange").and(ElementMatchers.takesArguments(Location.class, BlockData.class))))
                    .visit(Advice.to(OnSetResourcePackAdvice.class).on(ElementMatchers.named("setResourcePack").and(ElementMatchers.takesArguments(UUID.class, String.class, byte[].class, Component.class, boolean.class))))
                    .visit(Advice.to(OnGetRespawnLocationAdvice.class).on(
                        ElementMatchers.named("getRespawnLocation").and(ElementMatchers.takesArguments(boolean.class))
                            .or(ElementMatchers.named("getPotentialRespawnLocation").and(ElementMatchers.takesNoArguments()))
                    ))
                    .visit(Advice.to(OnSetRespawnLocationAdvice.class).on(ElementMatchers.named("setRespawnLocation").and(ElementMatchers.takesArguments(Location.class, boolean.class))))
                    .visit(Advice.to(OnGetLocaleAdvice.class).on(ElementMatchers.named("getLocale").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnUpdateInventoryAdvice.class).on(ElementMatchers.named("updateInventory").and(ElementMatchers.takesNoArguments())))
                    .visit(Advice.to(OnSendHealthUpdateAdvice.class).on(ElementMatchers.named("sendHealthUpdate").and(ElementMatchers.takesNoArguments())))
                    .make()
                    .load(CraftPlayer.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftPlayer in PlayerBridge", t);
            }

            LOGGER.info("[PatchBukkit] Successfully patched CraftPlayer/CraftHumanEntity/CraftLivingEntity with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to install PlayerBridge hooks", t);
        }
    }

    public static void syncHealthFromPumpkin(CraftLivingEntity entity) {
        if (entity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = entity.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            EntityHealthResponse res = NativeBridgeFfi.getEntityHealth(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null && res.getHealth() >= 0.0) {
                handle.setHealth((float) res.getHealth());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushHealthToPumpkin(CraftLivingEntity entity, double health) {
        if (entity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = entity.getHandle();
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

    public static void pushDamageToPumpkin(CraftLivingEntity entity, double amount) {
        if (entity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        LivingEntity handle = entity.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.damageEntity(
                DamageEntityRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setAmount(amount)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void syncFoodFromPumpkin(CraftHumanEntity human) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            GetFoodLevelResponse res = NativeBridgeFfi.getFoodLevel(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null) {
                FoodData foodData = handle.getFoodData();
                foodData.setFoodLevel(res.getFoodLevel());
                foodData.setSaturation(res.getSaturation());
                foodData.exhaustionLevel = res.getExhaustion();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushFoodLevelToPumpkin(CraftHumanEntity human, int foodLevel) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setFoodLevel(
                SetFoodLevelRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setFoodLevel(foodLevel)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSaturationToPumpkin(CraftHumanEntity human, float saturation) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setSaturation(
                SetSaturationRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setSaturation(saturation)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushExhaustionToPumpkin(CraftHumanEntity human, float exhaustion) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setExhaustion(
                SetExhaustionRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setExhaustion(exhaustion)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void syncGameModeFromPumpkin(CraftHumanEntity human) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        if (!(human.getHandle() instanceof ServerPlayer sp)) return;
        SYNCING.set(true);
        try {
            GetGamemodeResponse res = NativeBridgeFfi.getGamemode(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null) {
                GameType gt = GameType.byId(res.getGamemode());
                sp.gameMode.changeGameModeForPlayer(gt);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushGameModeToPumpkin(CraftPlayer player, GameMode mode) {
        if (player == null || mode == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setGamemode(
                SetGamemodeRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setGamemode(mode.getValue())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void syncExpFromPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            GetExperienceResponse res = NativeBridgeFfi.getExperience(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null) {
                sp.experienceLevel = res.getLevel();
                sp.experienceProgress = res.getProgress();
                sp.totalExperience = res.getTotalExperience();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushExpToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setExperience(
                SetExperienceRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setLevel(sp.experienceLevel)
                    .setProgress(sp.experienceProgress)
                    .setTotalExperience(sp.totalExperience)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void syncAbilitiesFromPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            patchbukkit.abilities.Abilities res = NativeBridgeFfi.getAbilities(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null) {
                Abilities ab = sp.getAbilities();
                ab.invulnerable = res.getInvulnerable();
                ab.flying = res.getFlying();
                ab.mayfly = res.getAllowFlying();
                ab.instabuild = res.getCreative();
                ab.mayBuild = res.getAllowModifyWorld();
                if (res.getFlySpeed() > 0f) {
                    ab.setFlyingSpeed(res.getFlySpeed());
                }
                if (res.getWalkSpeed() > 0f) {
                    ab.setWalkingSpeed(res.getWalkSpeed());
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAbilitiesToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            Abilities ab = sp.getAbilities();
            NativeBridgeFfi.setAbilities(
                SetAbilitiesRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setAbilities(
                        patchbukkit.abilities.Abilities.newBuilder()
                            .setInvulnerable(ab.invulnerable)
                            .setFlying(ab.flying)
                            .setAllowFlying(ab.mayfly)
                            .setCreative(ab.instabuild)
                            .setAllowModifyWorld(ab.mayBuild)
                            .setFlySpeed(ab.getFlyingSpeed())
                            .setWalkSpeed(ab.getWalkingSpeed())
                            .build()
                    )
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void syncPoseFromPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            GetPlayerPoseStateResponse res = NativeBridgeFfi.getPlayerPoseState(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null) {
                sp.setShiftKeyDown(res.getIsSneaking());
                sp.setSprinting(res.getIsSprinting());
                sp.setSwimming(res.getIsSwimming());
                sp.setSharedFlag(7, res.getIsGliding());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSneakingToPumpkin(CraftPlayer player, boolean sneaking) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setSneaking(
                SetSneakingRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setSneaking(sneaking)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSprintingToPumpkin(CraftPlayer player, boolean sprinting) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setSprinting(
                SetSprintingRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setSprinting(sprinting)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void applyOpFromPumpkin(CraftHumanEntity human, boolean isOp) {
        if (human == null) return;
        boolean prev = SYNCING.get();
        SYNCING.set(true);
        try {
            if (human.isOp() != isOp) {
                human.setOp(isOp);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(prev);
        }
    }

    public static void syncOpFromPumpkin(CraftHumanEntity human) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            IsOpResponse res = NativeBridgeFfi.isOp(BridgeUtils.convertUuid(handle.getUUID()));
            if (res != null && human.isOp() != res.getIsOp()) {
                human.setOp(res.getIsOp());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushOpToPumpkin(CraftHumanEntity human, boolean isOp) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setOp(
                SetOpRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setIsOp(isOp)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static int getPingFromPumpkin(CraftPlayer player, int currentPing) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return currentPing;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return currentPing;
        SYNCING.set(true);
        try {
            PlayerConnectionInfoResponse res = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null) {
                return res.getPing();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return currentPing;
    }

    public static void pushKickToPumpkin(CraftPlayer player, String reason) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.kickPlayer(
                KickPlayerRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setMessage(reason != null ? reason : "Kicked from server")
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushKickComponentToPumpkin(CraftPlayer player, Component reason) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        String legacy = reason != null
            ? LegacyComponentSerializer.legacySection().serialize(reason)
            : "Kicked from server";
        pushKickToPumpkin(player, legacy);
    }

    public static void pushRawMessageToPumpkin(CraftPlayer player, String message) {
        if (player == null || message == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        // Note: Only send via NativeBridgeFfi if the virtual channel doesn't already forward via PacketEncoder
        if (sp.connection != null && sp.connection.connection != null && sp.connection.connection.channel != null) {
            return;
        }
        SYNCING.set(true);
        try {
            NativeBridgeFfi.sendMessage(
                SendMessageRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setMessage(message)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushCompassTargetToPumpkin(CraftPlayer player, Location loc) {
        if (player == null || loc == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setCompassTarget(
                SetCompassTargetRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setPosition(
                        patchbukkit.common.Vec3.newBuilder()
                            .setX(loc.getX())
                            .setY(loc.getY())
                            .setZ(loc.getZ())
                            .build()
                    )
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPlayerTimeToPumpkin(CraftPlayer player, long time, boolean relative) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerTime(
                SetPlayerTimeRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setTime(time)
                    .setRelative(relative)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushResetPlayerTimeToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.resetPlayerTime(BridgeUtils.convertUuid(sp.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPlayerWeatherToPumpkin(CraftPlayer player, WeatherType weather) {
        if (player == null || weather == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerWeather(
                SetPlayerWeatherRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setWeather(weather == WeatherType.DOWNFALL ? 1 : 0)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushResetPlayerWeatherToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.resetPlayerWeather(BridgeUtils.convertUuid(sp.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnGetHealthAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftLivingEntity entity) {
            PlayerBridge.syncHealthFromPumpkin(entity);
        }
    }

    public static final class OnSetHealthAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity entity, @Advice.Argument(0) double health) {
            PlayerBridge.pushHealthToPumpkin(entity, health);
        }
    }

    public static final class OnSetRealHealthAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) double health) {
            PlayerBridge.pushHealthToPumpkin(player, health);
        }
    }

    public static final class OnDamageAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftLivingEntity entity, @Advice.Argument(0) double amount) {
            PlayerBridge.pushDamageToPumpkin(entity, amount);
        }
    }

    public static final class OnSyncFoodAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            PlayerBridge.syncFoodFromPumpkin(human);
        }
    }

    public static final class OnSetFoodLevelAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) int foodLevel) {
            PlayerBridge.pushFoodLevelToPumpkin(human, foodLevel);
        }
    }

    public static final class OnSetSaturationAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) float saturation) {
            PlayerBridge.pushSaturationToPumpkin(human, saturation);
        }
    }

    public static final class OnSetExhaustionAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) float exhaustion) {
            PlayerBridge.pushExhaustionToPumpkin(human, exhaustion);
        }
    }

    public static final class OnGetGameModeAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            PlayerBridge.syncGameModeFromPumpkin(human);
        }
    }

    public static final class OnSetGameModeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) GameMode mode) {
            PlayerBridge.pushGameModeToPumpkin(player, mode);
        }
    }

    public static final class OnSyncExpAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player) {
            PlayerBridge.syncExpFromPumpkin(player);
        }
    }

    public static final class OnPushExpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushExpToPumpkin(player);
        }
    }

    public static final class OnSyncAbilitiesAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player) {
            PlayerBridge.syncAbilitiesFromPumpkin(player);
        }
    }

    public static final class OnPushAbilitiesAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushAbilitiesToPumpkin(player);
        }
    }

    public static final class OnSyncPoseAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player) {
            PlayerBridge.syncPoseFromPumpkin(player);
        }
    }

    public static final class OnSetSneakingAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) boolean sneaking) {
            PlayerBridge.pushSneakingToPumpkin(player, sneaking);
        }
    }

    public static final class OnSetSprintingAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) boolean sprinting) {
            PlayerBridge.pushSprintingToPumpkin(player, sprinting);
        }
    }

    public static final class OnIsOpAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            PlayerBridge.syncOpFromPumpkin(human);
        }
    }

    public static final class OnSetOpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) boolean isOp) {
            PlayerBridge.pushOpToPumpkin(human, isOp);
        }
    }

    public static final class OnGetPingAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) int ping) {
            ping = PlayerBridge.getPingFromPumpkin(player, ping);
        }
    }

    public static final class OnKickPlayerAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player, @Advice.Argument(0) String reason) {
            PlayerBridge.pushKickToPumpkin(player, reason);
        }
    }

    public static final class OnKickComponentAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player, @Advice.Argument(0) Component reason) {
            PlayerBridge.pushKickComponentToPumpkin(player, reason);
        }
    }

    public static final class OnSendRawMessageAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(1) String message) {
            PlayerBridge.pushRawMessageToPumpkin(player, message);
        }
    }

    public static final class OnSetCompassTargetAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Location loc) {
            PlayerBridge.pushCompassTargetToPumpkin(player, loc);
        }
    }

    public static final class OnSetPlayerTimeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) long time, @Advice.Argument(1) boolean relative) {
            PlayerBridge.pushPlayerTimeToPumpkin(player, time, relative);
        }
    }

    public static final class OnResetPlayerTimeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushResetPlayerTimeToPumpkin(player);
        }
    }

    public static final class OnSetPlayerWeatherAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) WeatherType weather) {
            PlayerBridge.pushPlayerWeatherToPumpkin(player, weather);
        }
    }

    public static final class OnResetPlayerWeatherAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushResetPlayerWeatherToPumpkin(player);
        }
    }

    public static int getCooldownFromPumpkin(CraftHumanEntity human, Key key, int fallback) {
        if (human == null || key == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return fallback;
        SYNCING.set(true);
        try {
            GetCooldownResponse res = NativeBridgeFfi.getCooldown(
                GetCooldownRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setItemGroup(key.asString())
                    .build()
            );
            if (res != null && res.getIsOnCooldown()) {
                return Math.max(fallback, Math.round(res.getCooldown()));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushCooldownToPumpkin(CraftHumanEntity human, Key key, int ticks) {
        if (human == null || key == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setCooldown(
                SetCooldownRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setItemGroup(key.asString())
                    .setDurationTicks(ticks)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushTitleToPumpkin(CraftPlayer player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.sendTitle(
                SendTitleRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setTitle(title != null ? title : "")
                    .setSubtitle(subtitle != null ? subtitle : "")
                    .setFadeIn(fadeIn)
                    .setStay(stay)
                    .setFadeOut(fadeOut)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushResetTitleToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.resetTitle(BridgeUtils.convertUuid(sp.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushActionBarToPumpkin(CraftPlayer player, String message) {
        if (player == null || message == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.sendActionBar(
                SendActionBarRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setMessage(message)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushActionBarComponentToPumpkin(CraftPlayer player, Component message) {
        if (player == null || message == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        pushActionBarToPumpkin(player, LegacyComponentSerializer.legacySection().serialize(message));
    }

    public static void pushDisplayNameToPumpkin(CraftPlayer player, String name) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setDisplayName(
                SetDisplayNameRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setDisplayName(name != null ? name : player.getName())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPlayerListNameToPumpkin(CraftPlayer player, String name) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerListName(
                SetPlayerListNameRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setListName(name != null ? name : player.getName())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPlayerListHeaderFooterToPumpkin(CraftPlayer player, String header, String footer) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerListHeaderFooter(
                SetPlayerListHeaderFooterRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setHeader(header != null ? header : "")
                    .setFooter(footer != null ? footer : "")
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPlayerListHeaderFooterComponentToPumpkin(CraftPlayer player, Component header, Component footer) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        String h = header != null ? LegacyComponentSerializer.legacySection().serialize(header) : "";
        String f = footer != null ? LegacyComponentSerializer.legacySection().serialize(footer) : "";
        pushPlayerListHeaderFooterToPumpkin(player, h, f);
    }

    public static void pushPlaySoundLocationToPumpkin(
        CraftPlayer player,
        Location loc,
        String sound,
        SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        if (player == null || loc == null || sound == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.playerPlaySound(
                PlayerPlaySoundRequest.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setSound(
                        Sound.newBuilder()
                            .setName(sound)
                            .setCategory(category != null ? category.name() : "MASTER")
                            .build()
                    )
                    .setLocation(BridgeUtils.convertLocation(loc))
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

    public static void pushPlaySoundEntityToPumpkin(
        CraftPlayer player,
        Entity entity,
        String sound,
        SoundCategory category,
        float volume,
        float pitch,
        long seed
    ) {
        if (player == null || entity == null || sound == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.playerEntityPlaySound(
                PlayerEntityPlaySoundRequest.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setEntityUuid(BridgeUtils.convertUuid(entity.getUniqueId()))
                    .setSound(
                        Sound.newBuilder()
                            .setName(sound)
                            .setCategory(category != null ? category.name() : "MASTER")
                            .build()
                    )
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

    public static void pushStopSoundToPumpkin(CraftPlayer player, String sound, SoundCategory category) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.stopSound(
                StopSoundRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setSound(sound != null ? sound : "")
                    .setCategory(category != null ? category.name() : "")
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSpawnParticleToPumpkin(
        CraftPlayer player,
        Particle particle,
        double x,
        double y,
        double z,
        int count,
        double offsetX,
        double offsetY,
        double offsetZ,
        double extra,
        Object data
    ) {
        if (player == null || particle == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        if (player.getWorld() == null) return;
        SYNCING.set(true);
        try {
            byte[] encodedData = WorldBridge.encodeParticleData(particle, data);
            NativeBridgeFfi.spawnParticle(
                SpawnParticleRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(player.getWorld().getUID()))
                    .setParticle(particle.getKey().asString())
                    .setX(x)
                    .setY(y)
                    .setZ(z)
                    .setCount(count)
                    .setOffsetX(offsetX)
                    .setOffsetY(offsetY)
                    .setOffsetZ(offsetZ)
                    .setExtra(extra)
                    .setForce(true)
                    .setData(com.google.protobuf.ByteString.copyFrom(encodedData))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushBlockChangeToPumpkin(CraftPlayer player, Location loc, BlockData data) {
        if (player == null || loc == null || data == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            String stateStr = (data instanceof CraftBlockData cbd)
                ? WorldBridge.serializeBlockState(cbd.getState())
                : data.getAsString();
            NativeBridgeFfi.sendBlockChange(
                SendBlockChangeRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setX(loc.getBlockX())
                    .setY(loc.getBlockY())
                    .setZ(loc.getBlockZ())
                    .setBlockState(stateStr)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushResourcePackToPumpkin(CraftPlayer player, String url, Component prompt, boolean required) {
        if (player == null || url == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            String promptStr = prompt != null ? LegacyComponentSerializer.legacySection().serialize(prompt) : "";
            NativeBridgeFfi.sendResourcePack(
                SendResourcePackRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setUrl(url)
                    .setPrompt(promptStr)
                    .setRequired(required)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushRespawnLocationToPumpkin(CraftPlayer player, Location loc, boolean force) {
        if (player == null || loc == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setRespawnPoint(
                SetRespawnPointRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setPosition(
                        patchbukkit.common.Vec3.newBuilder()
                            .setX(loc.getX())
                            .setY(loc.getY())
                            .setZ(loc.getZ())
                            .build()
                    )
                    .setYaw(loc.getYaw())
                    .setForce(force)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static String getLocaleFromPumpkin(CraftPlayer player, String fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return fallback;
        SYNCING.set(true);
        try {
            GetPlayerLocaleResponse res = NativeBridgeFfi.getPlayerLocale(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null && !res.getLocale().isEmpty()) {
                return res.getLocale();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static final class OnGetCooldownAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) Key key, @Advice.Return(readOnly = false) int cooldown) {
            cooldown = PlayerBridge.getCooldownFromPumpkin(human, key, cooldown);
        }
    }

    public static final class OnSetCooldownAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) Key key, @Advice.Argument(1) int ticks) {
            PlayerBridge.pushCooldownToPumpkin(human, key, ticks);
        }
    }

    public static final class OnSendTitleAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) String title,
            @Advice.Argument(1) String subtitle,
            @Advice.Argument(2) int fadeIn,
            @Advice.Argument(3) int stay,
            @Advice.Argument(4) int fadeOut
        ) {
            PlayerBridge.pushTitleToPumpkin(player, title, subtitle, fadeIn, stay, fadeOut);
        }
    }

    public static final class OnResetTitleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushResetTitleToPumpkin(player);
        }
    }

    public static final class OnSendActionBarStringAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String message) {
            PlayerBridge.pushActionBarToPumpkin(player, message);
        }
    }

    public static final class OnSendActionBarComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component message) {
            PlayerBridge.pushActionBarComponentToPumpkin(player, message);
        }
    }

    public static final class OnSetDisplayNameAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String name) {
            PlayerBridge.pushDisplayNameToPumpkin(player, name);
        }
    }

    public static final class OnSetPlayerListNameAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String name) {
            PlayerBridge.pushPlayerListNameToPumpkin(player, name);
        }
    }

    public static final class OnSetPlayerListHeaderFooterAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String header, @Advice.Argument(1) String footer) {
            PlayerBridge.pushPlayerListHeaderFooterToPumpkin(player, header, footer);
        }
    }

    public static final class OnSendPlayerListHeaderAndFooterAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component header, @Advice.Argument(1) Component footer) {
            PlayerBridge.pushPlayerListHeaderFooterComponentToPumpkin(player, header, footer);
        }
    }

    public static final class OnPlaySoundLocationAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) Location loc,
            @Advice.Argument(1) String sound,
            @Advice.Argument(2) SoundCategory category,
            @Advice.Argument(3) float volume,
            @Advice.Argument(4) float pitch,
            @Advice.Argument(5) long seed
        ) {
            PlayerBridge.pushPlaySoundLocationToPumpkin(player, loc, sound, category, volume, pitch, seed);
        }
    }

    public static final class OnPlaySoundEntityAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) Entity entity,
            @Advice.Argument(1) String sound,
            @Advice.Argument(2) SoundCategory category,
            @Advice.Argument(3) float volume,
            @Advice.Argument(4) float pitch,
            @Advice.Argument(5) long seed
        ) {
            PlayerBridge.pushPlaySoundEntityToPumpkin(player, entity, sound, category, volume, pitch, seed);
        }
    }

    public static final class OnStopSoundAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String sound, @Advice.Argument(1) SoundCategory category) {
            PlayerBridge.pushStopSoundToPumpkin(player, sound, category);
        }
    }

    public static final class OnStopAllSoundsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushStopSoundToPumpkin(player, "", null);
        }
    }

    public static final class OnSpawnParticleAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) Particle particle,
            @Advice.Argument(1) double x,
            @Advice.Argument(2) double y,
            @Advice.Argument(3) double z,
            @Advice.Argument(4) int count,
            @Advice.Argument(5) double offsetX,
            @Advice.Argument(6) double offsetY,
            @Advice.Argument(7) double offsetZ,
            @Advice.Argument(8) double extra,
            @Advice.Argument(11) Object data
        ) {
            PlayerBridge.pushSpawnParticleToPumpkin(player, particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data);
        }
    }

    public static final class OnSendBlockChangeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Location loc, @Advice.Argument(1) BlockData data) {
            PlayerBridge.pushBlockChangeToPumpkin(player, loc, data);
        }
    }

    public static final class OnSetResourcePackAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(1) String url,
            @Advice.Argument(3) Component prompt,
            @Advice.Argument(4) boolean required
        ) {
            PlayerBridge.pushResourcePackToPumpkin(player, url, prompt, required);
        }
    }

    public static final class OnSetRespawnLocationAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Location loc, @Advice.Argument(1) boolean force) {
            PlayerBridge.pushRespawnLocationToPumpkin(player, loc, force);
        }
    }

    public static final class OnGetLocaleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) String locale) {
            locale = PlayerBridge.getLocaleFromPumpkin(player, locale);
        }
    }

    public static void pushOpenEnderChestToPumpkin(CraftHumanEntity human) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.openEnderChest(BridgeUtils.convertUuid(handle.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static Location getCompassTargetFromPumpkin(CraftPlayer player, Location fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return fallback;
        SYNCING.set(true);
        try {
            patchbukkit.common.Vec3 vec = NativeBridgeFfi.getCompassTarget(BridgeUtils.convertUuid(sp.getUUID()));
            if (vec != null && (vec.getX() != 0.0 || vec.getY() != 0.0 || vec.getZ() != 0.0)) {
                return new Location(player.getWorld(), vec.getX(), vec.getY(), vec.getZ());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static Location getRespawnLocationFromPumpkin(CraftPlayer player, Location fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return fallback;
        SYNCING.set(true);
        try {
            patchbukkit.common.Location protoLoc = NativeBridgeFfi.getRespawnPoint(BridgeUtils.convertUuid(sp.getUUID()));
            if (protoLoc != null && protoLoc.hasPosition()) {
                return BridgeUtils.convertLocation(protoLoc);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushUpdateInventoryToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.updateInventory(BridgeUtils.convertUuid(sp.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSendHealthUpdateToPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.sendGameEvent(
                patchbukkit.entity.SendGameEventRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setEventType(0)
                    .setValue((float) player.getHealth())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnGetEnderChestAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            PlayerBridge.pushOpenEnderChestToPumpkin(human);
        }
    }

    public static final class OnGetCompassTargetAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) Location loc) {
            loc = PlayerBridge.getCompassTargetFromPumpkin(player, loc);
        }
    }

    public static final class OnGetRespawnLocationAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) Location loc) {
            loc = PlayerBridge.getRespawnLocationFromPumpkin(player, loc);
        }
    }

    public static final class OnUpdateInventoryAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushUpdateInventoryToPumpkin(player);
        }
    }

    public static final class OnSendHealthUpdateAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            PlayerBridge.pushSendHealthUpdateToPumpkin(player);
        }
    }

    public static final class OnLivingSyncPoseAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftLivingEntity entity) {
            if (entity instanceof CraftPlayer cp) {
                PlayerBridge.syncPoseFromPumpkin(cp);
            }
        }
    }
}
