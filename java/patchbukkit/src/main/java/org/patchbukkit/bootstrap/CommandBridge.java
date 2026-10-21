package org.patchbukkit.bootstrap;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.PaperCommands;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.command.Command;
import org.bukkit.craftbukkit.CraftServer;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.command.RegisterCommandRequest;

/**
 * Bridges commands registered in Paper's Brigadier dispatcher and CraftCommandMap
 * into Pumpkin's command tree over FFI.
 */
public final class CommandBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final Set<String> REGISTERED_COMMANDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> VANILLA_COMMANDS = ConcurrentHashMap.newKeySet();

    private CommandBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            ByteBuddy byteBuddy = new ByteBuddy();
            byteBuddy
                .redefine(PaperCommands.class)
                .visit(Advice.to(RegisterBrigadierAdvice.class).on(
                    ElementMatchers.named("registerWithFlagsInternal")
                ))
                .make()
                .load(PaperCommands.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            byteBuddy
                .redefine(org.bukkit.command.SimpleCommandMap.class)
                .visit(Advice.to(RegisterSimpleCommandAdvice.class).on(
                    ElementMatchers.named("register")
                        .and(ElementMatchers.takesArguments(String.class, String.class, Command.class))
                ))
                .make()
                .load(org.bukkit.command.SimpleCommandMap.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            LOGGER.info("[PatchBukkit] Successfully patched PaperCommands and SimpleCommandMap with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch command managers", t);
        }
    }

    public static class RegisterBrigadierAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(
            @Advice.Argument(0) PluginMeta pluginMeta,
            @Advice.Argument(3) LiteralCommandNode<?> node,
            @Advice.Argument(4) String description,
            @Advice.Argument(5) Collection<String> aliases
        ) {
            if (node != null) {
                CommandBridge.onBrigadierCommandRegistered(pluginMeta, node.getLiteral(), description, aliases);
            }
        }
    }

    public static class RegisterSimpleCommandAdvice {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void onExit(
            @Advice.This org.bukkit.command.SimpleCommandMap map,
            @Advice.Argument(0) String label,
            @Advice.Argument(1) String fallbackPrefix,
            @Advice.Argument(2) Command command
        ) {
            CommandBridge.onSimpleCommandMapRegister(map, label, fallbackPrefix, command);
        }
    }

    public static void onSimpleCommandMapRegister(
        org.bukkit.command.SimpleCommandMap map,
        String label,
        String fallbackPrefix,
        Command command
    ) {
        if (label == null || command == null) return;
        if (map != null) {
            try {
                Map<String, Command> known = map.getKnownCommands();
                registerSlashVariants(known, label, fallbackPrefix, command);
                if (command.getAliases() != null) {
                    for (String alias : command.getAliases()) {
                        registerSlashVariants(known, alias, fallbackPrefix, command);
                    }
                }
            } catch (Throwable ignored) {}
        }
        onBukkitCommandRegistered(label, fallbackPrefix, command);
    }

    private static void registerSlashVariants(
        Map<String, Command> known,
        String rawKey,
        String fallbackPrefix,
        Command command
    ) {
        if (known == null || rawKey == null || rawKey.isBlank()) return;
        String trimmed = rawKey.trim().toLowerCase(java.util.Locale.ROOT);
        boolean startsWithSlash = trimmed.startsWith("/");
        String clean = trimmed;
        while (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        if (clean.isEmpty()) return;
        putIfMissing(known, clean, command);
        if (startsWithSlash) {
            putIfMissing(known, "/" + clean, command);
            putIfMissing(known, "//" + clean, command);
        }
        if (fallbackPrefix != null && !fallbackPrefix.isBlank()) {
            String pfx = fallbackPrefix.trim().toLowerCase(java.util.Locale.ROOT);
            putIfMissing(known, pfx + ":" + clean, command);
        }
    }

    private static void putIfMissing(Map<String, Command> known, String key, Command command) {
        if (!known.containsKey(key)) {
            known.put(key, command);
        }
    }

    private static boolean isVanillaCommand(Command command) {
        if (command == null) return false;
        return command instanceof org.bukkit.craftbukkit.command.VanillaCommandWrapper
            && !(command instanceof org.bukkit.command.PluginIdentifiableCommand);
    }

    public static void captureVanillaCommands(PumpkinDedicatedServer server) {
        if (server == null || server.server == null) return;
        CraftServer craftServer = server.server;
        try {
            for (Map.Entry<String, Command> entry : craftServer.getCommandMap().getKnownCommands().entrySet()) {
                if (entry.getKey() != null) {
                    VANILLA_COMMANDS.add(entry.getKey().trim().toLowerCase(java.util.Locale.ROOT));
                }
            }
        } catch (Throwable ignored) {}
        try {
            var dispatcher = PaperCommands.INSTANCE.getDispatcherInternal();
            if (dispatcher != null && dispatcher.getRoot() != null) {
                for (var child : dispatcher.getRoot().getChildren()) {
                    if (child instanceof LiteralCommandNode<?> lit && lit.getLiteral() != null) {
                        VANILLA_COMMANDS.add(lit.getLiteral().trim().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void onBrigadierCommandRegistered(
        PluginMeta pluginMeta,
        String label,
        String description,
        Collection<String> aliases
    ) {
        if (label == null || label.isBlank()) return;
        String pluginName = pluginMeta != null ? pluginMeta.getName() : "paper";
        List<String> aliasList = aliases != null ? new ArrayList<>(aliases) : Collections.emptyList();
        registerCommand(label, description != null ? description : "", aliasList, pluginName);
    }

    public static void onBukkitCommandRegistered(String label, String fallbackPrefix, Command command) {
        if (label == null || label.isBlank() || command == null) return;
        if (isVanillaCommand(command)) return;
        if ("minecraft".equalsIgnoreCase(fallbackPrefix) || "bukkit".equalsIgnoreCase(fallbackPrefix) || "spigot".equalsIgnoreCase(fallbackPrefix)) {
            if (!(command instanceof org.bukkit.command.PluginIdentifiableCommand)) {
                return;
            }
        }
        List<String> aliases = command.getAliases() != null ? command.getAliases() : Collections.emptyList();
        String desc = command.getDescription() != null ? command.getDescription() : "";
        registerCommand(label, desc, aliases, fallbackPrefix != null ? fallbackPrefix : "bukkit");
    }

    public static void registerCommand(String name, String description, List<String> aliases, String pluginName) {
        if (name == null) return;
        String trimmed = name.trim().toLowerCase(java.util.Locale.ROOT);
        boolean startsWithSlash = trimmed.startsWith("/");
        String clean = trimmed;
        while (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        if (clean.isEmpty()) return;
        if (clean.startsWith("minecraft:") || clean.startsWith("bukkit:") || clean.startsWith("spigot:") || clean.startsWith("paper:")) {
            return;
        }
        String targetName = startsWithSlash ? ("/" + clean) : clean;
        if (clean.contains(":")) {
            return;
        }
        if (!REGISTERED_COMMANDS.add(targetName)) return;
        if (aliases != null) {
            for (String alias : aliases) {
                if (alias != null && !alias.isBlank()) {
                    String aTrim = alias.trim().toLowerCase(java.util.Locale.ROOT);
                    boolean aSlash = aTrim.startsWith("/");
                    String aClean = aTrim;
                    while (aClean.startsWith("/")) {
                        aClean = aClean.substring(1).trim();
                    }
                    if (!aClean.isEmpty() && !aClean.contains(":")) {
                        REGISTERED_COMMANDS.add(aSlash ? ("/" + aClean) : aClean);
                    }
                }
            }
        }

        try {
            var request = RegisterCommandRequest.newBuilder()
                .setCmdName(targetName)
                .addAllAliases(aliases != null ? aliases : Collections.emptyList())
                .setDescription(description != null ? description : "")
                .setPluginName(pluginName != null ? pluginName : "")
                .build();
            NativeBridgeFfi.registerCommand(request);
            LOGGER.info("[PatchBukkit] Mirrored command '" + targetName + "' to Pumpkin (" + pluginName + ")");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to register command to native bridge: " + targetName, t);
        }
    }

    public static void syncAll(PumpkinDedicatedServer server) {
        if (server == null || server.server == null) return;
        CraftServer craftServer = server.server;
        for (Map.Entry<String, Command> entry : craftServer.getCommandMap().getKnownCommands().entrySet()) {
            Command cmd = entry.getValue();
            if (cmd == null || isVanillaCommand(cmd)) continue;
            String key = entry.getKey();
            if (key == null || VANILLA_COMMANDS.contains(key.trim().toLowerCase(java.util.Locale.ROOT))) continue;
            String pluginName = (cmd instanceof org.bukkit.command.PluginIdentifiableCommand pic && pic.getPlugin() != null)
                ? pic.getPlugin().getName()
                : "plugin";
            onBukkitCommandRegistered(key, pluginName, cmd);
        }
        try {
            var dispatcher = PaperCommands.INSTANCE.getDispatcherInternal();
            if (dispatcher != null && dispatcher.getRoot() != null) {
                for (var child : dispatcher.getRoot().getChildren()) {
                    if (child instanceof LiteralCommandNode<?> lit) {
                        String literal = lit.getLiteral();
                        if (literal == null) continue;
                        String lower = literal.trim().toLowerCase(java.util.Locale.ROOT);
                        if (VANILLA_COMMANDS.contains(lower)) continue;
                        registerCommand(literal, "", Collections.emptyList(), "paper");
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "PaperCommands dispatcher not available for sync", t);
        }
    }

    private static org.bukkit.command.CommandSender resolveSender(
        String senderUuid,
        String senderName,
        boolean isOp
    ) {
        if (senderUuid != null && !senderUuid.isBlank()) {
            java.util.UUID uuid = java.util.UUID.fromString(senderUuid);
            org.bukkit.command.CommandSender sender = org.bukkit.Bukkit.getPlayer(uuid);
            if (sender == null && HeadlessPaperServer.isBooted()) {
                var playerList = HeadlessPaperServer.get().getPlayerList();
                if (playerList != null) {
                    var nmsPlayer = playerList.getPlayer(uuid);
                    if (nmsPlayer != null) {
                        sender = nmsPlayer.getBukkitEntity();
                    }
                }
                if (sender == null) {
                    var sp = org.patchbukkit.network.VirtualChannelManager.getInstance()
                        .getOrCreateServerPlayer(uuid, senderName != null ? senderName : "Player", -1);
                    if (sp != null) {
                        sender = sp.getBukkitEntity();
                    }
                }
            }
            if (sender instanceof org.bukkit.craftbukkit.entity.CraftHumanEntity human) {
                try {
                    if (human.isOp() != isOp) {
                        PlayerBridge.applyOpFromPumpkin(human, isOp);
                    }
                } catch (Throwable ignored) {}
            } else if (sender != null && isOp) {
                try {
                    sender.setOp(true);
                } catch (Throwable ignored) {}
            }
            if (sender != null) {
                return sender;
            }
        }
        if (HeadlessPaperServer.isBooted()) {
            return HeadlessPaperServer.get().server.getConsoleSender();
        }
        return org.bukkit.Bukkit.getConsoleSender();
    }

    /** Temporary diagnostics: dumps GrimAC's view of a player ({@code pbdebug grim <name>}). */
    private static void debugDumpGrim(String name) {
        try {
            org.bukkit.plugin.Plugin grim = org.bukkit.Bukkit.getPluginManager().getPlugin("GrimAC");
            ClassLoader cl = grim.getClass().getClassLoader();
            Class<?> api = Class.forName("ac.grim.grimac.GrimAPI", true, cl);
            Object inst = api.getField("INSTANCE").get(null);
            Object pdm = api.getMethod("getPlayerDataManager").invoke(inst);
            org.bukkit.entity.Player bp = org.bukkit.Bukkit.getPlayerExact(name);
            Object gp = pdm.getClass().getMethod("getPlayer", java.util.UUID.class).invoke(pdm, bp.getUniqueId());
            if (gp == null) {
                LOGGER.info("[GrimDebug] no GrimPlayer for " + name);
                return;
            }
            StringBuilder sb = new StringBuilder("[GrimDebug] " + name + ":");
            for (String f : new String[] {"x", "y", "z", "lastY", "onGround", "lastOnGround", "clientClaimsLastOnGround",
                    "gravity", "hasGravity", "playerEntityHasGravity", "isFlying", "pose", "gamemode"}) {
                try {
                    java.lang.reflect.Field fld = gp.getClass().getField(f);
                    sb.append(' ').append(f).append('=').append(fld.get(gp));
                } catch (Throwable t) {
                    sb.append(' ').append(f).append("=?");
                }
            }
            try {
                Object cw = gp.getClass().getField("compensatedWorld").get(gp);
                int minH = (int) cw.getClass().getMethod("getMinHeight").invoke(cw);
                int maxH = (int) cw.getClass().getMethod("getMaxHeight").invoke(cw);
                Object chunks = cw.getClass().getField("chunks").get(cw);
                int chunkCount = (int) chunks.getClass().getMethod("size").invoke(chunks);
                Object b98 = cw.getClass().getMethod("getBlock", int.class, int.class, int.class).invoke(cw, 7, 98, 23);
                Object b97 = cw.getClass().getMethod("getBlock", int.class, int.class, int.class).invoke(cw, 7, 97, 23);
                Object b99 = cw.getClass().getMethod("getBlock", int.class, int.class, int.class).invoke(cw, 7, 99, 23);
                Object stu = gp.getClass().getMethod("getSetbackTeleportUtil").invoke(gp);
                boolean unloaded = (boolean) stu.getClass().getMethod("insideUnloadedChunk").invoke(stu);
                Object txSent = gp.getClass().getField("lastTransactionSent").get(gp);
                Object txRecv = gp.getClass().getField("lastTransactionReceived").get(gp);
                sb.append(" txSent=").append(txSent).append(" txRecv=").append(txRecv)
                  .append(" minH=").append(minH).append(" maxH=").append(maxH)
                  .append(" chunks=").append(chunkCount)
                  .append(" unloaded=").append(unloaded)
                  .append(" b97=").append(b97).append(" b98=").append(b98).append(" b99=").append(b99);
            } catch (Throwable t) {
                sb.append(" cwErr=").append(t);
            }
            LOGGER.info(sb.toString());
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[GrimDebug] failed", t);
        }
    }

    public static boolean dispatchRaw(String senderUuid, String senderName, boolean isOp, String commandLine) {
        if (commandLine.startsWith("/pbdebug grim ") || commandLine.startsWith("pbdebug grim ")) {
            debugDumpGrim(commandLine.substring(commandLine.indexOf("grim ") + 5).trim());
            return true;
        }
        Boolean prevThreadState = HeadlessPaperServer.enterPrimaryThread();
        try {
            org.bukkit.command.CommandSender sender = resolveSender(senderUuid, senderName, isOp);
            if (HeadlessPaperServer.isBooted()) {
                String cmd = commandLine.trim();
                if (cmd.startsWith("/")) cmd = cmd.substring(1).trim();
                try {
                    boolean handled = HeadlessPaperServer.get().server.dispatchCommand(sender, cmd);
                    LOGGER.info("[PatchBukkit] dispatchCommand('" + cmd + "') sender=" + sender.getName()
                        + " (" + sender.getClass().getSimpleName() + ") -> " + handled);
                    if (handled) {
                        return true;
                    }
                    LOGGER.info("[PatchBukkit] dispatchCommand returned false for '" + cmd + "' sender="
                        + sender.getName() + " (" + sender.getClass().getSimpleName() + ") op=" + sender.isOp());
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "[PatchBukkit] dispatchCommand threw for '" + cmd + "' sender=" + sender.getName(), t);
                }
                try {
                    boolean r = HeadlessPaperServer.get().server.getCommandMap().dispatch(sender, cmd);
                    LOGGER.info("[PatchBukkit] commandMap.dispatch fallback for '" + cmd + "' -> " + r);
                    return r;
                } catch (Throwable t) {
                    LOGGER.log(Level.WARNING, "[PatchBukkit] commandMap.dispatch threw for '" + cmd + "'", t);
                }
                return false;
            }
            return org.bukkit.Bukkit.dispatchCommand(sender, commandLine);
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error dispatching command: " + commandLine, t);
            return false;
        } finally {
            HeadlessPaperServer.restoreThreadState(prevThreadState);
        }
    }

    public static String[] tabCompleteRaw(
        String senderUuid,
        String senderName,
        boolean isOp,
        String fullCommand,
        String worldName,
        double x,
        double y,
        double z
    ) {
        Boolean prevThreadState = HeadlessPaperServer.enterPrimaryThread();
        try {
            org.bukkit.command.CommandSender sender = resolveSender(senderUuid, senderName, isOp);
            org.bukkit.Location loc = null;
            if (worldName != null && !worldName.isBlank()) {
                org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
                if (world == null) {
                    try {
                        world = org.bukkit.Bukkit.getWorld(java.util.UUID.fromString(worldName));
                    } catch (IllegalArgumentException ignored) {}
                }
                if (world == null && !org.bukkit.Bukkit.getWorlds().isEmpty()) {
                    world = org.bukkit.Bukkit.getWorlds().get(0);
                }
                if (world != null) {
                    loc = new org.bukkit.Location(world, x, y, z);
                }
            }
            List<String> list = HeadlessPaperServer.isBooted()
                ? HeadlessPaperServer.get().server.getCommandMap().tabComplete(sender, fullCommand, loc)
                : org.bukkit.Bukkit.getServer().getCommandMap().tabComplete(sender, fullCommand, loc);
            if ((list == null || list.isEmpty()) && HeadlessPaperServer.isBooted()) {
                try {
                    String raw = fullCommand.startsWith("/") ? fullCommand.substring(1) : fullCommand;
                    var nmsCommands = HeadlessPaperServer.get().getCommands();
                    var nmsSource = org.bukkit.craftbukkit.command.VanillaCommandWrapper.getListener(sender);
                    var parseResults = nmsCommands.getDispatcher().parse(raw, nmsSource);
                    var suggestions = nmsCommands.getDispatcher()
                        .getCompletionSuggestions(parseResults)
                        .get(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (suggestions != null && !suggestions.isEmpty()) {
                        list = new ArrayList<>();
                        for (var s : suggestions.getList()) {
                            list.add(s.getText());
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (list == null || list.isEmpty()) {
                return new String[0];
            }
            return list.toArray(new String[0]);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Error during tab completion: " + fullCommand, t);
            return new String[0];
        } finally {
            HeadlessPaperServer.restoreThreadState(prevThreadState);
        }
    }
}
