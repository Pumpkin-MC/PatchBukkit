package org.patchbukkit.world;

import com.google.common.base.Preconditions;
import io.papermc.paper.block.fluid.FluidData;
import io.papermc.paper.entity.poi.PoiSearchResult;
import io.papermc.paper.entity.poi.PoiType;
import io.papermc.paper.entity.poi.PoiType.Occupancy;
import io.papermc.paper.math.Position;
import io.papermc.paper.raytracing.PositionedRayTraceConfigurationBuilder;
import io.papermc.paper.world.MoonPhase;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.DragonBattle;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.world.TimeSkipEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.*;
import org.checkerframework.checker.index.qual.Positive;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.patchbukkit.PatchBukkitServer;
import org.patchbukkit.bridge.BridgeUtils;
import org.patchbukkit.entity.PatchBukkitEntity;
import org.patchbukkit.persistence.PatchBukkitPersistentDataContainer;
import patchbukkit.bridge.NativeBridgeFfi;
import patchbukkit.world.CreateWorldExplosionRequest;
import patchbukkit.world.GetForceLoadedChunksRequest;
import patchbukkit.world.GetWorldBorderRequest;
import patchbukkit.world.GetWorldEntitiesRequest;
import patchbukkit.world.GetWorldGamerulesRequest;
import patchbukkit.world.GetWorldInfoRequest;
import patchbukkit.world.GetWorldInfoResponse;
import patchbukkit.world.PlayWorldEffectRequest;
import patchbukkit.world.PlayWorldSoundRequest;
import patchbukkit.world.SaveWorldRequest;
import patchbukkit.world.SetChunkForceLoadedRequest;
import patchbukkit.world.SetWorldDifficultyRequest;
import patchbukkit.world.SetWorldGameruleRequest;
import patchbukkit.world.SetWorldPvpRequest;
import patchbukkit.world.SetWorldSpawnRequest;
import patchbukkit.world.SetWorldTimeRequest;
import patchbukkit.world.SetWorldWeatherRequest;
import patchbukkit.world.SpawnParticleRequest;
import patchbukkit.world.SpawnWorldEntityRequest;

@SuppressWarnings({ "deprecation", "removal", "unchecked" })
public class PatchBukkitWorld extends PatchBukkitRegionAccessor implements World {
    private static final Map<UUID, PatchBukkitWorld> instances = new ConcurrentHashMap<>();

    private final UUID uuid;
    private final Map<Long, PatchBukkitChunk> loadedChunks = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> registeredEntities = new ConcurrentHashMap<>();
    private final Map<int[], Set<Plugin>> chunkTickets = new ConcurrentHashMap<>();
    private final Map<String, List<MetadataValue>> metadataMap = new ConcurrentHashMap<>();
    private final PersistentDataContainer pdc = new PatchBukkitPersistentDataContainer();
    private final Map<String, String> gameRules = new ConcurrentHashMap<>();

    private String name = "world";
    private Environment environment = Environment.NORMAL;
    private int minHeight = -64;
    private int maxHeight = 320;
    private int logicalHeight = 384;
    private int seaLevel = 63;
    private long seed = 0L;
    private Difficulty difficulty = Difficulty.NORMAL;
    private boolean hardcore = false;
    private boolean pvp = true;
    private boolean autoSave = true;
    private boolean keepSpawnInMemory = true;
    private int spawnX = 0;
    private int spawnY = 64;
    private int spawnZ = 0;
    private float spawnAngle = 0.0f;
    private long time = 0;
    private long fullTime = 0;
    private boolean storm = false;
    private boolean thundering = false;
    private int weatherDuration = 6000;
    private int thunderDuration = 6000;
    private int clearWeatherDuration = 6000;

    private boolean voidDamageEnabled = true;
    private float voidDamageAmount = 4.0f;
    private double voidDamageMinBuildHeightOffset = -64.0;

    private final Spigot spigot = new Spigot() {
        @Override
        public @NotNull LightningStrike strikeLightning(@NotNull Location loc, boolean isSilent) {
            return PatchBukkitWorld.this.strikeLightning(loc);
        }

        @Override
        public @NotNull LightningStrike strikeLightningEffect(@NotNull Location loc, boolean isSilent) {
            return PatchBukkitWorld.this.strikeLightningEffect(loc);
        }
    };

    private PatchBukkitWorld(UUID uuid) {
        this.uuid = uuid;
        initDefaultGameRules();
        syncWorldInfo();
    }

    public static PatchBukkitWorld getOrCreate(UUID uuid) {
        return instances.computeIfAbsent(uuid, PatchBukkitWorld::new);
    }

    public static PatchBukkitWorld getOrCreate(String uuid) {
        return getOrCreate(UUID.fromString(uuid));
    }

    private void initDefaultGameRules() {
        this.gameRules.put("doDaylightCycle", "true");
        this.gameRules.put("doMobSpawning", "true");
        this.gameRules.put("doFireTick", "true");
        this.gameRules.put("keepInventory", "false");
        this.gameRules.put("mobGriefing", "true");
        this.gameRules.put("doWeatherCycle", "true");
        this.gameRules.put("naturalRegeneration", "true");
        this.gameRules.put("announceAdvancements", "true");
        this.gameRules.put("showDeathMessages", "true");
        this.gameRules.put("commandBlockOutput", "true");
    }

    public void syncWorldInfo() {
        try {
            GetWorldInfoResponse res = NativeBridgeFfi.getWorldInfo(GetWorldInfoRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .build());
            if (res != null) {
                this.minHeight = res.getMinHeight();
                this.maxHeight = res.getMaxHeight();
                this.logicalHeight = res.getLogicalHeight();
                this.seaLevel = res.getSeaLevel();
                this.seed = res.getSeed();
                if (!res.getName().isEmpty()) {
                    this.name = res.getName();
                }
                if (!res.getDimension().isEmpty()) {
                    if (res.getDimension().contains("nether")) {
                        this.environment = Environment.NETHER;
                    } else if (res.getDimension().contains("end")) {
                        this.environment = Environment.THE_END;
                    } else {
                        this.environment = Environment.NORMAL;
                    }
                }
                try {
                    this.difficulty = Difficulty.valueOf(res.getDifficulty().toUpperCase());
                } catch (Throwable ignored) {}
                this.hardcore = res.getHardcore();
                this.pvp = res.getPvp();
                this.spawnX = res.getSpawnX();
                this.spawnY = res.getSpawnY();
                this.spawnZ = res.getSpawnZ();
                this.spawnAngle = res.getSpawnAngle();
                this.time = res.getTime();
                this.fullTime = res.getFullTime();
                this.storm = res.getIsStorm();
                this.thundering = res.getIsThundering();
                this.weatherDuration = res.getWeatherDuration();
                this.thunderDuration = res.getThunderDuration();
                this.clearWeatherDuration = res.getClearWeatherDuration();
            }
        } catch (Throwable ignored) {}
    }

