package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.EventExecutor;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.bukkit.plugin.java.JavaPlugin;

public final class EventSystemTests {

    private final JavaPlugin plugin;

    public EventSystemTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "registerEvents() with annotated listener", category = TestCategory.EVENT_SYSTEM)
    public void testRegisterEvents() {
        Listener listener = new Listener() {
            @EventHandler
            public void onPluginEnable(PluginEnableEvent event) {
                // no-op, just verifying registration doesn't throw
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    @ConformanceTest(name = "registerEvent() with explicit priority", category = TestCategory.EVENT_SYSTEM)
    public void testRegisterEventWithPriority() {
        EventExecutor executor = (listener, event) -> {
            // no-op
        };
        Bukkit.getPluginManager().registerEvent(
                PluginEnableEvent.class,
                new Listener() {},
                EventPriority.HIGH,
                executor,
                plugin
        );
    }

    @ConformanceTest(name = "registerEvent() with ignoreCancelled flag", category = TestCategory.EVENT_SYSTEM)
    public void testRegisterEventIgnoreCancelled() {
        EventExecutor executor = (listener, event) -> {
            // no-op
        };
        Bukkit.getPluginManager().registerEvent(
                PluginEnableEvent.class,
                new Listener() {},
                EventPriority.NORMAL,
                executor,
                plugin,
                true
        );
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }
}
