package net.gameoverse.claims;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Which structure pieces cross a chunk column, and whether each reaches the surface. Pieces never change after world
 * generation, so they're cached per chunk; the surface (players dig and build) is read fresh every time.
 */
public final class StructureScan {
    /** A structure piece crossing a chunk column. */
    public record Piece(Identifier structure, BoundingBox box) {
    }

    private static final int CACHE_SIZE = 8192;
    private static final Map<ResourceKey<Level>, Map<Long, List<Piece>>> CACHE = new HashMap<>();
    private static List<Pattern> ignoredPatterns;
    private static List<TagKey<Structure>> ignoredTags;

    private StructureScan() {
    }

    static void clearCaches() {
        CACHE.clear();
        ignoredPatterns = null;
        ignoredTags = null;
    }

    /** Non-ignored structure pieces whose bounding box crosses this chunk column. Loads the chunk if needed. */
    public static List<Piece> pieces(ServerLevel level, int chunkX, int chunkZ) {
        Map<Long, List<Piece>> cache = CACHE.computeIfAbsent(level.dimension(), k -> new LinkedHashMap<>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, List<Piece>> eldest) {
                return size() > CACHE_SIZE;
            }
        });
        long key = ChunkPos.pack(chunkX, chunkZ);
        List<Piece> cached = cache.get(key);
        if (cached != null) return cached;
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        int minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ(), maxX = pos.getMaxBlockX(), maxZ = pos.getMaxBlockZ();
        Registry<Structure> registry = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Piece> found = new ArrayList<>();
        for (StructureStart start : level.structureManager().startsForStructure(pos, s -> !ignored(registry, s))) {
            Identifier id = registry.getKey(start.getStructure());
            for (StructurePiece piece : start.getPieces()) {
                BoundingBox box = piece.getBoundingBox();
                if (box.intersects(minX, minZ, maxX, maxZ)) found.add(new Piece(id, box));
            }
        }
        List<Piece> result = List.copyOf(found);
        cache.put(key, result);
        return result;
    }

    /**
     * Whether a piece reaches the surface in this chunk column: its top is within {@code surfaceMargin} of the lowest
     * ground (ocean floor counts as ground) sampled over the part of it inside the column. In a roofed dimension
     * (the Nether) everything counts as surface.
     */
    public static boolean reachesSurface(ServerLevel level, Piece piece, int chunkX, int chunkZ) {
        if (level.dimensionType().hasCeiling()) return true;
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        BoundingBox box = piece.box();
        int x0 = Math.max(box.minX(), pos.getMinBlockX()), x1 = Math.min(box.maxX(), pos.getMaxBlockX());
        int z0 = Math.max(box.minZ(), pos.getMinBlockZ()), z1 = Math.min(box.maxZ(), pos.getMaxBlockZ());
        int ground = Integer.MAX_VALUE;
        for (int[] xz : new int[][] {{x0, z0}, {x0, z1}, {x1, z0}, {x1, z1}, {(x0 + x1) >> 1, (z0 + z1) >> 1}}) {
            ground = Math.min(ground, surface(level, xz[0], xz[1]));
        }
        return box.maxY() >= ground - Config.INSTANCE.surfaceMargin;
    }

    /**
     * The y of the top solid block (water doesn't count, so the ocean floor is ground). Loads the chunk:
     * {@code Level.getHeight} answers the world's bottom for unloaded chunks.
     */
    public static int surface(ServerLevel level, int x, int z) {
        return level.getChunk(x >> 4, z >> 4).getHeight(Heightmap.Types.OCEAN_FLOOR, x & 15, z & 15);
    }

    /** The surface pieces in this chunk, the ones that make it unclaimable. */
    public static List<Piece> surfacePieces(ServerLevel level, int chunkX, int chunkZ) {
        List<Piece> out = new ArrayList<>();
        for (Piece piece : pieces(level, chunkX, chunkZ)) {
            if (reachesSurface(level, piece, chunkX, chunkZ)) out.add(piece);
        }
        return out;
    }

    /**
     * Whether {@code at} is inside (grown by {@code margin} blocks) an underground piece of this chunk: one that doesn't
     * reach the surface, so standing under a surface building's roof doesn't count.
     */
    public static boolean insideUndergroundPiece(ServerLevel level, int chunkX, int chunkZ, BlockPos at, int margin) {
        for (Piece piece : pieces(level, chunkX, chunkZ)) {
            if (piece.box().inflatedBy(margin).isInside(at) && !reachesSurface(level, piece, chunkX, chunkZ)) return true;
        }
        return false;
    }

    private static boolean ignored(Registry<Structure> registry, Structure structure) {
        if (ignoredPatterns == null) compileIgnored();
        Identifier id = registry.getKey(structure);
        if (id == null) return true;
        String s = id.toString();
        for (Pattern p : ignoredPatterns) if (p.matcher(s).matches()) return true;
        if (!ignoredTags.isEmpty()) {
            Holder<Structure> holder = registry.wrapAsHolder(structure);
            for (TagKey<Structure> tag : ignoredTags) if (holder.is(tag)) return true;
        }
        return false;
    }

    private static void compileIgnored() {
        List<Pattern> patterns = new ArrayList<>();
        List<TagKey<Structure>> tags = new ArrayList<>();
        for (String entry : Config.INSTANCE.ignoredStructures) {
            if (entry.startsWith("#")) {
                Identifier tag = Identifier.tryParse(entry.substring(1));
                if (tag != null) tags.add(TagKey.create(Registries.STRUCTURE, tag));
            } else {
                StringBuilder regex = new StringBuilder();
                for (String part : entry.split("\\*", -1)) {
                    if (!regex.isEmpty()) regex.append(".*");
                    regex.append(Pattern.quote(part));
                }
                patterns.add(Pattern.compile(regex.toString()));
            }
        }
        ignoredPatterns = patterns;
        ignoredTags = tags;
    }

    /** "minecraft:village_plains" -> "Village Plains", for messages. */
    public static String displayName(Identifier id) {
        String path = id.getPath();
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : path.split("_")) {
            if (word.isEmpty() || word.chars().allMatch(Character::isDigit)) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? id.toString() : out.toString();
    }
}
