package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.bukkit.plugin.java.JavaPlugin;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class ServicesManagerTests {

    private final JavaPlugin plugin;

    public ServicesManagerTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "Server.getServicesManager() returns non-null", category = TestCategory.SERVICES_MANAGER)
    public void testServicesManagerNotNull() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        assertNotNull(sm, "Server.getServicesManager()");
    }

    @ConformanceTest(name = "ServicesManager.register() + load() roundtrip", category = TestCategory.SERVICES_MANAGER)
    public void testRegisterAndLoad() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        sm.register(Runnable.class, () -> {}, plugin, ServicePriority.Normal);
        Runnable loaded = sm.load(Runnable.class);
        assertNotNull(loaded, "ServicesManager.load() after register()");
        sm.unregister(Runnable.class, loaded);
    }

    @ConformanceTest(name = "ServicesManager.isProvidedFor() returns true after register", category = TestCategory.SERVICES_MANAGER)
    public void testIsProvidedFor() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        Runnable provider = () -> {};
        sm.register(Runnable.class, provider, plugin, ServicePriority.Normal);
        assertTrue(sm.isProvidedFor(Runnable.class), "isProvidedFor() must be true after register()");
        sm.unregister(Runnable.class, provider);
    }

    @ConformanceTest(name = "ServicesManager.unregister() removes provider", category = TestCategory.SERVICES_MANAGER)
    public void testUnregister() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        Runnable provider = () -> {};
        sm.register(Runnable.class, provider, plugin, ServicePriority.Normal);
        sm.unregister(Runnable.class, provider);
        assertTrue(!sm.isProvidedFor(Runnable.class), "isProvidedFor() must be false after unregister()");
    }

    @ConformanceTest(name = "ServicesManager priority ordering is respected", category = TestCategory.SERVICES_MANAGER)
    public void testPriorityOrdering() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        Runnable low = () -> {};
        Runnable high = () -> {};
        sm.register(Runnable.class, low, plugin, ServicePriority.Lowest);
        sm.register(Runnable.class, high, plugin, ServicePriority.Highest);
        Runnable loaded = sm.load(Runnable.class);
        assertTrue(loaded == high, "Highest priority provider must be returned by load()");
        sm.unregisterAll(plugin);
    }

    @ConformanceTest(name = "ServicesManager.getRegistration() returns correct provider", category = TestCategory.SERVICES_MANAGER)
    public void testGetRegistration() {
        ServicesManager sm = Bukkit.getServer().getServicesManager();
        Runnable provider = () -> {};
        sm.register(Runnable.class, provider, plugin, ServicePriority.Normal);
        RegisteredServiceProvider<Runnable> rsp = sm.getRegistration(Runnable.class);
        assertNotNull(rsp, "getRegistration() must return non-null after register()");
        assertTrue(rsp.getProvider() == provider, "getRegistration().getProvider() must match registered instance");
        sm.unregisterAll(plugin);
    }
}
