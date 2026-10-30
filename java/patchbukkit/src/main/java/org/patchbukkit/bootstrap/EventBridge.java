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

    public static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final Set<String> REGISTERED_EVENTS = ConcurrentHashMap.newKeySet();

    private EventBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            new ByteBuddy()
                .redefine(PaperPluginManagerImpl.class)
                .visit(Advice.to(CallEventAdvice.class).on(
                    ElementMatchers.named("callEvent").and(ElementMatchers.takesArguments(1))
                ))
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
                    .visit(Advice.to(CallEventAdvice.class).on(
                        ElementMatchers.named("callEvent").and(ElementMatchers.takesArguments(1))
                    ))
                    .visit(Advice.to(RegisterEventAdvice.class).on(
                        ElementMatchers.named("registerEvent").and(ElementMatchers.takesArguments(6))
                    ))
                    .visit(Advice.to(RegisterEventsAdvice.class).on(
                        ElementMatchers.named("registerEvents").and(ElementMatchers.takesArguments(2))
                    ))
                    .make()
                    .load(eventMgrClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable ignored) {}

            try {
                Class<?> rpehClass = Class.forName("io.papermc.paper.plugin.manager.RuntimePluginEntrypointHandler");
                new ByteBuddy()
                    .redefine(rpehClass)
                    .visit(Advice.to(RuntimeEntrypointAdvice.class).on(
                        ElementMatchers.named("register").and(ElementMatchers.takesArguments(2))
                    ))
                    .make()
                    .load(rpehClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

                Class<?> srppsClass = Class.forName("io.papermc.paper.plugin.manager.SingularRuntimePluginProviderStorage");
                new ByteBuddy()
                    .redefine(srppsClass)
                    .visit(Advice.to(RuntimePaperPluginAdvice.class).on(
                        ElementMatchers.named("register").and(ElementMatchers.takesArguments(1))
                    ))
                    .make()
                    .load(srppsClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

                Class<?> mrppsClass = Class.forName("io.papermc.paper.plugin.manager.MultiRuntimePluginProviderStorage");
                new ByteBuddy()
                    .redefine(mrppsClass)
                    .visit(Advice.to(RuntimePaperPluginAdvice.class).on(
                        ElementMatchers.named("register").and(ElementMatchers.takesArguments(1))
                    ))
                    .make()
                    .load(mrppsClass.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch Paper runtime plugin entrypoint handlers", t);
            }

            LOGGER.info("[PatchBukkit] Successfully patched Paper event manager with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to patch Paper event manager", t);
        }
    }

    public static class RuntimeEntrypointAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(@Advice.Argument(0) Object entrypoint) {
            // Paper's RuntimePluginEntrypointHandler normally rejects plugins with a bootstrapper (like Veinminer)
            // at runtime. By skipping the check on non-PLUGIN entrypoints, modern Paper plugins load smoothly.
            return !io.papermc.paper.plugin.entrypoint.Entrypoint.PLUGIN.equals(entrypoint);
        }
    }

    public static class RuntimePaperPluginAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(
            @Advice.This io.papermc.paper.plugin.storage.ServerPluginProviderStorage storage,
            @Advice.Argument(0) io.papermc.paper.plugin.provider.PluginProvider<?> provider
        ) {
            return EventBridge.registerPaperPlugin(storage, provider);
        }
    }

    public static boolean registerPaperPlugin(
        io.papermc.paper.plugin.storage.ServerPluginProviderStorage storage,
        io.papermc.paper.plugin.provider.PluginProvider<?> provider
    ) {
        // Paper's Singular/MultiRuntimePluginProviderStorage normally rejects or skips modern Paper plugins at runtime.
        // By performing the registration directly, Paper plugins (e.g. Veinminer) can load cleanly.
        if (provider != null && provider.getClass().getName().contains("PaperServerPluginProvider")) {
            try {
                // 1. Add provider to the `providers` list on SimpleProviderStorage
                for (Class<?> c = storage.getClass(); c != null; c = c.getSuperclass()) {
                    try {
                        java.lang.reflect.Field providersField = c.getDeclaredField("providers");
                        providersField.setAccessible(true);
                        java.util.List list = (java.util.List) providersField.get(storage);
                        if (list != null) {
                            list.add(provider);
                        }
                        break;
                    } catch (NoSuchFieldException ignored) {}
                }

                // 2. Set `lastProvider` field on SingularRuntimePluginProviderStorage if present
                try {
                    java.lang.reflect.Field lpField = storage.getClass().getDeclaredField("lastProvider");
                    lpField.setAccessible(true);
                    lpField.set(storage, provider);
                } catch (NoSuchFieldException ignored) {}

                // 3. Register into Paper's LaunchEntryPointHandler
                io.papermc.paper.plugin.entrypoint.LaunchEntryPointHandler.INSTANCE.register(
                    io.papermc.paper.plugin.entrypoint.Entrypoint.PLUGIN,
                    (io.papermc.paper.plugin.provider.PluginProvider) provider
                );
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register Paper plugin provider", t);
            }
            return true;
        }
        return false;
    }

    public static class CallEventAdvice {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean onEnter(@Advice.Argument(0) Event event) {
            EventBridge.dispatchPluginEvent(event);
            return true;
        }
    }

    public static void dispatchPluginEvent(Event event) {
        if (event == null) return;
        for (org.bukkit.plugin.RegisteredListener listener : event.getHandlers().getRegisteredListeners()) {
            if (!listener.getPlugin().isEnabled()) {
                continue;
            }
            try {
                listener.callEvent(event);
            } catch (Throwable ex) {
                LOGGER.log(
                    Level.SEVERE,
                    "Could not pass event " + event.getEventName() + " to " + listener.getPlugin().getName(),
                    ex
                );
            }
        }
        if (org.patchbukkit.bridge.NativeFastPathFfi.isInitialized()) {
            try {
                NativeBridgeFfi.callEvent(patchbukkit.events.CallEventRequest.getDefaultInstance());
            } catch (Throwable ignored) {}
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
        if (REGISTERED_EVENTS.add(eventName)) {
            var request = RegisterEventRequest.newBuilder()
                .setEventType(eventName)
                .setPluginName("")
                .setPriority(Math.min(priority, 4))
                .setBlocking(true)
                .build();
            try {
                NativeBridgeFfi.registerEvent(request);
                LOGGER.info("[PatchBukkit] Registered bridge event: " + eventName + " (triggered by " + pluginName + ")");
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to register bridge event: " + eventName, t);
            }
        }
    }

    public static void init(net.minecraft.server.dedicated.DedicatedServer server) {
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
