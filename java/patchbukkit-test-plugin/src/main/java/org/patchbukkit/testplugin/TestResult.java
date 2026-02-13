package org.patchbukkit.testplugin;

public record TestResult(
    String name,
    TestCategory category,
    TestExpectation expectation,
    boolean passed,
    String detail
) {
    public String tag() {
        return expectation == TestExpectation.SHOULD_WORK ? "IMPL" : "STUB";
    }
}
