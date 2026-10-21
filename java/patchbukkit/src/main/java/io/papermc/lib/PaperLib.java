package io.papermc.lib;

import java.util.logging.Level;
import org.bukkit.plugin.Plugin;

/**
 * Stub / implementation of PaperLib for Paper plugin compatibility.
 */
public class PaperLib {

    private PaperLib() {
    }

    public static boolean isPaper() {
        return true;
    }

    public static boolean isSpigot() {
        return true;
    }

    public static boolean isVersion(int version) {
        return true;
    }

    public static boolean isVersion(int version, int patch) {
        return true;
    }

    public static int getMinecraftVersion() {
        return 26;
    }

    public static int getMinecraftPatchVersion() {
        return 3;
    }

    public static void suggestPaper(Plugin plugin) {
    }

    public static void suggestPaper(Plugin plugin, String reason) {
    }

    public static void suggestPaper(Plugin plugin, Level level, String reason) {
    }

    public static void suggestPaper(Plugin plugin, String reason, Object... args) {
    }

    public static void suggestPaper(Plugin plugin, Level level, String reason, Object... args) {
    }
}
