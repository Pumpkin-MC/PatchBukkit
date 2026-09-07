package org.patchbukkit.entity;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntitySnapshot;
import org.bukkit.entity.EntityType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* Local snapshot of an entity's restorable state. Creating the entity back
* goes through the world's normal spawn path, then re-applies the name.
*/
public final class PatchBukkitEntitySnapshot implements EntitySnapshot {

    private final EntityType entityType;
    private final Location location;
    private final Component customName;
    private final boolean customNameVisible;

    public PatchBukkitEntitySnapshot(
        @NotNull EntityType entityType,
        @NotNull Location location,
        @Nullable Component customName,
        boolean customNameVisible
    ) {
        this.entityType = entityType;
        this.location = location.clone();
        this.customName = customName;
        this.customNameVisible = customNameVisible;
    }

    @Override
    public @NotNull Entity createEntity(@NotNull World world) {
        return createEntity(new Location(world, location.getX(), location.getY(), location.getZ(),
            location.getYaw(), location.getPitch()));
    }

    @Override
    public @NotNull Entity createEntity(@NotNull Location loc) {
        Entity entity = loc.getWorld().spawnEntity(loc, entityType);
        if (customName != null) {
            entity.customName(customName);
        }
        entity.setCustomNameVisible(customNameVisible);
        return entity;
    }

    @Override
    public @NotNull EntityType getEntityType() {
        return this.entityType;
    }

    @Override
    public @NotNull String getAsString() {
        return this.entityType.getKey().asString();
    }
}
