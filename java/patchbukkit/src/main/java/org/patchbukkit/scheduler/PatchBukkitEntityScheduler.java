package org.patchbukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Minimal {@link EntityScheduler} that delegates to the regular Bukkit scheduler.
 *
 * <p>PatchBukkit runs a single-region server, so entity scheduling can be routed through
 * {@link org.bukkit.scheduler.BukkitScheduler} without per-entity thread affinity. This exists so
 * {@code Entity#getScheduler()} honours its {@code @NotNull} contract instead of returning
 * {@code null}.
 */
public final class PatchBukkitEntityScheduler implements EntityScheduler {

    @Override
    public boolean execute(
        @NotNull Plugin plugin,
        @NotNull Runnable run,
        @Nullable Runnable retired,
        long delayTicks
    ) {
        try {
            if (delayTicks <= 0) {
                Bukkit.getScheduler().runTask(plugin, run);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, run, delayTicks);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public @Nullable ScheduledTask run(
        @NotNull Plugin plugin,
        @NotNull Consumer<ScheduledTask> task,
        @Nullable Runnable retired
    ) {
        return schedule(plugin, task, 0L, -1L);
    }

    @Override
    public @Nullable ScheduledTask runDelayed(
        @NotNull Plugin plugin,
        @NotNull Consumer<ScheduledTask> task,
        @Nullable Runnable retired,
        long delayTicks
    ) {
        return schedule(plugin, task, delayTicks, -1L);
    }

    @Override
    public @Nullable ScheduledTask runAtFixedRate(
        @NotNull Plugin plugin,
        @NotNull Consumer<ScheduledTask> task,
        @Nullable Runnable retired,
        long initialDelayTicks,
        long periodTicks
    ) {
        return schedule(plugin, task, initialDelayTicks, periodTicks);
    }

    private ScheduledTask schedule(
        Plugin plugin,
        Consumer<ScheduledTask> task,
        long delayTicks,
        long periodTicks
    ) {
        WrappedScheduledTask[] holder = new WrappedScheduledTask[1];
        Runnable run = () -> {
            WrappedScheduledTask current = holder[0];
            if (current != null && !current.isCancelled()) {
                task.accept(current);
            }
        };

        BukkitTask bukkitTask;
        if (periodTicks > 0) {
            bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, run, delayTicks, periodTicks);
        } else if (delayTicks > 0) {
            bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, run, delayTicks);
        } else {
            bukkitTask = Bukkit.getScheduler().runTask(plugin, run);
        }

        WrappedScheduledTask scheduledTask = new WrappedScheduledTask(
            plugin,
            bukkitTask,
            periodTicks > 0
        );
        holder[0] = scheduledTask;
        return scheduledTask;
    }

    private static final class WrappedScheduledTask implements ScheduledTask {

        private final Plugin plugin;
        private final BukkitTask task;
        private final boolean repeating;

        private WrappedScheduledTask(Plugin plugin, BukkitTask task, boolean repeating) {
            this.plugin = plugin;
            this.task = task;
            this.repeating = repeating;
        }

        @Override
        public @NotNull Plugin getOwningPlugin() {
            return plugin;
        }

        @Override
        public boolean isRepeatingTask() {
            return repeating;
        }

        @Override
        public @NotNull CancelledState cancel() {
            if (task.isCancelled()) {
                return CancelledState.CANCELLED_ALREADY;
            }
            task.cancel();
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public @NotNull ExecutionState getExecutionState() {
            return task.isCancelled() ? ExecutionState.CANCELLED : ExecutionState.FINISHED;
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }
}
