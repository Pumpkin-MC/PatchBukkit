package org.patchbukkit.testplugin.tests;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.advancement.Advancement;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.patchbukkit.entity.PatchBukkitEntity;
import org.patchbukkit.entity.PatchBukkitHumanEntity;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class ExtendedCoverageTests {

    private static World firstWorld() {
        List<World> worlds = Bukkit.getWorlds();
        return worlds.isEmpty() ? null : worlds.get(0);
    }

    private static PatchBukkitEntity freshEntity() {
        return new PatchBukkitEntity(UUID.randomUUID(), "CoverageTest");
    }

    @ConformanceTest(name = "Server.setMotd()/getMotd() roundtrip", category = TestCategory.STUBS)
    public void testMotdRoundtrip() {
        String before = Bukkit.getMotd();
        Bukkit.setMotd("pbtest-motd");
        assertTrue("pbtest-motd".equals(Bukkit.getMotd()), "motd roundtrip");
        Bukkit.setMotd(before);
    }

    @ConformanceTest(name = "Server.setMaxPlayers()/getMaxPlayers() roundtrip", category = TestCategory.STUBS)
    public void testMaxPlayersRoundtrip() {
        int before = Bukkit.getMaxPlayers();
        Bukkit.setMaxPlayers(before + 1);
        assertTrue(Bukkit.getMaxPlayers() == before + 1, "max players roundtrip");
        Bukkit.setMaxPlayers(before);
    }

    @ConformanceTest(name = "Server.setWhitelistEnforced()/isWhitelistEnforced() roundtrip", category = TestCategory.STUBS)
    public void testWhitelistEnforcedRoundtrip() {
        boolean before = Bukkit.isWhitelistEnforced();
        Bukkit.setWhitelistEnforced(!before);
        assertTrue(Bukkit.isWhitelistEnforced() == !before, "whitelist enforced roundtrip");
        Bukkit.setWhitelistEnforced(before);
    }

    @ConformanceTest(name = "World.setGameRuleValue()/getGameRuleValue() roundtrip", category = TestCategory.STUBS)
    public void testGameruleRoundtrip() {
        World world = firstWorld();
        if (world == null) {
            return;
        }
        String before = world.getGameRuleValue("mobGriefing");
        assertTrue(world.setGameRuleValue("mobGriefing", "false"), "setGameRuleValue returns true");
        assertTrue("false".equals(world.getGameRuleValue("mobGriefing")), "gamerule roundtrip");
        assertTrue(world.setGameRuleValue("mobGriefing", before != null ? before : "true"), "restore gamerule");
    }

    @ConformanceTest(name = "BanList.addBan()/pardon() roundtrip", category = TestCategory.STUBS)
    public void testBanRoundtrip() {
        String target = "pbtest-probe-" + UUID.randomUUID().toString().substring(0, 8);
        var entry = Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(target, "probe", (java.util.Date) null, "pbtest");
        assertNotNull(entry, "addBan() returns entry");
        assertTrue(Bukkit.getBannedPlayers().stream().anyMatch(p -> target.equals(p.getName())), "banned player listed");
        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(target);
        assertTrue(Bukkit.getBannedPlayers().stream().noneMatch(p -> target.equals(p.getName())), "pardon removes ban");
    }

    @ConformanceTest(name = "Player.ban() writes the ban list", category = TestCategory.ENTITY)
    public void testPlayerBanWritesList() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var entry = player.ban("probe", (java.util.Date) null, "pbtest", false);
            assertNotNull(entry, "ban() returns entry");
            assertTrue(player.isBanned(), "player is banned");
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(player.getName());
            assertTrue(!player.isBanned(), "pardon clears ban");
        }
    }

    @ConformanceTest(name = "Entity.copy() clones local state", category = TestCategory.ENTITY)
    public void testEntityCopy() {
        PatchBukkitEntity original = freshEntity();
        original.setCustomName("CopyMe");
        original.setFireTicks(7);
        original.addScoreboardTag("copy-tag");
        Entity copy = original.copy();
        assertTrue(copy != original, "copy is a new instance");
        assertTrue(!copy.getUniqueId().equals(original.getUniqueId()), "copy has a new uuid");
        assertTrue("CopyMe".equals(copy.getCustomName()), "copy keeps custom name");
        assertTrue(copy.getFireTicks() == 7, "copy keeps fire ticks");
        assertTrue(copy.getScoreboardTags().contains("copy-tag"), "copy keeps scoreboard tags");
        Entity moved = original.copy(new Location(firstWorld(), 1, 2, 3));
        assertTrue(moved.getLocation().getBlockX() == 1, "copy(Location) moves");
    }

    @ConformanceTest(name = "Entity.createSnapshot() roundtrip", category = TestCategory.ENTITY)
    public void testEntitySnapshot() {
        PatchBukkitEntity original = freshEntity();
        original.setCustomName("SnapMe");
        var snapshot = original.createSnapshot();
        assertNotNull(snapshot, "createSnapshot() returns snapshot");
        assertTrue(snapshot.getEntityType() == original.getType(), "snapshot keeps type");
        assertTrue(snapshot.getAsString() != null, "snapshot string form");
    }

    @ConformanceTest(name = "Entity.collidesAt()/wouldCollideUsing() run cleanly", category = TestCategory.ENTITY)
    public void testCollides() {
        World world = firstWorld();
        if (world == null) {
            return;
        }
        PatchBukkitEntity entity = freshEntity();
        assertTrue(!entity.collidesAt(new Location(world, 0, 1000, 0)), "void does not collide");
        assertTrue(!entity.wouldCollideUsing(
            new org.bukkit.util.BoundingBox(0, 1000, 0, 1, 1001, 1)), "void box does not collide");
    }

    @ConformanceTest(name = "Entity.getScheduler() returns non-null", category = TestCategory.ENTITY)
    public void testEntityScheduler() {
        assertNotNull(freshEntity().getScheduler(), "getScheduler()");
    }

    @ConformanceTest(name = "Entity.setPose() runs cleanly", category = TestCategory.ENTITY)
    public void testSetPose() {
        PatchBukkitEntity entity = freshEntity();
        entity.setPose(Pose.SWIMMING, true);
        entity.setPose(Pose.STANDING, false);
    }

    @ConformanceTest(name = "World.spawnParticle()/playEffect() run cleanly", category = TestCategory.STUBS)
    public void testParticlesAndEffects() {
        World world = firstWorld();
        if (world == null) {
            return;
        }
        Location loc = new Location(world, 0, 100, 0);
        world.spawnParticle(Particle.FLAME, loc, 1);
        world.spawnParticle(Particle.SMOKE, 0, 100, 0, 2, 0.5, 0.5, 0.5, 0.1);
        world.playEffect(loc, Effect.SMOKE, 0);
        world.playEffect(loc, Effect.CLICK2, 0);
    }

    @ConformanceTest(name = "HumanEntity.openInventory() generic + close", category = TestCategory.ENTITY)
    public void testOpenInventory() {
        PatchBukkitHumanEntity human = new PatchBukkitHumanEntity(UUID.randomUUID(), "InvTest");
        Inventory chest = Bukkit.createInventory(null, InventoryType.CHEST);
        InventoryView view = human.openInventory(chest);
        assertNotNull(view, "openInventory() returns view");
        assertTrue(chest.getViewers().contains(human), "viewer tracked");
        human.closeInventory();
        assertTrue(human.getOpenInventory() == null, "closeInventory() clears view");
        assertTrue(!chest.getViewers().contains(human), "viewer untracked");
    }

    @ConformanceTest(name = "HumanEntity.openInventory() rejects merchant", category = TestCategory.ENTITY)
    public void testOpenMerchantRejected() {
        PatchBukkitHumanEntity human = new PatchBukkitHumanEntity(UUID.randomUUID(), "InvTest");
        Inventory merchant = Bukkit.createInventory(null, InventoryType.MERCHANT);
        boolean rejected = false;
        try {
            human.openInventory(merchant);
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        assertTrue(rejected, "merchant open rejected loudly");
    }

    @ConformanceTest(name = "ItemMeta enchants/flags roundtrip", category = TestCategory.ENTITY)
    public void testItemMetaEnchants() {
        ItemMeta meta = Bukkit.getItemFactory().getItemMeta(Material.DIAMOND_SWORD);
        assertNotNull(meta, "getItemMeta()");
        Enchantment sharpness = Enchantment.getByKey(NamespacedKey.minecraft("sharpness"));
        if (sharpness == null) {
            return;
        }
        assertTrue(meta.addEnchant(sharpness, 3, false), "addEnchant valid level");
        assertTrue(!meta.addEnchant(sharpness, 99, false), "addEnchant rejects bad level");
        assertTrue(meta.addEnchant(sharpness, 99, true), "addEnchant ignores bad level when allowed");
        assertTrue(meta.getEnchantLevel(sharpness) == 99, "enchant level roundtrip");
        assertTrue(meta.hasEnchant(sharpness), "hasEnchant()");
        assertTrue(meta.hasEnchants(), "hasEnchants()");
        Enchantment smite = Enchantment.getByKey(NamespacedKey.minecraft("smite"));
        if (smite != null) {
            assertTrue(meta.hasConflictingEnchant(smite), "sharpness conflicts with smite");
        }
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        assertTrue(meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS), "flag roundtrip");
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        assertTrue(!meta.hasItemFlag(ItemFlag.HIDE_ENCHANTS), "flag removal");
        meta.setUnbreakable(true);
        assertTrue(meta.isUnbreakable(), "unbreakable roundtrip");
        meta.setCustomModelData(7);
        assertTrue(meta.hasCustomModelData() && meta.getCustomModelData() == 7, "custom model data roundtrip");
        ItemMeta copy = meta.clone();
        assertTrue(copy.getEnchantLevel(sharpness) == 99, "clone keeps enchants");
        copy.removeEnchant(sharpness);
        assertTrue(copy.getEnchantLevel(sharpness) == 0, "clone removal works");
        assertTrue(meta.getEnchantLevel(sharpness) == 99, "clone is independent");
        assertTrue(meta.equals(meta), "meta equals itself");
        assertTrue(!meta.equals(null), "meta does not equal null");
    }

    @ConformanceTest(name = "ItemMeta names/lore/pdc roundtrip", category = TestCategory.ENTITY)
    public void testItemMetaText() {
        ItemMeta meta = Bukkit.getItemFactory().getItemMeta(Material.STONE);
        assertNotNull(meta, "getItemMeta()");
        meta.setDisplayName("Named");
        assertTrue(meta.hasDisplayName() && "Named".equals(meta.getDisplayName()), "display name roundtrip");
        meta.setLore(List.of("one", "two"));
        assertTrue(meta.hasLore() && meta.getLore().size() == 2, "lore roundtrip");
        meta.setLocalizedName("localized");
        assertTrue(meta.hasLocalizedName(), "localized name roundtrip");
        NamespacedKey key = new NamespacedKey("pbtest", "k");
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "v");
        assertTrue("v".equals(meta.getPersistentDataContainer().get(key, PersistentDataType.STRING)), "meta pdc roundtrip");
        assertNotNull(meta.serialize().get("meta-type"), "serialize() has meta-type");
    }

    @ConformanceTest(name = "Advancement registry resolves story/root", category = TestCategory.STUBS)
    public void testAdvancementRegistry() {
        Advancement root = Bukkit.getAdvancement(NamespacedKey.minecraft("story/root"));
        assertNotNull(root, "story/root resolves");
        assertTrue(!root.getCriteria().isEmpty(), "root has criteria");
        assertTrue(!root.getRequirements().getRequirements().isEmpty(), "root has requirement groups");
        assertTrue(Bukkit.getAdvancement(new NamespacedKey("pbtest", "nope")) == null, "unknown advancement is null");
        assertTrue(Bukkit.advancementIterator().hasNext(), "advancement iterator is non-empty");
    }

    @ConformanceTest(name = "Advancement progress reads live state", category = TestCategory.ENTITY)
    public void testAdvancementProgress() {
        Advancement root = Bukkit.getAdvancement(NamespacedKey.minecraft("story/root"));
        if (root == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            var progress = player.getAdvancementProgress(root);
            assertNotNull(progress, "getAdvancementProgress()");
            progress.isDone();
            progress.getAwardedCriteria();
            progress.getRemainingCriteria();
        }
    }

    @ConformanceTest(name = "Permission fallback answers unknown nodes", category = TestCategory.ENTITY)
    public void testPermissionFallback() {
        PatchBukkitHumanEntity human = new PatchBukkitHumanEntity(UUID.randomUUID(), "PermTest");
        assertTrue(!human.hasPermission("pbtest.definitely.not.a.real.permission"), "unknown node denied");
    }

    @ConformanceTest(name = "Player.isBanned() reflects ban list", category = TestCategory.ENTITY)
    public void testPlayerIsBanned() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            assertTrue(!player.isBanned(), "player starts unbanned");
            player.ban("probe", (java.util.Date) null, "pbtest", false);
            assertTrue(player.isBanned(), "player banned after ban()");
            Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(player.getName());
            assertTrue(!player.isBanned(), "player unbanned after pardon");
        }
    }
}
