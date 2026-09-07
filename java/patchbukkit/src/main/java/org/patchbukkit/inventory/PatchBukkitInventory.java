package org.patchbukkit.inventory;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PatchBukkitInventory implements Inventory {
    private final InventoryHolder holder;
    private final int size;
    private final String title;
    private final InventoryType type;
    private final ItemStack[] contents;
    private int maxStackSize = 64;
    private final Set<HumanEntity> viewers = Collections.synchronizedSet(new HashSet<>());

    public @NotNull String getTitle() {
        return this.title;
    }

    public void addViewer(@NotNull HumanEntity viewer) {
        this.viewers.add(viewer);
    }

    public void removeViewer(@NotNull HumanEntity viewer) {
        this.viewers.remove(viewer);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, int size, @NotNull String title) {
        this(holder, size, title, InventoryType.CHEST);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, @NotNull InventoryType type) {
        this(holder, type.getDefaultSize(), type.getDefaultTitle(), type);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, @NotNull InventoryType type, @NotNull String title) {
        this(holder, type.getDefaultSize(), title, type);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, @NotNull InventoryType type, @NotNull net.kyori.adventure.text.Component title) {
        this(holder, type.getDefaultSize(), net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title), type);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, int size, @NotNull net.kyori.adventure.text.Component title) {
        this(holder, size, net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title), InventoryType.CHEST);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, int size) {
        this(holder, size, "Inventory", InventoryType.CHEST);
    }

    public PatchBukkitInventory(@Nullable InventoryHolder holder, int size, @NotNull String title, @NotNull InventoryType type) {
        this.holder = holder;
        this.size = size;
        this.title = title;
        this.type = type;
        this.contents = new ItemStack[size];
        Arrays.fill(this.contents, ItemStack.empty());
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getMaxStackSize() {
        return this.maxStackSize;
    }

    @Override
    public void setMaxStackSize(int size) {
        this.maxStackSize = size;
    }

    @Override
    public @Nullable ItemStack getItem(int index) {
        if (index < 0 || index >= this.size) return null;
        return this.contents[index];
    }

    @Override
    public void setItem(int index, @Nullable ItemStack item) {
        if (index >= 0 && index < this.size) {
            this.contents[index] = (item != null) ? item.clone() : ItemStack.empty();
        }
    }

    @Override
    public @NotNull HashMap<Integer, ItemStack> addItem(@NotNull ItemStack... items) throws IllegalArgumentException {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        if (items == null) return leftover;

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.isEmpty()) continue;
            ItemStack remaining = item.clone();

            for (int slot = 0; slot < this.size; slot++) {
                ItemStack current = this.contents[slot];
                if (current != null && !current.isEmpty() && current.isSimilar(remaining)) {
                    int canAdd = Math.min(remaining.getAmount(), current.getMaxStackSize() - current.getAmount());
                    if (canAdd > 0) {
                        current.setAmount(current.getAmount() + canAdd);
                        remaining.setAmount(remaining.getAmount() - canAdd);
                        if (remaining.getAmount() <= 0) break;
                    }
                }
            }

            if (remaining.getAmount() > 0) {
                for (int slot = 0; slot < this.size; slot++) {
                    ItemStack current = this.contents[slot];
                    if (current == null || current.isEmpty()) {
                        this.contents[slot] = remaining.clone();
                        remaining.setAmount(0);
                        break;
                    }
                }
            }

            if (remaining.getAmount() > 0) {
                leftover.put(i, remaining);
            }
        }
        return leftover;
    }

    @Override
    public @NotNull HashMap<Integer, ItemStack> removeItem(@NotNull ItemStack... items) throws IllegalArgumentException {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        if (items == null) return leftover;

        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || item.isEmpty()) continue;
            int toRemove = item.getAmount();

            for (int slot = 0; slot < this.size; slot++) {
                ItemStack current = this.contents[slot];
                if (current != null && current.isSimilar(item)) {
                    int remove = Math.min(toRemove, current.getAmount());
                    current.setAmount(current.getAmount() - remove);
                    toRemove -= remove;
                    if (current.getAmount() <= 0) {
                        this.contents[slot] = ItemStack.empty();
                    }
                    if (toRemove <= 0) break;
                }
            }

            if (toRemove > 0) {
                ItemStack rem = item.clone();
                rem.setAmount(toRemove);
                leftover.put(i, rem);
            }
        }
        return leftover;
    }

    @Override
    public @NotNull HashMap<Integer, ItemStack> removeItemAnySlot(@NotNull ItemStack... items) throws IllegalArgumentException {
        return removeItem(items);
    }

    @Override
    public @NotNull ItemStack[] getContents() {
        return this.contents.clone();
    }

    @Override
    public void setContents(@NotNull ItemStack[] items) throws IllegalArgumentException {
        if (items == null) return;
        for (int i = 0; i < this.size; i++) {
            if (i < items.length && items[i] != null) {
                this.contents[i] = items[i].clone();
            } else {
                this.contents[i] = ItemStack.empty();
            }
        }
    }

    @Override
    public @NotNull ItemStack[] getStorageContents() {
        return getContents();
    }

    @Override
    public void setStorageContents(@NotNull ItemStack[] items) throws IllegalArgumentException {
        setContents(items);
    }

    @Override
    public boolean contains(@NotNull Material material) throws IllegalArgumentException {
        return first(material) != -1;
    }

    @Override
    public boolean contains(@Nullable ItemStack item) {
        return first(item) != -1;
    }

    @Override
    public boolean contains(@NotNull Material material, int amount) throws IllegalArgumentException {
        int count = 0;
        for (ItemStack is : this.contents) {
            if (is != null && is.getType() == material) {
                count += is.getAmount();
                if (count >= amount) return true;
            }
        }
        return false;
    }

    @Override
    public boolean contains(@Nullable ItemStack item, int amount) {
        if (item == null) return false;
        int count = 0;
        for (ItemStack is : this.contents) {
            if (is != null && is.isSimilar(item)) {
                count += is.getAmount();
                if (count >= amount) return true;
            }
        }
        return false;
    }

    @Override
    public boolean containsAtLeast(@Nullable ItemStack item, int amount) {
        return contains(item, amount);
    }

    @Override
    public @NotNull HashMap<Integer, ? extends ItemStack> all(@NotNull Material material) throws IllegalArgumentException {
        HashMap<Integer, ItemStack> map = new HashMap<>();
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] != null && this.contents[i].getType() == material) {
                map.put(i, this.contents[i]);
            }
        }
        return map;
    }

    @Override
    public @NotNull HashMap<Integer, ? extends ItemStack> all(@Nullable ItemStack item) {
        HashMap<Integer, ItemStack> map = new HashMap<>();
        if (item == null) return map;
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] != null && this.contents[i].isSimilar(item)) {
                map.put(i, this.contents[i]);
            }
        }
        return map;
    }

    @Override
    public int first(@NotNull Material material) throws IllegalArgumentException {
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] != null && this.contents[i].getType() == material) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int first(@NotNull ItemStack item) {
        if (item == null) return -1;
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] != null && this.contents[i].isSimilar(item)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int firstEmpty() {
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] == null || this.contents[i].isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack is : this.contents) {
            if (is != null && !is.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public void remove(@NotNull Material material) throws IllegalArgumentException {
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] != null && this.contents[i].getType() == material) {
                this.contents[i] = ItemStack.empty();
            }
        }
    }

    @Override
    public void remove(@NotNull ItemStack item) {
        if (item == null) return;
        for (int i = 0; i < this.size; i++) {
            if (this.contents[i] != null && this.contents[i].isSimilar(item)) {
                this.contents[i] = ItemStack.empty();
            }
        }
    }

    @Override
    public void clear(int index) {
        setItem(index, ItemStack.empty());
    }

    @Override
    public void clear() {
        Arrays.fill(this.contents, ItemStack.empty());
    }

    @Override
    public int close() {
        return 0;
    }

    @Override
    public @NotNull List<HumanEntity> getViewers() {
        synchronized (this.viewers) {
            return List.copyOf(this.viewers);
        }
    }

    @Override
    public @NotNull InventoryType getType() {
        return this.type;
    }

    @Override
    public @Nullable InventoryHolder getHolder() {
        return this.holder;
    }

    @Override
    public @Nullable InventoryHolder getHolder(boolean useSnapshot) {
        return this.holder;
    }

    @Override
    public @NotNull ListIterator<ItemStack> iterator() {
        return Arrays.asList(this.contents).listIterator();
    }

    @Override
    public @NotNull ListIterator<ItemStack> iterator(int index) {
        return Arrays.asList(this.contents).listIterator(index);
    }

    @Override
    public @Nullable Location getLocation() {
        return null;
    }
}
