package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.WeatherType;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import static org.patchbukkit.testplugin.TestAssertions.*;

/**
 * End-to-end tests requiring a live TCP-connected Pumpkin player (exercised by coverage_integration_test).
 */
public final class PlayerBridgeTests {

    @SuppressWarnings("unused")
    private final JavaPlugin plugin;

    public PlayerBridgeTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private Player requireOnlinePlayer() {
        var players = Bukkit.getOnlinePlayers();
        assertTrue(!players.isEmpty(), "Expected at least one live online Pumpkin player");
        return players.iterator().next();
    }

    @ConformanceTest(name = "Live player is present in world.getPlayers() and has connection info", category = TestCategory.PLAYER_BRIDGE)
    public void testLivePlayerWorldAndConnectionInfo() {
        Player player = requireOnlinePlayer();
        World world = player.getWorld();
        assertNotNull(world, "player.getWorld()");
        assertTrue(world.getPlayers().stream().anyMatch(p -> p.getUniqueId().equals(player.getUniqueId())),
            "world.getPlayers() must include live online player");
        assertNotNull(player.getAddress(), "player.getAddress() must be non-null");
        assertNotNull(player.locale(), "player.locale() must be non-null");
    }

    @ConformanceTest(name = "Live player EnderChest contents sync without opening a GUI", category = TestCategory.PLAYER_BRIDGE)
    public void testLivePlayerEnderChestSync() {
        Player player = requireOnlinePlayer();
        var ec = player.getEnderChest();
        assertNotNull(ec, "player.getEnderChest()");
        // Must NOT have forced an ender chest GUI open
        assertEquals(InventoryType.CRAFTING, player.getOpenInventory().getTopInventory().getType(),
            "getEnderChest() must not open a chest GUI");

        ec.setItem(0, new ItemStack(Material.DIAMOND, 5));
        ItemStack readBack = player.getEnderChest().getItem(0);
        assertNotNull(readBack, "EnderChest slot 0 after setItem");
        assertEquals(Material.DIAMOND, readBack.getType(), "EnderChest slot 0 material");
        assertEquals(5, readBack.getAmount(), "EnderChest slot 0 amount");
    }

    @ConformanceTest(name = "Live player inventory, equipment, food, and cooldowns sync with Pumpkin", category = TestCategory.PLAYER_BRIDGE)
    public void testLivePlayerInventoryFoodAndCooldowns() {
        Player player = requireOnlinePlayer();

        // Inventory & equipment
        player.getInventory().setItem(2, new ItemStack(Material.EMERALD, 3));
        player.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        ItemStack slot2 = player.getInventory().getItem(2);
        assertNotNull(slot2, "Inventory slot 2");
        assertEquals(Material.EMERALD, slot2.getType(), "Inventory slot 2 material");
        assertEquals(3, slot2.getAmount(), "Inventory slot 2 amount");
        ItemStack helmet = player.getInventory().getHelmet();
        assertNotNull(helmet, "Player helmet");
        assertEquals(Material.IRON_HELMET, helmet.getType(), "Player helmet material");

        // Food / saturation / exhaustion
        player.setFoodLevel(14);
        player.setSaturation(3.5f);
        player.setExhaustion(1.5f);
        assertEquals(14, player.getFoodLevel(), "Food level persisted in Pumpkin");
        assertEquals(3.5, player.getSaturation(), "Saturation persisted in Pumpkin");

        // Cooldowns (Material overload)
        player.setCooldown(Material.ENDER_PEARL, 60);
        assertTrue(player.hasCooldown(Material.ENDER_PEARL), "hasCooldown(ENDER_PEARL) true");
        int cd = player.getCooldown(Material.ENDER_PEARL);
        assertTrue(cd > 0 && cd <= 60, "getCooldown(ENDER_PEARL) in 1..60, got " + cd);
    }

    @ConformanceTest(name = "Live player time, weather, respawn point, potion effects, health/absorption, bossbar, sounds, and particles", category = TestCategory.PLAYER_BRIDGE)
    public void testLivePlayerTimeWeatherRespawnEffectsAndUi() {
        Player player = requireOnlinePlayer();
        World world = player.getWorld();

        // Player time & weather
        player.setPlayerTime(18000L, false);
        assertFalse(player.isPlayerTimeRelative(), "isPlayerTimeRelative() false");
        assertEquals(18000L, player.getPlayerTimeOffset(), "getPlayerTimeOffset() == 18000");
        player.resetPlayerTime();

        player.setPlayerWeather(WeatherType.DOWNFALL);
        assertEquals(WeatherType.DOWNFALL, player.getPlayerWeather(), "getPlayerWeather() == DOWNFALL");
        player.resetPlayerWeather();
        assertTrue(player.getPlayerWeather() == null, "getPlayerWeather() null after reset");

        // Respawn location set + clear
        Location respawn = new Location(world, 15.0, 72.0, -10.0, 45.0f, 0.0f);
        player.setRespawnLocation(respawn, true);
        Location readRespawn = player.getPotentialRespawnLocation();
        assertNotNull(readRespawn, "getPotentialRespawnLocation() non-null after set");
        assertEquals(15, readRespawn.getBlockX(), "Respawn X");
        assertEquals(72, readRespawn.getBlockY(), "Respawn Y");
        assertEquals(-10, readRespawn.getBlockZ(), "Respawn Z");
        player.setRespawnLocation(null, false);

        // Potion effects
        assertTrue(player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 200, 0)),
            "addPotionEffect(NIGHT_VISION)");
        assertTrue(player.hasPotionEffect(PotionEffectType.NIGHT_VISION), "hasPotionEffect(NIGHT_VISION)");
        player.clearActivePotionEffects();
        assertFalse(player.hasPotionEffect(PotionEffectType.NIGHT_VISION), "clearActivePotionEffects cleared NIGHT_VISION");

        // Health, max health, absorption, damage
        var maxHp = player.getAttribute(Attribute.MAX_HEALTH);
        assertNotNull(maxHp, "Player MAX_HEALTH attribute");
        maxHp.setBaseValue(24.0);
        assertEquals(24.0, maxHp.getBaseValue(), "Player MAX_HEALTH base == 24.0");
        player.setHealth(20.0);
        player.setAbsorptionAmount(4.0);
        player.damage(2.0);
        assertEquals(2.0, player.getAbsorptionAmount(), "Player absorption reduced to 2.0");
        assertEquals(20.0, player.getHealth(), "Player health intact at 20.0");

        // BossBar, health update, particle, sound
        BossBar bar = Bukkit.createBossBar("Coverage Bar", BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bar.addPlayer(player);
        bar.setProgress(0.5);
        bar.setTitle("Updated Coverage Bar");
        bar.removePlayer(player);

        player.sendHealthUpdate();
        player.spawnParticle(Particle.FLAME, player.getLocation(), 3);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    @ConformanceTest(name = "Dynamic WorldCreator createWorld + unloadWorld syncs with Pumpkin", category = TestCategory.PLAYER_BRIDGE)
    public void testDynamicCreateAndUnloadWorld() {
        World extra = Bukkit.createWorld(new WorldCreator("pb_extra_world"));
        assertNotNull(extra, "createWorld('pb_extra_world') must succeed");
        extra.getBlockAt(0, 70, 0).setType(Material.GOLD_BLOCK);
        assertEquals(Material.GOLD_BLOCK, extra.getBlockAt(0, 70, 0).getType(),
            "Block write in dynamically created world persists through Pumpkin");
        assertTrue(Bukkit.unloadWorld(extra, false), "unloadWorld('pb_extra_world') must succeed");
    }
}
