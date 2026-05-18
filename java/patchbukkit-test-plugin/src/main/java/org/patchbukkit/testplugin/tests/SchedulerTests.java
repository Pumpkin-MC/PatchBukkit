package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class SchedulerTests {

    private final JavaPlugin plugin;

    public SchedulerTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "Server.getScheduler() returns non-null", category = TestCategory.SCHEDULER)
    public void testSchedulerNotNull() {
        BukkitScheduler sched = Bukkit.getServer().getScheduler();
        assertNotNull(sched, "Server.getScheduler()");
    }

    @ConformanceTest(name = "runTaskAsynchronously() executes task", category = TestCategory.SCHEDULER)
    public void testRunTaskAsynchronously() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean ran = new AtomicBoolean(false);

        Bukkit.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ran.set(true);
            latch.countDown();
        });

        boolean finished = latch.await(3, TimeUnit.SECONDS);
        assertTrue(finished && ran.get(), "runTaskAsynchronously() task must execute within 3s");
    }

    @ConformanceTest(name = "runTaskLaterAsynchronously() executes after delay", category = TestCategory.SCHEDULER)
    public void testRunTaskLaterAsynchronously() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(plugin, latch::countDown, 1L);

        boolean finished = latch.await(3, TimeUnit.SECONDS);
        assertTrue(finished, "runTaskLaterAsynchronously() task must execute within 3s");
    }

    @ConformanceTest(name = "cancelTask() prevents execution", category = TestCategory.SCHEDULER)
    public void testCancelTask() throws InterruptedException {
        AtomicBoolean ran = new AtomicBoolean(false);

        BukkitTask task = Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(plugin, () -> ran.set(true), 40L);
        task.cancel();

        Thread.sleep(2500);
        assertTrue(!ran.get(), "Cancelled task must not execute");
    }

    @ConformanceTest(name = "runTaskTimerAsynchronously() executes repeatedly", category = TestCategory.SCHEDULER)
    public void testRunTaskTimerAsynchronously() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        BukkitTask[] ref = new BukkitTask[1];

        ref[0] = Bukkit.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            latch.countDown();
            if (latch.getCount() == 0 && ref[0] != null) ref[0].cancel();
        }, 0L, 1L);

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        ref[0].cancel();
        assertTrue(finished, "runTaskTimerAsynchronously() must fire at least 3 times within 5s");
    }

    @ConformanceTest(name = "scheduleAsyncDelayedTask() returns valid task id", category = TestCategory.SCHEDULER)
    public void testScheduleAsyncDelayedTask() {
        int id = Bukkit.getServer().getScheduler().scheduleAsyncDelayedTask(plugin, () -> {}, 1L);
        assertTrue(id > 0, "scheduleAsyncDelayedTask() must return positive task id");
        Bukkit.getServer().getScheduler().cancelTask(id);
    }
}
