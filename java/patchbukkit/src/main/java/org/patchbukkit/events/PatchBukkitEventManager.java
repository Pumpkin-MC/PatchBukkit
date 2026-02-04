package org.patchbukkit.events;

import org.bukkit.Server;
import org.bukkit.Warning;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.AuthorNagException;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.patchbukkit.bridge.BridgeUtils;

import com.google.common.collect.Sets;

import co.aikar.timings.TimedEventExecutor;

import org.jetbrains.annotations.NotNull;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.events.CallEventRequest;
import patchbukkit.events.PlayerJoinEvent;
import patchbukkit.events.RegisterEventRequest;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

public class PatchBukkitEventManager {

    private final Server server;

    public PatchBukkitEventManager(Server server) {
        this.server = server;
    }

    public void callEvent(@NotNull Event event) throws IllegalStateException {
        if (event.isAsynchronous() && this.server.isPrimaryThread()) {
            throw new IllegalStateException(event.getEventName() + " may only be triggered asynchronously.");
        } else if (!event.isAsynchronous() && !this.server.isPrimaryThread() && !this.server.isStopping()) {
            throw new IllegalStateException(event.getEventName() + " may only be triggered synchronously.");
        }

        var request = CallEventRequest.newBuilder();
        switch (event.getEventName()) {
            case "org.bukkit.event.player.PlayerJoinEvent":
                var castedEvent = (org.bukkit.event.player.PlayerJoinEvent) event;
                request.setPlayerJoin(
                    PlayerJoinEvent.newBuilder()
                            .setJoinMessage(castedEvent.joinMessage().toString())
                            .setPlayerUuid(BridgeUtils.convertUuid(castedEvent.getPlayer().getUniqueId()))
                );
                break;
        }
        var response = NativeBridgeFfi.callEvent(request.build());

        boolean handledByPumpkin;
        if (response == null) handledByPumpkin = false;
        else handledByPumpkin = response.getHandled();

        if (!handledByPumpkin) {
            // Pumpkin doesn't know this event type, dispatch Java-only
            callEventJavaOnly(event);
        }
    }

    /**
    * Java-only event dispatch for events that don't have Pumpkin equivalents.
    * Used for custom plugin events or unsupported Bukkit events.
    */
    private void callEventJavaOnly(@NotNull Event event) {
        HandlerList handlers = event.getHandlers();
        RegisteredListener[] listeners = handlers.getRegisteredListeners();

        for (RegisteredListener registration : listeners) {
            if (!registration.getPlugin().isEnabled()) {
                continue;
            }

            try {
                registration.callEvent(event);
            } catch (AuthorNagException ex) {
                Plugin plugin = registration.getPlugin();

                if (plugin.isNaggable()) {
                    plugin.setNaggable(false);

                    this.server.getLogger().log(Level.SEVERE, String.format(
                        "Nag author(s): '%s' of '%s' about the following: %s",
                        plugin.getPluginMeta().getAuthors(),
                        plugin.getPluginMeta().getDisplayName(),
                        ex.getMessage()
                    ));
                }
            } catch (Throwable ex) {
                this.server.getLogger().log(
                    Level.SEVERE,
                    "Could not pass event " + event.getEventName()
                        + " to " + registration.getPlugin().getPluginMeta().getDisplayName(),
                    ex
                );
            }
        }
    }

    /**
     * Called from Rust (via j4rs) when a Pumpkin event fires for a specific plugin.
     *
     * Iterates PatchBukkitEvent's HandlerList, filters to the target plugin
     * by name, and invokes its executors. Cancellation state is set on the
     * event and read back by Rust after this returns.
     *
     * @param event      The PatchBukkitEvent populated by Rust
     * @param pluginName The plugin whose handlers should execute
     */
    public void fireEvent(@NotNull Event event, @NotNull String pluginName) {
        for (RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if (!listener.getPlugin().getName().equals(pluginName)) continue;
            if (!listener.getPlugin().isEnabled()) continue;

            try {
                listener.callEvent(event);
            } catch (Throwable ex) {
                this.server.getLogger().log(
                    Level.SEVERE,
                    "Could not pass event " + event.getEventName()
                        + " to " + listener.getPlugin().getPluginMeta().getDisplayName(),
                    ex
                );
            }
        }
    }

    public void registerEvents(@NotNull Listener listener, @NotNull Plugin plugin) {
        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register " + listener + " while not enabled");
        }

