package org.patchbukkit.registry;

import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.HumanEntity;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.view.builder.InventoryViewBuilder;
import org.jetbrains.annotations.NotNull;

/**
 * Placeholder menu type synthesized by the registry fallback.
 *
 * <p>Bukkit resolves every vanilla menu key during {@code MenuType} and
 * {@code InventoryType} class initialization. Without an entry the lookup
 * throws and poisons both classes for the rest of the JVM lifetime, which
 * breaks player construction and every in-game command. Real menu behavior
 * (opening inventories) is not implemented yet; {@code create} and
 * {@code builder} throw until it is.
 */
public class PatchBukkitMenuType implements MenuType.Typed<InventoryView, InventoryViewBuilder<InventoryView>> {

    private final NamespacedKey key;

    public PatchBukkitMenuType(@NotNull NamespacedKey key) {
        this.key = key;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return key;
    }

    @Override
    public InventoryView create(@NotNull HumanEntity player, @NotNull String title) {
        throw new UnsupportedOperationException("Menus are not yet implemented (menu: " + key + ")");
    }

    @Override
    public InventoryView create(@NotNull HumanEntity player, @NotNull Component title) {
        throw new UnsupportedOperationException("Menus are not yet implemented (menu: " + key + ")");
    }

    @Override
    public InventoryViewBuilder<InventoryView> builder() {
        throw new UnsupportedOperationException("Menus are not yet implemented (menu: " + key + ")");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <V extends InventoryView, B extends InventoryViewBuilder<V>> MenuType.Typed<V, B> typed(@NotNull Class<V> viewClass) {
        if (viewClass.isAssignableFrom(InventoryView.class)) {
            return (MenuType.Typed<V, B>) this;
        }
        throw new IllegalArgumentException("Unsupported view class: " + viewClass + " for menu " + key);
    }

    @Override
    public MenuType.Typed<InventoryView, InventoryViewBuilder<InventoryView>> typed() {
        return this;
    }

    @Override
    public @NotNull Class<? extends InventoryView> getInventoryViewClass() {
        return InventoryView.class;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof MenuType other)) return false;
        return key.equals(other.getKey());
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    @Override
    public String toString() {
        return "PatchBukkitMenuType{" + key + "}";
    }
}
