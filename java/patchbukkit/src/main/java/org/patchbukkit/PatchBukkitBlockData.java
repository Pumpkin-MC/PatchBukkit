package org.patchbukkit;

import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class PatchBukkitBlockData {

    private PatchBukkitBlockData() {}

    public static BlockData newData(Material material, BlockType type, String data) {
        final Material mat = material != null ? material : (type != null ? type.asMaterial() : null);
        if (mat == null || mat.isLegacy()) {
            throw new IllegalArgumentException("Invalid block material: " + (material != null ? material : data));
        }

        // NOTE: this used to attempt Class.forName("net.minecraft.SharedConstants") /
        // "org.bukkit.craftbukkit.block.data.CraftBlockData" on every call. Neither class can
        // ever exist in this process (PatchBukkit ships no NMS/OBC), so both lookups were
        // guaranteed ClassNotFoundExceptions costing several microseconds per call on the
        // hottest path in the API (Block#getType() -> createBlockData). Removed.

        final String stateData;
        if (data != null && !data.isEmpty()) {
            if (data.startsWith("minecraft:") || data.contains(":")) {
                stateData = data;
            } else if (data.startsWith("[")) {
                stateData = mat.getKey().toString() + data;
            } else if (!data.contains("[")) {
                stateData = mat.getKey().toString();
            } else {
                int bracketIdx = data.indexOf('[');
                stateData = mat.getKey().toString() + data.substring(bracketIdx);
            }
        } else {
            stateData = mat.getKey().toString();
        }

        return (BlockData) Proxy.newProxyInstance(
                PatchBukkitBlockData.class.getClassLoader(),
                new Class<?>[]{BlockData.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getMaterial".equals(name)) {
                        return mat;
                    }
                    if ("getAsString".equals(name)) {
                        return stateData;
                    }
                    if ("clone".equals(name) || "copy".equals(name)) {
                        return newData(mat, type, stateData);
                    }
                    if ("matches".equals(name) && args != null && args.length == 1) {
                        if (args[0] instanceof BlockData other) {
                            return mat.equals(other.getMaterial());
                        }
                        return false;
                    }
                    if ("merge".equals(name)) {
                        return proxy;
                    }
                    if ("isSupported".equals(name)) {
                        return true;
                    }
                    if ("isFacingAllowed".equals(name)) {
                        return true;
                    }
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        if (args[0] instanceof BlockData other) {
                            return stateData.equals(other.getAsString());
                        }
                        return false;
                    }
                    if ("hashCode".equals(name)) {
                        return stateData.hashCode();
                    }
                    if ("toString".equals(name)) {
                        return stateData;
                    }
                    if (method.isDefault()) {
                        return InvocationHandler.invokeDefault(proxy, method, args);
                    }

                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) return false;
                    if (returnType == int.class || returnType == short.class || returnType == long.class || returnType == byte.class) return 0;
                    if (returnType == float.class || returnType == double.class) return 0.0f;
                    return null;
                }
        );
    }
}
