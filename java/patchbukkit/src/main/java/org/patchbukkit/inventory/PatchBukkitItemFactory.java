package org.patchbukkit.inventory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

public class PatchBukkitItemFactory {
    public static final ItemFactory INSTANCE = createFactory();

    private static ItemFactory createFactory() {
        return (ItemFactory) Proxy.newProxyInstance(
            ItemFactory.class.getClassLoader(),
            new Class<?>[] { ItemFactory.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getItemMeta".equals(name) || "createItemMeta".equals(name) || "asMetaFor".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof Material mat && mat == Material.AIR) {
                        return null;
                    }
                    return createMeta();
                }
                if ("isApplicable".equals(name)) {
                    if (args != null && args.length == 2 && args[0] instanceof ItemMeta meta) {
                        ItemStack stack = args[1] instanceof ItemStack s ? s
                            : (args[1] instanceof Material m ? new ItemStack(m) : null);
                        if (stack == null) {
                            return false;
                        }
                        try {
                            for (Enchantment ench : meta.getEnchants().keySet()) {
                                if (!ench.canEnchantItem(stack)) {
                                    return false;
                                }
                            }
                            return true;
                        } catch (Throwable ignored) {
                            return false;
                        }
                    }
                    return false;
                }
                if ("asMetaFor".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof ItemMeta meta) {
                        return copyMeta(meta);
                    }
                    return createMeta();
                }
                if ("getDefaultLeatherColor".equals(name)) {
                    return org.bukkit.Color.fromRGB(0xA06540);
                }
                if ("createItemStack".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof String input) {
                        Material mat = Material.matchMaterial(input);
                        if (mat != null && mat.isItem()) {
                            return new PatchBukkitItemStack(mat, 1);
                        }
                        throw new IllegalArgumentException("Unknown material: " + input);
                    }
                    throw new IllegalArgumentException("Material name cannot be null");
                }
                if ("getSpawnEgg".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof org.bukkit.entity.EntityType type) {
                        Material egg = Material.matchMaterial(type.name() + "_SPAWN_EGG");
                        if (egg != null) {
                            return egg;
                        }
                    }
                    return null;
                }
                if ("displayName".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof ItemStack stack) {
                        try {
                            if (stack.hasItemMeta()) {
                                Component custom = stack.getItemMeta().displayName();
                                if (custom != null && !custom.equals(Component.empty())) {
                                    return custom;
                                }
                            }
                        } catch (Throwable ignored) {}
                        return Component.translatable(stack.getType().translationKey());
                    }
                    return Component.empty();
                }
                if ("equals".equals(name)) {
                    if (args != null && args.length == 2) {
                        return Objects.equals(args[0], args[1]);
                    }
                    return false;
                }
                if ("ensureServerConform".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof ItemStack stack) {
                        if (stack instanceof PatchBukkitItemStack) {
                            return stack;
                        }
                        try {
                            return new PatchBukkitItemStack(stack.getType(), stack.getAmount());
                        } catch (Throwable ignored) {
                            return new PatchBukkitItemStack(Material.AIR, 0);
                        }
                    }
                    return new PatchBukkitItemStack(Material.AIR, 0);
                }
                if ("isItemEmpty".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof ItemStack stack) {
                        return PatchBukkitPlayerInventory.isItemEmpty(stack);
                    }
                    return true;
                }
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == double.class || returnType == float.class) return 0.0;
                return null;
            }
        );
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public static ItemMeta createMeta() {
        return copyMeta(null);
    }

    @SuppressWarnings("unchecked")
    static ItemMeta copyMeta(ItemMeta source) {
        Map<String, Object> state = new HashMap<>();
        org.patchbukkit.persistence.PatchBukkitPersistentDataContainer pdc =
            new org.patchbukkit.persistence.PatchBukkitPersistentDataContainer();
        Map<NamespacedKey, Object> customTags = new HashMap<>();
        if (source != null) {
            try {
                if (source.hasDisplayName()) state.put("displayName", source.getDisplayName());
                if (source.hasItemName()) state.put("itemName", source.getItemName());
                if (source.hasLocalizedName()) state.put("localizedName", source.getLocalizedName());
                if (source.hasLore()) state.put("lore", new ArrayList<>(source.getLore()));
                if (source.hasCustomModelData()) state.put("customModelData", source.getCustomModelData());
                if (source.hasEnchantable()) state.put("enchantable", source.getEnchantable());
                if (source.hasEnchants()) state.put("enchants", new java.util.LinkedHashMap<>(source.getEnchants()));
                if (!source.getItemFlags().isEmpty()) state.put("itemFlags", new HashSet<>(source.getItemFlags()));
                if (source.isUnbreakable()) state.put("unbreakable", true);
                if (source.isHideTooltip()) state.put("hideTooltip", true);
                if (source.isFireResistant()) state.put("fireResistant", true);
                if (source.isGlider()) state.put("glider", true);
                if (source.hasMaxStackSize()) state.put("maxStackSize", source.getMaxStackSize());
                if (source.hasRarity()) state.put("rarity", source.getRarity());
                if (source.hasEnchantmentGlintOverride()) state.put("glintOverride", source.getEnchantmentGlintOverride());
                if (source.hasTooltipStyle()) state.put("tooltipStyle", source.getTooltipStyle());
                if (source.hasItemModel()) state.put("itemModel", source.getItemModel());
                if (source.hasDamageResistant()) state.put("damageResistant", source.getDamageResistant());
                if (source.hasUseRemainder()) state.put("useRemainder", source.getUseRemainder());
                source.getPersistentDataContainer().copyTo(pdc, true);
            } catch (Throwable ignored) {}
        }
        return (ItemMeta) Proxy.newProxyInstance(
            ItemMeta.class.getClassLoader(),
            new Class<?>[] { ItemMeta.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("hasDisplayName".equals(name) || "hasCustomName".equals(name)) return state.containsKey("displayName");
                if ("getDisplayName".equals(name)) return state.get("displayName");
                if ("customName".equals(name) && (args == null || args.length == 0)) {
                    String legacy = (String) state.get("displayName");
                    return legacy != null ? LEGACY.deserialize(legacy) : Component.empty();
                }
                if (("customName".equals(name) || "displayName".equals(name)) && args != null && args.length > 0) {
                    setString(state, "displayName", args[0] instanceof Component c ? LEGACY.serialize(c) : null);
                    return null;
                }
                if ("setDisplayName".equals(name)) {
                    setString(state, "displayName", args != null && args.length > 0 ? (String) args[0] : null);
                    return null;
                }
                if ("getDisplayNameComponent".equals(name)) {
                    String legacy = (String) state.get("displayName");
                    return legacy != null
                        ? net.md_5.bungee.api.chat.TextComponent.fromLegacyText(legacy)
                        : new BaseComponent[0];
                }
                if ("setDisplayNameComponent".equals(name)) {
                    BaseComponent[] comps = args != null && args.length > 0 ? (BaseComponent[]) args[0] : null;
                    setString(state, "displayName",
                        comps != null && comps.length > 0 ? BaseComponent.toLegacyText(comps) : null);
                    return null;
                }
                if ("hasItemName".equals(name)) return state.containsKey("itemName");
                if ("getItemName".equals(name)) return state.get("itemName");
                if ("itemName".equals(name) && (args == null || args.length == 0)) {
                    String legacy = (String) state.get("itemName");
                    return legacy != null ? LEGACY.deserialize(legacy) : Component.empty();
                }
                if ("itemName".equals(name) || "setItemName".equals(name)) {
                    setString(state, "itemName", componentArg(args));
                    return null;
                }
                if ("hasLocalizedName".equals(name)) return state.containsKey("localizedName");
                if ("getLocalizedName".equals(name)) return state.get("localizedName");
                if ("setLocalizedName".equals(name)) {
                    setString(state, "localizedName", args != null && args.length > 0 ? (String) args[0] : null);
                    return null;
                }
                if ("hasLore".equals(name)) return state.containsKey("lore");
                if ("getLore".equals(name)) {
                    List<String> lore = (List<String>) state.get("lore");
                    return lore != null ? new ArrayList<>(lore) : null;
                }
                if ("lore".equals(name) && (args == null || args.length == 0)) {
                    List<String> lore = (List<String>) state.get("lore");
                    if (lore == null) return null;
                    List<Component> out = new ArrayList<>(lore.size());
                    for (String line : lore) out.add(LEGACY.deserialize(line));
                    return out;
                }
                if ("lore".equals(name) || "setLore".equals(name)) {
                    List<String> lines = null;
                    if (args != null && args.length > 0 && args[0] instanceof List<?> in) {
                        lines = new ArrayList<>(in.size());
                        for (Object line : in) {
                            lines.add(line instanceof Component c ? LEGACY.serialize(c) : String.valueOf(line));
                        }
                    }
                    if (lines != null) state.put("lore", lines);
                    else state.remove("lore");
                    return null;
                }
                if ("getLoreComponents".equals(name)) {
                    List<String> lore = (List<String>) state.get("lore");
                    if (lore == null) return null;
                    List<BaseComponent[]> out = new ArrayList<>(lore.size());
                    for (String line : lore) out.add(net.md_5.bungee.api.chat.TextComponent.fromLegacyText(line));
                    return out;
                }
                if ("setLoreComponents".equals(name)) {
                    List<String> lines = null;
                    if (args != null && args.length > 0 && args[0] instanceof List<?> in) {
                        lines = new ArrayList<>(in.size());
                        for (Object group : in) {
                            lines.add(group instanceof BaseComponent[] comps
                                ? BaseComponent.toLegacyText(comps) : String.valueOf(group));
                        }
                    }
                    if (lines != null) state.put("lore", lines);
                    else state.remove("lore");
                    return null;
                }
                if ("hasCustomModelData".equals(name)) return state.containsKey("customModelData");
                if ("getCustomModelData".equals(name)) {
                    Integer value = (Integer) state.get("customModelData");
                    return value != null ? value : 0;
                }
                if ("setCustomModelData".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof Integer value) state.put("customModelData", value);
                    else state.remove("customModelData");
                    return null;
                }
                if ("hasEnchantable".equals(name)) return state.containsKey("enchantable");
                if ("getEnchantable".equals(name)) {
                    Integer value = (Integer) state.get("enchantable");
                    return value != null ? value : 0;
                }
                if ("setEnchantable".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof Integer value) state.put("enchantable", value);
                    else state.remove("enchantable");
                    return null;
                }
                if ("hasEnchants".equals(name)) {
                    Map<Enchantment, Integer> enchants = enchants(state);
                    return !enchants.isEmpty();
                }
                if ("hasEnchant".equals(name)) {
                    return args != null && args.length > 0 && enchants(state).containsKey(args[0]);
                }
                if ("getEnchantLevel".equals(name)) {
                    if (args == null || args.length == 0) return 0;
                    return enchants(state).getOrDefault(args[0], 0);
                }
                if ("getEnchants".equals(name)) {
                    return new java.util.LinkedHashMap<>(enchants(state));
                }
                if ("addEnchant".equals(name)) {
                    if (args == null || args.length < 3 || !(args[0] instanceof Enchantment ench)) return false;
                    int level = args[1] instanceof Integer i ? i : 0;
                    boolean ignore = args[2] instanceof Boolean b && b;
                    if (!ignore && (level < 1 || level > ench.getMaxLevel())) return false;
                    enchants(state).put(ench, level);
                    return true;
                }
                if ("removeEnchant".equals(name)) {
                    if (args == null || args.length == 0) return false;
                    return enchants(state).remove(args[0]) != null;
                }
                if ("removeEnchantments".equals(name)) {
                    enchants(state).clear();
                    return null;
                }
                if ("hasConflictingEnchant".equals(name)) {
                    if (args == null || args.length == 0 || !(args[0] instanceof Enchantment ench)) return false;
                    for (Enchantment existing : enchants(state).keySet()) {
                        if (!existing.equals(ench)
                            && (existing.conflictsWith(ench) || ench.conflictsWith(existing))) {
                            return true;
                        }
                    }
                    return false;
                }
                if ("addItemFlags".equals(name)) {
                    if (args != null) {
                        Set<ItemFlag> flags = flags(state);
                        for (Object arg : args) {
                            if (arg instanceof ItemFlag[] arr) flags.addAll(List.of(arr));
                            else if (arg instanceof ItemFlag flag) flags.add(flag);
                        }
                    }
                    return null;
                }
                if ("removeItemFlags".equals(name)) {
                    if (args != null) {
                        Set<ItemFlag> flags = flags(state);
                        for (Object arg : args) {
                            if (arg instanceof ItemFlag[] arr) flags.removeAll(List.of(arr));
                            else if (arg instanceof ItemFlag flag) flags.remove(flag);
                        }
                    }
                    return null;
                }
                if ("getItemFlags".equals(name)) return new HashSet<>(flags(state));
                if ("hasItemFlag".equals(name)) {
                    return args != null && args.length > 0 && flags(state).contains(args[0]);
                }
                if ("isUnbreakable".equals(name)) return Boolean.TRUE.equals(state.get("unbreakable"));
                if ("setUnbreakable".equals(name)) {
                    state.put("unbreakable", args != null && args.length > 0 && Boolean.TRUE.equals(args[0]));
                    return null;
                }
                if ("isHideTooltip".equals(name)) return Boolean.TRUE.equals(state.get("hideTooltip"));
                if ("setHideTooltip".equals(name)) {
                    state.put("hideTooltip", args != null && args.length > 0 && Boolean.TRUE.equals(args[0]));
                    return null;
                }
                if ("isFireResistant".equals(name)) return Boolean.TRUE.equals(state.get("fireResistant"));
                if ("setFireResistant".equals(name)) {
                    state.put("fireResistant", args != null && args.length > 0 && Boolean.TRUE.equals(args[0]));
                    return null;
                }
                if ("isGlider".equals(name)) return Boolean.TRUE.equals(state.get("glider"));
                if ("setGlider".equals(name)) {
                    state.put("glider", args != null && args.length > 0 && Boolean.TRUE.equals(args[0]));
                    return null;
                }
                if ("hasMaxStackSize".equals(name)) return state.containsKey("maxStackSize");
                if ("getMaxStackSize".equals(name)) {
                    Integer value = (Integer) state.get("maxStackSize");
                    return value != null ? value : 0;
                }
                if ("setMaxStackSize".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof Integer value) state.put("maxStackSize", value);
                    else state.remove("maxStackSize");
                    return null;
                }
                if ("hasRarity".equals(name)) return state.containsKey("rarity");
                if ("getRarity".equals(name)) return state.get("rarity");
                if ("setRarity".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof ItemRarity rarity) state.put("rarity", rarity);
                    else state.remove("rarity");
                    return null;
                }
                if ("hasEnchantmentGlintOverride".equals(name)) return state.containsKey("glintOverride");
                if ("getEnchantmentGlintOverride".equals(name)) return state.get("glintOverride");
                if ("setEnchantmentGlintOverride".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof Boolean value) state.put("glintOverride", value);
                    else state.remove("glintOverride");
                    return null;
                }
                if ("hasTooltipStyle".equals(name)) return state.containsKey("tooltipStyle");
                if ("getTooltipStyle".equals(name)) return state.get("tooltipStyle");
                if ("setTooltipStyle".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof NamespacedKey key) state.put("tooltipStyle", key);
                    else state.remove("tooltipStyle");
                    return null;
                }
                if ("hasItemModel".equals(name)) return state.containsKey("itemModel");
                if ("getItemModel".equals(name)) return state.get("itemModel");
                if ("setItemModel".equals(name)) {
                    if (args != null && args.length > 0 && args[0] instanceof NamespacedKey key) state.put("itemModel", key);
                    else state.remove("itemModel");
                    return null;
                }
                if ("hasDamageResistant".equals(name)) return state.containsKey("damageResistant");
                if ("getDamageResistant".equals(name)) return state.get("damageResistant");
                if ("setDamageResistant".equals(name)) {
                    if (args != null && args.length > 0 && args[0] != null) state.put("damageResistant", args[0]);
                    else state.remove("damageResistant");
                    return null;
                }
                if ("hasUseRemainder".equals(name)) return state.containsKey("useRemainder");
                if ("getUseRemainder".equals(name)) return state.get("useRemainder");
                if ("setUseRemainder".equals(name)) {
                    if (args != null && args.length > 0 && args[0] != null) state.put("useRemainder", args[0]);
                    else state.remove("useRemainder");
                    return null;
                }
                if ("getPersistentDataContainer".equals(name)) return pdc;
                if ("getCustomTagContainer".equals(name)) return customTagContainer(customTags);
                if ("serialize".equals(name)) return serializeMeta(state);
                if ("clone".equals(name)) return copyMeta((ItemMeta) proxy);
                if ("equals".equals(name)) {
                    if (args == null || args.length == 0) return false;
                    if (proxy == args[0]) return true;
                    if (!(args[0] instanceof ItemMeta other)) return false;
                    try {
                        ItemMeta mine = (ItemMeta) proxy;
                        return Objects.equals(mine.getDisplayName(), other.getDisplayName())
                            && Objects.equals(mine.getLore(), other.getLore())
                            && Objects.equals(mine.getEnchants(), other.getEnchants())
                            && Objects.equals(mine.getItemFlags(), other.getItemFlags());
                    } catch (Throwable ignored) {
                        return false;
                    }
                }
                if ("hashCode".equals(name)) {
                    try {
                        ItemMeta mine = (ItemMeta) proxy;
                        return Objects.hash(mine.getDisplayName(), mine.getLore(), mine.getEnchants(), mine.getItemFlags());
                    } catch (Throwable ignored) {
                        return 0;
                    }
                }
                if ("toString".equals(name)) return "PatchBukkitItemMeta" + state;
                Class<?> returnType = method.getReturnType();
                if (returnType == boolean.class) return false;
                if (returnType == int.class) return 0;
                if (returnType == long.class) return 0L;
                if (returnType == double.class || returnType == float.class) return 0.0;
                return null;
            }
        );
    }

    private static void setString(Map<String, Object> state, String key, String value) {
        if (value != null) state.put(key, value);
        else state.remove(key);
    }

    private static String componentArg(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) return null;
        if (args[0] instanceof Component component) return LEGACY.serialize(component);
        return String.valueOf(args[0]);
    }

    @SuppressWarnings("unchecked")
    private static Map<Enchantment, Integer> enchants(Map<String, Object> state) {
        return (Map<Enchantment, Integer>) state.computeIfAbsent("enchants",
            k -> new java.util.LinkedHashMap<Enchantment, Integer>());
    }

    @SuppressWarnings("unchecked")
    private static Set<ItemFlag> flags(Map<String, Object> state) {
        return (Set<ItemFlag>) state.computeIfAbsent("itemFlags",
            k -> new HashSet<ItemFlag>());
    }

    private static Map<String, Object> serializeMeta(Map<String, Object> state) {
        Map<String, Object> out = new HashMap<>();
        out.put("meta-type", "UNSPECIFIC");
        for (Map.Entry<String, Object> entry : state.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> enchantMap) {
                Map<String, Integer> flat = new HashMap<>();
                for (Map.Entry<?, ?> e : enchantMap.entrySet()) {
                    if (e.getKey() instanceof Enchantment ench && e.getValue() instanceof Integer level) {
                        flat.put(ench.getKey().asString(), level);
                    }
                }
                out.put("enchants", flat);
            } else if (value instanceof Set<?> flagSet) {
                List<String> flat = new ArrayList<>();
                for (Object flag : flagSet) flat.add(String.valueOf(flag));
                out.put("ItemFlags", flat);
            } else if (value instanceof NamespacedKey key) {
                out.put(entry.getKey(), key.asString());
            } else {
                out.put(entry.getKey(), value);
            }
        }
        return out;
    }

    private static org.bukkit.inventory.meta.tags.CustomItemTagContainer customTagContainer(
            Map<NamespacedKey, Object> customTags) {
        return new org.bukkit.inventory.meta.tags.CustomItemTagContainer() {
            @Override
            public <T, Z> void setCustomTag(NamespacedKey key,
                    org.bukkit.inventory.meta.tags.ItemTagType<T, Z> type, Z value) {
                customTags.put(key, value);
            }

            @Override
            public <T, Z> boolean hasCustomTag(NamespacedKey key,
                    org.bukkit.inventory.meta.tags.ItemTagType<T, Z> type) {
                return customTags.containsKey(key);
            }

            @Override
            @SuppressWarnings("unchecked")
            public <T, Z> Z getCustomTag(NamespacedKey key,
                    org.bukkit.inventory.meta.tags.ItemTagType<T, Z> type) {
                try {
                    return (Z) customTags.get(key);
                } catch (ClassCastException e) {
                    return null;
                }
            }

            @Override
            public void removeCustomTag(NamespacedKey key) {
                customTags.remove(key);
            }

            @Override
            public boolean isEmpty() {
                return customTags.isEmpty();
            }

            @Override
            public org.bukkit.inventory.meta.tags.ItemTagAdapterContext getAdapterContext() {
                return new org.bukkit.inventory.meta.tags.ItemTagAdapterContext() {
                    @Override
                    public org.bukkit.inventory.meta.tags.CustomItemTagContainer newTagContainer() {
                        return customTagContainer(new HashMap<>());
                    }
                };
            }
        };
    }
}
