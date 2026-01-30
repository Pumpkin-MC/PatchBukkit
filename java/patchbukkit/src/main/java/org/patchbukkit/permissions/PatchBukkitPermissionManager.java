package org.patchbukkit.permissions;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.google.common.collect.ImmutableSet;

import io.papermc.paper.plugin.PermissionManager;

public class PatchBukkitPermissionManager implements PermissionManager {
    private final Map<String, Permission> permissions = new HashMap<>();
    private final Map<Boolean, Set<Permission>> defaultPerms = new LinkedHashMap<>();
    private final Map<String, Map<Permissible, Boolean>> permSubs = new HashMap<>();
    private final Map<Boolean, Map<Permissible, Boolean>> defSubs = new HashMap<>();

    public PatchBukkitPermissionManager() {
        this.defaultPerms().put(true, new LinkedHashSet<>());
        this.defaultPerms().put(false, new LinkedHashSet<>());
    }

    public Map<String, Permission> permissions() {
        return this.permissions;
    }

    public Map<Boolean, Set<Permission>> defaultPerms() {
        return this.defaultPerms;
    }

    public Map<String, Map<Permissible, Boolean>> permSubs() {
        return this.permSubs;
    }

    public Map<Boolean, Map<Permissible, Boolean>> defSubs() {
        return this.defSubs;
    }

    @Override
    @Nullable
    public Permission getPermission(@NotNull String name) {
        return this.permissions().get(name.toLowerCase(java.util.Locale.ENGLISH));
    }

    @Override
    public void addPermission(@NotNull Permission perm) {
        this.addPermission(perm, true);
    }

    @Override
    public void addPermissions(@NotNull List<Permission> permissions) {
        for (Permission permission : permissions) {
            this.addPermission(permission, false);
        }
        this.dirtyPermissibles();
    }

    // Allow suppressing permission default calculations
    private void addPermission(@NotNull Permission perm, boolean dirty) {
        String name = perm.getName().toLowerCase(java.util.Locale.ENGLISH);

        if (this.permissions().containsKey(name)) {
            throw new IllegalArgumentException("The permission " + name + " is already defined!");
        }

        this.permissions().put(name, perm);
        this.calculatePermissionDefault(perm, dirty);
    }

    @Override
    @NotNull
    public Set<Permission> getDefaultPermissions(boolean op) {
        return ImmutableSet.copyOf(this.defaultPerms().get(op));
    }


    @Override
    public void removePermission(@NotNull Permission perm) {
        this.removePermission(perm.getName());
    }


    @Override
    public void removePermission(@NotNull String name) {
        this.permissions().remove(name.toLowerCase(java.util.Locale.ENGLISH));
    }

    @Override
    public void recalculatePermissionDefaults(@NotNull Permission perm) {
        // we need a null check here because some plugins for some unknown reason pass null into this?
        if (perm != null && this.permissions().containsKey(perm.getName().toLowerCase(Locale.ROOT))) {
            this.defaultPerms().get(true).remove(perm);
            this.defaultPerms().get(false).remove(perm);

            this.calculatePermissionDefault(perm, true);
        }
    }

    private void calculatePermissionDefault(@NotNull Permission perm, boolean dirty) {
        if ((perm.getDefault() == PermissionDefault.OP) || (perm.getDefault() == PermissionDefault.TRUE)) {
            this.defaultPerms().get(true).add(perm);
            if (dirty) {
                this.dirtyPermissibles(true);
            }
        }
        if ((perm.getDefault() == PermissionDefault.NOT_OP) || (perm.getDefault() == PermissionDefault.TRUE)) {
            this.defaultPerms().get(false).add(perm);
            if (dirty) {
                this.dirtyPermissibles(false);
            }
        }
    }


    @Override
    public void subscribeToPermission(@NotNull String permission, @NotNull Permissible permissible) {
        String name = permission.toLowerCase(java.util.Locale.ENGLISH);
        Map<Permissible, Boolean> map = this.permSubs().computeIfAbsent(name, k -> new WeakHashMap<>());

        map.put(permissible, true);
    }

    @Override
    public void unsubscribeFromPermission(@NotNull String permission, @NotNull Permissible permissible) {
        String name = permission.toLowerCase(java.util.Locale.ENGLISH);
        Map<Permissible, Boolean> map = this.permSubs().get(name);

        if (map != null) {
            map.remove(permissible);

            if (map.isEmpty()) {
                this.permSubs().remove(name);
            }
        }
    }

    @Override
    @NotNull
    public Set<Permissible> getPermissionSubscriptions(@NotNull String permission) {
        String name = permission.toLowerCase(java.util.Locale.ENGLISH);
        Map<Permissible, Boolean> map = this.permSubs().get(name);

        if (map == null) {
            return ImmutableSet.of();
        } else {
            return ImmutableSet.copyOf(map.keySet());
        }
    }

    @Override
    public void subscribeToDefaultPerms(boolean op, @NotNull Permissible permissible) {
        Map<Permissible, Boolean> map = this.defSubs().computeIfAbsent(op, k -> new WeakHashMap<>());

        map.put(permissible, true);
    }

    @Override
    public void unsubscribeFromDefaultPerms(boolean op, @NotNull Permissible permissible) {
        Map<Permissible, Boolean> map = this.defSubs().get(op);

        if (map != null) {
            map.remove(permissible);

            if (map.isEmpty()) {
                this.defSubs().remove(op);
            }
        }
    }

    @Override
    @NotNull
    public Set<Permissible> getDefaultPermSubscriptions(boolean op) {
        Map<Permissible, Boolean> map = this.defSubs().get(op);

        if (map == null) {
            return ImmutableSet.of();
        } else {
            return ImmutableSet.copyOf(map.keySet());
        }
    }

    @Override
    @NotNull
    public Set<Permission> getPermissions() {
        return new HashSet<>(this.permissions().values());
    }

    @Override
    public void clearPermissions() {
        this.permissions().clear();
        this.defaultPerms().get(true).clear();
        this.defaultPerms().get(false).clear();
    }


    void dirtyPermissibles(boolean op) {
        Set<Permissible> permissibles = this.getDefaultPermSubscriptions(op);

        for (Permissible p : permissibles) {
            p.recalculatePermissions();
        }
    }

    void dirtyPermissibles() {
        this.dirtyPermissibles(true);
        this.dirtyPermissibles(false);
    }
}
