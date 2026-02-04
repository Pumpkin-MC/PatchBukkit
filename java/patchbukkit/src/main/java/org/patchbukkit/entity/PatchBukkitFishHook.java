package org.patchbukkit.entity;

import java.util.UUID;

import org.bukkit.entity.Entity;
import org.bukkit.entity.FishHook;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.Nullable;

public class PatchBukkitFishHook extends PatchBukkitProjectile implements FishHook {
    private int minWaitTime = 100;
    private int maxWaitTime = 600;
    private int minLureTime = 20;
    private int maxLureTime = 80;
    private float minLureAngle = 0.0f;
    private float maxLureAngle = 360.0f;
    private boolean applyLure = true;
    private double biteChance = 0.0;
    private boolean inOpenWater = false;
    private Entity hookedEntity;
    private boolean skyInfluenced = true;
    private boolean rainInfluenced = true;
    private HookState hookState = HookState.UNHOOKED;
    private int waitTime = 0;
    private int timeUntilBite = 0;

    public PatchBukkitFishHook(UUID uuid) {
        super(uuid, "FISHING_BOBBER");
    }

    @Override
    public int getMinWaitTime() {
        return minWaitTime;
    }

    @Override
    public void setMinWaitTime(int minWaitTime) {
        this.minWaitTime = minWaitTime;
    }

    @Override
    public int getMaxWaitTime() {
        return maxWaitTime;
    }

    @Override
    public void setMaxWaitTime(int maxWaitTime) {
        this.maxWaitTime = maxWaitTime;
    }

    @Override
    public void setWaitTime(int minWaitTime, int maxWaitTime) {
        this.minWaitTime = minWaitTime;
        this.maxWaitTime = maxWaitTime;
    }

    @Override
    public int getMinLureTime() {
        return minLureTime;
    }

    @Override
    public void setMinLureTime(int minLureTime) {
        this.minLureTime = minLureTime;
    }

    @Override
    public int getMaxLureTime() {
        return maxLureTime;
    }

    @Override
    public void setMaxLureTime(int maxLureTime) {
        this.maxLureTime = maxLureTime;
    }

    @Override
    public void setLureTime(int minLureTime, int maxLureTime) {
        this.minLureTime = minLureTime;
        this.maxLureTime = maxLureTime;
    }

    @Override
    public float getMinLureAngle() {
        return minLureAngle;
    }

    @Override
    public void setMinLureAngle(float minLureAngle) {
        this.minLureAngle = minLureAngle;
    }

    @Override
    public float getMaxLureAngle() {
        return maxLureAngle;
    }

    @Override
    public void setMaxLureAngle(float maxLureAngle) {
        this.maxLureAngle = maxLureAngle;
    }

    @Override
    public void setLureAngle(float minLureAngle, float maxLureAngle) {
        this.minLureAngle = minLureAngle;
        this.maxLureAngle = maxLureAngle;
    }

    @Override
    public boolean getApplyLure() {
        return applyLure;
    }

    @Override
    public void setApplyLure(boolean applyLure) {
        this.applyLure = applyLure;
    }

    @Override
    public double getBiteChance() {
        return biteChance;
    }

    @Override
    public void setBiteChance(double biteChance) throws IllegalArgumentException {
        if (biteChance < 0.0 || biteChance > 1.0) {
            throw new IllegalArgumentException("Bite chance must be between 0.0 and 1.0");
        }
        this.biteChance = biteChance;
    }

    @Override
    public boolean isInOpenWater() {
        return inOpenWater;
    }

    @Override
    public @Nullable Entity getHookedEntity() {
        return hookedEntity;
    }

    @Override
    public void setHookedEntity(@Nullable Entity entity) {
        this.hookedEntity = entity;
    }

    @Override
    public boolean pullHookedEntity() {
        return hookedEntity != null;
    }

    @Override
    public boolean isSkyInfluenced() {
        return skyInfluenced;
    }

    @Override
    public void setSkyInfluenced(boolean skyInfluenced) {
        this.skyInfluenced = skyInfluenced;
    }

    @Override
    public boolean isRainInfluenced() {
        return rainInfluenced;
    }

    @Override
    public void setRainInfluenced(boolean rainInfluenced) {
        this.rainInfluenced = rainInfluenced;
    }

    @Override
    public HookState getState() {
        return hookState;
    }

    @Override
    public int getWaitTime() {
        return waitTime;
    }

    @Override
    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    @Override
    public int getTimeUntilBite() {
        return timeUntilBite;
    }

    @Override
    public void setTimeUntilBite(int timeUntilBite) throws IllegalArgumentException {
        if (timeUntilBite < 0) {
            throw new IllegalArgumentException("Time until bite cannot be negative");
        }
        this.timeUntilBite = timeUntilBite;
    }

    @Override
    public void resetFishingState() {
        hookState = HookState.UNHOOKED;
        timeUntilBite = 0;
    }

    @Override
    public int retrieve(EquipmentSlot hand) {
        return 0;
    }
}
