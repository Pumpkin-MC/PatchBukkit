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

    private CommandBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            new ByteBuddy()
                .redefine(PaperCommands.class)
                .visit(Advice.to(RegisterBrigadierAdvice.class).on(
                    ElementMatchers.named("registerWithFlagsInternal")
                ))
                .make()
                .load(PaperCommands.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            LOGGER.info("[PatchBukkit] Successfully patched PaperCommands with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch PaperCommands", t);
        }
    }

    public static class RegisterBrigadierAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) PluginMeta pluginMeta,
            @Advice.Argument(2) String label,
            @Advice.Argument(4) String description,
            @Advice.Argument(5) Collection<String> aliases
        ) {
            CommandBridge.onBrigadierCommandRegistered(pluginMeta, label, description, aliases);
        }
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
        List<String> aliases = command.getAliases() != null ? command.getAliases() : Collections.emptyList();
        String desc = command.getDescription() != null ? command.getDescription() : "";
        registerCommand(label, desc, aliases, fallbackPrefix != null ? fallbackPrefix : "bukkit");
    }

    public static void registerCommand(String name, String description, List<String> aliases, String pluginName) {
        String clean = name.trim().toLowerCase();
        while (clean.startsWith("/")) {
            clean = clean.substring(1).trim();
        }
        if (clean.isEmpty()) return;
        if (!REGISTERED_COMMANDS.add(clean)) return;

        try {
            var request = RegisterCommandRequest.newBuilder()
                .setCmdName(clean)
                .addAllAliases(aliases != null ? aliases : Collections.emptyList())
                .setDescription(description != null ? description : "")
                .setPluginName(pluginName != null ? pluginName : "")
                .build();
            NativeBridgeFfi.registerCommand(request);
            LOGGER.info("[PatchBukkit] Mirrored command '" + clean + "' to Pumpkin (" + pluginName + ")");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to register command to native bridge: " + clean, t);
        }
    }

    public static void syncAll(PumpkinDedicatedServer server) {
        if (server == null || server.server == null) return;
        CraftServer craftServer = server.server;
        for (Map.Entry<String, Command> entry : craftServer.getCommandMap().getKnownCommands().entrySet()) {
            Command cmd = entry.getValue();
            if (cmd == null) continue;
            onBukkitCommandRegistered(entry.getKey(), "bukkit", cmd);
        }
        try {
            var dispatcher = PaperCommands.INSTANCE.getDispatcher();
            if (dispatcher != null && dispatcher.getRoot() != null) {
                for (var child : dispatcher.getRoot().getChildren()) {
                    if (child instanceof LiteralCommandNode<?> lit) {
                        registerCommand(lit.getLiteral(), "", Collections.emptyList(), "paper");
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "PaperCommands dispatcher not available for sync", t);
        }
    }
}
