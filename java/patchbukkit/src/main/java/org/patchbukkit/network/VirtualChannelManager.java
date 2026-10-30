package org.patchbukkit.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the lifecycle and I/O of virtual Netty channels and authentic Paper
 * {@link net.minecraft.server.level.ServerPlayer} instances for connected players.
 */
public final class VirtualChannelManager {

    private static final Logger LOGGER = Logger.getLogger("VirtualChannelManager");
    private static final VirtualChannelManager INSTANCE = new VirtualChannelManager();

    private final Map<UUID, PatchBukkitVirtualChannel> channels = new ConcurrentHashMap<>();
    private final Map<UUID, net.minecraft.server.level.ServerPlayer> serverPlayers = new ConcurrentHashMap<>();

    private static final java.util.concurrent.atomic.AtomicLong INBOUND_COUNT = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong OUTBOUND_COUNT = new java.util.concurrent.atomic.AtomicLong();

    private VirtualChannelManager() {}

    public static VirtualChannelManager getInstance() {
        return INSTANCE;
    }

    public PatchBukkitVirtualChannel getOrCreateChannel(UUID uuid) {
        if (uuid == null) return null;
        return channels.computeIfAbsent(uuid, PatchBukkitVirtualChannel::new);
    }

    /**
     * Entrypoint invoked by Rust's {@code EventManager::register_player} over JNI.
     */
    public static void registerPlayer(String uuidStr, String name, boolean isOp, int entityId) {
        try {
            UUID uuid = UUID.fromString(uuidStr);
            net.minecraft.server.level.ServerPlayer sp =
                INSTANCE.getOrCreateServerPlayer(uuid, name != null ? name : "Player", entityId);
            if (sp != null) {
                try {
                    org.patchbukkit.bootstrap.PlayerBridge.applyOpFromPumpkin(sp.getBukkitEntity(), isOp);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Failed to register player: " + name, t);
        }
    }

    public net.minecraft.server.level.ServerPlayer getOrCreateServerPlayer(UUID uuid, String name) {
        return getOrCreateServerPlayer(uuid, name, -1);
    }

    public synchronized net.minecraft.server.level.ServerPlayer getOrCreateServerPlayer(
        UUID uuid,
        String name,
        int entityId
    ) {
        if (uuid == null) return null;
        net.minecraft.server.level.ServerPlayer existing = serverPlayers.get(uuid);
        if (existing != null) {
            if (entityId > 0 && existing.getId() != entityId) {
                existing.setId(entityId);
                PatchBukkitVirtualChannel ch = channels.get(uuid);
                if (ch != null && org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    ch.setSuppressOutboundBridge(true);
                    try {
                        net.minecraft.server.dedicated.DedicatedServer ds = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
                        net.minecraft.server.level.ServerLevel lvl = ds.getLevel(net.minecraft.world.level.Level.OVERWORLD);
                        ch.writeAndFlush(new net.minecraft.network.protocol.game.ClientboundLoginPacket(
                                existing.getId(),
                                lvl.getLevelData().isHardcore(),
                                ds.levelKeys(),
                                ds.getMaxPlayers(),
                                10,
                                10,
                                false,
                                true,
                                false,
                                existing.createCommonSpawnInfo(lvl),
                                ds.usesAuthentication(),
                                ds.enforceSecureProfile()
                        ));
                    } catch (Throwable ignored) {
                    } finally {
                        ch.setSuppressOutboundBridge(false);
                    }
                }
            }
            return existing;
        }

        if (!org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            return null;
        }

        try {
            net.minecraft.server.dedicated.DedicatedServer dedicatedServer = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            net.minecraft.server.level.ServerLevel level = dedicatedServer.getLevel(net.minecraft.world.level.Level.OVERWORLD);
            String rawName = name != null && !name.isEmpty() ? name : "Player";
            if (rawName.length() > 16) {
                rawName = rawName.substring(0, 16);
            }
            com.mojang.authlib.GameProfile profile =
                    new com.mojang.authlib.GameProfile(uuid, rawName);

            net.minecraft.server.level.ServerPlayer serverPlayer =
                    new net.minecraft.server.level.ServerPlayer(
                            dedicatedServer,
                            level,
                            profile,
                            net.minecraft.server.level.ClientInformation.createDefault()
                    );
            if (entityId > 0) {
                serverPlayer.setId(entityId);
            }

            PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);
            net.minecraft.network.Connection connection =
                    new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            connection.channel = channel;
            connection.address = new java.net.InetSocketAddress("127.0.0.1", 54321);

            net.minecraft.server.network.CommonListenerCookie cookie =
                    net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
            net.minecraft.server.network.ServerGamePacketListenerImpl listener =
                    new net.minecraft.server.network.ServerGamePacketListenerImpl(dedicatedServer, connection, serverPlayer, cookie);
            serverPlayer.connection = listener;

            net.minecraft.network.ProtocolInfo<net.minecraft.network.protocol.game.ClientGamePacketListener> outboundProtocol = getClientboundProtocol();
            if (outboundProtocol == null) {
                outboundProtocol = net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                        net.minecraft.network.RegistryFriendlyByteBuf.decorator(dedicatedServer.registryAccess())
                );
                clientboundProtocol = outboundProtocol;
            }
            try {
                java.lang.reflect.Field plField = net.minecraft.network.Connection.class.getDeclaredField("packetListener");
                plField.setAccessible(true);
                plField.set(connection, listener);
            } catch (Throwable ignored) {}

            if (dedicatedServer.getConnection() != null && dedicatedServer.getConnection().getConnections() != null) {
                dedicatedServer.getConnection().getConnections().add(connection);
            }

            net.minecraft.server.dedicated.DedicatedPlayerList pl =
                    (net.minecraft.server.dedicated.DedicatedPlayerList) dedicatedServer.getPlayerList();
            if (pl != null) {
                if (pl.getPlayers() != null && !pl.getPlayers().contains(serverPlayer)) {
                    pl.getPlayers().add(serverPlayer);
                }
                try {
                    java.lang.reflect.Field byUuidField = null;
                    Class<?> curPl = pl.getClass();
                    while (curPl != null && byUuidField == null) {
                        for (java.lang.reflect.Field f : curPl.getDeclaredFields()) {
                            if (f.getName().equals("playersByUUID")) {
                                byUuidField = f;
                                break;
                            }
                        }
                        curPl = curPl.getSuperclass();
                    }
                    if (byUuidField != null) {
                        byUuidField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<UUID, net.minecraft.server.level.ServerPlayer> map =
                                (Map<UUID, net.minecraft.server.level.ServerPlayer>) byUuidField.get(pl);
                        if (map != null) {
                            map.put(uuid, serverPlayer);
                        }
                    }
                } catch (Throwable ignored) {}
                try {
                    java.lang.reflect.Field byNameField = null;
                    Class<?> curPl = pl.getClass();
                    while (curPl != null && byNameField == null) {
                        for (java.lang.reflect.Field f : curPl.getDeclaredFields()) {
                            if (f.getName().equals("playersByName")) {
                                byNameField = f;
                                break;
                            }
                        }
                        curPl = curPl.getSuperclass();
                    }
                    if (byNameField != null) {
                        byNameField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<String, net.minecraft.server.level.ServerPlayer> byName =
                                (Map<String, net.minecraft.server.level.ServerPlayer>) byNameField.get(pl);
                        if (byName != null) {
                            byName.put(rawName.toLowerCase(java.util.Locale.ROOT), serverPlayer);
                        }
                    }
                } catch (Throwable ignored) {}
            }

            serverPlayers.put(uuid, serverPlayer);

            // Trigger server channel accept handlers (e.g. PacketEvents ServerChannelHandler)
            // and transition injected packet listeners through Handshake -> Login -> Configuration -> Play.
            simulateChannelInjectionAndLogin(channel, uuid, rawName, dedicatedServer, serverPlayer, level, outboundProtocol, connection);

            LOGGER.info("[VirtualChannelManager] Created authentic ServerPlayer for " + rawName + " (" + uuid + ")");
            org.patchbukkit.bootstrap.CommandBridge.checkAndSyncIfNeeded();
            return serverPlayer;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[VirtualChannelManager] Failed to construct ServerPlayer for " + uuid, t);
            return null;
        }
    }

