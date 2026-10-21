package org.patchbukkit.log;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.log.LogLevel;
import patchbukkit.log.SendLogRequest;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;

@Plugin(
        name = "NativeBridge",
        category = Core.CATEGORY_NAME,
        elementType = org.apache.logging.log4j.core.Appender.ELEMENT_TYPE,
        printObject = true
)
public class NativeBridgeLog4jAppender extends AbstractAppender {

    protected NativeBridgeLog4jAppender(
            String name,
            Filter filter,
            Layout<? extends Serializable> layout,
            boolean ignoreExceptions,
            Property[] properties
    ) {
        super(name, filter, layout, ignoreExceptions, properties);
    }

    @PluginFactory
    public static NativeBridgeLog4jAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout
    ) {
        if (name == null) {
            name = "NativeBridge";
        }
        return new NativeBridgeLog4jAppender(
                name,
                filter,
                layout,
                true,
                Property.EMPTY_ARRAY
        );
    }

    public static NativeBridgeLog4jAppender create(String name) {
        return createAppender(name, null, null);
    }

    @Override
    public void append(LogEvent event) {
        if (event == null) return;
        if (LogBridge.isLogging()) return;
        LogBridge.setLogging(true);
        try {
            String loggerName = event.getLoggerName();
            if (loggerName == null) {
                loggerName = "";
            }

            // Filter internal JDK / system loggers if below WARNING
            if (loggerName.startsWith("jdk.") || loggerName.startsWith("sun.") ||
                loggerName.startsWith("java.") || loggerName.startsWith("javax.")) {
                if (event.getLevel().intLevel() > Level.WARN.intLevel()) {
                    return;
                }
            }

            LogLevel logLevel = toLogLevel(event.getLevel());
            String message = event.getMessage() != null ? event.getMessage().getFormattedMessage() : "";
            Throwable thrown = event.getThrown();
            if (thrown != null) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                if (!message.isEmpty()) {
                    pw.println(message);
                }
                thrown.printStackTrace(pw);
                pw.flush();
                message = sw.toString();
            }

            try {
                if (org.patchbukkit.bridge.NativeFastPathFfi.isInitialized()) {
                    NativeBridgeFfi.sendLog(
                            SendLogRequest.newBuilder()
                                    .setLevel(logLevel)
                                    .setMessage(message)
                                    .setLoggerName(loggerName)
                                    .build()
                    );
                } else {
                    LogBridge.ORIGINAL_OUT.println("[" + event.getLevel() + "][" + loggerName + "] " + message);
                }
            } catch (Throwable t) {
                LogBridge.ORIGINAL_ERR.println("[" + event.getLevel() + "][" + loggerName + "] " + message);
            }
        } catch (Throwable t) {
            LogBridge.ORIGINAL_ERR.println("[NativeBridgeLog4jAppender Error] Failed to publish record: " + t.getMessage());
        } finally {
            LogBridge.setLogging(false);
        }
    }

    public static LogLevel toLogLevel(Level level) {
        if (level == null) return LogLevel.INFO;
        if (level.isMoreSpecificThan(Level.ERROR)) {
            return LogLevel.SEVERE;
        } else if (level.isMoreSpecificThan(Level.WARN)) {
            return LogLevel.WARNING;
        } else if (level.isMoreSpecificThan(Level.INFO)) {
            return LogLevel.INFO;
        } else if (level.isMoreSpecificThan(Level.DEBUG)) {
            return LogLevel.CONFIG;
        } else {
            return LogLevel.FINE;
        }
    }
}
