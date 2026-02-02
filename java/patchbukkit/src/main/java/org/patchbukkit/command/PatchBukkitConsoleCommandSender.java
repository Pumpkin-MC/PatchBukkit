package org.patchbukkit.command;

import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.checkerframework.checker.units.qual.m;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.PatchBukkitServer;

import net.kyori.adventure.text.Component;

public class PatchBukkitConsoleCommandSender implements ConsoleCommandSender {

    @Override
    public void sendMessage(@NotNull String message) {
        System.out.println(message);
    }

    @Override
    public void sendMessage(@NotNull String... messages) {
        System.out.println(messages);
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
        return Bukkit.getServer();
    }

    @Override
    public @NotNull String getName() {
        return "CONSOLE";
    }

    @Override
    public @NotNull Spigot spigot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'spigot'");
    }

    @Override
    public @NotNull Component name() {
        return Component.text("CONSOLE");
    }

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
        return true;
    }

    @Override
    public void setOp(boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setOp'");
    }

    @Override
    public boolean isConversing() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isConversing'");
    }

    @Override
    public void acceptConversationInput(@NotNull String input) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'acceptConversationInput'");
    }

    @Override
    public boolean beginConversation(@NotNull Conversation conversation) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'beginConversation'");
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'abandonConversation'");
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation, @NotNull ConversationAbandonedEvent details) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'abandonConversation'");
    }

    @Override
    public void sendRawMessage(@NotNull String message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendRawMessage'");
    }

    @Override
    public void sendRawMessage(@Nullable UUID sender, @NotNull String message) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'sendRawMessage'");
    }
    
}
