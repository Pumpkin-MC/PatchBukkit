package org.patchbukkit.bootstrap;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import io.papermc.paper.adventure.PaperAdventure;
import java.nio.file.Files;
import java.nio.file.Path;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.patchbukkit.PatchBukkitServer;

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
        // Same order as production (rust/src/java/jvm/worker.rs): boot, then install the facade.
        PatchBukkitServer.initServer();
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
    void patchBukkitServerUsesRealObjectsAndStaysFacade() {
        PatchBukkitServer.initServer();
        assertSame(server.server, Bukkit.getServer(), "Bukkit.getServer() should be the real CraftServer");
        assertSame(server, PatchBukkitServer.getDedicatedServer());
        assertSame(server.server, PatchBukkitServer.getCraftServer());
        assertSame(server.getPlayerList(), PatchBukkitServer.getDedicatedPlayerList());
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

        // Dispatch via PatchBukkitCommandMap.dispatchRaw
        boolean executed = org.patchbukkit.command.PatchBukkitCommandMap.dispatchRaw(null, "Console", true, "phase5test");
        assertTrue(executed, "Command dispatch must succeed");
        assertTrue(cmdExecuted.get(), "Command execute callback must be invoked");

        // Tab completion via PatchBukkitCommandMap.tabCompleteRaw
        String[] completions = org.patchbukkit.command.PatchBukkitCommandMap.tabCompleteRaw(
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
}
