package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import java.util.List;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class CommandSystemTests {

    @ConformanceTest(name = "CommandMap.register() accepts a command", category = TestCategory.COMMAND_SYSTEM)
    public void testRegister() {
        CommandMap map = Bukkit.getServer().getCommandMap();
        Command testCmd = new BukkitCommand("pbtestdummy", "test", "/pbtestdummy", List.of()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }
        };
        boolean registered = map.register("patchbukkittest", testCmd);
        // registered is true if direct label was available, false if only fallback was used
        // Either way it should not throw
    }

    @ConformanceTest(name = "CommandMap.getCommand() retrieves registered command", category = TestCategory.COMMAND_SYSTEM)
    public void testGetCommand() {
        CommandMap map = Bukkit.getServer().getCommandMap();
        // "pbtest" was registered by the plugin
        Command cmd = map.getCommand("pbtest");
        assertNotNull(cmd, "CommandMap.getCommand(\"pbtest\")");
    }

    @ConformanceTest(name = "CommandMap.getKnownCommands() returns map", category = TestCategory.COMMAND_SYSTEM)
    public void testGetKnownCommands() {
        CommandMap map = Bukkit.getServer().getCommandMap();
        var known = map.getKnownCommands();
        assertNotNull(known, "CommandMap.getKnownCommands()");
        assertTrue(!known.isEmpty(), "getKnownCommands() should not be empty");
    }

    @ConformanceTest(name = "CommandMap.dispatch() executes a command", category = TestCategory.COMMAND_SYSTEM)
    public void testDispatch() {
        CommandMap map = Bukkit.getServer().getCommandMap();

        // Register a command that sets a flag
        final boolean[] executed = {false};
        Command testCmd = new BukkitCommand("pbtestexec", "test", "/pbtestexec", List.of()) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                executed[0] = true;
                return true;
            }
        };
        map.register("patchbukkittest", testCmd);

        CommandSender sender = Bukkit.getConsoleSender();
        boolean result = map.dispatch(sender, "/pbtestexec");
        assertTrue(result, "dispatch() with leading slash should return true");
        assertTrue(executed[0], "command should have been executed");
    }

    @ConformanceTest(name = "CommandMap.tabComplete() returns completions", category = TestCategory.COMMAND_SYSTEM)
    public void testTabComplete() {
        CommandMap map = Bukkit.getServer().getCommandMap();
        CommandSender sender = Bukkit.getConsoleSender();
        List<String> completions = map.tabComplete(sender, "pb");
        assertNotNull(completions, "CommandMap.tabComplete()");
        // Should include "pbtest" at minimum
    }

    @ConformanceTest(name = "CommandMap.register() with fallback prefix creates alias", category = TestCategory.COMMAND_SYSTEM)
    public void testFallbackAlias() {
        CommandMap map = Bukkit.getServer().getCommandMap();
        Command testCmd = new BukkitCommand("pbtestalias", "test", "/pbtestalias", List.of("pbta")) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return true;
            }
        };
        map.register("myprefix", testCmd);

        // Should be accessible via fallback prefix
        Command found = map.getCommand("myprefix:pbtestalias");
        assertNotNull(found, "CommandMap.getCommand(\"myprefix:pbtestalias\")");
    }

    @ConformanceTest(name = "CommandMap supports WorldEdit double-slash commands", category = TestCategory.COMMAND_SYSTEM)
    public void testDoubleSlashCommands() {
        CommandMap map = Bukkit.getServer().getCommandMap();
        final String[] executedLabel = {null};
        Command testCmd = new BukkitCommand("//pbwand", "wand", "//pbwand", List.of("//pbset", "pbset")) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                executedLabel[0] = label;
                return true;
            }
        };
        map.register("pbworldedit", testCmd);

        CommandSender sender = Bukkit.getConsoleSender();

        // 1. Dispatch with double slash
        boolean resultDouble = map.dispatch(sender, "//pbwand");
        assertTrue(resultDouble, "dispatch() with double slash should return true");
        assertTrue("//pbwand".equals(executedLabel[0]), "executed label for //pbwand");

        // 2. Dispatch with single slash
        boolean resultSingle = map.dispatch(sender, "/pbwand");
        assertTrue(resultSingle, "dispatch() with single slash should return true");

        // 3. Dispatch without slash
        boolean resultNoSlash = map.dispatch(sender, "pbwand");
        assertTrue(resultNoSlash, "dispatch() without slash should return true");

        // 4. Dispatch alias with double slash
        boolean resultAlias = map.dispatch(sender, "//pbset");
        assertTrue(resultAlias, "dispatch() with double slash alias should return true");

        // 5. getCommand lookups
        assertNotNull(map.getCommand("//pbwand"), "getCommand(\"//pbwand\")");
        assertNotNull(map.getCommand("/pbwand"), "getCommand(\"/pbwand\")");
        assertNotNull(map.getCommand("pbwand"), "getCommand(\"pbwand\")");
        assertNotNull(map.getCommand("//pbset"), "getCommand(\"//pbset\")");
    }

}
