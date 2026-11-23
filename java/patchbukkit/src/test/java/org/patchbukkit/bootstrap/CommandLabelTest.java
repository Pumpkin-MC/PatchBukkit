package org.patchbukkit.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
