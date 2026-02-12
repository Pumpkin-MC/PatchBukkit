package org.patchbukkit.testplugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.patchbukkit.testplugin.tests.*;

public final class PatchBukkitTestPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        TestFramework framework = new TestFramework(getLogger());

        framework.registerSuite(new ServerTests(this));
        framework.registerSuite(new PluginManagerTests(this));
        framework.registerSuite(new CommandSystemTests());
        framework.registerSuite(new EventSystemTests(this));
        framework.registerSuite(new PermissionsTests());
        framework.registerSuite(new RegistryTests());
        framework.registerSuite(new EntityTests());
        framework.registerSuite(new ConsoleSenderTests());
        framework.registerSuite(new UnsafeValuesTests());
        framework.registerSuite(new StubTests());

        // Set executor on the PluginCommand created by PatchBukkit's Rust side
        PbTestCommand cmd = new PbTestCommand(framework);
        Command existing = Bukkit.getServer().getCommandMap().getCommand("pbtest");
        if (existing instanceof PluginCommand pluginCmd) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        } else {
            // Fallback: register directly on CommandMap
            Bukkit.getServer().getCommandMap().register("patchbukkittest", new PbTestBukkitCommand(cmd));
        }

        getLogger().info("PatchBukkit Test Plugin loaded. Run /pbtest [category|all] to execute tests.");
    }
}
