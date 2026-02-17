package org.patchbukkit;

import com.google.common.collect.Multimap;
import com.google.gson.JsonObject;
import io.papermc.paper.entity.EntitySerializationFlag;
import io.papermc.paper.inventory.tooltip.TooltipContext;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.registry.RegistryKey;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.event.HoverEvent.ShowItem;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import patchbukkit.bridge.NativeBridgeFfi;

import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.attribute.Attributable;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageSource.Builder;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.CreativeCategory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.potion.PotionType.InternalPotionData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.patchbukkit.events.PatchBukkitLifecycleEventManager;
import org.patchbukkit.versioning.ApiVersion;
import org.patchbukkit.versioning.Versioning;
import patchbukkit.common.EmptyRequest;

@SuppressWarnings("removal")
public class PatchBukkitUnsafeValues implements UnsafeValues {

    public static final PatchBukkitUnsafeValues INSTANCE =
        new PatchBukkitUnsafeValues();

    @Override
    public boolean isSupportedApiVersion(String apiVersion) {
        if (apiVersion == null) return false;
        final ApiVersion toCheck = ApiVersion.getOrCreateVersion(apiVersion);
        var minimumApi = NativeBridgeFfi.getPatchBukkitConfig(EmptyRequest.newBuilder().build()).getMinimumSupportedPluginApi();
        final ApiVersion minimumVersion = ApiVersion.getOrCreateVersion(minimumApi);

        return !toCheck.isNewerThan(ApiVersion.CURRENT) && !toCheck.isOlderThan(minimumVersion);
    }

    @Override
    public void checkSupported(PluginDescriptionFile pdf)
        throws InvalidPluginException {
        String api = pdf.getAPIVersion();
        if (api != null && !isSupportedApiVersion(api)) {
            throw new InvalidPluginException("Unsupported API: " + api);
        }
    }

