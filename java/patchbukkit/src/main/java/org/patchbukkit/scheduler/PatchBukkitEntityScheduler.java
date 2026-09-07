package org.patchbukkit.scheduler;

import io.papermc.paper.threadedregions.scheduler.EntityScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.entity.PatchBukkitEntity;

/**
* Folia entity scheduler backed by the server scheduler. Tasks are bound to
* the entity's lifetime: once it is dead or removed, new tasks retire
* immediately instead of running.
*/
public final class PatchBukkitEntityScheduler implements EntityScheduler {

    private final PatchBukkitEntity entity;

    public PatchBukkitEntityScheduler(@NotNull PatchBukkitEntity entity) {
        this.entity = entity;
    }

    private boolean retired() {
        return entity.isDead() || !entity.isValid();
    }

    @Override
    public boolean execute(@NotNull Plugin plugin, @NotNull Runnable run, @Nullable Runnable retired, long delayTicks) {
        if (retired()) {
            if (retired != null) {
                retired.run();
            }
            return false;
        }
        Bukkit.getScheduler().runTaskLater(plugin, run, delayTicks);
        return true;
    }

    @Override
    public @NotNull ScheduledTask run(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, @Nullable Runnable retired) {
        return schedule(plugin, task, retired, 0L, -1L);
    }

    @Override
    public @NotNull ScheduledTask runDelayed(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, @Nullable Runnable retired, long delayTicks) {
        return schedule(plugin, task, retired, delayTicks, -1L);
    }

    @Override
    public @NotNull ScheduledTask runAtFixedRate(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task, @Nullable Runnable retired, long delayTicks, long periodTicks) {
        return schedule(plugin, task, retired, delayTicks, periodTicks);
    }

    private @NotNull ScheduledTask schedule(@NotNull Plugin plugin, @NotNull Consumer<ScheduledTask> task,
            @Nullable Runnable retired, long delayTicks, long periodTicks) {
        boolean repeating = periodTicks > 0;
        PatchBukkitScheduledTask scheduled = new PatchBukkitScheduledTask(plugin, repeating);
        if (retired()) {
            if (retired != null) {
                retired.run();
            }
            scheduled.setState(ScheduledTask.ExecutionState.CANCELLED);
            return scheduled;
        }
        Runnable runner = () -> {
            scheduled.setState(ScheduledTask.ExecutionState.RUNNING);
            try {
                task.accept(scheduled);
            } finally {
                if (!repeating) {
                    scheduled.setState(ScheduledTask.ExecutionState.FINISHED);
                }
            }
        };
        if (repeating) {
            scheduled.setBukkitTask(Bukkit.getScheduler().runTaskTimer(plugin, runner, delayTicks, periodTicks));
        } else if (delayTicks > 0) {
            scheduled.setBukkitTask(Bukkit.getScheduler().runTaskLater(plugin, runner, delayTicks));
        } else {
            scheduled.setBukkitTask(Bukkit.getScheduler().runTask(plugin, runner));
        }
        return scheduled;
    }
}
