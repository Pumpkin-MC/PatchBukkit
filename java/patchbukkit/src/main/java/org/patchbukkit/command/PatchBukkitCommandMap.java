package org.patchbukkit.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.command.RegisterCommandRequest;

public class PatchBukkitCommandMap extends SimpleCommandMap {

    public PatchBukkitCommandMap(Server server) {
        super(server != null ? server : org.bukkit.Bukkit.getServer(), new HashMap<>());
    }

    public PatchBukkitCommandMap() {
        super(org.bukkit.Bukkit.getServer(), new HashMap<>());
    }

    private String cleanLabel(String label) {
        if (label == null) return "";
        String clean = label.trim();
        while (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        return clean.toLowerCase();
    }

    private void registerVariants(String key, Command command) {
        if (key == null || key.isEmpty()) return;
        key = key.toLowerCase().trim();
        if (!knownCommands.containsKey(key)) {
            knownCommands.put(key, command);
        }
        String clean = cleanLabel(key);
        if (!clean.isEmpty()) {
            if (!knownCommands.containsKey(clean)) {
                knownCommands.put(clean, command);
            }
            String single = "/" + clean;
            if (!knownCommands.containsKey(single)) {
                knownCommands.put(single, command);
            }
            String doubleSlash = "//" + clean;
            if (!knownCommands.containsKey(doubleSlash)) {
                knownCommands.put(doubleSlash, command);
            }
        }
    }

    @Override
    public void registerAll(@NotNull String fallbackPrefix, @NotNull List<Command> commands) {
        for (Command command : commands) {
            register(fallbackPrefix, command);
        }
    }

    @Override
    public boolean register(@NotNull String label, @NotNull String fallbackPrefix, @NotNull Command command) {
        if (label == null || fallbackPrefix == null || command == null) {
            return false;
        }
        label = label.toLowerCase().trim();
        fallbackPrefix = fallbackPrefix.toLowerCase().trim();
        String clean = cleanLabel(label);

        boolean registeredDirectly = false;
        if (!knownCommands.containsKey(label) && !knownCommands.containsKey(clean)) {
            registeredDirectly = true;
        }

        command.register(this);

        registerVariants(label, command);
        registerVariants(fallbackPrefix + ":" + label, command);
        if (!clean.isEmpty()) {
            registerVariants(clean, command);
            registerVariants(fallbackPrefix + ":" + clean, command);
        }

        if (command.getAliases() != null) {
            for (String alias : command.getAliases()) {
                if (alias == null) continue;
                alias = alias.toLowerCase().trim();
                registerVariants(alias, command);
                registerVariants(fallbackPrefix + ":" + alias, command);
            }
        }

        try {
            List<String> aliasesList = command.getAliases() != null ? command.getAliases() : Collections.emptyList();
            String desc = command.getDescription() != null ? command.getDescription() : "";
            RegisterCommandRequest request = RegisterCommandRequest.newBuilder()
                    .setCmdName(label)
                    .addAllAliases(aliasesList)
                    .setDescription(desc)
                    .setPluginName(fallbackPrefix)
                    .build();
            NativeBridgeFfi.registerCommand(request);
        } catch (Throwable t) {
            server.getLogger().log(java.util.logging.Level.WARNING, "Failed to register command to native bridge: " + label, t);
        }

        return registeredDirectly;
    }

    @Override
    public boolean register(@NotNull String fallbackPrefix, @NotNull Command command) {
        if (command == null) return false;
        return register(command.getName(), fallbackPrefix, command);
    }

    @Override
    public boolean dispatch(@NotNull CommandSender sender, @NotNull String cmdLine) throws CommandException {
        if (cmdLine == null) return false;
        String rawLine = cmdLine.trim();
        if (rawLine.isEmpty()) return false;

        String[] split = rawLine.split("\\s+");
        if (split.length == 0) return false;

        String rawLabel = split[0].toLowerCase();
        Command command = knownCommands.get(rawLabel);

        if (command == null) {
            String clean = cleanLabel(rawLabel);
            command = knownCommands.get(clean);
        }

        if (command == null) {
            return false; // Command not found
        }

        try {
            String[] args = Arrays.copyOfRange(split, 1, split.length);
            String executedLabel = rawLabel.startsWith("//") ? rawLabel : cleanLabel(rawLabel);
            return command.execute(sender, executedLabel, args);
        } catch (Exception ex) {
            throw new CommandException("Unhandled exception executing '" + cmdLine + "'", ex);
        }
    }

    public static boolean dispatchRaw(String senderUuid, String senderName, boolean isOp, String commandLine) {
        try {
            org.bukkit.command.CommandSender sender;
            if (senderUuid != null && !senderUuid.isBlank()) {
                java.util.UUID uuid = java.util.UUID.fromString(senderUuid);
                sender = org.bukkit.Bukkit.getPlayer(uuid);
                if (sender == null && org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    var playerList = org.patchbukkit.bootstrap.HeadlessPaperServer.get().getPlayerList();
                    if (playerList != null) {
                        var nmsPlayer = playerList.getPlayer(uuid);
                        if (nmsPlayer != null) {
                            sender = nmsPlayer.getBukkitEntity();
                        }
                    }
                }
                if (sender == null) {
                    sender = new org.patchbukkit.entity.CraftPlayer(uuid, senderName != null ? senderName : "Player");
                }
                if (sender instanceof org.patchbukkit.entity.PatchBukkitPlayer p) {
                    p.setOp(isOp);
                }
            } else {
                if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    sender = org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getConsoleSender();
                } else {
                    sender = org.bukkit.Bukkit.getConsoleSender();
                }
            }
            if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                String cmd = commandLine.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
                try {
                    if (org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.dispatchCommand(sender, cmd)) {
                        return true;
                    }
                } catch (Throwable ignored) {
                }
                try {
                    return org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getCommandMap().dispatch(sender, cmd);
                } catch (Throwable ignored) {
                }
                return false;
            }
            return org.bukkit.Bukkit.dispatchCommand(sender, commandLine);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.SEVERE, "Error dispatching command: " + commandLine, t);
            return false;
        }
    }

    public static String[] tabCompleteRaw(String senderUuid, String senderName, boolean isOp, String fullCommand, String worldName, double x, double y, double z) {
        try {
            org.bukkit.command.CommandSender sender;
            if (senderUuid != null && !senderUuid.isBlank()) {
                java.util.UUID uuid = java.util.UUID.fromString(senderUuid);
                sender = org.bukkit.Bukkit.getPlayer(uuid);
                if (sender == null && org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    var playerList = org.patchbukkit.bootstrap.HeadlessPaperServer.get().getPlayerList();
                    if (playerList != null) {
                        var nmsPlayer = playerList.getPlayer(uuid);
                        if (nmsPlayer != null) {
                            sender = nmsPlayer.getBukkitEntity();
                        }
                    }
                }
                if (sender == null) {
                    sender = new org.patchbukkit.entity.CraftPlayer(uuid, senderName != null ? senderName : "Player");
                }
                if (sender instanceof org.patchbukkit.entity.PatchBukkitPlayer p) {
                    p.setOp(isOp);
                }
            } else {
                if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    sender = org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getConsoleSender();
                } else {
                    sender = org.bukkit.Bukkit.getConsoleSender();
                }
            }
            Location loc = null;
            if (worldName != null && !worldName.isBlank()) {
                if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                    org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                    if (world == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
                        world = org.bukkit.Bukkit.getWorlds().get(0);
                    }
                    if (world != null) {
                        loc = new Location(world, x, y, z);
                    }
                } else {
                    org.bukkit.World world = org.patchbukkit.world.PatchBukkitWorld.getOrCreate(worldName);
                    loc = new Location(world, x, y, z);
                }
            }
            List<String> list;
            if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                list = org.patchbukkit.bootstrap.HeadlessPaperServer.get().server.getCommandMap().tabComplete(sender, fullCommand, loc);
            } else {
                list = org.bukkit.Bukkit.getServer().getCommandMap().tabComplete(sender, fullCommand, loc);
            }
            if (list == null || list.isEmpty()) {
                return new String[0];
            }
            return list.toArray(new String[0]);
        } catch (Throwable t) {
            org.bukkit.Bukkit.getLogger().log(java.util.logging.Level.WARNING, "Error during tab completion: " + fullCommand, t);
            return new String[0];
        }
    }

    @Override
    public void clearCommands() {
        knownCommands.clear();
    }

    @Override
    public @Nullable Command getCommand(@NotNull String name) {
        if (name == null) return null;
        String lower = name.toLowerCase().trim();
        Command cmd = knownCommands.get(lower);
        if (cmd == null) {
            cmd = knownCommands.get(cleanLabel(lower));
        }
        return cmd;
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
        String rawLine = cmdLine.trim();
        String[] split = rawLine.split(" ", -1);
        if (split.length == 0) return new ArrayList<>();

        String label = split[0].toLowerCase();
        Command command = knownCommands.get(label);
        if (command == null) {
            command = knownCommands.get(cleanLabel(label));
        }

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