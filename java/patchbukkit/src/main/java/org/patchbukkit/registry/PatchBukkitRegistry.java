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
import org.patchbukkit.bridge.NativePatchBukkit;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

public class PatchBukkitRegistry<B extends Keyed> implements Registry<B> {

    private final Map<NamespacedKey, B> entries = new LinkedHashMap<>();
    private final Map<String, PatchBukkitTag<B>> tags = new LinkedHashMap<>();

    /**
     * Build a registry by fetching JSON from the native side and parsing
     * entries and tags from the combined response.
     *
     * Expected JSON shape:
     * {
     *   "entries": [{"name": "...", "id": ...}, ...],
     *   "tags": {"minecraft:logs": ["minecraft:oak_log", ...], ...}
     * }
     *
     * @param registryName The native registry name (e.g. "sound_event")
     * @param registryKey  The Paper RegistryKey for this registry
     * @param factory      Converts a single JsonObject into a B instance
     */
    public PatchBukkitRegistry(
            String registryName,
            RegistryKey<B> registryKey,
            Function<JsonObject, B> factory
    ) {
        String json = NativePatchBukkit.getRegistryData(registryName);
        if (json == null) return;

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        JsonArray entriesArray = root.getAsJsonArray("entries");
        if (entriesArray != null) {
            for (JsonElement element : entriesArray) {
                B value = factory.apply(element.getAsJsonObject());
                if (value != null) {
                    entries.put(value.getKey(), value);
                }
            }
        }

        JsonElement tagsElement = root.get("tags");
        if (tagsElement != null && tagsElement.isJsonObject()) {
            loadTags(tagsElement.getAsJsonObject(), registryKey);
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
