package org.patchbukkit.advancement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementRequirements;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* Advancement backed by the server's static registry. Metadata is fetched
* once and cached; per-player progress lives on the player side.
*/
public final class PatchBukkitAdvancement implements Advancement {

    private final NamespacedKey key;
    private final List<String> criteria;
    private final List<Collection<String>> requirementGroups;
    private final String parentId;
    private final List<String> childrenIds;
    private final boolean hasDisplay;
    private final String displayTitleKey;
    private final String displayDescriptionKey;
    private final String displayIconId;
    private final int displayIconCount;
    private final String displayFrame;
    private final String displayBackground;
    private final boolean displayShowToast;
    private final boolean displayAnnounceToChat;
    private final boolean displayHidden;

    public PatchBukkitAdvancement(
        @NotNull NamespacedKey key,
        @NotNull List<String> criteria,
        @NotNull List<Collection<String>> requirementGroups,
        @Nullable String parentId,
        @NotNull List<String> childrenIds,
        boolean hasDisplay,
        @NotNull String displayTitleKey,
        @NotNull String displayDescriptionKey,
        @NotNull String displayIconId,
        int displayIconCount,
        @NotNull String displayFrame,
        @NotNull String displayBackground,
        boolean displayShowToast,
        boolean displayAnnounceToChat,
        boolean displayHidden
    ) {
        this.key = key;
        this.criteria = List.copyOf(criteria);
        List<Collection<String>> groups = new ArrayList<>(requirementGroups.size());
        for (Collection<String> group : requirementGroups) {
            groups.add(List.copyOf(group));
        }
        this.requirementGroups = Collections.unmodifiableList(groups);
        this.parentId = parentId;
        this.childrenIds = List.copyOf(childrenIds);
        this.hasDisplay = hasDisplay;
        this.displayTitleKey = displayTitleKey;
        this.displayDescriptionKey = displayDescriptionKey;
        this.displayIconId = displayIconId;
        this.displayIconCount = displayIconCount;
        this.displayFrame = displayFrame;
        this.displayBackground = displayBackground;
        this.displayShowToast = displayShowToast;
        this.displayAnnounceToChat = displayAnnounceToChat;
        this.displayHidden = displayHidden;
    }

