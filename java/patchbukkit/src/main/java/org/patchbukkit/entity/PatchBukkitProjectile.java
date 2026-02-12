package org.patchbukkit.entity;

import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class PatchBukkitProjectile extends PatchBukkitEntity implements Projectile {
    private ProjectileSource shooter;
    private boolean bounce;
    private boolean hasLeftShooter;
    private boolean hasBeenShot;
    private UUID ownerUuid;

    public PatchBukkitProjectile(UUID uuid, String name) {
        super(uuid, name);
        warnSnapshotLimitation(
            "entity:projectile_snapshot",
            "PatchBukkit projectile wrappers currently keep local snapshot state and may diverge from Pumpkin runtime state."
        );
    }

    @Override
    public @Nullable ProjectileSource getShooter() {
        return shooter;
    }

    @Override
    public void setShooter(@Nullable ProjectileSource shooter) {
        this.shooter = shooter;
        if (shooter instanceof Entity entity) {
            this.ownerUuid = entity.getUniqueId();
        }
    }

    @Override
    public boolean doesBounce() {
        return bounce;
    }

    @Override
    public void setBounce(boolean bounce) {
        this.bounce = bounce;
    }

    @Override
    public boolean hasLeftShooter() {
        return hasLeftShooter;
    }

    @Override
    public void setHasLeftShooter(boolean hasLeftShooter) {
        this.hasLeftShooter = hasLeftShooter;
    }

    @Override
    public boolean hasBeenShot() {
        return hasBeenShot;
    }

    @Override
    public void setHasBeenShot(boolean hasBeenShot) {
        this.hasBeenShot = hasBeenShot;
    }

    @Override
    public boolean canHitEntity(Entity entity) {
        return true;
    }

    @Override
    public void hitEntity(Entity entity) {
        // No-op
    }

    @Override
    public void hitEntity(Entity entity, Vector vector) {
        // No-op
    }

    @Override
    public UUID getOwnerUniqueId() {
        return ownerUuid;
    }
}
