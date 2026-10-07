package org.patchbukkit.loader;

import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class PatchBukkitPluginClassLoader
    extends URLClassLoader
    implements ConfiguredPluginClassLoader
{

    private static final Logger LOGGER = Logger.getLogger("PatchBukkitPluginClassLoader");
    private final PluginDescriptionFile description;
    private final File dataFolder;
    private final File file;
    private JavaPlugin plugin;

    static {
        ClassLoader.registerAsParallelCapable();
        try {
            org.patchbukkit.PatchBukkitServer.ensureCraftRegistry();
        } catch (Throwable ignored) {}
    }

    public static final java.util.Set<PatchBukkitPluginClassLoader> ALL_LOADERS =
        new java.util.concurrent.CopyOnWriteArraySet<>();

    public PatchBukkitPluginClassLoader(ClassLoader parent, File file)
        throws MalformedURLException, InvalidDescriptionException {
        this(parent, file, new URL[0]);
    }

    public PatchBukkitPluginClassLoader(
        ClassLoader parent,
        File file,
        URL[] extraUrls
    ) throws MalformedURLException, InvalidDescriptionException {
        super(buildUrls(file, extraUrls), parent);
        this.file = file;
        this.description = loadDescription(file);
        this.dataFolder = new File(file.getParentFile(), description.getName());
        ALL_LOADERS.add(this);

        File libsDir = new File(file.getParentFile(), "patchbukkit-libs");
        if (!libsDir.exists()) {
            libsDir.mkdirs();
        }

        // NMS/CraftBukkit classes come from the patched Paper server on the system
        // classpath (downloaded at runtime by the Rust side), so nothing to add here.

        // Extract and load nested JARs inside plugin JAR
        for (File nestedJar : extractNestedJars(file, libsDir)) {
            try {
                addURL(nestedJar.toURI().toURL());
            } catch (MalformedURLException ignored) {}
        }

        List<String> libsToResolve = new ArrayList<>();
        if (this.description.getLibraries() != null) {
            libsToResolve.addAll(this.description.getLibraries());
        }
        for (String lib : extractLibraries(file)) {
            if (!libsToResolve.contains(lib)) {
                libsToResolve.add(lib);
            }
        }

        if (!libsToResolve.isEmpty()) {
            List<File> resolved = LibraryResolver.resolveLibraries(
                String.join("\n", libsToResolve),
                libsDir
            );
            for (File lib : resolved) {
                if (lib != null && lib.exists()) {
                    try {
                        addURL(lib.toURI().toURL());
                    } catch (MalformedURLException ignored) {}
                }
            }
        }
    }

    private static List<File> extractNestedJars(File file, File libsDir) {
        List<File> extractedFiles = new ArrayList<>();
        try (JarFile jar = new JarFile(file)) {
            java.util.Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!entry.isDirectory() && name.endsWith(".jar")) {
                    if (name.startsWith("META-INF/jars/") ||
                        name.startsWith("paper-libraries/") ||
                        name.startsWith("BOOT-INF/lib/") ||
                        name.startsWith("lib/") ||
                        name.startsWith("libraries/")) {
                        
                        File targetFile = new File(libsDir, new File(name).getName());
                        if (!targetFile.exists()) {
                            try (InputStream is = jar.getInputStream(entry);
                                 java.io.FileOutputStream fos = new java.io.FileOutputStream(targetFile)) {
                                is.transferTo(fos);
                            }
                        }
                        if (targetFile.exists()) {
                            extractedFiles.add(targetFile);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return extractedFiles;
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractLibraries(File file) {
        List<String> libraries = new ArrayList<>();
        try (JarFile jar = new JarFile(file)) {
            // 1. Try paper-libraries.json
            JarEntry entryJson = jar.getJarEntry("paper-libraries.json");
            if (entryJson != null) {
                try (InputStream is = jar.getInputStream(entryJson)) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Object obj = yaml.load(is);
                    if (obj instanceof Map<?, ?> data) {
                        Object repos = data.get("repositories");
                        if (repos instanceof Map<?, ?> repoMap) {
                            for (Object v : repoMap.values()) {
                                if (v != null && !v.toString().isBlank()) {
                                    String r = v.toString().trim();
                                    if (!libraries.contains("repo:" + r)) {
                                        libraries.add("repo:" + r);
                                    }
                                }
                            }
                        } else if (repos instanceof List<?> repoList) {
                            for (Object o : repoList) {
                                if (o != null) {
                                    String r = o.toString().trim();
                                    if (o instanceof Map<?, ?> m && m.containsKey("url")) {
                                        r = m.get("url").toString().trim();
                                    }
                                    if (!libraries.contains("repo:" + r)) {
                                        libraries.add("repo:" + r);
                                    }
                                }
                            }
                        }
                        Object deps = data.get("dependencies");
                        if (deps instanceof List<?> list) {
                            for (Object o : list) {
                                if (o != null && !libraries.contains(o.toString())) {
                                    libraries.add(o.toString());
                                }
                            }
                        } else if (deps instanceof Map<?, ?> map) {
                            for (Map.Entry<?, ?> e : map.entrySet()) {
                                if (e.getKey() != null && e.getValue() != null) {
                                    String coord = e.getKey().toString() + ":" + e.getValue().toString();
                                    if (!libraries.contains(coord)) {
                                        libraries.add(coord);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 2. Try paper-libraries.list
            JarEntry entryList = jar.getJarEntry("paper-libraries.list");
            if (entryList != null) {
                try (InputStream is = jar.getInputStream(entryList);
                     java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty() && !line.startsWith("#")) {
                            if (!libraries.contains(line)) {
                                libraries.add(line);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }

            // 3. Try paper-plugin.yml or plugin.yml
            JarEntry entryYaml = jar.getJarEntry("paper-plugin.yml");
            if (entryYaml == null) {
                entryYaml = jar.getJarEntry("plugin.yml");
            }
            if (entryYaml != null) {
                try (InputStream is = jar.getInputStream(entryYaml)) {
                    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
                    Object obj = yaml.load(is);
                    if (obj instanceof Map<?, ?> data) {
                        Object libs = data.get("libraries");
                        if (libs instanceof List<?> list) {
                            for (Object o : list) {
                                if (o != null && !libraries.contains(o.toString())) {
                                    libraries.add(o.toString());
                                }
                            }
                        }
                        Object repos = data.get("repositories");
                        if (repos instanceof Map<?, ?> repoMap) {
                            for (Object v : repoMap.values()) {
                                if (v != null && !v.toString().isBlank()) {
                                    String r = v.toString().trim();
                                    if (!libraries.contains("repo:" + r)) {
                                        libraries.add("repo:" + r);
                                    }
                                }
                            }
                        } else if (repos instanceof List<?> repoList) {
                            for (Object o : repoList) {
                                if (o != null) {
                                    String r = o.toString().trim();
                                    if (o instanceof Map<?, ?> m && m.containsKey("url")) {
                                        r = m.get("url").toString().trim();
                                    }
                                    if (!libraries.contains("repo:" + r)) {
                                        libraries.add("repo:" + r);
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return libraries;
    }

    private static URL[] buildUrls(File file, URL[] extraUrls)
        throws MalformedURLException {
        List<URL> urls = new ArrayList<>();
        urls.add(file.toURI().toURL());
        if (extraUrls != null) {
            for (URL url : extraUrls) {
                if (url != null) {
                    urls.add(url);
                }
            }
        }
        return urls.toArray(new URL[0]);
    }

    private static PluginDescriptionFile loadDescription(File file)
        throws InvalidDescriptionException {
        try (JarFile jar = new JarFile(file)) {
            JarEntry entry = jar.getJarEntry("paper-plugin.yml");
            if (entry == null) {
                entry = jar.getJarEntry("plugin.yml");
            }
            if (entry == null) {
                throw new InvalidDescriptionException(
                    "Jar does not contain plugin.yml"
                );
            }
            try (InputStream stream = jar.getInputStream(entry)) {
                return new PluginDescriptionFile(stream);
            }
        } catch (IOException e) {
            throw new InvalidDescriptionException(e);
        }
    }

    /**
     * Child-first class loading: try to load from plugin JAR before delegating to parent.
     * This is critical for plugin classes to be loaded by this classloader.
     */
    @Override
    protected Class<?> loadClass(String name, boolean resolve)
        throws ClassNotFoundException {
        if (name != null && name.indexOf('/') != -1) {
            name = name.replace('/', '.');
        }
        synchronized (getClassLoadingLock(name)) {
            // First, check if already loaded
            Class<?> c = findLoadedClass(name);

            if (c == null) {
                // For plugin-specific classes, try to load from JAR first (child-first)
                // For JDK and server classes, delegate to parent
                if (
                    !name.startsWith("java.") &&
                    !name.startsWith("jdk.") &&
                    !name.startsWith("sun.") &&
                    !name.startsWith("javax.") &&
                    !name.startsWith("org.bukkit.") &&
                    !name.startsWith("io.papermc.") &&
                    !name.startsWith("net.minecraft.") &&
                    !name.startsWith("org.spigotmc.") &&
                    !name.startsWith("com.destroystokyo.paper.") &&
                    // Adventure is provided by the Paper server. Plugins that bundle an
                    // unrelocated copy must still use the server's one (as on real Paper),
                    // otherwise Paper's ServiceLoader providers are "not a subtype".
                    !name.startsWith("net.kyori.") &&
                    !name.startsWith("org.patchbukkit.")
                ) {
                    try {
                        // Try to find in plugin JAR / libraries first
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        // Not in local JAR, check other plugin classloaders in global pool
                        for (PatchBukkitPluginClassLoader other : ALL_LOADERS) {
                            if (other != this) {
                                try {
                                    c = other.findLoadedClass(name);
                                    if (c == null) {
                                        c = other.findClass(name);
                                    }
                                    if (c != null) {
                                        break;
                                    }
                                } catch (ClassNotFoundException ignored) {}
                            }
                        }
                    }
                }

                // If not found in JAR (or is a system/server class), delegate to parent
                if (c == null) {
                    try {
                        c = getParent().loadClass(name);
                    } catch (ClassNotFoundException e) {
                        String remapped = remapLegacyClass(name);
                        if (remapped != null) {
                            try {
                                c = getParent().loadClass(remapped);
                            } catch (ClassNotFoundException ignored) {
                                try {
                                    c = findClass(remapped);
                                } catch (ClassNotFoundException ignored2) {}
                            }
                        }
                        if (c == null) {
                            try {
                                c = findClass(name);
                            } catch (ClassNotFoundException ignored3) {}
                        }
                        if (c == null) {
                            throw e;
                        }
                    }
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> loaded = findLoadedClass(name);
        if (loaded != null) return loaded;

        String path = name.replace('.', '/').concat(".class");
        URL resource = findResource(path);
        if (resource != null) {
            try (InputStream is = resource.openStream()) {
                byte[] raw = is.readAllBytes();
                byte[] transformed = BytecodeTransformer.transform(raw);
                return defineClass(name, transformed, 0, transformed.length, (java.security.CodeSource) null);
            } catch (Throwable t) {
                LOGGER.log(Level.FINE, "[PatchBukkit] Failed to define transformed class " + name, t);
            }
        }
        return super.findClass(name);
    }

    private static String remapLegacyClass(String name) {
        if (name == null) return null;

        // Missing dot in CraftBukkit package (e.g. from Cloud command framework where CB_PKG_VERSION was empty)
        // e.g. org.bukkit.craftbukkitcommand.VanillaCommandWrapper -> org.bukkit.craftbukkit.command.VanillaCommandWrapper
        if (name.startsWith("org.bukkit.craftbukkit") && !name.startsWith("org.bukkit.craftbukkit.")) {
            return "org.bukkit.craftbukkit." + name.substring("org.bukkit.craftbukkit".length());
        }

        // 1. Versioned CraftBukkit (e.g. org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer -> org.bukkit.craftbukkit.entity.CraftPlayer)
        if (name.startsWith("org.bukkit.craftbukkit.")) {
            String unversioned = name.replaceFirst("^org\\.bukkit\\.craftbukkit\\.(v[0-9_]+R[0-9]+\\.)?", "org.bukkit.craftbukkit.");
            if (!unversioned.equals(name)) {
                return unversioned;
            }
        }

        // 2. Legacy pre-1.17 NMS package (net.minecraft.server.v1_XX_RX.XYZ)
        if (name.startsWith("net.minecraft.server.v")) {
            String simpleName = name.substring(name.lastIndexOf('.') + 1);
            return switch (simpleName) {
                case "EntityPlayer" -> "net.minecraft.server.level.ServerPlayer";
                case "MinecraftServer" -> "net.minecraft.server.MinecraftServer";
                case "WorldServer" -> "net.minecraft.server.level.ServerLevel";
                case "ItemStack" -> "net.minecraft.world.item.ItemStack";
                case "Item" -> "net.minecraft.world.item.Item";
                case "Block" -> "net.minecraft.world.level.block.Block";
                case "Entity" -> "net.minecraft.world.entity.Entity";
                case "NBTTagCompound" -> "net.minecraft.nbt.CompoundTag";
                case "Packet" -> "net.minecraft.network.protocol.Packet";
                case "PlayerConnection" -> "net.minecraft.server.network.ServerGamePacketListenerImpl";
                case "World" -> "net.minecraft.world.level.Level";
                default -> null;
            };
        }

        return null;
    }

    @Override
    public PluginMeta getConfiguration() {
        return description;
    }

    @Override
    public Class<?> loadClass(
        String name,
        boolean resolve,
        boolean checkGlobal,
        boolean checkLibraries
    ) throws ClassNotFoundException {
        return loadClass(name, resolve);
    }

    @Override
    public void init(JavaPlugin plugin) {
        this.plugin = plugin;
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        plugin.init(
            org.bukkit.Bukkit.getServer(),
            description,
            dataFolder,
            file,
            this,
            description,
            com.destroystokyo.paper.utils.PaperPluginLogger.getLogger(
                description
            )
        );
    }

    @Override
    @Nullable
    public JavaPlugin getPlugin() {
        return plugin;
    }

    @Override
    @Nullable
    public PluginClassLoaderGroup getGroup() {
        return null;
    }

    public PluginDescriptionFile getDescription() {
        return description;
    }

    public File getDataFolder() {
        return dataFolder;
    }

    @Override
    public void close() throws IOException {
        ALL_LOADERS.remove(this);
        super.close();
    }
}
