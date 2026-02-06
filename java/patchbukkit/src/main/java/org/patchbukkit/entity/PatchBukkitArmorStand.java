package org.patchbukkit.entity;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;

import io.papermc.paper.math.Rotations;
import org.jetbrains.annotations.NotNull;

public class PatchBukkitArmorStand extends PatchBukkitLivingEntity implements ArmorStand {

    public PatchBukkitArmorStand(UUID uuid, String name) {
        super(uuid, name);
        warnSnapshotLimitation(
            "entity:armor_stand_snapshot",
            "PatchBukkitArmorStand currently mirrors a limited local snapshot; unsupported fields may not reflect Pumpkin state."
        );
    }

    @Override
    public @NotNull ItemStack getItemInHand() {
        return new ItemStack(Material.AIR);
    }

    @Override
    public void setItemInHand(ItemStack item) {
        // TODO: integrate with equipment
        throw new UnsupportedOperationException("Unimplemented method 'setItemInHand'");
    }

    @Override
    public @NotNull ItemStack getBoots() {
        return new ItemStack(Material.AIR);
    }

    @Override
    public void setBoots(ItemStack item) {
        throw new UnsupportedOperationException("Unimplemented method 'setBoots'");
    }

    @Override
    public @NotNull ItemStack getLeggings() {
        return new ItemStack(Material.AIR);
    }

    @Override
    public void setLeggings(ItemStack item) {
        throw new UnsupportedOperationException("Unimplemented method 'setLeggings'");
    }

    @Override
    public @NotNull ItemStack getChestplate() {
        return new ItemStack(Material.AIR);
    }

    @Override
    public void setChestplate(ItemStack item) {
        throw new UnsupportedOperationException("Unimplemented method 'setChestplate'");
    }

    @Override
    public @NotNull ItemStack getHelmet() {
        return new ItemStack(Material.AIR);
    }

    @Override
    public void setHelmet(ItemStack item) {
        throw new UnsupportedOperationException("Unimplemented method 'setHelmet'");
    }

    @Override
    public @NotNull EulerAngle getBodyPose() {
        return EulerAngle.ZERO;
    }

    @Override
    public void setBodyPose(EulerAngle pose) {
        throw new UnsupportedOperationException("Unimplemented method 'setBodyPose'");
    }

    @Override
    public @NotNull EulerAngle getLeftArmPose() {
        return EulerAngle.ZERO;
    }

    @Override
    public void setLeftArmPose(EulerAngle pose) {
        throw new UnsupportedOperationException("Unimplemented method 'setLeftArmPose'");
    }

    @Override
    public @NotNull EulerAngle getRightArmPose() {
        return EulerAngle.ZERO;
    }

    @Override
    public void setRightArmPose(EulerAngle pose) {
        throw new UnsupportedOperationException("Unimplemented method 'setRightArmPose'");
    }

    @Override
    public @NotNull EulerAngle getLeftLegPose() {
        return EulerAngle.ZERO;
    }

    @Override
    public void setLeftLegPose(EulerAngle pose) {
        throw new UnsupportedOperationException("Unimplemented method 'setLeftLegPose'");
    }

    @Override
    public @NotNull EulerAngle getRightLegPose() {
        return EulerAngle.ZERO;
    }

    @Override
    public void setRightLegPose(EulerAngle pose) {
        throw new UnsupportedOperationException("Unimplemented method 'setRightLegPose'");
    }

    @Override
    public @NotNull EulerAngle getHeadPose() {
        return EulerAngle.ZERO;
    }

    @Override
    public void setHeadPose(EulerAngle pose) {
        throw new UnsupportedOperationException("Unimplemented method 'setHeadPose'");
    }

    @Override
    public boolean hasBasePlate() {
        return true;
    }

    @Override
    public void setBasePlate(boolean basePlate) {
        throw new UnsupportedOperationException("Unimplemented method 'setBasePlate'");
    }

    @Override
    public boolean isVisible() {
        return true;
    }

    @Override
    public void setVisible(boolean visible) {
        throw new UnsupportedOperationException("Unimplemented method 'setVisible'");
    }

    @Override
    public boolean hasArms() {
        return true;
    }

    @Override
    public void setArms(boolean arms) {
        throw new UnsupportedOperationException("Unimplemented method 'setArms'");
    }

