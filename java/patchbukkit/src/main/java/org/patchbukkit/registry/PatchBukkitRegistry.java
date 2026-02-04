package org.patchbukkit.registry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.tag.Tag;
import io.papermc.paper.registry.tag.TagKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.registry.GetRegistryDataRequest;
import patchbukkit.registry.GetRegistryDataResponse;
import patchbukkit.registry.RegistryType;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class PatchBukkitRegistry<P, B extends Keyed> implements Registry<B> {

    private final Map<NamespacedKey, B> entries = new LinkedHashMap<>();
    private final Map<String, PatchBukkitTag<B>> tags = new LinkedHashMap<>();

    public PatchBukkitRegistry(
            RegistryType registryType,
            Function<GetRegistryDataResponse, List<P>> extractor,
            Function<P, B> factory
    ) {
        if (registryType == null) return;

        GetRegistryDataRequest request = GetRegistryDataRequest.newBuilder()
                .setRegistry(registryType)
                .build();

        GetRegistryDataResponse response = NativeBridgeFfi.getRegistryData(request);
        if (response == null) return;

        List<P> protoEntries = extractor.apply(response);
        for (P protoEntry : protoEntries) {
            B value = factory.apply(protoEntry);
            if (value != null) {
                entries.put(value.getKey(), value);
            }
        }
    }


    private void loadTags(JsonObject tagMap, RegistryKey<B> registryKey) {
        for (Map.Entry<String, JsonElement> entry : tagMap.entrySet()) {
            String tagName = entry.getKey();
            JsonArray members = entry.getValue().getAsJsonArray();

            Set<NamespacedKey> memberKeys = new LinkedHashSet<>();
            for (JsonElement member : members) {
                String raw = member.getAsString();
                // Tag values may or may not have "minecraft:" prefix
                NamespacedKey key;
                if (raw.contains(":")) {
                    key = NamespacedKey.fromString(raw);
                } else {
                    key = new NamespacedKey(NamespacedKey.MINECRAFT, raw);
                }
                if (key != null) memberKeys.add(key);
            }

            NamespacedKey tagKeyName = tagName.contains(":")
                    ? NamespacedKey.fromString(tagName)
                    : new NamespacedKey(NamespacedKey.MINECRAFT, tagName);

            if (tagKeyName == null) continue;

            TagKey<B> tagKey = TagKey.create(registryKey, tagKeyName);
            tags.put(tagName, new PatchBukkitTag<>(tagKey, registryKey, memberKeys));
        }
    }

    @Override
    public @Nullable B get(NamespacedKey key) {
        return entries.get(key);
    }

    @Override
    public @Nullable NamespacedKey getKey(B value) {
        return value.getKey();
    }

    @Override
    public boolean hasTag(TagKey<B> key) {
        return tags.containsKey(key.key().asString());
    }

    @Override
    public @NonNull Tag<B> getTag(TagKey<B> key) {
        PatchBukkitTag<B> tag = tags.get(key.key().asString());
        if (tag == null) {
            throw new NoSuchElementException("Unknown tag: " + key.key().asString());
        }
        return tag;
    }

    public static <B extends Keyed> PatchBukkitRegistry<Object, B> empty(RegistryKey<B> registryKey) {
        return new PatchBukkitRegistry<>(
                null,
                response -> Collections.emptyList(),
                obj -> null
        );
    }

    @Override
    public @NonNull Collection<Tag<B>> getTags() {
        return Collections.unmodifiableCollection(tags.values());
    }

    @Override
    public @NonNull Stream<B> stream() {
        return entries.values().stream();
    }

    @Override
    public @NonNull Stream<NamespacedKey> keyStream() {
        return entries.keySet().stream();
    }

    @Override
    public int size() {
        return entries.size();
    }

    @Override
    public @NonNull Iterator<B> iterator() {
        return Collections.unmodifiableCollection(entries.values()).iterator();
    }
}
