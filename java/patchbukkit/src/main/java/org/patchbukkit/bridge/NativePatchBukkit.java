package org.patchbukkit.bridge;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.util.UUID;

import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.patchbukkit.events.PatchBukkitEventSerializer;

public class NativePatchBukkit {

    private static final Linker LINKER = Linker.nativeLinker();

    private static MethodHandle getWorldNative;
    private static MethodHandle freeStringNative;
    private static MethodHandle getRegistryDataNative;
    private static MethodHandle playerEntityPlaySoundNative;
    private static MethodHandle playerPlaySoundNative;

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
        long freeStringAddr,
        long getWorldAddr,
        long getRegistryDataAddr,
        long playerEntityPlaySoundAddr,
        long playerPlaySoundAddr
    ) {

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

        // const char* rust_get_registry_entries(const char* registry_name)
        getRegistryDataNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(getRegistryDataAddr),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)
        );

        // void rust_player_entity_play_sound(const char* player_uuid, const char* sound_name,
        //     const char* sound_category, const char* entity_uuid, float volume, float pitch)
        playerEntityPlaySoundNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(playerEntityPlaySoundAddr),
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,    // player_uuid
                ValueLayout.ADDRESS,    // sound_name
                ValueLayout.ADDRESS,    // sound_category
                ValueLayout.ADDRESS,    // entity_uuid
                ValueLayout.JAVA_FLOAT, // volume
                ValueLayout.JAVA_FLOAT  // pitch
            )
        );

        // void rust_player_play_sound(const char* player_uuid, const char* sound_name,
        //     const char* sound_category, double x, double y, double z, float volume, float pitch)
        playerPlaySoundNative = LINKER.downcallHandle(
            MemorySegment.ofAddress(playerPlaySoundAddr),
            FunctionDescriptor.ofVoid(
                ValueLayout.ADDRESS,     // player_uuid
                ValueLayout.ADDRESS,     // sound_name
                ValueLayout.ADDRESS,     // sound_category
                ValueLayout.JAVA_DOUBLE, // x
                ValueLayout.JAVA_DOUBLE, // y
                ValueLayout.JAVA_DOUBLE, // z
                ValueLayout.JAVA_FLOAT,  // volume
                ValueLayout.JAVA_FLOAT   // pitch
            )
        );
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

    /**
     * Get all data for a registry (entries + tags) as JSON.
     *
     * @param registryName The registry identifier (e.g. "sound_event", "block")
     * @return JSON string with "entries" and "tags" fields, or null if registry unknown
     */
    public static String getRegistryData(String registryName) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment nameStr = arena.allocateFrom(registryName);
            MemorySegment resultPtr = (MemorySegment) getRegistryDataNative.invokeExact(nameStr);

            if (resultPtr.equals(MemorySegment.NULL)) return null;

            try {
                return resultPtr.reinterpret(Long.MAX_VALUE).getString(0);
            } finally {
                freeRustString(resultPtr);
            }
        } catch (Throwable t) {
            throw new RuntimeException("Failed to get registry data: " + registryName, t);
        }
    }

    /**
     * Play a sound effect attached to an entity, heard by a specific player.
     *
     * @param playerUuid    The player who will hear the sound
     * @param soundName     Sound identifier (e.g. "block.note_block.chime")
     * @param soundCategory Category name (e.g. "master", "music", "blocks")
     * @param entityUuid    The entity the sound is attached to
     * @param volume        Volume (1.0 = normal)
     * @param pitch         Pitch (1.0 = normal)
     */
    public static void playerEntityPlaySound(
            UUID playerUuid,
            String soundName,
            String soundCategory,
            UUID entityUuid,
            float volume,
            float pitch
    ) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment playerUuidStr = arena.allocateFrom(playerUuid.toString());
            MemorySegment soundNameStr = arena.allocateFrom(soundName);
            MemorySegment soundCategoryStr = arena.allocateFrom(soundCategory);
            MemorySegment entityUuidStr = arena.allocateFrom(entityUuid.toString());

            playerEntityPlaySoundNative.invokeExact(
                playerUuidStr,
                soundNameStr,
                soundCategoryStr,
                entityUuidStr,
                volume,
                pitch
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native entityPlaySound", t);
        }
    }

    /**
     * Play a sound effect at a location, heard by a specific player.
     *
     * @param playerUuid    The player who will hear the sound
     * @param soundName     Sound identifier (e.g. "block.note_block.chime")
     * @param soundCategory Category name (e.g. "master", "music", "blocks")
     * @param x             X coordinate
     * @param y             Y coordinate
     * @param z             Z coordinate
     * @param volume        Volume (1.0 = normal)
     * @param pitch         Pitch (1.0 = normal)
     */
    public static void playerPlaySound(
            UUID playerUuid,
            String soundName,
            String soundCategory,
            double x,
            double y,
            double z,
            float volume,
            float pitch
    ) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment playerUuidStr = arena.allocateFrom(playerUuid.toString());
            MemorySegment soundNameStr = arena.allocateFrom(soundName);
            MemorySegment soundCategoryStr = arena.allocateFrom(soundCategory);

            playerPlaySoundNative.invokeExact(
                playerUuidStr,
                soundNameStr,
                soundCategoryStr,
                x,
                y,
                z,
                volume,
                pitch
            );
        } catch (Throwable t) {
            throw new RuntimeException("Failed to call native playerPlaySound", t);
        }
    }
}
