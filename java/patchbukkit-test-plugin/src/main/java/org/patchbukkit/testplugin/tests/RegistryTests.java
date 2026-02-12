package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.patchbukkit.testplugin.TestExpectation;
import org.bukkit.plugin.java.JavaPlugin;

public final class RegistryTests {

    private final JavaPlugin plugin;

    public RegistryTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "Registry.SOUNDS is accessible", category = TestCategory.REGISTRY)
    public void testSoundsRegistryExists() {
        Registry<Sound> sounds = Registry.SOUNDS;
        assertNotNull(sounds, "Registry.SOUNDS");
    }

    @ConformanceTest(name = "Registry.SOUNDS.iterator() works", category = TestCategory.REGISTRY)
    public void testSoundsIteration() {
        Registry<Sound> sounds = Registry.SOUNDS;
        assertNotNull(sounds, "Registry.SOUNDS");
        var iterator = sounds.iterator();
        assertNotNull(iterator, "Registry.SOUNDS.iterator()");
    }

    @ConformanceTest(name = "Registry.SOUNDS.stream() works", category = TestCategory.REGISTRY)
    public void testSoundsStream() {
        Registry<Sound> sounds = Registry.SOUNDS;
        assertNotNull(sounds, "Registry.SOUNDS");
        var stream = sounds.stream();
        assertNotNull(stream, "Registry.SOUNDS.stream()");
    }

    @ConformanceTest(name = "Server.getRegistry(Sound.class) returns registry", category = TestCategory.REGISTRY,
            expectation = TestExpectation.EXPECT_UNSUPPORTED)
    public void testServerGetRegistry() {
        // Server.getRegistry() returns null in PatchBukkit — might throw or return null
        Registry<Sound> reg = Bukkit.getServer().getRegistry(Sound.class);
        if (reg == null) {
            throw new UnsupportedOperationException("Server.getRegistry() returned null (not implemented)");
        }
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }
}
