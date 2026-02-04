package org.patchbukkit.registry;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.jspecify.annotations.Nullable;

import com.google.gson.JsonObject;
import patchbukkit.registry.GetRegistryDataResponse;
import patchbukkit.registry.RegistryType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PatchBukkitRegistryAccess implements RegistryAccess {
    private static final Map<RegistryKey<?>, Registry<?>> instances = new ConcurrentHashMap<>();

    /**
    * Maps a RegistryKey to (native registry name, factory function).
    * Add an entry here for each registry you support.
    */
    private static final Map<RegistryKey<?>, RegistryFactory<?, ?>> FACTORIES = Map.of(
        RegistryKey.SOUND_EVENT, new RegistryFactory<>(
            RegistryType.SOUND_EVENT,
            response -> response.getSoundEvent().getSoundEventsList(),
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

    @Override
    public <T extends Keyed> @Nullable Registry<T> getRegistry(Class<T> type) {
        final RegistryKey<T> registryKey = byType(type);
        return this.getRegistry(registryKey);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> registryKey) {
        if (registryKey == null) return null;

        return (Registry<T>) instances.computeIfAbsent(registryKey, key -> {
            RegistryFactory<?, ?> factoryEntry = FACTORIES.get(key);
            if (factoryEntry != null) {
                return buildRegistry(factoryEntry);
            }
            // Return an empty registry for unsupported types
            return PatchBukkitRegistry.empty((RegistryKey) key);
        });
    }

    @SuppressWarnings("unchecked")
    private static <P, B extends Keyed> PatchBukkitRegistry<P, B> buildRegistry(RegistryFactory<P, B> factory) {
        return new PatchBukkitRegistry<>(
                factory.registryType(),
                factory.extractor(),
                factory.factory()
        );
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T extends Keyed> @Nullable RegistryKey<T> byType(final Class<T> type) {
        return (RegistryKey<T>) LegacyRegistryIdentifiers.CLASS_TO_KEY_MAP.get(type);
    }
}
