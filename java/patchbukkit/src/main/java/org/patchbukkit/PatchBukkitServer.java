package org.patchbukkit;

import com.destroystokyo.paper.entity.ai.MobGoals;
import io.papermc.paper.ban.BanListType;
import io.papermc.paper.configuration.ServerConfiguration;
import io.papermc.paper.datapack.DatapackManager;
import io.papermc.paper.math.Position;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.InetAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.BanList;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Server;
import org.bukkit.ServerLinks;
import org.bukkit.ServerTickManager;
import org.bukkit.StructureType;
import org.bukkit.Tag;
import org.bukkit.UnsafeValues;
import org.bukkit.Warning.WarningState;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.advancement.Advancement;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarFlag;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.KeyedBossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityFactory;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.help.HelpMap;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemCraftResult;
import org.bukkit.inventory.ItemFactory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.Recipe;
import org.bukkit.loot.LootTable;
import org.bukkit.map.MapCursor.Type;
import org.bukkit.map.MapView;
import org.bukkit.packs.ResourcePack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.plugin.messaging.Messenger;
import org.bukkit.potion.PotionBrewer;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.structure.StructureManager;
import org.bukkit.util.CachedServerIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.patchbukkit.command.PatchBukkitCommandMap;
import org.patchbukkit.command.PatchBukkitConsoleCommandSender;
import org.patchbukkit.scheduler.PatchBukkitScheduler;

@SuppressWarnings("removal")
public class PatchBukkitServer implements Server {

    private final String serverName =
        io.papermc.paper.ServerBuildInfo.buildInfo().brandName();
    private final String bukkitVersion = Versioning.getBukkitVersion();
    private final CommandMap commandMap = new PatchBukkitCommandMap();
    private final BukkitScheduler scheduler = new PatchBukkitScheduler();
    private final PatchBukkitPluginManager pluginManager =
        new PatchBukkitPluginManager(this);


    private final Map<UUID, Player> onlinePlayers = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Player> onlinePlayersByName = new java.util.concurrent.ConcurrentHashMap<>();

    private final Logger logger = Logger.getLogger("Minecraft");

    /**
     * Called from Rust when a player joins the Pumpkin server
     */
    public void registerPlayer(Player player) {
        this.onlinePlayers.put(player.getUniqueId(), player);
        this.onlinePlayersByName.put(player.getName().toLowerCase(), player);
    }

    /**
     * Called from Rust when a player leaves
     */
    public void unregisterPlayer(UUID uuid) {
        Player p = this.onlinePlayers.remove(uuid);
        if (p != null) {
            this.onlinePlayersByName.remove(p.getName().toLowerCase());
        }
    }

    public void registerPlugin(@NotNull Plugin plugin) {
        this.pluginManager.registerPlugin(plugin);
    }

