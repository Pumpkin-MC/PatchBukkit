package org.patchbukkit.testplugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;

public final class TestFramework {

    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String RED     = "\u001B[31m";
    private static final String GREEN   = "\u001B[32m";
    private static final String YELLOW  = "\u001B[33m";
    private static final String CYAN    = "\u001B[36m";
    private static final String WHITE   = "\u001B[37m";

    private final Logger logger;
    private final List<Object> suites = new ArrayList<>();

    public TestFramework(Logger logger) {
        this.logger = logger;
    }

    public void registerSuite(Object suite) {
        suites.add(suite);
    }

    public List<TestResult> runAll() {
        List<TestResult> results = new ArrayList<>();
        for (Object suite : suites) {
            results.addAll(runSuite(suite));
        }
        return results;
    }

    public List<TestResult> runCategory(TestCategory category) {
        List<TestResult> results = new ArrayList<>();
        for (Object suite : suites) {
            for (Method method : suite.getClass().getDeclaredMethods()) {
                ConformanceTest ann = method.getAnnotation(ConformanceTest.class);
                if (ann != null && ann.category() == category) {
                    results.add(runTest(suite, method, ann));
                }
            }
        }
        return results;
    }

    private List<TestResult> runSuite(Object suite) {
        List<TestResult> results = new ArrayList<>();
        for (Method method : suite.getClass().getDeclaredMethods()) {
            ConformanceTest ann = method.getAnnotation(ConformanceTest.class);
            if (ann != null) {
                results.add(runTest(suite, method, ann));
            }
        }
        return results;
    }

    private TestResult runTest(Object suite, Method method, ConformanceTest ann) {
        try {
            method.setAccessible(true);
            method.invoke(suite);

            if (ann.expectation() == TestExpectation.SHOULD_WORK) {
                return new TestResult(ann.name(), ann.category(), ann.expectation(), true, null);
            } else {
                return new TestResult(ann.name(), ann.category(), ann.expectation(), false,
                        "Expected UnsupportedOperationException but method succeeded");
            }
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;

            if (ann.expectation() == TestExpectation.EXPECT_UNSUPPORTED) {
                if (cause instanceof UnsupportedOperationException) {
                    return new TestResult(ann.name(), ann.category(), ann.expectation(), true, null);
                } else {
                    return new TestResult(ann.name(), ann.category(), ann.expectation(), false,
                            "Expected UnsupportedOperationException but got " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
                }
            } else {
                return new TestResult(ann.name(), ann.category(), ann.expectation(), false,
                        cause.getClass().getSimpleName() + ": " + cause.getMessage());
            }
        }
    }

    public void reportResults(List<TestResult> results) {
        reportResults(results, logger::info);
        logger.info("[PBTEST_SUMMARY] total=" + results.size()
                + " passed=" + results.stream().filter(TestResult::passed).count()
                + " failed=" + results.stream().filter(r -> !r.passed()).count());
    }

    public void reportResults(List<TestResult> results, CommandSender sender) {
        reportResults(results, sender::sendMessage);
    }

    private void reportResults(List<TestResult> results, Consumer<String> out) {
        Map<TestCategory, List<TestResult>> grouped = new EnumMap<>(TestCategory.class);
        for (TestResult r : results) {
            grouped.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(r);
        }

        int total = results.size();
        int passed = (int) results.stream().filter(TestResult::passed).count();
        int failed = total - passed;

        out.accept(CYAN + BOLD + "========================================" + RESET);
        out.accept(CYAN + BOLD + "  PatchBukkit Conformance Test Results" + RESET);
        out.accept(CYAN + BOLD + "========================================" + RESET);

        for (TestCategory cat : TestCategory.values()) {
            List<TestResult> catResults = grouped.get(cat);
            if (catResults == null || catResults.isEmpty()) continue;

            out.accept(YELLOW + BOLD + "--- " + cat.name() + " ---" + RESET);
            for (TestResult r : catResults) {
                if (r.passed()) {
                    out.accept(GREEN + "  PASS" + RESET + " [" + r.tag() + "] " + r.name());
                } else {
                    out.accept(RED + "  FAIL" + RESET + " [" + r.tag() + "] " + r.name() + RED + " -- " + r.detail() + RESET);
                }
            }
        }

        out.accept(CYAN + BOLD + "========================================" + RESET);
        String summaryColor = failed == 0 ? GREEN : RED;
        out.accept(BOLD + "  Total: " + WHITE + total + RESET
                + BOLD + "  Passed: " + GREEN + passed + RESET
                + BOLD + "  Failed: " + summaryColor + failed + RESET);
        out.accept(CYAN + BOLD + "========================================" + RESET);
    }
}
