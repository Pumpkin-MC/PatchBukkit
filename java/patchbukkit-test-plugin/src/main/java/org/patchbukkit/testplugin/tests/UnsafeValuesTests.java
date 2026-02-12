package org.patchbukkit.testplugin.tests;

import net.kyori.adventure.text.flattener.ComponentFlattener;
import org.bukkit.Bukkit;
import org.bukkit.UnsafeValues;
import org.patchbukkit.testplugin.ConformanceTest;
import org.patchbukkit.testplugin.TestCategory;

import static org.patchbukkit.testplugin.TestAssertions.*;

@SuppressWarnings("deprecation")
public final class UnsafeValuesTests {

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

}
