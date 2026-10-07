package org.patchbukkit.bootstrap;

/**
 * ByteBuddy method delegation interceptor for {@code ca.spottedleaf.moonrise.common.util.TickThread#isTickThread()}.
 *
 * <p>Allows PatchBukkit's worker thread and test execution threads to be recognized as
 * valid primary tick threads by Paper's main-thread assertions.
 */
public final class TickThreadInterceptor {

    private TickThreadInterceptor() {}

    public static boolean isTickThread() {
        Thread thread = Thread.currentThread();
        return (thread instanceof ca.spottedleaf.moonrise.common.util.TickThread)
            || HeadlessPaperServer.isMainThread(thread);
    }
}
