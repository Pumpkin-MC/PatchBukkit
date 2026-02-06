package org.patchbukkit.world;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public final class PatchBukkitBlockState {
    private PatchBukkitBlockState() {
    }

    public static BlockState create(PatchBukkitWorld world, int x, int y, int z, String blockKey) {
        return (BlockState) Proxy.newProxyInstance(
            PatchBukkitBlockState.class.getClassLoader(),
            new Class<?>[]{BlockState.class},
            new Handler(world, x, y, z, blockKey)
        );
    }

    private static final class Handler implements InvocationHandler {
        private final PatchBukkitWorld world;
        private final int x;
        private final int y;
        private final int z;
        private final String blockKey;

        private Handler(PatchBukkitWorld world, int x, int y, int z, String blockKey) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockKey = blockKey;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            switch (name) {
                case "getBlock":
                    return world.getBlockAt(x, y, z);
                case "getWorld":
                    return world;
                case "getLocation":
                    return new Location(world, x, y, z);
                case "getX":
                    return x;
                case "getY":
                    return y;
                case "getZ":
                    return z;
                case "getType":
                    return PatchBukkitBlock.queryMaterial(world, x, y, z, blockKey);
                case "getBlockData":
                    return PatchBukkitBlock.queryMaterial(world, x, y, z, blockKey).createBlockData();
                case "update":
                    return false;
                case "toString":
                    String resolvedKey = PatchBukkitBlock.queryBlockKey(world, x, y, z, blockKey);
                    return "PatchBukkitBlockState{" + resolvedKey + " @ " + x + "," + y + "," + z + "}";
                default:
                    throw new UnsupportedOperationException("Unimplemented method '" + name + "'");
            }
        }
    }
}
