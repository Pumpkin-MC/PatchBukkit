package org.patchbukkit.loader;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.patchbukkit.PatchBukkitPluginManager;
import org.patchbukkit.command.CommandFactory;
import org.patchbukkit.command.PatchBukkitCommandMap;

public class PatchBukkitBootstrap {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkitBootstrap");

    public static boolean bootstrapPlugins(String pluginsDirPath) {
        try {
            org.patchbukkit.PatchBukkitServer.initServer();
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

            return true;
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Fatal error during bootstrap", t);
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
            holder.depends = description.getDepend() != null ? description.getDepend() : Collections.emptyList();
            holder.softDepends = description.getSoftDepend() != null ? description.getSoftDepend() : Collections.emptyList();
            holder.provides = description.getProvides() != null ? description.getProvides() : Collections.emptyList();
            holder.libraries = description.getLibraries() != null ? description.getLibraries() : Collections.emptyList();
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
        if (pm instanceof PatchBukkitPluginManager patchPm) {
            patchPm.registerPlugin(plugin);
        } else if (pm instanceof org.bukkit.plugin.SimplePluginManager spm) {
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
            org.patchbukkit.PatchBukkitServer.ensureCraftRegistry();
            plugin.onLoad();
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "[PatchBukkit] Error during onLoad() for " + holder.name, t);
        }

        holder.pluginInstance = plugin;
        registerPluginCommands(plugin, holder.description);
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

                    PluginCommand pluginCmd = CommandFactory.create(cmdName, plugin);
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
