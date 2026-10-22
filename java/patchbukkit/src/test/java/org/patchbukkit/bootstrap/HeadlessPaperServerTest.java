package org.patchbukkit.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.papermc.paper.adventure.PaperAdventure;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.dedicated.DedicatedServer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.patchbukkit.network.VirtualChannelManager;

/**
 * Phase 1 exit criteria: the real Paper server boots headlessly and the real
 * CraftBukkit / NMS data layer works without a running server.
 *
 * <p>Runs in its own JVM (see build.gradle.kts, forkEvery = 1) because the boot must
 * happen before anything initializes the fallback registries.
 */
public class HeadlessPaperServerTest {

    @TempDir
    static Path root;

    static DedicatedServer server;
    static Object bukkitServerAfterBoot;

    @BeforeAll
    static void boot() throws Exception {
        server = HeadlessPaperServer.boot(root);
        bukkitServerAfterBoot = Bukkit.getServer();
    }

    @Test
    void bootCreatesRealCraftServer() {
        assertNotNull(server);
        assertInstanceOf(CraftServer.class, server.server);
        assertSame(server.server, bukkitServerAfterBoot, "CraftServer registers itself with Bukkit");
        assertSame(server, server.server.getServer());
        assertNotNull(server.getPlayerList());
        assertEquals("26.3-R0.1-SNAPSHOT", Bukkit.getBukkitVersion());
        assertTrue(Bukkit.getVersion().contains("26.3"), "Version string must contain 26.3");
    }

    @Test
    void allPaperFilesStayInsideRoot() {
        for (String file : new String[] {"server.properties", "bukkit.yml", "commands.yml", "spigot.yml", "config"}) {
            assertTrue(Files.exists(root.resolve(file)), file + " should be created under the PatchBukkit root");
        }
    }

    @Test
    void datapackRegistriesAreLoaded() {
        // Enchantments are a datapack (dynamic) registry: only present with a full WorldLoader boot.
        assertTrue(CraftRegistry.getMinecraftRegistry().lookupOrThrow(Registries.ENCHANTMENT).size() > 0);
        assertNotNull(Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sharpness")));
        assertNotNull(Registry.BIOME.get(NamespacedKey.minecraft("plains")));
    }

    @Test
    void itemStacksRoundTripThroughNms() {
        ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
        sword.addEnchantment(Enchantment.SHARPNESS, 3);

        net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(sword);
        assertEquals(net.minecraft.world.item.Items.DIAMOND_SWORD, nms.getItem());
        assertTrue(nms.isEnchanted());

        ItemStack back = CraftItemStack.asBukkitCopy(nms);
        assertEquals(3, back.getEnchantmentLevel(Enchantment.SHARPNESS));
    }

    @Test
    void adventureComponentsConvertToVanilla() {
        assertEquals("hello pumpkin", PaperAdventure.asVanilla(Component.text("hello pumpkin")).getString());
    }

    @Test
    void packetsEncodeWithRealCodecs() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        ClientboundSystemChatPacket packet =
            new ClientboundSystemChatPacket(net.minecraft.network.chat.Component.literal("hi"), false);
        ClientboundSystemChatPacket.STREAM_CODEC.encode(buf, packet);
        assertTrue(buf.readableBytes() > 0);

        ClientboundSystemChatPacket decoded = ClientboundSystemChatPacket.STREAM_CODEC.decode(buf);
        assertEquals("hi", decoded.content().getString());
    }

