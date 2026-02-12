package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.patchbukkit.testplugin.TestExpectation;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class StubTests {

    private final JavaPlugin plugin;

    public StubTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // --- Server stubs that should throw UnsupportedOperationException ---

    @ConformanceTest(name = "Server.getWorlds() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetWorlds() {
        Bukkit.getServer().getWorlds();
    }

    @ConformanceTest(name = "Server.getMaxPlayers() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetMaxPlayers() {
        Bukkit.getServer().getMaxPlayers();
    }

    @ConformanceTest(name = "Server.getPort() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetPort() {
        Bukkit.getServer().getPort();
    }

    @ConformanceTest(name = "Server.getIp() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetIp() {
        Bukkit.getServer().getIp();
    }

    @ConformanceTest(name = "Server.getViewDistance() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetViewDistance() {
        Bukkit.getServer().getViewDistance();
    }

    @ConformanceTest(name = "Server.getSimulationDistance() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetSimulationDistance() {
        Bukkit.getServer().getSimulationDistance();
    }

    @ConformanceTest(name = "Server.getUpdateFolder() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetUpdateFolder() {
        Bukkit.getServer().getUpdateFolder();
    }

    @ConformanceTest(name = "Server.getUpdateFolderFile() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetUpdateFolderFile() {
        Bukkit.getServer().getUpdateFolderFile();
    }

    @ConformanceTest(name = "Server.getConnectionThrottle() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetConnectionThrottle() {
        Bukkit.getServer().getConnectionThrottle();
    }

    @ConformanceTest(name = "Server.broadcastMessage() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    @SuppressWarnings("deprecation")
    public void testBroadcastMessage() {
        Bukkit.getServer().broadcastMessage("test");
    }

    @ConformanceTest(name = "Server.getOfflinePlayer(UUID) throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetOfflinePlayer() {
        Bukkit.getServer().getOfflinePlayer(UUID.randomUUID());
    }

    @ConformanceTest(name = "Server.getBanList() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetBanList() {
        Bukkit.getServer().getBanList(org.bukkit.BanList.Type.NAME);
    }

    @ConformanceTest(name = "Server.getOperators() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetOperators() {
        Bukkit.getServer().getOperators();
    }

    @ConformanceTest(name = "Server.getWhitelistedPlayers() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetWhitelistedPlayers() {
        Bukkit.getServer().getWhitelistedPlayers();
    }

    @ConformanceTest(name = "Server.reloadWhitelist() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testReloadWhitelist() {
        Bukkit.getServer().reloadWhitelist();
    }

    @ConformanceTest(name = "Server.shutdown() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testShutdown() {
        Bukkit.getServer().shutdown();
    }

    @ConformanceTest(name = "Server.getMotd() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetMotd() {
        Bukkit.getServer().getMotd();
    }

    @ConformanceTest(name = "Server.getAllowNether() throws UnsupportedOperationException",
            category = TestCategory.STUBS, expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetAllowNether() {
        Bukkit.getServer().getAllowNether();
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }
}
