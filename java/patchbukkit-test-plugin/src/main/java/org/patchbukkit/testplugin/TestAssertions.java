package org.patchbukkit.testplugin;

public final class TestAssertions {

    private TestAssertions() {}

    public static void assertNotNull(Object value, String what) {
        if (value == null) throw new AssertionError(what + " returned null");
    }

    public static void assertTrue(boolean condition, String what) {
        if (!condition) throw new AssertionError(what);
    }

    public static void assertFalse(boolean condition, String what) {
        if (condition) throw new AssertionError(what);
    }

    public static void assertEquals(Object expected, Object actual, String what) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void assertEquals(double expected, double actual, String what) {
        if (Math.abs(expected - actual) > 1e-4) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
