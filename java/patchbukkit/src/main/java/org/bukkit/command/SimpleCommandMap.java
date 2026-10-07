package org.bukkit.command;

import org.bukkit.Location;
import org.bukkit.Server;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SimpleCommandMap implements CommandMap {
    protected final Map<String, Command> knownCommands;
    protected final Server server;

    public SimpleCommandMap(@NotNull Server server, @NotNull Map<String, Command> knownCommands) {
        this.server = server;
        this.knownCommands = knownCommands != null ? knownCommands : new HashMap<>();
    }

    public SimpleCommandMap(@NotNull Server server) {
        this(server, new HashMap<>());
    }

    @Override
    public void registerAll(@NotNull String fallbackPrefix, @NotNull List<Command> commands) {
        if (commands != null) {
            for (Command c : commands) {
                register(fallbackPrefix, c);
            }
        }
    }

    @Override
    public boolean register(@NotNull String label, @NotNull String fallbackPrefix, @NotNull Command command) {
        if (label == null || fallbackPrefix == null || command == null) {
            return false;
        }
        label = label.toLowerCase().trim();
        fallbackPrefix = fallbackPrefix.toLowerCase().trim();

        boolean registeredDirectly = false;
        if (!knownCommands.containsKey(label)) {
            knownCommands.put(label, command);
            registeredDirectly = true;
        }

        command.register(this);
        knownCommands.put(fallbackPrefix + ":" + label, command);

        if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
                if (alias == null) continue;
                alias = alias.toLowerCase().trim();
                if (!knownCommands.containsKey(alias)) {
                    knownCommands.put(alias, command);
                }
                knownCommands.put(fallbackPrefix + ":" + alias, command);
            }
        }
        try {
            org.patchbukkit.bootstrap.CommandBridge.onBukkitCommandRegistered(label, fallbackPrefix, command);
        } catch (Throwable ignored) {}
        return registeredDirectly;
    }

    @Override
    public boolean register(@NotNull String fallbackPrefix, @NotNull Command command) {
        if (command == null) return false;
        return register(command.getName(), fallbackPrefix, command);
    }

    @Override
    public boolean dispatch(@NotNull CommandSender sender, @NotNull String commandLine) throws CommandException {
        if (commandLine == null) return false;
        String cleanLine = commandLine.trim();
        while (cleanLine.startsWith("/")) {
            cleanLine = cleanLine.substring(1).trim();
        }
        if (cleanLine.isEmpty()) return false;

        String[] split = cleanLine.split("\\s+");
        if (split.length == 0) return false;

        String sentLabel = split[0].toLowerCase();
        Command command = knownCommands.get(sentLabel);
        if (command == null) return false;

        try {
            String[] args = Arrays.copyOfRange(split, 1, split.length);
            return command.execute(sender, sentLabel, args);
        } catch (Exception ex) {
            throw new CommandException("Unhandled exception executing '" + commandLine + "'", ex);
        }
    }

    @Override
    public void clearCommands() {
        knownCommands.clear();
    }

    @Override
    public @Nullable Command getCommand(@NotNull String name) {
        if (name == null) return null;
        return knownCommands.get(name.toLowerCase());
    }

    @Override
    public @NotNull Map<String, Command> getKnownCommands() {
        return Collections.unmodifiableMap(knownCommands);
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String cmdLine) {
        return tabComplete(sender, cmdLine, null);
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String cmdLine, @Nullable Location location) {
        if (cmdLine == null) return new ArrayList<>();
        String cleanLine = cmdLine.trim();
        while (cleanLine.startsWith("/")) {
            cleanLine = cleanLine.substring(1).trim();
        }
        String[] split = cleanLine.split(" ", -1);
        if (split.length == 0) return new ArrayList<>();

        String label = split[0].toLowerCase();
        Command command = knownCommands.get(label);

        if (split.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String key : knownCommands.keySet()) {
                if (key.startsWith(label)) completions.add(key);
            }
            return completions;
        }

        if (command != null) {
            String[] args = Arrays.copyOfRange(split, 1, split.length);
            return command.tabComplete(sender, label, args, location);
        }

        return null;
    }
}
