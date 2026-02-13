package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import java.util.Set;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class PermissionsTests {

    @ConformanceTest(name = "addPermission() and getPermission()", category = TestCategory.PERMISSIONS)
    public void testAddAndGetPermission() {
        PluginManager pm = Bukkit.getPluginManager();
        // Clean up from previous runs
        pm.removePermission("patchbukkit.test.add");
        Permission perm = new Permission("patchbukkit.test.add", PermissionDefault.OP);
        pm.addPermission(perm);
        Permission found = pm.getPermission("patchbukkit.test.add");
        assertNotNull(found, "getPermission() after addPermission()");
        assertTrue(found == perm, "getPermission() should return the same instance");
        pm.removePermission(perm);
    }

    @ConformanceTest(name = "removePermission() removes added permission", category = TestCategory.PERMISSIONS)
    public void testRemovePermission() {
        PluginManager pm = Bukkit.getPluginManager();
        pm.removePermission("patchbukkit.test.remove");
        Permission perm = new Permission("patchbukkit.test.remove", PermissionDefault.OP);
        pm.addPermission(perm);
        pm.removePermission(perm);
        Permission found = pm.getPermission("patchbukkit.test.remove");
        assertTrue(found == null, "getPermission() should return null after remove");
    }

    @ConformanceTest(name = "getPermission() returns null for unknown", category = TestCategory.PERMISSIONS)
    public void testGetPermissionUnknown() {
        Permission found = Bukkit.getPluginManager().getPermission("patchbukkit.test.nonexistent");
        assertTrue(found == null, "getPermission(unknown) should return null");
    }

    @ConformanceTest(name = "getDefaultPermissions(op=true) returns set", category = TestCategory.PERMISSIONS)
    public void testGetDefaultPermissionsOp() {
        Set<Permission> defaults = Bukkit.getPluginManager().getDefaultPermissions(true);
        assertNotNull(defaults, "getDefaultPermissions(true)");
    }

    @ConformanceTest(name = "getDefaultPermissions(op=false) returns set", category = TestCategory.PERMISSIONS)
    public void testGetDefaultPermissionsNonOp() {
        Set<Permission> defaults = Bukkit.getPluginManager().getDefaultPermissions(false);
        assertNotNull(defaults, "getDefaultPermissions(false)");
    }

    @ConformanceTest(name = "Console sender has op permissions", category = TestCategory.PERMISSIONS)
    public void testConsoleSenderHasOpPermissions() {
        var console = Bukkit.getConsoleSender();
        assertTrue(console.isOp(), "Console sender should be op");

        // Add a permission with OP default
        PluginManager pm = Bukkit.getPluginManager();
        pm.removePermission("patchbukkit.test.oponly");
        Permission perm = new Permission("patchbukkit.test.oponly", PermissionDefault.OP);
        pm.addPermission(perm);

        // Console (op=true) should have this permission
        boolean hasPerm = console.hasPermission("patchbukkit.test.oponly");
        pm.removePermission(perm);
        assertTrue(hasPerm, "Console (op) should have OP-default permission");
    }

}
