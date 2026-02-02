package org.patchbukkit.loader;

import java.io.File;
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

    public static JavaPlugin createPlugin(String jarPath, String mainClass) {
        try {
            File jarFile = new File(jarPath);
            if (!jarFile.exists()) {
                System.err.println(
                    "[PatchBukkit] Plugin file does not exist: " + jarPath
                );
                return null;
            }

            PatchBukkitPluginClassLoader classLoader =
                new PatchBukkitPluginClassLoader(
                    PatchBukkitPluginLoader.class.getClassLoader(),
                    jarFile
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
