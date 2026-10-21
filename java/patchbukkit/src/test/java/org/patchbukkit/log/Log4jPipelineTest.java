package org.patchbukkit.log;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class Log4jPipelineTest {

    @Test
    public void testLog4jPipelineAndFiltering() {
        LogBridge.init();

        Logger rootLogger = (Logger) LogManager.getRootLogger();
        assertNotNull(rootLogger, "Root logger should be an instance of org.apache.logging.log4j.core.Logger");

        List<String> capturedMessages = new CopyOnWriteArrayList<>();
        List<String> capturedLoggers = new CopyOnWriteArrayList<>();

        Filter testFilter = new AbstractFilter() {
            @Override
            public Result filter(LogEvent event) {
                if (event != null) {
                    capturedLoggers.add(event.getLoggerName());
                    capturedMessages.add(event.getMessage().getFormattedMessage());
                }
                return Result.NEUTRAL;
            }
        };
        testFilter.start();
        rootLogger.addFilter(testFilter);

        try {
            // 1. Test direct Log4j logging
            LogManager.getLogger("MyLog4jPlugin").info("Direct log4j message 123");

            // 2. Test Java Util Logging (JUL) forwarding via ForwardLogHandler
            java.util.logging.Logger.getLogger("MyJulPlugin").info("JUL message 456");

            // 3. Test SLF4J logging via log4j-slf4j2-impl
            LoggerFactory.getLogger("MySlf4jPlugin").info("SLF4J message 789");

            // 4. Test Apache Commons Logging via LogFactory (used by HttpClient / Aether / CivModCore)
            org.apache.commons.logging.LogFactory.getLog("MyJclPlugin").info("JCL message 012");

            // Verify Log4j captured all logging mechanisms
            assertTrue(capturedLoggers.contains("MyLog4jPlugin"), "Log4j direct logger should be captured");
            assertTrue(capturedMessages.contains("Direct log4j message 123"), "Log4j direct message should be captured");

            assertTrue(capturedLoggers.contains("MyJulPlugin"), "JUL logger should be forwarded to Log4j");
            assertTrue(capturedMessages.contains("JUL message 456"), "JUL message should be forwarded to Log4j");

            assertTrue(capturedLoggers.contains("MySlf4jPlugin"), "SLF4J logger should be routed to Log4j");
            assertTrue(capturedMessages.contains("SLF4J message 789"), "SLF4J message should be routed to Log4j");

            assertTrue(capturedLoggers.contains("MyJclPlugin"), "JCL logger should be routed to Log4j");
            assertTrue(capturedMessages.contains("JCL message 012"), "JCL message should be routed to Log4j");
        } finally {
            testFilter.stop();
        }
    }
}
