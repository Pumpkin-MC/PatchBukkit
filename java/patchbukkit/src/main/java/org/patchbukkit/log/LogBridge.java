package org.patchbukkit.log;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.log.LogLevel;
import patchbukkit.log.SendLogRequest;

public final class LogBridge {

    public static final PrintStream ORIGINAL_OUT = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true);
    public static final PrintStream ORIGINAL_ERR = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.err), true);
    private static final ThreadLocal<Boolean> IN_LOGGING = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static volatile boolean initialized = false;

    public static final Map<Level, LogLevel> LEVEL_MAP = Map.of(
        Level.SEVERE,  LogLevel.SEVERE,
        Level.WARNING, LogLevel.WARNING,
        Level.INFO,    LogLevel.INFO,
        Level.CONFIG,  LogLevel.CONFIG,
        Level.FINE,    LogLevel.FINE,
        Level.FINER,   LogLevel.FINER,
        Level.FINEST,  LogLevel.FINEST
    );

    private LogBridge() {}

    public static boolean isLogging() {
        return IN_LOGGING.get();
    }

    public static void setLogging(boolean logging) {
        IN_LOGGING.set(logging);
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        configureLog4j();
        configureJulLogger();
        redirectSystemStreams();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Logger.getLogger("Minecraft").log(Level.SEVERE, "Uncaught exception in thread " + thread.getName(), throwable);
        });
    }

    private static void configureLog4j() {
        try {
            org.apache.logging.log4j.core.LoggerContext context =
                (org.apache.logging.log4j.core.LoggerContext) org.apache.logging.log4j.LogManager.getContext(false);
            org.apache.logging.log4j.core.config.Configuration config = context.getConfiguration();

            NativeBridgeLog4jAppender appender = NativeBridgeLog4jAppender.create("NativeBridge");
            appender.start();
            config.addAppender(appender);

            org.apache.logging.log4j.core.config.LoggerConfig rootConfig = config.getRootLogger();
            rootConfig.removeAppender("TerminalConsole");
            rootConfig.removeAppender("ServerGuiConsole");
            rootConfig.removeAppender("Async");

            org.apache.logging.log4j.Level targetLevel = getEffectiveLog4jLevel();
            rootConfig.addAppender(appender, targetLevel, null);
            rootConfig.setLevel(targetLevel);
            context.updateLoggers();
        } catch (Throwable t) {
            ORIGINAL_ERR.println("[PatchBukkit] Failed to initialize Log4j native bridge appender: " + t.getMessage());
        }
    }

    public static org.apache.logging.log4j.Level getEffectiveLog4jLevel() {
        try {
            var cfg = NativeBridgeFfi.getPatchBukkitConfig(patchbukkit.common.EmptyRequest.newBuilder().build());
            if (cfg != null) {
                if (cfg.getDebugBridge()) {
                    return org.apache.logging.log4j.Level.ALL;
                }
                String level = cfg.getLogLevel();
                if (level != null && !level.isBlank()) {
                    return switch (level.toLowerCase(Locale.ENGLISH).trim()) {
                        case "trace" -> org.apache.logging.log4j.Level.TRACE;
                        case "debug" -> org.apache.logging.log4j.Level.DEBUG;
                        case "warn", "warning" -> org.apache.logging.log4j.Level.WARN;
                        case "error", "severe" -> org.apache.logging.log4j.Level.ERROR;
                        case "off" -> org.apache.logging.log4j.Level.OFF;
                        case "all" -> org.apache.logging.log4j.Level.ALL;
                        default -> org.apache.logging.log4j.Level.INFO;
                    };
                }
            }
        } catch (Throwable ignored) {}
        return org.apache.logging.log4j.Level.INFO;
    }

    public static Level getEffectiveJulLevel() {
        try {
            var cfg = NativeBridgeFfi.getPatchBukkitConfig(patchbukkit.common.EmptyRequest.newBuilder().build());
            if (cfg != null) {
                if (cfg.getDebugBridge()) {
                    return Level.ALL;
                }
                String level = cfg.getLogLevel();
                if (level != null && !level.isBlank()) {
                    return switch (level.toLowerCase(Locale.ENGLISH).trim()) {
                        case "trace" -> Level.FINEST;
                        case "debug" -> Level.FINE;
                        case "warn", "warning" -> Level.WARNING;
                        case "error", "severe" -> Level.SEVERE;
                        case "off" -> Level.OFF;
                        case "all" -> Level.ALL;
                        default -> Level.INFO;
                    };
                }
            }
        } catch (Throwable ignored) {}
        return Level.INFO;
    }

    private static void configureJulLogger() {
        Logger root = Logger.getLogger("");
        root.setUseParentHandlers(false);
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
        root.setLevel(getEffectiveJulLevel());
        try {
            root.addHandler(new org.bukkit.craftbukkit.util.ForwardLogHandler());
        } catch (Throwable t) {
            ORIGINAL_ERR.println("[PatchBukkit] Failed to install ForwardLogHandler, using fallback: " + t.getMessage());
            root.addHandler(new java.util.logging.Handler() {
                @Override
                public void publish(java.util.logging.LogRecord record) {
                    if (record == null) return;
                    if (IN_LOGGING.get()) return;
                    IN_LOGGING.set(true);
                    try {
                        LogLevel logLevel = LEVEL_MAP.getOrDefault(record.getLevel(), LogLevel.INFO);
                        String message = formatLogRecord(record);
                        String loggerName = record.getLoggerName() != null ? record.getLoggerName() : "";
                        NativeBridgeFfi.sendLog(
                            SendLogRequest.newBuilder()
                                .setLevel(logLevel)
                                .setMessage(message)
                                .setLoggerName(loggerName)
                                .build()
                        );
                    } catch (Throwable ignored) {
                    } finally {
                        IN_LOGGING.set(false);
                    }
                }
                @Override public void flush() {}
                @Override public void close() {}
            });
        }
    }

    public static String formatLogRecord(java.util.logging.LogRecord record) {
        String msg = record.getMessage();
        if (msg == null) {
            msg = "";
        } else if (record.getResourceBundle() != null) {
            try {
                msg = record.getResourceBundle().getString(msg);
            } catch (Exception ignored) {}
        }
        Object[] params = record.getParameters();
        if (params != null && params.length > 0 && msg.contains("{0}")) {
            try {
                msg = MessageFormat.format(msg, params);
            } catch (Exception ignored) {}
        }
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            if (!msg.isEmpty()) {
                pw.println(msg);
            }
            record.getThrown().printStackTrace(pw);
            pw.flush();
            msg = sw.toString();
        }
        return msg;
    }

    private static void redirectSystemStreams() {
        try {
            System.setOut(new LoggingPrintStream(ORIGINAL_OUT, Level.INFO, "STDOUT"));
            System.setErr(new LoggingPrintStream(ORIGINAL_ERR, Level.SEVERE, "STDERR"));
        } catch (Throwable t) {
            ORIGINAL_ERR.println("[PatchBukkit] Failed to redirect System streams: " + t.getMessage());
        }
    }

    private static class LoggingPrintStream extends PrintStream {
        private final PrintStream delegate;
        private final Level level;
        private final String loggerName;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public LoggingPrintStream(PrintStream delegate, Level level, String loggerName) {
            super(delegate, true);
            this.delegate = delegate;
            this.level = level;
            this.loggerName = loggerName;
        }

        @Override
        public void write(int b) {
            delegate.write(b);
            if (b == '\n') {
                flushBuffer();
            } else if (b != '\r') {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] buf, int off, int len) {
            delegate.write(buf, off, len);
            for (int i = off; i < off + len; i++) {
                byte b = buf[i];
                if (b == '\n') {
                    flushBuffer();
                } else if (b != '\r') {
                    buffer.write(b);
                }
            }
        }

        private synchronized void flushBuffer() {
            if (buffer.size() == 0) return;
            String line = buffer.toString(StandardCharsets.UTF_8);
            buffer.reset();
            if (line.isEmpty() || IN_LOGGING.get()) return;
            IN_LOGGING.set(true);
            try {
                org.apache.logging.log4j.Logger log4jLogger = org.apache.logging.log4j.LogManager.getLogger(loggerName);
                if (level == Level.SEVERE) {
                    log4jLogger.error(line);
                } else if (level == Level.WARNING) {
                    log4jLogger.warn(line);
                } else {
                    log4jLogger.info(line);
                }
            } catch (Throwable t) {
                try {
                    LogLevel logLevel = LEVEL_MAP.getOrDefault(level, LogLevel.INFO);
                    NativeBridgeFfi.sendLog(
                        SendLogRequest.newBuilder()
                            .setLevel(logLevel)
                            .setMessage(line)
                            .setLoggerName(loggerName)
                            .build()
                    );
                } catch (Throwable ignored) {}
            } finally {
                IN_LOGGING.set(false);
            }
        }
    }
}
