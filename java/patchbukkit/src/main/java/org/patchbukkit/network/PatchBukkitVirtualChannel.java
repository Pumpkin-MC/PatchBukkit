package org.patchbukkit.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * A virtual Netty channel representing a player's network pipeline.
 * Fully emulates the vanilla Minecraft Netty pipeline structure, including
 * timeout, legacy query, splitter, decrypt, decompress, decoder, prepender,
 * encrypt, compress, encoder, bundle_packer, and packet_handler.
 */
public class PatchBukkitVirtualChannel extends EmbeddedChannel {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkitVirtualChannel");

    private final UUID playerUuid;
    private final SocketAddress localAddress;
    private final SocketAddress remoteAddress;
    private int compressionThreshold = 256;
    private boolean encrypted = false;

    public PatchBukkitVirtualChannel(UUID playerUuid) {
        super();
        this.playerUuid = playerUuid;
        this.localAddress = new InetSocketAddress("127.0.0.1", 25565);
        this.remoteAddress = new InetSocketAddress("127.0.0.1", 50000 + (Math.abs(playerUuid.hashCode()) % 10000));

        setupPipeline();
    }

    private void setupPipeline() {
        // Full Vanilla Minecraft pipeline structure:
        // Inbound:  timeout -> legacy_query -> splitter -> [decrypt] -> [decompress] -> decoder -> packet_handler
        // Outbound: timeout -> prepender -> [encrypt] -> [compress] -> encoder -> [bundle_packer] -> packet_handler

        // 1. "timeout" (ReadTimeoutHandler / WriteTimeoutHandler emulation)
        pipeline().addLast("timeout", new ChannelDuplexHandler());

        // 2. "legacy_query" (handles legacy 1.6- server ping queries)
        pipeline().addLast("legacy_query", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(msg);
            }
        });

        // 3. "splitter" (Varint21FrameDecoder)
        pipeline().addLast("splitter", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(msg);
            }
        });

        // 4. "decompress" (CompressionDecoder - enabled by default for play phase, threshold 256)
        pipeline().addLast("decompress", new VirtualCompressionDecoder(compressionThreshold, false));

        // 5. "decoder" (PacketDecoder: ByteBuf -> Packet)
        pipeline().addLast("decoder", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                ctx.fireChannelRead(msg);
            }
        });

        // 6. "prepender" (Varint21LengthFieldPrepender)
        pipeline().addLast("prepender", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                ctx.write(msg, promise);
            }
        });

        // 7. "compress" (CompressionEncoder - enabled by default for play phase, threshold 256)
        pipeline().addLast("compress", new VirtualCompressionEncoder(compressionThreshold));

        // 7.5 "bridge_outbound" (intercepts raw ByteBufs exiting encoder and sends them to Pumpkin via FFI)
        pipeline().addLast("bridge_outbound", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                if (msg instanceof ByteBuf buf) {
                    try {
                        int readable = buf.readableBytes();
                        if (readable > 0) {
                            byte[] bytes = new byte[readable];
                            buf.getBytes(buf.readerIndex(), bytes);
                            try {
                                patchbukkit.bridge.NativeBridgeFfi.sendRawPacket(
                                    patchbukkit.entity.SendRawPacketRequest.newBuilder()
                                        .setUuid(org.patchbukkit.bridge.BridgeUtils.convertUuid(playerUuid))
                                        .setPayload(com.google.protobuf.ByteString.copyFrom(bytes))
                                        .build()
                                );
                            } catch (Throwable t) {
                                LOGGER.log(java.util.logging.Level.FINE, "[PatchBukkitVirtualChannel] Failed to send raw packet to Pumpkin for " + playerUuid, t);
                            }
                        }
                    } finally {
                        io.netty.util.ReferenceCountUtil.release(msg);
                    }
                    promise.setSuccess();
                    return;
                }
                ctx.write(msg, promise);
            }
        });

        // 8. "encoder" (PacketEncoder: Packet -> ByteBuf)
        pipeline().addLast("encoder", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                ctx.write(msg, promise);
            }
        });

        // 9. "bundle_packer" (PacketBundlePacker - 1.20+)
        pipeline().addLast("bundle_packer", new ChannelOutboundHandlerAdapter() {
            @Override
            public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
                ctx.write(msg, promise);
            }
        });

        // 10. "packet_handler" (Connection / ServerGamePacketListenerImpl terminal handler)
        pipeline().addLast("packet_handler", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) {
                // Packet reached the end of inbound pipeline
                if (msg instanceof ByteBuf buf) {
                    buf.release();
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                LOGGER.fine("[PatchBukkitVirtualChannel] Exception in pipeline for " + playerUuid + ": " + cause.getMessage());
            }
        });
    }

    /**
     * Updates or toggles compression handlers exactly like Vanilla Minecraft Connection.setupCompression().
     *
     * @param threshold             the compression threshold, or -1 to disable
     * @param validateDecompressed  whether to validate decompressed size
     */
    public void setupCompression(int threshold, boolean validateDecompressed) {
        this.compressionThreshold = threshold;
        if (threshold >= 0) {
            var decompress = pipeline().get("decompress");
            if (decompress instanceof VirtualCompressionDecoder cd) {
                cd.setThreshold(threshold, validateDecompressed);
            } else {
                if (pipeline().get("decoder") != null) {
                    pipeline().addBefore("decoder", "decompress", new VirtualCompressionDecoder(threshold, validateDecompressed));
                }
            }

            var compress = pipeline().get("compress");
            if (compress instanceof VirtualCompressionEncoder ce) {
                ce.setThreshold(threshold);
            } else {
                if (pipeline().get("encoder") != null) {
                    pipeline().addBefore("encoder", "compress", new VirtualCompressionEncoder(threshold));
                }
            }
        } else {
            if (pipeline().get("decompress") != null) {
                pipeline().remove("decompress");
            }
            if (pipeline().get("compress") != null) {
                pipeline().remove("compress");
            }
        }
    }

    public void setupCompression(int threshold) {
        setupCompression(threshold, false);
    }

    public void setCompressionThreshold(int threshold) {
        setupCompression(threshold, false);
    }

    public int getCompressionThreshold() {
        return compressionThreshold;
    }

    public boolean isCompressed() {
        return compressionThreshold >= 0 && pipeline().get("decompress") != null;
    }

    /**
     * Enables or updates encryption handlers in the pipeline matching Vanilla Connection.setEncryptionKey().
     */
    public void setEncryptionKey(Object decryptCipher, Object encryptCipher) {
        this.encrypted = true;
        if (pipeline().get("decrypt") == null && pipeline().get("splitter") != null) {
            pipeline().addBefore("splitter", "decrypt", new VirtualCipherDecoder(decryptCipher));
        }
        if (pipeline().get("encrypt") == null && pipeline().get("prepender") != null) {
            pipeline().addBefore("prepender", "encrypt", new VirtualCipherEncoder(encryptCipher));
        }
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Override
    public SocketAddress localAddress() {
        return localAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
        return remoteAddress;
    }

    @Override
    public boolean isActive() {
        return isOpen();
    }

    /**
     * Emulates Vanilla net.minecraft.network.CompressionDecoder.
     */
    public static class VirtualCompressionDecoder extends ChannelInboundHandlerAdapter {
        public int threshold;
        public boolean validateDecompressed;

        public VirtualCompressionDecoder(int threshold, boolean validateDecompressed) {
            this.threshold = threshold;
            this.validateDecompressed = validateDecompressed;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public void setThreshold(int threshold, boolean validateDecompressed) {
            this.threshold = threshold;
            this.validateDecompressed = validateDecompressed;
        }

        public int threshold() {
            return threshold;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Emulates Vanilla net.minecraft.network.CompressionEncoder.
     */
    public static class VirtualCompressionEncoder extends ChannelOutboundHandlerAdapter {
        public int threshold;

        public VirtualCompressionEncoder(int threshold) {
            this.threshold = threshold;
        }

        public int getThreshold() {
            return threshold;
        }

        public void setThreshold(int threshold) {
            this.threshold = threshold;
        }

        public int threshold() {
            return threshold;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            ctx.write(msg, promise);
        }
    }

    /**
     * Emulates Vanilla net.minecraft.network.CipherDecoder.
     */
    public static class VirtualCipherDecoder extends ChannelInboundHandlerAdapter {
        public final Object cipher;

        public VirtualCipherDecoder(Object cipher) {
            this.cipher = cipher;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Emulates Vanilla net.minecraft.network.CipherEncoder.
     */
    public static class VirtualCipherEncoder extends ChannelOutboundHandlerAdapter {
        public final Object cipher;

        public VirtualCipherEncoder(Object cipher) {
            this.cipher = cipher;
        }

        @Override
        public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
            ctx.write(msg, promise);
        }
    }
}
