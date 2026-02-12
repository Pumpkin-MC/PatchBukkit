package org.patchbukkit.testplugin.tests;

import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;
import org.bukkit.plugin.java.JavaPlugin;

@SuppressWarnings("deprecation")
public final class UnsafeValuesTests {

    private final JavaPlugin plugin;

    public UnsafeValuesTests(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @ConformanceTest(name = "UnsafeValues.getDataVersion() returns positive int", category = TestCategory.UNSAFE_VALUES)
    public void testGetDataVersion() {
        UnsafeValues unsafe = Bukkit.getUnsafe();
        int version = unsafe.getDataVersion();
        assertTrue(version > 0, "getDataVersion() should be > 0, got " + version);
    }

    @ConformanceTest(name = "UnsafeValues.isSupportedApiVersion(\"1.21\") returns true", category = TestCategory.UNSAFE_VALUES)
    public void testIsSupportedApiVersion121() {
        UnsafeValues unsafe = Bukkit.getUnsafe();
        boolean supported = unsafe.isSupportedApiVersion("1.21");
        assertTrue(supported, "isSupportedApiVersion(\"1.21\") should be true");
    }

    @ConformanceTest(name = "UnsafeValues.isSupportedApiVersion(\"1.20\") returns true", category = TestCategory.UNSAFE_VALUES)
    public void testIsSupportedApiVersion120() {
        UnsafeValues unsafe = Bukkit.getUnsafe();
        boolean supported = unsafe.isSupportedApiVersion("1.20");
        assertTrue(supported, "isSupportedApiVersion(\"1.20\") should be true");
    }

    @ConformanceTest(name = "UnsafeValues.isSupportedApiVersion(\"1.0\") returns false", category = TestCategory.UNSAFE_VALUES)
    public void testIsSupportedApiVersionOld() {
        UnsafeValues unsafe = Bukkit.getUnsafe();
        boolean supported = unsafe.isSupportedApiVersion("1.0");
        assertTrue(!supported, "isSupportedApiVersion(\"1.0\") should be false");
    }

    @ConformanceTest(name = "UnsafeValues.componentFlattener() returns non-null", category = TestCategory.UNSAFE_VALUES)
    public void testComponentFlattener() {
        UnsafeValues unsafe = Bukkit.getUnsafe();
        ComponentFlattener flattener = unsafe.componentFlattener();
        assertNotNull(flattener, "UnsafeValues.componentFlattener()");
    }

    private static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }

    private static void assertTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