    @Test
    void craftServerAndRegistriesWork() {
        assertSame(server.server, Bukkit.getServer(), "Bukkit.getServer() should be the real CraftServer");
        assertSame(server, HeadlessPaperServer.get());
        assertNotNull(Bukkit.createBlockData("minecraft:chest[facing=north]"));
        assertNotNull(org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        assertNotNull(org.bukkit.damage.DamageType.ARROW);
    }

    @Test
    void shadowOverworldIsLoaded() {
        assertNotNull(server.getLevel(net.minecraft.world.level.Level.OVERWORLD), "Shadow overworld must exist in MinecraftServer");
        assertNotNull(server.server.getWorld("world"), "Shadow overworld must be registered in CraftServer");
        assertNotNull(server.server.getScoreboardManager(), "CraftServer scoreboard manager must be initialized");
    }

    @Test
    void worldBlockAndChunkAccessWorksWithoutBlocking() {
        org.bukkit.World world = server.server.getWorld("world");
        assertNotNull(world, "Overworld must be found");

        // Chunk access
        assertTrue(world.isChunkLoaded(0, 0), "Chunk (0, 0) should be considered loaded");
        org.bukkit.Chunk chunk = world.getChunkAt(0, 0);
        assertNotNull(chunk, "Chunk (0, 0) should be returned non-blocking");
        assertEquals(0, chunk.getX());
        assertEquals(0, chunk.getZ());

        // Block access via CraftBlock
        org.bukkit.block.Block block = world.getBlockAt(0, 64, 0);
        assertNotNull(block, "Block should be returned");
        assertEquals(org.bukkit.Material.AIR, block.getType(), "Default block state should be air without FFI");
        assertNotNull(block.getBlockData(), "BlockData should not be null");

        // NMS Level block queries
        net.minecraft.server.level.ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        assertNotNull(level);
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(new net.minecraft.core.BlockPos(0, 64, 0));
        assertNotNull(state);
        assertTrue(state.isAir());

        // Test setBlock does not throw
        assertDoesNotThrow(() -> level.setBlock(new net.minecraft.core.BlockPos(0, 64, 0), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 3, 512));
    }

    @Test
    void entitySpawnAndLookupWorks() {
        org.bukkit.World world = server.server.getWorld("world");
        assertNotNull(world, "Overworld must exist");

        org.bukkit.Location loc = new org.bukkit.Location(world, 10.5, 64.0, 10.5);
        org.bukkit.entity.Entity pig = world.spawnEntity(loc, org.bukkit.entity.EntityType.PIG);
        assertNotNull(pig, "Spawned entity must not be null");
        assertEquals(org.bukkit.entity.EntityType.PIG, pig.getType());
        assertTrue(pig.isValid(), "Spawned entity must be valid");

        // Lookup by UUID
        org.bukkit.entity.Entity foundByUuid = world.getEntity(pig.getUniqueId());
        assertNotNull(foundByUuid, "Entity must be found by UUID");
        assertEquals(pig.getUniqueId(), foundByUuid.getUniqueId());

        // CraftWorld.getEntities()
        java.util.Collection<org.bukkit.entity.Entity> entities = world.getEntities();
        assertTrue(entities.stream().anyMatch(e -> e.getUniqueId().equals(pig.getUniqueId())), "Entity list must contain spawned entity");

        // Real CraftBukkit / NMS cast verification
        assertInstanceOf(org.bukkit.craftbukkit.entity.CraftEntity.class, pig);
        org.bukkit.craftbukkit.entity.CraftEntity craftEntity = (org.bukkit.craftbukkit.entity.CraftEntity) pig;
        net.minecraft.world.entity.Entity nms = craftEntity.getHandle();
        assertNotNull(nms);
        assertInstanceOf(net.minecraft.world.entity.animal.pig.Pig.class, nms);

        // NMS ServerLevel entity lookup
        net.minecraft.server.level.ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        assertNotNull(level);
        net.minecraft.world.entity.Entity nmsFound = level.getEntity(pig.getUniqueId());
        assertNotNull(nmsFound, "NMS entity must be found in ServerLevel");
        assertSame(nms, nmsFound);
    }

    private static org.bukkit.plugin.Plugin createMockPlugin(String name) {
        return (org.bukkit.plugin.Plugin) java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.plugin.Plugin.class.getClassLoader(),
            new Class<?>[]{org.bukkit.plugin.Plugin.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getName")) return name;
                if (method.getName().equals("isEnabled")) return true;
                if (method.getName().equals("getPluginMeta")) {
                    return (io.papermc.paper.plugin.configuration.PluginMeta) java.lang.reflect.Proxy.newProxyInstance(
                        io.papermc.paper.plugin.configuration.PluginMeta.class.getClassLoader(),
                        new Class<?>[]{io.papermc.paper.plugin.configuration.PluginMeta.class},
                        (p, m, a) -> {
                            if (m.getName().equals("getName")) return name;
                            if (m.getName().equals("getDisplayName")) return name;
                            if (m.getName().equals("getVersion")) return "1.0";
                            if (m.getName().equals("getMainClass")) return name;
                            if (m.getName().equals("getAPIVersion")) return "1.21";
                            if (m.getReturnType().equals(String.class)) return "";
                            if (m.getReturnType().equals(java.util.List.class)) return java.util.List.of();
                            if (m.getReturnType().equals(java.util.Set.class)) return java.util.Set.of();
                            if (m.getReturnType().equals(java.util.Map.class)) return java.util.Map.of();
                            if (m.getReturnType().equals(boolean.class)) return false;
                            if (m.getReturnType().equals(int.class)) return 0;
                            return null;
                        }
                    );
                }
                if (method.getName().equals("getLogger")) return java.util.logging.Logger.getLogger(name);
                if (method.getName().equals("getServer")) return server.server;
                if (method.getReturnType().equals(boolean.class)) return false;
                if (method.getReturnType().equals(int.class)) return 0;
                return null;
            }
        );
    }

