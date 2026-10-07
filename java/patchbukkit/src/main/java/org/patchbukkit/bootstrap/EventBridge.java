package org.patchbukkit.bootstrap;

import io.papermc.paper.plugin.manager.PaperPluginManagerImpl;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.events.RegisterEventRequest;

/**
 * Bridges Bukkit/Paper event listeners to Pumpkin's plugin/event manager.
 *
 * <p>Paper's event manager is intercepted via ByteBuddy so that whenever a plugin
 * registers an event handler (either via {@code registerEvents} or {@code registerEvent}),
 * a corresponding registration is sent to Pumpkin via FFI.
 *
 * <p>When Pumpkin fires an event, {@link org.patchbukkit.events.PatchBukkitEventFactory}
 * instantiates authentic event classes and dispatches them through Paper's event handlers.
 */
public final class EventBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final Set<String> REGISTERED_EVENTS = ConcurrentHashMap.newKeySet();

    private EventBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            new ByteBuddy()
                .redefine(PaperPluginManagerImpl.class)
                .visit(Advice.to(RegisterEventAdvice.class).on(
                    ElementMatchers.named("registerEvent").and(ElementMatchers.takesArguments(6))
                ))
                .visit(Advice.to(RegisterEventsAdvice.class).on(
                    ElementMatchers.named("registerEvents").and(ElementMatchers.takesArguments(2))
                ))
                .make()
                .load(PaperPluginManagerImpl.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            try {
                Class<?> eventMgrClass = Class.forName("io.papermc.paper.plugin.manager.PaperEventManager");
                new ByteBuddy()
                    .redefine(eventMgrClass)
                    .visit(Advice.to(RegisterEventAdvice.class).on(
                        ElementMatchers.named("registerEvent").and(ElementMatchers.takesArguments(6))
                    ))
                    .visit(Advice.to(RegisterEventsAdvice.class).on(
                        ElementMatchers.named("registerEvents").and(ElementMatchers.takesArguments(2))
                    ))
                    .make()
                    .load(eventMgrClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable ignored) {}

            LOGGER.info("[PatchBukkit] Successfully patched Paper event manager with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch Paper event manager", t);
        }
    }

    public static class RegisterEventAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) Class<?> eventClass,
            @Advice.Argument(2) EventPriority priority,
            @Advice.Argument(4) Plugin plugin
        ) {
            if (eventClass != null && plugin != null) {
                EventBridge.onEventRegistered(
                    eventClass.getName(),
                    plugin.getName(),
                    priority != null ? priority.ordinal() : 0
                );
            }
        }
    }

    public static class RegisterEventsAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.Argument(0) Listener listener,
            @Advice.Argument(1) Plugin plugin
        ) {
            if (listener != null && plugin != null) {
                EventBridge.onEventsRegistered(listener, plugin);
            }
        }
    }

    public static void onEventsRegistered(Listener listener, Plugin plugin) {
        if (listener == null || plugin == null) return;
        for (Class<?> clazz = listener.getClass(); clazz != null && clazz != Object.class; clazz = clazz.getSuperclass()) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.isAnnotationPresent(EventHandler.class)) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params.length == 1 && Event.class.isAssignableFrom(params[0])) {
                        EventHandler eh = m.getAnnotation(EventHandler.class);
                        int priority = eh != null && eh.priority() != null ? eh.priority().ordinal() : 0;
                        onEventRegistered(params[0].getName(), plugin.getName(), priority);
                    }
                }
            }
        }
    }

    public static void onEventRegistered(String eventName, String pluginName, int priority) {
        if (eventName == null || pluginName == null) return;
        String key = pluginName + ":" + eventName;
        if (REGISTERED_EVENTS.add(key)) {
            var request = RegisterEventRequest.newBuilder()
                .setEventType(eventName)
                .setPluginName(pluginName)
                .setPriority(Math.min(priority, 4))
                .setBlocking(true)
                .build();
            try {
                NativeBridgeFfi.registerEvent(request);
                LOGGER.info("[PatchBukkit] Registered bridge event: " + key);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to register bridge event: " + key, t);
            }
        }
    }

    public static void init(PumpkinDedicatedServer server) {
        // Ensure ServerTickStartEvent is registered with Pumpkin so Pumpkin ticks the JVM worker
        var request = RegisterEventRequest.newBuilder()
            .setEventType("com.destroystokyo.paper.event.server.ServerTickStartEvent")
            .setPluginName("PatchBukkit")
            .setPriority(0)
            .setBlocking(true)
            .build();
        try {
            NativeBridgeFfi.registerEvent(request);
            LOGGER.info("[PatchBukkit] Registered ServerTickStartEvent bridge with Pumpkin");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "Failed to register ServerTickStartEvent with Pumpkin", t);
        }
    }
}
