package org.patchbukkit.bootstrap;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.IpBanList;
import net.minecraft.server.players.IpBanListEntry;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.StoredUserEntry;
import net.minecraft.server.players.StoredUserList;
import net.minecraft.server.players.UserBanList;
import net.minecraft.server.players.UserBanListEntry;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.craftbukkit.CraftOfflinePlayer;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.ban.CraftIpBanList;
import org.bukkit.craftbukkit.ban.CraftProfileBanList;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.common.EmptyRequest;
import patchbukkit.server.BanEntryProto;
import patchbukkit.server.BroadcastMessageRequest;
import patchbukkit.server.CreateWorldRequest;
import patchbukkit.server.CreateWorldResponse;
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
 * Bridges authentic Paper {@link CraftServer}, {@link CraftOfflinePlayer}, ban lists, and
 * {@link ServerTickRateManager} reads and mutations to Pumpkin's server state via FFI.
 */
public final class ServerBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private static volatile Field PROFILE_BAN_LIST_FIELD;
    private static volatile Field IP_BAN_LIST_FIELD;
    private static volatile Field STORED_USER_MAP_FIELD;
    private static volatile Method GET_KEY_FOR_USER_METHOD;
    private static volatile Field SERVER_LEVEL_UUID_FIELD;

    private ServerBridge() {}

    public static void patch() {
        try {
            // 1. Hook CraftServer (composed with CommandBridge via BridgePatcher)
            BridgePatcher.register(CraftServer.class, "ServerBridge", builder -> builder
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
            );

            // 2. Hook CraftOfflinePlayer (isOp, setOp, isWhitelisted, setWhitelisted)
            BridgePatcher.register(CraftOfflinePlayer.class, "ServerBridge", builder -> builder
                .visit(Advice.to(OnOfflineIsOpAdvice.class).on(
                    ElementMatchers.named("isOp").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnOfflineSetOpAdvice.class).on(
                    ElementMatchers.named("setOp").and(ElementMatchers.takesArguments(boolean.class))
                ))
                .visit(Advice.to(OnOfflineIsWhitelistedAdvice.class).on(
                    ElementMatchers.named("isWhitelisted").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnOfflineSetWhitelistedAdvice.class).on(
                    ElementMatchers.named("setWhitelisted").and(ElementMatchers.takesArguments(boolean.class))
                ))
            );

            // 3. Hook CraftProfileBanList and CraftIpBanList
            BridgePatcher.register(CraftProfileBanList.class, "ServerBridge", builder -> builder
                .visit(Advice.to(OnProfileGetByNameAdvice.class).on(
                    ElementMatchers.named("getProfileByName").and(ElementMatchers.takesArguments(String.class))
                ))
                .visit(Advice.to(OnProfileBanSyncAdvice.class).on(
                    ElementMatchers.named("isBanned").and(ElementMatchers.takesArguments(NameAndId.class))
                        .or(ElementMatchers.named("getBanEntry").and(ElementMatchers.takesArguments(NameAndId.class)))
                        .or(ElementMatchers.named("getEntries").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnProfileAddBanAdvice.class).on(
                    ElementMatchers.named("addBan").and(
                        ElementMatchers.takesArguments(NameAndId.class, String.class, Date.class, String.class)
                    )
                ))
                .visit(Advice.to(OnProfilePardonAdvice.class).on(
                    ElementMatchers.named("pardon").and(ElementMatchers.takesArguments(NameAndId.class))
                ))
            );

            BridgePatcher.register(CraftIpBanList.class, "ServerBridge", builder -> builder
                .visit(Advice.to(OnIpBanSyncAdvice.class).on(
                    ElementMatchers.named("isBanned").and(ElementMatchers.takesArguments(String.class))
                        .or(ElementMatchers.named("getBanEntry").and(ElementMatchers.takesArguments(String.class)))
                        .or(ElementMatchers.named("getEntries").and(ElementMatchers.takesNoArguments()))
                ))
                .visit(Advice.to(OnIpAddBanAdvice.class).on(
                    ElementMatchers.named("addBan").and(
                        ElementMatchers.takesArguments(String.class, String.class, Date.class, String.class)
                    )
                ))
                .visit(Advice.to(OnIpPardonAdvice.class).on(
                    ElementMatchers.named("pardon").and(ElementMatchers.takesArguments(String.class))
                ))
            );

            // 4. Hook NMS ServerTickRateManager (used by CraftServerTickManager)
            BridgePatcher.register(ServerTickRateManager.class, "ServerBridge", builder -> builder
                .visit(Advice.to(OnPushTickRateAdvice.class).on(
                    ElementMatchers.named("setTickRate").and(ElementMatchers.takesArguments(float.class))
                        .or(ElementMatchers.named("setFrozen").and(ElementMatchers.takesArguments(boolean.class)))
                ))
            );

            // 5. Hook DedicatedServer and MinecraftServer shutdown/init in headless mode
            BridgePatcher.register(MinecraftServer.class, "ServerBridge", builder -> builder
                .visit(Advice.to(HaltAdvice.class).on(
                    ElementMatchers.named("halt")
                        .and(ElementMatchers.takesArguments(boolean.class, boolean.class))
                ))
            );
            BridgePatcher.register(DedicatedServer.class, "ServerBridge", builder -> builder
                .visit(Advice.to(StopServerAdvice.class).on(
                    ElementMatchers.named("stopServer")
                        .and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(InitServerAdvice.class).on(
                    ElementMatchers.named("initServer")
                        .and(ElementMatchers.takesNoArguments())
                ))
            );

            LOGGER.info("[PatchBukkit] Registered ServerBridge hooks with BridgePatcher");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register ServerBridge hooks", t);
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

    public static UUID pushCreateWorldToPumpkin(WorldCreator creator) {
        if (creator == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return null;
        SYNCING.set(true);
        try {
            String dim = switch (creator.environment()) {
                case NETHER -> "minecraft:the_nether";
                case THE_END -> "minecraft:the_end";
                default -> "minecraft:overworld";
            };
            CreateWorldResponse res = NativeBridgeFfi.createWorld(
                CreateWorldRequest.newBuilder()
                    .setName(creator.name())
                    .setDimension(dim)
                    .setSeed(creator.seed())
                    .build()
            );
            if (res != null && res.getSuccess() && res.hasWorldUuid()) {
                return BridgeUtils.convertUuid(res.getWorldUuid());
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return null;
    }

    public static void alignCreatedWorldUuid(World world, UUID pumpkinUuid) {
        if (world == null || pumpkinUuid == null || !(world instanceof CraftWorld cw)) return;
        try {
            ServerLevel level = cw.getHandle();
            if (level == null || pumpkinUuid.equals(level.uuid)) return;
            Field f = SERVER_LEVEL_UUID_FIELD;
            if (f == null) {
                f = ServerLevel.class.getDeclaredField("uuid");
                f.setAccessible(true);
                SERVER_LEVEL_UUID_FIELD = f;
            }
            f.set(level, pumpkinUuid);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[PatchBukkit] Could not align created world UUID to Pumpkin UUID", t);
        }
    }

    public static void pushUnloadWorldToPumpkin(World world, boolean save, boolean unloaded) {
        if (!unloaded || world == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
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
            if (res != null) {
                Set<OfflinePlayer> ops = new HashSet<>();
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
            if (res != null) {
                Set<OfflinePlayer> list = new HashSet<>();
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
            if (res != null) {
                Set<String> ips = new HashSet<>();
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
        pushIpBanEntryToPumpkin(ip, "Banned by operator", null, "Server", remove);
    }

    public static void pushIpBanEntryToPumpkin(String ip, String reason, Date expires, String source, boolean remove) {
        if (ip == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            BanEntryProto.Builder entry = BanEntryProto.newBuilder()
                .setTarget(ip)
                .setSource(source != null && !source.isEmpty() ? source : "Server")
                .setReason(reason != null ? reason : "Banned by an operator.");
            if (expires != null) {
                entry.setExpires(expires.getTime());
            }
            NativeBridgeFfi.setBanEntry(
                SetBanEntryRequest.newBuilder()
                    .setBanType("IP")
                    .setEntry(entry.build())
                    .setRemove(remove)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushProfileBanToPumpkin(NameAndId profile, String reason, Date expires, String source, boolean remove) {
        if (profile == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            String target = (profile.name() != null && !profile.name().isEmpty())
                ? profile.name()
                : (profile.id() != null ? profile.id().toString() : "");
            if (target.isEmpty()) return;
            BanEntryProto.Builder entry = BanEntryProto.newBuilder()
                .setTarget(target)
                .setSource(source != null && !source.isEmpty() ? source : "Server")
                .setReason(reason != null ? reason : "Banned by an operator.");
            if (expires != null) {
                entry.setExpires(expires.getTime());
            }
            NativeBridgeFfi.setBanEntry(
                SetBanEntryRequest.newBuilder()
                    .setBanType("NAME")
                    .setEntry(entry.build())
                    .setRemove(remove)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V extends StoredUserEntry<K>> void replaceStoredListEntries(
        StoredUserList<K, V> list,
        java.util.List<V> newEntries
    ) throws Exception {
        Field mapField = STORED_USER_MAP_FIELD;
        if (mapField == null) {
            mapField = StoredUserList.class.getDeclaredField("map");
            mapField.setAccessible(true);
            STORED_USER_MAP_FIELD = mapField;
        }
        Method keyMethod = GET_KEY_FOR_USER_METHOD;
        if (keyMethod == null) {
            keyMethod = StoredUserList.class.getDeclaredMethod("getKeyForUser", Object.class);
            keyMethod.setAccessible(true);
            GET_KEY_FOR_USER_METHOD = keyMethod;
        }
        Map<String, V> map = (Map<String, V>) mapField.get(list);
        if (map == null) return;
        map.clear();
        for (V entry : newEntries) {
            K user = entry.getUser();
            if (user != null) {
                String key = (String) keyMethod.invoke(list, user);
                if (key != null) {
                    map.put(key, entry);
                }
            }
        }
    }

    public static void syncProfileBanListFromPumpkin(CraftProfileBanList banList) {
        if (banList == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            GetBanListResponse res = NativeBridgeFfi.getBanList(
                GetBanListRequest.newBuilder().setBanType("NAME").build()
            );
            if (res == null) return;

            Field f = PROFILE_BAN_LIST_FIELD;
            if (f == null) {
                f = CraftProfileBanList.class.getDeclaredField("list");
                f.setAccessible(true);
                PROFILE_BAN_LIST_FIELD = f;
            }
            UserBanList userList = (UserBanList) f.get(banList);
            if (userList == null) return;

            java.util.List<UserBanListEntry> entries = new java.util.ArrayList<>(res.getEntriesCount());
            for (BanEntryProto proto : res.getEntriesList()) {
                String target = proto.getTarget();
                if (target.isEmpty()) continue;
                NameAndId nameAndId = resolveNameAndId(target);
                Date created = proto.getCreated() > 0 ? new Date(proto.getCreated()) : new Date();
                Date expires = proto.getExpires() > 0 ? new Date(proto.getExpires()) : null;
                String source = proto.getSource().isEmpty() ? "Server" : proto.getSource();
                String reason = proto.getReason().isEmpty() ? null : proto.getReason();
                entries.add(new UserBanListEntry(nameAndId, created, source, expires, reason));
            }
            replaceStoredListEntries(userList, entries);
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    private static NameAndId resolveNameAndId(String target) {
        try {
            UUID parsed = UUID.fromString(target);
            OfflinePlayer op = Bukkit.getOfflinePlayer(parsed);
            String name = op.getName() != null ? op.getName() : target;
            return new NameAndId(parsed, name);
        } catch (IllegalArgumentException ignored) {}
        try {
            org.bukkit.entity.Player online = Bukkit.getPlayerExact(target);
            if (online != null) {
                return new NameAndId(online.getUniqueId(), online.getName());
            }
            OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(target);
            if (cached != null && cached.getUniqueId() != null) {
                return new NameAndId(cached.getUniqueId(), cached.getName() != null ? cached.getName() : target);
            }
        } catch (Throwable ignored) {}
        return NameAndId.createOffline(target);
    }

    public static void syncIpBanListFromPumpkin(CraftIpBanList banList) {
        if (banList == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        SYNCING.set(true);
        try {
            GetBanListResponse res = NativeBridgeFfi.getBanList(
                GetBanListRequest.newBuilder().setBanType("IP").build()
            );
            if (res == null) return;

            Field f = IP_BAN_LIST_FIELD;
            if (f == null) {
                f = CraftIpBanList.class.getDeclaredField("list");
                f.setAccessible(true);
                IP_BAN_LIST_FIELD = f;
            }
            IpBanList ipList = (IpBanList) f.get(banList);
            if (ipList == null) return;

            java.util.List<IpBanListEntry> entries = new java.util.ArrayList<>(res.getEntriesCount());
            for (BanEntryProto proto : res.getEntriesList()) {
                String target = proto.getTarget();
                if (target.isEmpty()) continue;
                Date created = proto.getCreated() > 0 ? new Date(proto.getCreated()) : new Date();
                Date expires = proto.getExpires() > 0 ? new Date(proto.getExpires()) : null;
                String source = proto.getSource().isEmpty() ? "Server" : proto.getSource();
                String reason = proto.getReason().isEmpty() ? null : proto.getReason();
                entries.add(new IpBanListEntry(target, created, source, expires, reason));
            }
            replaceStoredListEntries(ipList, entries);
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static boolean isOfflineOpFromPumpkin(CraftOfflinePlayer player, boolean fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetOperatorsResponse res = NativeBridgeFfi.getOperators(EmptyRequest.getDefaultInstance());
            if (res != null) {
                UUID targetUuid = player.getUniqueId();
                String targetName = player.getName();
                for (OperatorEntryProto entry : res.getOperatorsList()) {
                    if (entry.hasUuid() && targetUuid.equals(BridgeUtils.convertUuid(entry.getUuid()))) {
                        return true;
                    }
                    if (targetName != null && !entry.getName().isEmpty() && targetName.equalsIgnoreCase(entry.getName())) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static boolean isOfflineWhitelistedFromPumpkin(CraftOfflinePlayer player, boolean fallback) {
        if (player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        SYNCING.set(true);
        try {
            GetWhitelistResponse res = NativeBridgeFfi.getWhitelist(EmptyRequest.getDefaultInstance());
            if (res != null) {
                UUID targetUuid = player.getUniqueId();
                String targetName = player.getName();
                for (WhitelistEntryProto entry : res.getPlayersList()) {
                    if (entry.hasUuid() && targetUuid.equals(BridgeUtils.convertUuid(entry.getUuid()))) {
                        return true;
                    }
                    if (targetName != null && !entry.getName().isEmpty() && targetName.equalsIgnoreCase(entry.getName())) {
                        return true;
                    }
                }
                return false;
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
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

    // ---- Advice classes ----

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

    public static final class OnCreateWorldAdvice {
        @Advice.OnMethodEnter
        public static UUID onEnter(@Advice.Argument(0) WorldCreator creator) {
            return ServerBridge.pushCreateWorldToPumpkin(creator);
        }

        @Advice.OnMethodExit
        public static void onExit(@Advice.Enter UUID pumpkinUuid, @Advice.Return World world) {
            ServerBridge.alignCreatedWorldUuid(world, pumpkinUuid);
        }
    }

    public static final class OnUnloadWorldAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) World world,
            @Advice.Argument(1) boolean save,
            @Advice.Return boolean unloaded
        ) {
            ServerBridge.pushUnloadWorldToPumpkin(world, save, unloaded);
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

    public static final class OnOfflineIsOpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftOfflinePlayer player, @Advice.Return(readOnly = false) boolean op) {
            op = ServerBridge.isOfflineOpFromPumpkin(player, op);
        }
    }

    public static final class OnOfflineSetOpAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftOfflinePlayer player, @Advice.Argument(0) boolean isOp) {
            ServerBridge.pushOfflineSetOpToPumpkin(player, isOp);
        }
    }

    public static final class OnOfflineIsWhitelistedAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftOfflinePlayer player, @Advice.Return(readOnly = false) boolean whitelisted) {
            whitelisted = ServerBridge.isOfflineWhitelistedFromPumpkin(player, whitelisted);
        }
    }

    public static final class OnOfflineSetWhitelistedAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftOfflinePlayer player, @Advice.Argument(0) boolean whitelisted) {
            ServerBridge.pushOfflineSetWhitelistedToPumpkin(player, whitelisted);
        }
    }

    public static final class OnProfileGetByNameAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) String name,
            @Advice.Return(readOnly = false) NameAndId profile
        ) {
            if (profile == null && name != null && !name.isEmpty()) {
                profile = NameAndId.createOffline(name);
            }
        }
    }

    public static final class OnProfileBanSyncAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftProfileBanList banList) {
            ServerBridge.syncProfileBanListFromPumpkin(banList);
        }
    }

    public static final class OnProfileAddBanAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) NameAndId profile,
            @Advice.Argument(1) String reason,
            @Advice.Argument(2) Date expires,
            @Advice.Argument(3) String source
        ) {
            ServerBridge.pushProfileBanToPumpkin(profile, reason, expires, source, false);
        }
    }

    public static final class OnProfilePardonAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) NameAndId profile) {
            ServerBridge.pushProfileBanToPumpkin(profile, null, null, null, true);
        }
    }

    public static final class OnIpBanSyncAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftIpBanList banList) {
            ServerBridge.syncIpBanListFromPumpkin(banList);
        }
    }

    public static final class OnIpAddBanAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) String target,
            @Advice.Argument(1) String reason,
            @Advice.Argument(2) Date expires,
            @Advice.Argument(3) String source
        ) {
            ServerBridge.pushIpBanEntryToPumpkin(target, reason, expires, source, false);
        }
    }

    public static final class OnIpPardonAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.Argument(0) String target) {
            ServerBridge.pushIpBanEntryToPumpkin(target, null, null, null, true);
        }
    }

    public static final class HaltAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(@Advice.This MinecraftServer server) {
            server.hasFullyShutdown = true;
            return true;
        }
    }

    public static final class StopServerAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(@Advice.This DedicatedServer server) {
            server.hasFullyShutdown = true;
            return true;
        }
    }

    public static final class InitServerAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter() {
            return true;
        }

        @Advice.OnMethodExit
        public static void onExit(@Advice.Return(readOnly = false) boolean ret) {
            ret = false;
        }
    }
}
