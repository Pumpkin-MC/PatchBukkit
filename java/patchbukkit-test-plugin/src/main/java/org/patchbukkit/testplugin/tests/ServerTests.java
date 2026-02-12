package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.patchbukkit.testplugin.TestExpectation;
import org.bukkit.plugin.java.JavaPlugin;

public final class ServerTests {

    private final JavaPlugin plugin;

    public ServerTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "Server.getName() returns non-null", category = TestCategory.SERVER)
    public void testGetName() {
        String name = Bukkit.getServer().getName();
        assertNotNull(name, "Server.getName()");
    }

    @ConformanceTest(name = "Server.getVersion() returns non-null", category = TestCategory.SERVER)
    public void testGetVersion() {
        String version = Bukkit.getServer().getVersion();
        assertNotNull(version, "Server.getVersion()");
    }

    @ConformanceTest(name = "Server.getBukkitVersion() returns non-null", category = TestCategory.SERVER)
    public void testGetBukkitVersion() {
        String version = Bukkit.getServer().getBukkitVersion();
        assertNotNull(version, "Server.getBukkitVersion()");
    }

    @ConformanceTest(name = "Server.getPluginManager() returns non-null", category = TestCategory.SERVER)
    public void testGetPluginManager() {
        PluginManager pm = Bukkit.getServer().getPluginManager();
        assertNotNull(pm, "Server.getPluginManager()");
    }

    @ConformanceTest(name = "Server.getConsoleSender() returns non-null", category = TestCategory.SERVER)
    public void testGetConsoleSender() {
        ConsoleCommandSender cs = Bukkit.getServer().getConsoleSender();
        assertNotNull(cs, "Server.getConsoleSender()");
    }

    @ConformanceTest(name = "Server.getCommandMap() returns non-null", category = TestCategory.SERVER)
    public void testGetCommandMap() {
        CommandMap cm = Bukkit.getServer().getCommandMap();
        assertNotNull(cm, "Server.getCommandMap()");
    }

    @ConformanceTest(name = "Server.getScheduler() returns non-null", category = TestCategory.SERVER)
    public void testGetScheduler() {
        BukkitScheduler sched = Bukkit.getServer().getScheduler();
        assertNotNull(sched, "Server.getScheduler()");
    }

    @ConformanceTest(name = "Server.getOnlinePlayers() returns collection", category = TestCategory.SERVER)
    public void testGetOnlinePlayers() {
        var players = Bukkit.getServer().getOnlinePlayers();
        assertNotNull(players, "Server.getOnlinePlayers()");
    }

    @ConformanceTest(name = "Server.getUnsafe() returns non-null", category = TestCategory.SERVER)
    public void testGetUnsafe() {
        Object unsafe = Bukkit.getUnsafe();
        assertNotNull(unsafe, "Server.getUnsafe()");
    }

    @ConformanceTest(name = "Server.suggestPlayerNamesWhenNullTabCompletions() returns boolean", category = TestCategory.SERVER)
    public void testSuggestPlayerNames() {
        // Should return true according to implementation
        boolean result = Bukkit.getServer().suggestPlayerNamesWhenNullTabCompletions();
        assertTrue(result, "Server.suggestPlayerNamesWhenNullTabCompletions()");
    }

    @ConformanceTest(name = "Bukkit.getServer() matches plugin.getServer()", category = TestCategory.SERVER)
    public void testServerConsistency() {
        Server bukkit = Bukkit.getServer();
        Server fromPlugin = plugin.getServer();
        assertTrue(bukkit == fromPlugin, "Bukkit.getServer() == plugin.getServer()");
    }

    @ConformanceTest(name = "Server.getPluginCommand() returns null for unregistered command", category = TestCategory.SERVER)
    public void testGetPluginCommand() {
        // getPluginCommand only finds PluginCommands registered via plugin.yml;
        // commands registered directly on the CommandMap are not PluginCommands
        var cmd = Bukkit.getServer().getPluginCommand("nonexistent_abc");
        assertTrue(cmd == null, "Server.getPluginCommand(unregistered) should be null");
    }

    @ConformanceTest(name = "Server.getPluginCommand() returns null for unknown", category = TestCategory.SERVER)
    public void testGetPluginCommandUnknown() {
        var cmd = Bukkit.getServer().getPluginCommand("nonexistent_command_xyz");
        assertTrue(cmd == null, "Server.getPluginCommand(unknown) should be null");
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) {
            throw new AssertionError(what + " returned null");
        }
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
