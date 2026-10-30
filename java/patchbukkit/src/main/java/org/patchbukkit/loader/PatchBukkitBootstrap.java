package org.patchbukkit.loader;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;

public class PatchBukkitBootstrap {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkitBootstrap");

    public static Plugin loadPlugin(File jarFile) throws Exception {
        if (jarFile == null || !jarFile.exists()) {
            return null;
        }
        registerEmbeddedPluginDatapackTags(List.of(jarFile));
        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        if (pm == null) {
            return null;
        }
        return pm.loadPlugin(jarFile);
    }

    public static boolean bootstrapPlugins(String pluginsDirPath) {
        org.patchbukkit.bootstrap.SchedulerBridge.setInPluginBootstrap(true);
        try {
            File pluginsDir = new File(pluginsDirPath);
            if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
                LOGGER.info("[PatchBukkit] Plugins directory does not exist: " + pluginsDirPath);
                return true;
            }

            List<File> jarFiles = new ArrayList<>();
            File[] files = pluginsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".jar")) {
                        jarFiles.add(file);
                    }
                }
            }

            if (jarFiles.isEmpty()) {
                LOGGER.info("[PatchBukkit] No plugin JAR files found in: " + pluginsDirPath);
                return true;
            }

            Set<String> disabledPlugins = new HashSet<>();
            boolean debugBridge = false;
            try {
                var config = patchbukkit.bridge.NativeBridgeFfi.getPatchBukkitConfig(patchbukkit.common.EmptyRequest.newBuilder().build());
                if (config != null) {
                    for (String d : config.getDisabledPluginsList()) {
                        disabledPlugins.add(d.toLowerCase(Locale.ENGLISH));
                    }
                    debugBridge = config.getDebugBridge();
                }
            } catch (Throwable ignored) {}

            if (debugBridge && !disabledPlugins.isEmpty()) {
                LOGGER.info("[PatchBukkit] Disabled plugins from config: " + disabledPlugins);
            }

            List<File> activeJars = new ArrayList<>();
            for (File jarFile : jarFiles) {
                String name = getPluginName(jarFile);
                if (name == null) {
                    continue;
                }
                if (disabledPlugins.contains(name.toLowerCase(Locale.ENGLISH))) {
                    LOGGER.info("[PatchBukkit] Plugin '" + name + "' is disabled in configuration, skipping.");
                    continue;
                }
                activeJars.add(jarFile);
            }

            registerEmbeddedPluginDatapackTags(activeJars);

            org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
            io.papermc.paper.plugin.manager.PaperPluginManagerImpl paperPm = null;
            if (pm instanceof org.bukkit.plugin.SimplePluginManager spm
                    && spm.paperPluginManager instanceof io.papermc.paper.plugin.manager.PaperPluginManagerImpl ppm) {
                paperPm = ppm;
            } else if (io.papermc.paper.plugin.manager.PaperPluginManagerImpl.getInstance() != null) {
                paperPm = io.papermc.paper.plugin.manager.PaperPluginManagerImpl.getInstance();
            }

            List<Plugin> loadedPlugins = new ArrayList<>();
            if (paperPm != null) {
                Plugin[] batch = paperPm.loadPlugins(activeJars.toArray(new File[0]));
                if (batch != null) {
                    Collections.addAll(loadedPlugins, batch);
                }
            } else if (pm != null) {
                for (File jar : activeJars) {
                    try {
                        Plugin p = pm.loadPlugin(jar);
                        if (p != null) loadedPlugins.add(p);
                    } catch (Throwable t) {
                        LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to load plugin " + jar.getName(), t);
                    }
                }
            }

            // Enable all instantiated plugins in dependency order
            if (pm != null) {
                for (Plugin plugin : loadedPlugins) {
                    if (plugin != null && !plugin.isEnabled()) {
                        try {
                            ClassLoader oldCl = Thread.currentThread().getContextClassLoader();
                            try {
                                Thread.currentThread().setContextClassLoader(plugin.getClass().getClassLoader());
                                pm.enablePlugin(plugin);
                                org.patchbukkit.bootstrap.CommandBridge.checkAndSyncIfNeeded();
                            } finally {
                                Thread.currentThread().setContextClassLoader(oldCl);
                            }
                            LOGGER.info("[PatchBukkit] Enabled plugin: " + plugin.getName());
                        } catch (Throwable t) {
                            LOGGER.log(Level.SEVERE, "[PatchBukkit] Error enabling plugin " + plugin.getName(), t);
                        }
                    }
                }
            }

            try {
                io.papermc.paper.command.brigadier.PaperCommands.INSTANCE.setValid();
                io.papermc.paper.plugin.lifecycle.event.LifecycleEventRunner.INSTANCE.callReloadableRegistrarEvent(
                    io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents.COMMANDS,
                    io.papermc.paper.command.brigadier.PaperCommands.INSTANCE,
                    org.bukkit.plugin.Plugin.class,
                    io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent.Cause.INITIAL
                );
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Error running Paper LifecycleEvents.COMMANDS", t);
            }

            if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()
                    && org.patchbukkit.bootstrap.HeadlessPaperServer.get() != null) {
                org.patchbukkit.bootstrap.CommandBridge.syncAll(org.patchbukkit.bootstrap.HeadlessPaperServer.get());
            }

            org.patchbukkit.network.VirtualChannelManager.getInstance().refreshPacketInterceptionState();

            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Fatal error during bootstrap", t);
            return false;
        } finally {
            org.patchbukkit.bootstrap.SchedulerBridge.setInPluginBootstrap(false);
            org.patchbukkit.bootstrap.SchedulerBridge.heartbeat();
        }
    }

    public static boolean disablePlugins() {
        try {
            org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
            if (pm != null) {
                pm.disablePlugins();
            }
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Error during disablePlugins", t);
            return false;
        }
    }

    private static String getPluginName(File jarFile) {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("paper-plugin.yml");
            if (entry == null) {
                entry = jar.getJarEntry("plugin.yml");
            }
            if (entry == null) return null;
            try (InputStream in = jar.getInputStream(entry)) {
                PluginDescriptionFile desc = new PluginDescriptionFile(in);
                return desc.getName();
            }
        } catch (Throwable ignored) {
            return null;
        }
    }





    private static final java.util.regex.Pattern DATAPACK_TAG_PATTERN =
        java.util.regex.Pattern.compile("(?:^|.*/)data/([^/]+)/tags/(blocks?|items?|fluids?|entity_types?)/(.+)\\.json$");

    private record RawTagEntry(String namespace, String path, com.google.gson.JsonObject json) {}

    /**
     * Scans plugin JARs for embedded datapack tag definitions (such as Veinminer's
     * {@code veinminer_dp/data/c/tags/block/ores.json}) and merges them into the live
     * NMS {@link net.minecraft.core.registries.BuiltInRegistries} before plugins load.
     */
    private static void registerEmbeddedPluginDatapackTags(Collection<File> jarFiles) {
        if (!org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted() || jarFiles == null || jarFiles.isEmpty()) {
            return;
        }
        Map<String, List<RawTagEntry>> byRegistry = new HashMap<>();
        for (File jarFile : jarFiles) {
            if (jarFile == null || !jarFile.exists()) continue;
            try (JarFile jar = new JarFile(jarFile)) {
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !entry.getName().endsWith(".json")) continue;
                    var matcher = DATAPACK_TAG_PATTERN.matcher(entry.getName());
                    if (!matcher.matches()) continue;
                    String namespace = matcher.group(1);
                    String kind = matcher.group(2);
                    String tagPath = matcher.group(3);
                    String normKind = switch (kind) {
                        case "block", "blocks" -> "block";
                        case "item", "items" -> "item";
                        case "fluid", "fluids" -> "fluid";
                        case "entity_type", "entity_types" -> "entity_type";
                        default -> null;
                    };
                    if (normKind == null) continue;
                    try (InputStream in = jar.getInputStream(entry);
                         java.io.InputStreamReader reader = new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)) {
                        var parsed = com.google.gson.JsonParser.parseReader(reader);
                        if (parsed != null && parsed.isJsonObject()) {
                            byRegistry.computeIfAbsent(normKind, k -> new ArrayList<>())
                                .add(new RawTagEntry(namespace, tagPath, parsed.getAsJsonObject()));
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }

        if (byRegistry.containsKey("block")) {
            applyTagsToRegistry(net.minecraft.core.registries.BuiltInRegistries.BLOCK, byRegistry.get("block"));
        }
        if (byRegistry.containsKey("item")) {
            applyTagsToRegistry(net.minecraft.core.registries.BuiltInRegistries.ITEM, byRegistry.get("item"));
        }
        if (byRegistry.containsKey("fluid")) {
            applyTagsToRegistry(net.minecraft.core.registries.BuiltInRegistries.FLUID, byRegistry.get("fluid"));
        }
        if (byRegistry.containsKey("entity_type")) {
            applyTagsToRegistry(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE, byRegistry.get("entity_type"));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void applyTagsToRegistry(
        net.minecraft.core.Registry<T> registry,
        List<RawTagEntry> rawEntries
    ) {
        if (rawEntries == null || rawEntries.isEmpty()) return;
        try {
            Map<net.minecraft.tags.TagKey<T>, net.minecraft.core.HolderSet.Named<T>> frozenTags = null;
            if (registry instanceof net.minecraft.core.MappedRegistry<T> mapped) {
                try {
                    java.lang.reflect.Field ftField = net.minecraft.core.MappedRegistry.class.getDeclaredField("frozenTags");
                    ftField.setAccessible(true);
                    frozenTags = (Map<net.minecraft.tags.TagKey<T>, net.minecraft.core.HolderSet.Named<T>>) ftField.get(mapped);
                } catch (Throwable ignored) {}
            }

            Map<net.minecraft.tags.TagKey<T>, List<net.minecraft.core.Holder<T>>> merged = new LinkedHashMap<>();
            final Map<net.minecraft.tags.TagKey<T>, net.minecraft.core.HolderSet.Named<T>> frozenRef = frozenTags;
            registry.getTags().forEach(named -> {
                List<net.minecraft.core.Holder<T>> list = new ArrayList<>();
                named.forEach(list::add);
                merged.put(named.key(), list);
                if (frozenRef != null) {
                    frozenRef.putIfAbsent(named.key(), named);
                }
            });

            // Two passes so tags referencing other newly-added plugin tags also resolve
            for (int pass = 0; pass < 2; pass++) {
                for (RawTagEntry raw : rawEntries) {
                    net.minecraft.resources.Identifier tagId =
                        net.minecraft.resources.Identifier.fromNamespaceAndPath(raw.namespace(), raw.path());
                    net.minecraft.tags.TagKey<T> tagKey = net.minecraft.tags.TagKey.create(registry.key(), tagId);
                    boolean replace = raw.json().has("replace") && raw.json().get("replace").getAsBoolean();
                    List<net.minecraft.core.Holder<T>> holders = (replace || pass > 0)
                        ? new ArrayList<>()
                        : new ArrayList<>(merged.getOrDefault(tagKey, List.of()));
                    if (raw.json().has("values") && raw.json().get("values").isJsonArray()) {
                        for (com.google.gson.JsonElement el : raw.json().getAsJsonArray("values")) {
                            String valStr = null;
                            if (el.isJsonPrimitive()) {
                                valStr = el.getAsString();
                            } else if (el.isJsonObject() && el.getAsJsonObject().has("id")) {
                                valStr = el.getAsJsonObject().get("id").getAsString();
                            }
                            if (valStr == null || valStr.isEmpty()) continue;
                            if (valStr.startsWith("#")) {
                                net.minecraft.resources.Identifier refId =
                                    net.minecraft.resources.Identifier.parse(valStr.substring(1));
                                net.minecraft.tags.TagKey<T> refKey =
                                    net.minecraft.tags.TagKey.create(registry.key(), refId);
                                for (net.minecraft.core.Holder<T> h : merged.getOrDefault(refKey, List.of())) {
                                    if (!holders.contains(h)) {
                                        holders.add(h);
                                    }
                                }
                            } else {
                                net.minecraft.resources.Identifier elemId =
                                    net.minecraft.resources.Identifier.parse(valStr);
                                registry.get(elemId).ifPresent(holder -> {
                                    if (!holders.contains(holder)) {
                                        holders.add(holder);
                                    }
                                });
                            }
                        }
                    }
                    merged.put(tagKey, holders);
                }
            }

            registry.prepareTagReload(new net.minecraft.tags.TagLoader.LoadResult<>(registry.key(), merged)).apply();

            if (frozenRef != null) {
                registry.getTags().forEach(named -> frozenRef.putIfAbsent(named.key(), named));
            }
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to apply embedded plugin datapack tags to " + registry.key(), t);
        }
    }
}
