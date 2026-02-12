package org.patchbukkit.testplugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.command.CommandSender;

public final class TestFramework {

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

    // ANSI color codes
    private static final String RESET   = "\u001B[0m";
    private static final String BOLD    = "\u001B[1m";
    private static final String RED     = "\u001B[31m";
    private static final String GREEN   = "\u001B[32m";
    private static final String YELLOW  = "\u001B[33m";
    private static final String CYAN    = "\u001B[36m";
    private static final String WHITE   = "\u001B[37m";

    public void reportResults(List<TestResult> results) {
        Map<TestCategory, List<TestResult>> grouped = new EnumMap<>(TestCategory.class);
        for (TestResult r : results) {
            grouped.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(r);
        }

        int total = results.size();
        int passed = 0;
        for (TestResult r : results) {
            if (r.passed()) passed++;
        }
        int failed = total - passed;

        logger.info(CYAN + BOLD + "========================================" + RESET);
        logger.info(CYAN + BOLD + "  PatchBukkit Conformance Test Results" + RESET);
        logger.info(CYAN + BOLD + "========================================" + RESET);

        for (TestCategory cat : TestCategory.values()) {
            List<TestResult> catResults = grouped.get(cat);
            if (catResults == null || catResults.isEmpty()) continue;

            logger.info(YELLOW + BOLD + "--- " + cat.name() + " ---" + RESET);
            for (TestResult r : catResults) {
                if (r.passed()) {
                    logger.info(GREEN + "  PASS" + RESET + " [" + r.tag() + "] " + r.name());
                } else {
                    logger.info(RED + "  FAIL" + RESET + " [" + r.tag() + "] " + r.name() + RED + " -- " + r.detail() + RESET);
                }
            }
        }

        logger.info(CYAN + BOLD + "========================================" + RESET);
        String summaryColor = failed == 0 ? GREEN : RED;
        logger.info(BOLD + "  Total: " + WHITE + total + RESET
                + BOLD + "  Passed: " + GREEN + passed + RESET
                + BOLD + "  Failed: " + summaryColor + failed + RESET);
        logger.info(CYAN + BOLD + "========================================" + RESET);
        logger.info("[PBTEST_SUMMARY] total=" + total + " passed=" + passed + " failed=" + failed);
    }

    public void reportResults(List<TestResult> results, CommandSender sender) {
        Map<TestCategory, List<TestResult>> grouped = new EnumMap<>(TestCategory.class);
        for (TestResult r : results) {
            grouped.computeIfAbsent(r.category(), k -> new ArrayList<>()).add(r);
        }

        int total = results.size();
        int passed = 0;
        for (TestResult r : results) {
            if (r.passed()) passed++;
        }
        int failed = total - passed;

        sender.sendMessage(CYAN + BOLD + "========================================" + RESET);
        sender.sendMessage(CYAN + BOLD + "  PatchBukkit Conformance Test Results" + RESET);
        sender.sendMessage(CYAN + BOLD + "========================================" + RESET);

        for (TestCategory cat : TestCategory.values()) {
            List<TestResult> catResults = grouped.get(cat);
            if (catResults == null || catResults.isEmpty()) continue;

            sender.sendMessage(YELLOW + BOLD + "--- " + cat.name() + " ---" + RESET);
            for (TestResult r : catResults) {
                if (r.passed()) {
                    sender.sendMessage(GREEN + "  PASS" + RESET + " [" + r.tag() + "] " + r.name());
                } else {
                    sender.sendMessage(RED + "  FAIL" + RESET + " [" + r.tag() + "] " + r.name() + RED + " -- " + r.detail() + RESET);
                }
            }
        }

        sender.sendMessage(CYAN + BOLD + "========================================" + RESET);
        String summaryColor = failed == 0 ? GREEN : RED;
        sender.sendMessage(BOLD + "  Total: " + WHITE + total + RESET
                + BOLD + "  Passed: " + GREEN + passed + RESET
                + BOLD + "  Failed: " + summaryColor + failed + RESET);
        sender.sendMessage(CYAN + BOLD + "========================================" + RESET);
    }
}
