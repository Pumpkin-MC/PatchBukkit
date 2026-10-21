package org.patchbukkit.bootstrap;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.itemstack.GetPlayerInventoryResponse;
import patchbukkit.itemstack.SetPlayerEquipmentRequest;
import patchbukkit.itemstack.SetPlayerInventorySlotRequest;
import patchbukkit.itemstack.SetPlayerSelectedSlotRequest;

/**
 * Bridges Paper's {@link CraftInventoryPlayer} and NMS {@link Inventory} with Pumpkin's
 * live player inventory and equipment via FFI.
 */
public final class InventoryBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);

    private InventoryBridge() {}

    public static void patch() {
        try {
            ByteBuddyAgent.install();
            ByteBuddy byteBuddy = new ByteBuddy();

            // 1. Hook CraftHumanEntity.getInventory() and getEquipment()
            try {
                byteBuddy
                    .redefine(CraftHumanEntity.class)
                    .visit(Advice.to(OnGetInventoryAdvice.class).on(
                        ElementMatchers.named("getInventory")
                            .or(ElementMatchers.named("getEquipment"))
                            .or(ElementMatchers.named("getItemInHand"))
                    ))
                    .make()
                    .load(CraftHumanEntity.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftHumanEntity in InventoryBridge", t);
            }

            // 2. Hook NMS net.minecraft.world.entity.player.Inventory mutations
            try {
                byteBuddy
                    .redefine(Inventory.class)
                    .visit(Advice.to(OnSetSlotAdvice.class).on(
                        ElementMatchers.named("setItem").and(ElementMatchers.takesArguments(int.class, ItemStack.class))
                    ))
                    .visit(Advice.to(OnSetSelectedSlotAdvice.class).on(
                        ElementMatchers.named("setSelectedSlot").and(ElementMatchers.takesArguments(int.class))
                    ))
                    .visit(Advice.to(OnClearAdvice.class).on(
                        ElementMatchers.named("clearContent").and(ElementMatchers.takesNoArguments())
                    ))
                    .visit(Advice.to(OnBulkInventoryMutateAdvice.class).on(
                        ElementMatchers.named("add")
                            .or(ElementMatchers.named("removeItem"))
                            .or(ElementMatchers.named("removeItemNoUpdate"))
                    ))
                    .make()
                    .load(Inventory.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook NMS Inventory in InventoryBridge", t);
            }

            // 3. Hook CraftInventoryPlayer equipment and bulk setters
            try {
                byteBuddy
                    .redefine(CraftInventoryPlayer.class)
                    .visit(Advice.to(OnCraftInventoryPlayerMutateAdvice.class).on(
                        ElementMatchers.named("setItemInOffHand")
                            .or(ElementMatchers.named("setHelmet"))
                            .or(ElementMatchers.named("setChestplate"))
                            .or(ElementMatchers.named("setLeggings"))
                            .or(ElementMatchers.named("setBoots"))
                            .or(ElementMatchers.named("setItem").and(ElementMatchers.takesArgument(0, org.bukkit.inventory.EquipmentSlot.class)))
                            .or(ElementMatchers.named("setStorageContents"))
                            .or(ElementMatchers.named("setArmorContents"))
                            .or(ElementMatchers.named("setExtraContents"))
                    ))
                    .make()
                    .load(CraftInventoryPlayer.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to hook CraftInventoryPlayer in InventoryBridge", t);
            }

            LOGGER.info("[PatchBukkit] Successfully patched Inventory and CraftInventoryPlayer with ByteBuddy");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to install InventoryBridge hooks", t);
        }
    }

    public static ItemStack protoToNms(patchbukkit.itemstack.ItemStack proto) {
        if (proto == null || proto.getAmount() <= 0 || proto.getType().isEmpty() || "minecraft:air".equals(proto.getType())) {
            return ItemStack.EMPTY;
        }
        try {
            String keyStr = proto.getType().startsWith("minecraft:") ? proto.getType() : "minecraft:" + proto.getType();
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(keyStr));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack nms = new ItemStack(item, proto.getAmount());
            if (proto.getDamage() > 0
                    || !proto.getCustomName().isEmpty()
                    || proto.getLoreCount() > 0
                    || proto.getEnchantmentsCount() > 0
                    || proto.getUnbreakable()) {
                org.bukkit.inventory.ItemStack bukkit = CraftItemStack.asBukkitMirror(nms);
                ItemMeta meta = bukkit.getItemMeta();
                if (meta != null) {
                    if (proto.getDamage() > 0 && meta instanceof Damageable dmg) {
                        dmg.setDamage(proto.getDamage());
                    }
                    if (!proto.getCustomName().isEmpty()) {
                        meta.setDisplayName(proto.getCustomName());
                    }
                    if (proto.getLoreCount() > 0) {
                        meta.setLore(proto.getLoreList());
                    }
                    if (proto.getUnbreakable()) {
                        meta.setUnbreakable(true);
                    }
                    if (proto.getEnchantmentsCount() > 0) {
                        for (Map.Entry<String, Integer> entry : proto.getEnchantmentsMap().entrySet()) {
                            NamespacedKey encKey = NamespacedKey.fromString(entry.getKey());
                            if (encKey != null) {
                                Enchantment enc = Registry.ENCHANTMENT.get(encKey);
                                if (enc != null) {
                                    meta.addEnchant(enc, entry.getValue(), true);
                                }
                            }
                        }
                    }
                    bukkit.setItemMeta(meta);
                }
            }
            return nms;
        } catch (Throwable ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static patchbukkit.itemstack.ItemStack nmsToProto(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return patchbukkit.itemstack.ItemStack.newBuilder()
                .setType("minecraft:air")
                .setAmount(0)
                .build();
        }
        Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String typeStr = key != null ? key.toString() : "minecraft:air";
        patchbukkit.itemstack.ItemStack.Builder builder = patchbukkit.itemstack.ItemStack.newBuilder()
            .setType(typeStr)
            .setAmount(stack.getCount());
        try {
            if (stack.getDamageValue() > 0) {
                builder.setDamage(stack.getDamageValue());
            }
            org.bukkit.inventory.ItemStack bukkit = CraftItemStack.asBukkitMirror(stack);
            if (bukkit.hasItemMeta()) {
                ItemMeta meta = bukkit.getItemMeta();
                if (meta != null) {
                    if (meta.hasDisplayName()) {
                        builder.setCustomName(meta.getDisplayName());
                    }
                    if (meta.hasLore() && meta.getLore() != null) {
                        builder.addAllLore(meta.getLore());
                    }
                    if (meta.isUnbreakable()) {
                        builder.setUnbreakable(true);
                    }
                    if (meta.hasEnchants()) {
                        for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                            builder.putEnchantments(entry.getKey().getKey().toString(), entry.getValue());
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return builder.build();
    }

    public static void syncFromPumpkin(CraftHumanEntity human) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player player = human.getHandle();
        if (player == null) return;
        UUID uuid = player.getUUID();
        if (uuid == null) return;

        SYNCING.set(true);
        try {
            GetPlayerInventoryResponse res = NativeBridgeFfi.getPlayerInventory(BridgeUtils.convertUuid(uuid));
            if (res == null) return;

            Inventory inv = player.getInventory();
            if (res.getSelectedSlot() >= 0 && res.getSelectedSlot() < 9) {
                inv.setSelectedSlot(res.getSelectedSlot());
            }

            NonNullList<ItemStack> mainItems = inv.getNonEquipmentItems();
            List<patchbukkit.itemstack.ItemStack> protoItems = res.getMainInventoryList();
            int limit = Math.min(mainItems.size(), protoItems.size());
            for (int i = 0; i < limit; i++) {
                mainItems.set(i, protoToNms(protoItems.get(i)));
            }

            if (res.hasBoots()) {
                inv.equipment.set(EquipmentSlot.FEET, protoToNms(res.getBoots()));
            }
            if (res.hasLeggings()) {
                inv.equipment.set(EquipmentSlot.LEGS, protoToNms(res.getLeggings()));
            }
            if (res.hasChestplate()) {
                inv.equipment.set(EquipmentSlot.CHEST, protoToNms(res.getChestplate()));
            }
            if (res.hasHelmet()) {
                inv.equipment.set(EquipmentSlot.HEAD, protoToNms(res.getHelmet()));
            }
            if (res.hasOffHand()) {
                inv.equipment.set(EquipmentSlot.OFFHAND, protoToNms(res.getOffHand()));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSlotToPumpkin(Inventory inv, int slot, ItemStack stack) {
        if (inv == null || inv.player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID uuid = inv.player.getUUID();
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerInventorySlot(
                SetPlayerInventorySlotRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(uuid))
                    .setSlot(slot)
                    .setItem(nmsToProto(stack))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushSelectedSlotToPumpkin(Inventory inv, int slot) {
        if (inv == null || inv.player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID uuid = inv.player.getUUID();
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerSelectedSlot(
                SetPlayerSelectedSlotRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(uuid))
                    .setSlot(slot)
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushClearToPumpkin(Inventory inv) {
        if (inv == null || inv.player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID uuid = inv.player.getUUID();
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.clearPlayerInventory(BridgeUtils.convertUuid(uuid));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAllToPumpkin(Inventory inv) {
        if (inv == null || inv.player == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        UUID uuid = inv.player.getUUID();
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            patchbukkit.common.UUID protoUuid = BridgeUtils.convertUuid(uuid);
            NonNullList<ItemStack> mainItems = inv.getNonEquipmentItems();
            int size = Math.min(36, mainItems.size());
            for (int i = 0; i < size; i++) {
                NativeBridgeFfi.setPlayerInventorySlot(
                    SetPlayerInventorySlotRequest.newBuilder()
                        .setUuid(protoUuid)
                        .setSlot(i)
                        .setItem(nmsToProto(mainItems.get(i)))
                        .build()
                );
            }
            EquipmentSlot[] eqSlots = {
                EquipmentSlot.FEET,
                EquipmentSlot.LEGS,
                EquipmentSlot.CHEST,
                EquipmentSlot.HEAD,
                EquipmentSlot.OFFHAND
            };
            int[] eqSlotTypes = { 2, 3, 4, 5, 1 };
            for (int i = 0; i < eqSlots.length; i++) {
                ItemStack eqItem = inv.equipment.get(eqSlots[i]);
                patchbukkit.itemstack.ItemStack protoItem = nmsToProto(eqItem);
                NativeBridgeFfi.setPlayerInventorySlot(
                    SetPlayerInventorySlotRequest.newBuilder()
                        .setUuid(protoUuid)
                        .setSlot(36 + i)
                        .setItem(protoItem)
                        .build()
                );
                NativeBridgeFfi.setPlayerEquipment(
                    SetPlayerEquipmentRequest.newBuilder()
                        .setUuid(protoUuid)
                        .setSlotType(eqSlotTypes[i])
                        .setItem(protoItem)
                        .build()
                );
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static final class OnGetInventoryAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            InventoryBridge.syncFromPumpkin(human);
        }
    }

    public static final class OnSetSlotAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This Inventory inv,
            @Advice.Argument(0) int slot,
            @Advice.Argument(1) ItemStack stack
        ) {
            InventoryBridge.pushSlotToPumpkin(inv, slot, stack);
        }
    }

    public static final class OnSetSelectedSlotAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This Inventory inv, @Advice.Argument(0) int slot) {
            InventoryBridge.pushSelectedSlotToPumpkin(inv, slot);
        }
    }

    public static final class OnClearAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This Inventory inv) {
            InventoryBridge.pushClearToPumpkin(inv);
        }
    }

    public static final class OnBulkInventoryMutateAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This Inventory inv) {
            InventoryBridge.pushAllToPumpkin(inv);
        }
    }

    public static final class OnCraftInventoryPlayerMutateAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer craftInv) {
            InventoryBridge.pushAllToPumpkin(craftInv.getInventory());
        }
    }
}
