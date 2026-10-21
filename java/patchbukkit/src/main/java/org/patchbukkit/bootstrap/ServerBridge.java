package org.patchbukkit.bootstrap;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.ServerTickRateManager;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftOfflinePlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.common.EmptyRequest;
import patchbukkit.server.BanEntryProto;
import patchbukkit.server.BroadcastMessageRequest;
import patchbukkit.server.CreateWorldRequest;
import patchbukkit.server.GetBanListRequest;
import patchbukkit.server.GetBanListResponse;
import patchbukkit.server.GetOperatorsResponse;
import patchbukkit.server.GetWhitelistResponse;
import patchbukkit.server.OperatorEntryProto;
import patchbukkit.server.ServerInfoResponse;
import patchbukkit.server.ServerTickInfoResponse;
import patchbukkit.server.SetBanEntryRequest;
import patchbukkit.server.SetOperatorRequest;
import patchbukkit.server.SetServerDefaultGamemodeRequest;
import patchbukkit.server.SetServerIdleTimeoutRequest;
import patchbukkit.server.SetServerMaxPlayersRequest;
import patchbukkit.server.SetServerMotdRequest;
import patchbukkit.server.SetServerTickRateRequest;
import patchbukkit.server.SetServerWhitelistEnforcedRequest;
import patchbukkit.server.SetServerWhitelistRequest;
import patchbukkit.server.SetWhitelistPlayerRequest;
import patchbukkit.server.ShutdownServerRequest;
import patchbukkit.server.UnloadWorldRequest;
import patchbukkit.server.WhitelistEntryProto;

/**
 * Bridges authentic Paper {@link CraftServer} and {@link ServerTickRateManager}
 * reads and mutations to Pumpkin's server state via FFI.
 */