        for (Map.Entry<Class<? extends Event>, Set<RegisteredListener>> entry : this.createRegisteredListeners(listener, plugin).entrySet()) {
            this.getEventListeners(this.getRegistrationClass(entry.getKey())).registerAll(entry.getValue());

            for (RegisteredListener rl : entry.getValue()) {
                int priorityOrdinal = Math.min(rl.getPriority().ordinal(), 4);
                var request = RegisterEventRequest.newBuilder().setEventType(entry.getKey().getName()).setPluginName(plugin.getName()).setPriority(priorityOrdinal).setBlocking(true).build();
                NativeBridgeFfi.registerEvent(request);
            }
        }
    }

    public void registerEvent(@NotNull Class<? extends Event> event, @NotNull Listener listener, @NotNull EventPriority priority, @NotNull EventExecutor executor, @NotNull Plugin plugin) {
        this.registerEvent(event, listener, priority, executor, plugin, false);
    }

    public void registerEvent(@NotNull Class<? extends Event> event, @NotNull Listener listener, @NotNull EventPriority priority, @NotNull EventExecutor executor, @NotNull Plugin plugin, boolean ignoreCancelled) {
        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register " + event + " while not enabled");
        }

        executor = new TimedEventExecutor(executor, plugin, null, event);
        this.getEventListeners(event).register(new RegisteredListener(listener, executor, priority, plugin, ignoreCancelled));

        int priorityOrdinal = Math.min(priority.ordinal(), 4);
        var request = RegisterEventRequest.newBuilder().setEventType(event.getName()).setPluginName(plugin.getName()).setPriority(priorityOrdinal).setBlocking(true).build();
        NativeBridgeFfi.registerEvent(request);

    }

    @NotNull
    private HandlerList getEventListeners(@NotNull Class<? extends Event> type) {
        try {
            Method method = this.getRegistrationClass(type).getDeclaredMethod("getHandlerList");
            method.setAccessible(true);
            return (HandlerList) method.invoke(null);
        } catch (Exception e) {
            throw new IllegalPluginAccessException(e.toString());
        }
    }

    @NotNull
    private Class<? extends Event> getRegistrationClass(@NotNull Class<? extends Event> clazz) {
        try {
            clazz.getDeclaredMethod("getHandlerList");
            return clazz;
        } catch (NoSuchMethodException e) {
            if (clazz.getSuperclass() != null
                && !clazz.getSuperclass().equals(Event.class)
                && Event.class.isAssignableFrom(clazz.getSuperclass())) {
                return this.getRegistrationClass(clazz.getSuperclass().asSubclass(Event.class));
            } else {
                throw new IllegalPluginAccessException("Unable to find handler list for event " + clazz.getName() + ". Static getHandlerList method required!");
            }
        }
    }

    @NotNull
    public Map<Class<? extends Event>, Set<RegisteredListener>> createRegisteredListeners(@NotNull Listener listener, @NotNull final Plugin plugin) {
        Map<Class<? extends Event>, Set<RegisteredListener>> ret = new HashMap<>();

        Set<Method> methods;
        try {
            Class<?> listenerClazz = listener.getClass();
            methods = Sets.union(
                Set.of(listenerClazz.getMethods()),
                Set.of(listenerClazz.getDeclaredMethods())
            );
        } catch (NoClassDefFoundError e) {
            plugin.getLogger().severe("Failed to register events for " + listener.getClass() + " because " + e.getMessage() + " does not exist.");
            return ret;
        }

        for (final Method method : methods) {
            final EventHandler eh = method.getAnnotation(EventHandler.class);
            if (eh == null) continue;
            // Do not register bridge or synthetic methods to avoid event duplication
            // Fixes SPIGOT-893
            if (method.isBridge() || method.isSynthetic()) {
                continue;
            }
            final Class<?> checkClass;
            if (method.getParameterTypes().length != 1 || !Event.class.isAssignableFrom(checkClass = method.getParameterTypes()[0])) {
                plugin.getLogger().severe(plugin.getPluginMeta().getDisplayName() + " attempted to register an invalid EventHandler method signature \"" + method.toGenericString() + "\" in " + listener.getClass());
                continue;
            }
            final Class<? extends Event> eventClass = checkClass.asSubclass(Event.class);
            method.setAccessible(true);
            Set<RegisteredListener> eventSet = ret.computeIfAbsent(eventClass, k -> new HashSet<>());

            for (Class<?> clazz = eventClass; Event.class.isAssignableFrom(clazz); clazz = clazz.getSuperclass()) {
                // This loop checks for extending deprecated events
                if (clazz.getAnnotation(Deprecated.class) != null) {
                    Warning warning = clazz.getAnnotation(Warning.class);
                    Warning.WarningState warningState = this.server.getWarningState();
                    if (!warningState.printFor(warning)) {
                        break;
                    }
                    plugin.getLogger().log(
                        Level.WARNING,
                        String.format(
                            "\"%s\" has registered a listener for %s on method \"%s\", but the event is Deprecated. \"%s\"; please notify the authors %s.",
                            plugin.getPluginMeta().getDisplayName(),
                            clazz.getName(),
                            method.toGenericString(),
                            (warning != null && warning.reason().length() != 0) ? warning.reason() : "Server performance will be affected",
                            Arrays.toString(plugin.getPluginMeta().getAuthors().toArray())),
                        warningState == Warning.WarningState.ON ? new AuthorNagException(null) : null);
                    break;
                }
            }

            EventExecutor executor = new TimedEventExecutor(EventExecutor.create(method, eventClass), plugin, method, eventClass);
            eventSet.add(new RegisteredListener(listener, executor, eh.priority(), plugin, eh.ignoreCancelled()));
        }
        return ret;
    }

    public void clearEvents() {
        HandlerList.unregisterAll();
    }
}
