package org.patchbukkit.registry;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.jspecify.annotations.Nullable;

import patchbukkit.registry.GetRegistryDataResponse;
import patchbukkit.registry.RegistryType;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PatchBukkitRegistryAccess extends io.papermc.paper.registry.PaperRegistryAccess {
    private static final Map<RegistryKey<?>, Registry<?>> instances = new ConcurrentHashMap<>();

    /**
    * Maps a RegistryKey to (native registry name, factory function).
    * Add an entry here for each registry you support.
    */
    private static final Map<RegistryKey<?>, RegistryFactory<?, ?>> FACTORIES = Map.of(
        RegistryKey.SOUND_EVENT, new RegistryFactory<>(
            RegistryType.SOUND_EVENT,
            response -> (response != null && response.hasSoundEvent()) ? response.getSoundEvent().getSoundEventsList() : Collections.emptyList(),
            data -> new PatchBukkitSound(
                data.getName(),
                data.getId()
            )
        )
    );

    private record RegistryFactory<P, B extends Keyed>(
            RegistryType registryType,
            Function<GetRegistryDataResponse, List<P>> extractor,
            Function<P, B> factory
    ) {}

    public <T extends Keyed> @Nullable Registry<T> getRegistry(Class<T> type) {
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.usesRealRegistries()) {
            // Paper hands out delayed registries here for Registry.* constants that are
            // initialized during Bootstrap, before the datapack registries are loaded.
            return super.getRegistry(type);
        }
        final RegistryKey<T> registryKey = byType(type);
        return this.getRegistry(registryKey);
    }

    @Override
    public <M> void lockReferenceHolders(final net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<M>> resourceKey) {
        try {
            final var entry = io.papermc.paper.registry.PaperRegistries.getEntry(resourceKey);
            if (entry != null) {
                Registry<?> reg = super.getRegistry(entry.apiKey());
                if (reg instanceof org.bukkit.craftbukkit.CraftRegistry<?, ?> craftRegistry) {
                    craftRegistry.lockReferenceHolders();
                }
            }
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        if (registryKey == null) return null;

        // With the real Paper server booted, all registries (items, blocks, damage types,
        // sounds, ...) are the real CraftRegistry instances backed by the loaded NMS registries.
        // Pumpkin targets the same Minecraft version, so keys and ids match.
        if (org.patchbukkit.bootstrap.HeadlessPaperServer.usesRealRegistries()) {
            return super.getRegistry(registryKey);
        }

        if (!FACTORIES.containsKey(registryKey) && !RegistryKey.ITEM.equals(registryKey) && !RegistryKey.BLOCK.equals(registryKey) && !RegistryKey.DAMAGE_TYPE.equals(registryKey)) {
            try {
                Registry<T> superReg = super.getRegistry(registryKey);
                if (superReg != null) {
                    return superReg;
                }
            } catch (Throwable ignored) {}
        }

        Registry<?> existing = instances.get(registryKey);
        if (existing != null) {
            return (Registry<T>) existing;
        }

        PatchBukkitRegistry reg;
        RegistryFactory<?, ?> factoryEntry = FACTORIES.get(registryKey);
        if (factoryEntry != null) {
            reg = new PatchBukkitRegistry(factoryEntry.registryType(), factoryEntry.extractor(), factoryEntry.factory(), registryKey);
        } else {
            reg = new PatchBukkitRegistry(registryKey);
        }

        Registry<?> winner = instances.putIfAbsent(registryKey, reg);
        return (Registry<T>) (winner != null ? winner : reg);
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T extends Keyed> @Nullable RegistryKey<T> byType(final Class<T> type) {
        if (type == null) return null;
        return (RegistryKey<T>) LegacyRegistryIdentifiers.CLASS_TO_KEY_MAP.get(type);
    }
}