    public static @Nullable PatchBukkitAdvancement fetch(@NotNull NamespacedKey key) {
        try {
            var resp = patchbukkit.bridge.NativeBridgeFfi.getAdvancementInfo(
                patchbukkit.advancement.GetAdvancementInfoRequest.newBuilder()
                    .setAdvancementId(key.asString())
                    .build());
            if (resp == null || !resp.getExists()) {
                return null;
            }
            List<Collection<String>> groups = new ArrayList<>(resp.getRequirementGroupsCount());
            for (patchbukkit.advancement.CriterionGroup group : resp.getRequirementGroupsList()) {
                groups.add(List.copyOf(group.getCriteriaList()));
            }
            String parent = resp.getParentId().isEmpty() ? null : resp.getParentId();
            return new PatchBukkitAdvancement(key, resp.getAllCriteriaList(), groups, parent,
                resp.getChildrenIdsList(), resp.getHasDisplay(), resp.getDisplayTitleKey(),
                resp.getDisplayDescriptionKey(), resp.getDisplayIconId(), resp.getDisplayIconCount(),
                resp.getDisplayFrame(), resp.getDisplayBackground(), resp.getDisplayShowToast(),
                resp.getDisplayAnnounceToChat(), resp.getDisplayHidden());
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return this.key;
    }

    @Override
    public @NotNull Collection<String> getCriteria() {
        return this.criteria;
    }

    @Override
    public @NotNull AdvancementRequirements getRequirements() {
        List<org.bukkit.advancement.AdvancementRequirement> wrapped = new ArrayList<>(requirementGroups.size());
        for (Collection<String> group : requirementGroups) {
            wrapped.add(new PatchBukkitAdvancementRequirement(group));
        }
        List<org.bukkit.advancement.AdvancementRequirement> result =
            Collections.unmodifiableList(wrapped);
        return () -> result;
    }

    @Override
    public @Nullable io.papermc.paper.advancement.AdvancementDisplay getDisplay() {
        if (!hasDisplay) {
            return null;
        }
        return new PatchBukkitAdvancementDisplay();
    }

    @Override
    public @NotNull Component displayName() {
        if (hasDisplay && !displayTitleKey.isEmpty()) {
            return Component.translatable(displayTitleKey);
        }
        return Component.text(key.asString());
    }

    @Override
    public @Nullable Advancement getParent() {
        if (parentId == null) {
            return null;
        }
        try {
            return org.patchbukkit.PatchBukkitServer.getInstance()
                .getAdvancement(NamespacedKey.fromString(parentId));
        } catch (Throwable ignored) {
            return null;
        }
    }

    @Override
    public @NotNull Collection<Advancement> getChildren() {
        List<Advancement> out = new ArrayList<>(childrenIds.size());
        for (String id : childrenIds) {
            try {
                Advancement child = org.patchbukkit.PatchBukkitServer.getInstance()
                    .getAdvancement(NamespacedKey.fromString(id));
                if (child != null) {
                    out.add(child);
                }
            } catch (Throwable ignored) {}
        }
        return Collections.unmodifiableList(out);
    }

    @Override
    public @NotNull Advancement getRoot() {
        Advancement current = this;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        return current;
    }

    private static final class PatchBukkitAdvancementRequirement
            implements org.bukkit.advancement.AdvancementRequirement {
        private final List<String> required;

        PatchBukkitAdvancementRequirement(@NotNull Collection<String> required) {
            this.required = List.copyOf(required);
        }

        @Override
        public @NotNull List<String> getRequiredCriteria() {
            return required;
        }

        @Override
        public boolean isStrict() {
            // Vanilla requirement groups complete when any one criterion is
            // awarded, so groups are never strict.
            return false;
        }
    }

    private final class PatchBukkitAdvancementDisplay
            implements io.papermc.paper.advancement.AdvancementDisplay {

        @Override
        public @NotNull Frame frame() {
            try {
                return Frame.valueOf(displayFrame);
            } catch (Throwable ignored) {
                return Frame.TASK;
            }
        }

        @Override
        public @NotNull Component title() {
            return Component.translatable(displayTitleKey.isEmpty() ? key.asString() : displayTitleKey);
        }

        @Override
        public @NotNull Component description() {
            return Component.translatable(displayDescriptionKey);
        }

        @Override
        public @NotNull org.bukkit.inventory.ItemStack icon() {
            try {
                String id = displayIconId.startsWith("minecraft:")
                    ? displayIconId.substring("minecraft:".length()) : displayIconId;
                org.bukkit.Material material = org.bukkit.Material.matchMaterial(id.toUpperCase(java.util.Locale.ROOT));
                if (material == null || !material.isItem()) {
                    material = org.bukkit.Material.STONE;
                }
                return new org.bukkit.inventory.ItemStack(material, Math.max(1, displayIconCount));
            } catch (Throwable ignored) {
                return new org.bukkit.inventory.ItemStack(org.bukkit.Material.STONE);
            }
        }

        @Override
        public boolean doesShowToast() {
            return displayShowToast;
        }

        @Override
        public boolean doesAnnounceToChat() {
            return displayAnnounceToChat;
        }

        @Override
        public boolean isHidden() {
            return displayHidden;
        }

        @Override
        public @Nullable NamespacedKey backgroundPath() {
            try {
                return displayBackground.isEmpty() ? null : NamespacedKey.fromString(displayBackground);
            } catch (Throwable ignored) {
                return null;
            }
        }

        @Override
        public @NotNull Component displayName() {
            return title();
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Keyed keyed && key.equals(keyed.getKey());
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }
}
