package org.patchbukkit.persistence;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PatchBukkitPersistentDataContainer implements PersistentDataContainer {
    private final Map<NamespacedKey, Object> storage = new HashMap<>();

    @Override
    public <T, Z> void set(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
        storage.put(key, value);
    }

    @Override
    public <T, Z> boolean has(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
        return storage.containsKey(key);
    }

    @Override
    public boolean has(@NotNull NamespacedKey key) {
        return storage.containsKey(key);
    }

    @Override
    public <T, Z> @Nullable Z get(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type) {
        return (Z) storage.get(key);
    }

    @Override
    public <T, Z> @NotNull Z getOrDefault(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z defaultValue) {
        Z val = get(key, type);
        return val != null ? val : defaultValue;
    }

    @Override
    public @NotNull Set<NamespacedKey> getKeys() {
        return storage.keySet();
    }

    @Override
    public void remove(@NotNull NamespacedKey key) {
        storage.remove(key);
    }

    @Override
    public boolean isEmpty() {
        return storage.isEmpty();
    }

    public int getSize() {
        return storage.size();
    }

    @Override
    public void copyTo(@NotNull PersistentDataContainer other, boolean replace) {
        // Raw values carry no type info, so a faithful copy only works
        // between our own containers; foreign ones are left untouched.
        if (other instanceof PatchBukkitPersistentDataContainer ours && ours != this) {
            if (replace) {
                ours.storage.clear();
            }
            ours.storage.putAll(this.storage);
        }
    }

    @Override
    public @NotNull PersistentDataAdapterContext getAdapterContext() {
        return new PersistentDataAdapterContext() {
            @Override
            public @NotNull PersistentDataContainer newPersistentDataContainer() {
                return new PatchBukkitPersistentDataContainer();
            }
        };
    }

    @Override
    public void readFromBytes(byte @NotNull [] bytes, boolean clear) {
    }

    @Override
    public byte @NotNull [] serializeToBytes() {
        return new byte[0];
    }
}