    public void removePlayer(UUID uuid) {
        if (uuid == null) return;
        PatchBukkitVirtualChannel channel = channels.remove(uuid);
        net.minecraft.server.level.ServerPlayer sp = serverPlayers.remove(uuid);

        if (sp != null && org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
            net.minecraft.server.dedicated.DedicatedServer ds = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (sp.connection != null && sp.connection.connection != null) {
                net.minecraft.server.network.ServerConnectionListener scl = ds.getConnection();
                if (scl != null && scl.getConnections() != null) {
                    scl.getConnections().remove(sp.connection.connection);
                }
            }
            net.minecraft.server.dedicated.DedicatedPlayerList pl =
                (net.minecraft.server.dedicated.DedicatedPlayerList) ds.getPlayerList();
            if (pl != null) {
                if (pl.getPlayers() != null) {
                    pl.getPlayers().removeIf(p -> {
                        try {
                            return sp == p || uuid.equals(p.getUUID());
                        } catch (Throwable ignored) {
                            return false;
                        }
                    });
                }
                try {
                    java.lang.reflect.Field byUuidField = null;
                    Class<?> curPl = pl.getClass();
                    while (curPl != null && byUuidField == null) {
                        for (java.lang.reflect.Field f : curPl.getDeclaredFields()) {
                            if (f.getName().equals("playersByUUID")) {
                                byUuidField = f;
                                break;
                            }
                        }
                        curPl = curPl.getSuperclass();
                    }
                    if (byUuidField != null) {
                        byUuidField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<UUID, net.minecraft.server.level.ServerPlayer> map =
                                (Map<UUID, net.minecraft.server.level.ServerPlayer>) byUuidField.get(pl);
                        if (map != null) {
                            map.remove(uuid);
                        }
                    }
                } catch (Throwable ignored) {}
                try {
                    java.lang.reflect.Field byNameField = null;
                    Class<?> curPl = pl.getClass();
                    while (curPl != null && byNameField == null) {
                        for (java.lang.reflect.Field f : curPl.getDeclaredFields()) {
                            if (f.getName().equals("playersByName")) {
                                byNameField = f;
                                break;
                            }
                        }
                        curPl = curPl.getSuperclass();
                    }
                    if (byNameField != null) {
                        byNameField.setAccessible(true);
                        @SuppressWarnings("unchecked")
                        Map<String, net.minecraft.server.level.ServerPlayer> byName =
                                (Map<String, net.minecraft.server.level.ServerPlayer>) byNameField.get(pl);
                        if (byName != null) {
                            byName.values().removeIf(p -> p == sp);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (channel != null && channel.isOpen()) {
            channel.close();
        }
        refreshPacketInterceptionState();
    }

    public static boolean hasPacketPlugins() {
        return hasNettyInjectors();
    }

    /**
     * Generic detection of Netty-level packet interception, independent of which library a plugin
     * shades or relocates: on Paper, packet libraries either register a Paper
     * {@link io.papermc.paper.network.ChannelInitializeListener} or inject a handler into the
     * server channel pipeline of {@code ServerConnectionListener}. If either happened, every
     * play-phase packet (including the very first join teleport) must flow through the virtual
     * channel pipeline, exactly as on a real Paper server.
     */
    private static boolean hasNettyInjectors() {
        try {
            if (!io.papermc.paper.network.ChannelInitializeListenerHolder.getListeners().isEmpty()) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            if (!org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) return false;
            net.minecraft.server.network.ServerConnectionListener scl =
                    org.patchbukkit.bootstrap.HeadlessPaperServer.get().getConnection();
            if (scl == null) return false;
            java.lang.reflect.Field channelsField =
                    net.minecraft.server.network.ServerConnectionListener.class.getDeclaredField("channels");
            channelsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.List<io.netty.channel.ChannelFuture> chList =
                    (java.util.List<io.netty.channel.ChannelFuture>) channelsField.get(scl);
            if (chList == null) return false;
            for (io.netty.channel.ChannelFuture cf : chList) {
                if (cf == null || cf.channel() == null) continue;
                for (String name : cf.channel().pipeline().names()) {
                    if (!name.startsWith("DefaultChannelPipeline$TailContext")
                            && !name.startsWith("EmbeddedChannel$")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static volatile boolean lastInterceptionState = false;

    /**
     * Updates Rust-side {@code PACKET_INTERCEPTION_ENABLED} flag based on whether any active
     * virtual channel has custom Netty pipeline handlers attached or packet plugins are installed.
     */
    public void refreshPacketInterceptionState() {
        boolean needed = hasPacketPlugins();
        if (!needed) {
            for (PatchBukkitVirtualChannel ch : channels.values()) {
                if (ch != null && ch.isOpen() && ch.hasCustomPipelineHandlers()) {
                    needed = true;
                    break;
                }
            }
        }
        if (needed != lastInterceptionState) {
            lastInterceptionState = needed;
            LOGGER.info("[VirtualChannelManager] refreshPacketInterceptionState changed: needed=" + needed);
        }
        org.patchbukkit.bridge.NativeFastPathFfi.setPacketInterception(needed);
    }

    /**
     * Drains pending and scheduled tasks across all active player virtual channels.
     * Invoked every server tick from {@link org.patchbukkit.bootstrap.SchedulerBridge#heartbeat}.
     */
    public void tickChannels() {
        for (PatchBukkitVirtualChannel ch : channels.values()) {
            if (ch != null && ch.isActive()) {
                ch.drainPendingTasks();
            }
        }
    }

    private void simulateChannelInjectionAndLogin(
            PatchBukkitVirtualChannel channel,
            UUID uuid,
            String name,
            net.minecraft.server.dedicated.DedicatedServer dedicatedServer,
            net.minecraft.server.level.ServerPlayer serverPlayer,
            net.minecraft.server.level.ServerLevel level,
            net.minecraft.network.ProtocolInfo<net.minecraft.network.protocol.game.ClientGamePacketListener> outboundProtocol,
            net.minecraft.network.Connection connection
    ) {
        try {
            net.minecraft.server.network.ServerConnectionListener scl = dedicatedServer.getConnection();
            if (scl != null) {
                java.lang.reflect.Field channelsField =
                        net.minecraft.server.network.ServerConnectionListener.class.getDeclaredField("channels");
                channelsField.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.List<io.netty.channel.ChannelFuture> chList =
                        (java.util.List<io.netty.channel.ChannelFuture>) channelsField.get(scl);
                if (chList != null) {
                    for (io.netty.channel.ChannelFuture cf : chList) {
                        if (cf != null && cf.channel() != null) {
                            cf.channel().pipeline().fireChannelRead(channel);
                        }
                    }
                    channel.pipeline().fireChannelRegistered();
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[VirtualChannelManager] Failed to trigger ServerChannelHandler for " + uuid, t);
        }

        // Paper's ServerConnectionListener#initChannel invokes registered ChannelInitializeListeners
        // for every new connection; mirror that contract for the virtual channel.
        try {
            io.papermc.paper.network.ChannelInitializeListenerHolder.callListeners(channel);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[VirtualChannelManager] ChannelInitializeListeners failed for " + uuid, t);
        }

        channel.setSuppressOutboundBridge(true);
        try {
            // 1. Inbound Handshake (0x00 in HANDSHAKING -> transitions decoder to LOGIN)
            ByteBuf handshake = channel.alloc().buffer();
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, net.minecraft.SharedConstants.getProtocolVersion());
            writeUtf(handshake, "127.0.0.1");
            handshake.writeShort(25565);
            writeVarInt(handshake, 2); // Next state = LOGIN
            channel.fireInbound(handshake);

            // 2. Outbound LoginSuccess (0x02 in LOGIN -> sets UserProfile, transitions encoder to CONFIGURATION, triggers packet listeners)
            ByteBuf loginSuccess = channel.alloc().buffer();
            writeVarInt(loginSuccess, 0x02);
            loginSuccess.writeLong(uuid.getMostSignificantBits());
            loginSuccess.writeLong(uuid.getLeastSignificantBits());
            writeUtf(loginSuccess, name);
            writeVarInt(loginSuccess, 0); // 0 properties
            loginSuccess.writeLong(0L);   // sessionId MSB (26.2+)
            loginSuccess.writeLong(0L);   // sessionId LSB (26.2+)
            channel.writeAndFlush(loginSuccess);

            // 3. Inbound LoginSuccessAck (0x03 in LOGIN -> transitions decoder to CONFIGURATION)
            ByteBuf loginAck = channel.alloc().buffer();
            writeVarInt(loginAck, 0x03);
            channel.fireInbound(loginAck);

            // 4. Outbound ConfigurationEnd (0x03 in CONFIGURATION -> transitions encoder to PLAY)
            ByteBuf configEnd = channel.alloc().buffer();
            writeVarInt(configEnd, 0x03);
            channel.writeAndFlush(configEnd);

            // 5. Inbound ConfigurationEndAck (0x03 in CONFIGURATION -> transitions decoder to PLAY)
            ByteBuf configAck = channel.alloc().buffer();
            writeVarInt(configAck, 0x03);
            channel.fireInbound(configAck);

            // 6. Bind authentic Paper outbound encoder & terminal connection handler for PLAY state.
            for (String handlerName : new java.util.ArrayList<>(channel.pipeline().names())) {
                if (!PatchBukkitVirtualChannel.isDefaultHandlerName(handlerName)) {
                    if (handlerName.contains("encoder") && channel.pipeline().get("pre-" + handlerName) == null) {
                        channel.pipeline().addBefore(handlerName, "pre-" + handlerName, new io.netty.channel.ChannelOutboundHandlerAdapter());
                    } else if (handlerName.contains("decoder") && channel.pipeline().get("pre-" + handlerName) == null) {
                        channel.pipeline().addAfter(handlerName, "pre-" + handlerName, new io.netty.channel.ChannelInboundHandlerAdapter());
                    }
                }
            }
            if (channel.pipeline().get("encoder") != null) {
                channel.pipeline().replace("encoder", "encoder", new net.minecraft.network.PacketEncoder<>(outboundProtocol));
            }
            if (channel.pipeline().get("packet_handler") != null) {
                channel.pipeline().replace("packet_handler", "packet_handler", connection);
            }

            // Bind authentic Bukkit Player to pipeline handlers and user tracking
            org.bukkit.entity.Player bukkitPlayer = serverPlayer.getBukkitEntity();
            for (String handlerName : channel.pipeline().names()) {
                io.netty.channel.ChannelHandler handler = channel.pipeline().get(handlerName);
                if (handler == null) continue;
                for (java.lang.reflect.Field f : handler.getClass().getFields()) {
                    if (org.bukkit.entity.Player.class.isAssignableFrom(f.getType())) {
                        try {
                            f.set(handler, bukkitPlayer);
                            LOGGER.info("[VirtualChannelManager] Bound Player to " + handlerName + "." + f.getName());
                        } catch (Throwable ignored) {}
                    }
                }
            }

            // In authentic Paper/Spigot, placing a player into the server dispatches
            // PlayerJoinEvent so all plugins associate the player before play packets are processed.
            try {
                org.bukkit.event.player.PlayerJoinEvent joinEvent =
                        new org.bukkit.event.player.PlayerJoinEvent(bukkitPlayer, net.kyori.adventure.text.Component.empty());
                org.bukkit.Bukkit.getPluginManager().callEvent(joinEvent);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[VirtualChannelManager] Failed dispatching PlayerJoinEvent for " + name, t);
            }

            // 7. Outbound JOIN_GAME (ClientboundLoginPacket) -> initializes entityId, dimensionType, and world in packet listeners
            net.minecraft.network.protocol.game.ClientboundLoginPacket loginPacket =
                    new net.minecraft.network.protocol.game.ClientboundLoginPacket(
                            serverPlayer.getId(),
                            level.getLevelData().isHardcore(),
                            dedicatedServer.levelKeys(),
                            dedicatedServer.getMaxPlayers(),
                            10, // chunkRadius
                            10, // simulationDistance
                            false,
                            true,
                            false,
                            serverPlayer.createCommonSpawnInfo(level),
                            dedicatedServer.usesAuthentication(),
                            dedicatedServer.enforceSecureProfile()
                    );
            channel.writeAndFlush(loginPacket);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[VirtualChannelManager] Failed to simulate login lifecycle for " + uuid, t);
        } finally {
            channel.setSuppressOutboundBridge(false);
            refreshPacketInterceptionState();
        }
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~0x7F) != 0) {
            buf.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private static void writeUtf(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    /**
     * Direct Panama FFM upcall entrypoint invoked from Rust (`try_dispatch_packet_upcall`)
     * without JNI method lookup, `JByteArray` allocation, or Protobuf serialization.
     */
    public static int handlePacketUpcall(
        long uuidMsb,
        long uuidLsb,
        int packetId,
        long payloadAddr,
        int payloadLen,
        int isOutbound
    ) {
        if (payloadLen < 0 || (payloadLen > 0 && payloadAddr == 0L)) return 0;
        UUID uuid = new UUID(uuidMsb, uuidLsb);
        PatchBukkitVirtualChannel channel = INSTANCE.getOrCreateChannel(uuid);
        if (channel == null || !channel.isActive() || !channel.hasCustomPipelineHandlers()) return 0;

        byte[] payload = payloadLen > 0
            ? java.lang.foreign.MemorySegment.ofAddress(payloadAddr)
                .reinterpret(payloadLen)
                .toArray(java.lang.foreign.ValueLayout.JAVA_BYTE)
            : new byte[0];

        if (isOutbound != 0) {
            return INSTANCE.handlePacketSent(uuid, packetId, payload) ? 1 : 0;
        }
        INSTANCE.handlePacketReceived(uuid, packetId, payload);
        return 0;
    }

    public void handlePacketReceived(UUID uuid, int packetId, byte[] payload) {
        if (uuid == null || payload == null) return;
        LOGGER.fine("[VirtualChannelManager] handlePacketReceived: uuid=" + uuid + " packetId=0x" + Integer.toHexString(packetId) + " len=" + payload.length);
        PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);
        if (channel == null || !channel.isActive() || !channel.hasCustomPipelineHandlers()) {
            LOGGER.warning("[VirtualChannelManager] handlePacketReceived SKIPPED (no active channel or no custom handlers): uuid=" + uuid);
            return;
        }

        try {
            ByteBuf buffer;
            if ((org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()
                    || channel.pipeline().get("decoder") instanceof net.minecraft.network.PacketDecoder)
                    && packetId >= 0) {
                buffer = channel.alloc().buffer(5 + payload.length);
                writeVarInt(buffer, packetId);
                buffer.writeBytes(payload);
            } else {
                buffer = Unpooled.wrappedBuffer(payload);
            }
            channel.fireInbound(buffer);
            long n = INBOUND_COUNT.incrementAndGet();
            if (LOGGER.isLoggable(Level.FINE) && (n == 1 || n % 2000 == 0)) {
                LOGGER.fine("[VirtualChannelManager] inbound packets forwarded to PacketEvents: " + n
                    + " (last id=0x" + Integer.toHexString(packetId) + ", handlers=" + channel.pipeline().names() + ")");
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[VirtualChannelManager] Error in inbound packet processing for " + uuid, t);
        }
    }

    private static volatile net.minecraft.network.ProtocolInfo<net.minecraft.network.protocol.game.ClientGamePacketListener> clientboundProtocol;

    public static net.minecraft.network.ProtocolInfo<net.minecraft.network.protocol.game.ClientGamePacketListener> getClientboundProtocol() {
        net.minecraft.network.ProtocolInfo<net.minecraft.network.protocol.game.ClientGamePacketListener> p = clientboundProtocol;
        if (p == null) {
            net.minecraft.server.dedicated.DedicatedServer dedicatedServer = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
            if (dedicatedServer != null) {
                p = net.minecraft.network.protocol.game.GameProtocols.CLIENTBOUND_TEMPLATE.bind(
                        net.minecraft.network.RegistryFriendlyByteBuf.decorator(dedicatedServer.registryAccess())
                );
                clientboundProtocol = p;
            }
        }
        return p;
    }

    /**
     * Runs an outbound packet through the player's virtual pipeline (PacketEvents etc.).
     *
     * <p>Returns {@code true} when the pipeline took ownership of the packet: it is written out
     * through {@code bridge_outbound} in pipeline order, so packets a plugin sends before/after it
     * (e.g. pre/post transactions) keep their position relative to it on the wire, exactly as
     * on a real Paper server. The caller must then NOT send the packet itself. Returns
     * {@code false} when the caller still has to send it.
     */
    public boolean handlePacketSent(UUID uuid, int packetId, byte[] payload) {
        if (uuid == null || payload == null) return false;
        LOGGER.fine("[VirtualChannelManager] handlePacketSent: uuid=" + uuid + " packetId=0x" + Integer.toHexString(packetId) + " len=" + payload.length);
        PatchBukkitVirtualChannel channel = getOrCreateChannel(uuid);
        if (channel == null || !channel.isActive() || !channel.hasCustomPipelineHandlers()) {
            LOGGER.warning("[VirtualChannelManager] handlePacketSent SKIPPED (no active channel or no custom handlers): uuid=" + uuid);
            return false;
        }

        if (!(org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted() && packetId >= 0)) {
            channel.setSuppressOutboundBridge(true);
            try {
                channel.writeAndFlush(Unpooled.wrappedBuffer(payload));
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[VirtualChannelManager] Error in outbound packet processing for " + uuid, t);
            } finally {
                channel.setSuppressOutboundBridge(false);
            }
            return false;
        }

        try {
            ByteBuf buffer = channel.alloc().buffer(5 + payload.length);
            writeVarInt(buffer, packetId);
            buffer.writeBytes(payload);

            net.minecraft.network.ProtocolInfo<net.minecraft.network.protocol.game.ClientGamePacketListener> protocol = getClientboundProtocol();
            if (packetId == 0x49 || packetId == 73) {
                LOGGER.info("[VirtualChannelManager] Attempting to decode 0x49: protocol=" + (protocol != null)
                    + " encoder=" + (channel.pipeline().get("encoder") != null ? channel.pipeline().get("encoder").getClass().getName() : "null")
                    + " handlers=" + channel.pipeline().names());
            }
            if (protocol != null && channel.pipeline().get("encoder") instanceof net.minecraft.network.PacketEncoder) {
                try {
                    net.minecraft.network.protocol.Packet<?> packet = protocol.codec().decode(buffer);
                    if (packet != null) {
                        io.netty.util.ReferenceCountUtil.release(buffer);
                        io.netty.channel.ChannelFuture writeFuture = channel.writeAndFlush(packet);
                        if (writeFuture.cause() != null) {
                            LOGGER.log(Level.WARNING, "[VirtualChannelManager] Outbound write failed for 0x" + Integer.toHexString(packetId), writeFuture.cause());
                        }
                        channel.runPendingTasks();
                        channel.drainPendingTasks();
                        long n = OUTBOUND_COUNT.incrementAndGet();
                        if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket posPacket) {
                            LOGGER.info("[VirtualChannelManager] Outbound teleport forwarded: id="
                                + posPacket.id() + " pos=" + posPacket.change().position());
                            net.minecraft.server.level.ServerPlayer sp = serverPlayers.get(uuid);
                            if (sp != null && sp.connection != null) {
                                try {
                                    java.lang.reflect.Field atField = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
                                    atField.setAccessible(true);
                                    atField.setInt(sp.connection, posPacket.id());

                                    java.lang.reflect.Field apField = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("awaitingPositionFromClient");
                                    apField.setAccessible(true);
                                    apField.set(sp.connection, posPacket.change().position());

                                    java.lang.reflect.Field jtField = net.minecraft.server.network.ServerGamePacketListenerImpl.class.getDeclaredField("justTeleported");
                                    jtField.setAccessible(true);
                                    jtField.setBoolean(sp.connection, true);
                                } catch (Throwable ignored) {}
                            }
                        } else if (LOGGER.isLoggable(Level.FINE) && (n == 1 || n % 1000 == 0)) {
                            LOGGER.fine("[VirtualChannelManager] Outbound packet decoded & forwarded to PacketEvents: " + n
                                + " (last id=0x" + Integer.toHexString(packetId) + " class=" + packet.getClass().getSimpleName() + ")");
                        }
                        return true;
                    }
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "[VirtualChannelManager] Failed to decode outbound packet 0x"
                        + Integer.toHexString(packetId) + " (" + payload.length + " bytes) as NMS packet, falling back to raw ByteBuf", t);
                    buffer.readerIndex(0);
                }
            }

            channel.writeAndFlush(buffer);
            long n = OUTBOUND_COUNT.incrementAndGet();
            if (packetId == 0x49 || packetId == 73) {
                LOGGER.warning("[VirtualChannelManager] OUTBOUND 0x49 FELL BACK TO RAW BYTEBUF!");
            }
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[VirtualChannelManager] Error in outbound packet processing for " + uuid, t);
            return false;
        }
    }
}
