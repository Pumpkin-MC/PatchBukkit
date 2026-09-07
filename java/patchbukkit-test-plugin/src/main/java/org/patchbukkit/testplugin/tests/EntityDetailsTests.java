package org.patchbukkit.testplugin.tests;

import net.kyori.adventure.sound.Sound.Source;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.persistence.PersistentDataType;
import org.patchbukkit.entity.PatchBukkitEntity;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import java.util.List;
import java.util.UUID;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class EntityDetailsTests {

    private static PatchBukkitEntity freshEntity() {
        return new PatchBukkitEntity(UUID.randomUUID(), "DetailTest");
    }

    @ConformanceTest(name = "Entity custom name roundtrip", category = TestCategory.ENTITY)
    public void testCustomName() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getCustomName() == null, "default custom name is null");
        assertTrue(e.customName() == null, "default customName() is null");
        e.setCustomName("Hi");
        assertTrue("Hi".equals(e.getCustomName()), "getCustomName after set");
        e.customName(Component.text("Yo"));
        assertTrue("Yo".equals(e.getCustomName()), "getCustomName mirrors component");
        e.setCustomName(null);
        assertTrue(e.getCustomName() == null && e.customName() == null, "clearing works");
        e.setCustomNameVisible(true);
        assertTrue(e.isCustomNameVisible(), "custom name visible flag");
    }

    @ConformanceTest(name = "Entity persistent data container roundtrip", category = TestCategory.ENTITY)
    public void testPersistentData() {
        PatchBukkitEntity e = freshEntity();
        assertNotNull(e.getPersistentDataContainer(), "getPersistentDataContainer()");
        NamespacedKey key = new NamespacedKey("pbtest", "k");
        e.getPersistentDataContainer().set(key, PersistentDataType.STRING, "v");
        assertTrue("v".equals(e.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "PDC roundtrip");
    }

    @ConformanceTest(name = "Entity dimensions and bounding box", category = TestCategory.ENTITY)
    public void testDimensions() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getHeight() == 1.8, "default height");
        assertTrue(e.getWidth() == 0.6, "default width");
        assertNotNull(e.getBoundingBox(), "getBoundingBox()");
        assertTrue(e.getBoundingBox().getMaxY() == 1.8, "box height matches");
    }

    @ConformanceTest(name = "Entity teleport delegation", category = TestCategory.ENTITY)
    public void testTeleport() {
        PatchBukkitEntity e = freshEntity();
        PatchBukkitEntity target = freshEntity();
        assertTrue(e.teleport(target), "teleport(Entity)");
        assertTrue(e.teleport(target, TeleportCause.PLUGIN), "teleport(Entity, cause)");
    }

    @ConformanceTest(name = "Entity teleportAsync completes", category = TestCategory.ENTITY)
    public void testTeleportAsync() throws Exception {
        PatchBukkitEntity e = freshEntity();
        Location loc = new Location(null, 1, 2, 3);
        assertTrue(e.teleportAsync(loc, TeleportCause.PLUGIN).get(), "teleportAsync result");
    }

    @ConformanceTest(name = "Entity nearby lookup does not throw", category = TestCategory.ENTITY)
    public void testNearby() {
        PatchBukkitEntity e = freshEntity();
        assertNotNull(e.getNearbyEntities(5, 5, 5), "getNearbyEntities()");
    }

    @ConformanceTest(name = "Entity id is stable", category = TestCategory.ENTITY)
    public void testEntityId() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getEntityId() == e.getEntityId(), "getEntityId() stable");
    }

    @ConformanceTest(name = "Entity fire ticks roundtrip", category = TestCategory.ENTITY)
    public void testFireTicks() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getFireTicks() == 0, "default fire ticks");
        assertTrue(e.getMaxFireTicks() == 20, "max fire ticks");
        e.setFireTicks(100);
        assertTrue(e.getFireTicks() == 100, "set fire ticks");
        e.setFireTicks(-5);
        assertTrue(e.getFireTicks() == 0, "fire ticks clamp at zero");
    }

    @ConformanceTest(name = "Entity visual fire roundtrip", category = TestCategory.ENTITY)
    public void testVisualFire() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(!e.isVisualFire(), "default visual fire off");
        e.setVisualFire(true);
        assertTrue(e.isVisualFire(), "visual fire on");
        e.setVisualFire(net.kyori.adventure.util.TriState.FALSE);
        assertTrue(!e.isVisualFire(), "visual fire off via TriState");
    }

    @ConformanceTest(name = "Entity freeze ticks roundtrip and lock", category = TestCategory.ENTITY)
    public void testFreezeTicks() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getFreezeTicks() == 0 && !e.isFrozen(), "default not frozen");
        assertTrue(e.getMaxFreezeTicks() == 140, "max freeze ticks");
        e.setFreezeTicks(10);
        assertTrue(e.getFreezeTicks() == 10 && e.isFrozen(), "frozen after set");
        e.lockFreezeTicks(true);
        assertTrue(e.isFreezeTickingLocked(), "lock flag");
        e.setFreezeTicks(50);
        assertTrue(e.getFreezeTicks() == 10, "locked ticks unchanged");
    }

    @ConformanceTest(name = "Entity boolean flags roundtrip", category = TestCategory.ENTITY)
    public void testFlags() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(!e.isInvisible() && !e.hasNoPhysics() && !e.isGlowing()
            && !e.isInvulnerable() && !e.isSilent() && !e.isPersistent(), "flag defaults");
        assertTrue(e.hasGravity(), "gravity defaults on");
        e.setInvisible(true);
        e.setNoPhysics(true);
        e.setGlowing(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setGravity(false);
        e.setPersistent(true);
        assertTrue(e.isInvisible() && e.hasNoPhysics() && e.isGlowing()
            && e.isInvulnerable() && e.isSilent() && e.isPersistent(), "flags set");
        assertTrue(!e.hasGravity(), "gravity off");
    }

    @ConformanceTest(name = "Entity remove lifecycle", category = TestCategory.ENTITY)
    public void testRemoveLifecycle() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.isValid() && !e.isDead() && e.isTicking() && e.isInWorld(), "alive defaults");
        e.remove();
        assertTrue(e.isDead() && !e.isValid() && !e.isTicking() && !e.isInWorld(), "after remove()");
    }

    @ConformanceTest(name = "Entity vehicle subsystem is coherently empty", category = TestCategory.ENTITY)
    public void testVehicles() {
        PatchBukkitEntity e = freshEntity();
        PatchBukkitEntity other = freshEntity();
        assertTrue(e.getPassengers().isEmpty(), "no passengers");
        assertTrue(e.getPassenger() == null, "getPassenger null");
        assertTrue(e.getVehicle() == null, "getVehicle null");
        assertTrue(e.isEmpty() && !e.isInsideVehicle(), "empty, not in vehicle");
        assertTrue(!e.setPassenger(other) && !e.addPassenger(other)
            && !e.removePassenger(other) && !e.eject() && !e.leaveVehicle(), "vehicle mutations refuse");
    }

    @ConformanceTest(name = "Entity pick item stack", category = TestCategory.ENTITY)
    public void testPickItemStack() {
        PatchBukkitEntity e = freshEntity();
        assertNotNull(e.getPickItemStack(), "getPickItemStack()");
    }

    @ConformanceTest(name = "Entity damage cause defaults null", category = TestCategory.ENTITY)
    public void testDamageCause() {
        // NOTE: constructing a real EntityDamageEvent needs registry-backed
        // damage classes that hit another registry gap (same disease as menus
        // had). Only the storage default is asserted here until that is fixed.
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getLastDamageCause() == null, "default damage cause null");
    }

    @ConformanceTest(name = "Entity ticks lived roundtrip", category = TestCategory.ENTITY)
    public void testTicksLived() {
        PatchBukkitEntity e = freshEntity();
        e.setTicksLived(42);
        assertTrue(e.getTicksLived() == 42, "ticks lived roundtrip");
    }

    @ConformanceTest(name = "Entity playEffect is a no-op", category = TestCategory.ENTITY)
    public void testPlayEffect() {
        freshEntity().playEffect(org.bukkit.EntityEffect.HIT);
    }

    @ConformanceTest(name = "Entity swim sounds", category = TestCategory.ENTITY)
    public void testSwimSounds() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getSwimSound() == Sound.ENTITY_GENERIC_SWIM, "swim sound");
        assertTrue(e.getSwimSplashSound() == Sound.ENTITY_GENERIC_SPLASH, "splash sound");
        assertTrue(e.getSwimHighSpeedSplashSound() == Sound.ENTITY_GENERIC_SPLASH, "high speed splash");
    }

    @ConformanceTest(name = "Entity scoreboard tags roundtrip", category = TestCategory.ENTITY)
    public void testScoreboardTags() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.addScoreboardTag("a"), "add tag");
        assertTrue(!e.addScoreboardTag("a"), "duplicate add refused");
        assertTrue(e.getScoreboardTags().contains("a"), "tag present");
        assertTrue(e.removeScoreboardTag("a"), "remove tag");
        assertTrue(!e.removeScoreboardTag("a"), "duplicate remove refused");
        assertTrue(e.getScoreboardEntryName().equals(e.getUniqueId().toString()), "scoreboard entry name");
    }

    @ConformanceTest(name = "Entity piston reaction and facing", category = TestCategory.ENTITY)
    public void testPistonAndFacing() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getPistonMoveReaction() == org.bukkit.block.PistonMoveReaction.MOVE, "piston reaction");
        assertTrue(e.getFacing() == BlockFace.SOUTH, "facing at yaw zero");
    }

    @ConformanceTest(name = "Entity pose defaults and fixed flag", category = TestCategory.ENTITY)
    public void testPose() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getPose() == org.bukkit.entity.Pose.STANDING, "default pose");
        assertTrue(!e.hasFixedPose(), "pose not fixed by default");
        e.setPose(org.bukkit.entity.Pose.SNEAKING, true);
        assertTrue(e.hasFixedPose(), "pose fixed after set");
    }

    @ConformanceTest(name = "Entity spawn category and reason", category = TestCategory.ENTITY)
    public void testSpawnInfo() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getSpawnCategory() == org.bukkit.entity.SpawnCategory.MISC, "default category");
        assertTrue(e.getEntitySpawnReason() == org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL, "spawn reason");
        assertTrue(!e.fromMobSpawner(), "not from spawner");
        assertTrue(!e.spawnAt(new Location(null, 0, 64, 0),
            org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.NATURAL), "spawnAt refuses honestly");
    }

    @ConformanceTest(name = "Entity coordinates delegate to location", category = TestCategory.ENTITY)
    public void testCoords() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getX() == 0.0 && e.getY() == 0.0 && e.getZ() == 0.0, "origin coords");
        assertTrue(e.getPitch() == 0.0f && e.getYaw() == 0.0f, "origin rotation");
        assertNotNull(e.getOrigin(), "getOrigin()");
        assertTrue(e.getAsString() != null && e.getAsString().contains(e.getUniqueId().toString()), "getAsString()");
    }

    @ConformanceTest(name = "Entity team display name and spigot", category = TestCategory.ENTITY)
    public void testDisplayAndSpigot() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.teamDisplayName().equals(Component.text("DetailTest")), "team name falls back");
        assertNotNull(e.spigot(), "spigot()");
    }

    @ConformanceTest(name = "Entity sound source and category", category = TestCategory.ENTITY)
    public void testSoundSource() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.soundSource() == Source.NEUTRAL, "sound source");
        assertTrue(e.getSoundCategory() == SoundCategory.NEUTRAL, "sound category");
    }

    @ConformanceTest(name = "Entity portal cooldown roundtrip", category = TestCategory.ENTITY)
    public void testPortalCooldown() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getPortalCooldown() == 0, "default cooldown");
        e.setPortalCooldown(7);
        assertTrue(e.getPortalCooldown() == 7, "cooldown roundtrip");
    }

    @ConformanceTest(name = "Entity environment flags default false", category = TestCategory.ENTITY)
    public void testEnvironmentFlags() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(!e.isUnderWater() && !e.isInRain() && !e.isInLava() && !e.isInPowderedSnow(), "environment defaults");
    }

    @ConformanceTest(name = "Entity tracking sets are empty", category = TestCategory.ENTITY)
    public void testTracking() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getTrackedBy().isEmpty(), "getTrackedBy empty");
        assertTrue(e.getTrackedPlayers().isEmpty(), "getTrackedPlayers empty");
    }

    @ConformanceTest(name = "Entity type reports unknown", category = TestCategory.ENTITY)
    public void testType() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(e.getType() == EntityType.UNKNOWN, "synthetic type");
    }

    @ConformanceTest(name = "Online players iteration still works", category = TestCategory.ENTITY)
    public void testOnlinePlayersStillWork() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            assertNotNull(p.getName(), "online player name");
        }
    }

    @ConformanceTest(name = "Entity nearby via world does not throw", category = TestCategory.ENTITY)
    public void testNearbyViaWorld() {
        PatchBukkitEntity e = freshEntity();
        assertTrue(!Bukkit.getWorlds().isEmpty(), "worlds loaded");
        java.util.Collection<Entity> nearby = Bukkit.getWorlds().get(0).getNearbyEntities(e.getLocation(), 4, 4, 4);
        assertNotNull(nearby, "world nearby lookup");
    }
}
