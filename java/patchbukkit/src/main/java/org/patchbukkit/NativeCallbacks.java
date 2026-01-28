// No longer used currently, keeping around for reference in the short term
package org.patchbukkit;

import org.astonbitecode.j4rs.api.invocation.NativeCallbackToRustChannelSupport;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class NativeCallbacks extends NativeCallbackToRustChannelSupport {

    private static NativeCallbacks instance;

    public NativeCallbacks() {
        NativeCallbacks.setInstance(this);
    }

    private static synchronized void setInstance(
        NativeCallbacks nativeCallbacks
    ) {
        if (instance == null) {
            instance = nativeCallbacks;
        }
    }

    public static NativeCallbacks getInstance() {
        return instance;
    }

    public void registerEventCallback(
        @NotNull Listener listener,
        @NotNull Plugin plugin
    ) {
        doCallback(
            new CallbackValue(
                "REGISTER_EVENT_CALLBACK",
                new Object[] { listener, plugin }
            )
        );
    }
}
