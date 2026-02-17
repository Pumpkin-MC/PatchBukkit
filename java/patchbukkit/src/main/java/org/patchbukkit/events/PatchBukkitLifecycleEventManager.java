package org.patchbukkit.events;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.handler.configuration.LifecycleEventHandlerConfiguration;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEventType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class PatchBukkitLifecycleEventManager implements LifecycleEventManager<Plugin> {

    private final Plugin plugin;
    private final BooleanSupplier registrationCheck;
    private final List<RegisteredHandler<?>> handlers = new ArrayList<>();

    public PatchBukkitLifecycleEventManager(Plugin plugin, BooleanSupplier registrationCheck) {
        this.plugin = plugin;
        this.registrationCheck = registrationCheck;
    }

    @Override
    public void registerEventHandler(
            @NotNull LifecycleEventHandlerConfiguration<? super Plugin> handlerConfiguration) {
        if (!registrationCheck.getAsBoolean()) {
            throw new IllegalStateException(
                "Cannot register lifecycle event handler for " + plugin.getName()
                + " — registration is not allowed at this time");
        }
    }

    public Plugin getPlugin() {
        return plugin;
    }

    private record RegisteredHandler<E extends LifecycleEvent>(
        LifecycleEventType<? super Plugin, E, ?> type,
        LifecycleEventHandler<? super E> handler
    ) {}
}
