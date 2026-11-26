package org.patchbukkit.bootstrap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;

/**
 * Central coordinator for ByteBuddy class redefinitions across all PatchBukkit bridges.
 *
 * <p>{@link ByteBuddy#redefine(Class)} reads the unmodified {@code .class} bytes from the
 * classloader. If two bridges call {@code redefine(CraftServer.class)} independently, the second
 * call silently erases every hook installed by the first. {@code BridgePatcher} aggregates all
 * transforms per target class and applies a single composed {@code redefine} for each class.
 */
public final class BridgePatcher {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");

    private static final Map<Class<?>, List<NamedTransform>> PENDING = new LinkedHashMap<>();
    private static final Map<Class<?>, List<String>> APPLIED = new ConcurrentHashMap<>();

    private record NamedTransform(String source, UnaryOperator<DynamicType.Builder<?>> transform) {}

    private BridgePatcher() {}

    public static synchronized void register(
        Class<?> target,
        String source,
        UnaryOperator<DynamicType.Builder<?>> transform
    ) {
        if (target == null || transform == null) return;
        PENDING.computeIfAbsent(target, k -> new ArrayList<>()).add(new NamedTransform(source, transform));
    }

    public static synchronized void registerByName(
        String className,
        String source,
        UnaryOperator<DynamicType.Builder<?>> transform
    ) {
        if (className == null || transform == null) return;
        try {
            Class<?> target = Class.forName(className);
            register(target, source, transform);
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[PatchBukkit] Skipping ByteBuddy patch for unavailable class " + className + " (" + source + ")", t);
        }
    }

    /**
     * Applies all registered class transforms in a single {@link ByteBuddy#redefine(Class)} pass
     * per target class.
     */
    public static synchronized void applyAll() {
        if (PENDING.isEmpty()) return;
        ByteBuddyAgent.install();
        ByteBuddy byteBuddy = new ByteBuddy();

        for (Map.Entry<Class<?>, List<NamedTransform>> entry : PENDING.entrySet()) {
            Class<?> target = entry.getKey();
            List<NamedTransform> transforms = entry.getValue();
            if (transforms.isEmpty()) continue;

            try {
                DynamicType.Builder<?> builder = byteBuddy.redefine(target);
                List<String> sources = new ArrayList<>(transforms.size());
                for (NamedTransform nt : transforms) {
                    builder = nt.transform.apply(builder);
                    sources.add(nt.source);
                }
                builder
                    .make()
                    .load(target.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
                APPLIED.put(target, Collections.unmodifiableList(sources));
                LOGGER.info("[PatchBukkit] Patched " + target.getName() + " with " + sources.size() + " bridge(s): " + sources);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to apply composed ByteBuddy patch to " + target.getName(), t);
            }
        }
        PENDING.clear();
    }

    /** Returns the list of bridge sources applied to {@code target} (for diagnostics & tests). */
    public static List<String> getAppliedSources(Class<?> target) {
        return APPLIED.getOrDefault(target, List.of());
    }
}
