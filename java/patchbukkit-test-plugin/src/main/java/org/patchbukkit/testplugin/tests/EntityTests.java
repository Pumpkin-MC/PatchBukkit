package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.patchbukkit.testplugin.TestExpectation;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.UUID;

public final class EntityTests {

    private final JavaPlugin plugin;

    public EntityTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "Server.getOnlinePlayers() returns iterable collection", category = TestCategory.ENTITY)
    public void testOnlinePlayersIteration() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        assertNotNull(players, "Bukkit.getOnlinePlayers()");
        // Iterate without error
        for (Player p : players) {
            assertNotNull(p, "Player in online players collection");
        }
    }

    @ConformanceTest(name = "Server.getEntity(UUID) stub", category = TestCategory.ENTITY,
            expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testGetEntity() {
        Bukkit.getServer().getEntity(UUID.randomUUID());
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }
}
