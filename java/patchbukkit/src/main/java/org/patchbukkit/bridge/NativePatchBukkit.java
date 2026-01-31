package org.patchbukkit.bridge;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.UUID;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

public class NativePatchBukkit {

    private static final Linker LINKER = Linker.nativeLinker();

    private static MethodHandle sendMessageNative;
    private static MethodHandle registerEventNative;
    private static MethodHandle getAbilitiesNative;
    private static MethodHandle setAbilitiesNative;
    private static MethodHandle getLocationNative;
    private static MethodHandle getWorldNative;
    private static MethodHandle freeStringNative;

    // Struct layout matching Rust's #[repr(C)] AbilitiesFFI
    private static final StructLayout ABILITIES_LAYOUT =
        MemoryLayout.structLayout(
            ValueLayout.JAVA_BOOLEAN.withName("invulnerable"),
            ValueLayout.JAVA_BOOLEAN.withName("flying"),
            ValueLayout.JAVA_BOOLEAN.withName("allow_flying"),
            ValueLayout.JAVA_BOOLEAN.withName("creative"),
            ValueLayout.JAVA_BOOLEAN.withName("allow_modify_world"),
            MemoryLayout.paddingLayout(3), // Padding to align f32 to 4-byte boundary
            ValueLayout.JAVA_FLOAT.withName("fly_speed"),
            ValueLayout.JAVA_FLOAT.withName("walk_speed")
        );

