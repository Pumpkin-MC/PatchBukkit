package org.patchbukkit.bootstrap;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.scheduler.CraftScheduler;

/**
 * Bridges CraftScheduler's heartbeat to Pumpkin's tick loop.
 *
 * <p>When Pumpkin executes a server tick, it fires {@code ServerTickStartEvent}, which
 * triggers {@link #heartbeat()}. That advances the scheduler tick and executes all pending
 * synchronous tasks on the main thread (worker thread).
 */
public final class SchedulerBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static java.util.concurrent.ScheduledExecutorService fallbackTicker;
    private static volatile boolean inPluginBootstrap = false;
    private static final java.util.concurrent.atomic.AtomicLong HEARTBEAT_COUNT = new java.util.concurrent.atomic.AtomicLong(0);

    private SchedulerBridge() {}

    public static void setInPluginBootstrap(boolean value) {
        inPluginBootstrap = value;
    }

    public static synchronized void startFallbackTicker() {
        if (fallbackTicker != null && !fallbackTicker.isShutdown()) return;
        fallbackTicker = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PatchBukkit-SchedulerFallbackTicker");
            t.setDaemon(true);
            return t;
        });
        fallbackTicker.scheduleAtFixedRate(SchedulerBridge::heartbeat, 50, 50, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Called when Pumpkin's native {@code ServerTickStartEvent} fires on the JVM worker thread.
     * Shuts down the fallback ticker (used only in standalone JUnit tests) so all scheduler
     * heartbeats run exclusively and deterministically on the worker thread.
     */
    public static void onServerTick() {
        if (fallbackTicker != null) {
            synchronized (SchedulerBridge.class) {
                if (fallbackTicker != null) {
                    fallbackTicker.shutdownNow();
                    fallbackTicker = null;
                }
            }
        }
        heartbeat();
    }

    /**
     * The rest of what Paper's MinecraftServer.tickChildren runs after the Bukkit scheduler: the Folia-style global and
     * entity schedulers (Simple Voice Chat starts its voice server from the global one) and chat click callbacks.
     */
    private static void tickPaperSchedulers(net.minecraft.server.dedicated.DedicatedServer server) {
        try {
            ((io.papermc.paper.threadedregions.scheduler.FoliaGlobalRegionScheduler) org.bukkit.Bukkit.getGlobalRegionScheduler()).tick();
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Global region scheduler tick failed", t);
        }
        for (io.papermc.paper.threadedregions.EntityScheduler entityScheduler : server.entitySchedulerTickList.getAllSchedulers()) {
            try {
                if (!entityScheduler.isRetired()) entityScheduler.executeTick();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Entity scheduler tick failed", t);
            }
        }
        try {
            int tick = net.minecraft.server.MinecraftServer.currentTick;
            io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.ADVENTURE_CLICK_MANAGER.handleQueue(tick);
            io.papermc.paper.adventure.providers.ClickCallbackProviderImpl.DIALOG_CLICK_MANAGER.handleQueue(tick);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Click callback queue failed", t);
        }
    }

    /**
     * Executes one tick of the Paper scheduler on the calling (main) thread.
     */
    public static synchronized void heartbeat() {
        if (inPluginBootstrap || !HeadlessPaperServer.isBooted()) return;
        HEARTBEAT_COUNT.incrementAndGet();
        Boolean prevThreadState = HeadlessPaperServer.enterPrimaryThread();
        try {
            net.minecraft.server.dedicated.DedicatedServer server = HeadlessPaperServer.get();
            if (server != null && server.server != null) {
                net.minecraft.server.MinecraftServer.currentTick++;
                CraftServer craftServer = server.server;
                if (craftServer.getScheduler() instanceof CraftScheduler scheduler) {
                    scheduler.mainThreadHeartbeat();
                }
                tickPaperSchedulers(server);
                org.patchbukkit.network.VirtualChannelManager.getInstance().tickChannels();
                org.patchbukkit.bootstrap.CommandBridge.checkAndSyncIfNeeded();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error in CraftScheduler heartbeat", t);
        } finally {
            HeadlessPaperServer.restoreThreadState(prevThreadState);
        }
    }
}
