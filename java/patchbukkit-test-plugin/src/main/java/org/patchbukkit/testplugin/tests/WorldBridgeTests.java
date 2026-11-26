package org.patchbukkit.testplugin.tests;

import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import java.util.Date;
import java.util.UUID;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class WorldBridgeTests {

    private final JavaPlugin plugin;

    public WorldBridgeTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "World block read/write round-trips through Pumpkin", category = TestCategory.WORLD)
    public void testWorldBlockRoundTrip() {
        World world = Bukkit.getWorlds().get(0);
        Block block = world.getBlockAt(8, 80, 8);
        Material original = block.getType();
        try {
            block.setType(Material.DIAMOND_BLOCK);
            assertEquals(Material.DIAMOND_BLOCK, world.getBlockAt(8, 80, 8).getType(), "Diamond block persisted in Pumpkin");
            block.setType(Material.OAK_PLANKS);
            assertEquals(Material.OAK_PLANKS, world.getBlockAt(8, 80, 8).getType(), "Oak planks persisted in Pumpkin");
        } finally {
            block.setType(original);
        }
    }

    @ConformanceTest(name = "World gamerules (typed GameRule and legacy string) round-trip via Pumpkin", category = TestCategory.WORLD)
    @SuppressWarnings("deprecation")
    public void testWorldGamerules() {
        World world = Bukkit.getWorlds().get(0);
        Boolean origKeepInv = world.getGameRuleValue(GameRule.KEEP_INVENTORY);
        try {
            world.setGameRule(GameRule.KEEP_INVENTORY, true);
            assertEquals(Boolean.TRUE, world.getGameRuleValue(GameRule.KEEP_INVENTORY), "Typed KEEP_INVENTORY == true");
            assertEquals("true", world.getGameRuleValue("keepInventory"), "Legacy string keepInventory == true");

            world.setGameRuleValue("keepInventory", "false");
            assertEquals(Boolean.FALSE, world.getGameRuleValue(GameRule.KEEP_INVENTORY), "Typed KEEP_INVENTORY == false");

            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 7);
            assertEquals(Integer.valueOf(7), world.getGameRuleValue(GameRule.RANDOM_TICK_SPEED), "Typed RANDOM_TICK_SPEED == 7");
        } finally {
            if (origKeepInv != null) {
                world.setGameRule(GameRule.KEEP_INVENTORY, origKeepInv);
            }
            world.setGameRule(GameRule.RANDOM_TICK_SPEED, 3);
        }
    }

    @ConformanceTest(name = "World PVP, difficulty, time, weather, spawn, and border sync with Pumpkin", category = TestCategory.WORLD)
    public void testWorldMetadataSync() {
        World world = Bukkit.getWorlds().get(0);

        boolean origPvp = world.getPVP();
        world.setPVP(false);
        assertFalse(world.getPVP(), "World PVP should be false after setPVP(false)");
        world.setPVP(true);
        assertTrue(world.getPVP(), "World PVP should be true after setPVP(true)");
        world.setPVP(origPvp);

        Difficulty origDiff = world.getDifficulty();
        world.setDifficulty(Difficulty.HARD);
        assertEquals(Difficulty.HARD, world.getDifficulty(), "Difficulty should be HARD");
        world.setDifficulty(origDiff);

        world.setTime(6000L);
        long t = world.getTime();
        assertTrue(t >= 5900L && t <= 6200L, "World time should be near 6000, got " + t);

        world.setStorm(true);
        assertTrue(world.hasStorm(), "World should have storm after setStorm(true)");
        world.setStorm(false);
        assertFalse(world.hasStorm(), "World should not have storm after setStorm(false)");

        Location newSpawn = new Location(world, 12, 70, -8, 90f, 0f);
        assertTrue(world.setSpawnLocation(newSpawn), "setSpawnLocation(Location) returns true");
        Location readSpawn = world.getSpawnLocation();
        assertEquals(12, readSpawn.getBlockX(), "Spawn X");
        assertEquals(70, readSpawn.getBlockY(), "Spawn Y");
        assertEquals(-8, readSpawn.getBlockZ(), "Spawn Z");

        var border = world.getWorldBorder();
        border.setCenter(10.0, -20.0);
        border.setSize(500.0);
        border.setWarningDistance(15);
        border.setDamageBuffer(3.0);
        assertEquals(10.0, border.getCenter().getX(), "Border center X");
        assertEquals(-20.0, border.getCenter().getZ(), "Border center Z");
        assertEquals(500.0, border.getSize(), "Border size");
        assertEquals(15, border.getWarningDistance(), "Border warning distance");
    }

    @ConformanceTest(name = "World biome, heightmap, light level, seed, and lightning work via Pumpkin", category = TestCategory.WORLD)
    public void testWorldQueriesAndLightning() {
        World world = Bukkit.getWorlds().get(0);
        world.loadChunk(0, 0, true);

        Biome biome = world.getBiome(0, 64, 0);
        assertNotNull(biome, "getBiome(0, 64, 0) must not be null");

        int highest = world.getHighestBlockYAt(0, 0, HeightMap.WORLD_SURFACE);
        assertTrue(highest >= world.getMinHeight() && highest <= world.getMaxHeight(),
            "Highest block Y in valid range, got " + highest);

        Block block = world.getBlockAt(0, Math.min(world.getMaxHeight() - 1, Math.max(world.getMinHeight(), highest + 1)), 0);
        byte light = block.getLightLevel();
        byte sky = block.getLightFromSky();
        byte blk = block.getLightFromBlocks();
        assertTrue(light >= 0 && light <= 15, "light level 0..15");
        assertTrue(sky >= 0 && sky <= 15, "sky light 0..15");
        assertTrue(blk >= 0 && blk <= 15, "block light 0..15");

        // Seed query runs cleanly
        long seed = world.getSeed();
        assertTrue(seed == world.getSeed(), "World seed is deterministic");

        // Lightning strike effect does not throw
        var strike = world.strikeLightningEffect(new Location(world, 0.5, 80.0, 0.5));
        assertNotNull(strike, "strikeLightningEffect returns LightningStrike");
    }

    @ConformanceTest(name = "BlockEntity NBT (Sign text) persists through Pumpkin", category = TestCategory.WORLD)
    public void testBlockEntitySignNbtRoundTrip() {
        World world = Bukkit.getWorlds().get(0);
        Block block = world.getBlockAt(10, 82, 10);
        Material original = block.getType();
        try {
            block.setType(Material.OAK_SIGN);
            var state = block.getState();
            assertTrue(state instanceof Sign, "OAK_SIGN BlockState must be a Sign, got " + state.getClass().getName());
            Sign sign = (Sign) state;
            sign.getSide(Side.FRONT).line(0, Component.text("PatchBukkit"));
            sign.getSide(Side.FRONT).line(1, Component.text("Coverage OK"));
            assertTrue(sign.update(true, false), "Sign.update() succeeds");

            var reread = world.getBlockAt(10, 82, 10).getState();
            assertTrue(reread instanceof Sign, "Re-read BlockState must be a Sign");
            Sign rereadSign = (Sign) reread;
            String line0 = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(rereadSign.getSide(Side.FRONT).line(0));
            assertEquals("PatchBukkit", line0, "Sign line 0 must persist through Pumpkin NBT");
        } finally {
            block.setType(original);
        }
    }

    @ConformanceTest(name = "LivingEntity health, absorption, damage, potion effects, attributes, metadata, equipment, and passengers sync with Pumpkin", category = TestCategory.ENTITY)
    public void testLivingEntityFullBridge() {
        World world = Bukkit.getWorlds().get(0);
        Location loc = new Location(world, 4.5, 75.0, 4.5);
        Zombie zombie = (Zombie) world.spawnEntity(loc, EntityType.ZOMBIE);
        Cow cow = (Cow) world.spawnEntity(new Location(world, 6.5, 75.0, 4.5), EntityType.COW);
        try {
            // 1. Health, max health, absorption, and absorption-first damage
            AttributeInstance maxHp = zombie.getAttribute(Attribute.MAX_HEALTH);
            assertNotNull(maxHp, "Zombie MAX_HEALTH attribute must exist");
            maxHp.setBaseValue(30.0);
            assertEquals(30.0, maxHp.getBaseValue(), "MAX_HEALTH base value persisted in Pumpkin");

            NamespacedKey modKey = new NamespacedKey(plugin, "test_hp_bonus");
            AttributeModifier bonus = new AttributeModifier(modKey, 4.0, AttributeModifier.Operation.ADD_NUMBER);
            maxHp.addModifier(bonus);
            assertTrue(maxHp.getModifiers().stream().anyMatch(m -> modKey.equals(m.getKey())),
                "Attribute modifier persisted in Pumpkin");
            maxHp.removeModifier(modKey);
            assertTrue(maxHp.getModifiers().stream().noneMatch(m -> modKey.equals(m.getKey())),
                "Attribute modifier removed in Pumpkin");

            zombie.setHealth(20.0);
            assertEquals(20.0, zombie.getHealth(), "Zombie health set to 20.0");
            zombie.setAbsorptionAmount(6.0);
            assertEquals(6.0, zombie.getAbsorptionAmount(), "Zombie absorption set to 6.0");

            zombie.damage(4.0);
            assertEquals(2.0, zombie.getAbsorptionAmount(), "Damage absorbs from absorption first (6 - 4 = 2)");
            assertEquals(20.0, zombie.getHealth(), "Health untouched while absorption covers full damage");

            zombie.damage(5.0);
            assertEquals(0.0, zombie.getAbsorptionAmount(), "Remaining absorption depleted to 0");
            assertEquals(17.0, zombie.getHealth(), "Overflow damage (3) subtracted from health (20 - 3 = 17)");

            // 2. Potion effects
            assertTrue(zombie.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 200, 1)),
                "addPotionEffect(SPEED) returns true");
            assertTrue(zombie.hasPotionEffect(PotionEffectType.SPEED), "hasPotionEffect(SPEED) after add");
            PotionEffect active = zombie.getPotionEffect(PotionEffectType.SPEED);
            assertNotNull(active, "getPotionEffect(SPEED) non-null");
            assertEquals(1, active.getAmplifier(), "SPEED amplifier == 1");
            zombie.removePotionEffect(PotionEffectType.SPEED);
            assertFalse(zombie.hasPotionEffect(PotionEffectType.SPEED), "SPEED removed in Pumpkin");

            // 3. Entity metadata & scoreboard tags
            zombie.customName(Component.text("BridgeZombie"));
            zombie.setCustomNameVisible(true);
            zombie.setFireTicks(60);
            zombie.setGlowing(true);
            zombie.setSilent(true);
            zombie.setGravity(false);
            zombie.setInvulnerable(true);
            zombie.addScoreboardTag("pb_coverage_tag");

            assertTrue(zombie.isCustomNameVisible(), "customNameVisible == true");
            assertTrue(zombie.getFireTicks() > 0, "fireTicks > 0");
            assertTrue(zombie.isGlowing(), "glowing == true");
            assertTrue(zombie.isSilent(), "silent == true");
            assertFalse(zombie.hasGravity(), "hasGravity == false");
            assertTrue(zombie.isInvulnerable(), "invulnerable == true");
            assertTrue(zombie.getScoreboardTags().contains("pb_coverage_tag"), "scoreboard tag present");

            zombie.removeScoreboardTag("pb_coverage_tag");
            assertFalse(zombie.getScoreboardTags().contains("pb_coverage_tag"), "scoreboard tag removed");

            // 4. Non-player LivingEntity equipment
            var eq = zombie.getEquipment();
            assertNotNull(eq, "Zombie equipment non-null");
            eq.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            ItemStack helmet = eq.getHelmet();
            assertNotNull(helmet, "Zombie helmet non-null");
            assertEquals(Material.DIAMOND_HELMET, helmet.getType(), "Zombie helmet synced via Pumpkin");

            // 5. Passengers & vehicles
            assertTrue(cow.addPassenger(zombie), "cow.addPassenger(zombie)");
            assertTrue(cow.getPassengers().stream().anyMatch(e -> e.getUniqueId().equals(zombie.getUniqueId())),
                "cow.getPassengers() includes zombie");
            Entity veh = zombie.getVehicle();
            assertNotNull(veh, "zombie.getVehicle() non-null");
            assertEquals(cow.getUniqueId(), veh.getUniqueId(), "zombie.getVehicle() is cow");
            cow.eject();
            assertTrue(cow.getPassengers().isEmpty(), "cow.getPassengers() empty after eject()");

            // 6. Hand swing executes cleanly
            zombie.swingMainHand();
            zombie.swingOffHand();
        } finally {
            zombie.remove();
            cow.remove();
        }
    }

    @ConformanceTest(name = "Server bans (profile + IP), operators, and whitelist round-trip through Pumpkin", category = TestCategory.SERVER)
    @SuppressWarnings("deprecation")
    public void testServerAdminListsBridge() {
        // 1. Profile ban list
        var nameBans = Bukkit.getServer().getBanList(BanList.Type.NAME);
        String bannedUser = "PbBannedTester";
        nameBans.addBan(bannedUser, "Conformance ban test", (Date) null, "TestSuite");
        assertTrue(nameBans.isBanned(bannedUser), "Profile ban added in Pumpkin");
        nameBans.pardon(bannedUser);
        assertFalse(nameBans.isBanned(bannedUser), "Profile ban pardoned in Pumpkin");

        // 2. IP ban list
        var ipBans = Bukkit.getServer().getBanList(BanList.Type.IP);
        String testIp = "198.51.100.42";
        ipBans.addBan(testIp, "Conformance IP ban", (Date) null, "TestSuite");
        assertTrue(ipBans.isBanned(testIp), "IP ban added in Pumpkin");
        assertTrue(Bukkit.getIPBans().contains(testIp), "Server.getIPBans() contains banned IP");
        ipBans.pardon(testIp);
        assertFalse(ipBans.isBanned(testIp), "IP ban pardoned in Pumpkin");
        assertFalse(Bukkit.getIPBans().contains(testIp), "Server.getIPBans() no longer contains pardoned IP");

        // 3. OfflinePlayer operator & whitelist sync
        UUID testUuid = UUID.randomUUID();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(testUuid);
        offline.setOp(true);
        assertTrue(offline.isOp(), "OfflinePlayer.isOp() true in Pumpkin");
        assertTrue(Bukkit.getOperators().stream().anyMatch(p -> testUuid.equals(p.getUniqueId())),
            "Server.getOperators() contains op");
        offline.setOp(false);
        assertFalse(offline.isOp(), "OfflinePlayer.isOp() false after deop");
        assertFalse(Bukkit.getOperators().stream().anyMatch(p -> testUuid.equals(p.getUniqueId())),
            "Server.getOperators() removes deopped player");

        offline.setWhitelisted(true);
        assertTrue(offline.isWhitelisted(), "OfflinePlayer.isWhitelisted() true in Pumpkin");
        assertTrue(Bukkit.getWhitelistedPlayers().stream().anyMatch(p -> testUuid.equals(p.getUniqueId())),
            "Server.getWhitelistedPlayers() contains whitelisted player");
        offline.setWhitelisted(false);
        assertFalse(offline.isWhitelisted(), "OfflinePlayer.isWhitelisted() false after removal");
        assertFalse(Bukkit.getWhitelistedPlayers().stream().anyMatch(p -> testUuid.equals(p.getUniqueId())),
            "Server.getWhitelistedPlayers() removes unwhitelisted player");
    }
}
