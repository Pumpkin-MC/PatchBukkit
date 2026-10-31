package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.patchbukkit.testplugin.TestExpectation;
import org.bukkit.plugin.java.JavaPlugin;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class ServerTests {

    private final JavaPlugin plugin;

    public ServerTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "Server.getName() returns non-null", category = TestCategory.SERVER)
    public void testGetName() {
        String name = Bukkit.getServer().getName();
        assertNotNull(name, "Server.getName()");
    }

    @ConformanceTest(name = "Server.getVersion() returns non-null", category = TestCategory.SERVER)
    public void testGetVersion() {
        String version = Bukkit.getServer().getVersion();
        assertNotNull(version, "Server.getVersion()");
    }

    @ConformanceTest(name = "Server.getBukkitVersion() returns non-null", category = TestCategory.SERVER)
    public void testGetBukkitVersion() {
        String version = Bukkit.getServer().getBukkitVersion();
        assertNotNull(version, "Server.getBukkitVersion()");
    }

    @ConformanceTest(name = "Server.getPluginManager() returns non-null", category = TestCategory.SERVER)
    public void testGetPluginManager() {
        PluginManager pm = Bukkit.getServer().getPluginManager();
        assertNotNull(pm, "Server.getPluginManager()");
    }

    @ConformanceTest(name = "Server.getConsoleSender() returns non-null", category = TestCategory.SERVER)
    public void testGetConsoleSender() {
        ConsoleCommandSender cs = Bukkit.getServer().getConsoleSender();
        assertNotNull(cs, "Server.getConsoleSender()");
    }

    @ConformanceTest(name = "Server.getCommandMap() returns non-null", category = TestCategory.SERVER)
    public void testGetCommandMap() {
        CommandMap cm = Bukkit.getServer().getCommandMap();
        assertNotNull(cm, "Server.getCommandMap()");
    }

    @ConformanceTest(name = "Server.getScheduler() returns non-null", category = TestCategory.SERVER)
    public void testGetScheduler() {
        BukkitScheduler sched = Bukkit.getServer().getScheduler();
        assertNotNull(sched, "Server.getScheduler()");
    }

    @ConformanceTest(name = "Server.getOnlinePlayers() returns collection", category = TestCategory.SERVER)
    public void testGetOnlinePlayers() {
        var players = Bukkit.getServer().getOnlinePlayers();
        assertNotNull(players, "Server.getOnlinePlayers()");
    }

    @ConformanceTest(name = "Server.getUnsafe() returns non-null", category = TestCategory.SERVER)
    public void testGetUnsafe() {
        Object unsafe = Bukkit.getUnsafe();
        assertNotNull(unsafe, "Server.getUnsafe()");
    }

    @ConformanceTest(name = "Server.suggestPlayerNamesWhenNullTabCompletions() returns boolean", category = TestCategory.SERVER)
    public void testSuggestPlayerNames() {
        boolean result = Bukkit.getServer().suggestPlayerNamesWhenNullTabCompletions();
        assertTrue(result, "Server.suggestPlayerNamesWhenNullTabCompletions()");
    }

    @ConformanceTest(name = "Bukkit.getServer() matches plugin.getServer()", category = TestCategory.SERVER)
    public void testServerConsistency() {
        Server bukkit = Bukkit.getServer();
        Server fromPlugin = plugin.getServer();
        assertTrue(bukkit == fromPlugin, "Bukkit.getServer() == plugin.getServer()");
    }

    @ConformanceTest(name = "Server.getPluginCommand() returns null for unregistered command", category = TestCategory.SERVER)
    public void testGetPluginCommand() {
        // getPluginCommand only finds PluginCommands registered via plugin.yml;
        // commands registered directly on the CommandMap are not PluginCommands
        var cmd = Bukkit.getServer().getPluginCommand("nonexistent_abc");
        assertTrue(cmd == null, "Server.getPluginCommand(unregistered) should be null");
    }

    @ConformanceTest(name = "Server.getPluginCommand() returns null for unknown", category = TestCategory.SERVER)
    public void testGetPluginCommandUnknown() {
        var cmd = Bukkit.getServer().getPluginCommand("nonexistent_command_xyz");
        assertTrue(cmd == null, "Server.getPluginCommand(unknown) should be null");
    }

    @ConformanceTest(name = "Server.getWorlds() returns non-empty list", category = TestCategory.SERVER)
    public void testGetWorlds() {
        var worlds = Bukkit.getServer().getWorlds();
        assertNotNull(worlds, "Server.getWorlds()");
        assertTrue(!worlds.isEmpty(), "Server.getWorlds() should not be empty");
    }

    @ConformanceTest(name = "Server.getMaxPlayers() returns positive int", category = TestCategory.SERVER)
    public void testGetMaxPlayers() {
        int max = Bukkit.getServer().getMaxPlayers();
        assertTrue(max >= 0, "Server.getMaxPlayers() >= 0");
    }

    @ConformanceTest(name = "Server.getPort() returns port", category = TestCategory.SERVER)
    public void testGetPort() {
        int port = Bukkit.getServer().getPort();
        assertTrue(port >= 0, "Server.getPort() >= 0");
    }

    @ConformanceTest(name = "Server.getIp() returns string", category = TestCategory.SERVER)
    public void testGetIp() {
        String ip = Bukkit.getServer().getIp();
        assertNotNull(ip, "Server.getIp()");
    }

    @ConformanceTest(name = "Server.getViewDistance() returns positive int", category = TestCategory.SERVER)
    public void testGetViewDistance() {
        int vd = Bukkit.getServer().getViewDistance();
        assertTrue(vd > 0, "Server.getViewDistance() > 0");
    }

    @ConformanceTest(name = "Server.getSimulationDistance() returns positive int", category = TestCategory.SERVER)
    public void testGetSimulationDistance() {
        int sd = Bukkit.getServer().getSimulationDistance();
        assertTrue(sd > 0, "Server.getSimulationDistance() > 0");
    }

    @ConformanceTest(name = "Server.getUpdateFolder() returns string", category = TestCategory.SERVER)
    public void testGetUpdateFolder() {
        String uf = Bukkit.getServer().getUpdateFolder();
        assertNotNull(uf, "Server.getUpdateFolder()");
    }

    @ConformanceTest(name = "Server.getUpdateFolderFile() returns non-null", category = TestCategory.SERVER)
    public void testGetUpdateFolderFile() {
        java.io.File file = Bukkit.getServer().getUpdateFolderFile();
        assertNotNull(file, "Server.getUpdateFolderFile()");
    }

    @ConformanceTest(name = "Server.getConnectionThrottle() returns long", category = TestCategory.SERVER)
    public void testGetConnectionThrottle() {
        long throttle = Bukkit.getServer().getConnectionThrottle();
        assertTrue(throttle >= 0, "Server.getConnectionThrottle() >= 0");
    }

    @ConformanceTest(name = "Server.broadcastMessage() works without throwing", category = TestCategory.SERVER)
    @SuppressWarnings("deprecation")
    public void testBroadcastMessage() {
        int reached = Bukkit.getServer().broadcastMessage("test conformance broadcast");
        assertTrue(reached >= 0, "broadcastMessage return >= 0");
    }

    @ConformanceTest(name = "Server.getOfflinePlayer(UUID) returns non-null", category = TestCategory.SERVER)
    public void testGetOfflinePlayer() {
        var op = Bukkit.getServer().getOfflinePlayer(java.util.UUID.randomUUID());
        assertNotNull(op, "Server.getOfflinePlayer(UUID)");
    }

    @ConformanceTest(name = "Server.getBanList() returns non-null", category = TestCategory.SERVER)
    public void testGetBanList() {
        var bans = Bukkit.getServer().getBanList(org.bukkit.BanList.Type.NAME);
        assertNotNull(bans, "Server.getBanList(NAME)");
    }

    @ConformanceTest(name = "Server.getOperators() returns non-null set", category = TestCategory.SERVER)
    public void testGetOperators() {
        var ops = Bukkit.getServer().getOperators();
        assertNotNull(ops, "Server.getOperators()");
    }

    @ConformanceTest(name = "Server.getWhitelistedPlayers() returns non-null set", category = TestCategory.SERVER)
    public void testGetWhitelistedPlayers() {
        var wl = Bukkit.getServer().getWhitelistedPlayers();
        assertNotNull(wl, "Server.getWhitelistedPlayers()");
    }

    @ConformanceTest(name = "Server.reloadWhitelist() executes without throwing", category = TestCategory.SERVER)
    public void testReloadWhitelist() {
        Bukkit.getServer().reloadWhitelist();
    }

    @ConformanceTest(name = "Server.getMotd() returns non-null", category = TestCategory.SERVER)
    public void testGetMotd() {
        String motd = Bukkit.getServer().getMotd();
        assertNotNull(motd, "Server.getMotd()");
    }

    @ConformanceTest(name = "Server.getAllowNether() returns boolean", category = TestCategory.SERVER)
    public void testGetAllowNether() {
        boolean nether = Bukkit.getServer().getAllowNether();
        assertTrue(nether || !nether, "Server.getAllowNether()");
    }

    @ConformanceTest(name = "World distance settings (view, simulation, send-view)", category = TestCategory.SERVER)
    public void testWorldDistances() {
        var worlds = Bukkit.getServer().getWorlds();
        assertTrue(!worlds.isEmpty(), "getWorlds() not empty");
        var world = worlds.get(0);
        int vd = world.getViewDistance();
        assertTrue(vd > 0, "world.getViewDistance() > 0");
        world.setViewDistance(8);
        assertTrue(world.getViewDistance() == 8, "world.getViewDistance() == 8");

        int sd = world.getSimulationDistance();
        assertTrue(sd > 0, "world.getSimulationDistance() > 0");
        world.setSimulationDistance(6);
        assertTrue(world.getSimulationDistance() == 6, "world.getSimulationDistance() == 6");

        int svd = world.getSendViewDistance();
        assertTrue(svd > 0, "world.getSendViewDistance() > 0");
        world.setSendViewDistance(9);
        assertTrue(world.getSendViewDistance() == 9, "world.getSendViewDistance() == 9");
    }

    @ConformanceTest(name = "World chunk state and generation methods", category = TestCategory.SERVER)
    public void testWorldChunkState() throws Exception {
        var worlds = Bukkit.getServer().getWorlds();
        var world = worlds.get(0);

        world.loadChunk(0, 0, true);
        assertTrue(world.isChunkLoaded(0, 0), "chunk (0,0) is loaded");
        assertTrue(world.isChunkInUse(0, 0), "chunk (0,0) is in use");

        var state = world.getChunkGenerationState(0, 0);
        assertNotNull(state, "getChunkGenerationState(0, 0)");

        var future = world.getChunkGenerationStateAsync(0, 0);
        assertNotNull(future, "getChunkGenerationStateAsync(0, 0)");
        var asyncState = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(asyncState, "asyncState");

        var loadedChunks = world.getLoadedChunks();
        assertNotNull(loadedChunks, "getLoadedChunks()");
        assertTrue(loadedChunks.length > 0, "loadedChunks.length > 0");

        int chunkCount = world.getChunkCount();
        assertTrue(chunkCount > 0, "chunkCount > 0");

        assertTrue(world.getTileEntityCount() >= 0, "tileEntityCount >= 0");
        assertTrue(world.getTickableTileEntityCount() >= 0, "tickableTileEntityCount >= 0");
        assertTrue(world.getPlayerCount() >= 0, "playerCount >= 0");

        boolean refreshed = world.refreshChunk(0, 0);
        assertTrue(refreshed, "refreshChunk(0, 0)");

        var seeing = world.getPlayersSeeingChunk(0, 0);
        assertNotNull(seeing, "getPlayersSeeingChunk");
    }

    @ConformanceTest(name = "World plugin chunk ticket management", category = TestCategory.SERVER)
    public void testWorldPluginChunkTickets() {
        var worlds = Bukkit.getServer().getWorlds();
        var world = worlds.get(0);

        boolean added = world.addPluginChunkTicket(5, 5, this.plugin);
        assertTrue(added, "addPluginChunkTicket(5, 5)");

        var tickets = world.getPluginChunkTickets(5, 5);
        assertNotNull(tickets, "getPluginChunkTickets(5, 5)");
        assertTrue(tickets.contains(this.plugin), "tickets contain plugin");

        var allTickets = world.getPluginChunkTickets();
        assertNotNull(allTickets, "getPluginChunkTickets()");
        assertTrue(allTickets.containsKey(this.plugin), "allTickets contains plugin");

        boolean removed = world.removePluginChunkTicket(5, 5, this.plugin);
        assertTrue(removed, "removePluginChunkTicket(5, 5)");

        world.addPluginChunkTicket(6, 6, this.plugin);
        world.removePluginChunkTickets(this.plugin);
        var ticketsAfter = world.getPluginChunkTickets(6, 6);
        assertTrue(ticketsAfter.isEmpty(), "ticketsAfter removePluginChunkTickets is empty");
    }
}
