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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class PatchBukkitPluginClassLoader
    extends URLClassLoader
    implements ConfiguredPluginClassLoader
{

    private final PluginDescriptionFile description;
    private final File dataFolder;
    private final File file;
    private JavaPlugin plugin;

    static {
        ClassLoader.registerAsParallelCapable();
    }

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
                    !name.startsWith("javax.")
                ) {
                    try {
                        // Try to find in plugin JAR first
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        // Not in JAR, delegate to parent
                    }
                }

                // If not found in JAR (or is a system class), delegate to parent
                if (c == null) {
                    c = getParent().loadClass(name);
                }
            }

            if (resolve) {
                resolveClass(c);
            }
            return c;
        }
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
        super.close();
    }
}
