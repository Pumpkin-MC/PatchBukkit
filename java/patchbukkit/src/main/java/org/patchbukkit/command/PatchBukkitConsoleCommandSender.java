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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.kyori.adventure.text.Component;

public class PatchBukkitConsoleCommandSender
    extends PatchBukkitCommandSender
    implements ConsoleCommandSender
{

    private @Nullable Conversation conversation;

    public PatchBukkitConsoleCommandSender() {
        super(Bukkit.getServer(), "CONSOLE");
    }

    @Override
    public void sendMessage(@NotNull String message) {
        super.sendMessage(message);
    }

    @Override
    public void sendMessage(@NotNull String... messages) {
        super.sendMessage(messages);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String message) {
        super.sendMessage(sender, message);
    }

    @Override
    public void sendMessage(@Nullable UUID sender, @NotNull String... messages) {
        super.sendMessage(sender, messages);
    }

    @Override
    public @NotNull Server getServer() {
        return Bukkit.getServer();
    }

    @Override
    public @NotNull String getName() {
        return super.getName();
    }

    @Override
    public @NotNull Spigot spigot() {
        return super.spigot();
    }

    @Override
    public @NotNull Component name() {
        return Component.text(getName());
    }

    @Override
    public boolean isPermissionSet(@NotNull String name) {
        return super.isPermissionSet(name);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission perm) {
        return super.isPermissionSet(perm);
    }

    @Override
    public boolean hasPermission(@NotNull String name) {
        return super.hasPermission(name);
    }

    @Override
    public boolean hasPermission(@NotNull Permission perm) {
        return super.hasPermission(perm);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value) {
        return super.addAttachment(plugin, name, value);
    }

    @Override
    public @NotNull PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return super.addAttachment(plugin);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String name, boolean value,
            int ticks) {
        return super.addAttachment(plugin, name, value, ticks);
    }

    @Override
    public @Nullable PermissionAttachment addAttachment(@NotNull Plugin plugin, int ticks) {
        return super.addAttachment(plugin, ticks);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment attachment) {
        super.removeAttachment(attachment);
    }

    @Override
    public void recalculatePermissions() {
        super.recalculatePermissions();
    }

    @Override
    public @NotNull Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return super.getEffectivePermissions();
    }

    @Override
    public boolean isOp() {
        return true;
    }

    @Override
    public void setOp(boolean value) {
        // Console is always op; ignore attempts to change.
    }

    @Override
    public boolean isConversing() {
        return this.conversation != null;
    }

    @Override
    public void acceptConversationInput(@NotNull String input) {
        if (this.conversation != null) {
            this.conversation.acceptInput(input);
        }
    }

    @Override
    public boolean beginConversation(@NotNull Conversation conversation) {
        if (this.conversation != null) {
            return false;
        }
        this.conversation = conversation;
        conversation.begin();
        return true;
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation) {
        if (this.conversation != null && this.conversation.equals(conversation)) {
            conversation.abandon();
            this.conversation = null;
        }
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation, @NotNull ConversationAbandonedEvent details) {
        if (this.conversation != null && this.conversation.equals(conversation)) {
            conversation.abandon(details);
            this.conversation = null;
        }
    }

    @Override
    public void sendRawMessage(@NotNull String message) {
        this.sendMessage(message);
    }

    @Override
    public void sendRawMessage(@Nullable UUID sender, @NotNull String message) {
        this.sendMessage(sender, message);
    }
    
}
