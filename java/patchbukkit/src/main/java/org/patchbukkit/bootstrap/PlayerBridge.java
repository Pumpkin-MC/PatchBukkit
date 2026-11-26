package org.patchbukkit.bootstrap;

import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.protobuf.ByteString;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.WeatherType;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.abilities.SetAbilitiesRequest;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.entity.GetCooldownRequest;
import patchbukkit.entity.GetCooldownResponse;
import patchbukkit.entity.GetExperienceResponse;
import patchbukkit.entity.GetFoodLevelResponse;
import patchbukkit.entity.GetGamemodeResponse;
import patchbukkit.entity.GetPlayerLocaleResponse;
import patchbukkit.entity.GetPlayerPoseStateResponse;
import patchbukkit.entity.GetPlayerTimeWeatherResponse;
import patchbukkit.entity.IsOpResponse;
import patchbukkit.entity.KickPlayerRequest;
import patchbukkit.entity.PlayerConnectionInfoResponse;
import patchbukkit.entity.PlayerSleepRequest;
import patchbukkit.entity.SendActionBarRequest;
import patchbukkit.entity.SendBlockChangeRequest;
import patchbukkit.entity.SendPluginMessageRequest;
import patchbukkit.entity.SendResourcePackRequest;
import patchbukkit.entity.SendTitleRequest;
import patchbukkit.entity.SetCompassTargetRequest;
import patchbukkit.entity.SetCooldownRequest;
import patchbukkit.entity.SetDisplayNameRequest;
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
import patchbukkit.entity.SetSpectatorTargetRequest;
import patchbukkit.entity.SetSprintingRequest;
import patchbukkit.entity.StopSoundRequest;
import patchbukkit.entity.UpdateBossBarRequest;
import patchbukkit.message.SendMessageRequest;
import patchbukkit.sound.PlayerEntityPlaySoundRequest;
import patchbukkit.sound.PlayerPlaySoundRequest;
import patchbukkit.sound.Sound;
import patchbukkit.world.SpawnParticleRequest;

/**
 * Bridges authentic Paper {@link CraftPlayer}, {@link CraftHumanEntity}, and {@link ServerBossEvent}
 * state reads and mutations to Pumpkin's live player state via FFI.
 */
