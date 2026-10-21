package org.patchbukkit.bridge;

import java.util.UUID;

public class BridgeUtils {
    public static UUID convertUuid(patchbukkit.common.UUID uuid) {
        return UUID.fromString(uuid.getValue());
    }

    public static patchbukkit.common.UUID convertUuid(UUID uuid) {
        return patchbukkit.common.UUID.newBuilder().setValue(uuid.toString()).build();
    }

    public static patchbukkit.common.Location convertLocation(org.bukkit.Location location) {
        if (location == null) return null;
        var pos = patchbukkit.common.Vec3.newBuilder()
            .setX(location.getX())
            .setY(location.getY())
            .setZ(location.getZ())
            .build();
        var worldUuid = location.getWorld() != null ? convertUuid(location.getWorld().getUID()) : null;
        var worldBuilder = patchbukkit.common.World.newBuilder();
        if (worldUuid != null) worldBuilder.setUuid(worldUuid);
        return patchbukkit.common.Location.newBuilder()
            .setWorld(worldBuilder.build())
            .setPosition(pos)
            .setYaw(location.getYaw())
            .setPitch(location.getPitch())
            .build();
    }

    public static org.bukkit.Location convertLocation(patchbukkit.common.Location location) {
        if (location == null) return null;
        org.bukkit.World world = null;
        if (location.hasWorld() && location.getWorld().hasUuid()) {
            UUID worldUid = convertUuid(location.getWorld().getUuid());
            if (org.patchbukkit.bootstrap.HeadlessPaperServer.isBooted()) {
                var server = org.patchbukkit.bootstrap.HeadlessPaperServer.get();
                world = server.server.getWorld(worldUid);
                if (world == null && server.overworld() != null) {
                    world = server.overworld().getWorld();
                }
            }
        }
        double x = location.hasPosition() ? location.getPosition().getX() : 0.0;
        double y = location.hasPosition() ? location.getPosition().getY() : 0.0;
        double z = location.hasPosition() ? location.getPosition().getZ() : 0.0;
        return new org.bukkit.Location(world, x, y, z, location.getYaw(), location.getPitch());
    }
}
