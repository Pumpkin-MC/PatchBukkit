package org.patchbukkit.bootstrap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.protobuf.ByteString;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.matcher.ElementMatchers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.inventory.CraftEntityEquipment;
import org.bukkit.craftbukkit.inventory.CraftInventoryPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.bridge.NativeFastPathFfi;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.itemstack.GetEntityEquipmentResponse;
import patchbukkit.itemstack.GetPlayerEnderChestResponse;
import patchbukkit.itemstack.GetPlayerInventoryResponse;
import patchbukkit.itemstack.SetPlayerEnderChestSlotRequest;
import patchbukkit.itemstack.SetPlayerEquipmentRequest;
import patchbukkit.itemstack.SetPlayerInventorySlotRequest;
import patchbukkit.itemstack.SetPlayerSelectedSlotRequest;

/**
 * Bridges Paper's {@link CraftInventoryPlayer}, {@link CraftEntityEquipment},
 * {@link PlayerEnderChestContainer}, and NMS {@link Inventory} with Pumpkin's
 * live player inventory, ender chest, and living entity equipment via FFI.
 */
public final class InventoryBridge {

    private static final Logger LOGGER = Logger.getLogger("PatchBukkit");
    private static final ThreadLocal<Boolean> SYNCING = ThreadLocal.withInitial(() -> false);
    private static volatile Field ENDER_CHEST_OWNER_FIELD;

    private InventoryBridge() {}

    public static void patch() {
        try {
            // 1. Hook CraftHumanEntity.getInventory(), getEquipment(), getItemInHand(), getEnderChest()
            BridgePatcher.register(CraftHumanEntity.class, "InventoryBridge", builder -> builder
                .visit(Advice.to(OnGetInventoryAdvice.class).on(
                    ElementMatchers.named("getInventory")
                        .or(ElementMatchers.named("getEquipment"))
                        .or(ElementMatchers.named("getItemInHand"))
                ))
                .visit(Advice.to(OnGetEnderChestAdvice.class).on(
                    ElementMatchers.named("getEnderChest").and(ElementMatchers.takesNoArguments())
                ))
            );

            // 2. Hook NMS net.minecraft.world.entity.player.Inventory mutations
            BridgePatcher.register(Inventory.class, "InventoryBridge", builder -> builder
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
            );

            // 3. Hook CraftInventoryPlayer equipment (single-slot) and bulk setters
            BridgePatcher.register(CraftInventoryPlayer.class, "InventoryBridge", builder -> builder
                .visit(Advice.to(OnSetHelmetAdvice.class).on(
                    ElementMatchers.named("setHelmet").and(ElementMatchers.takesArguments(org.bukkit.inventory.ItemStack.class))
                ))
                .visit(Advice.to(OnSetChestplateAdvice.class).on(
                    ElementMatchers.named("setChestplate").and(ElementMatchers.takesArguments(org.bukkit.inventory.ItemStack.class))
                ))
                .visit(Advice.to(OnSetLeggingsAdvice.class).on(
                    ElementMatchers.named("setLeggings").and(ElementMatchers.takesArguments(org.bukkit.inventory.ItemStack.class))
                ))
                .visit(Advice.to(OnSetBootsAdvice.class).on(
                    ElementMatchers.named("setBoots").and(ElementMatchers.takesArguments(org.bukkit.inventory.ItemStack.class))
                ))
                .visit(Advice.to(OnSetOffHandAdvice.class).on(
                    ElementMatchers.named("setItemInOffHand").and(ElementMatchers.takesArguments(org.bukkit.inventory.ItemStack.class))
                ))
                .visit(Advice.to(OnSetEquipmentSlotAdvice.class).on(
                    ElementMatchers.named("setItem").and(
                        ElementMatchers.takesArgument(0, org.bukkit.inventory.EquipmentSlot.class)
                            .and(ElementMatchers.takesArgument(1, org.bukkit.inventory.ItemStack.class))
                    )
                ))
                .visit(Advice.to(OnCraftInventoryPlayerBulkAdvice.class).on(
                    ElementMatchers.named("setStorageContents")
                        .or(ElementMatchers.named("setArmorContents"))
                        .or(ElementMatchers.named("setExtraContents"))
                ))
            );

            // 4. Hook SimpleContainer to sync PlayerEnderChestContainer mutations without opening a GUI
            BridgePatcher.register(SimpleContainer.class, "InventoryBridge", builder -> builder
                .visit(Advice.to(OnEnderChestSetItemAdvice.class).on(
                    ElementMatchers.named("setItem").and(ElementMatchers.takesArguments(int.class, ItemStack.class))
                ))
                .visit(Advice.to(OnEnderChestClearAdvice.class).on(
                    ElementMatchers.named("clearContent").and(ElementMatchers.takesNoArguments())
                ))
                .visit(Advice.to(OnEnderChestBulkAdvice.class).on(
                    ElementMatchers.named("removeItem")
                        .or(ElementMatchers.named("removeItemNoUpdate"))
                ))
            );

            // 5. Hook CraftEntityEquipment for non-player LivingEntities
            BridgePatcher.register(CraftEntityEquipment.class, "InventoryBridge", builder -> builder
                .visit(Advice.to(OnGetEntityEquipmentAdvice.class).on(
                    ElementMatchers.named("getEquipment").and(ElementMatchers.takesArguments(EquipmentSlot.class))
                ))
                .visit(Advice.to(OnSetEntityEquipmentAdvice.class).on(
                    ElementMatchers.named("setEquipment").and(
                        ElementMatchers.takesArguments(EquipmentSlot.class, org.bukkit.inventory.ItemStack.class, boolean.class)
                    )
                ))
                .visit(Advice.to(OnClearEntityEquipmentAdvice.class).on(
                    ElementMatchers.named("clear").and(ElementMatchers.takesNoArguments())
                ))
            );

            LOGGER.info("[PatchBukkit] Registered InventoryBridge hooks with BridgePatcher");
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[PatchBukkit] Failed to register InventoryBridge hooks", t);
        }
    }