public final class PlayerBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private PlayerBridge() {}

    public static void patch() {
        try {
            // 1. Hook CraftHumanEntity (composed with InventoryBridge via BridgePatcher)
            BridgePatcher.register(CraftHumanEntity.class, "PlayerBridge", builder -> builder
                .visit(Advice.to(OnSyncFoodAdvice.class).on(
                    ElementMatchers.named("getFoodLevel")
                        .or(ElementMatchers.named("getSaturation"))
                        .or(ElementMatchers.named("getExhaustion"))
                ))
                .visit(Advice.to(OnSetFoodLevelAdvice.class).on(
                    ElementMatchers.named("setFoodLevel").and(ElementMatchers.takesArguments(int.class))
                ))
                .visit(Advice.to(OnSetSaturationAdvice.class).on(
                    ElementMatchers.named("setSaturation").and(ElementMatchers.takesArguments(float.class))
                ))
                .visit(Advice.to(OnSetExhaustionAdvice.class).on(
                    ElementMatchers.named("setExhaustion").and(ElementMatchers.takesArguments(float.class))
                ))
                .visit(Advice.to(OnGetGameModeAdvice.class).on(
                    ElementMatchers.named("getGameMode").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnIsOpAdvice.class).on(
                    ElementMatchers.named("isOp").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetOpAdvice.class).on(
                    ElementMatchers.named("setOp").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnGetCooldownKeyAdvice.class).on(
                    ElementMatchers.named("getCooldown").and(ElementMatchers.takesArguments(Key.class))
                ))
                .visit(Advice.to(OnSetCooldownKeyAdvice.class).on(
                    ElementMatchers.named("setCooldown").and(ElementMatchers.takesArguments(Key.class, int.class))
                ))
                .visit(Advice.to(OnGetCooldownMaterialAdvice.class).on(
                    ElementMatchers.named("getCooldown").and(ElementMatchers.takesArguments(Material.class))
                ))
                .visit(Advice.to(OnHasCooldownMaterialAdvice.class).on(
                    ElementMatchers.named("hasCooldown").and(ElementMatchers.takesArguments(Material.class))
                ))
                .visit(Advice.to(OnGetCooldownItemAdvice.class).on(
                    ElementMatchers.named("getCooldown").and(ElementMatchers.takesArguments(ItemStack.class))
                ))
                .visit(Advice.to(OnHasCooldownItemAdvice.class).on(
                    ElementMatchers.named("hasCooldown").and(ElementMatchers.takesArguments(ItemStack.class))
                ))
                .visit(Advice.to(OnSetCooldownItemAdvice.class).on(
                    ElementMatchers.named("setCooldown").and(ElementMatchers.takesArguments(ItemStack.class, int.class))
                ))
                .visit(Advice.to(OnGetPotentialRespawnLocationAdvice.class).on(
                    ElementMatchers.named("getPotentialRespawnLocation").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSleepAdvice.class).on(
                    ElementMatchers.named("sleep").and(ElementMatchers.takesArguments(Location.class, boolean.class))
                ))
                .visit(Advice.to(OnWakeupAdvice.class).on(
                    ElementMatchers.named("wakeup").and(ElementMatchers.takesArguments(boolean.class))
                ))
            );

            // 2. Hook CraftPlayer
            BridgePatcher.register(CraftPlayer.class, "PlayerBridge", builder -> builder
                .visit(Advice.to(OnPlayerGetHealthAdvice.class).on(
                    ElementMatchers.named("getHealth").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetRealHealthAdvice.class).on(
                    ElementMatchers.named("setRealHealth").and(ElementMatchers.takesArguments(double.class))
                ))
                .visit(Advice.to(OnPlayerSetMaxHealthAdvice.class).on(
                    ElementMatchers.named("setMaxHealth").and(ElementMatchers.takesArguments(double.class))
                        .or(ElementMatchers.named("resetMaxHealth").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnPlayerSetRotationAdvice.class).on(
                    ElementMatchers.named("setRotation").and(ElementMatchers.takesArguments(float.class, float.class))
                ))
                .visit(Advice.to(OnGetGameModeAdvice.class).on(
                    ElementMatchers.named("getGameMode").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetGameModeAdvice.class).on(
                    ElementMatchers.named("setGameMode").and(ElementMatchers.takesArguments(GameMode.class))
                ))
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
                .visit(Advice.to(OnSetSneakingAdvice.class).on(
                    ElementMatchers.named("setSneaking").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetSprintingAdvice.class).on(
                    ElementMatchers.named("setSprinting").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnIsOpAdvice.class).on(
                    ElementMatchers.named("isOp").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetOpAdvice.class).on(
                    ElementMatchers.named("setOp").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnGetPingAdvice.class).on(
                    ElementMatchers.named("getPing").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetAddressAdvice.class).on(
                    ElementMatchers.named("getAddress").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetClientBrandAdvice.class).on(
                    ElementMatchers.named("getClientBrandName").and(ElementMatchers.takesNoArguments())
                ))
                // Hook ONLY canonical kick(Component, PlayerKickEvent.Cause) on exit so cancelled PlayerKickEvent is respected
                .visit(Advice.to(OnKickComponentAdvice.class).on(
                    ElementMatchers.named("kick").and(ElementMatchers.takesArguments(Component.class, PlayerKickEvent.Cause.class))
                ))
                .visit(Advice.to(OnSendRawMessageAdvice.class).on(
                    ElementMatchers.named("sendRawMessage").and(ElementMatchers.takesArguments(UUID.class, String.class))
                ))
                .visit(Advice.to(OnGetCompassTargetAdvice.class).on(
                    ElementMatchers.named("getCompassTarget").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetCompassTargetAdvice.class).on(
                    ElementMatchers.named("setCompassTarget").and(ElementMatchers.takesArguments(Location.class))
                ))
                .visit(Advice.to(OnSyncPlayerTimeWeatherAdvice.class).on(
                    ElementMatchers.named("getPlayerTime").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("getPlayerTimeOffset").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("isPlayerTimeRelative").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("getPlayerWeather").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnSetPlayerTimeAdvice.class).on(
                    ElementMatchers.named("setPlayerTime").and(ElementMatchers.takesArguments(long.class, boolean.class))
                ))
                .visit(Advice.to(OnResetPlayerTimeAdvice.class).on(
                    ElementMatchers.named("resetPlayerTime").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetPlayerWeatherAdvice.class).on(
                    ElementMatchers.named("setPlayerWeather").and(ElementMatchers.takesArguments(WeatherType.class))
                ))
                .visit(Advice.to(OnResetPlayerWeatherAdvice.class).on(
                    ElementMatchers.named("resetPlayerWeather").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSendTitleAdvice.class).on(
                    ElementMatchers.named("sendTitle").and(ElementMatchers.takesArguments(String.class, String.class, int.class, int.class, int.class))
                ))
                .visit(Advice.to(OnResetTitleAdvice.class).on(
                    ElementMatchers.named("resetTitle").and(ElementMatchers.takesNoArguments())
                        .or(ElementMatchers.named("clearTitle").and(ElementMatchers.takesNoArguments()))
                        .or(ElementMatchers.named("hideTitle").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnSendActionBarStringAdvice.class).on(
                    ElementMatchers.named("sendActionBar").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnSendActionBarComponentAdvice.class).on(
                    ElementMatchers.named("sendActionBar").and(ElementMatchers.takesArguments(Component.class))
                ))
                .visit(Advice.to(OnSetDisplayNameAdvice.class).on(
                    ElementMatchers.named("setDisplayName").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnSetDisplayNameComponentAdvice.class).on(
                    ElementMatchers.named("displayName").and(ElementMatchers.takesArguments(Component.class))
                ))
                .visit(Advice.to(OnSetPlayerListNameAdvice.class).on(
                    ElementMatchers.named("setPlayerListName").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnSetPlayerListNameComponentAdvice.class).on(
                    ElementMatchers.named("playerListName").and(ElementMatchers.takesArguments(Component.class))
                ))
                .visit(Advice.to(OnSetPlayerListHeaderFooterAdvice.class).on(
                    ElementMatchers.named("setPlayerListHeaderFooter").and(ElementMatchers.takesArguments(String.class, String.class))
                ))
                .visit(Advice.to(OnSendPlayerListHeaderAdvice.class).on(
                    ElementMatchers.named("sendPlayerListHeader").and(ElementMatchers.takesArguments(Component.class))
                ))
                .visit(Advice.to(OnSendPlayerListFooterAdvice.class).on(
                    ElementMatchers.named("sendPlayerListFooter").and(ElementMatchers.takesArguments(Component.class))
                ))
                .visit(Advice.to(OnSendPlayerListHeaderAndFooterAdvice.class).on(
                    ElementMatchers.named("sendPlayerListHeaderAndFooter").and(ElementMatchers.takesArguments(Component.class, Component.class))
                ))
                // Hook canonical playSound0 overloads so String, Sound enum, and Adventure Sound all bridge cleanly
                .visit(Advice.to(OnPlaySound0LocationAdvice.class).on(
                    ElementMatchers.named("playSound0").and(
                        ElementMatchers.takesArguments(Location.class, Holder.class, SoundSource.class, float.class, float.class, long.class)
                    )
                ))
                .visit(Advice.to(OnPlaySound0EntityAdvice.class).on(
                    ElementMatchers.named("playSound0").and(
                        ElementMatchers.takesArguments(Entity.class, Holder.class, SoundSource.class, float.class, float.class, long.class)
                    )
                ))
                .visit(Advice.to(OnStopSoundAdvice.class).on(
                    ElementMatchers.named("stopSound").and(ElementMatchers.takesArguments(String.class, SoundCategory.class))
                ))
                .visit(Advice.to(OnStopAllSoundsAdvice.class).on(
                    ElementMatchers.named("stopAllSounds").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSpawnParticleAdvice.class).on(
                    ElementMatchers.named("spawnParticle").and(ElementMatchers.takesArgument(0, Particle.class)).and(ElementMatchers.takesArgument(1, double.class))
                ))
                .visit(Advice.to(OnSendBlockChangeAdvice.class).on(
                    ElementMatchers.named("sendBlockChange").and(ElementMatchers.takesArguments(Location.class, BlockData.class))
                ))
                .visit(Advice.to(OnSetResourcePackComponentAdvice.class).on(
                    ElementMatchers.named("setResourcePack").and(ElementMatchers.takesArguments(UUID.class, String.class, byte[].class, Component.class, boolean.class))
                ))
                .visit(Advice.to(OnSetResourcePackStringUuidAdvice.class).on(
                    ElementMatchers.named("setResourcePack").and(ElementMatchers.takesArguments(UUID.class, String.class, byte[].class, String.class, boolean.class))
                ))
                .visit(Advice.to(OnSetResourcePackLegacyAdvice.class).on(
                    ElementMatchers.named("setResourcePack").and(ElementMatchers.takesArguments(String.class, byte[].class, String.class, boolean.class))
                ))
                .visit(Advice.to(OnGetRespawnLocationAdvice.class).on(
                    ElementMatchers.named("getRespawnLocation").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnSetRespawnLocationAdvice.class).on(
                    ElementMatchers.named("setRespawnLocation").and(ElementMatchers.takesArguments(Location.class, boolean.class))
                ))
                .visit(Advice.to(OnGetLocaleAdvice.class).on(
                    ElementMatchers.named("getLocale").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnGetAdventureLocaleAdvice.class).on(
                    ElementMatchers.named("locale").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnUpdateInventoryAdvice.class).on(
                    ElementMatchers.named("updateInventory").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSendHealthUpdateAdvice.class).on(
                    ElementMatchers.named("sendHealthUpdate").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnSetSpectatorTargetAdvice.class).on(
                    ElementMatchers.named("setSpectatorTarget").and(ElementMatchers.takesArguments(Entity.class))
                ))
                .visit(Advice.to(OnSendPluginMessageAdvice.class).on(
                    ElementMatchers.named("sendPluginMessage").and(ElementMatchers.takesArguments(Plugin.class, String.class, byte[].class))
                ))
            );

            // 3. Hook ServerBossEvent (backs CraftBossBar and NMS bossbars)
            BridgePatcher.register(ServerBossEvent.class, "PlayerBridge", builder -> builder
                .visit(Advice.to(OnBossBarAddPlayerAdvice.class).on(
                    ElementMatchers.named("addPlayer").and(ElementMatchers.takesArguments(ServerPlayer.class))
                ))
                .visit(Advice.to(OnBossBarRemovePlayerAdvice.class).on(
                    ElementMatchers.named("removePlayer").and(ElementMatchers.takesArguments(ServerPlayer.class))
                ))
                .visit(Advice.to(OnBossBarRemoveAllAdvice.class).on(
                    ElementMatchers.named("removeAllPlayers").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnBossBarSetVisibleAdvice.class).on(
                    ElementMatchers.named("setVisible").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnBossBarSetProgressAdvice.class).on(
                    ElementMatchers.named("setProgress").and(ElementMatchers.takesArguments(float.class))
                ))
                .visit(Advice.to(OnBossBarSetNameAdvice.class).on(
                    ElementMatchers.named("setName").and(ElementMatchers.takesArguments(net.minecraft.network.chat.Component.class))
                ))
                .visit(Advice.to(OnBossBarSetStyleAdvice.class).on(
                    ElementMatchers.named("setColor").and(ElementMatchers.takesArguments(BossEvent.BossBarColor.class))
                        .or(ElementMatchers.named("setOverlay").and(ElementMatchers.takesArguments(BossEvent.BossBarOverlay.class)))
                ))
                .visit(Advice.to(OnBossBarSetFlagsAdvice.class).on(
                    ElementMatchers.named("setDarkenScreen").and(ElementMatchers.takesArguments(boolean.class))
                        .or(ElementMatchers.named("setPlayBossMusic").and(ElementMatchers.takesArguments(boolean.class)))
                        .or(ElementMatchers.named("setCreateWorldFog").and(ElementMatchers.takesArguments(boolean.class)))
                ))
            );

            LOGGER.info("[PatchBukkit] Registered PlayerBridge hooks with BridgePatcher");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register PlayerBridge hooks", t);
        }
    }

    // ---- Food / Saturation / Exhaustion ----

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

    // ---- GameMode / Exp / Abilities / Pose / Op ----

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

    // ---- Connection Info / Locale / Kick / Raw Message ----

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

    public static InetSocketAddress getAddressFromPumpkin(CraftPlayer player, InetSocketAddress fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return fallback;
        SYNCING.set(true);
        try {
            PlayerConnectionInfoResponse res = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null && !res.getAddress().isEmpty()) {
                int port = res.getPort() > 0 && res.getPort() <= 65535 ? res.getPort() : 0;
                return InetSocketAddress.createUnresolved(res.getAddress(), port);
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static String getClientBrandFromPumpkin(CraftPlayer player, String fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return fallback;
        SYNCING.set(true);
        try {
            PlayerConnectionInfoResponse res = NativeBridgeFfi.getPlayerConnectionInfo(BridgeUtils.convertUuid(sp.getUUID()));
            if (res != null && !res.getClientBrand().isEmpty()) {
                return res.getClientBrand();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushKickComponentIfDisconnected(CraftPlayer player, Component reason) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        boolean disconnected = sp.hasDisconnected()
            || sp.connection == null
            || !sp.connection.isAcceptingMessages();
        if (!disconnected) {
            return;
        }
        String legacy = reason != null
            ? LegacyComponentSerializer.legacySection().serialize(reason)
            : "Kicked from server";
        SYNCING.set(true);
        try {
            NativeBridgeFfi.kickPlayer(
                KickPlayerRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setMessage(legacy)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushRawMessageToPumpkin(CraftPlayer player, String message) {
        if (player == null || message == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
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

    // ---- Compass / Time / Weather / Sleep / Spectator / Plugin Messaging ----

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

    public static void syncPlayerTimeWeatherFromPumpkin(CraftPlayer player) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            GetPlayerTimeWeatherResponse res = NativeBridgeFfi.getPlayerTimeWeather(BridgeUtils.convertUuid(sp.getUUID()));
            if (res == null) return;
            if (res.getHasPlayerTime()) {
                player.setPlayerTime(res.getPlayerTime(), res.getTimeRelative());
            } else {
                player.resetPlayerTime();
            }
            if (res.getPlayerWeather() == 0) {
                player.setPlayerWeather(WeatherType.CLEAR);
            } else if (res.getPlayerWeather() == 1) {
                player.setPlayerWeather(WeatherType.DOWNFALL);
            } else {
                player.resetPlayerWeather();
            }
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

    public static void pushPlayerSleepToPumpkin(CraftHumanEntity human, Location loc, boolean wakeUp) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            PlayerSleepRequest.Builder req = PlayerSleepRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                .setWakeUp(wakeUp);
            if (!wakeUp && loc != null) {
                req.setX(loc.getBlockX()).setY(loc.getBlockY()).setZ(loc.getBlockZ());
            }
            NativeBridgeFfi.playerSleep(req.build());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSpectatorTargetToPumpkin(CraftPlayer player, Entity target) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            SetSpectatorTargetRequest.Builder req = SetSpectatorTargetRequest.newBuilder()
                .setPlayerUuid(BridgeUtils.convertUuid(sp.getUUID()));
            if (target != null) {
                req.setTargetUuid(BridgeUtils.convertUuid(target.getUniqueId()));
            }
            NativeBridgeFfi.setSpectatorTarget(req.build());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushPluginMessageToPumpkin(CraftPlayer player, String channel, byte[] message) {
        if (player == null || channel == null || message == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        if (sp.connection != null && sp.connection.connection != null && sp.connection.connection.channel != null) {
            return;
        }
        SYNCING.set(true);
        try {
            NativeBridgeFfi.sendPluginMessage(
                SendPluginMessageRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setChannel(channel)
                    .setData(ByteString.copyFrom(message))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Cooldowns ----

    public static int getCooldownFromPumpkin(CraftHumanEntity human, String itemGroup, int fallback) {
        if (human == null || itemGroup == null || itemGroup.isEmpty() || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return fallback;
        }
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return fallback;
        SYNCING.set(true);
        try {
            GetCooldownResponse res = NativeBridgeFfi.getCooldown(
                GetCooldownRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setItemGroup(itemGroup)
                    .build()
            );
            if (res != null) {
                if (res.getRemainingTicks() > 0) {
                    return res.getRemainingTicks();
                }
                return res.getIsOnCooldown() ? Math.max(1, fallback) : 0;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static boolean hasCooldownFromPumpkin(CraftHumanEntity human, String itemGroup, boolean fallback) {
        if (human == null || itemGroup == null || itemGroup.isEmpty() || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return fallback;
        }
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return fallback;
        SYNCING.set(true);
        try {
            GetCooldownResponse res = NativeBridgeFfi.getCooldown(
                GetCooldownRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setItemGroup(itemGroup)
                    .build()
            );
            if (res != null) {
                return res.getIsOnCooldown();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushCooldownToPumpkin(CraftHumanEntity human, String itemGroup, int ticks) {
        if (human == null || itemGroup == null || itemGroup.isEmpty() || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setCooldown(
                SetCooldownRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(handle.getUUID()))
                    .setItemGroup(itemGroup)
                    .setDurationTicks(ticks)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Titles / ActionBar / Display & Tablist Names ----

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

    public static void pushDisplayNameComponentToPumpkin(CraftPlayer player, Component name) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        String legacy = name != null ? LegacyComponentSerializer.legacySection().serialize(name) : player.getName();
        pushDisplayNameToPumpkin(player, legacy);
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

    public static void pushPlayerListNameComponentToPumpkin(CraftPlayer player, Component name) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        String legacy = name != null ? LegacyComponentSerializer.legacySection().serialize(name) : player.getName();
        pushPlayerListNameToPumpkin(player, legacy);
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

    // ---- Sounds / Particles / BlockChange / ResourcePacks / Respawn ----

    private static String extractSoundName(Holder<SoundEvent> holder) {
        if (holder == null) return null;
        if (holder.unwrapKey().isPresent()) {
            return holder.unwrapKey().get().identifier().toString();
        }
        if (holder.value() != null && holder.value().location() != null) {
            return holder.value().location().toString();
        }
        return null;
    }

    private static String soundSourceToCategory(SoundSource source) {
        return source != null ? source.name().toUpperCase(Locale.ROOT) : "MASTER";
    }

    public static void pushPlaySound0LocationToPumpkin(
        CraftPlayer player,
        Location loc,
        Holder<SoundEvent> sound,
        SoundSource source,
        float volume,
        float pitch,
        long seed
    ) {
        if (player == null || loc == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        String soundName = extractSoundName(sound);
        if (soundName == null || soundName.isEmpty()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.playerPlaySound(
                PlayerPlaySoundRequest.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(sp.getUUID()))
                    .setSound(
                        Sound.newBuilder()
                            .setName(soundName)
                            .setCategory(soundSourceToCategory(source))
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

    public static void pushPlaySound0EntityToPumpkin(
        CraftPlayer player,
        Entity entity,
        Holder<SoundEvent> sound,
        SoundSource source,
        float volume,
        float pitch,
        long seed
    ) {
        if (player == null || entity == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        String soundName = extractSoundName(sound);
        if (soundName == null || soundName.isEmpty()) return;
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
                            .setName(soundName)
                            .setCategory(soundSourceToCategory(source))
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
        SYNCING.set(true);
        try {
            byte[] encodedData = WorldBridge.encodeParticleData(particle, data);
            // Pass player UUID so Pumpkin delivers the particle ONLY to this player
            NativeBridgeFfi.spawnParticle(
                SpawnParticleRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(player.getUniqueId()))
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
                    .setData(ByteString.copyFrom(encodedData))
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

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    public static void pushResourcePackToPumpkin(
        CraftPlayer player,
        UUID packId,
        String url,
        byte[] hash,
        String prompt,
        boolean required
    ) {
        if (player == null || url == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            SendResourcePackRequest.Builder req = SendResourcePackRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                .setUrl(url)
                .setPrompt(prompt != null ? prompt : "")
                .setRequired(required)
                .setHash(bytesToHex(hash));
            if (packId != null) {
                req.setPackId(BridgeUtils.convertUuid(packId));
            }
            NativeBridgeFfi.sendResourcePack(req.build());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushRespawnLocationToPumpkin(CraftPlayer player, Location loc, boolean force) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        ServerPlayer sp = player.getHandle();
        if (sp == null) return;
        SYNCING.set(true);
        try {
            SetRespawnPointRequest.Builder req = SetRespawnPointRequest.newBuilder()
                .setUuid(BridgeUtils.convertUuid(sp.getUUID()))
                .setForce(force);
            if (loc == null) {
                req.setClear(true);
            } else {
                req.setClear(false)
                    .setPosition(
                        patchbukkit.common.Vec3.newBuilder()
                            .setX(loc.getX())
                            .setY(loc.getY())
                            .setZ(loc.getZ())
                            .build()
                    )
                    .setYaw(loc.getYaw())
                    .setPitch(loc.getPitch());
                if (loc.getWorld() != null) {
                    req.setWorldUuid(BridgeUtils.convertUuid(loc.getWorld().getUID()));
                }
            }
            NativeBridgeFfi.setRespawnPoint(req.build());
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

    public static Locale getAdventureLocaleFromPumpkin(CraftPlayer player, Locale fallback) {
        String locStr = getLocaleFromPumpkin(player, null);
        if (locStr == null || locStr.isEmpty()) return fallback;
        try {
            Locale parsed = Locale.forLanguageTag(locStr.replace('_', '-'));
            if (parsed != null && !parsed.getLanguage().isEmpty()) {
                return parsed;
            }
        } catch (Throwable ignored) {}
        return fallback;
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

    public static Location getRespawnLocationFromPumpkin(CraftHumanEntity human, Location fallback) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        net.minecraft.world.entity.player.Player handle = human.getHandle();
        if (handle == null) return fallback;
        SYNCING.set(true);
        try {
            patchbukkit.common.Location protoLoc = NativeBridgeFfi.getRespawnPoint(BridgeUtils.convertUuid(handle.getUUID()));
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
            NativeBridgeFfi.sendHealthUpdate(BridgeUtils.convertUuid(sp.getUUID()));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- BossBar ----

    private static int bossBarColorToProto(BossEvent.BossBarColor color) {
        if (color == null) return 6;
        return switch (color) {
            case PINK -> 0;
            case BLUE -> 1;
            case RED -> 2;
            case GREEN -> 3;
            case YELLOW -> 4;
            case PURPLE -> 5;
            case WHITE -> 6;
        };
    }

    private static int bossBarOverlayToProto(BossEvent.BossBarOverlay overlay) {
        if (overlay == null) return 0;
        return switch (overlay) {
            case NOTCHED_6 -> 1;
            case NOTCHED_10 -> 2;
            case NOTCHED_12 -> 3;
            case NOTCHED_20 -> 4;
            default -> 0;
        };
    }

    private static int bossBarFlagsToProto(ServerBossEvent bar) {
        int flags = 0;
        if (bar.shouldDarkenScreen()) flags |= 1;
        if (bar.shouldPlayBossMusic()) flags |= 2;
        if (bar.shouldCreateWorldFog()) flags |= 4;
        return flags;
    }

    private static String bossBarTitle(ServerBossEvent bar) {
        try {
            if (bar.getName() != null) {
                return LegacyComponentSerializer.legacySection().serialize(
                    io.papermc.paper.adventure.PaperAdventure.asAdventure(bar.getName())
                );
            }
        } catch (Throwable ignored) {}
        return "";
    }

    public static void pushBossBarForPlayer(ServerBossEvent bar, ServerPlayer player, int action) {
        if (bar == null || player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.updateBossBar(
                UpdateBossBarRequest.newBuilder()
                    .setPlayerUuid(BridgeUtils.convertUuid(player.getUUID()))
                    .setBossbarUuid(BridgeUtils.convertUuid(bar.getId()))
                    .setAction(action)
                    .setTitle(bossBarTitle(bar))
                    .setProgress(bar.getProgress())
                    .setColor(bossBarColorToProto(bar.getColor()))
                    .setDivision(bossBarOverlayToProto(bar.getOverlay()))
                    .setFlags(bossBarFlagsToProto(bar))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushBossBarToAllPlayers(ServerBossEvent bar, int action) {
        if (bar == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        if (action != 1 && !bar.isVisible()) return;
        for (ServerPlayer player : bar.getPlayers()) {
            pushBossBarForPlayer(bar, player, action);
        }
    }

    // ---- Advice classes ----

    public static final class OnPlayerGetHealthAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player) {
            EntityBridge.syncLivingHealthFromPumpkin(player);
        }
    }

    public static final class OnSetRealHealthAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) double health) {
            EntityBridge.pushLivingHealthToPumpkin(player, health);
        }
    }

    public static final class OnPlayerSetMaxHealthAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player) {
            EntityBridge.pushMaxHealthToPumpkin(player);
        }
    }

    public static final class OnPlayerSetRotationAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) float yaw, @Advice.Argument(1) float pitch) {
            EntityBridge.pushRotationToPumpkin(player, yaw, pitch);
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

    public static final class OnGetAddressAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) InetSocketAddress addr) {
            addr = PlayerBridge.getAddressFromPumpkin(player, addr);
        }
    }

    public static final class OnGetClientBrandAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) String brand) {
            brand = PlayerBridge.getClientBrandFromPumpkin(player, brand);
        }
    }

    public static final class OnKickComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component reason) {
            PlayerBridge.pushKickComponentIfDisconnected(player, reason);
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

    public static final class OnSyncPlayerTimeWeatherAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftPlayer player) {
            PlayerBridge.syncPlayerTimeWeatherFromPumpkin(player);
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

    public static final class OnGetCooldownKeyAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) Key key, @Advice.Return(readOnly = false) int cooldown) {
            if (key != null) {
                cooldown = PlayerBridge.getCooldownFromPumpkin(human, key.asString(), cooldown);
            }
        }
    }

    public static final class OnSetCooldownKeyAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) Key key, @Advice.Argument(1) int ticks) {
            if (key != null) {
                PlayerBridge.pushCooldownToPumpkin(human, key.asString(), ticks);
            }
        }
    }

    public static final class OnGetCooldownMaterialAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) Material material, @Advice.Return(readOnly = false) int cooldown) {
            if (material != null) {
                cooldown = PlayerBridge.getCooldownFromPumpkin(human, material.getKey().asString(), cooldown);
            }
        }
    }

    public static final class OnHasCooldownMaterialAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) Material material, @Advice.Return(readOnly = false) boolean has) {
            if (material != null) {
                has = PlayerBridge.hasCooldownFromPumpkin(human, material.getKey().asString(), has);
            }
        }
    }

    public static final class OnGetCooldownItemAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) ItemStack item, @Advice.Return(readOnly = false) int cooldown) {
            if (item != null) {
                cooldown = PlayerBridge.getCooldownFromPumpkin(human, item.getType().getKey().asString(), cooldown);
            }
        }
    }

    public static final class OnHasCooldownItemAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) ItemStack item, @Advice.Return(readOnly = false) boolean has) {
            if (item != null) {
                has = PlayerBridge.hasCooldownFromPumpkin(human, item.getType().getKey().asString(), has);
            }
        }
    }

    public static final class OnSetCooldownItemAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Argument(0) ItemStack item, @Advice.Argument(1) int ticks) {
            if (item != null) {
                PlayerBridge.pushCooldownToPumpkin(human, item.getType().getKey().asString(), ticks);
            }
        }
    }

    public static final class OnSleepAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftHumanEntity human,
            @Advice.Argument(0) Location loc,
            @Advice.Return boolean slept
        ) {
            if (slept) {
                PlayerBridge.pushPlayerSleepToPumpkin(human, loc, false);
            }
        }
    }

    public static final class OnWakeupAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human) {
            PlayerBridge.pushPlayerSleepToPumpkin(human, null, true);
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

    public static final class OnSetDisplayNameComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component name) {
            PlayerBridge.pushDisplayNameComponentToPumpkin(player, name);
        }
    }

    public static final class OnSetPlayerListNameAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String name) {
            PlayerBridge.pushPlayerListNameToPumpkin(player, name);
        }
    }

    public static final class OnSetPlayerListNameComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component name) {
            PlayerBridge.pushPlayerListNameComponentToPumpkin(player, name);
        }
    }

    public static final class OnSetPlayerListHeaderFooterAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) String header, @Advice.Argument(1) String footer) {
            PlayerBridge.pushPlayerListHeaderFooterToPumpkin(player, header, footer);
        }
    }

    public static final class OnSendPlayerListHeaderAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component header) {
            PlayerBridge.pushPlayerListHeaderFooterComponentToPumpkin(player, header, player.playerListFooter());
        }
    }

    public static final class OnSendPlayerListFooterAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component footer) {
            PlayerBridge.pushPlayerListHeaderFooterComponentToPumpkin(player, player.playerListHeader(), footer);
        }
    }

    public static final class OnSendPlayerListHeaderAndFooterAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Component header, @Advice.Argument(1) Component footer) {
            PlayerBridge.pushPlayerListHeaderFooterComponentToPumpkin(player, header, footer);
        }
    }

    public static final class OnPlaySound0LocationAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) Location loc,
            @Advice.Argument(1) Holder<SoundEvent> sound,
            @Advice.Argument(2) SoundSource source,
            @Advice.Argument(3) float volume,
            @Advice.Argument(4) float pitch,
            @Advice.Argument(5) long seed
        ) {
            PlayerBridge.pushPlaySound0LocationToPumpkin(player, loc, sound, source, volume, pitch, seed);
        }
    }

    public static final class OnPlaySound0EntityAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) Entity entity,
            @Advice.Argument(1) Holder<SoundEvent> sound,
            @Advice.Argument(2) SoundSource source,
            @Advice.Argument(3) float volume,
            @Advice.Argument(4) float pitch,
            @Advice.Argument(5) long seed
        ) {
            PlayerBridge.pushPlaySound0EntityToPumpkin(player, entity, sound, source, volume, pitch, seed);
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

    public static final class OnSetResourcePackComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) UUID id,
            @Advice.Argument(1) String url,
            @Advice.Argument(2) byte[] hash,
            @Advice.Argument(3) Component prompt,
            @Advice.Argument(4) boolean required
        ) {
            String promptStr = prompt != null ? LegacyComponentSerializer.legacySection().serialize(prompt) : "";
            PlayerBridge.pushResourcePackToPumpkin(player, id, url, hash, promptStr, required);
        }
    }

    public static final class OnSetResourcePackStringUuidAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) UUID id,
            @Advice.Argument(1) String url,
            @Advice.Argument(2) byte[] hash,
            @Advice.Argument(3) String prompt,
            @Advice.Argument(4) boolean required
        ) {
            PlayerBridge.pushResourcePackToPumpkin(player, id, url, hash, prompt, required);
        }
    }

    public static final class OnSetResourcePackLegacyAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(0) String url,
            @Advice.Argument(1) byte[] hash,
            @Advice.Argument(2) String prompt,
            @Advice.Argument(3) boolean required
        ) {
            PlayerBridge.pushResourcePackToPumpkin(player, null, url, hash, prompt, required);
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

    public static final class OnGetAdventureLocaleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Return(readOnly = false) Locale locale) {
            locale = PlayerBridge.getAdventureLocaleFromPumpkin(player, locale);
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

    public static final class OnGetPotentialRespawnLocationAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftHumanEntity human, @Advice.Return(readOnly = false) Location loc) {
            loc = PlayerBridge.getRespawnLocationFromPumpkin(human, loc);
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

    public static final class OnSetSpectatorTargetAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftPlayer player, @Advice.Argument(0) Entity target) {
            PlayerBridge.pushSpectatorTargetToPumpkin(player, target);
        }
    }

    public static final class OnSendPluginMessageAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftPlayer player,
            @Advice.Argument(1) String channel,
            @Advice.Argument(2) byte[] message
        ) {
            PlayerBridge.pushPluginMessageToPumpkin(player, channel, message);
        }
    }

    public static final class OnBossBarAddPlayerAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar, @Advice.Argument(0) ServerPlayer player) {
            if (bar.isVisible()) {
                PlayerBridge.pushBossBarForPlayer(bar, player, 0);
            }
        }
    }

    public static final class OnBossBarRemovePlayerAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar, @Advice.Argument(0) ServerPlayer player) {
            PlayerBridge.pushBossBarForPlayer(bar, player, 1);
        }
    }

    public static final class OnBossBarRemoveAllAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This ServerBossEvent bar) {
            PlayerBridge.pushBossBarToAllPlayers(bar, 1);
        }
    }

    public static final class OnBossBarSetVisibleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar, @Advice.Argument(0) boolean visible) {
            PlayerBridge.pushBossBarToAllPlayers(bar, visible ? 0 : 1);
        }
    }

    public static final class OnBossBarSetProgressAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar) {
            PlayerBridge.pushBossBarToAllPlayers(bar, 2);
        }
    }

    public static final class OnBossBarSetNameAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar) {
            PlayerBridge.pushBossBarToAllPlayers(bar, 3);
        }
    }

    public static final class OnBossBarSetStyleAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar) {
            PlayerBridge.pushBossBarToAllPlayers(bar, 4);
        }
    }

    public static final class OnBossBarSetFlagsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerBossEvent bar) {
            PlayerBridge.pushBossBarToAllPlayers(bar, 5);
        }
    }
}
