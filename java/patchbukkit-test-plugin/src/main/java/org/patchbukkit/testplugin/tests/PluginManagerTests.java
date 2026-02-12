package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.bukkit.plugin.java.JavaPlugin;

public final class PluginManagerTests {

    private final JavaPlugin plugin;

    public PluginManagerTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "PluginManager.getPlugins() contains this plugin", category = TestCategory.PLUGIN_MANAGER)
    public void testGetPlugins() {
        Plugin[] plugins = Bukkit.getPluginManager().getPlugins();
        assertNotNull(plugins, "PluginManager.getPlugins()");
        boolean found = false;
        for (Plugin p : plugins) {
            if (p == plugin) {
                found = true;
                break;
            }
        }
        assertTrue(found, "getPlugins() should contain this plugin");
    }

    @ConformanceTest(name = "PluginManager.getPlugin() finds this plugin by name", category = TestCategory.PLUGIN_MANAGER)
    public void testGetPlugin() {
        Plugin found = Bukkit.getPluginManager().getPlugin("PatchBukkitTest");
        assertNotNull(found, "PluginManager.getPlugin(\"PatchBukkitTest\")");
        assertTrue(found == plugin, "getPlugin() should return this plugin instance");
    }

    @ConformanceTest(name = "PluginManager.getPlugin() returns null for unknown", category = TestCategory.PLUGIN_MANAGER)
    public void testGetPluginUnknown() {
        Plugin found = Bukkit.getPluginManager().getPlugin("NoSuchPlugin_XYZ_12345");
        assertTrue(found == null, "getPlugin(unknown) should return null");
    }

    @ConformanceTest(name = "PluginManager.isPluginEnabled(String) for this plugin", category = TestCategory.PLUGIN_MANAGER)
    public void testIsPluginEnabledByName() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled("PatchBukkitTest");
        assertTrue(enabled, "isPluginEnabled(\"PatchBukkitTest\") should be true");
    }

    @ConformanceTest(name = "PluginManager.isPluginEnabled(Plugin) for this plugin", category = TestCategory.PLUGIN_MANAGER)
    public void testIsPluginEnabledByInstance() {
        boolean enabled = Bukkit.getPluginManager().isPluginEnabled(plugin);
        assertTrue(enabled, "isPluginEnabled(plugin) should be true");
    }

    @ConformanceTest(name = "PluginManager.getPermissions() returns set", category = TestCategory.PLUGIN_MANAGER)
    public void testGetPermissions() {
        var perms = Bukkit.getPluginManager().getPermissions();
        assertNotNull(perms, "PluginManager.getPermissions()");
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
