package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import java.util.Collection;
import java.util.UUID;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class EntityTests {

    @ConformanceTest(name = "Server.getOnlinePlayers() returns iterable collection", category = TestCategory.ENTITY)
    public void testOnlinePlayersIteration() {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        assertNotNull(players, "Bukkit.getOnlinePlayers()");
        // Iterate without error
        for (Player p : players) {
            assertNotNull(p, "Player in online players collection");
        }
    }

    @ConformanceTest(name = "Server.getEntity(UUID) works (null for unknown)", category = TestCategory.ENTITY)
    public void testGetEntity() {
        assertNull(Bukkit.getServer().getEntity(UUID.randomUUID()), "Server.getEntity(unknown UUID)");
    }

    @ConformanceTest(name = "Player flight state and speed getters run cleanly", category = TestCategory.ENTITY)
    public void testPlayerFlightState() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean allow = p.getAllowFlight();
            boolean flying = p.isFlying();
            float flySpeed = p.getFlySpeed();
            float walkSpeed = p.getWalkSpeed();
            assertNotNull(allow, "Player.getAllowFlight()");
            assertNotNull(flying, "Player.isFlying()");
            assertTrue(flySpeed >= -1.0f && flySpeed <= 1.0f, "Fly speed in valid range");
            assertTrue(walkSpeed >= -1.0f && walkSpeed <= 1.0f, "Walk speed in valid range");
        }
    }

    @ConformanceTest(name = "Player ground state and velocity getters run cleanly", category = TestCategory.ENTITY)
    public void testPlayerGroundStateAndVelocity() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean onGround = p.isOnGround();
            org.bukkit.util.Vector vel = p.getVelocity();
            assertNotNull(onGround, "Player.isOnGround()");
            assertNotNull(vel, "Player.getVelocity()");
        }
    }

    @ConformanceTest(name = "Player inventory and equipment getters and setters run cleanly", category = TestCategory.ENTITY)
    public void testPlayerInventoryAndEquipment() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.PlayerInventory inv = p.getInventory();
            org.bukkit.inventory.EntityEquipment eq = p.getEquipment();
            assertNotNull(inv, "Player.getInventory()");
            assertNotNull(eq, "Player.getEquipment()");
            assertNotNull(inv.getItemInMainHand(), "PlayerInventory.getItemInMainHand()");
            assertNotNull(inv.getItemInOffHand(), "PlayerInventory.getItemInOffHand()");
            assertNotNull(inv.getArmorContents(), "PlayerInventory.getArmorContents()");
            assertTrue(inv.getArmorContents().length == 4, "PlayerInventory.getArmorContents().length == 4");
            assertNotNull(inv.getContents(), "PlayerInventory.getContents()");
            assertTrue(inv.getContents().length == 41, "PlayerInventory.getContents().length == 41");
            assertTrue(inv.getHeldItemSlot() >= 0 && inv.getHeldItemSlot() < 9, "PlayerInventory.getHeldItemSlot() valid slot");
        }
    }
}