    @Test
    void schedulerBridgeExecutesTasks() throws Exception {
        org.bukkit.plugin.Plugin plugin = createMockPlugin("SchedulerTestPlugin");
        org.bukkit.craftbukkit.scheduler.CraftScheduler scheduler =
            (org.bukkit.craftbukkit.scheduler.CraftScheduler) server.server.getScheduler();
        assertNotNull(scheduler);

        // 1. Sync task execution on heartbeat
        java.util.concurrent.atomic.AtomicBoolean taskRan = new java.util.concurrent.atomic.AtomicBoolean(false);
        scheduler.runTask(plugin, () -> taskRan.set(true));
        assertFalse(taskRan.get(), "Task should not run before heartbeat");
        SchedulerBridge.heartbeat();
        assertTrue(taskRan.get(), "Task must run after heartbeat");

        // 2. Delayed task execution
        java.util.concurrent.atomic.AtomicInteger delayedRuns = new java.util.concurrent.atomic.AtomicInteger(0);
        scheduler.runTaskLater(plugin, delayedRuns::incrementAndGet, 2);
        SchedulerBridge.heartbeat(); // tick 1
        assertEquals(0, delayedRuns.get(), "Task should not run at delay - 1");
        SchedulerBridge.heartbeat(); // tick 2
        assertEquals(1, delayedRuns.get(), "Task should run at scheduled tick");

        // 3. Repeating task
        java.util.concurrent.atomic.AtomicInteger repeatingRuns = new java.util.concurrent.atomic.AtomicInteger(0);
        org.bukkit.scheduler.BukkitTask repeatingTask = scheduler.runTaskTimer(plugin, repeatingRuns::incrementAndGet, 0, 1);
        SchedulerBridge.heartbeat();
        assertEquals(1, repeatingRuns.get());
        SchedulerBridge.heartbeat();
        assertEquals(2, repeatingRuns.get());
        repeatingTask.cancel();
        SchedulerBridge.heartbeat();
        assertEquals(2, repeatingRuns.get(), "Task should not run after cancellation");

        // 4. Async task execution
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<Thread> asyncThread = new java.util.concurrent.atomic.AtomicReference<>();
        scheduler.runTaskAsynchronously(plugin, () -> {
            asyncThread.set(Thread.currentThread());
            latch.countDown();
        });
        SchedulerBridge.heartbeat();
        assertTrue(latch.await(2, java.util.concurrent.TimeUnit.SECONDS), "Async task must execute");
        assertNotEquals(Thread.currentThread(), asyncThread.get(), "Async task must execute on separate worker thread");
    }

