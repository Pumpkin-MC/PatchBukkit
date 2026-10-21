package org.patchbukkit.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.patchbukkit.bootstrap.HeadlessPaperServer;

import static org.junit.jupiter.api.Assertions.*;

public class VirtualChannelPipelineTest {

    @TempDir
    static Path root;

    @BeforeAll
    public static void setUp() throws Exception {
        HeadlessPaperServer.boot(root);
    }

    @Test
    public void testPlayerHandleAndChannelReflection() throws Exception {
        UUID uuid = UUID.randomUUID();
        ServerPlayer nmsPlayer = VirtualChannelManager.getInstance().getOrCreateServerPlayer(uuid, "Steve", 101);
        assertNotNull(nmsPlayer, "ServerPlayer must not be null");
        CraftPlayer player = nmsPlayer.getBukkitEntity();
        assertNotNull(player, "CraftPlayer must not be null");
        assertSame(nmsPlayer, player.getHandle());

        // PacketEvents reflects on handle.connection
        Field connectionField = nmsPlayer.getClass().getField("connection");
        assertNotNull(connectionField);
        Object connection = connectionField.get(nmsPlayer);
        assertNotNull(connection, "Handle connection must not be null");

        // Verify nested connection.connection.channel (Mojang ServerGamePacketListenerImpl -> Connection -> channel)
        Field nestedConnField = connection.getClass().getSuperclass().getDeclaredField("connection");
        nestedConnField.setAccessible(true);
        Object nestedConn = nestedConnField.get(connection);
        Field nestedChannelField = nestedConn.getClass().getField("channel");
        Object channelObj = nestedChannelField.get(nestedConn);
        assertNotNull(channelObj, "Channel must not be null");
        assertInstanceOf(Channel.class, channelObj);
        assertSame(channelObj, nmsPlayer.connection.connection.channel, "NMS ServerPlayer channel must match");

        Channel channel = (Channel) channelObj;
        assertTrue(channel.isOpen());
        assertTrue(channel.isActive());

        // Verify standard Minecraft pipeline handlers exist in exact vanilla order
        assertNotNull(channel.pipeline().get("timeout"), "Pipeline must contain 'timeout'");
        assertNotNull(channel.pipeline().get("legacy_query"), "Pipeline must contain 'legacy_query'");
        assertNotNull(channel.pipeline().get("splitter"), "Pipeline must contain 'splitter'");
        assertNotNull(channel.pipeline().get("decompress"), "Pipeline must contain 'decompress'");
        assertNotNull(channel.pipeline().get("decoder"), "Pipeline must contain 'decoder'");
        assertNotNull(channel.pipeline().get("prepender"), "Pipeline must contain 'prepender'");
        assertNotNull(channel.pipeline().get("compress"), "Pipeline must contain 'compress'");
        assertNotNull(channel.pipeline().get("encoder"), "Pipeline must contain 'encoder'");
        assertNotNull(channel.pipeline().get("bundle_packer"), "Pipeline must contain 'bundle_packer'");
        assertNotNull(channel.pipeline().get("packet_handler"), "Pipeline must contain 'packet_handler'");
    }

    @Test
    public void testCompressionAndEncryptionLifecycle() {
        UUID uuid = UUID.randomUUID();
        PatchBukkitVirtualChannel channel = VirtualChannelManager.getInstance().getOrCreateChannel(uuid);

        // Initially compressed with threshold 256
        assertTrue(channel.isCompressed());
        assertEquals(256, channel.getCompressionThreshold());
        assertFalse(channel.isEncrypted());

        // Change compression threshold
        channel.setupCompression(512, true);
        assertEquals(512, channel.getCompressionThreshold());
        PatchBukkitVirtualChannel.VirtualCompressionDecoder decoder =
                (PatchBukkitVirtualChannel.VirtualCompressionDecoder) channel.pipeline().get("decompress");
        PatchBukkitVirtualChannel.VirtualCompressionEncoder encoder =
                (PatchBukkitVirtualChannel.VirtualCompressionEncoder) channel.pipeline().get("compress");
        assertNotNull(decoder);
        assertNotNull(encoder);
        assertEquals(512, decoder.threshold());
        assertTrue(decoder.validateDecompressed);
        assertEquals(512, encoder.threshold());

        // Disable compression with -1
        channel.setupCompression(-1, false);
        assertFalse(channel.isCompressed());
        assertNull(channel.pipeline().get("decompress"));
        assertNull(channel.pipeline().get("compress"));

        // Re-enable compression
        channel.setupCompression(128, false);
        assertTrue(channel.isCompressed());
        assertEquals(128, channel.getCompressionThreshold());
        assertNotNull(channel.pipeline().get("decompress"));
        assertNotNull(channel.pipeline().get("compress"));

        // Enable encryption
        Object dummyCipher = new Object();
        channel.setEncryptionKey(dummyCipher, dummyCipher);
        assertTrue(channel.isEncrypted());
        assertNotNull(channel.pipeline().get("decrypt"));
        assertNotNull(channel.pipeline().get("encrypt"));
    }

    @Test
    public void testPacketEventsStylePipelineInjection() {
        UUID uuid = UUID.randomUUID();
        ServerPlayer nmsPlayer = VirtualChannelManager.getInstance().getOrCreateServerPlayer(uuid, "Alex", 102);
        Channel channel = nmsPlayer.connection.connection.channel;

        AtomicBoolean injectedInboundReceived = new AtomicBoolean(false);
        AtomicInteger receivedBytesCount = new AtomicInteger(0);

        // Simulate PacketEvents injecting its handler before "packet_handler"
        channel.pipeline().addBefore("packet_handler", "packetevents_inbound_handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof ByteBuf buf) {
                    injectedInboundReceived.set(true);
                    receivedBytesCount.set(buf.readableBytes());
                }
                ctx.fireChannelRead(msg);
            }
        });

        // Simulate Pumpkin receiving packet bytes from the client and forwarding to VirtualChannelManager
        byte[] testPacket = "hello_minecraft_packet".getBytes(StandardCharsets.UTF_8);
        VirtualChannelManager.getInstance().handlePacketReceived(uuid, 0x01, testPacket);

        assertTrue(injectedInboundReceived.get(), "Injected PacketEvents handler should have intercepted the inbound packet");
        assertEquals(1 + testPacket.length, receivedBytesCount.get(), "Injected handler should have received VarInt packetId + payload bytes");

        // Simulate outbound write
        AtomicBoolean outboundIntercepted = new AtomicBoolean(false);
        channel.pipeline().addBefore("encoder", "packetevents_outbound_handler", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                outboundIntercepted.set(true);
                ctx.write(msg, promise);
            }
        });

        channel.writeAndFlush("dummy_packet");
        assertTrue(outboundIntercepted.get(), "Outbound write should have passed through injected outbound handler");
    }
}
