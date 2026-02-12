package org.patchbukkit.entity;

import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Egg;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PatchBukkitEgg extends PatchBukkitProjectile implements Egg {
    private ItemStack itemStack;

    public PatchBukkitEgg(@NotNull UUID uuid) {
        this(uuid, new ItemStack(Material.EGG));
    }

    public PatchBukkitEgg(@NotNull UUID uuid, @NotNull ItemStack itemStack) {
        super(uuid, "EGG");
        this.itemStack = itemStack;
    }

    @Override
    public @NotNull ItemStack getItem() {
        return itemStack;
    }

    @Override
    public void setItem(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack;
    }
}