    @Override
    public boolean isSmall() {
        return false;
    }

    @Override
    public void setSmall(boolean small) {
        throw new UnsupportedOperationException("Unimplemented method 'setSmall'");
    }

    @Override
    public boolean isMarker() {
        return false;
    }

    @Override
    public void setMarker(boolean marker) {
        throw new UnsupportedOperationException("Unimplemented method 'setMarker'");
    }

    @Override
    public void addEquipmentLock(@NotNull EquipmentSlot slot, @NotNull LockType lockType) {
        throw new UnsupportedOperationException("Unimplemented method 'addEquipmentLock'");
    }

    @Override
    public void removeEquipmentLock(@NotNull EquipmentSlot slot, @NotNull LockType lockType) {
        throw new UnsupportedOperationException("Unimplemented method 'removeEquipmentLock'");
    }

    @Override
    public boolean hasEquipmentLock(@NotNull EquipmentSlot slot, @NotNull LockType lockType) {
        return false;
    }

    @Override
    public boolean canMove() {
        return true;
    }

    @Override
    public void setCanMove(boolean move) {
        throw new UnsupportedOperationException("Unimplemented method 'setCanMove'");
    }

    @Override
    public EntityEquipment getEquipment() {
        return null;
    }

    @Override
    public boolean canTick() {
        return true;
    }

    @Override
    public void setCanTick(boolean tick) {
        throw new UnsupportedOperationException("Unimplemented method 'setCanTick'");
    }

    @Override
    public @NotNull ItemStack getItem(@NotNull EquipmentSlot slot) {
        return new ItemStack(Material.AIR);
    }

    @Override
    public void setItem(@NotNull EquipmentSlot slot, ItemStack item) {
        throw new UnsupportedOperationException("Unimplemented method 'setItem'");
    }

    @Override
    public @NotNull Set<EquipmentSlot> getDisabledSlots() {
        return Collections.emptySet();
    }

    @Override
    public void setDisabledSlots(@NotNull EquipmentSlot @NotNull... slots) {
        throw new UnsupportedOperationException("Unimplemented method 'setDisabledSlots'");
    }

    @Override
    public void addDisabledSlots(@NotNull EquipmentSlot @NotNull... slots) {
        throw new UnsupportedOperationException("Unimplemented method 'addDisabledSlots'");
    }

    @Override
    public void removeDisabledSlots(@NotNull EquipmentSlot @NotNull... slots) {
        throw new UnsupportedOperationException("Unimplemented method 'removeDisabledSlots'");
    }

    @Override
    public boolean isSlotDisabled(@NotNull EquipmentSlot slot) {
        return false;
    }

    @Override
    public @NotNull Rotations getBodyRotations() {
        return Rotations.ZERO;
    }

    @Override
    public void setBodyRotations(@NotNull Rotations rotations) {
        throw new UnsupportedOperationException("Unimplemented method 'setBodyRotations'");
    }

    @Override
    public @NotNull Rotations getLeftArmRotations() {
        return Rotations.ZERO;
    }

    @Override
    public void setLeftArmRotations(@NotNull Rotations rotations) {
        throw new UnsupportedOperationException("Unimplemented method 'setLeftArmRotations'");
    }

    @Override
    public @NotNull Rotations getRightArmRotations() {
        return Rotations.ZERO;
    }

    @Override
    public void setRightArmRotations(@NotNull Rotations rotations) {
        throw new UnsupportedOperationException("Unimplemented method 'setRightArmRotations'");
    }

    @Override
    public @NotNull Rotations getLeftLegRotations() {
        return Rotations.ZERO;
    }

    @Override
    public void setLeftLegRotations(@NotNull Rotations rotations) {
        throw new UnsupportedOperationException("Unimplemented method 'setLeftLegRotations'");
    }

    @Override
    public @NotNull Rotations getRightLegRotations() {
        return Rotations.ZERO;
    }

    @Override
    public void setRightLegRotations(@NotNull Rotations rotations) {
        throw new UnsupportedOperationException("Unimplemented method 'setRightLegRotations'");
    }

    @Override
    public @NotNull Rotations getHeadRotations() {
        return Rotations.ZERO;
    }

    @Override
    public void setHeadRotations(@NotNull Rotations rotations) {
        throw new UnsupportedOperationException("Unimplemented method 'setHeadRotations'");
    }
}
