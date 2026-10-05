package net.gameoverse.claims;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

/**
 * {@code config/gameoverse_claims.json}, written with the defaults on first start. Missing fields keep their defaults.
 */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Refuse claims on chunks with a structure piece that reaches the surface (every piece in roofed dimensions). */
    public boolean blockStructureClaims = true;
    /** A piece counts as underground when its top is at least this many blocks below the ground surface above it. */
    public int surfaceMargin = 4;
    /**
     * Let players through other players' claims while they stand inside an underground structure piece (mineshaft,
     * stronghold, dungeon, ancient city...) at least {@link #undergroundAccessDepth} blocks below the surface.
     */
    public boolean openUndergroundStructures = true;
    public int undergroundAccessDepth = 6;
    /**
     * Structures that never matter for claims: decoration, fossils, buried treasure. {@code *} matches any run of
     * characters; {@code #namespace:tag} matches a structure tag.
     */
    public List<String> ignoredStructures = List.of(
        "minecraft:buried_treasure", "minecraft:ruined_portal*", "minecraft:nether_fossil",
        "legacies_and_legends:buried_treasure_adaptation", "legacies_and_legends:buried_barrel",
        "betterarcheology:fossil_*", "hopo:underwater/underwater_fossils",
        "adorabuild_structures:*_tree_1", "explorations:logs", "explorations:large_oak_tree",
        "formationsoverworld:log_spikes", "formationsoverworld:stone_ore_spikes",
        "formationsnether:quartz_spikes", "formationsnether:ore_shard", "formationsnether:large_tree",
        "fishofthieves:guardian_fruit_tree");
    /** Bonus chunk claims one Land Deed adds. */
    public int deedClaims = 9;
    /** Land Deeds only work for players who can claim at all (vouched or verified: their base limit is above 0). */
    public boolean deedNeedsBaseLimit = true;
    /** Chance of a Land Deed in each Locked Chest tier's loot. */
    public Map<String, Double> lockedChestDeedChance = ordered(
        "common", 0.05, "rare", 0.10, "epic", 0.20, "legendary", 0.35, "divine", 0.60);
    /** Chunks the existing-claims report checks per server tick. */
    public int reportChunksPerTick = 16;

    public static Config INSTANCE = new Config();

    @SuppressWarnings("unchecked")
    private static <V> Map<String, V> ordered(Object... keysAndValues) {
        Map<String, V> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) map.put((String) keysAndValues[i], (V) keysAndValues[i + 1]);
        return map;
    }

    static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("gameoverse_claims.json");
        try {
            if (Files.exists(path)) {
                Config read = GSON.fromJson(Files.readString(path), Config.class);
                if (read != null) INSTANCE = read;
            }
            Files.writeString(path, GSON.toJson(INSTANCE));
        } catch (IOException | RuntimeException e) {
            GameoverseClaims.LOG.warn("Can't read config/gameoverse_claims.json, using the defaults: {}", e.toString());
            INSTANCE = new Config();
        }
        StructureScan.clearCaches();
    }
}
