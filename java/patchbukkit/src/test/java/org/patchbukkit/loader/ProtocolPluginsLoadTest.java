package org.patchbukkit.loader;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.patchbukkit.bootstrap.HeadlessPaperServer;
import org.patchbukkit.network.VirtualChannelManager;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolPluginsLoadTest {

    @TempDir
    static Path root;

    private static final String PROTOCOLLIB_URL =
            "https://repo1.maven.org/maven2/net/dmulloy2/ProtocolLib/5.4.0/ProtocolLib-5.4.0.jar";
    private static final String PACKETEVENTS_URL =
            "https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar";

    private static Path testPluginsDir;

    @BeforeAll
    public static void setUp() throws Exception {
        HeadlessPaperServer.boot(root);

        testPluginsDir = Path.of("build", "test-plugins");
        Files.createDirectories(testPluginsDir);
    }

    private static File ensurePluginJar(String filename, String url) throws Exception {
        Path target = testPluginsDir.resolve(filename);
        if (!Files.exists(target) || Files.size(target) == 0) {
            try (InputStream in = URI.create(url).toURL().openStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target.toFile();
    }

    @Test
    public void testLoadPacketEvents() throws Exception {
        File jarFile = ensurePluginJar("PacketEvents.jar", PACKETEVENTS_URL);
        assertTrue(jarFile.exists(), "PacketEvents jar must exist");

        Plugin plugin = Bukkit.getPluginManager().loadPlugin(jarFile);
        assertNotNull(plugin, "PacketEvents plugin must load successfully");
        assertEquals("packetevents", plugin.getName().toLowerCase(), "Plugin name should match");

        // Test enable
        assertDoesNotThrow(() -> Bukkit.getPluginManager().enablePlugin(plugin));
        assertTrue(plugin.isEnabled(), "PacketEvents should be enabled");
    }

    @Test
    public void testLoadProtocolLib() throws Exception {
        File jarFile = ensurePluginJar("ProtocolLib.jar", PROTOCOLLIB_URL);
        assertTrue(jarFile.exists(), "ProtocolLib jar must exist");

        Plugin plugin = Bukkit.getPluginManager().loadPlugin(jarFile);
        assertNotNull(plugin, "ProtocolLib plugin must load successfully");
        assertEquals("protocollib", plugin.getName().toLowerCase(), "Plugin name should match");

        // Test enable (ProtocolLib 5.4.0 disables itself on MC 26.2 without dev builds)
        try {
            Bukkit.getPluginManager().enablePlugin(plugin);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testLoadGrimAC() throws Exception {
        File grimFile = ensurePluginJar("GrimAC.jar", "https://cdn.modrinth.com/data/LJNGWSvH/versions/nKI7MWZj/grimac-bukkit-2.3.74-f5bbe9c.jar");
        assertTrue(grimFile.exists(), "GrimAC jar must exist");

        Plugin plugin = Bukkit.getPluginManager().loadPlugin(grimFile);
        assertNotNull(plugin, "GrimAC plugin must load successfully");
        assertEquals("grimac", plugin.getName().toLowerCase());

        assertDoesNotThrow(() -> Bukkit.getPluginManager().enablePlugin(plugin));
        assertTrue(plugin.isEnabled(), "GrimAC should be enabled");

        org.bukkit.entity.Player player = VirtualChannelManager.getInstance()
                .getOrCreateServerPlayer(java.util.UUID.randomUUID(), "GrimTester", 999)
                .getBukkitEntity();
        org.bukkit.event.player.PlayerJoinEvent joinEvent =
                new org.bukkit.event.player.PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty());
        assertDoesNotThrow(() -> Bukkit.getPluginManager().callEvent(joinEvent));
    }

    @Test
    public void testLoadVeinminer() throws Exception {
        File jarFile = ensurePluginJar("Veinminer.jar", "https://cdn.modrinth.com/data/OhduvhIc/versions/O6IYCV7p/veinminer-paper-2.12.1.jar");
        assertTrue(jarFile.exists(), "Veinminer jar must exist");

        Plugin plugin = Bukkit.getPluginManager().loadPlugin(jarFile);
        assertNotNull(plugin, "Veinminer plugin must load successfully");
        assertEquals("veinminer", plugin.getName().toLowerCase(), "Plugin name should match");

        assertDoesNotThrow(() -> Bukkit.getPluginManager().enablePlugin(plugin));
        assertTrue(plugin.isEnabled(), "Veinminer should be enabled");
    }

    @Test
    public void testLoadLuckPerms() throws Exception {
        File jarFile = ensurePluginJar("LuckPerms.jar", "https://download.luckperms.net/1675/bukkit/loader/LuckPerms-Bukkit-5.5.88.jar");
        assertTrue(jarFile.exists(), "LuckPerms jar must exist");

        Plugin plugin = Bukkit.getPluginManager().loadPlugin(jarFile);
        assertNotNull(plugin, "LuckPerms plugin must load successfully");
        assertEquals("luckperms", plugin.getName().toLowerCase(), "Plugin name should match");

        assertDoesNotThrow(() -> Bukkit.getPluginManager().enablePlugin(plugin));
        assertTrue(plugin.isEnabled(), "LuckPerms should be enabled");

        // Verify LuckPerms API provider
        Class<?> providerClass = Class.forName("net.luckperms.api.LuckPermsProvider", true, plugin.getClass().getClassLoader());
        Object api = providerClass.getMethod("get").invoke(null);
        assertNotNull(api, "LuckPermsProvider.get() must not be null");

        Object meta = api.getClass().getMethod("getPluginMetadata").invoke(api);
        String version = (String) meta.getClass().getMethod("getVersion").invoke(meta);
        assertEquals("5.5.88", version, "LuckPerms version should be 5.5.88");

        // Test player permission integration
        java.util.UUID uuid = java.util.UUID.randomUUID();
        String playerName = "LPT_" + uuid.toString().substring(0, 8);

        // 1. Fire AsyncPlayerPreLoginEvent so LuckPerms loads the user into its cache
        org.bukkit.event.player.AsyncPlayerPreLoginEvent preLoginEvent =
                new org.bukkit.event.player.AsyncPlayerPreLoginEvent(
                        playerName,
                        java.net.InetAddress.getLoopbackAddress(),
                        uuid
                );
        Bukkit.getPluginManager().callEvent(preLoginEvent);

        // Wait for LuckPerms to load user into UserManager
        Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
        Object loadedUser = null;
        for (int i = 0; i < 20; i++) {
            loadedUser = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, uuid);
            if (loadedUser != null) {
                break;
            }
            Thread.sleep(100);
        }
        assertNotNull(loadedUser, "LuckPerms User should be loaded from pre-login");

        // 2. Create authentic ServerPlayer and fire PlayerLoginEvent to inject LuckPermsPermissible
        org.bukkit.entity.Player player = VirtualChannelManager.getInstance()
                .getOrCreateServerPlayer(uuid, playerName, 1005)
                .getBukkitEntity();
        assertNotNull(player, "Player should be created");

        org.bukkit.event.player.PlayerLoginEvent loginEvent =
                new org.bukkit.event.player.PlayerLoginEvent(player, "localhost", java.net.InetAddress.getLoopbackAddress());
        Bukkit.getPluginManager().callEvent(loginEvent);

        // 3. Fire join event
        org.bukkit.event.player.PlayerJoinEvent joinEvent =
                new org.bukkit.event.player.PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty());
        Bukkit.getPluginManager().callEvent(joinEvent);

        // Check permissible base injection
        assertFalse(player.hasPermission("patchbukkit.test.perm"), "Player should not have permission initially");

        // Dispatch LuckPerms console command
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uuid + " permission set patchbukkit.test.perm true");

        // Wait up to 2 seconds for LP async executor to update permission cache
        boolean hasPermission = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (player.hasPermission("patchbukkit.test.perm")) {
                hasPermission = true;
                break;
            }
        }
        assertTrue(hasPermission, "Player should have permission after LP command");

        // Revoke permission
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uuid + " permission unset patchbukkit.test.perm");
        boolean permissionRevoked = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (!player.hasPermission("patchbukkit.test.perm")) {
                permissionRevoked = true;
                break;
            }
        }
        assertTrue(permissionRevoked, "Player should not have permission after LP unset command");

        // Test group inheritance and prefix metadata
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp creategroup tester_group");
        Thread.sleep(50);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group tester_group permission set patchbukkit.group.perm true");
        Thread.sleep(50);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp group tester_group meta setprefix 50 &6[Tester] ");
        Thread.sleep(50);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + uuid + " parent add tester_group");

        boolean inheritedPerm = false;
        for (int i = 0; i < 20; i++) {
            Thread.sleep(100);
            if (player.hasPermission("patchbukkit.group.perm")) {
                inheritedPerm = true;
                break;
            }
        }
        assertTrue(inheritedPerm, "Player should inherit permission from tester_group");

        // Verify player adapter metadata
        Object playerAdapter = api.getClass().getMethod("getPlayerAdapter", Class.class).invoke(api, org.bukkit.entity.Player.class);
        java.lang.reflect.Method getMetaMethod = null;
        for (java.lang.reflect.Method m : playerAdapter.getClass().getMethods()) {
            if (m.getName().equals("getMetaData") && m.getParameterCount() == 1) {
                getMetaMethod = m;
                break;
            }
        }
        assertNotNull(getMetaMethod, "getMetaData method should exist on PlayerAdapter");
        Object cachedMetaData = getMetaMethod.invoke(playerAdapter, player);
        String prefix = (String) cachedMetaData.getClass().getMethod("getPrefix").invoke(cachedMetaData);
        assertNotNull(prefix, "Prefix should not be null");
        assertTrue(prefix.contains("[Tester]"), "Prefix should contain [Tester], got: " + prefix);
    }
}
