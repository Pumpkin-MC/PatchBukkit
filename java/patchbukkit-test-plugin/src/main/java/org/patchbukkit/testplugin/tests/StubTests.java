package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import static org.patchbukkit.testplugin.TestAssertions.*;

import java.util.UUID;

public final class StubTests {

    @ConformanceTest(name = "Server.getWorlds() works",
            category = TestCategory.STUBS)
    public void testGetWorlds() {
        Bukkit.getServer().getWorlds();
    }

    @ConformanceTest(name = "Server.getMaxPlayers() works",
            category = TestCategory.STUBS)
    public void testGetMaxPlayers() {
        Bukkit.getServer().getMaxPlayers();
    }

    @ConformanceTest(name = "Server.getPort() works",
            category = TestCategory.STUBS)
    public void testGetPort() {
        Bukkit.getServer().getPort();
    }

    @ConformanceTest(name = "Server.getIp() works",
            category = TestCategory.STUBS)
    public void testGetIp() {
        Bukkit.getServer().getIp();
    }

    @ConformanceTest(name = "Server.getViewDistance() works",
            category = TestCategory.STUBS)
    public void testGetViewDistance() {
        Bukkit.getServer().getViewDistance();
    }

    @ConformanceTest(name = "Server.getSimulationDistance() works",
            category = TestCategory.STUBS)
    public void testGetSimulationDistance() {
        Bukkit.getServer().getSimulationDistance();
    }

    @ConformanceTest(name = "Server.getUpdateFolder() works",
            category = TestCategory.STUBS)
    public void testGetUpdateFolder() {
        Bukkit.getServer().getUpdateFolder();
    }

    @ConformanceTest(name = "Server.getUpdateFolderFile() works",
            category = TestCategory.STUBS)
    public void testGetUpdateFolderFile() {
        Bukkit.getServer().getUpdateFolderFile();
    }

    @ConformanceTest(name = "Server.getConnectionThrottle() works",
            category = TestCategory.STUBS)
    public void testGetConnectionThrottle() {
        Bukkit.getServer().getConnectionThrottle();
    }

    @ConformanceTest(name = "Server.broadcastMessage() works",
            category = TestCategory.STUBS)
    @SuppressWarnings("deprecation")
    public void testBroadcastMessage() {
        Bukkit.getServer().broadcastMessage("test");
    }

    @ConformanceTest(name = "Server.getOfflinePlayer(UUID) works",
            category = TestCategory.STUBS)
    public void testGetOfflinePlayer() {
        Bukkit.getServer().getOfflinePlayer(UUID.randomUUID());
    }

    @ConformanceTest(name = "Server.getBanList() works",
            category = TestCategory.STUBS)
    public void testGetBanList() {
        Bukkit.getServer().getBanList(org.bukkit.BanList.Type.NAME);
    }

    @ConformanceTest(name = "Server.getOperators() works",
            category = TestCategory.STUBS)
    public void testGetOperators() {
        Bukkit.getServer().getOperators();
    }

    @ConformanceTest(name = "Server.getWhitelistedPlayers() works",
            category = TestCategory.STUBS)
    public void testGetWhitelistedPlayers() {
        Bukkit.getServer().getWhitelistedPlayers();
    }

    @ConformanceTest(name = "Server.reloadWhitelist() works",
            category = TestCategory.STUBS)
    public void testReloadWhitelist() {
        Bukkit.getServer().reloadWhitelist();
    }

    // NOTE: Server.shutdown() is intentionally not tested here — it would
    // actually stop the server when running /pbtest.

    @ConformanceTest(name = "Server.getMotd() works",
            category = TestCategory.STUBS)
    public void testGetMotd() {
        Bukkit.getServer().getMotd();
    }

    @ConformanceTest(name = "Server.getAllowNether() works",
            category = TestCategory.STUBS)
    public void testGetAllowNether() {
        Bukkit.getServer().getAllowNether();
    }

    @ConformanceTest(name = "InventoryType initializes (menu registry fallback)", category = TestCategory.STUBS)
    public void testInventoryTypeInit() {
        // Touching InventoryType runs MenuType.<clinit>, which resolves every
        // vanilla menu key via getOrThrow. A single gap used to poison both
        // classes for the JVM lifetime and break all in-game commands.
        org.bukkit.event.inventory.InventoryType type = org.bukkit.event.inventory.InventoryType.CHEST;
        assertNotNull(type, "InventoryType.CHEST");
        assertTrue(type.getDefaultSize() > 0, "InventoryType.CHEST.getDefaultSize()");
    }

    @ConformanceTest(name = "PaperLib detection classes and PaperLib.isPaper() are available", category = TestCategory.STUBS)
    public void testPaperLibDetection() throws ClassNotFoundException {
        Class.forName("com.destroystokyo.paper.PaperConfig");
        Class.forName("io.papermc.paper.configuration.Configuration");
        org.patchbukkit.testplugin.TestAssertions.assertTrue(io.papermc.lib.PaperLib.isPaper(), "PaperLib.isPaper() must be true");
    }

}