    // VarHandles for accessing struct fields
    private static final VarHandle INVULNERABLE = ABILITIES_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("invulnerable")
    );
    private static final VarHandle FLYING = ABILITIES_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("flying")
    );
    private static final VarHandle ALLOW_FLYING = ABILITIES_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("allow_flying")
    );
    private static final VarHandle CREATIVE = ABILITIES_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("creative")
    );
    private static final VarHandle ALLOW_MODIFY_WORLD =
        ABILITIES_LAYOUT.varHandle(
            MemoryLayout.PathElement.groupElement("allow_modify_world")
        );
    private static final VarHandle FLY_SPEED = ABILITIES_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("fly_speed")
    );
    private static final VarHandle WALK_SPEED = ABILITIES_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("walk_speed")
    );

    // Struct layout for Vec3
    private static final StructLayout VEC3_LAYOUT = MemoryLayout.structLayout(
        ValueLayout.JAVA_DOUBLE.withName("x"),
        ValueLayout.JAVA_DOUBLE.withName("y"),
        ValueLayout.JAVA_DOUBLE.withName("z")
    );

    // VarHandles for Vec3 fields
    private static final VarHandle VEC3_X = VEC3_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("x")
    );
    private static final VarHandle VEC3_Y = VEC3_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("y")
    );
    private static final VarHandle VEC3_Z = VEC3_LAYOUT.varHandle(
        MemoryLayout.PathElement.groupElement("z")
    );

    public record Vec3(double x, double y, double z) {}

    /**
     * Java record to hold player abilities.
     */
    public record Abilities(
        boolean invulnerable,
        boolean flying,
        boolean allowFlying,
        boolean creative,
        boolean allowModifyWorld,
        float flySpeed,
        float walkSpeed
    ) {
        // "Wither" methods - return a copy with one field changed
        public Abilities withInvulnerable(boolean value) {
            return new Abilities(
                value,
                flying,
                allowFlying,
                creative,
                allowModifyWorld,
                flySpeed,
                walkSpeed
            );
        }

        public Abilities withFlying(boolean value) {
            return new Abilities(
                invulnerable,
                value,
                allowFlying,
                creative,
                allowModifyWorld,
                flySpeed,
                walkSpeed
            );
        }

        public Abilities withAllowFlying(boolean value) {
            return new Abilities(
                invulnerable,
                flying,
                value,
                creative,
                allowModifyWorld,
                flySpeed,
                walkSpeed
            );
        }

        public Abilities withCreative(boolean value) {
            return new Abilities(
                invulnerable,
                flying,
                allowFlying,
                value,
                allowModifyWorld,
                flySpeed,
                walkSpeed
            );
        }

        public Abilities withAllowModifyWorld(boolean value) {
            return new Abilities(
                invulnerable,
                flying,
                allowFlying,
                creative,
                value,
                flySpeed,
                walkSpeed
            );
        }

        public Abilities withFlySpeed(float value) {
            return new Abilities(
                invulnerable,
                flying,
                allowFlying,
                creative,
                allowModifyWorld,
                value,
                walkSpeed
            );
        }

        public Abilities withWalkSpeed(float value) {
            return new Abilities(
                invulnerable,
                flying,
                allowFlying,
                creative,
                allowModifyWorld,
                flySpeed,
                value
            );
        }
    }

    /**
     * Called from Rust during initialization to register native function pointers.
     */
    public static void initCallbacks(
        long sendMessageAddr,
        long registerEventAddr,
        long getAbilitiesAddr,
        long setAbilitiesAddr,
        long getLocationAddr
    ) {
        // void rust_send_message(const char* uuid, const char* message)
        sendMessageNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(sendMessageAddr),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // void rust_register_event(void* listener, void* plugin)
        registerEventNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(registerEventAddr),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // bool rust_get_abilities(const char* uuid, AbilitiesFFI* out)
        getAbilitiesNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(getAbilitiesAddr),
            FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, // return type
                ValueLayout.ADDRESS, // uuid string
                ValueLayout.ADDRESS // out pointer to AbilitiesFFI
            )
        );

        // bool rust_set_abilities(const char* uuid, AbilitiesFFI* abilities)
        setAbilitiesNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(setAbilitiesAddr),
            FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, // return type
                ValueLayout.ADDRESS, // uuid string
                ValueLayout.ADDRESS // pointer to AbilitiesFFI
            )
        );

        // bool rust_get_location(const char* uuid, Vec3FFI* out)
        getLocationNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(getLocationAddr),
            FunctionDescriptor.of(
                ValueLayout.JAVA_BOOLEAN, // return type
                ValueLayout.ADDRESS, // uuid string
                ValueLayout.ADDRESS // out pointer to Vec3FFI
            )
        );

        // const char* rust_get_world(const char* uuid)
        getWorldNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(getWorldAddr),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // void rust_free_string(const char* str)
        freeStringNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(freeStringAddr),
            FunctionDescriptor.ofVoid(ValueLayout.ADDRESS)
        );

    }

    /**
     * Send a message to a player by UUID.
     */
    public static void sendMessage(UUID uuid, String message) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment uuidStr = arena.allocateFrom(uuid.toString());
            MemorySegment msgStr = arena.allocateFrom(message);
            sendMessageNative.invokeExact(uuidStr, msgStr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native sendMessage", t);
        }
    }

    /**
     * Register an event listener for a plugin.
     */
    public static void registerEvent(Listener listener, Plugin plugin) {
        // For objects, you have options:
        // 1. Pass identifying info (strings, IDs) instead of object refs
        // 2. Store objects in a Java-side registry and pass an ID
        // 3. Use JNI NewGlobalRef from Rust side if you need to hold refs
        // Simple approach - pass plugin name, handle lookup in Rust
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment pluginName = arena.allocateFrom(plugin.getName());
            MemorySegment listenerClass = arena.allocateFrom(
                listener.getClass().getName()
            );
            registerEventNative.invokeExact(listenerClass, pluginName);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to register event", t);
        }
    }

    /**
     * Get a player's abilities by UUID.
     *
     * @param uuid The player's UUID
     * @return The player's abilities, or null if player not found
     */
    public static Abilities getAbilities(UUID uuid) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment uuidStr = arena.allocateFrom(uuid.toString());
            MemorySegment outStruct = arena.allocate(ABILITIES_LAYOUT);

            boolean success = (boolean) getAbilitiesNative.invokeExact(
                uuidStr,
                outStruct
            );

            if (!success) {
                return null;
            }

            return new Abilities(
                (boolean) INVULNERABLE.get(outStruct, 0L),
                (boolean) FLYING.get(outStruct, 0L),
                (boolean) ALLOW_FLYING.get(outStruct, 0L),
                (boolean) CREATIVE.get(outStruct, 0L),
                (boolean) ALLOW_MODIFY_WORLD.get(outStruct, 0L),
                (float) FLY_SPEED.get(outStruct, 0L),
                (float) WALK_SPEED.get(outStruct, 0L)
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native getAbilities", t);
        }
    }

    public static boolean setAbilities(UUID uuid, Abilities abilities) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment uuidStr = arena.allocateFrom(uuid.toString());
            MemorySegment abilitiesStruct = arena.allocate(ABILITIES_LAYOUT);

            // Populate the struct with values from the Abilities record
            INVULNERABLE.set(abilitiesStruct, 0L, abilities.invulnerable());
            FLYING.set(abilitiesStruct, 0L, abilities.flying());
            ALLOW_FLYING.set(abilitiesStruct, 0L, abilities.allowFlying());
            CREATIVE.set(abilitiesStruct, 0L, abilities.creative());
            ALLOW_MODIFY_WORLD.set(
                abilitiesStruct,
                0L,
                abilities.allowModifyWorld()
            );
            FLY_SPEED.set(abilitiesStruct, 0L, abilities.flySpeed());
            WALK_SPEED.set(abilitiesStruct, 0L, abilities.walkSpeed());

            return (boolean) setAbilitiesNative.invokeExact(
                uuidStr,
                abilitiesStruct
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native setAbilities", t);
        }
    }

    /**
     * Get a player's location by UUID.
     *
     * @param uuid The player's UUID
     * @return The player's position, or null if player not found
     */
    public static Vec3 getLocation(UUID uuid) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment uuidStr = arena.allocateFrom(uuid.toString());
            MemorySegment outStruct = arena.allocate(VEC3_LAYOUT);

            boolean success = (boolean) getLocationNative.invokeExact(
                uuidStr,
                outStruct
            );

            if (!success) {
                return null;
            }

            return new Vec3(
                (double) VEC3_X.get(outStruct, 0L),
                (double) VEC3_Y.get(outStruct, 0L),
                (double) VEC3_Z.get(outStruct, 0L)
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native getLocation", t);
        }
    }

    /**
     * Free a string allocated by the Rust side.
     */
    private static void freeRustString(MemorySegment ptr) {
        try {
            freeStringNative.invokeExact(ptr);
        } catch (Throwable t) {
            throw new RuntimeException("Failed to free Rust string", t);
        }
    }

    /**
     * Get the world UUID for an entity.
     */
    public static String getWorld(UUID entityUuid) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment uuidStr = arena.allocateFrom(entityUuid.toString());
            MemorySegment resultPtr = (MemorySegment) getWorldNative.invokeExact(uuidStr);

            if (resultPtr.equals(MemorySegment.NULL)) {
                return null;
            }

            // Read the string, then free the Rust-allocated memory
            try {
                String result = resultPtr.reinterpret(Long.MAX_VALUE).getString(0);
                return result;
            } finally {
                freeRustString(resultPtr);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native getWorld", t);
        }
    }

}