public final class ServerBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private ServerBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            ByteBuddy byteBuddy = new ByteBuddy();

            // 1. Hook CraftServer
            try {
                byteBuddy
                    .redefine(CraftServer.class)
                    .visit(Advice.to(OnSyncServerInfoAdvice.class).on(
                        ElementMatchers.named("getMotd")
                            .or(ElementMatchers.named("motd").and(ElementMatchers.takesNoArguments()))
                            .or(ElementMatchers.named("getMaxPlayers"))
                            .or(ElementMatchers.named("hasWhitelist"))
                            .or(ElementMatchers.named("isWhitelistEnforced"))
                            .or(ElementMatchers.named("getIdleTimeout"))
                            .or(ElementMatchers.named("getDefaultGameMode"))
                    ))
                    .visit(Advice.to(OnSetMotdAdvice.class).on(
                        ElementMatchers.named("setMotd").and(ElementMatchers.takesArguments(String.class))
                    ))
                    .visit(Advice.to(OnSetMotdComponentAdvice.class).on(
                        ElementMatchers.named("motd").and(ElementMatchers.takesArguments(Component.class))
                    ))
                    .visit(Advice.to(OnSetMaxPlayersAdvice.class).on(
                        ElementMatchers.named("setMaxPlayers").and(ElementMatchers.takesArguments(int.class))
                    ))
                    .visit(Advice.to(OnSetWhitelistAdvice.class).on(
                        ElementMatchers.named("setWhitelist").and(ElementMatchers.takesArguments(boolean.class))
                    ))
                    .visit(Advice.to(OnSetWhitelistEnforcedAdvice.class).on(
                        ElementMatchers.named("setWhitelistEnforced").and(ElementMatchers.takesArguments(boolean.class))
                    ))
                    .visit(Advice.to(OnSetIdleTimeoutAdvice.class).on(
                        ElementMatchers.named("setIdleTimeout").and(ElementMatchers.takesArguments(int.class))
                    ))
                    .visit(Advice.to(OnSetDefaultGameModeAdvice.class).on(
                        ElementMatchers.named("setDefaultGameMode").and(ElementMatchers.takesArguments(GameMode.class))
                    ))
                    .visit(Advice.to(OnBroadcastAdvice.class).on(
                        ElementMatchers.named("broadcast").and(ElementMatchers.takesArguments(Component.class, String.class))
                    ))
                    .visit(Advice.to(OnGetTpsAdvice.class).on(
                        ElementMatchers.named("getTPS").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnGetAverageTickTimeAdvice.class).on(
                        ElementMatchers.named("getAverageTickTime").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnSavePlayersAdvice.class).on(
                        ElementMatchers.named("savePlayers").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnShutdownAdvice.class).on(
                        ElementMatchers.named("shutdown").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnCreateWorldAdvice.class).on(
                        ElementMatchers.named("createWorld").and(ElementMatchers.takesArguments(WorldCreator.class))
                    ))
                    .visit(Advice.to(OnUnloadWorldAdvice.class).on(
                        ElementMatchers.named("unloadWorld").and(ElementMatchers.takesArguments(World.class, boolean.class))
                    ))
                    .visit(Advice.to(OnGetOperatorsAdvice.class).on(
                        ElementMatchers.named("getOperators").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnGetWhitelistedPlayersAdvice.class).on(
                        ElementMatchers.named("getWhitelistedPlayers").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnGetIpBansAdvice.class).on(
                        ElementMatchers.named("getIPBans").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnBanIpAdvice.class).on(
                        ElementMatchers.named("banIP").and(ElementMatchers.takesArguments(String.class))
                    ))
                    .visit(Advice.to(OnUnbanIpAdvice.class).on(
                        ElementMatchers.named("unbanIP").and(ElementMatchers.takesArguments(String.class))
                    ))
                    .make()
                    .load(CraftServer.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftServer in ServerBridge", t);
            }

            // 2. Hook CraftOfflinePlayer (setOp, setWhitelisted)
            try {
                byteBuddy
                    .redefine(CraftOfflinePlayer.class)
                    .visit(Advice.to(OnOfflineSetOpAdvice.class).on(
                        ElementMatchers.named("setOp").and(ElementMatchers.takesArguments(boolean.class))
                    ))
                    .visit(Advice.to(OnOfflineSetWhitelistedAdvice.class).on(
                        ElementMatchers.named("setWhitelisted").and(ElementMatchers.takesArguments(boolean.class))
                    ))
                    .make()
                    .load(CraftOfflinePlayer.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftOfflinePlayer in ServerBridge", t);
            }

            // 2. Hook NMS ServerTickRateManager (used by CraftServerTickManager)
            try {
                byteBuddy
                    .redefine(ServerTickRateManager.class)
                    .visit(Advice.to(OnPushTickRateAdvice.class).on(
                        ElementMatchers.named("setTickRate").and(ElementMatchers.takesArguments(float.class))
                            .or(ElementMatchers.named("setFrozen").and(ElementMatchers.takesArguments(boolean.class)))
                    ))
                    .make()
                    .load(ServerTickRateManager.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook ServerTickRateManager in ServerBridge", t);
            }

            LOGGER.info("[PatchBukkit] Successfully patched CraftServer and ServerTickRateManager with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to install ServerBridge hooks", t);
        }
    }

    public static void syncServerInfoFromPumpkin(CraftServer server) {
        if (server == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            ServerInfoResponse res = NativeBridgeFfi.getServerInfo(EmptyRequest.getDefaultInstance());
            if (res != null) {
                if (!res.getMotd().isEmpty()) {
                    server.setMotd(res.getMotd());
                }
                if (res.getMaxPlayers() > 0) {
                    server.setMaxPlayers(res.getMaxPlayers());
                }
                server.setWhitelist(res.getHasWhitelist());
                server.setWhitelistEnforced(res.getIsWhitelistEnforced());
                server.setIdleTimeout(res.getIdleTimeout());
                if (!res.getDefaultGamemode().isEmpty()) {
                    try {
                        GameMode gm = GameMode.valueOf(res.getDefaultGamemode().toUpperCase(java.util.Locale.ROOT));
                        server.setDefaultGameMode(gm);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushMotdToPumpkin(String motd) {
        if (motd == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerMotd(
                SetServerMotdRequest.newBuilder().setMotd(motd).build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushMotdComponentToPumpkin(Component motd) {
        if (motd == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        pushMotdToPumpkin(LegacyComponentSerializer.legacySection().serialize(motd));
    }

    public static void pushMaxPlayersToPumpkin(int maxPlayers) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerMaxPlayers(
                SetServerMaxPlayersRequest.newBuilder().setMaxPlayers(maxPlayers).build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWhitelistToPumpkin(boolean enabled) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerWhitelist(
                SetServerWhitelistRequest.newBuilder().setEnabled(enabled).build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushWhitelistEnforcedToPumpkin(boolean enforced) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerWhitelistEnforced(
                SetServerWhitelistEnforcedRequest.newBuilder().setEnforced(enforced).build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushIdleTimeoutToPumpkin(int timeoutMinutes) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerIdleTimeout(
                SetServerIdleTimeoutRequest.newBuilder().setTimeoutMinutes(timeoutMinutes).build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushDefaultGameModeToPumpkin(GameMode mode) {
        if (mode == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerDefaultGamemode(
                SetServerDefaultGamemodeRequest.newBuilder().setGamemode(mode.name()).build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushBroadcastToPumpkin(Component message, String permission) {
        if (message == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            String legacy = LegacyComponentSerializer.legacySection().serialize(message);
            NativeBridgeFfi.broadcastMessage(
                BroadcastMessageRequest.newBuilder()
                    .setMessage(legacy)
                    .setPermission(permission != null ? permission : "")
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static double[] getTpsFromPumpkin(double[] fallback) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            ServerTickInfoResponse res = NativeBridgeFfi.getServerTickInfo(EmptyRequest.getDefaultInstance());
            if (res != null && res.getTpsCount() >= 3) {
                return new double[] { res.getTps(0), res.getTps(1), res.getTps(2) };
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static double getAverageTickTimeFromPumpkin(double fallback) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            ServerTickInfoResponse res = NativeBridgeFfi.getServerTickInfo(EmptyRequest.getDefaultInstance());
            if (res != null && res.getAverageTickTime() > 0.0) {
                return res.getAverageTickTime();
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushSaveAllWorldsToPumpkin() {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.saveAllWorlds(EmptyRequest.getDefaultInstance());
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushShutdownToPumpkin() {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.shutdownServer(
                ShutdownServerRequest.newBuilder().setSave(true).setMessage("Server shutting down").build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushTickRateToPumpkin(ServerTickRateManager manager) {
        if (manager == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setServerTickRate(
                SetServerTickRateRequest.newBuilder()
                    .setTickRate(manager.tickrate())
                    .setFrozen(manager.isFrozen())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnSyncServerInfoAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftServer server) {
            ServerBridge.syncServerInfoFromPumpkin(server);
        }
    }

    public static final class OnSetMotdAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) String motd) {
            ServerBridge.pushMotdToPumpkin(motd);
        }
    }

    public static final class OnSetMotdComponentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) Component motd) {
            ServerBridge.pushMotdComponentToPumpkin(motd);
        }
    }

    public static final class OnSetMaxPlayersAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) int maxPlayers) {
            ServerBridge.pushMaxPlayersToPumpkin(maxPlayers);
        }
    }

    public static final class OnSetWhitelistAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) boolean enabled) {
            ServerBridge.pushWhitelistToPumpkin(enabled);
        }
    }

    public static final class OnSetWhitelistEnforcedAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) boolean enforced) {
            ServerBridge.pushWhitelistEnforcedToPumpkin(enforced);
        }
    }

    public static final class OnSetIdleTimeoutAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) int timeout) {
            ServerBridge.pushIdleTimeoutToPumpkin(timeout);
        }
    }

    public static final class OnSetDefaultGameModeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) GameMode mode) {
            ServerBridge.pushDefaultGameModeToPumpkin(mode);
        }
    }

    public static final class OnBroadcastAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) Component message, @Advice.Argument(1) String permission) {
            ServerBridge.pushBroadcastToPumpkin(message, permission);
        }
    }

    public static final class OnGetTpsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Return(readOnly = false) double[] tps) {
            tps = ServerBridge.getTpsFromPumpkin(tps);
        }
    }

    public static final class OnGetAverageTickTimeAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Return(readOnly = false) double avg) {
            avg = ServerBridge.getAverageTickTimeFromPumpkin(avg);
        }
    }

    public static final class OnSavePlayersAdvice {
        @Advice.OnMethodExit
        public static void onExit() {
            ServerBridge.pushSaveAllWorldsToPumpkin();
        }
    }

    public static final class OnShutdownAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter() {
            ServerBridge.pushShutdownToPumpkin();
            return NativeFastPathFfi.isInitialized();
        }
    }

    public static final class OnPushTickRateAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This ServerTickRateManager manager) {
            ServerBridge.pushTickRateToPumpkin(manager);
        }
    }

    public static void pushCreateWorldToPumpkin(WorldCreator creator) {
        if (creator == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            String dim = switch (creator.environment()) {
                case NETHER -> "minecraft:the_nether";
                case THE_END -> "minecraft:the_end";
                default -> "minecraft:overworld";
            };
            NativeBridgeFfi.createWorld(
                CreateWorldRequest.newBuilder()
                    .setName(creator.name())
                    .setDimension(dim)
                    .setSeed(creator.seed())
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushUnloadWorldToPumpkin(World world, boolean save) {
        if (world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.unloadWorld(
                UnloadWorldRequest.newBuilder()
                    .setWorldUuid(BridgeUtils.convertUuid(world.getUID()))
                    .setWorldName(world.getName())
                    .setSave(save)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static Set<OfflinePlayer> getOperatorsFromPumpkin(CraftServer server, Set<OfflinePlayer> fallback) {
        if (server == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetOperatorsResponse res = NativeBridgeFfi.getOperators(EmptyRequest.getDefaultInstance());
            if (res != null && res.getOperatorsCount() > 0) {
                Set<OfflinePlayer> ops = new java.util.HashSet<>(fallback);
                for (OperatorEntryProto entry : res.getOperatorsList()) {
                    if (entry.hasUuid()) {
                        ops.add(server.getOfflinePlayer(BridgeUtils.convertUuid(entry.getUuid())));
                    }
                }
                return ops;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static Set<OfflinePlayer> getWhitelistedFromPumpkin(CraftServer server, Set<OfflinePlayer> fallback) {
        if (server == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWhitelistResponse res = NativeBridgeFfi.getWhitelist(EmptyRequest.getDefaultInstance());
            if (res != null && res.getPlayersCount() > 0) {
                Set<OfflinePlayer> list = new java.util.HashSet<>(fallback);
                for (WhitelistEntryProto entry : res.getPlayersList()) {
                    if (entry.hasUuid()) {
                        list.add(server.getOfflinePlayer(BridgeUtils.convertUuid(entry.getUuid())));
                    }
                }
                return list;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static Set<String> getIpBansFromPumpkin(Set<String> fallback) {
        if (!NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetBanListResponse res = NativeBridgeFfi.getBanList(
                GetBanListRequest.newBuilder().setBanType("IP").build()
            );
            if (res != null && res.getEntriesCount() > 0) {
                Set<String> ips = new java.util.HashSet<>(fallback);
                for (BanEntryProto entry : res.getEntriesList()) {
                    if (!entry.getTarget().isEmpty()) {
                        ips.add(entry.getTarget());
                    }
                }
                return ips;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushIpBanToPumpkin(String ip, boolean remove) {
        if (ip == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setBanEntry(
                SetBanEntryRequest.newBuilder()
                    .setBanType("IP")
                    .setEntry(BanEntryProto.newBuilder().setTarget(ip).setReason("Banned by operator").build())
                    .setRemove(remove)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushOfflineSetOpToPumpkin(CraftOfflinePlayer player, boolean isOp) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            UUID uuid = player.getUniqueId();
            String name = player.getName() != null ? player.getName() : "";
            NativeBridgeFfi.setOperator(
                SetOperatorRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(uuid))
                    .setName(name)
                    .setLevel(4)
                    .setIsOp(isOp)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushOfflineSetWhitelistedToPumpkin(CraftOfflinePlayer player, boolean whitelisted) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            UUID uuid = player.getUniqueId();
            String name = player.getName() != null ? player.getName() : "";
            NativeBridgeFfi.setWhitelistPlayer(
                SetWhitelistPlayerRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(uuid))
                    .setName(name)
                    .setWhitelisted(whitelisted)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnCreateWorldAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.Argument(0) WorldCreator creator) {
            ServerBridge.pushCreateWorldToPumpkin(creator);
        }
    }

    public static final class OnUnloadWorldAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) World world, @Advice.Argument(1) boolean save) {
            ServerBridge.pushUnloadWorldToPumpkin(world, save);
        }
    }

    public static final class OnGetOperatorsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftServer server, @Advice.Return(readOnly = false) Set<OfflinePlayer> ops) {
            ops = ServerBridge.getOperatorsFromPumpkin(server, ops);
        }
    }

    public static final class OnGetWhitelistedPlayersAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftServer server, @Advice.Return(readOnly = false) Set<OfflinePlayer> list) {
            list = ServerBridge.getWhitelistedFromPumpkin(server, list);
        }
    }

    public static final class OnGetIpBansAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Return(readOnly = false) Set<String> ips) {
            ips = ServerBridge.getIpBansFromPumpkin(ips);
        }
    }

    public static final class OnBanIpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) String ip) {
            ServerBridge.pushIpBanToPumpkin(ip, false);
        }
    }

    public static final class OnUnbanIpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) String ip) {
            ServerBridge.pushIpBanToPumpkin(ip, true);
        }
    }

    public static final class OnOfflineSetOpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftOfflinePlayer player, @Advice.Argument(0) boolean isOp) {
            ServerBridge.pushOfflineSetOpToPumpkin(player, isOp);
        }
    }

    public static final class OnOfflineSetWhitelistedAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftOfflinePlayer player, @Advice.Argument(0) boolean whitelisted) {
            ServerBridge.pushOfflineSetWhitelistedToPumpkin(player, whitelisted);
        }
    }
}