    public void registerEntity(Entity entity) {
        if (entity != null) {
            this.registeredEntities.put(entity.getUniqueId(), entity);
        }
    }

    public void unregisterEntity(UUID uid) {
        this.registeredEntities.remove(uid);
    }

    @Override
    public boolean isVoidDamageEnabled() {
        return this.voidDamageEnabled;
    }

    @Override
    public void setVoidDamageEnabled(boolean enabled) {
        this.voidDamageEnabled = enabled;
    }

    @Override
    public float getVoidDamageAmount() {
        return this.voidDamageAmount;
    }

    @Override
    public void setVoidDamageAmount(float voidDamageAmount) {
        this.voidDamageAmount = voidDamageAmount;
    }

    @Override
    public double getVoidDamageMinBuildHeightOffset() {
        return this.voidDamageMinBuildHeightOffset;
    }

    @Override
    public void setVoidDamageMinBuildHeightOffset(double offset) {
        this.voidDamageMinBuildHeightOffset = offset;
    }

    @Override
    public @NotNull Block getBlockAt(int x, int y, int z) {
        return new PatchBukkitBlock(this, x, y, z);
    }

    @Override
    public @NotNull Block getBlockAt(@NotNull Location location) {
        return getBlockAt(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    @Override
    public @NotNull Chunk getChunkAt(int x, int z) {
        return this.loadedChunks.computeIfAbsent(chunkKey(x, z), k -> new PatchBukkitChunk(this, x, z));
    }

    @Override
    public @NotNull Chunk getChunkAt(int x, int z, boolean generate) {
        return getChunkAt(x, z);
    }

    @Override
    public @NotNull Chunk getChunkAt(@NotNull Location location) {
        return getChunkAt(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    @Override
    public @NotNull Chunk getChunkAt(@NotNull Block block) {
        return getChunkAt(block.getX() >> 4, block.getZ() >> 4);
    }

    @Override
    public boolean isChunkLoaded(int x, int z) {
        return true;
    }

    @Override
    public boolean isChunkLoaded(@NotNull Chunk chunk) {
        return true;
    }

    @Override
    public @NotNull Chunk@NotNull [] getLoadedChunks() {
        return this.loadedChunks.values().toArray(new Chunk[0]);
    }

    @Override
    public void loadChunk(int x, int z) {
        getChunkAt(x, z);
    }

    @Override
    public void loadChunk(@NotNull Chunk chunk) {
        loadChunk(chunk.getX(), chunk.getZ());
    }

    @Override
    public boolean loadChunk(int x, int z, boolean generate) {
        getChunkAt(x, z);
        return true;
    }

    @Override
    public boolean unloadChunk(@NotNull Chunk chunk) {
        return unloadChunk(chunk.getX(), chunk.getZ(), true);
    }

    @Override
    public boolean unloadChunk(int x, int z) {
        return unloadChunk(x, z, true);
    }

    @Override
    public boolean unloadChunk(int x, int z, boolean save) {
        this.loadedChunks.remove(chunkKey(x, z));
        return true;
    }

    @Override
    public boolean unloadChunkRequest(int x, int z) {
        return unloadChunk(x, z, true);
    }

    @Override
    public boolean regenerateChunk(int x, int z) {
        return true;
    }

    @Override
    public boolean refreshChunk(int x, int z) {
        return true;
    }

    @Override
    public boolean isChunkGenerated(int x, int z) {
        return true;
    }

    @Override
    public boolean isChunkInUse(int x, int z) {
        return true;
    }

    @Override
    public @NotNull Item dropItem(@NotNull Location loc, @NotNull ItemStack item) {
        return dropItem(loc, item, null);
    }

    @Override
    public @NotNull Item dropItem(@NotNull Location loc, @NotNull ItemStack item, @Nullable Consumer<? super Item> function) {
        UUID entityUuid = UUID.randomUUID();
        try {
            var res = NativeBridgeFfi.spawnWorldEntity(SpawnWorldEntityRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setEntityType("ITEM")
                .setItemType(item.getType().getKey().toString())
                .setItemCount(item.getAmount())
                .setX(loc.getX())
                .setY(loc.getY())
                .setZ(loc.getZ())
                .build());
            if (res != null && res.hasEntityUuid()) {
                entityUuid = UUID.fromString(res.getEntityUuid().getValue());
            }
        } catch (Throwable ignored) {}

        Item itemEntity = (Item) PatchBukkitEntity.create(entityUuid, EntityType.ITEM, loc);
        if (function != null) {
            function.accept(itemEntity);
        }
        registerEntity(itemEntity);
        return itemEntity;
    }

    @Override
    public @NotNull Item dropItemNaturally(@NotNull Location loc, @NotNull ItemStack item) {
        return dropItem(loc, item);
    }

    @Override
    public @NotNull Item dropItemNaturally(@NotNull Location loc, @NotNull ItemStack item, @Nullable Consumer<? super Item> function) {
        return dropItem(loc, item, function);
    }

    @Override
    public @NotNull Arrow spawnArrow(@NotNull Location loc, @NotNull Vector velocity, float speed, float spread) {
        Arrow arrow = (Arrow) PatchBukkitEntity.create(UUID.randomUUID(), EntityType.ARROW, loc);
        arrow.setVelocity(velocity.clone().normalize().multiply(speed));
        registerEntity(arrow);
        return arrow;
    }

    @Override
    public <T extends AbstractArrow> @NotNull T spawnArrow(@NotNull Location location, @NotNull Vector direction, float speed, float spread, @NotNull Class<T> clazz) {
        T arrow = (T) PatchBukkitEntity.create(UUID.randomUUID(), EntityType.ARROW, location);
        arrow.setVelocity(direction.clone().normalize().multiply(speed));
        registerEntity(arrow);
        return arrow;
    }

    @Override
    public @NotNull LightningStrike strikeLightning(@NotNull Location loc) {
        UUID entityUuid = UUID.randomUUID();
        try {
            var res = NativeBridgeFfi.spawnWorldEntity(SpawnWorldEntityRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setEntityType("LIGHTNING_BOLT")
                .setX(loc.getX())
                .setY(loc.getY())
                .setZ(loc.getZ())
                .build());
            if (res != null && res.hasEntityUuid()) {
                entityUuid = UUID.fromString(res.getEntityUuid().getValue());
            }
        } catch (Throwable ignored) {}

        LightningStrike bolt = (LightningStrike) PatchBukkitEntity.create(entityUuid, EntityType.LIGHTNING_BOLT, loc);
        registerEntity(bolt);
        return bolt;
    }

    @Override
    public @NotNull LightningStrike strikeLightningEffect(@NotNull Location loc) {
        return strikeLightning(loc);
    }

    @Override
    public @Nullable Location findLightningRod(@NotNull Location location) {
        return null;
    }

    @Override
    public @Nullable Location findLightningTarget(@NotNull Location location) {
        return location.clone();
    }

    @Override
    public @NotNull List<Entity> getEntities() {
        List<Entity> list = new ArrayList<>(this.registeredEntities.values());
        for (Player player : getPlayers()) {
            if (!list.contains(player)) {
                list.add(player);
            }
        }
        try {
            var res = NativeBridgeFfi.getWorldEntities(GetWorldEntitiesRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .build());
            if (res != null) {
                for (var summary : res.getEntitiesList()) {
                    if (summary.hasUuid()) {
                        UUID u = UUID.fromString(summary.getUuid().getValue());
                        if (!this.registeredEntities.containsKey(u)) {
                            EntityType type = EntityType.UNKNOWN;
                            try {
                                type = EntityType.valueOf(summary.getEntityType());
                            } catch (Throwable ignored) {}
                            Location loc = new Location(this, summary.getX(), summary.getY(), summary.getZ(), summary.getYaw(), summary.getPitch());
                            Entity e = PatchBukkitEntity.create(u, type, loc);
                            list.add(e);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    @Override
    public @NotNull List<LivingEntity> getLivingEntities() {
        List<LivingEntity> living = new ArrayList<>();
        for (Entity e : getEntities()) {
            if (e instanceof LivingEntity le) {
                living.add(le);
            }
        }
        return living;
    }

    @Override
    public @NotNull List<Player> getPlayers() {
        List<Player> list = new ArrayList<>();
        if (Bukkit.getServer() instanceof PatchBukkitServer server) {
            for (Player p : server.getOnlinePlayers()) {
                if (p.getWorld().getUID().equals(this.uuid)) {
                    list.add(p);
                }
            }
        }
        return list;
    }

    @Override
    public @NotNull Collection<Entity> getNearbyEntities(@NotNull Location location, double x, double y, double z) {
        return getNearbyEntities(location, x, y, z, null);
    }

    @Override
    public @NotNull Collection<Entity> getNearbyEntities(@NotNull Location location, double x, double y, double z, @Nullable Predicate<? super Entity> filter) {
        List<Entity> list = new ArrayList<>();
        double minX = location.getX() - x;
        double maxX = location.getX() + x;
        double minY = location.getY() - y;
        double maxY = location.getY() + y;
        double minZ = location.getZ() - z;
        double maxZ = location.getZ() + z;

        for (Entity e : getEntities()) {
            Location loc = e.getLocation();
            if (loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ) {
                if (filter == null || filter.test(e)) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    @Override
    public @NotNull Collection<Entity> getNearbyEntities(@NotNull BoundingBox boundingBox) {
        return getNearbyEntities(boundingBox, null);
    }

    @Override
    public @NotNull Collection<Entity> getNearbyEntities(@NotNull BoundingBox boundingBox, @Nullable Predicate<? super Entity> filter) {
        List<Entity> list = new ArrayList<>();
        for (Entity e : getEntities()) {
            Location loc = e.getLocation();
            if (boundingBox.contains(loc.getX(), loc.getY(), loc.getZ())) {
                if (filter == null || filter.test(e)) {
                    list.add(e);
                }
            }
        }
        return list;
    }

    @Override
    public @NotNull String getName() {
        return this.name;
    }

    @Override
    public @NotNull UUID getUID() {
        return this.uuid;
    }

    @Override
    public @NotNull NamespacedKey getKey() {
        return NamespacedKey.minecraft(this.name.toLowerCase().replace(' ', '_'));
    }

    @Override
    public @NotNull Location getSpawnLocation() {
        return new Location(this, this.spawnX, this.spawnY, this.spawnZ, this.spawnAngle, 0.0f);
    }

    @Override
    public boolean setSpawnLocation(@NotNull Location location) {
        return setSpawnLocation(location.getBlockX(), location.getBlockY(), location.getBlockZ(), location.getYaw());
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z, float angle) {
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.spawnAngle = angle;
        try {
            NativeBridgeFfi.setWorldSpawn(SetWorldSpawnRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setX(x)
                .setY(y)
                .setZ(z)
                .setAngle(angle)
                .build());
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public boolean setSpawnLocation(int x, int y, int z) {
        return setSpawnLocation(x, y, z, 0.0f);
    }

    @Override
    public long getTime() {
        return this.time;
    }

    @Override
    public void setTime(long time) {
        this.time = time;
        try {
            NativeBridgeFfi.setWorldTime(SetWorldTimeRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setTime(time)
                .setFullTime(-1)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public long getFullTime() {
        return this.fullTime;
    }

    @Override
    public void setFullTime(long time) {
        this.fullTime = time;
        try {
            NativeBridgeFfi.setWorldTime(SetWorldTimeRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setTime(-1)
                .setFullTime(time)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public long getGameTime() {
        return this.fullTime;
    }

    @Override
    public boolean hasStorm() {
        return this.storm;
    }

    @Override
    public void setStorm(boolean hasStorm) {
        this.storm = hasStorm;
        try {
            NativeBridgeFfi.setWorldWeather(SetWorldWeatherRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setStorm(hasStorm)
                .setThundering(this.thundering)
                .setWeatherDuration(this.weatherDuration)
                .setThunderDuration(this.thunderDuration)
                .setClearWeatherDuration(this.clearWeatherDuration)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public int getWeatherDuration() {
        return this.weatherDuration;
    }

    @Override
    public void setWeatherDuration(int duration) {
        this.weatherDuration = duration;
        setStorm(this.storm);
    }

    @Override
    public boolean isThundering() {
        return this.thundering;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.thundering = thundering;
        try {
            NativeBridgeFfi.setWorldWeather(SetWorldWeatherRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setStorm(this.storm)
                .setThundering(thundering)
                .setWeatherDuration(this.weatherDuration)
                .setThunderDuration(this.thunderDuration)
                .setClearWeatherDuration(this.clearWeatherDuration)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public int getThunderDuration() {
        return this.thunderDuration;
    }

    @Override
    public void setThunderDuration(int duration) {
        this.thunderDuration = duration;
        setThundering(this.thundering);
    }

    @Override
    public boolean isClearWeather() {
        return !this.storm && !this.thundering;
    }

    @Override
    public void setClearWeatherDuration(int duration) {
        this.clearWeatherDuration = duration;
        this.storm = false;
        this.thundering = false;
        setStorm(false);
    }

    @Override
    public int getClearWeatherDuration() {
        return this.clearWeatherDuration;
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power) {
        return createExplosion(x, y, z, power, false, true, null);
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire) {
        return createExplosion(x, y, z, power, setFire, true, null);
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks) {
        return createExplosion(x, y, z, power, setFire, breakBlocks, null);
    }

    @Override
    public boolean createExplosion(double x, double y, double z, float power, boolean setFire, boolean breakBlocks, @Nullable Entity source) {
        try {
            NativeBridgeFfi.createWorldExplosion(CreateWorldExplosionRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setX(x)
                .setY(y)
                .setZ(z)
                .setPower(power)
                .setSetFire(setFire)
                .setBreakBlocks(breakBlocks)
                .build());
            return true;
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power) {
        return createExplosion(loc.getX(), loc.getY(), loc.getZ(), power);
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power, boolean setFire) {
        return createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, setFire);
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power, boolean setFire, boolean breakBlocks) {
        return createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, setFire, breakBlocks);
    }

    @Override
    public boolean createExplosion(@NotNull Location loc, float power, boolean setFire, boolean breakBlocks, @Nullable Entity source) {
        return createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, setFire, breakBlocks, source);
    }

    @Override
    public boolean createExplosion(@Nullable Entity source, @NotNull Location loc, float power, boolean setFire, boolean breakBlocks, boolean createSmoke) {
        return createExplosion(loc.getX(), loc.getY(), loc.getZ(), power, setFire, breakBlocks, source);
    }

    @Override
    public @NotNull Environment getEnvironment() {
        return this.environment;
    }

    @Override
    public long getSeed() {
        return this.seed;
    }

    @Override
    public boolean getPVP() {
        return this.pvp;
    }

    @Override
    public void setPVP(boolean pvp) {
        this.pvp = pvp;
        try {
            NativeBridgeFfi.setWorldPvp(SetWorldPvpRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setPvp(pvp)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public @Nullable ChunkGenerator getGenerator() {
        return null;
    }

    @Override
    public @Nullable BiomeProvider getBiomeProvider() {
        return null;
    }

    @Override
    public @NotNull BiomeProvider vanillaBiomeProvider() {
        return new BiomeProvider() {
            @Override
            public @NotNull Biome getBiome(@NotNull WorldInfo worldInfo, int x, int y, int z) {
                return PatchBukkitWorld.this.getBiome(x, y, z);
            }

            @Override
            public @NotNull List<Biome> getBiomes(@NotNull WorldInfo worldInfo) {
                return List.of(Biome.PLAINS);
            }
        };
    }

    @Override
    public void save() {
        try {
            NativeBridgeFfi.saveWorld(SaveWorldRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public void save(boolean flush) {
        save();
    }

    @Override
    public @NotNull List<BlockPopulator> getPopulators() {
        return Collections.emptyList();
    }

    @Override
    public @NotNull FallingBlock spawnFallingBlock(@NotNull Location location, @NotNull BlockData data) throws IllegalArgumentException {
        FallingBlock entity = (FallingBlock) PatchBukkitEntity.create(UUID.randomUUID(), EntityType.FALLING_BLOCK, location);
        registerEntity(entity);
        return entity;
    }

    @Override
    public @NotNull FallingBlock spawnFallingBlock(@NotNull Location location, @NotNull MaterialData data) throws IllegalArgumentException {
        return spawnFallingBlock(location, data.getItemType().createBlockData());
    }

    @Override
    public @NotNull FallingBlock spawnFallingBlock(@NotNull Location location, @NotNull Material material, byte data) throws IllegalArgumentException {
        return spawnFallingBlock(location, material.createBlockData());
    }

    @Override
    public void playEffect(@NotNull Location location, @NotNull Effect effect, int data) {
        playEffect(location, effect, data, 64);
    }

    @Override
    public void playEffect(@NotNull Location location, @NotNull Effect effect, int data, int radius) {
        if (location == null || effect == null) {
            return;
        }
        try {
            NativeBridgeFfi.playWorldEffect(PlayWorldEffectRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setEffectId(effect.getId())
                .setX(location.getBlockX())
                .setY(location.getBlockY())
                .setZ(location.getBlockZ())
                .setData(data)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public <T> void playEffect(@NotNull Location location, @NotNull Effect effect, @Nullable T data) {
        playEffect(location, effect, data, 64);
    }

    @Override
    public <T> void playEffect(@NotNull Location location, @NotNull Effect effect, @Nullable T data, int radius) {
        int raw = (data instanceof Number number) ? number.intValue() : 0;
        playEffect(location, effect, raw, radius);
    }

    @Override
    public boolean hasStructureAt(@NotNull Position position, @NotNull Structure structure) {
        return false;
    }

    @Override
    public @NotNull Collection<GeneratedStructure> getStructures(int x, int z) {
        return Collections.emptyList();
    }

    @Override
    public @NotNull Collection<GeneratedStructure> getStructures(int x, int z, @NotNull Structure structure) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable Location locateNearestStructure(@NotNull Location origin, @NotNull StructureType structureType, int radius, boolean findUnexplored) {
        return null;
    }

    @Override
    public @NotNull String@NotNull [] getGameRules() {
        return this.gameRules.keySet().toArray(new String[0]);
    }

    @Override
    public @Nullable String getGameRuleValue(@Nullable String rule) {
        if (rule == null) return null;
        return this.gameRules.get(rule);
    }

    @Override
    public boolean setGameRuleValue(@NotNull String rule, @NotNull String value) {
        this.gameRules.put(rule, value);
        try {
            NativeBridgeFfi.setWorldGamerule(SetWorldGameruleRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setRule(rule)
                .setValue(value)
                .build());
            return true;
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean isGameRule(@NotNull String rule) {
        return this.gameRules.containsKey(rule);
    }

    @Override
    public <T> @Nullable T getGameRuleValue(@NotNull GameRule<T> rule) {
        String val = this.gameRules.get(rule.getName());
        if (val == null) return rule.getDefaultValue();
        if (rule.getType() == Boolean.class) {
            return (T) Boolean.valueOf(val);
        } else if (rule.getType() == Integer.class) {
            try {
                return (T) Integer.valueOf(val);
            } catch (Throwable ignored) {}
        }
        return rule.getDefaultValue();
    }

    @Override
    public <T> @Nullable T getGameRuleDefault(@NotNull GameRule<T> rule) {
        return rule.getDefaultValue();
    }

    @Override
    public <T> boolean setGameRule(@NotNull GameRule<T> rule, @NotNull T newValue) {
        return setGameRuleValue(rule.getName(), String.valueOf(newValue));
    }

    @Override
    public @NotNull WorldBorder getWorldBorder() {
        return new PatchBukkitWorldBorder(this);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, 0, 0, 0, 0, null, false);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count) {
        spawnParticle(particle, x, y, z, count, 0, 0, 0, 0, null, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, @Nullable T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, 0, 0, 0, 0, data, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, @Nullable T data) {
        spawnParticle(particle, x, y, z, count, 0, 0, 0, 0, data, false);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, 0, null, false);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 0, null, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, @Nullable T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, 0, data, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, @Nullable T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, 0, data, false);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, null, false);
    }

    @Override
    public void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, null, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, false);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @NotNull Location location, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data, boolean force) {
        spawnParticle(particle, location.getX(), location.getY(), location.getZ(), count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data, boolean force) {
        try {
            NativeBridgeFfi.spawnParticle(SpawnParticleRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setParticle(particle.name())
                .setX(x)
                .setY(y)
                .setZ(z)
                .setCount(count)
                .setOffsetX(offsetX)
                .setOffsetY(offsetY)
                .setOffsetZ(offsetZ)
                .setExtra(extra)
                .setForce(force)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, float volume, float pitch) {
        playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, float volume, float pitch) {
        playSound(location, sound, SoundCategory.MASTER, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(location, sound.name().toLowerCase().replace('_', '.'), category, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {
        try {
            NativeBridgeFfi.playWorldSound(PlayWorldSoundRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setSound(sound)
                .setCategory(category.name())
                .setX(location.getX())
                .setY(location.getY())
                .setZ(location.getZ())
                .setVolume(volume)
                .setPitch(pitch)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        playSound(location, sound, category, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Location location, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        playSound(location, sound, category, volume, pitch);
    }

    @Override
    public @Nullable StructureSearchResult locateNearestStructure(@NotNull Location origin, @NotNull Structure structure, int radius, boolean findUnexplored) {
        return null;
    }

    @Override
    public @Nullable StructureSearchResult locateNearestStructure(@NotNull Location origin, org.bukkit.generator.structure.@NotNull StructureType structureType, int radius, boolean findUnexplored) {
        return null;
    }

    @Override
    public @Nullable BiomeSearchResult locateNearestBiome(@NotNull Location origin, int radius, @NotNull Biome... biomes) {
        return null;
    }

    @Override
    public @Nullable BiomeSearchResult locateNearestBiome(@NotNull Location origin, int radius, int horizontalInterval, int verticalInterval, @NotNull Biome... biomes) {
        return null;
    }

    @Override
    public int getMinHeight() {
        return this.minHeight;
    }

    @Override
    public int getMaxHeight() {
        return this.maxHeight;
    }

    @Override
    public int getLogicalHeight() {
        return this.logicalHeight;
    }

    @Override
    public int getSeaLevel() {
        return this.seaLevel;
    }

    @Override
    public boolean getKeepSpawnInMemory() {
        return this.keepSpawnInMemory;
    }

    @Override
    public void setKeepSpawnInMemory(boolean keepLoaded) {
        this.keepSpawnInMemory = keepLoaded;
    }

    @Override
    public boolean isAutoSave() {
        return this.autoSave;
    }

    @Override
    public void setAutoSave(boolean value) {
        this.autoSave = value;
    }

    @Override
    public void setDifficulty(@NotNull Difficulty difficulty) {
        this.difficulty = difficulty;
        try {
            NativeBridgeFfi.setWorldDifficulty(SetWorldDifficultyRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setDifficulty(difficulty.name())
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public @NotNull Difficulty getDifficulty() {
        return this.difficulty;
    }

    @Override
    public @NotNull File getWorldFolder() {
        return new File(this.name);
    }

    @Override
    public @NotNull WorldType getWorldType() {
        return WorldType.NORMAL;
    }

    @Override
    public boolean canGenerateStructures() {
        return true;
    }

    @Override
    public boolean isHardcore() {
        return this.hardcore;
    }

    @Override
    public void setHardcore(boolean hardcore) {
        this.hardcore = hardcore;
    }

    @Override
    public long getTicksPerAnimalSpawns() {
        return 400L;
    }

    @Override
    public void setTicksPerAnimalSpawns(int ticksPerAnimalSpawns) {
    }

    @Override
    public long getTicksPerMonsterSpawns() {
        return 1L;
    }

    @Override
    public void setTicksPerMonsterSpawns(int ticksPerMonsterSpawns) {
    }

    @Override
    public long getTicksPerWaterSpawns() {
        return 1L;
    }

    @Override
    public void setTicksPerWaterSpawns(int ticksPerWaterSpawns) {
    }

    @Override
    public long getTicksPerWaterAmbientSpawns() {
        return 1L;
    }

    @Override
    public void setTicksPerWaterAmbientSpawns(int ticksPerWaterAmbientSpawns) {
    }

    @Override
    public long getTicksPerWaterUndergroundCreatureSpawns() {
        return 1L;
    }

    @Override
    public void setTicksPerWaterUndergroundCreatureSpawns(int ticksPerWaterUndergroundCreatureSpawns) {
    }

    @Override
    public long getTicksPerAmbientSpawns() {
        return 1L;
    }

    @Override
    public void setTicksPerAmbientSpawns(int ticksPerAmbientSpawns) {
    }

    @Override
    public long getTicksPerSpawns(@NotNull SpawnCategory spawnCategory) {
        return 1L;
    }

    @Override
    public void setTicksPerSpawns(@NotNull SpawnCategory spawnCategory, int ticksPerSpawns) {
    }

    @Override
    public int getMonsterSpawnLimit() {
        return 70;
    }

    @Override
    public void setMonsterSpawnLimit(int limit) {
    }

    @Override
    public int getAnimalSpawnLimit() {
        return 10;
    }

    @Override
    public void setAnimalSpawnLimit(int limit) {
    }

    @Override
    public int getWaterAnimalSpawnLimit() {
        return 5;
    }

    @Override
    public void setWaterAnimalSpawnLimit(int limit) {
    }

    @Override
    public int getWaterAmbientSpawnLimit() {
        return 20;
    }

    @Override
    public void setWaterAmbientSpawnLimit(int limit) {
    }

    @Override
    public int getWaterUndergroundCreatureSpawnLimit() {
        return 5;
    }

    @Override
    public void setWaterUndergroundCreatureSpawnLimit(int limit) {
    }

    @Override
    public int getAmbientSpawnLimit() {
        return 15;
    }

    @Override
    public void setAmbientSpawnLimit(int limit) {
    }

    @Override
    public int getSpawnLimit(@NotNull SpawnCategory spawnCategory) {
        return 10;
    }

    @Override
    public void setSpawnLimit(@NotNull SpawnCategory spawnCategory, int limit) {
    }

    @Override
    public void setMetadata(@NotNull String metadataKey, @NotNull MetadataValue newMetadataValue) {
        List<MetadataValue> list = this.metadataMap.computeIfAbsent(metadataKey, k -> new ArrayList<>());
        list.removeIf(v -> v.getOwningPlugin() == newMetadataValue.getOwningPlugin());
        list.add(newMetadataValue);
    }

    @Override
    public @NotNull List<MetadataValue> getMetadata(@NotNull String metadataKey) {
        List<MetadataValue> list = this.metadataMap.get(metadataKey);
        return list != null ? Collections.unmodifiableList(new ArrayList<>(list)) : Collections.emptyList();
    }

    @Override
    public boolean hasMetadata(@NotNull String metadataKey) {
        List<MetadataValue> list = this.metadataMap.get(metadataKey);
        return list != null && !list.isEmpty();
    }

    @Override
    public void removeMetadata(@NotNull String metadataKey, @NotNull Plugin owningPlugin) {
        List<MetadataValue> list = this.metadataMap.get(metadataKey);
        if (list != null) {
            list.removeIf(v -> v.getOwningPlugin() == owningPlugin);
            if (list.isEmpty()) {
                this.metadataMap.remove(metadataKey);
            }
        }
    }

    @Override
    public void sendPluginMessage(@NotNull Plugin source, @NotNull String channel, byte@NotNull [] message) {
        Bukkit.getServer().sendPluginMessage(source, channel, message);
    }

    @Override
    public @NotNull Set<String> getListeningPluginChannels() {
        return Bukkit.getServer().getListeningPluginChannels();
    }

    @Override
    public @NotNull PersistentDataContainer getPersistentDataContainer() {
        return this.pdc;
    }

    @Override
    public void setChunkForceLoaded(int x, int z, boolean forced) {
        try {
            NativeBridgeFfi.setChunkForceLoaded(SetChunkForceLoadedRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .setX(x)
                .setZ(z)
                .setForced(forced)
                .build());
        } catch (Throwable ignored) {}
    }

    @Override
    public boolean isChunkForceLoaded(int x, int z) {
        for (Chunk c : getForceLoadedChunks()) {
            if (c.getX() == x && c.getZ() == z) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @NotNull Collection<Chunk> getForceLoadedChunks() {
        List<Chunk> list = new ArrayList<>();
        try {
            var res = NativeBridgeFfi.getForceLoadedChunks(GetForceLoadedChunksRequest.newBuilder()
                .setWorldUuid(BridgeUtils.convertUuid(this.uuid))
                .build());
            if (res != null) {
                for (var coord : res.getChunksList()) {
                    list.add(getChunkAt(coord.getX(), coord.getZ()));
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    @Override
    public boolean addPluginChunkTicket(int x, int z, @NotNull Plugin plugin) {
        int[] key = new int[] { x, z };
        return this.chunkTickets.computeIfAbsent(key, k -> new HashSet<>()).add(plugin);
    }

    @Override
    public boolean removePluginChunkTicket(int x, int z, @NotNull Plugin plugin) {
        int[] key = new int[] { x, z };
        Set<Plugin> set = this.chunkTickets.get(key);
        return set != null && set.remove(plugin);
    }

    @Override
    public void removePluginChunkTickets(@NotNull Plugin plugin) {
        for (Set<Plugin> set : this.chunkTickets.values()) {
            set.remove(plugin);
        }
    }

    @Override
    public @NotNull Collection<Plugin> getPluginChunkTickets(int x, int z) {
        int[] key = new int[] { x, z };
        Set<Plugin> set = this.chunkTickets.get(key);
        return set != null ? Collections.unmodifiableSet(new HashSet<>(set)) : Collections.emptySet();
    }

    @Override
    public @NotNull Map<Plugin, Collection<Chunk>> getPluginChunkTickets() {
        Map<Plugin, Collection<Chunk>> map = new HashMap<>();
        for (Map.Entry<int[], Set<Plugin>> entry : this.chunkTickets.entrySet()) {
            Chunk c = getChunkAt(entry.getKey()[0], entry.getKey()[1]);
            for (Plugin p : entry.getValue()) {
                map.computeIfAbsent(p, k -> new ArrayList<>()).add(c);
            }
        }
        return map;
    }

    @Override
    public int getViewDistance() {
        return Bukkit.getServer().getViewDistance();
    }

    @Override
    public void setViewDistance(int viewDistance) {
    }

    @Override
    public int getSimulationDistance() {
        return Bukkit.getServer().getSimulationDistance();
    }

    @Override
    public void setSimulationDistance(int simulationDistance) {
    }

    @Override
    public int getSendViewDistance() {
        return getViewDistance();
    }

    @Override
    public void setSendViewDistance(int viewDistance) {
    }

    @Override
    public @NotNull Spigot spigot() {
        return this.spigot;
    }

    @Override
    public @Nullable RayTraceResult rayTraceBlocks(@NotNull Location start, @NotNull Vector direction, double maxDistance) {
        return rayTraceBlocks(start, direction, maxDistance, FluidCollisionMode.NEVER, false);
    }

    @Override
    public @Nullable RayTraceResult rayTraceBlocks(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode) {
        return rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode, false);
    }

    @Override
    public @Nullable RayTraceResult rayTraceBlocks(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks) {
        Vector dir = direction.clone().normalize().multiply(0.2);
        Location current = start.clone();
        double traveled = 0;
        while (traveled < maxDistance) {
            current.add(dir);
            traveled += 0.2;
            Block b = getBlockAt(current);
            Material type = b.getType();
            if (type != Material.AIR && type != Material.CAVE_AIR && type != Material.VOID_AIR) {
                if (!ignorePassableBlocks || b.isCollidable()) {
                    return new RayTraceResult(current.toVector(), b, null);
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance) {
        return rayTraceEntities(start, direction, maxDistance, 0.0, null);
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance, double raySize) {
        return rayTraceEntities(start, direction, maxDistance, raySize, null);
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance, @Nullable Predicate<? super Entity> filter) {
        return rayTraceEntities(start, direction, maxDistance, 0.0, filter);
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(@NotNull Location start, @NotNull Vector direction, double maxDistance, double raySize, @Nullable Predicate<? super Entity> filter) {
        Vector dir = direction.clone().normalize().multiply(0.2);
        Location current = start.clone();
        double traveled = 0;
        while (traveled < maxDistance) {
            current.add(dir);
            traveled += 0.2;
            Collection<Entity> nearby = getNearbyEntities(current, 0.5 + raySize, 0.5 + raySize, 0.5 + raySize, filter);
            if (!nearby.isEmpty()) {
                Entity hit = nearby.iterator().next();
                return new RayTraceResult(current.toVector(), hit, null);
            }
        }
        return null;
    }

    @Override
    public @Nullable RayTraceResult rayTrace(@NotNull Location start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, @Nullable Predicate<? super Entity> filter) {
        RayTraceResult blockHit = rayTraceBlocks(start, direction, maxDistance, fluidCollisionMode, ignorePassableBlocks);
        RayTraceResult entityHit = rayTraceEntities(start, direction, maxDistance, raySize, filter);
        if (blockHit == null) return entityHit;
        if (entityHit == null) return blockHit;
        double blockDist = blockHit.getHitPosition().distanceSquared(start.toVector());
        double entityDist = entityHit.getHitPosition().distanceSquared(start.toVector());
        return blockDist <= entityDist ? blockHit : entityHit;
    }

    @Override
    public int getEntityCount() {
        return getEntities().size();
    }

    @Override
    public int getPlayerCount() {
        return getPlayers().size();
    }

    @Override
    public int getTickableTileEntityCount() {
        return 0;
    }

    @Override
    public int getTileEntityCount() {
        return 0;
    }

    @Override
    public int getChunkCount() {
        return this.loadedChunks.size();
    }

    @Override
    public @Nullable DragonBattle getEnderDragonBattle() {
        return null;
    }

    @Override
    public boolean lineOfSightExists(@NotNull Location from, @NotNull Location to) {
        Vector dir = to.toVector().subtract(from.toVector());
        double dist = dir.length();
        if (dist <= 0) return true;
        RayTraceResult hit = rayTraceBlocks(from, dir, dist, FluidCollisionMode.NEVER, true);
        return hit == null;
    }

    @Override
    public boolean hasRaids() {
        return false;
    }

    @Override
    public @Nullable org.bukkit.Raid locateNearestRaid(@NotNull Location location, int radius) {
        return null;
    }

    @Override
    public @Nullable org.bukkit.Raid getRaid(int id) {
        return null;
    }

    @Override
    public @NotNull List<org.bukkit.Raid> getRaids() {
        return Collections.emptyList();
    }

    @Override
    public <T> void spawnParticle(@NotNull Particle particle, @Nullable List<Player> receivers, @Nullable Player source, double x, double y, double z, int count, double offsetX, double offsetY, double offsetZ, double extra, @Nullable T data, boolean force) {
        spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, data, force);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(entity.getLocation(), sound, category, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch) {
        playSound(entity.getLocation(), sound, category, volume, pitch);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull Sound sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        playSound(entity.getLocation(), sound, category, volume, pitch, seed);
    }

    @Override
    public void playSound(@NotNull Entity entity, @NotNull String sound, @NotNull SoundCategory category, float volume, float pitch, long seed) {
        playSound(entity.getLocation(), sound, category, volume, pitch, seed);
    }

    @Override
    public @Nullable Location locateNearestPoi(@NotNull Location location, @NotNull PoiType poiType, int radius, @NotNull Occupancy occupancy) {
        return null;
    }

    @Override
    public @NotNull List<PoiSearchResult> locateAllPoiInRange(@NotNull Location location, @NotNull Predicate<PoiType> predicate, int radius, @NotNull Occupancy occupancy) {
        return Collections.emptyList();
    }

    @Override
    public double getCoordinateScale() {
        return this.environment == Environment.NETHER ? 8.0 : 1.0;
    }

    @Override
    public boolean isFixedTime() {
        return this.environment != Environment.NORMAL;
    }

    @Override
    public @NotNull Collection<Material> getInfiniburn() {
        return Collections.singleton(Material.NETHERRACK);
    }

    @Override
    public void sendGameEvent(@Nullable Entity sourceEntity, @NotNull org.bukkit.GameEvent gameEvent, @NotNull Vector position) {
    }

    @Override
    public boolean hasBonusChest() {
        return false;
    }

    @Override
    public @NotNull Path getWorldPath() {
        return Path.of(this.name);
    }

    @Override
    public boolean isDayTime() {
        return (this.time % 24000) < 12000;
    }

    @Override
    public boolean getAllowAnimals() {
        return true;
    }

    @Override
    public boolean getAllowMonsters() {
        return true;
    }

    @Override
    public @NotNull ChunkSnapshot getEmptyChunkSnapshot(int x, int z, boolean includeBiome, boolean includeBiomeTempRain) {
        return getChunkAt(x, z).getChunkSnapshot(false, includeBiome, includeBiomeTempRain);
    }

    @Override
    public double getTemperature(int x, int y, int z) {
        return 0.8;
    }

    @Override
    public double getHumidity(int x, int y, int z) {
        return 0.5;
    }

    @Override
    public boolean isNatural() {
        return this.environment == Environment.NORMAL;
    }

    @Override
    public boolean isBedWorks() {
        return this.environment == Environment.NORMAL;
    }

    @Override
    public boolean hasSkyLight() {
        return this.environment != Environment.NETHER;
    }

    @Override
    public boolean hasCeiling() {
        return this.environment == Environment.NETHER;
    }

    @Override
    public boolean isPiglinSafe() {
        return this.environment == Environment.NETHER;
    }

    @Override
    public boolean isRespawnAnchorWorks() {
        return this.environment == Environment.NETHER;
    }

    @Override
    public boolean isUltraWarm() {
        return this.environment == Environment.NETHER;
    }

    @Override
    public @Nullable Entity getEntity(@NotNull UUID uuid) {
        for (Entity e : getEntities()) {
            if (e.getUniqueId().equals(uuid)) {
                return e;
            }
        }
        return null;
    }

    @Override
    public @Nullable RayTraceResult rayTraceBlocks(@NotNull Position start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, @Nullable Predicate<? super Block> filter) {
        return rayTraceBlocks(new Location(this, start.x(), start.y(), start.z()), direction, maxDistance, fluidCollisionMode, ignorePassableBlocks);
    }

    @Override
    public @Nullable RayTraceResult rayTrace(@NotNull Position start, @NotNull Vector direction, double maxDistance, @NotNull FluidCollisionMode fluidCollisionMode, boolean ignorePassableBlocks, double raySize, @Nullable Predicate<? super Entity> entityFilter, @Nullable Predicate<? super Block> blockFilter) {
        return rayTrace(new Location(this, start.x(), start.y(), start.z()), direction, maxDistance, fluidCollisionMode, ignorePassableBlocks, raySize, entityFilter);
    }

    @Override
    public void setBiome(int x, int z, @NotNull Biome biome) {
        setBiome(x, 64, z, biome);
    }

    @Override
    public void setSpawnFlags(boolean allowMonsters, boolean allowAnimals) {
    }

    @Override
    public void setAllowMonsterSpawning(boolean allow) {
    }

    @Override
    public <T extends LivingEntity> @NotNull T spawn(@NotNull Location location, @NotNull Class<T> clazz, @NotNull CreatureSpawnEvent.SpawnReason reason, boolean randomizeData, @Nullable Consumer<? super T> function) throws IllegalArgumentException {
        return (T) spawn(location, (Class) clazz, function, reason);
    }

    @Override
    public @Nullable RayTraceResult rayTraceEntities(@NotNull Position start, @NotNull Vector direction, double maxDistance, double raySize, @Nullable Predicate<? super Entity> filter) {
        return rayTraceEntities(new Location(this, start.x(), start.y(), start.z()), direction, maxDistance, raySize, filter);
    }

    @Override
    public @Nullable RayTraceResult rayTrace(@NotNull Consumer<PositionedRayTraceConfigurationBuilder> consumer) {
        return null;
    }

    @Override
    public void getChunkAtAsync(int x, int z, boolean gen, boolean urgent, @Nullable Consumer<? super Chunk> cb) {
        Chunk c = getChunkAt(x, z);
        if (cb != null) {
            cb.accept(c);
        }
    }

    @Override
    public void getChunksAtAsync(int minX, int minZ, int maxX, int maxZ, boolean urgent, @NotNull Runnable onLoad) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                getChunkAt(cx, cz);
            }
        }
        if (onLoad != null) {
            onLoad.run();
        }
    }

    @Override
    public <T extends Entity> @NotNull Collection<T> getEntitiesByClass(@NotNull Class<T>... classes) {
        List<T> list = new ArrayList<>();
        for (Entity e : getEntities()) {
            for (Class<T> c : classes) {
                if (c.isInstance(e)) {
                    list.add((T) e);
                    break;
                }
            }
        }
        return list;
    }

    @Override
    public boolean generateTree(@NotNull Location location, @NotNull TreeType type) {
        return true;
    }

    @Override
    public boolean generateTree(@NotNull Location location, @NotNull TreeType type, @NotNull BlockChangeDelegate delegate) {
        return true;
    }

    @Override
    public @NotNull Collection<Chunk> getIntersectingChunks(@NotNull BoundingBox box) {
        List<Chunk> list = new ArrayList<>();
        int minX = (int) Math.floor(box.getMinX()) >> 4;
        int maxX = (int) Math.floor(box.getMaxX()) >> 4;
        int minZ = (int) Math.floor(box.getMinZ()) >> 4;
        int maxZ = (int) Math.floor(box.getMaxZ()) >> 4;
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                list.add(getChunkAt(cx, cz));
            }
        }
        return list;
    }

    @Override
    public @NotNull Collection<Player> getPlayersSeeingChunk(int chunkX, int chunkZ) {
        return getChunkAt(chunkX, chunkZ).getPlayersSeeingChunk();
    }

    @Override
    public @NotNull Collection<Player> getPlayersSeeingChunk(@NotNull Chunk chunk) {
        return chunk.getPlayersSeeingChunk();
    }
}
