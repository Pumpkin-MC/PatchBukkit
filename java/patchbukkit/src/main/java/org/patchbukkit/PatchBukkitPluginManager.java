package org.patchbukkit;

import io.papermc.paper.plugin.PermissionManager;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.io.File;
import java.util.List;
import java.util.Set;
import org.bukkit.Server;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

@SuppressWarnings("removal")
public class PatchBukkitPluginManager implements PluginManager {

    final PatchBukkitEventManager patchBukkitEventManager;

    public PatchBukkitPluginManager(Server server) {
        this.patchBukkitEventManager = new PatchBukkitEventManager(server);
    }

    @Override
    public void registerInterface(@NotNull Class<? extends PluginLoader> loader)
        throws IllegalArgumentException {}

    @Override
    public @Nullable Plugin getPlugin(@NotNull String name) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlugin'"
        );
    }

    @Override
    public @NotNull Plugin[] getPlugins() {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlugins'"
        );
    }

    @Override
    public boolean isPluginEnabled(@NotNull String name) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'isPluginEnabled'"
        );
    }

    @Override
    public boolean isPluginEnabled(@Nullable Plugin plugin) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'isPluginEnabled'"
        );
    }

    @Override
    public @Nullable Plugin loadPlugin(@NotNull File file)
        throws InvalidPluginException, InvalidDescriptionException, UnknownDependencyException {
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadPlugin'"
        );
    }

    @Override
    public @NotNull Plugin[] loadPlugins(@NotNull File directory) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadPlugins'"
        );
    }

    @Override
    public @NotNull Plugin[] loadPlugins(@NonNull @NotNull File[] files) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadPlugins'"
        );
    }

    @Override
    public void disablePlugins() {
        throw new UnsupportedOperationException(
            "Unimplemented method 'disablePlugins'"
        );
    }

    @Override
    public void clearPlugins() {
        throw new UnsupportedOperationException(
            "Unimplemented method 'clearPlugins'"
        );
    }

    @Override
    public void callEvent(@NotNull Event event) throws IllegalStateException {
        this.patchBukkitEventManager.callEvent(event);
    }

    @Override
    public void registerEvents(
        @NotNull Listener listener,
        @NotNull Plugin plugin
    ) {
        this.patchBukkitEventManager.registerEvents(listener, plugin);
    }

    @Override
    public void registerEvent(
        @NotNull Class<? extends Event> event,
        @NotNull Listener listener,
        @NotNull EventPriority priority,
        @NotNull EventExecutor executor,
        @NotNull Plugin plugin
    ) {
        this.patchBukkitEventManager.registerEvent(
            event,
            listener,
            priority,
            executor,
            plugin
        );
    }

    @Override
    public void registerEvent(
        @NotNull Class<? extends Event> event,
        @NotNull Listener listener,
        @NotNull EventPriority priority,
        @NotNull EventExecutor executor,
        @NotNull Plugin plugin,
        boolean ignoreCancelled
    ) {
        this.patchBukkitEventManager.registerEvent(
            event,
            listener,
            priority,
            executor,
            plugin,
            ignoreCancelled
        );
    }

    @Override
    public void enablePlugin(@NotNull Plugin plugin) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'enablePlugin'"
        );
    }

    @Override
    public void disablePlugin(@NotNull Plugin plugin) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'disablePlugin'"
        );
    }

    @Override
    public @Nullable Permission getPermission(@NotNull String name) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPermission'"
        );
    }

    @Override
    public void addPermission(@NotNull Permission perm) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'addPermission'"
        );
    }

    @Override
    public void removePermission(@NotNull Permission perm) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'removePermission'"
        );
    }

    @Override
    public void removePermission(@NotNull String name) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'removePermission'"
        );
    }

    @Override
    public @NotNull Set<Permission> getDefaultPermissions(boolean op) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getDefaultPermissions'"
        );
    }

    @Override
    public void recalculatePermissionDefaults(@NotNull Permission perm) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'recalculatePermissionDefaults'"
        );
    }

    @Override
    public void subscribeToPermission(
        @NotNull String permission,
        @NotNull Permissible permissible
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'subscribeToPermission'"
        );
    }

    @Override
    public void unsubscribeFromPermission(
        @NotNull String permission,
        @NotNull Permissible permissible
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'unsubscribeFromPermission'"
        );
    }

    @Override
    public @NotNull Set<Permissible> getPermissionSubscriptions(
        @NotNull String permission
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPermissionSubscriptions'"
        );
    }

    @Override
    public void subscribeToDefaultPerms(
        boolean op,
        @NotNull Permissible permissible
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'subscribeToDefaultPerms'"
        );
    }

    @Override
    public void unsubscribeFromDefaultPerms(
        boolean op,
        @NotNull Permissible permissible
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'unsubscribeFromDefaultPerms'"
        );
    }

    @Override
    public @NotNull Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getDefaultPermSubscriptions'"
        );
    }

    @Override
    public @NotNull Set<Permission> getPermissions() {
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPermissions'"
        );
    }

    @Override
    public void addPermissions(List<Permission> perm) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'addPermissions'"
        );
    }

    @Override
    public void clearPermissions() {
        throw new UnsupportedOperationException(
            "Unimplemented method 'clearPermissions'"
        );
    }

    @Override
    public boolean useTimings() {
        throw new UnsupportedOperationException(
            "Unimplemented method 'useTimings'"
        );
    }

    @Override
    public boolean isTransitiveDependency(
        PluginMeta pluginMeta,
        PluginMeta dependencyConfig
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'isTransitiveDependency'"
        );
    }

    @Override
    public void overridePermissionManager(
        @NotNull Plugin plugin,
        @Nullable PermissionManager permissionManager
    ) {
        throw new UnsupportedOperationException(
            "Unimplemented method 'overridePermissionManager'"
        );
    }
}