    @Test
    void commandBridgeAndDispatchWorks() {
        java.util.concurrent.atomic.AtomicBoolean cmdExecuted = new java.util.concurrent.atomic.AtomicBoolean(false);
        org.bukkit.command.Command testCmd = new org.bukkit.command.Command("phase5test") {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
                cmdExecuted.set(true);
                return true;
            }

            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) {
                return java.util.List.of("candidate1", "candidate2");
            }
        };

        server.server.getCommandMap().register("phase5plugin", testCmd);

        // Direct CommandMap dispatch without leading slash
        assertTrue(
            server.server.getCommandMap().dispatch(server.server.getConsoleSender(), "phase5test"),
            "CommandMap.dispatch without leading slash must succeed"
        );
        cmdExecuted.set(false);

        // Dispatch via CommandBridge.dispatchRaw
        boolean executed = CommandBridge.dispatchRaw(null, "Console", true, "phase5test");
        assertTrue(executed, "Command dispatch must succeed");
        assertTrue(cmdExecuted.get(), "Command execute callback must be invoked");

        // Tab completion via CommandBridge.tabCompleteRaw
        String[] completions = CommandBridge.tabCompleteRaw(
            null, "Console", true, "phase5test can", "world", 0, 64, 0
        );
        assertNotNull(completions);
        assertTrue(completions.length > 0, "Tab completion must return candidates");
        assertEquals("candidate1", completions[0]);
    }

    public static class TestEventListener implements org.bukkit.event.Listener {
        final java.util.concurrent.atomic.AtomicBoolean tickReceived = new java.util.concurrent.atomic.AtomicBoolean(false);

        @org.bukkit.event.EventHandler
        public void onTick(com.destroystokyo.paper.event.server.ServerTickStartEvent event) {
            tickReceived.set(true);
        }
    }

    @Test
    void eventBridgeAndDispatchWorks() {
        org.bukkit.plugin.Plugin plugin = createMockPlugin("EventTestPlugin");
        TestEventListener listener = new TestEventListener();
        server.server.getPluginManager().registerEvents(listener, plugin);

        patchbukkit.events.Event protoEvent = patchbukkit.events.Event.newBuilder()
            .setServerTickStart(patchbukkit.events.ServerTickStartEvent.newBuilder().setTick(42).build())
            .build();

        byte[] resp = org.patchbukkit.events.PatchBukkitEventFactory.fireEventFromBytes(
            protoEvent.toByteArray(), "EventTestPlugin"
        );
        assertNotNull(resp);
        assertTrue(listener.tickReceived.get(), "Registered listener must receive ServerTickStartEvent");
    }

    @Test
    void playerJoinEventUsesAuthenticCraftPlayer() {
        java.util.UUID uuid = java.util.UUID.randomUUID();

        // 1. AsyncPlayerPreLoginEvent fires first and must not prematurely create a "Player" ServerPlayer
        //    or throw "may only be triggered asynchronously" when dispatched via PluginManager.callEvent.
        patchbukkit.events.Event protoPreLogin = patchbukkit.events.Event.newBuilder()
            .setAsyncPlayerPreLogin(
                patchbukkit.events.AsyncPlayerPreLoginEvent.newBuilder()
                    .setPlayerUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()).build())
                    .setPlayerName("TestGrimPlayer")
                    .setIpAddress("127.0.0.1")
                    .build()
            )
            .build();
        org.bukkit.event.Event preLoginEvent = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(protoPreLogin);
        assertInstanceOf(org.bukkit.event.player.AsyncPlayerPreLoginEvent.class, preLoginEvent);
        assertDoesNotThrow(() -> Bukkit.getPluginManager().callEvent(preLoginEvent));

        // 2. PlayerLogin / PlayerJoin registers the player with their real username and entity ID
        VirtualChannelManager.registerPlayer(uuid.toString(), "TestGrimPlayer", true, 123);

        patchbukkit.events.Event protoJoin = patchbukkit.events.Event.newBuilder()
            .setPlayerJoin(
                patchbukkit.events.PlayerJoinEvent.newBuilder()
                    .setPlayerUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()).build())
                    .setEntityId(123)
                    .setJoinMessage("")
                    .build()
            )
            .build();

        org.bukkit.event.Event created = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(protoJoin);
        assertInstanceOf(org.bukkit.event.player.PlayerJoinEvent.class, created);
        org.bukkit.event.player.PlayerJoinEvent joinEvent = (org.bukkit.event.player.PlayerJoinEvent) created;
        assertInstanceOf(
            org.bukkit.craftbukkit.entity.CraftPlayer.class,
            joinEvent.getPlayer(),
            "PlayerJoinEvent player must be org.bukkit.craftbukkit.entity.CraftPlayer"
        );
        org.bukkit.craftbukkit.entity.CraftPlayer cp = (org.bukkit.craftbukkit.entity.CraftPlayer) joinEvent.getPlayer();
        assertEquals("TestGrimPlayer", cp.getName());
        assertEquals(123, cp.getEntityId());
        assertNotNull(cp.getHandle().connection);
        assertNotNull(cp.getHandle().connection.connection);
        assertNotNull(cp.getHandle().connection.connection.channel);
    }

    public static class PaperChatTestListener implements org.bukkit.event.Listener {
        final java.util.concurrent.atomic.AtomicReference<String> receivedMsg =
            new java.util.concurrent.atomic.AtomicReference<>();

        @org.bukkit.event.EventHandler
        public void onPaperChat(io.papermc.paper.event.player.AsyncChatEvent event) {
            receivedMsg.set(
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(event.message())
            );
            event.message(Component.text("ModifiedByPaperChat"));
        }
    }

    @Test
    void playerInventoryWorldBridgeAndPaperChatWork() throws Exception {
        java.util.UUID uuid = java.util.UUID.randomUUID();
        VirtualChannelManager.registerPlayer(uuid.toString(), "BridgeTestPlayer", false, 456);
        org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
        assertNotNull(player, "Registered player must be found via Bukkit.getPlayer");

        // 1. PlayerBridge state operations
        player.setHealth(15.0);
        assertEquals(15.0, player.getHealth(), 0.01);
        player.setFoodLevel(14);
        assertEquals(14, player.getFoodLevel());
        player.setSaturation(3.5f);
        assertEquals(3.5f, player.getSaturation(), 0.01f);
        player.setLevel(7);
        assertEquals(7, player.getLevel());
        player.setAllowFlight(true);
        assertTrue(player.getAllowFlight());
        player.setFlying(true);
        assertTrue(player.isFlying());
        player.setSneaking(true);
        assertTrue(player.isSneaking());
        player.setSprinting(true);
        assertTrue(player.isSprinting());
        player.setGameMode(org.bukkit.GameMode.CREATIVE);
        assertEquals(org.bukkit.GameMode.CREATIVE, player.getGameMode());

        // 2. InventoryBridge operations
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        assertNotNull(inv);
        inv.clear();
        inv.setHeldItemSlot(2);
        assertEquals(2, inv.getHeldItemSlot());
        inv.setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE, 1));
        assertEquals(Material.DIAMOND_PICKAXE, inv.getItemInMainHand().getType());
        assertEquals(Material.DIAMOND_PICKAXE, inv.getItem(2).getType());
        inv.setItemInOffHand(new ItemStack(Material.SHIELD, 1));
        assertEquals(Material.SHIELD, inv.getItemInOffHand().getType());
        inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET, 1));
        assertEquals(Material.DIAMOND_HELMET, inv.getHelmet().getType());

        // 3. WorldBridge metadata, gamerules, PvP & WorldBorder operations
        org.bukkit.World world = server.server.getWorld("world");
        assertNotNull(world);
        world.setTime(6000L);
        assertEquals(6000L, world.getTime());
        world.setStorm(true);
        assertTrue(world.hasStorm());
        world.setStorm(false);
        assertFalse(world.hasStorm());
        world.setDifficulty(org.bukkit.Difficulty.HARD);
        assertEquals(org.bukkit.Difficulty.HARD, world.getDifficulty());
        world.setPVP(true);
        assertTrue(world.getPVP());
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        assertEquals(Boolean.TRUE, world.getGameRuleValue(org.bukkit.GameRule.KEEP_INVENTORY));
        org.bukkit.WorldBorder border = world.getWorldBorder();
        assertNotNull(border);
        border.setCenter(10.0, 20.0);
        border.setSize(500.0);
        assertEquals(500.0, border.getSize(), 0.01);

        // 4. ServerBridge operations
        server.server.setMotd("PatchBukkit Test MOTD");
        assertEquals("PatchBukkit Test MOTD", server.server.getMotd());
        server.server.setMaxPlayers(42);
        assertEquals(42, server.server.getMaxPlayers());
        server.server.setIdleTimeout(15);
        assertEquals(15, server.server.getIdleTimeout());
        org.bukkit.ServerTickManager tickManager = server.server.getServerTickManager();
        assertNotNull(tickManager);
        tickManager.setTickRate(20.0f);
        assertEquals(20.0f, tickManager.getTickRate(), 0.01f);

        // 5. PlayerBridge UI / cooldown / sound / particle helpers
        player.setDisplayName("CustomBridgeName");
        assertEquals("CustomBridgeName", player.getDisplayName());
        player.setPlayerListName("CustomTabName");
        assertEquals("CustomTabName", player.getPlayerListName());
        player.setPlayerListHeaderFooter("Header", "Footer");
        player.sendTitle("Title", "Subtitle", 10, 40, 10);
        player.sendActionBar(Component.text("ActionBar"));
        player.setCooldown(Material.ENDER_PEARL, 20);
        assertTrue(player.getCooldown(Material.ENDER_PEARL) >= 0);

        // 6. Rich ItemStack metadata round-trip (damage, customName, lore, enchantments, unbreakable)
        ItemStack richSword = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta swordMeta = richSword.getItemMeta();
        assertNotNull(swordMeta);
        ((org.bukkit.inventory.meta.Damageable) swordMeta).setDamage(42);
        swordMeta.setDisplayName("Excalibur");
        swordMeta.setLore(List.of("Line 1", "Line 2"));
        swordMeta.setUnbreakable(true);
        swordMeta.addEnchant(Enchantment.SHARPNESS, 5, true);
        richSword.setItemMeta(swordMeta);

        net.minecraft.world.item.ItemStack nmsSword = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(richSword);
        patchbukkit.itemstack.ItemStack protoSword = InventoryBridge.nmsToProto(nmsSword);
        assertEquals(42, protoSword.getDamage());
        assertEquals("Excalibur", protoSword.getCustomName());
        assertEquals(List.of("Line 1", "Line 2"), protoSword.getLoreList());
        assertTrue(protoSword.getUnbreakable());
        assertEquals(5, protoSword.getEnchantmentsOrDefault("minecraft:sharpness", 0));

        net.minecraft.world.item.ItemStack restoredNms = InventoryBridge.protoToNms(protoSword);
        ItemStack restoredBukkit = org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitMirror(restoredNms);
        ItemMeta restoredMeta = restoredBukkit.getItemMeta();
        assertNotNull(restoredMeta);
        assertEquals(42, ((org.bukkit.inventory.meta.Damageable) restoredMeta).getDamage());
        assertEquals("Excalibur", restoredMeta.getDisplayName());
        assertEquals(List.of("Line 1", "Line 2"), restoredMeta.getLore());
        assertTrue(restoredMeta.isUnbreakable());
        assertEquals(5, restoredMeta.getEnchantLevel(Enchantment.SHARPNESS));

        // 7. Dual Paper AsyncChatEvent + Bukkit AsyncPlayerChatEvent dispatch
        org.bukkit.plugin.Plugin plugin = createMockPlugin("PaperChatPlugin");
        PaperChatTestListener chatListener = new PaperChatTestListener();
        server.server.getPluginManager().registerEvents(chatListener, plugin);

        patchbukkit.events.Event protoChat = patchbukkit.events.Event.newBuilder()
            .setAsyncPlayerChat(
                patchbukkit.events.AsyncPlayerChatEvent.newBuilder()
                    .setPlayerUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()).build())
                    .setMessage("HelloPaperWorld")
                    .build()
            )
            .build();
        byte[] respBytes = org.patchbukkit.events.PatchBukkitEventFactory.fireEventFromBytes(
            protoChat.toByteArray(), "PaperChatPlugin"
        );
        assertEquals("HelloPaperWorld", chatListener.receivedMsg.get());
        patchbukkit.events.FireEventResponse resp = patchbukkit.events.FireEventResponse.parseFrom(respBytes);
        assertEquals("ModifiedByPaperChat", resp.getData().getAsyncPlayerChat().getMessage());

        // 8. CraftLivingEntity & extended event round-trip verification
        org.bukkit.entity.Pig pig = world.spawn(new org.bukkit.Location(world, 0, 64, 0), org.bukkit.entity.Pig.class);
        assertNotNull(pig);
        pig.setHealth(7.5);
        assertEquals(7.5, pig.getHealth(), 0.01);
        pig.setPose(org.bukkit.entity.Pose.SITTING, false);
        assertEquals(org.bukkit.entity.Pose.SITTING, pig.getPose());
        pig.remove();

        // Verify TabCompleteEvent & ServerCommandEvent creation + serialization
        patchbukkit.events.Event protoTab = patchbukkit.events.Event.newBuilder()
            .setTabComplete(
                patchbukkit.events.TabCompleteEvent.newBuilder()
                    .setBuffer("/gamemode ")
                    .addCompletions("survival")
                    .addCompletions("creative")
                    .build()
            )
            .build();
        org.bukkit.event.Event tabEvent = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(protoTab);
        assertInstanceOf(org.bukkit.event.server.TabCompleteEvent.class, tabEvent);
        org.bukkit.event.server.TabCompleteEvent tce = (org.bukkit.event.server.TabCompleteEvent) tabEvent;
        assertEquals(List.of("survival", "creative"), tce.getCompletions());
        tce.setCompletions(List.of("spectator"));
        patchbukkit.events.FireEventResponse tabResp = patchbukkit.events.FireEventResponse.parseFrom(
            org.patchbukkit.events.PatchBukkitEventFactory.toFireEventResponse(tce)
        );
        assertEquals(List.of("spectator"), tabResp.getData().getTabComplete().getCompletionsList());

        // 9. Verify comprehensive event instantiation across World, Server, Entity, Vehicle & Hanging events
        org.bukkit.event.Event weatherEv = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setWeatherChange(patchbukkit.events.WeatherChangeEvent.newBuilder().setToWeatherState(true))
                .build()
        );
        assertInstanceOf(org.bukkit.event.weather.WeatherChangeEvent.class, weatherEv);

        org.bukkit.event.Event pingEv = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setServerListPing(
                    patchbukkit.events.ServerListPingEvent.newBuilder()
                        .setMotd("Test Ping")
                        .setNumPlayers(5)
                        .setMaxPlayers(20)
                )
                .build()
        );
        assertInstanceOf(org.bukkit.event.server.ServerListPingEvent.class, pingEv);
        assertInstanceOf(com.destroystokyo.paper.event.server.PaperServerListPingEvent.class, pingEv);
        assertDoesNotThrow(() -> {
            for (org.bukkit.entity.Player p : (org.bukkit.event.server.ServerListPingEvent) pingEv) {
                // Must be iterable without throwing UnsupportedOperationException
            }
        });

        org.bukkit.event.Event dmgEv = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setEntityDamageByEntity(
                    patchbukkit.events.EntityDamageByEntityEvent.newBuilder()
                        .setEntityId(101)
                        .setDamagerId(102)
                        .setDamage(6.0f)
                        .setCause("ENTITY_ATTACK")
                )
                .build()
        );
        assertInstanceOf(org.bukkit.event.entity.EntityDamageByEntityEvent.class, dmgEv);

        org.bukkit.event.Event vehicleCreateEv = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setVehicleCreate(patchbukkit.events.VehicleCreateEvent.newBuilder().setVehicleId(201))
                .build()
        );
        assertInstanceOf(org.bukkit.event.vehicle.VehicleCreateEvent.class, vehicleCreateEv);

        org.bukkit.event.Event slimeSplitEv = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setSlimeSplit(patchbukkit.events.SlimeSplitEvent.newBuilder().setEntityId(202).setCount(3))
                .build()
        );
        assertInstanceOf(org.bukkit.event.entity.SlimeSplitEvent.class, slimeSplitEv);

        // 10. Verify Plugin Messaging Channel registration and incoming payload dispatch
        java.util.concurrent.atomic.AtomicReference<byte[]> receivedPayload = new java.util.concurrent.atomic.AtomicReference<>();
        server.server.getMessenger().registerIncomingPluginChannel(
            plugin,
            "patchbukkit:test",
            (ch, p, msg) -> receivedPayload.set(msg)
        );
        org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setPlayerCustomPayload(
                    patchbukkit.events.PlayerCustomPayloadEvent.newBuilder()
                        .setPlayerUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()))
                        .setChannel("patchbukkit:test")
                        .setData(com.google.protobuf.ByteString.copyFromUtf8("hello-channel"))
                )
                .build()
        );
        assertNotNull(receivedPayload.get());
        assertEquals("hello-channel", new String(receivedPayload.get(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(player.getListeningPluginChannels().contains("patchbukkit:test"));

        // 11. Verify Custom GUI Inventory open, click (slot/clickType/action/item), and close lifecycle
        org.bukkit.inventory.Inventory customGui = Bukkit.createInventory(
            null,
            27,
            net.kyori.adventure.text.Component.text("Test Menu")
        );
        org.bukkit.inventory.ItemStack guiButton = new org.bukkit.inventory.ItemStack(Material.DIAMOND, 3);
        customGui.setItem(13, guiButton);
        player.openInventory(customGui);
        assertEquals(27, player.getOpenInventory().getTopInventory().getSize());

        org.bukkit.event.Event clickEvRaw = org.patchbukkit.events.PatchBukkitEventFactory.createEvent(
            patchbukkit.events.Event.newBuilder()
                .setInventoryClick(
                    patchbukkit.events.InventoryClickEvent.newBuilder()
                        .setPlayerUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()))
                        .setClickType("Right")
                        .setSlot(13)
                        .setRawSlot(13)
                        .setHotbarButton(-1)
                )
                .build()
        );
        assertInstanceOf(org.bukkit.event.inventory.InventoryClickEvent.class, clickEvRaw);
        org.bukkit.event.inventory.InventoryClickEvent clickEv =
            (org.bukkit.event.inventory.InventoryClickEvent) clickEvRaw;
        assertEquals(13, clickEv.getRawSlot());
        assertEquals(13, clickEv.getSlot());
        assertEquals(org.bukkit.event.inventory.ClickType.RIGHT, clickEv.getClick());
        assertEquals(org.bukkit.event.inventory.InventoryAction.PICKUP_HALF, clickEv.getAction());
        assertNotNull(clickEv.getCurrentItem());
        assertEquals(Material.DIAMOND, clickEv.getCurrentItem().getType());
        assertEquals(3, clickEv.getCurrentItem().getAmount());

        // Closing the inventory resets player's open container back to player inventory
        byte[] closeBytes = patchbukkit.events.Event.newBuilder()
            .setInventoryClose(
                patchbukkit.events.InventoryCloseEvent.newBuilder()
                    .setPlayerUuid(patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()))
            )
            .build()
            .toByteArray();
        org.patchbukkit.events.PatchBukkitEventFactory.fireEventFromBytes(closeBytes, "");
        assertEquals(
            org.bukkit.event.inventory.InventoryType.CRAFTING,
            player.getOpenInventory().getTopInventory().getType()
        );
    }
}
