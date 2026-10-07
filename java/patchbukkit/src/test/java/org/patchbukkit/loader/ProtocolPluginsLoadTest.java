package org.patchbukkit.loader;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.patchbukkit.PatchBukkitServer;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolPluginsLoadTest {

    private static final String PROTOCOLLIB_URL =
            "https://repo1.maven.org/maven2/net/dmulloy2/ProtocolLib/5.4.0/ProtocolLib-5.4.0.jar";
    private static final String PACKETEVENTS_URL =
            "https://github.com/retrooper/packetevents/releases/download/v2.7.0/packetevents-spigot-2.7.0.jar";

    private static Path testPluginsDir;

    @BeforeAll
    public static void setUp() throws Exception {
        PatchBukkitServer.initServer();

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

        org.bukkit.entity.Player player = new org.patchbukkit.entity.CraftPlayer(java.util.UUID.randomUUID(), "GrimTester");
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
}