    @Override
    public void sendPluginMessage(
        @NotNull Plugin source,
        @NotNull String channel,
        byte@NotNull [] message
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'sendPluginMessage'"
        );
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getListeningPluginChannels'"
        );
    }

    @Override
    public @NotNull Iterable<? extends Audience> audiences() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'audiences'"
        );
    }

    @Override
    public @NotNull File getPluginsFolder() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPluginsFolder'"
        );
    }

    @Override
    public @NotNull String getName() {
        return this.serverName;
    }

    @Override
    public @NotNull String getVersion() {
        return "1.21.11";
    }

    @Override
    public @NotNull String getBukkitVersion() {
        return this.bukkitVersion;
    }

    @Override
    public @NotNull String getMinecraftVersion() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMinecraftVersion'"
        );
    }

    @Override
    public @NotNull Collection<? extends Player> getOnlinePlayers() {
        return Collections.unmodifiableCollection(onlinePlayers.values());
    }

    @Override
    public int getMaxPlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMaxPlayers'"
        );
    }

    @Override
    public void setMaxPlayers(int maxPlayers) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setMaxPlayers'"
        );
    }

    @Override
    public int getPort() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPort'"
        );
    }

    @Override
    public int getViewDistance() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getViewDistance'"
        );
    }

    @Override
    public int getSimulationDistance() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSimulationDistance'"
        );
    }

    @Override
    public @NotNull String getIp() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getIp'");
    }

    @Override
    public @NotNull String getWorldType() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorldType'"
        );
    }

    @Override
    public boolean getGenerateStructures() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGenerateStructures'"
        );
    }

    @Override
    public int getMaxWorldSize() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMaxWorldSize'"
        );
    }

    @Override
    public boolean getAllowEnd() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAllowEnd'"
        );
    }

    @Override
    public boolean getAllowNether() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAllowNether'"
        );
    }

    @Override
    public boolean isLoggingIPs() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isLoggingIPs'"
        );
    }

    @Override
    public @NotNull List<String> getInitialEnabledPacks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getInitialEnabledPacks'"
        );
    }

    @Override
    public @NotNull List<String> getInitialDisabledPacks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getInitialDisabledPacks'"
        );
    }

    @Override
    public @NotNull ServerTickManager getServerTickManager() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getServerTickManager'"
        );
    }

    @Override
    public @Nullable ResourcePack getServerResourcePack() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getServerResourcePack'"
        );
    }

    @Override
    public @NotNull String getResourcePack() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getResourcePack'"
        );
    }

    @Override
    public @NotNull String getResourcePackHash() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getResourcePackHash'"
        );
    }

    @Override
    public @NotNull String getResourcePackPrompt() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getResourcePackPrompt'"
        );
    }

    @Override
    public boolean isResourcePackRequired() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isResourcePackRequired'"
        );
    }

    @Override
    public boolean hasWhitelist() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'hasWhitelist'"
        );
    }

    @Override
    public void setWhitelist(boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setWhitelist'"
        );
    }

    @Override
    public boolean isWhitelistEnforced() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isWhitelistEnforced'"
        );
    }

    @Override
    public void setWhitelistEnforced(boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setWhitelistEnforced'"
        );
    }

    @Override
    public @NotNull Set<OfflinePlayer> getWhitelistedPlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWhitelistedPlayers'"
        );
    }

    @Override
    public void reloadWhitelist() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'reloadWhitelist'"
        );
    }

    @Override
    public @NotNull String getUpdateFolder() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getUpdateFolder'"
        );
    }

    @Override
    public @NotNull File getUpdateFolderFile() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getUpdateFolderFile'"
        );
    }

    @Override
    public long getConnectionThrottle() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getConnectionThrottle'"
        );
    }

    @Override
    public int getTicksPerSpawns(@NotNull SpawnCategory spawnCategory) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTicksPerSpawns'"
        );
    }

    @Override
    public @Nullable Player getPlayer(@NotNull String name) {
        return onlinePlayersByName.get(name.toLowerCase());
    }

    @Override
    public @Nullable Player getPlayerExact(@NotNull String name) {
        return onlinePlayersByName.get(name.toLowerCase());
    }

    @Override
    public @NotNull List<Player> matchPlayer(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'matchPlayer'"
        );
    }

   @Override
    public @Nullable Player getPlayer(@NotNull UUID id) {
        return onlinePlayers.get(id);
    }

    @Override
    public @Nullable UUID getPlayerUniqueId(@NotNull String playerName) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPlayerUniqueId'"
        );
    }

    @Override
    public @NotNull PluginManager getPluginManager() {
        return this.pluginManager;
    }

    @Override
    public @NotNull BukkitScheduler getScheduler() {
        return this.scheduler;
    }

    @Override
    public @NotNull ServicesManager getServicesManager() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getServicesManager'"
        );
    }

    @Override
    public @NotNull List<World> getWorlds() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorlds'"
        );
    }

    @Override
    public boolean isTickingWorlds() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isTickingWorlds'"
        );
    }

    @Override
    public @Nullable World createWorld(@NotNull WorldCreator creator) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createWorld'"
        );
    }

    @Override
    public boolean unloadWorld(@NotNull String name, boolean save) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'unloadWorld'"
        );
    }

    @Override
    public boolean unloadWorld(@NotNull World world, boolean save) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'unloadWorld'"
        );
    }

    @Override
    public @NotNull World getRespawnWorld() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRespawnWorld'"
        );
    }

    @Override
    public void setRespawnWorld(@NotNull World world) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setRespawnWorld'"
        );
    }

    @Override
    public @Nullable World getWorld(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorld'"
        );
    }

    @Override
    public @Nullable World getWorld(@NotNull UUID uid) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorld'"
        );
    }

    @Override
    public @Nullable World getWorld(@NotNull Key worldKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorld'"
        );
    }

    @Override
    public @NotNull WorldBorder createWorldBorder() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createWorldBorder'"
        );
    }

    @Override
    public @Nullable MapView getMap(int id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMap'"
        );
    }

    @Override
    public @NotNull MapView createMap(@NotNull World world) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createMap'"
        );
    }

    @Override
    public @NotNull ItemStack createExplorerMap(
        @NotNull World world,
        @NotNull Location location,
        @NotNull StructureType structureType,
        int radius,
        boolean findUnexplored
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createExplorerMap'"
        );
    }

    @Override
    public @Nullable ItemStack createExplorerMap(
        @NotNull World world,
        @NotNull Location location,
        org.bukkit.generator.structure.@NotNull StructureType structureType,
        @NotNull Type mapIcon,
        int radius,
        boolean findUnexplored
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createExplorerMap'"
        );
    }

    @Override
    public void reload() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'reload'"
        );
    }

    @Override
    public void reloadData() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'reloadData'"
        );
    }

    @Override
    public void updateResources() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'updateResources'"
        );
    }

    @Override
    public void updateRecipes() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'updateRecipes'"
        );
    }

    @Override
    public @NotNull Logger getLogger() {
        return this.logger;
    }

    @Override
    public @Nullable PluginCommand getPluginCommand(@NotNull String name) {
        Command cmd = this.commandMap.getCommand(name);
    return (cmd instanceof PluginCommand) ? (PluginCommand) cmd : null;
    }

    @Override
    public void savePlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'savePlayers'"
        );
    }

    @Override
    public boolean dispatchCommand(
        @NotNull CommandSender sender,
        @NotNull String commandLine
    ) throws CommandException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'dispatchCommand'"
        );
    }

    @Override
    public boolean addRecipe(@Nullable Recipe recipe, boolean resendRecipes) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'addRecipe'"
        );
    }

    @Override
    public @NotNull List<Recipe> getRecipesFor(@NotNull ItemStack result) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRecipesFor'"
        );
    }

    @Override
    public @Nullable Recipe getRecipe(@NotNull NamespacedKey recipeKey) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRecipe'"
        );
    }

    @Override
    public @Nullable Recipe getCraftingRecipe(
        @NotNull ItemStack@NotNull [] craftingMatrix,
        @NotNull World world
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getCraftingRecipe'"
        );
    }

    @Override
    public @NotNull ItemCraftResult craftItemResult(
        @NotNull ItemStack@NotNull [] craftingMatrix,
        @NotNull World world,
        @NotNull Player player
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'craftItemResult'"
        );
    }

    @Override
    public @NotNull ItemCraftResult craftItemResult(
        @NotNull ItemStack@NotNull [] craftingMatrix,
        @NotNull World world
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'craftItemResult'"
        );
    }

    @Override
    public @NotNull Iterator<Recipe> recipeIterator() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'recipeIterator'"
        );
    }

    @Override
    public void clearRecipes() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'clearRecipes'"
        );
    }

    @Override
    public void resetRecipes() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'resetRecipes'"
        );
    }

    @Override
    public boolean removeRecipe(
        @NotNull NamespacedKey key,
        boolean resendRecipes
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'removeRecipe'"
        );
    }

    @Override
    public @NotNull Map<String, String[]> getCommandAliases() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getCommandAliases'"
        );
    }

    @Override
    public int getSpawnRadius() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSpawnRadius'"
        );
    }

    @Override
    public void setSpawnRadius(int value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setSpawnRadius'"
        );
    }

    @Override
    public boolean isEnforcingSecureProfiles() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isEnforcingSecureProfiles'"
        );
    }

    @Override
    public boolean isAcceptingTransfers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isAcceptingTransfers'"
        );
    }

    @Override
    public boolean getHideOnlinePlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHideOnlinePlayers'"
        );
    }

    @Override
    public boolean getOnlineMode() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getOnlineMode'"
        );
    }

    @Override
    public @NotNull ServerConfiguration getServerConfig() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getServerConfig'"
        );
    }

    @Override
    public boolean getAllowFlight() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAllowFlight'"
        );
    }

    @Override
    public boolean isHardcore() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isHardcore'"
        );
    }

    @Override
    public void shutdown() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'shutdown'"
        );
    }

    @Override
    public int broadcast(
        @NotNull Component message,
        @NotNull String permission
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'broadcast'"
        );
    }

    @Override
    public @NotNull OfflinePlayer getOfflinePlayer(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getOfflinePlayer'"
        );
    }

    @Override
    public @Nullable OfflinePlayer getOfflinePlayerIfCached(
        @NotNull String name
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getOfflinePlayerIfCached'"
        );
    }

    @Override
    public @NotNull OfflinePlayer getOfflinePlayer(@NotNull UUID id) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getOfflinePlayer'"
        );
    }

    @Override
    public @NotNull PlayerProfile createPlayerProfile(
        @Nullable UUID uniqueId,
        @Nullable String name
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createPlayerProfile'"
        );
    }

    @Override
    public @NotNull PlayerProfile createPlayerProfile(@NotNull UUID uniqueId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createPlayerProfile'"
        );
    }

    @Override
    public @NotNull PlayerProfile createPlayerProfile(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createPlayerProfile'"
        );
    }

    @Override
    public @NotNull Set<String> getIPBans() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getIPBans'"
        );
    }

    @Override
    public void banIP(@NotNull String address) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'banIP'");
    }

    @Override
    public void unbanIP(@NotNull String address) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'unbanIP'"
        );
    }

    @Override
    public void banIP(@NotNull InetAddress address) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'banIP'");
    }

    @Override
    public void unbanIP(@NotNull InetAddress address) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'unbanIP'"
        );
    }

    @Override
    public @NotNull Set<OfflinePlayer> getBannedPlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBannedPlayers'"
        );
    }

    @Override
    public <T extends BanList<?>> @NotNull T getBanList(
        org.bukkit.BanList.@NotNull Type type
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBanList'"
        );
    }

    @Override
    public <B extends BanList<E>, E> @NotNull B getBanList(
        @NotNull BanListType<B> type
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBanList'"
        );
    }

    @Override
    public @NotNull Set<OfflinePlayer> getOperators() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getOperators'"
        );
    }

    @Override
    public @NotNull GameMode getDefaultGameMode() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getDefaultGameMode'"
        );
    }

    @Override
    public void setDefaultGameMode(@NotNull GameMode mode) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setDefaultGameMode'"
        );
    }

    @Override
    public boolean forcesDefaultGameMode() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'forcesDefaultGameMode'"
        );
    }

    @Override
    public @NotNull ConsoleCommandSender getConsoleSender() {
        return new PatchBukkitConsoleCommandSender();
    }

    @Override
    public @NotNull CommandSender createCommandSender(
        @NotNull Consumer<? super Component> feedback
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createCommandSender'"
        );
    }

    @Override
    public @NotNull File getWorldContainer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWorldContainer'"
        );
    }

    @Override
    public @NotNull OfflinePlayer@NotNull [] getOfflinePlayers() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getOfflinePlayers'"
        );
    }

    @Override
    public @NotNull Messenger getMessenger() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMessenger'"
        );
    }

    @Override
    public @NotNull HelpMap getHelpMap() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getHelpMap'"
        );
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        @NotNull InventoryType type
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createInventory'"
        );
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        @NotNull InventoryType type,
        @NotNull Component title
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createInventory'"
        );
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        @NotNull InventoryType type,
        @NotNull String title
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createInventory'"
        );
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        int size
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createInventory'"
        );
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        int size,
        @NotNull Component title
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createInventory'"
        );
    }

    @Override
    public @NotNull Inventory createInventory(
        @Nullable InventoryHolder owner,
        int size,
        @NotNull String title
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createInventory'"
        );
    }

    @Override
    public @NotNull Merchant createMerchant(@Nullable Component title) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createMerchant'"
        );
    }

    @Override
    public @NotNull Merchant createMerchant(@Nullable String title) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createMerchant'"
        );
    }

    @Override
    public int getMaxChainedNeighborUpdates() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMaxChainedNeighborUpdates'"
        );
    }

    @Override
    public @NotNull Merchant createMerchant() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createMerchant'"
        );
    }

    @Override
    public int getSpawnLimit(@NotNull SpawnCategory spawnCategory) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getSpawnLimit'"
        );
    }

    @Override
    public boolean isPrimaryThread() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isPrimaryThread'"
        );
    }

    @Override
    public @NotNull Component motd() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'motd'");
    }

    @Override
    public void motd(@NotNull Component motd) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'motd'");
    }

    @Override
    public @Nullable Component shutdownMessage() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'shutdownMessage'"
        );
    }

    @Override
    public @NotNull String getMotd() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMotd'"
        );
    }

    @Override
    public void setMotd(@NotNull String motd) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setMotd'"
        );
    }

    @Override
    public @NotNull ServerLinks getServerLinks() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getServerLinks'"
        );
    }

    @Override
    public @Nullable String getShutdownMessage() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getShutdownMessage'"
        );
    }

    @Override
    public @NotNull WarningState getWarningState() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getWarningState'"
        );
    }

    @Override
    public @NotNull ItemFactory getItemFactory() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getItemFactory'"
        );
    }

    @Override
    public @NotNull EntityFactory getEntityFactory() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntityFactory'"
        );
    }

    @Override
    public @NotNull ScoreboardManager getScoreboardManager() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getScoreboardManager'"
        );
    }

    @Override
    public @NotNull Criteria getScoreboardCriteria(@NotNull String name) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getScoreboardCriteria'"
        );
    }

    @Override
    public @Nullable CachedServerIcon getServerIcon() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getServerIcon'"
        );
    }

    @Override
    public @NotNull CachedServerIcon loadServerIcon(@NotNull File file)
        throws IllegalArgumentException, Exception {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadServerIcon'"
        );
    }

    @Override
    public @NotNull CachedServerIcon loadServerIcon(
        @NotNull BufferedImage image
    ) throws IllegalArgumentException, Exception {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'loadServerIcon'"
        );
    }

    @Override
    public void setIdleTimeout(int threshold) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setIdleTimeout'"
        );
    }

    @Override
    public int getIdleTimeout() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getIdleTimeout'"
        );
    }

    @Override
    public int getPauseWhenEmptyTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPauseWhenEmptyTime'"
        );
    }

    @Override
    public void setPauseWhenEmptyTime(int seconds) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'setPauseWhenEmptyTime'"
        );
    }

    @Override
    public @NotNull ChunkData createChunkData(@NotNull World world) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createChunkData'"
        );
    }

    @Override
    public @NotNull BossBar createBossBar(
        @Nullable String title,
        @NotNull BarColor color,
        @NotNull BarStyle style,
        @NotNull BarFlag... flags
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createBossBar'"
        );
    }

    @Override
    public @NotNull KeyedBossBar createBossBar(
        @NotNull NamespacedKey key,
        @Nullable String title,
        @NotNull BarColor color,
        @NotNull BarStyle style,
        @NotNull BarFlag... flags
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createBossBar'"
        );
    }

    @Override
    public @NotNull Iterator<KeyedBossBar> getBossBars() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBossBars'"
        );
    }

    @Override
    public @Nullable KeyedBossBar getBossBar(@NotNull NamespacedKey key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getBossBar'"
        );
    }

    @Override
    public boolean removeBossBar(@NotNull NamespacedKey key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'removeBossBar'"
        );
    }

    @Override
    public @Nullable Entity getEntity(@NotNull UUID uuid) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getEntity'"
        );
    }

    @Override
    public double@NotNull [] getTPS() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTPS'"
        );
    }

    @Override
    public long@NotNull [] getTickTimes() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTickTimes'"
        );
    }

    @Override
    public double getAverageTickTime() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAverageTickTime'"
        );
    }

    @Override
    public @NotNull CommandMap getCommandMap() {
        return commandMap;
    }

    @Override
    public @Nullable Advancement getAdvancement(@NotNull NamespacedKey key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAdvancement'"
        );
    }

    @Override
    public @NotNull Iterator<Advancement> advancementIterator() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'advancementIterator'"
        );
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull Material material) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createBlockData'"
        );
    }

    @Override
    public @NotNull BlockData createBlockData(
        @NotNull Material material,
        @Nullable Consumer<? super BlockData> consumer
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createBlockData'"
        );
    }

    @Override
    public @NotNull BlockData createBlockData(@NotNull String data)
        throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createBlockData'"
        );
    }

    @Override
    public @NotNull BlockData createBlockData(
        @Nullable Material material,
        @Nullable String data
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createBlockData'"
        );
    }

    @Override
    public <T extends Keyed> @Nullable Tag<T> getTag(
        @NotNull String registry,
        @NotNull NamespacedKey tag,
        @NotNull Class<T> clazz
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTag'"
        );
    }

    @Override
    public <T extends Keyed> @NotNull Iterable<Tag<T>> getTags(
        @NotNull String registry,
        @NotNull Class<T> clazz
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getTags'"
        );
    }

    @Override
    public @Nullable LootTable getLootTable(@NotNull NamespacedKey key) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getLootTable'"
        );
    }

    @Override
    public @NotNull List<Entity> selectEntities(
        @NotNull CommandSender sender,
        @NotNull String selector
    ) throws IllegalArgumentException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'selectEntities'"
        );
    }

    @Override
    public @NotNull StructureManager getStructureManager() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getStructureManager'"
        );
    }

    @Override
    public <T extends Keyed> @Nullable Registry<T> getRegistry(
        @NotNull Class<T> tClass
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRegistry'"
        );
    }

    @Override
    public @NotNull UnsafeValues getUnsafe() {
        return PatchBukkitUnsafeValues.INSTANCE;
    }

    @Override
    public @NotNull Spigot spigot() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'spigot'"
        );
    }

    @Override
    public void restart() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'restart'"
        );
    }

    @Override
    public void reloadPermissions() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'reloadPermissions'"
        );
    }

    @Override
    public boolean reloadCommandAliases() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'reloadCommandAliases'"
        );
    }

    @Override
    public boolean suggestPlayerNamesWhenNullTabCompletions() {
        return true;
    }

    @Override
    public @NotNull String getPermissionMessage() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPermissionMessage'"
        );
    }

    @Override
    public @NotNull Component permissionMessage() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'permissionMessage'"
        );
    }

    @Override
    public com.destroystokyo.paper.profile.@NotNull PlayerProfile createProfile(
        @NotNull UUID uuid
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createProfile'"
        );
    }

    @Override
    public com.destroystokyo.paper.profile.@NotNull PlayerProfile createProfile(
        @NotNull String name
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createProfile'"
        );
    }

    @Override
    public com.destroystokyo.paper.profile.@NotNull PlayerProfile createProfile(
        @Nullable UUID uuid,
        @Nullable String name
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createProfile'"
        );
    }

    @Override
    public com.destroystokyo.paper.profile.@NotNull PlayerProfile createProfileExact(
        @Nullable UUID uuid,
        @Nullable String name
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'createProfileExact'"
        );
    }

    @Override
    public int getCurrentTick() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getCurrentTick'"
        );
    }

    @Override
    public boolean isStopping() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isStopping'"
        );
    }

    @Override
    public @NotNull MobGoals getMobGoals() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getMobGoals'"
        );
    }

    @Override
    public @NotNull DatapackManager getDatapackManager() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getDatapackManager'"
        );
    }

    @Override
    public @NotNull PotionBrewer getPotionBrewer() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getPotionBrewer'"
        );
    }

    @Override
    public @NotNull RegionScheduler getRegionScheduler() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getRegionScheduler'"
        );
    }

    @Override
    public @NotNull AsyncScheduler getAsyncScheduler() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getAsyncScheduler'"
        );
    }

    @Override
    public @NotNull GlobalRegionScheduler getGlobalRegionScheduler() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'getGlobalRegionScheduler'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        @NotNull Position position
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        @NotNull Position position,
        int squareRadiusChunks
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Location location) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull Location location,
        int squareRadiusChunks
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        int chunkX,
        int chunkZ
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        int chunkX,
        int chunkZ,
        int squareRadiusChunks
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(
        @NotNull World world,
        int minChunkX,
        int minChunkZ,
        int maxChunkX,
        int maxChunkZ
    ) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isOwnedByCurrentRegion(@NotNull Entity entity) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isOwnedByCurrentRegion'"
        );
    }

    @Override
    public boolean isGlobalTickThread() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isGlobalTickThread'"
        );
    }

    @Override
    public boolean isPaused() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'isPaused'"
        );
    }

    @Override
    public void allowPausing(@NotNull Plugin plugin, boolean value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException(
            "Unimplemented method 'allowPausing'"
        );
    }
}