    private static HolderLookup.Provider getRegistryLookup() {
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null) {
            return server.registryAccess();
        }
        try {
            if (HeadlessPaperServer.get() != null) {
                return HeadlessPaperServer.get().registryAccess();
            }
        } catch (Throwable ignored) {}
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    public static ItemStack protoToNms(patchbukkit.itemstack.ItemStack proto) {
        if (proto == null || proto.getAmount() <= 0 || proto.getType().isEmpty() || "minecraft:air".equals(proto.getType())) {
            return ItemStack.EMPTY;
        }
        if (!proto.getItemNbt().isEmpty()) {
            try {
                ByteArrayInputStream byteIn = new ByteArrayInputStream(proto.getItemNbt().toByteArray());
                DataInputStream dis = new DataInputStream(byteIn);
                CompoundTag compound = NbtIo.read(dis);
                HolderLookup.Provider lookup = getRegistryLookup();
                ItemStack nms = ItemStack.OPTIONAL_CODEC
                    .parse(lookup.createSerializationContext(NbtOps.INSTANCE), compound)
                    .result()
                    .orElse(ItemStack.EMPTY);
                if (nms != null && !nms.isEmpty()) {
                    if (!proto.getCustomData().isEmpty() && !nms.has(DataComponents.CUSTOM_DATA)) {
                        try {
                            ByteArrayInputStream cbais = new ByteArrayInputStream(proto.getCustomData().toByteArray());
                            DataInputStream cdis = new DataInputStream(cbais);
                            nms.set(DataComponents.CUSTOM_DATA, CustomData.of(NbtIo.read(cdis)));
                        } catch (Throwable ignored) {}
                    }
                    return nms;
                }
            } catch (Throwable ignored) {}
        }
        try {
            String keyStr = proto.getType().startsWith("minecraft:") ? proto.getType() : "minecraft:" + proto.getType();
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(keyStr));
            if (item == null || item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            ItemStack nms = new ItemStack(item, proto.getAmount());
            CompoundTag customDataTag = null;
            if (proto.getCustomData() != null && !proto.getCustomData().isEmpty()) {
                try {
                    ByteArrayInputStream byteIn = new ByteArrayInputStream(proto.getCustomData().toByteArray());
                    DataInputStream dis = new DataInputStream(byteIn);
                    customDataTag = NbtIo.read(dis);
                    nms.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag));
                } catch (Throwable ignored) {}
            }
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
            if (customDataTag != null) {
                nms.set(DataComponents.CUSTOM_DATA, CustomData.of(customDataTag));
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
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            if (customData != null && !customData.isEmpty()) {
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    NbtIo.write(customData.copyTag(), dos);
                    builder.setCustomData(ByteString.copyFrom(baos.toByteArray()));
                } catch (Throwable ignored) {}
            }
            try {
                HolderLookup.Provider lookup = getRegistryLookup();
                Tag tag = ItemStack.OPTIONAL_CODEC
                    .encodeStart(lookup.createSerializationContext(NbtOps.INSTANCE), stack)
                    .result()
                    .orElse(null);
                if (tag instanceof CompoundTag compound && !compound.isEmpty()) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    DataOutputStream dos = new DataOutputStream(baos);
                    NbtIo.write(compound, dos);
                    builder.setItemNbt(ByteString.copyFrom(baos.toByteArray()));
                }
            } catch (Throwable ignored) {}
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

    public static void syncEnderChestFromPumpkin(CraftHumanEntity human) {
        if (human == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        net.minecraft.world.entity.player.Player player = human.getHandle();
        if (player == null) return;
        UUID uuid = player.getUUID();
        if (uuid == null) return;

        SYNCING.set(true);
        try {
            GetPlayerEnderChestResponse res = NativeBridgeFfi.getPlayerEnderChest(BridgeUtils.convertUuid(uuid));
            if (res == null) return;
            PlayerEnderChestContainer ec = player.getEnderChestInventory();
            int limit = Math.min(ec.getContainerSize(), res.getItemsCount());
            for (int i = 0; i < limit; i++) {
                ec.setItem(i, protoToNms(res.getItems(i)));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    private static UUID getEnderChestOwnerUuid(PlayerEnderChestContainer container) {
        if (container == null) return null;
        try {
            Field f = ENDER_CHEST_OWNER_FIELD;
            if (f == null) {
                f = PlayerEnderChestContainer.class.getDeclaredField("owner");
                f.setAccessible(true);
                ENDER_CHEST_OWNER_FIELD = f;
            }
            Object ownerObj = f.get(container);
            if (ownerObj instanceof net.minecraft.world.entity.player.Player p) {
                return p.getUUID();
            }
        } catch (Throwable ignored) {}
        try {
            org.bukkit.inventory.InventoryHolder holder = container.getOwner();
            if (holder instanceof org.bukkit.entity.Player p) {
                return p.getUniqueId();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void pushEnderChestSlotToPumpkin(SimpleContainer container, int slot, ItemStack stack) {
        if (!(container instanceof PlayerEnderChestContainer ec) || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return;
        }
        UUID uuid = getEnderChestOwnerUuid(ec);
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.setPlayerEnderChestSlot(
                SetPlayerEnderChestSlotRequest.newBuilder()
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

    public static void pushClearEnderChestToPumpkin(SimpleContainer container) {
        if (!(container instanceof PlayerEnderChestContainer ec) || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return;
        }
        UUID uuid = getEnderChestOwnerUuid(ec);
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            NativeBridgeFfi.clearPlayerEnderChest(BridgeUtils.convertUuid(uuid));
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushAllEnderChestToPumpkin(SimpleContainer container) {
        if (!(container instanceof PlayerEnderChestContainer ec) || !NativeFastPathFfi.isInitialized() || SYNCING.get()) {
            return;
        }
        UUID uuid = getEnderChestOwnerUuid(ec);
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            patchbukkit.common.UUID protoUuid = BridgeUtils.convertUuid(uuid);
            int size = Math.min(27, ec.getContainerSize());
            for (int i = 0; i < size; i++) {
                NativeBridgeFfi.setPlayerEnderChestSlot(
                    SetPlayerEnderChestSlotRequest.newBuilder()
                        .setUuid(protoUuid)
                        .setSlot(i)
                        .setItem(nmsToProto(ec.getItem(i)))
                        .build()
                );
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

    public static void pushSinglePlayerEquipmentToPumpkin(CraftInventoryPlayer craftInv, int slotType, org.bukkit.inventory.ItemStack item) {
        if (craftInv == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Inventory inv = craftInv.getInventory();
        if (inv == null || inv.player == null) return;
        UUID uuid = inv.player.getUUID();
        if (uuid == null) return;
        SYNCING.set(true);
        try {
            ItemStack nms = item != null ? CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
            NativeBridgeFfi.setPlayerEquipment(
                SetPlayerEquipmentRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(uuid))
                    .setSlotType(slotType)
                    .setItem(nmsToProto(nms))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void pushBukkitEquipmentSlotToPumpkin(CraftInventoryPlayer craftInv, org.bukkit.inventory.EquipmentSlot slot, org.bukkit.inventory.ItemStack item) {
        if (slot == null) return;
        int slotType = switch (slot) {
            case HAND -> 0;
            case OFF_HAND -> 1;
            case FEET -> 2;
            case LEGS -> 3;
            case CHEST -> 4;
            case HEAD -> 5;
            default -> -1;
        };
        if (slotType >= 0) {
            pushSinglePlayerEquipmentToPumpkin(craftInv, slotType, item);
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

    private static int nmsSlotToProtoSlotType(EquipmentSlot slot) {
        if (slot == null) return -1;
        return switch (slot) {
            case MAINHAND -> 0;
            case OFFHAND -> 1;
            case FEET -> 2;
            case LEGS -> 3;
            case CHEST -> 4;
            case HEAD -> 5;
            default -> -1;
        };
    }

    public static org.bukkit.inventory.ItemStack getEntityEquipmentFromPumpkin(
        CraftEntityEquipment equipment,
        EquipmentSlot slot,
        org.bukkit.inventory.ItemStack fallback
    ) {
        if (equipment == null || slot == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return fallback;
        Entity holder = equipment.getHolder();
        if (holder == null || holder instanceof CraftHumanEntity) return fallback;
        SYNCING.set(true);
        try {
            GetEntityEquipmentResponse res = NativeBridgeFfi.getEntityEquipment(
                BridgeUtils.convertUuid(holder.getUniqueId())
            );
            if (res == null) return fallback;
            patchbukkit.itemstack.ItemStack proto = switch (slot) {
                case MAINHAND -> res.hasMainHand() ? res.getMainHand() : null;
                case OFFHAND -> res.hasOffHand() ? res.getOffHand() : null;
                case FEET -> res.hasBoots() ? res.getBoots() : null;
                case LEGS -> res.hasLeggings() ? res.getLeggings() : null;
                case CHEST -> res.hasChestplate() ? res.getChestplate() : null;
                case HEAD -> res.hasHelmet() ? res.getHelmet() : null;
                default -> null;
            };
            if (proto != null) {
                return CraftItemStack.asBukkitCopy(protoToNms(proto));
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
        return fallback;
    }

    public static void pushEntityEquipmentToPumpkin(
        CraftEntityEquipment equipment,
        EquipmentSlot slot,
        org.bukkit.inventory.ItemStack item
    ) {
        if (equipment == null || slot == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Entity holder = equipment.getHolder();
        if (holder == null || holder instanceof CraftHumanEntity) return;
        int slotType = nmsSlotToProtoSlotType(slot);
        if (slotType < 0) return;
        SYNCING.set(true);
        try {
            ItemStack nms = item != null ? CraftItemStack.asNMSCopy(item) : ItemStack.EMPTY;
            NativeBridgeFfi.setPlayerEquipment(
                SetPlayerEquipmentRequest.newBuilder()
                    .setUuid(BridgeUtils.convertUuid(holder.getUniqueId()))
                    .setSlotType(slotType)
                    .setItem(nmsToProto(nms))
                    .build()
            );
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    public static void clearEntityEquipmentInPumpkin(CraftEntityEquipment equipment) {
        if (equipment == null || !NativeFastPathFfi.isInitialized() || SYNCING.get()) return;
        Entity holder = equipment.getHolder();
        if (holder == null || holder instanceof CraftHumanEntity) return;
        SYNCING.set(true);
        try {
            patchbukkit.common.UUID protoUuid = BridgeUtils.convertUuid(holder.getUniqueId());
            patchbukkit.itemstack.ItemStack air = nmsToProto(ItemStack.EMPTY);
            for (int slotType = 0; slotType <= 5; slotType++) {
                NativeBridgeFfi.setPlayerEquipment(
                    SetPlayerEquipmentRequest.newBuilder()
                        .setUuid(protoUuid)
                        .setSlotType(slotType)
                        .setItem(air)
                        .build()
                );
            }
        } catch (Throwable ignored) {
        } finally {
            SYNCING.set(false);
        }
    }

    // ---- Advice classes ----

    public static final class OnGetInventoryAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            InventoryBridge.syncFromPumpkin(human);
        }
    }

    public static final class OnGetEnderChestAdvice {
        @Advice.OnMethodEnter
        public static void onEnter(@Advice.This CraftHumanEntity human) {
            InventoryBridge.syncEnderChestFromPumpkin(human);
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

    public static final class OnSetHelmetAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer inv, @Advice.Argument(0) org.bukkit.inventory.ItemStack item) {
            InventoryBridge.pushSinglePlayerEquipmentToPumpkin(inv, 5, item);
        }
    }

    public static final class OnSetChestplateAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer inv, @Advice.Argument(0) org.bukkit.inventory.ItemStack item) {
            InventoryBridge.pushSinglePlayerEquipmentToPumpkin(inv, 4, item);
        }
    }

    public static final class OnSetLeggingsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer inv, @Advice.Argument(0) org.bukkit.inventory.ItemStack item) {
            InventoryBridge.pushSinglePlayerEquipmentToPumpkin(inv, 3, item);
        }
    }

    public static final class OnSetBootsAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer inv, @Advice.Argument(0) org.bukkit.inventory.ItemStack item) {
            InventoryBridge.pushSinglePlayerEquipmentToPumpkin(inv, 2, item);
        }
    }

    public static final class OnSetOffHandAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer inv, @Advice.Argument(0) org.bukkit.inventory.ItemStack item) {
            InventoryBridge.pushSinglePlayerEquipmentToPumpkin(inv, 1, item);
        }
    }

    public static final class OnSetEquipmentSlotAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftInventoryPlayer inv,
            @Advice.Argument(0) org.bukkit.inventory.EquipmentSlot slot,
            @Advice.Argument(1) org.bukkit.inventory.ItemStack item
        ) {
            InventoryBridge.pushBukkitEquipmentSlotToPumpkin(inv, slot, item);
        }
    }

    public static final class OnCraftInventoryPlayerBulkAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftInventoryPlayer craftInv) {
            InventoryBridge.pushAllToPumpkin(craftInv.getInventory());
        }
    }

    public static final class OnEnderChestSetItemAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This SimpleContainer container,
            @Advice.Argument(0) int slot,
            @Advice.Argument(1) ItemStack stack
        ) {
            InventoryBridge.pushEnderChestSlotToPumpkin(container, slot, stack);
        }
    }

    public static final class OnEnderChestClearAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This SimpleContainer container) {
            InventoryBridge.pushClearEnderChestToPumpkin(container);
        }
    }

    public static final class OnEnderChestBulkAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This SimpleContainer container) {
            InventoryBridge.pushAllEnderChestToPumpkin(container);
        }
    }

    public static final class OnGetEntityEquipmentAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntityEquipment equipment,
            @Advice.Argument(0) EquipmentSlot slot,
            @Advice.Return(readOnly = false) org.bukkit.inventory.ItemStack ret
        ) {
            ret = InventoryBridge.getEntityEquipmentFromPumpkin(equipment, slot, ret);
        }
    }

    public static final class OnSetEntityEquipmentAdvice {
        @Advice.OnMethodExit
        public static void onExit(
            @Advice.This CraftEntityEquipment equipment,
            @Advice.Argument(0) EquipmentSlot slot,
            @Advice.Argument(1) org.bukkit.inventory.ItemStack item
        ) {
            InventoryBridge.pushEntityEquipmentToPumpkin(equipment, slot, item);
        }
    }

    public static final class OnClearEntityEquipmentAdvice {
        @Advice.OnMethodExit
        public static void onExit(@Advice.This CraftEntityEquipment equipment) {
            InventoryBridge.clearEntityEquipmentInPumpkin(equipment);
        }
    }
}
