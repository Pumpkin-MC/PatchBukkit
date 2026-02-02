package org.patchbukkit.loader;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.UnknownDependencyException;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings({ "deprecation", "removal" })
public class PatchBukkitPluginLoader implements PluginLoader {

    public static JavaPlugin createPlugin(
        String jarPath,
        String mainClass,
        String extraClasspath,
        String libraryCoordinates
    ) {
        try {
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                System.err.println(
                    "[PatchBukkit] Plugin file does not exist: " + jarPath
                );
                return null;
            }

            LinkedHashSet<URL> extraUrls = new LinkedHashSet<>();
            if (extraClasspath != null && !extraClasspath.isBlank()) {
                String[] paths = extraClasspath.split(File.pathSeparator);
                for (String path : paths) {
                    if (path == null || path.isBlank()) {
                        continue;
                    }
                    File extraFile = new File(path.trim());
                    if (extraFile.exists()) {
                        extraUrls.add(extraFile.toURI().toURL());
                    }
                }
            }

            if (libraryCoordinates != null && !libraryCoordinates.isBlank()) {
                File libsDir = new File(jarFile.getParentFile(), "patchbukkit-libs");
                if (!libsDir.exists()) {
                    libsDir.mkdirs();
                }
                List<File> libraries = LibraryResolver.resolveLibraries(
                    libraryCoordinates,
                    libsDir
                );
                for (File lib : libraries) {
                    if (lib != null && lib.exists()) {
                        extraUrls.add(lib.toURI().toURL());
                    }
                }
            }

            PatchBukkitPluginClassLoader classLoader =
                new PatchBukkitPluginClassLoader(
                    PatchBukkitPluginLoader.class.getClassLoader(),
                    jarFile,
                    extraUrls.toArray(new URL[0])
                );

            Class<?> jarClass = Class.forName(mainClass, true, classLoader);
            return (JavaPlugin) jarClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public Plugin loadPlugin(File file)
        throws InvalidPluginException, UnknownDependencyException {
        throw new UnsupportedOperationException("Use createPlugin() instead");
    }

    @Override
    public PluginDescriptionFile getPluginDescription(File file)
        throws InvalidDescriptionException {
        throw new UnsupportedOperationException(
            "Use PatchBukkitPluginClassLoader.getDescription() instead"
        );
    }

    @Override
    public Pattern[] getPluginFileFilters() {
        return new Pattern[] { Pattern.compile("\\.jar$") };
    }

    @Override
    public void enablePlugin(Plugin plugin) {
        if (plugin instanceof JavaPlugin javaPlugin) {
            javaPlugin.setEnabled(true);
        }
    }

    @Override
    public void disablePlugin(Plugin plugin) {
        if (plugin instanceof JavaPlugin javaPlugin) {
            javaPlugin.setEnabled(false);
        }
    }

    @Override
    public Map<
        Class<? extends Event>,
        Set<RegisteredListener>
    > createRegisteredListeners(Listener listener, Plugin plugin) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'createRegisteredListeners'"
        );
    }
}
