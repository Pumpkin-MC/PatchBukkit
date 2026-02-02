package org.patchbukkit.command;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;

public class PatchBukkitCommandSender implements CommandSender {

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isPermissionSet'");
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isPermissionSet'");
    }

    @Override
    public boolean hasPermission(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasPermission'");
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'hasPermission'");
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value,
            int ticks) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'addAttachment'");
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'removeAttachment'");
    }

    @Override
    public void recalculatePermissions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'recalculatePermissions'");
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEffectivePermissions'");
    }

    @Override
    public boolean isOp() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isOp'");
    }

    @Override
    public void setOp(boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setOp'");
    }

    @Override
    public void sendMessage(@NotNull String message) {
        throw new UnsupportedOperationException("Unimplemented method 'sendMessage'");
    }

    @Override
    public void sendMessage(@NotNull String... messages) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendMessage'");
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendMessage'");
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendMessage'");
    }

    @Override
    public @NotNull Server getServer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getServer'");
    }

    @Override
    public @NotNull String getName() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getName'");
    }

    @Override
    public @NotNull Spigot spigot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spigot'");
    }

    @Override
    public @NotNull Component name() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'name'");
    }
    
}
