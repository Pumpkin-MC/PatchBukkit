package org.patchbukkit.registry;

import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.stream.Collectors;

public class PatchBukkitTag<B extends Keyed> implements Tag<B> {

    private final TagKey<B> tagKey;
    private final Set<TypedKey<B>> memberKeys;

    public PatchBukkitTag(TagKey<B> tagKey, RegistryKey<B> registryKey, Set<NamespacedKey> rawKeys) {
        this.tagKey = tagKey;
        this.memberKeys = rawKeys.stream()
                .map(k -> TypedKey.create(registryKey, k))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public TagKey<B> tagKey() {
        return tagKey;
    }

    @Override
    public @Unmodifiable Collection<TypedKey<B>> values() {
        return memberKeys;
    }

    @Override
    public @Unmodifiable Collection<B> resolve(Registry<B> registry) {
        List<B> resolved = new ArrayList<>();
        for (TypedKey<B> key : memberKeys) {
            B value = registry.get(key.key());
            if (value != null) resolved.add(value);
        }
        return Collections.unmodifiableList(resolved);
    }

    @Override
    public boolean contains(TypedKey<B> valueKey) {
        return memberKeys.contains(valueKey);
    }

    @Override
    public RegistryKey<B> registryKey() {
        return tagKey.registryKey();
    }
}