	@Override
	public ComponentFlattener componentFlattener() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'componentFlattener'");
	}

	@Override
	public PlainComponentSerializer plainComponentSerializer() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'plainComponentSerializer'");
	}

	@Override
	public PlainTextComponentSerializer plainTextSerializer() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'plainTextSerializer'");
	}

	@Override
	public GsonComponentSerializer gsonComponentSerializer() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'gsonComponentSerializer'");
	}

	@Override
	public GsonComponentSerializer colorDownsamplingGsonComponentSerializer() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'colorDownsamplingGsonComponentSerializer'");
	}

	@Override
	public LegacyComponentSerializer legacyComponentSerializer() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'legacyComponentSerializer'");
	}

	@Override
	public Component resolveWithContext(Component component, CommandSender context, Entity scoreboardSubject,
			boolean bypassPermissions) throws IOException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'resolveWithContext'");
	}

	@Override
	public Material toLegacy(Material material) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'toLegacy'");
	}

	@Override
	public Material fromLegacy(Material material) {
	    return PatchBukkitLegacy.fromLegacy(material);
	}

	@Override
	public Material fromLegacy(MaterialData material) {
	    return PatchBukkitLegacy.fromLegacy(material);
	}

	@Override
	public Material fromLegacy(MaterialData material, boolean itemPriority) {
	    return PatchBukkitLegacy.fromLegacy(material, itemPriority);
	}

	@Override
	public BlockData fromLegacy(Material material, byte data) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'fromLegacy'");
	}

	@Override
	public Material getMaterial(String material, int version) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getMaterial'");
	}

	@Override
	public int getDataVersion() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getDataVersion'");
	}

	@Override
	public ItemStack modifyItemStack(ItemStack stack, String arguments) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'modifyItemStack'");
	}

	@Override
	public byte[] processClass(PluginDescriptionFile pdf, String path, byte[] clazz) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'processClass'");
	}

	@Override
	public Advancement loadAdvancement(NamespacedKey key, String advancement) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'loadAdvancement'");
	}

	@Override
	public boolean removeAdvancement(NamespacedKey key) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'removeAdvancement'");
	}

	@Override
	public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(Material material, EquipmentSlot slot) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getDefaultAttributeModifiers'");
	}

	@Override
	public CreativeCategory getCreativeCategory(Material material) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getCreativeCategory'");
	}

	@Override
	public String getBlockTranslationKey(Material material) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getBlockTranslationKey'");
	}

	@Override
	public String getItemTranslationKey(Material material) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getItemTranslationKey'");
	}

	@Override
	public String getTranslationKey(EntityType entityType) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getTranslationKey'");
	}

	@Override
	public String getTranslationKey(ItemStack itemStack) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getTranslationKey'");
	}

	@Override
	public String getTranslationKey(Attribute attribute) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getTranslationKey'");
	}

	@Override
	public InternalPotionData getInternalPotionData(NamespacedKey key) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getInternalPotionData'");
	}

	@Override
	public @NotNull Builder createDamageSourceBuilder(@NotNull DamageType damageType) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'createDamageSourceBuilder'");
	}

	@Override
	public String get(Class<?> aClass, String value) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'get'");
	}

	@Override
	public <B extends Keyed> B get(RegistryKey<B> registry, NamespacedKey key) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'get'");
	}

	@Override
	public byte[] serializeItem(ItemStack item) {
	    System.out.println("Serializing item: " + item);
	    System.out.println(item.getType().getKey().getKey());
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'serializeItem'");
	}

	@Override
	public ItemStack deserializeItem(byte[] data) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'deserializeItem'");
	}

	@Override
	public @NotNull JsonObject serializeItemAsJson(@NotNull ItemStack itemStack) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'serializeItemAsJson'");
	}

	@Override
	public @NotNull ItemStack deserializeItemFromJson(@NotNull JsonObject data) throws IllegalArgumentException {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'deserializeItemFromJson'");
	}

	@Override
	public byte @NotNull [] serializeEntity(@NotNull Entity entity,
			@NotNull EntitySerializationFlag... serializationFlags) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'serializeEntity'");
	}

	@Override
	public @NotNull Entity deserializeEntity(byte @NotNull [] data, @NotNull World world, boolean preserveUUID,
			boolean preservePassengers) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'deserializeEntity'");
	}

	@Override
	public int nextEntityId() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'nextEntityId'");
	}

	@Override
	public @NotNull String getMainLevelName() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getMainLevelName'");
	}

	@Override
	public int getProtocolVersion() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getProtocolVersion'");
	}

	@Override
	public boolean isValidRepairItemStack(@NotNull ItemStack itemToBeRepaired, @NotNull ItemStack repairMaterial) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'isValidRepairItemStack'");
	}

	@Override
	public boolean hasDefaultEntityAttributes(@NotNull NamespacedKey entityKey) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'hasDefaultEntityAttributes'");
	}

	@Override
	public @NotNull Attributable getDefaultEntityAttributes(@NotNull NamespacedKey entityKey) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getDefaultEntityAttributes'");
	}

	@Override
	public @NotNull NamespacedKey getBiomeKey(RegionAccessor accessor, int x, int y, int z) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getBiomeKey'");
	}

	@Override
	public void setBiomeKey(RegionAccessor accessor, int x, int y, int z, NamespacedKey biomeKey) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'setBiomeKey'");
	}

	@Override
	public String getStatisticCriteriaKey(@NotNull Statistic statistic) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getStatisticCriteriaKey'");
	}

	@Override
	public @Nullable Color getSpawnEggLayerColor(EntityType entityType, int layer) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'getSpawnEggLayerColor'");
	}

	@Override
	public LifecycleEventManager<Plugin> createPluginLifecycleEventManager(JavaPlugin plugin,
			BooleanSupplier registrationCheck) {
		return new PatchBukkitLifecycleEventManager(plugin, registrationCheck);
	}

	@Override
	public @NotNull List<Component> computeTooltipLines(@NotNull ItemStack itemStack,
			@NotNull TooltipContext tooltipContext, @Nullable Player player) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'computeTooltipLines'");
	}

	@Override
	public ItemStack createEmptyStack() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'createEmptyStack'");
	}

	@Override
	public @NotNull Map<String, Object> serializeStack(ItemStack itemStack) {
        System.out.println("Serializing itemstack: " + itemStack);
        System.out.println(itemStack.getType().getKey().getKey());
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'serializeStack'");
	}

	@Override
	public @NotNull ItemStack deserializeStack(@NotNull Map<String, Object> args) {
	    System.out.println("Deserializing itemstack: " + args);
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'deserializeStack'");
	}

	@Override
	public @NotNull ItemStack deserializeItemHover(@NotNull ShowItem itemHover) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'deserializeItemHover'");
	}
}
