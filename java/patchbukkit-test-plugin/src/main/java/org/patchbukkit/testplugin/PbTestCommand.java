package org.patchbukkit.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PbTestCommand implements CommandExecutor, TabCompleter {

    private final TestFramework framework;

    public PbTestCommand(TestFramework framework) {
        this.framework = framework;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String target = (args.length > 0) ? args[0].toUpperCase(Locale.ROOT) : "ALL";

        List<TestResult> results;
        if ("ALL".equals(target)) {
            results = framework.runAll();
        } else {
            TestCategory category;
            try {
                category = TestCategory.valueOf(target);
            } catch (IllegalArgumentException e) {
                sender.sendMessage("Unknown category: " + target + ". Use /pbtest [category|all]");
                return true;
            }
            results = framework.runCategory(category);
        }

        framework.reportResults(results, sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();

        String prefix = args[0].toUpperCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();
        completions.add("all");
        for (TestCategory cat : TestCategory.values()) {
            completions.add(cat.name().toLowerCase(Locale.ROOT));
        }

        completions.removeIf(s -> !s.toUpperCase(Locale.ROOT).startsWith(prefix));
        return completions;
    }
}
