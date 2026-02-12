package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.bukkit.plugin.java.JavaPlugin;

public final class ConsoleSenderTests {

    private final JavaPlugin plugin;

    public ConsoleSenderTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "ConsoleSender.getName() returns non-null", category = TestCategory.CONSOLE_SENDER)
    public void testGetName() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        String name = console.getName();
        assertNotNull(name, "ConsoleSender.getName()");
    }

    @ConformanceTest(name = "ConsoleSender.sendMessage(String) does not throw", category = TestCategory.CONSOLE_SENDER)
    public void testSendMessage() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage("[PBTest] Console sendMessage test");
    }

    @ConformanceTest(name = "ConsoleSender.sendMessage(String[]) does not throw", category = TestCategory.CONSOLE_SENDER)
    public void testSendMessageArray() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(new String[]{"[PBTest] Line 1", "[PBTest] Line 2"});
    }

    @ConformanceTest(name = "ConsoleSender.isOp() returns true", category = TestCategory.CONSOLE_SENDER)
    public void testIsOp() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        assertTrue(console.isOp(), "ConsoleSender.isOp() should be true");
    }

    @ConformanceTest(name = "ConsoleSender.getServer() returns non-null", category = TestCategory.CONSOLE_SENDER)
    public void testGetServer() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        Server server = console.getServer();
        assertNotNull(server, "ConsoleSender.getServer()");
    }

    @ConformanceTest(name = "ConsoleSender.spigot() returns non-null", category = TestCategory.CONSOLE_SENDER)
    public void testSpigot() {
        ConsoleCommandSender console = Bukkit.getConsoleSender();
        var spigot = console.spigot();
        assertNotNull(spigot, "ConsoleSender.spigot()");
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
