package org.patchbukkit.loader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.lang.reflect.Constructor;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class PatchBukkitBootstrap {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkitBootstrap");

    public static Plugin loadPlugin(File jarFile) throws Exception {
        PluginHolder holder = parsePluginHolder(jarFile);
        if (holder == null) {
            return null;
        }
        registerEmbeddedPluginDatapackTags(List.of(jarFile));
        if (jarFile.getParentFile() != null) {
            healVeinminerGroupsConfig(jarFile.getParentFile());
        }
        Map<String, PluginHolder> holders = new LinkedHashMap<>();
        holders.put(holder.name.toLowerCase(Locale.ENGLISH), holder);
        loadAndRegisterPlugin(holder, holders);
        return holder.pluginInstance;
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
            findJarsRecursive(pluginsDir, jarFiles);

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

            Map<String, PluginHolder> holders = new LinkedHashMap<>();

            for (File jarFile : jarFiles) {
                try {
                    PluginHolder holder = parsePluginHolder(jarFile);
                    if (holder != null) {
                        if (disabledPlugins.contains(holder.name.toLowerCase(Locale.ENGLISH))) {
                            LOGGER.info("[PatchBukkit] Plugin '" + holder.name + "' is disabled in configuration, skipping.");
                            continue;
                        }
                        holders.put(holder.name.toLowerCase(Locale.ENGLISH), holder);
                    }
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to parse plugin JAR " + jarFile.getName(), t);
                }
            }

            List<PluginHolder> loadOrder = computeLoadOrder(holders);

            List<File> activeJars = new ArrayList<>(loadOrder.size());
            for (PluginHolder holder : loadOrder) {
                activeJars.add(holder.jarFile);
            }
            registerEmbeddedPluginDatapackTags(activeJars);
            healVeinminerGroupsConfig(pluginsDir);

            for (PluginHolder holder : loadOrder) {
                try {
                    loadAndRegisterPlugin(holder, holders);
                } catch (Throwable t) {
                    LOGGER.log(Level.SEVERE, "[PatchBukkit] Failed to load plugin " + holder.name, t);
                }
            }

            // Enable all instantiated plugins in dependency order
            org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
            if (pm != null) {
                for (PluginHolder holder : loadOrder) {
                    if (holder.pluginInstance != null) {
                        try {
                            pm.enablePlugin(holder.pluginInstance);
                            LOGGER.info("[PatchBukkit] Enabled plugin: " + holder.name);
                        } catch (Throwable t) {
                            LOGGER.log(Level.SEVERE, "[PatchBukkit] Error enabling plugin " + holder.name, t);
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
                    && org.patchbukkit.bootstrap.HeadlessPaperServer.get() instanceof org.patchbukkit.bootstrap.PumpkinDedicatedServer pds) {
                org.patchbukkit.bootstrap.CommandBridge.syncAll(pds);
            }

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
                org.bukkit.plugin.Plugin[] plugins = pm.getPlugins();
                if (plugins != null) {
                    for (int i = plugins.length - 1; i >= 0; i--) {
                        org.bukkit.plugin.Plugin plugin = plugins[i];
                        if (plugin != null && plugin.isEnabled()) {
                            try {
                                pm.disablePlugin(plugin);
                                LOGGER.info("[PatchBukkit] Disabled plugin: " + plugin.getName());
                            } catch (Throwable t) {
                                LOGGER.log(Level.SEVERE, "[PatchBukkit] Error disabling plugin " + plugin.getName(), t);
                            }
                        }
                    }
                }
            }
            for (PatchBukkitPluginClassLoader loader : new ArrayList<>(PatchBukkitPluginClassLoader.ALL_LOADERS)) {
                try {
                    loader.close();
                } catch (Throwable ignored) {}
            }
            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Error during disablePlugins", t);
            return false;
        }
    }

    private static void findJarsRecursive(File dir, List<File> result) {
        if (dir == null || !dir.isDirectory()) return;
        if (dir.getName().equals("patchbukkit-libs")) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                if (!file.getName().equals("patchbukkit-libs")) {
                    findJarsRecursive(file, result);
                }
            } else if (file.getName().endsWith(".jar")) {
                result.add(file);
            }
        }
    }

    private static PluginHolder parsePluginHolder(File jarFile) throws Exception {
        try (JarFile jar = new JarFile(jarFile)) {
            JarEntry entry = jar.getJarEntry("plugin.yml");
            if (entry == null) {
                entry = jar.getJarEntry("paper-plugin.yml");
            }
            if (entry == null) {
                return null;
            }

            PluginDescriptionFile description;
            try (InputStream in = jar.getInputStream(entry)) {
                description = new PluginDescriptionFile(in);
            }

            PluginHolder holder = new PluginHolder();
            holder.jarFile = jarFile;
            holder.description = description;
            holder.name = description.getName();
            holder.mainClass = description.getMain();
            List<String> depends = new ArrayList<>(description.getDepend() != null ? description.getDepend() : Collections.emptyList());
            List<String> softDepends = new ArrayList<>(description.getSoftDepend() != null ? description.getSoftDepend() : Collections.emptyList());
            holder.provides = description.getProvides() != null ? description.getProvides() : Collections.emptyList();
            holder.libraries = description.getLibraries() != null ? description.getLibraries() : Collections.emptyList();

            // Support Paper's paper-plugin.yml dependencies.server map format
            if ("paper-plugin.yml".equals(entry.getName())) {
                try (InputStream rawIn = jar.getInputStream(entry)) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Object obj = yaml.load(rawIn);
                    if (obj instanceof Map<?, ?> data && data.get("dependencies") instanceof Map<?, ?> depsMap) {
                        for (String phase : List.of("server", "bootstrap")) {
                            if (depsMap.get(phase) instanceof Map<?, ?> phaseDeps) {
                                for (Map.Entry<?, ?> depEntry : phaseDeps.entrySet()) {
                                    if (depEntry.getKey() == null) continue;
                                    String depName = depEntry.getKey().toString();
                                    boolean required = true;
                                    if (depEntry.getValue() instanceof Map<?, ?> cfg && cfg.get("required") instanceof Boolean reqBool) {
                                        required = reqBool;
                                    }
                                    if (required) {
                                        if (!depends.contains(depName)) depends.add(depName);
                                    } else {
                                        if (!softDepends.contains(depName)) softDepends.add(depName);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            holder.depends = depends;
            holder.softDepends = softDepends;
            return holder;
        }
    }

    private static List<PluginHolder> computeLoadOrder(Map<String, PluginHolder> holders) {
        List<PluginHolder> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (PluginHolder holder : holders.values()) {
            visitHolder(holder, holders, visited, visiting, order);
        }

        return order;
    }

    private static void visitHolder(
        PluginHolder holder,
        Map<String, PluginHolder> holders,
        Set<String> visited,
        Set<String> visiting,
        List<PluginHolder> order
    ) {
        String key = holder.name.toLowerCase(Locale.ENGLISH);
        if (visited.contains(key)) return;
        if (visiting.contains(key)) {
            System.err.println("[PatchBukkit] Circular dependency detected involving plugin: " + holder.name);
            return;
        }

        visiting.add(key);

        for (String dep : holder.depends) {
            PluginHolder depHolder = holders.get(dep.toLowerCase(Locale.ENGLISH));
            if (depHolder != null) {
                visitHolder(depHolder, holders, visited, visiting, order);
            }
        }

        for (String dep : holder.softDepends) {
            PluginHolder depHolder = holders.get(dep.toLowerCase(Locale.ENGLISH));
            if (depHolder != null) {
                visitHolder(depHolder, holders, visited, visiting, order);
            }
        }

        visiting.remove(key);
        visited.add(key);
        order.add(holder);
    }

    private static void loadAndRegisterPlugin(PluginHolder holder, Map<String, PluginHolder> allHolders) throws Exception {
        LinkedHashSet<URL> extraUrls = new LinkedHashSet<>();

        if (!holder.libraries.isEmpty()) {
            File libsDir = new File(holder.jarFile.getParentFile(), "patchbukkit-libs");
            if (!libsDir.exists()) libsDir.mkdirs();
            String libCoords = String.join("\n", holder.libraries);
            List<File> resolvedLibs = LibraryResolver.resolveLibraries(libCoords, libsDir);
            for (File lib : resolvedLibs) {
                if (lib != null && lib.exists()) {
                    extraUrls.add(lib.toURI().toURL());
                }
            }
        }

        PatchBukkitPluginClassLoader classLoader = new PatchBukkitPluginClassLoader(
            PatchBukkitBootstrap.class.getClassLoader(),
            holder.jarFile,
            extraUrls.toArray(new URL[0])
        );

        Class<?> jarClass = Class.forName(holder.mainClass, true, classLoader);
        Class<? extends Plugin> pluginClass = jarClass.asSubclass(Plugin.class);
        Plugin plugin = pluginClass.getDeclaredConstructor().newInstance();

        if (plugin instanceof JavaPlugin javaPlugin) {
            classLoader.init(javaPlugin);
        }

        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        if (pm instanceof org.bukkit.plugin.SimplePluginManager spm) {
            try {
                if (spm.paperPluginManager instanceof io.papermc.paper.plugin.manager.PaperPluginManagerImpl paperPm) {
                    paperPm.loadPlugin(plugin);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register plugin with PaperPluginManager: " + holder.name, t);
            }
            try {
                java.lang.reflect.Field lnField = org.bukkit.plugin.SimplePluginManager.class.getDeclaredField("lookupNames");
                lnField.setAccessible(true);
                @SuppressWarnings("unchecked")
                Map<String, Plugin> lookupNames = (Map<String, Plugin>) lnField.get(spm);
                if (lookupNames != null) {
                    lookupNames.put(plugin.getName().replace(' ', '_').toLowerCase(Locale.ENGLISH), plugin);
                }
                java.lang.reflect.Field pField = org.bukkit.plugin.SimplePluginManager.class.getDeclaredField("plugins");
                pField.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<Plugin> plugins = (List<Plugin>) pField.get(spm);
                if (plugins != null && !plugins.contains(plugin)) {
                    plugins.add(plugin);
                }
            } catch (Throwable ignored) {}
        } else if (pm instanceof io.papermc.paper.plugin.manager.PaperPluginManagerImpl paperPm) {
            try {
                paperPm.loadPlugin(plugin);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register plugin with PaperPluginManager: " + holder.name, t);
            }
        }

        try {
            plugin.onLoad();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Error during onLoad() for " + holder.name, t);
        }

        holder.pluginInstance = plugin;
        registerPluginPermissions(holder.description);
        registerPluginCommands(plugin, holder.description);
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

    /**
     * Repairs {@code Veinminer/groups.json} if a prior run stripped {@code #c:ores} from the
     * default {@code Ores} group when the {@code c:ores} tag was not yet registered.
     */
    private static void healVeinminerGroupsConfig(File pluginsDir) {
        List<File> candidates = new ArrayList<>(2);
        if (pluginsDir != null) {
            candidates.add(new File(new File(pluginsDir, "Veinminer"), "groups.json"));
        }
        File cwdGroups = new File("plugins/Veinminer/groups.json");
        if (!candidates.contains(cwdGroups)) {
            candidates.add(cwdGroups);
        }
        for (File groupsFile : candidates) {
            if (!groupsFile.exists() || !groupsFile.isFile()) continue;
            try {
                String content = java.nio.file.Files.readString(groupsFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
                var parsed = com.google.gson.JsonParser.parseString(content);
                if (parsed == null || !parsed.isJsonArray()) continue;
                com.google.gson.JsonArray arr = parsed.getAsJsonArray();
                boolean modified = false;
                for (com.google.gson.JsonElement el : arr) {
                    if (!el.isJsonObject()) continue;
                    com.google.gson.JsonObject group = el.getAsJsonObject();
                    if (group.has("name")
                            && "Ores".equalsIgnoreCase(group.get("name").getAsString())
                            && group.has("blocks")
                            && group.get("blocks").isJsonArray()
                            && group.getAsJsonArray("blocks").isEmpty()) {
                        group.getAsJsonArray("blocks").add("#c:ores");
                        modified = true;
                    }
                }
                if (modified) {
                    com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().serializeNulls().create();
                    java.nio.file.Files.writeString(
                        groupsFile.toPath(),
                        gson.toJson(arr),
                        java.nio.charset.StandardCharsets.UTF_8
                    );
                    LOGGER.info("[PatchBukkit] Restored #c:ores in " + groupsFile.getPath());
                }
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Registers the permissions declared in a plugin's {@code plugin.yml}. Without this, Bukkit treats
     * every declared node as unknown, whose default is "op". That makes e.g. GrimAC's
     * {@code grim.exempt} (default {@code false}) true for every operator, silently disabling all
     * checks for them.
     */
    private static void registerPluginPermissions(PluginDescriptionFile description) {
        org.bukkit.plugin.PluginManager pm = Bukkit.getPluginManager();
        if (pm == null || description.getPermissions() == null) return;
        for (org.bukkit.permissions.Permission permission : description.getPermissions()) {
            try {
                if (pm.getPermission(permission.getName()) == null) {
                    pm.addPermission(permission);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register permission " + permission.getName(), t);
            }
        }
    }

    private static PluginCommand createPluginCommand(String name, Plugin plugin) {
        try {
            Constructor<PluginCommand> ctor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);
            return ctor.newInstance(name, plugin);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to instantiate PluginCommand for " + name, t);
            return null;
        }
    }

    public static void registerPluginCommands(Plugin plugin, PluginDescriptionFile description) {
        if (plugin == null || description == null) return;
        org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();
        if (commandMap != null) {
            Map<String, Map<String, Object>> commands = description.getCommands();
            if (commands != null) {
                for (Map.Entry<String, Map<String, Object>> entry : commands.entrySet()) {
                    String cmdName = entry.getKey();
                    Map<String, Object> cmdData = entry.getValue();

                    PluginCommand pluginCmd = createPluginCommand(cmdName, plugin);
                    if (pluginCmd != null) {
                        if (cmdData != null) {
                            if (cmdData.containsKey("description")) {
                                pluginCmd.setDescription(String.valueOf(cmdData.get("description")));
                            }
                            if (cmdData.containsKey("usage")) {
                                pluginCmd.setUsage(String.valueOf(cmdData.get("usage")));
                            }
                            if (cmdData.containsKey("permission")) {
                                pluginCmd.setPermission(String.valueOf(cmdData.get("permission")));
                            }
                            if (cmdData.containsKey("permission-message")) {
                                pluginCmd.setPermissionMessage(String.valueOf(cmdData.get("permission-message")));
                            }
                            if (cmdData.get("aliases") instanceof List<?> aliases) {
                                List<String> strAliases = new ArrayList<>();
                                for (Object a : aliases) {
                                    if (a != null) strAliases.add(String.valueOf(a));
                                }
                                pluginCmd.setAliases(strAliases);
                            }
                        }

                        commandMap.register(cmdName, description.getName(), pluginCmd);
                        System.out.println("[PatchBukkit] Registered command: /" + cmdName + " for plugin " + description.getName());
                    }
                }
            }
        }
    }

    private static class PluginHolder {
        File jarFile;
        PluginDescriptionFile description;
        String name;
        String mainClass;
        List<String> depends = Collections.emptyList();
        List<String> softDepends = Collections.emptyList();
        List<String> provides = Collections.emptyList();
        List<String> libraries = Collections.emptyList();
        Plugin pluginInstance;
    }
}
