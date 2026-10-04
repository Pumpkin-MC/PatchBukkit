package org.patchbukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
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
 *
 * <p>Pending work is bound to the owning entity's lifecycle: once the entity is retired, new
 * scheduling is rejected and already-queued callbacks run their retirement callback instead of the
 * task.
 */
public final class PatchBukkitEntityScheduler implements EntityScheduler {

    private final BooleanSupplier retiredCheck;

    public PatchBukkitEntityScheduler() {
        this(() -> false);
    }

    public PatchBukkitEntityScheduler(BooleanSupplier retiredCheck) {
        this.retiredCheck = retiredCheck;
    }

    @Override
    public boolean execute(
        @NotNull Plugin plugin,
        @NotNull Runnable run,
        @Nullable Runnable retired,
        long delayTicks
    ) {
        if (retiredCheck.getAsBoolean()) {
            if (retired != null) {
                retired.run();
            }
            return false;
        }
        try {
            Runnable guarded = () -> {
                if (retiredCheck.getAsBoolean()) {
                    if (retired != null) {
                        retired.run();
                    }
                    return;
                }
                run.run();
            };
            if (delayTicks <= 0) {
                Bukkit.getScheduler().runTask(plugin, guarded);
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, guarded, delayTicks);
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
        return schedule(plugin, task, retired, 0L, -1L);
    }

    @Override
    public @Nullable ScheduledTask runDelayed(
        @NotNull Plugin plugin,
        @NotNull Consumer<ScheduledTask> task,
        @Nullable Runnable retired,
        long delayTicks
    ) {
        return schedule(plugin, task, retired, delayTicks, -1L);
    }

    @Override
    public @Nullable ScheduledTask runAtFixedRate(
        @NotNull Plugin plugin,
        @NotNull Consumer<ScheduledTask> task,
        @Nullable Runnable retired,
        long initialDelayTicks,
        long periodTicks
    ) {
        return schedule(plugin, task, retired, initialDelayTicks, periodTicks);
    }

    private ScheduledTask schedule(
        Plugin plugin,
        Consumer<ScheduledTask> task,
        Runnable retired,
        long delayTicks,
        long periodTicks
    ) {
        if (retiredCheck.getAsBoolean()) {
            return null;
        }
        boolean repeating = periodTicks > 0;
        // Create the wrapper before scheduling so the callback always receives a non-null task,
        // even if the scheduler runs it immediately.
        WrappedScheduledTask scheduledTask = new WrappedScheduledTask(plugin, repeating);
        Runnable run = () -> {
            if (retiredCheck.getAsBoolean()) {
                if (retired != null) {
                    retired.run();
                }
                return;
            }
            if (scheduledTask.isCancelled()) {
                return;
            }
            scheduledTask.beginRun();
            try {
                task.accept(scheduledTask);
            } finally {
                scheduledTask.endRun();
            }
        };

        BukkitTask bukkitTask;
        if (repeating) {
            bukkitTask = Bukkit.getScheduler().runTaskTimer(plugin, run, delayTicks, periodTicks);
        } else if (delayTicks > 0) {
            bukkitTask = Bukkit.getScheduler().runTaskLater(plugin, run, delayTicks);
        } else {
            bukkitTask = Bukkit.getScheduler().runTask(plugin, run);
        }
        scheduledTask.setBukkitTask(bukkitTask);
        return scheduledTask;
    }

    private static final class WrappedScheduledTask implements ScheduledTask {

        private final Plugin plugin;
        private final boolean repeating;
        private final AtomicReference<ExecutionState> state = new AtomicReference<>(
            ExecutionState.IDLE
        );
        private volatile BukkitTask task;
        private volatile boolean cancelled;

        private WrappedScheduledTask(Plugin plugin, boolean repeating) {
            this.plugin = plugin;
            this.repeating = repeating;
        }

        private void setBukkitTask(BukkitTask task) {
            this.task = task;
        }

        private void beginRun() {
            state.set(ExecutionState.RUNNING);
        }

        private void endRun() {
            if (cancelled) {
                state.set(
                    repeating ? ExecutionState.CANCELLED_RUNNING : ExecutionState.CANCELLED
                );
            } else if (repeating) {
                state.set(ExecutionState.IDLE);
            } else {
                state.set(ExecutionState.FINISHED);
            }
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
            if (state.get() == ExecutionState.FINISHED) {
                return CancelledState.ALREADY_EXECUTED;
            }
            if (cancelled || (task != null && task.isCancelled())) {
                return CancelledState.CANCELLED_ALREADY;
            }
            cancelled = true;
            if (task != null) {
                task.cancel();
            }
            // If a callback is mid-flight it will transition to CANCELLED_RUNNING/CANCELLED when
            // it finishes. Otherwise cancellation leaves nothing running.
            if (state.get() != ExecutionState.RUNNING) {
                state.set(ExecutionState.CANCELLED);
            }
            return CancelledState.CANCELLED_BY_CALLER;
        }

        @Override
        public @NotNull ExecutionState getExecutionState() {
            return state.get();
        }

        @Override
        public boolean isCancelled() {
            return cancelled || (task != null && task.isCancelled());
        }
    }
}
