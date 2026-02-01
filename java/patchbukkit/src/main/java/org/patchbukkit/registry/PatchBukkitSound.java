package org.patchbukkit.registry;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

public class PatchBukkitSound implements Sound {

    private final NamespacedKey namespacedKey;
    private final Key adventureKey;
    private final String enumName;
    private final int protocolId;

    public PatchBukkitSound(String soundName, int protocolId) {
        this.namespacedKey = new NamespacedKey(NamespacedKey.MINECRAFT, soundName);
        this.adventureKey = Key.key(Key.MINECRAFT_NAMESPACE, soundName);
        this.protocolId = protocolId;
        this.enumName = soundName.toUpperCase().replace('.', '_');
    }

    @Override
    public NamespacedKey getKey() {
        return namespacedKey;
    }

    @Override
    public Key key() {
        return adventureKey;
    }

    @Override
    public int ordinal() {
        return protocolId;
    }

    @Override
    public String name() {
        return enumName;
    }

    @Override
    public int compareTo(Sound other) {
        return Integer.compare(this.ordinal(), other.ordinal());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Sound other)) return false;
        return namespacedKey.equals(other.getKey());
    }

    @Override
    public int hashCode() {
        return namespacedKey.hashCode();
    }

    @Override
    public String toString() {
        return "PatchBukkitSound{" + namespacedKey + "}";
    }
}
