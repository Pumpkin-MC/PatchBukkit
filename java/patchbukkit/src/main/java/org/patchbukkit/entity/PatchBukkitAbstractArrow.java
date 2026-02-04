package org.patchbukkit.entity;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.inventory.ItemStack;

public class PatchBukkitAbstractArrow extends PatchBukkitProjectile implements AbstractArrow {
    private PickupStatus pickupStatus = PickupStatus.ALLOWED;
    private double damage = 2.0;
    private int knockbackStrength = 0;
    private int pierceLevel = 0;
    private boolean critical = false;
    private boolean inBlock = false;
    private boolean shotFromCrossbow = false;
    private boolean noPhysics = false;
    private int lifetimeTicks = 0;
    private ItemStack item = new ItemStack(Material.ARROW, 1);
    private ItemStack weapon = null;
    private Sound hitSound = Sound.ENTITY_ARROW_HIT;

    public PatchBukkitAbstractArrow(UUID uuid, String name) {
        super(uuid, name);
    }

    public Block getAttachedBlock() {
        return null;
    }

    public double getDamage() {
        return damage;
    }

    public void setDamage(double damage) {
        this.damage = damage;
    }

    public int getKnockbackStrength() {
        return knockbackStrength;
    }

    public void setKnockbackStrength(int knockbackStrength) {
        this.knockbackStrength = knockbackStrength;
    }

    public PickupStatus getPickupStatus() {
        return pickupStatus;
    }

    public void setPickupStatus(PickupStatus status) {
        this.pickupStatus = status;
    }

    public int getPierceLevel() {
        return pierceLevel;
    }

    public void setPierceLevel(int pierceLevel) {
        this.pierceLevel = pierceLevel;
    }

    public boolean isCritical() {
        return critical;
    }

    public void setCritical(boolean critical) {
        this.critical = critical;
    }

    public boolean isInBlock() {
        return inBlock;
    }

    public boolean isShotFromCrossbow() {
        return shotFromCrossbow;
    }

    public void setShotFromCrossbow(boolean shotFromCrossbow) {
        this.shotFromCrossbow = shotFromCrossbow;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        if (item != null) {
            this.item = item;
        }
    }

    public ItemStack getWeapon() {
        return weapon != null ? weapon : item;
    }

    public void setWeapon(ItemStack weapon) {
        this.weapon = weapon;
    }

    public Sound getHitSound() {
        return hitSound;
    }

    public void setHitSound(Sound sound) {
        if (sound != null) {
            this.hitSound = sound;
        }
    }

    public boolean hasNoPhysics() {
        return noPhysics;
    }

    public void setNoPhysics(boolean noPhysics) {
        this.noPhysics = noPhysics;
    }

    public int getLifetimeTicks() {
        return lifetimeTicks;
    }

    public void setLifetimeTicks(int ticks) {
        this.lifetimeTicks = ticks;
    }
}
