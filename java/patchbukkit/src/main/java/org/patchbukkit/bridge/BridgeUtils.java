package org.patchbukkit.bridge;

import org.bukkit.Location;
import org.patchbukkit.world.PatchBukkitWorld;

import java.util.UUID;

public class BridgeUtils {
    public static UUID convertUuid(patchbukkit.common.UUID uuid) {
        return UUID.fromString(uuid.getValue());
    }

    public static patchbukkit.common.UUID convertUuid(UUID uuid) {
        return patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()).build();
    }

    public static Location convertLocation(patchbukkit.common.Location location) {
        if (location == null || location.getWorld() == null || location.getWorld().getUuid() == null) {
            return null;
        }

        var world = PatchBukkitWorld.getOrCreate(convertUuid(location.getWorld().getUuid()));
        var position = location.getPosition();
        if (position == null) {
            return null;
        }

        return new Location(
            world,
            position.getX(),
            position.getY(),
            position.getZ(),
            location.getYaw(),
            location.getPitch()
        );
    }

    public static patchbukkit.common.Location convertLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }

        return patchbukkit.common.Location.newBuilder()
            .setWorld(patchbukkit.common.World.newBuilder()
                .setUuid(convertUuid(location.getWorld().getUID()))
                .build())
            .setPosition(patchbukkit.common.Vec3.newBuilder()
                .setX(location.getX())
                .setY(location.getY())
                .setZ(location.getZ())
                .build())
            .setYaw(location.getYaw())
            .setPitch(location.getPitch())
            .build();
    }

    public static PatchBukkitWorld convertWorld(patchbukkit.common.World world) {
        if (world == null || world.getUuid() == null) {
            return null;
        }
        return PatchBukkitWorld.getOrCreate(convertUuid(world.getUuid()));
    }

    public static patchbukkit.common.World convertWorld(org.bukkit.World world) {
        if (world == null) {
            return null;
        }
        return patchbukkit.common.World.newBuilder()
            .setUuid(convertUuid(world.getUID()))
            .build();
    }
}
