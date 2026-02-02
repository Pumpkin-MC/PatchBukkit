package org.patchbukkit.command;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissibleBase;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.permissions.ServerOperator;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.chat.BaseComponent;

public class PatchBukkitCommandSender implements CommandSender, ServerOperator {

    private final Server server;
    private final String name;
    private final PermissibleBase perm;
    private boolean op;

    private final Spigot spigot = new Spigot() {
        @Override
        public void sendMessage(@NotNull BaseComponent component) {
            PatchBukkitCommandSender.this.sendMessage(BaseComponent.toLegacyText(component));
        }

        @Override
        public void sendMessage(@NotNull BaseComponent... components) {
            PatchBukkitCommandSender.this.sendMessage(BaseComponent.toLegacyText(components));
        }

        @Override
        public void sendMessage(@NotNull UUID sender, @NotNull BaseComponent component) {
            PatchBukkitCommandSender.this.sendMessage(sender, BaseComponent.toLegacyText(component));
        }

        @Override
        public void sendMessage(@NotNull UUID sender, @NotNull BaseComponent... components) {
            PatchBukkitCommandSender.this.sendMessage(sender, BaseComponent.toLegacyText(components));
        }
    };

    public PatchBukkitCommandSender() {
        this(Bukkit.getServer(), "CommandSender");
    }

    public PatchBukkitCommandSender(@Nullable Server server, @Nullable String name) {
        this.server = server != null ? server : Bukkit.getServer();
        this.name = name != null ? name : "CommandSender";
        this.op = false;
        this.perm = new PermissibleBase(this);
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        return this.perm.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        return this.perm.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(@NotNull String name) {
        return this.perm.hasPermission(name);
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        return this.perm.hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        return this.perm.addAttachment(plugin, name, value);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return this.perm.addAttachment(plugin);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value,
            int ticks) {
        return this.perm.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        return this.perm.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        this.perm.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        this.perm.recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return this.perm.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return this.op;
    }

    @Override
    public void setOp(boolean value) {
        this.op = value;
        this.perm.recalculatePermissions();
    }

    @Override
    public void sendMessage(@NotNull String message) {
        if (message == null) {
            return;
        }
        if (this.server != null && this.server.getLogger() != null) {
            this.server.getLogger().info(message);
        } else {
            System.out.println(message);
        }
    }

    @Override
    public void sendMessage(@NotNull String... messages) {
        if (messages == null) {
            return;
        }
        for (String message : messages) {
            this.sendMessage(message);
        }
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        this.sendMessage(message);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
        this.sendMessage(messages);
    }

    @Override
    public @NotNull Server getServer() {
        return this.server != null ? this.server : Bukkit.getServer();
    }

    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public @NotNull Spigot spigot() {
        return this.spigot;
    }

    @Override
    public @NotNull Component name() {
        return Component.text(this.getName());
    }

}
