package org.patchbukkit.testplugin.tests;

import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import static org.patchbukkit.testplugin.TestAssertions.*;

public final class RegistryTests {

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

    @ConformanceTest(name = "Server.getRegistry(Sound.class) returns registry", category = TestCategory.REGISTRY)
    public void testServerGetRegistry() {
        Registry<Sound> reg = Bukkit.getServer().getRegistry(Sound.class);
        assertNotNull(reg, "Server.getRegistry(Sound.class)");
    }

}
