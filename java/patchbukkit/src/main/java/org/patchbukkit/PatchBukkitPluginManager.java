package org.patchbukkit;

import io.papermc.paper.plugin.PermissionManager;
import io.papermc.paper.plugin.configuration.PluginMeta;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
import org.patchbukkit.permissions.PatchBukkitPermissionManager;

@SuppressWarnings("removal")
public class PatchBukkitPluginManager implements PluginManager {
    private final Server server;
    private final PatchBukkitEventManager eventManager;
    private final Map<String, Plugin> plugins = new ConcurrentHashMap<>();
    PermissionManager permissionManager;

    public PatchBukkitPluginManager(Server server) {
        this.server = server;
        this.eventManager = new PatchBukkitEventManager(server);
        this.permissionManager = new PatchBukkitPermissionManager();
    }

    @Override
    public void registerInterface(@NotNull Class<? extends PluginLoader> loader)
        throws IllegalArgumentException {}

    public void registerPlugin(@NotNull Plugin plugin) {
        plugins.put(plugin.getName().toLowerCase(), plugin);
    }

   @Override
    public @Nullable Plugin getPlugin(@NotNull String name) {
        return plugins.get(name.toLowerCase());
    }

   @Override
    public @NotNull Plugin[] getPlugins() {
        return plugins.values().toArray(new Plugin[0]);
    }

    @Override
    public boolean isPluginEnabled(@NotNull String name) {
        Plugin plugin = getPlugin(name);
        return plugin != null && plugin.isEnabled();
    }

    @Override
    public boolean isPluginEnabled(@Nullable Plugin plugin) {
        return plugin != null && plugins.containsValue(plugin) && plugin.isEnabled();
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
        this.eventManager.callEvent(event);
    }

    @Override
    public void registerEvents(@NotNull Listener listener, @NotNull Plugin plugin) {
        this.eventManager.registerEvents(listener, plugin);
    }

    @Override
    public void registerEvent(
        @NotNull Class<? extends Event> event,
        @NotNull Listener listener,
        @NotNull EventPriority priority,
        @NotNull EventExecutor executor,
        @NotNull Plugin plugin
    ) {
        this.eventManager.registerEvent(
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
        this.eventManager.registerEvent(
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
        if (!plugin.isEnabled()) {
            try {
                plugin.getPluginLoader().enablePlugin(plugin);
            } catch (Throwable ex) {
                server.getLogger().severe("Error enabling " + plugin.getName() + ": " + ex.getMessage());
            }
        }
    }

    @Override
    public void disablePlugin(@NotNull Plugin plugin) {
        if (plugin.isEnabled()) {
            plugin.getPluginLoader().disablePlugin(plugin);
        }
    }

    @Override
    public @Nullable Permission getPermission(@NotNull String name) {
        return this.permissionManager.getPermission(name);
    }

    @Override
    public void addPermission(@NotNull Permission perm) {
        this.permissionManager.addPermission(perm);
    }

    @Override
    public void removePermission(@NotNull Permission perm) {
        this.permissionManager.removePermission(perm);
    }

    @Override
    public void removePermission(@NotNull String name) {
        this.permissionManager.removePermission(name);
    }

    @Override
    public @NotNull Set<Permission> getDefaultPermissions(boolean op) {
        return this.permissionManager.getDefaultPermissions(op);
    }

    @Override
    public void recalculatePermissionDefaults(@NotNull Permission perm) {
        this.permissionManager.recalculatePermissionDefaults(perm);
    }

    @Override
    public void subscribeToPermission(@NotNull String permission, @NotNull Permissible permissible) {
        this.permissionManager.subscribeToPermission(permission, permissible);
    }

    @Override
    public void unsubscribeFromPermission(@NotNull String permission, @NotNull Permissible permissible) {
        this.permissionManager.unsubscribeFromPermission(permission, permissible);
    }

    @Override
    public @NotNull Set<Permissible> getPermissionSubscriptions(@NotNull String permission) {
        return this.permissionManager.getPermissionSubscriptions(permission);
    }

    @Override
    public void subscribeToDefaultPerms(boolean op, @NotNull Permissible permissible) {
        this.permissionManager.subscribeToDefaultPerms(op, permissible);
    }

    @Override
    public void unsubscribeFromDefaultPerms(boolean op, @NotNull Permissible permissible) {
        this.permissionManager.unsubscribeFromDefaultPerms(op, permissible);
    }

    @Override
    public @NotNull Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        return this.permissionManager.getDefaultPermSubscriptions(op);
    }

    @Override
    public @NotNull Set<Permission> getPermissions() {
        return this.permissionManager.getPermissions();
    }

    @Override
    public void addPermissions(@NotNull List<Permission> perm) {
        this.permissionManager.addPermissions(perm);
    }

    @Override
    public void clearPermissions() {
        this.permissionManager.clearPermissions();
    }

    @Override
    public void overridePermissionManager(@NotNull Plugin plugin, @Nullable PermissionManager permissionManager) {
        this.permissionManager = permissionManager;
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
}
