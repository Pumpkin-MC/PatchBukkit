package org.patchbukkit.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class PbTestBukkitCommand extends Command {

    private final PbTestCommand handler;

    public PbTestBukkitCommand(PbTestCommand handler) {
        super("pbtest", "Run PatchBukkit conformance tests", "/pbtest [category|all]", List.of());
        this.handler = handler;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        return handler.onCommand(sender, this, label, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return handler.onTabComplete(sender, this, alias, args);
    }
}
