package org.patchbukkit.entity;

import java.util.UUID;

import org.bukkit.entity.ExperienceOrb;
import org.jetbrains.annotations.Nullable;

public class PatchBukkitExperienceOrb extends PatchBukkitEntity implements ExperienceOrb {
    private int experience = 0;
    private int count = 1;
    private UUID triggerEntityId;
    private UUID sourceEntityId;
    private SpawnReason spawnReason = SpawnReason.UNKNOWN;

    public PatchBukkitExperienceOrb(UUID uuid) {
        super(uuid, "EXPERIENCE_ORB");
    }

    @Override
    public int getExperience() {
        return experience;
    }

    @Override
    public void setExperience(int experience) {
        this.experience = experience;
    }

    @Override
    public int getCount() {
        return count;
    }

    @Override
    public void setCount(int count) {
        this.count = count;
    }

    @Override
    public @Nullable UUID getTriggerEntityId() {
        return triggerEntityId;
    }

    @Override
    public @Nullable UUID getSourceEntityId() {
        return sourceEntityId;
    }

    @Override
    public SpawnReason getSpawnReason() {
        return spawnReason;
    }

    public void setTriggerEntityId(@Nullable UUID triggerEntityId) {
        this.triggerEntityId = triggerEntityId;
    }

    public void setSourceEntityId(@Nullable UUID sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public void setSpawnReason(SpawnReason spawnReason) {
        this.spawnReason = spawnReason != null ? spawnReason : SpawnReason.UNKNOWN;
    }
}
