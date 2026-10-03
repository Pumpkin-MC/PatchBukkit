package org.patchbukkit.registry;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public final class PatchBukkitItemType {

    private PatchBukkitItemType() {}

    public static ItemType create(Material material) {
        if (material == null) return null;

        return (ItemType) Proxy.newProxyInstance(
                PatchBukkitItemType.class.getClassLoader(),
                new Class<?>[]{ItemType.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getKey".equals(name) || "key".equals(name)) {
                        return material.getKey();
                    }
                    if ("asMaterial".equals(name)) {
                        return material;
                    }
                    // Material.getMaxStackSize()/getMaxDurability()/isEdible()/isRecord()
                    // all delegate to ItemType, and Material.asItemType() returns this very
                    // proxy, so calling back into Material here recurses until the stack
                    // overflows. Return safe defaults until item data is bridged from the host.
                    if ("getMaxStackSize".equals(name)) {
                        return 64;
                    }
                    if ("getMaxDurability".equals(name)) {
                        return 0;
                    }
                    if ("isEdible".equals(name)) {
                        return false;
                    }
                    if ("isRecord".equals(name)) {
                        return false;
                    }
                    if ("createItemStack".equals(name)) {
                        int amount = (args != null && args.length > 0 && args[0] instanceof Integer i) ? i : 1;
                        return new org.patchbukkit.inventory.PatchBukkitItemStack(material, amount);
                    }
                    if ("equals".equals(name) && args != null && args.length == 1) {
                        if (args[0] instanceof Keyed k) {
                            return material.getKey().equals(k.getKey());
                        }
                        return false;
                    }
                    if ("hashCode".equals(name)) {
                        return material.getKey().hashCode();
                    }
                    if ("toString".equals(name)) {
                        return "ItemType{" + material.getKey() + "}";
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
