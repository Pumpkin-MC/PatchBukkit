package org.patchbukkit.testplugin;

public final class TestAssertions {

    private TestAssertions() {}

    public static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }

    public static void assertNull(Object value, String what) {
        if (value != null) throw new AssertionError(what + " expected null but got " + value);
    }

    public static void assertTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }
}
