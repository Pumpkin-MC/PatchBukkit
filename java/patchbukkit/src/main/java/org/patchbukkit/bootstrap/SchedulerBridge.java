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

    private SchedulerBridge() {}

    /**
     * Executes one tick of the Paper scheduler on the calling (main) thread.
     */
    public static void heartbeat() {
        if (!HeadlessPaperServer.isBooted()) return;
        try {
            net.minecraft.server.dedicated.DedicatedServer server = HeadlessPaperServer.get();
            if (server != null && server.server != null) {
                CraftServer craftServer = server.server;
                if (craftServer.getScheduler() instanceof CraftScheduler scheduler) {
                    scheduler.mainThreadHeartbeat();
                }
            }
        } catch (Throwable t) {
            LOGGER.log(Level.SEVERE, "Error in CraftScheduler heartbeat", t);
        }
    }
}
