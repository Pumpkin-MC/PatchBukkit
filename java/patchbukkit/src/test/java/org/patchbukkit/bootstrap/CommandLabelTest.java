package org.patchbukkit.bootstrap;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandLabelTest {

    @Test
    void keepsOnlyTheCommandName() {
        assertEquals("/login", CommandBridge.commandLabel("/login secret"));
        assertEquals("/login", CommandBridge.commandLabel("login secret"));
        assertEquals("/register", CommandBridge.commandLabel("  /register secret secret  "));
        assertEquals("/login", CommandBridge.commandLabel("/login\tsecret"));
        assertEquals("/login", CommandBridge.commandLabel("/login secret"));
        assertEquals("/spawn", CommandBridge.commandLabel("/spawn"));
    }

    @Test
    void neverReturnsArgumentsForOddInput() {
        assertEquals("/", CommandBridge.commandLabel(null));
        assertEquals("/", CommandBridge.commandLabel(""));
        assertEquals("/", CommandBridge.commandLabel("/ login secret"));
    }

    @Test
    void redactedThrowableKeepsClassesAndFramesButNoMessages() {
        Throwable cause = new IllegalStateException("bad password secret");
        Throwable wrapped = new org.bukkit.command.CommandException("Unhandled exception executing 'login secret' in x", cause);
        StringWriter out = new StringWriter();
        CommandBridge.redacted(wrapped).printStackTrace(new PrintWriter(out));
        String log = out.toString();
        assertFalse(log.contains("secret"), log);
        assertTrue(log.contains("org.bukkit.command.CommandException"), log);
        assertTrue(log.contains("java.lang.IllegalStateException"), log);
        assertTrue(log.contains("CommandLabelTest"), log);
    }
}
