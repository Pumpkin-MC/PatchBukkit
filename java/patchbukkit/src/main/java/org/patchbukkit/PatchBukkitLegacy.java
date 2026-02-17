package org.patchbukkit;

import org.bukkit.Material;
import org.bukkit.material.MaterialData;

@SuppressWarnings({ "deprecation", "removal" })
public final class PatchBukkitLegacy {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(PatchBukkitLegacy.class);

    public static Material fromLegacy(Material material) {
        if (material == null || !material.isLegacy()) {
            return material;
        }

        return PatchBukkitLegacy.fromLegacy(new MaterialData(material));
    }

    public static Material fromLegacy(MaterialData materialData) {
        return PatchBukkitLegacy.fromLegacy(materialData, false);
    }

    public static Material fromLegacy(MaterialData materialData, boolean itemPriority) {
        Material material = materialData.getItemType();
        if (material == null || !material.isLegacy()) {
            return material;
        }

		throw new UnsupportedOperationException("Cannot convert from legacy to new Material");
    }
}
