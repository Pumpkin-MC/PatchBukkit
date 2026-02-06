package org.patchbukkit.world;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.patchbukkit.bridge.BridgeUtils;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

import patchbukkit.bridge.NativeBridgeFfi;

public final class PatchBukkitBlock {
    private PatchBukkitBlock() {
    }

    public static Block create(PatchBukkitWorld world, int x, int y, int z, String blockKey) {
        return (Block) Proxy.newProxyInstance(
            PatchBukkitBlock.class.getClassLoader(),
            new Class<?>[]{Block.class},
            new Handler(world, x, y, z, blockKey)
        );
    }

    static Material resolveMaterial(String blockKey) {
        if (blockKey == null || blockKey.isBlank()) {
            return Material.AIR;
        }

        Material material = Material.matchMaterial(blockKey);
        if (material != null) {
            return material;
        }

        String key = blockKey.contains(":") ? blockKey : "minecraft:" + blockKey;
        material = Material.matchMaterial(key);
        if (material != null) {
            return material;
        }

        String legacy = blockKey.replace("minecraft:", "").toUpperCase(Locale.ROOT);
        material = Material.getMaterial(legacy);
        return material != null ? material : Material.AIR;
    }

    static String queryBlockKey(PatchBukkitWorld world, int x, int y, int z, String fallbackBlockKey) {
        var request = patchbukkit.block.GetBlockRequest.newBuilder()
            .setWorld(patchbukkit.common.World.newBuilder()
                .setUuid(BridgeUtils.convertUuid(world.getUID()))
                .build())
            .setX(x)
            .setY(y)
            .setZ(z)
            .build();
        var response = NativeBridgeFfi.getBlock(request);
        if (response == null || response.getBlockKey().isBlank()) {
            return fallbackBlockKey;
        }
        return response.getBlockKey();
    }

    static Material queryMaterial(PatchBukkitWorld world, int x, int y, int z, String fallbackBlockKey) {
        return resolveMaterial(queryBlockKey(world, x, y, z, fallbackBlockKey));
    }

    private static final class Handler implements InvocationHandler {
        private final PatchBukkitWorld world;
        private final int x;
        private final int y;
        private final int z;
        private final String fallbackBlockKey;

        private Handler(PatchBukkitWorld world, int x, int y, int z, String blockKey) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.fallbackBlockKey = blockKey;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "getWorld":
                    return world;
                case "getX":
                    return x;
                case "getY":
                    return y;
                case "getZ":
                    return z;
                case "getLocation":
                    return new Location(world, x, y, z);
                case "getType":
                    return queryMaterial(world, x, y, z, fallbackBlockKey);
                case "getBlockData":
                    return queryMaterial(world, x, y, z, fallbackBlockKey).createBlockData();
                case "getState":
                    String key = queryBlockKey(world, x, y, z, fallbackBlockKey);
                    return PatchBukkitBlockState.create(world, x, y, z, key);
                case "isEmpty":
                    return queryMaterial(world, x, y, z, fallbackBlockKey).isAir();
                case "isLiquid":
                    Material material = queryMaterial(world, x, y, z, fallbackBlockKey);
                    return material == Material.WATER || material == Material.LAVA;
                case "getRelative":
                    return getRelative(args);
                case "getChunk":
                    throw new UnsupportedOperationException("Unimplemented method 'getChunk'");
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "toString":
                    String blockKey = queryBlockKey(world, x, y, z, fallbackBlockKey);
                    return "PatchBukkitBlock{" + blockKey + " @ " + x + "," + y + "," + z + "}";
                default:
                    throw new UnsupportedOperationException("Unimplemented method '" + name + "'");
            }
        }

        private Block getRelative(Object[] args) {
            if (args == null || args.length == 0) {
                return world.getBlockAt(x, y, z);
            }
            if (args.length == 1 && args[0] instanceof BlockFace face) {
                return world.getBlockAt(x + face.getModX(), y + face.getModY(), z + face.getModZ());
            }
            if (args.length == 2 && args[0] instanceof BlockFace face && args[1] instanceof Integer distance) {
                return world.getBlockAt(
                    x + face.getModX() * distance,
                    y + face.getModY() * distance,
                    z + face.getModZ() * distance
                );
            }
            if (args.length == 3
                && args[0] instanceof Integer dx
                && args[1] instanceof Integer dy
                && args[2] instanceof Integer dz) {
                return world.getBlockAt(x + dx, y + dy, z + dz);
            }
            throw new UnsupportedOperationException("Unimplemented method 'getRelative'");
        }
    }
}
