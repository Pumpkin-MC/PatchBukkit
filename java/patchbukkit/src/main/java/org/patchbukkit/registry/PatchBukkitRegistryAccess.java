package org.patchbukkit.registry;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.jspecify.annotations.Nullable;
import org.patchbukkit.bridge.NativePatchBukkit;

import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class PatchBukkitRegistryAccess implements RegistryAccess {
    private static final Map<RegistryKey<?>, Registry<?>> instances = new ConcurrentHashMap<>();

    /**
    * Maps a RegistryKey to (native registry name, factory function).
    * Add an entry here for each registry you support.
    */
    private static final Map<RegistryKey<?>, RegistryFactory<?>> FACTORIES = Map.of(
        RegistryKey.SOUND_EVENT, new RegistryFactory<>(
            "sound_event",
            RegistryKey.SOUND_EVENT,
            json -> new PatchBukkitSound(
                json.get("name").getAsString(),
                json.get("id").getAsInt()
            )
        )
    );

    private record RegistryFactory<B extends Keyed>(
        String nativeRegistryName,
        RegistryKey<B> registryKey,
        Function<JsonObject, B> factory
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
            RegistryFactory<?> factoryEntry = FACTORIES.get(key);
            if (factoryEntry != null) {
                return buildRegistry(factoryEntry);
            }

            // We return an empty registry for unsupported types, since if we don't the server will crash immediately when the registry class is first loaded
            return new PatchBukkitRegistry<>(
                key.key().value(),
                (RegistryKey) key,
                json -> null
            );
        });
    }

    private static <B extends Keyed> PatchBukkitRegistry<B> buildRegistry(RegistryFactory<B> factory) {
        return new PatchBukkitRegistry<>(
            factory.nativeRegistryName(),
            factory.registryKey(),
            factory.factory()
        );
    }

    @SuppressWarnings({"unchecked", "deprecation"})
    public static <T extends Keyed> @Nullable RegistryKey<T> byType(final Class<T> type) {
        return (RegistryKey<T>) LegacyRegistryIdentifiers.CLASS_TO_KEY_MAP.get(type);
    }
}
