package org.patchbukkit.bridge;

import io.netty.buffer.ByteBuf;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Zero-allocation primitive FFM fast-path bridge for hot-path operations
 * (block state ID queries/mutations, entity position/velocity sync, raw packets, and Panama upcalls).
 *
 * <p>Bypasses Protobuf serialization, string UUID formatting, and per-call Arena allocation.
 */
public final class NativeFastPathFfi {

    private static final Linker LINKER = Linker.nativeLinker();

    /**
     * Thread-local 64-byte native buffer (`8 * sizeof(double)`) used by {@link #syncNmsEntityState}
     * to read `[x, y, z, vx, vy, vz, yaw, pitch]` with zero heap allocation.
     */
    private static final ThreadLocal<MemorySegment> ENTITY_STATE_BUF =
        ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(ValueLayout.JAVA_DOUBLE, 8));

    /**
     * Thread-local reusable native buffer (64 KiB) used by {@link #sendRawPacket} when a {@link ByteBuf}
     * is heap-backed and does not expose a direct native memory address.
     */
    private static final int PACKET_SCRATCH_SIZE = 65536;
    private static final ThreadLocal<MemorySegment> PACKET_SCRATCH_BUF =
        ThreadLocal.withInitial(() -> Arena.ofAuto().allocate(PACKET_SCRATCH_SIZE, 8));

    private static MethodHandle getBlockStateIdHandle;
    private static MethodHandle setBlockStateIdHandle;
    private static MethodHandle teleportEntityHandle;
    private static MethodHandle setEntityVelocityHandle;
    private static MethodHandle setPacketInterceptionHandle;
    private static MethodHandle syncEntityStateHandle;
    private static MethodHandle sendRawPacketHandle;

    private NativeFastPathFfi() {}

    public static void init(
        long getBlockStateIdAddr,
        long setBlockStateIdAddr,
        long teleportEntityAddr,
        long setEntityVelocityAddr,
        long setPacketInterceptionAddr,
        long syncEntityStateAddr,
        long sendRawPacketAddr,
        long registerUpcallsAddr
    ) {
        try {
            getBlockStateIdHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(getBlockStateIdAddr),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
                )
            );
            setBlockStateIdHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(setBlockStateIdAddr),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT
                )
            );
            teleportEntityHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(teleportEntityAddr),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_FLOAT,
                    ValueLayout.JAVA_FLOAT
                )
            );
            setEntityVelocityHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(setEntityVelocityAddr),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE,
                    ValueLayout.JAVA_DOUBLE
                )
            );
            setPacketInterceptionHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(setPacketInterceptionAddr),
                FunctionDescriptor.ofVoid(ValueLayout.JAVA_INT)
            );
            syncEntityStateHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(syncEntityStateAddr),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS
                )
            );
            sendRawPacketHandle = LINKER.downcallHandle(
                MemorySegment.ofAddress(sendRawPacketAddr),
                FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT
                )
            );

            if (registerUpcallsAddr != 0L) {
                MethodHandle registerUpcallsHandle = LINKER.downcallHandle(
                    MemorySegment.ofAddress(registerUpcallsAddr),
                    FunctionDescriptor.ofVoid(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG)
                );

                MethodHandles.Lookup lookup = MethodHandles.publicLookup();
                MethodHandle packetTarget = lookup.findStatic(
                    org.patchbukkit.network.VirtualChannelManager.class,
                    "handlePacketUpcall",
                    MethodType.methodType(
                        int.class,
                        long.class,
                        long.class,
                        int.class,
                        long.class,
                        int.class,
                        int.class
                    )
                );
                MemorySegment packetUpcallStub = LINKER.upcallStub(
                    packetTarget,
                    FunctionDescriptor.of(
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_LONG,
                        ValueLayout.JAVA_INT,
                        ValueLayout.JAVA_INT
                    ),
                    Arena.global()
                );

                MethodHandle heartbeatTarget = lookup.findStatic(
                    org.patchbukkit.bootstrap.SchedulerBridge.class,
                    "onServerTick",
                    MethodType.methodType(void.class)
                );
                MemorySegment heartbeatUpcallStub = LINKER.upcallStub(
                    heartbeatTarget,
                    FunctionDescriptor.ofVoid(),
                    Arena.global()
                );

                registerUpcallsHandle.invokeExact(
                    packetUpcallStub.address(),
                    heartbeatUpcallStub.address()
                );
            }
        } catch (Throwable t) {
            System.err.println("[PatchBukkit FastFFI] Failed init: " + t.getMessage());
            t.printStackTrace();
        }
    }

    public static boolean isInitialized() {
        return getBlockStateIdHandle != null;
    }

    /**
     * Returns Pumpkin's u16 block state ID at (x, y, z), or -1 if unavailable.
     */
    public static int getBlockStateId(UUID worldUuid, int x, int y, int z) {
        MethodHandle h = getBlockStateIdHandle;
        if (h == null || worldUuid == null) return -1;
        try {
            return (int) h.invokeExact(
                worldUuid.getMostSignificantBits(),
                worldUuid.getLeastSignificantBits(),
                x,
                y,
                z
            );
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Sets Pumpkin's u16 block state ID at (x, y, z). Returns true on success.
     */
    public static boolean setBlockStateId(UUID worldUuid, int x, int y, int z, int stateId, boolean applyPhysics) {
        MethodHandle h = setBlockStateIdHandle;
        if (h == null || worldUuid == null) return false;
        try {
            int res = (int) h.invokeExact(
                worldUuid.getMostSignificantBits(),
                worldUuid.getLeastSignificantBits(),
                x,
                y,
                z,
                stateId,
                applyPhysics ? 1 : 0
            );
            return res != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Teleports an entity or player synchronously by UUID without Protobuf allocation.
     */
    public static boolean teleportEntity(
        UUID entityUuid,
        UUID worldUuid,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
    ) {
        MethodHandle h = teleportEntityHandle;
        if (h == null || entityUuid == null) return false;
        long worldMsb = worldUuid != null ? worldUuid.getMostSignificantBits() : 0L;
        long worldLsb = worldUuid != null ? worldUuid.getLeastSignificantBits() : 0L;
        try {
            int res = (int) h.invokeExact(
                entityUuid.getMostSignificantBits(),
                entityUuid.getLeastSignificantBits(),
                worldMsb,
                worldLsb,
                x,
                y,
                z,
                yaw,
                pitch
            );
            return res != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Updates an entity's or player's velocity by UUID without Protobuf allocation.
     */
    public static boolean setEntityVelocity(UUID entityUuid, double vx, double vy, double vz) {
        MethodHandle h = setEntityVelocityHandle;
        if (h == null || entityUuid == null) return false;
        try {
            int res = (int) h.invokeExact(
                entityUuid.getMostSignificantBits(),
                entityUuid.getLeastSignificantBits(),
                vx,
                vy,
                vz
            );
            return res != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Toggles Rust-side packet event dispatch ({@code PacketReceivedEvent} / {@code PacketSentEvent})
     * based on whether any active virtual channel has custom Netty pipeline handlers.
     */
    public static void setPacketInterception(boolean enabled) {
        MethodHandle h = setPacketInterceptionHandle;
        if (h == null) return;
        try {
            h.invokeExact(enabled ? 1 : 0);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Synchronizes an NMS {@link net.minecraft.world.entity.Entity}'s position, rotation, and velocity
     * from Pumpkin's live entity state using a thread-local 64-byte native buffer (zero heap allocation).
     */
    public static boolean syncNmsEntityState(net.minecraft.world.entity.Entity handle) {
        MethodHandle h = syncEntityStateHandle;
        if (h == null || handle == null) return false;
        UUID entityUuid = handle.getUUID();
        if (entityUuid == null) return false;
        try {
            MemorySegment seg = ENTITY_STATE_BUF.get();
            int res = (int) h.invokeExact(
                entityUuid.getMostSignificantBits(),
                entityUuid.getLeastSignificantBits(),
                seg
            );
            if (res == 0) return false;
            double x = seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 0);
            double y = seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 1);
            double z = seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 2);
            double vx = seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 3);
            double vy = seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 4);
            double vz = seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 5);
            float yaw = (float) seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 6);
            float pitch = (float) seg.getAtIndex(ValueLayout.JAVA_DOUBLE, 7);
            handle.setPosRaw(x, y, z);
            handle.setYRot(yaw);
            handle.setXRot(pitch);
            handle.setDeltaMovement(vx, vy, vz);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Sends a raw outbound packet from a Netty {@link ByteBuf} to a player in Pumpkin
     * without Protobuf {@code SendRawPacketRequest} / {@code ByteString} allocation.
     */
    public static boolean sendRawPacket(UUID playerUuid, ByteBuf buf) {
        MethodHandle h = sendRawPacketHandle;
        if (h == null || playerUuid == null || buf == null) return false;
        int readable = buf.readableBytes();
        if (readable <= 0) return false;
        try {
            if (buf.hasMemoryAddress()) {
                MemorySegment seg = MemorySegment.ofAddress(buf.memoryAddress() + buf.readerIndex());
                int res = (int) h.invokeExact(
                    playerUuid.getMostSignificantBits(),
                    playerUuid.getLeastSignificantBits(),
                    seg,
                    readable
                );
                return res != 0;
            }
            if (readable <= PACKET_SCRATCH_SIZE) {
                MemorySegment scratch = PACKET_SCRATCH_BUF.get();
                ByteBuffer nioBuf = scratch.asSlice(0, readable).asByteBuffer();
                buf.getBytes(buf.readerIndex(), nioBuf);
                int res = (int) h.invokeExact(
                    playerUuid.getMostSignificantBits(),
                    playerUuid.getLeastSignificantBits(),
                    scratch,
                    readable
                );
                return res != 0;
            }
            try (Arena fallback = Arena.ofConfined()) {
                MemorySegment seg = fallback.allocate(readable, 1);
                buf.getBytes(buf.readerIndex(), seg.asByteBuffer());
                int res = (int) h.invokeExact(
                    playerUuid.getMostSignificantBits(),
                    playerUuid.getLeastSignificantBits(),
                    seg,
                    readable
                );
                return res != 0;
            }
        } catch (Throwable t) {
            return false;
        }
    }
}
