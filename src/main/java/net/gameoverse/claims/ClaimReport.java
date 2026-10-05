package net.gameoverse.claims;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * Lists existing player claims that hold structures, a few chunks per tick (unloaded ones are read from disk), into
 * {@code logs/gameoverse-claims-report.tsv}. Read-only: deciding what to do about each is an admin's call.
 */
final class ClaimReport {
    private record Job(String owner, Identifier dimension, ChunkPos pos) {
    }

    private static final Deque<Job> QUEUE = new ArrayDeque<>();
    private static final List<String> ROWS = new ArrayList<>();
    private static final Map<String, Integer> PER_OWNER = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private static CommandSourceStack requester;
    private static int total;

    private ClaimReport() {
    }

    static void register() {
        ServerTickEvents.END_SERVER_TICK.register(ClaimReport::tick);
    }

    /** Starts a report; false if one is already running. */
    static boolean start(MinecraftServer server, CommandSourceStack source) {
        if (!QUEUE.isEmpty()) return false;
        ROWS.clear();
        PER_OWNER.clear();
        OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerInfoStream().forEach(info -> {
            UUID id = info.getPlayerId();
            if (SpecialClaimOwners.SERVER.equals(id) || SpecialClaimOwners.EXPIRED.equals(id)) return;
            String owner = info.getPlayerUsername() + " (" + id + ")";
            info.getStream().forEach(dim -> dim.getValue().getStream().forEach(list ->
                list.getStream().forEach(pos -> QUEUE.add(new Job(owner, dim.getKey(), pos)))));
        });
        total = QUEUE.size();
        requester = source;
        source.sendSuccess(() -> Component.literal("Checking " + total + " claimed chunks for structures..."), false);
        if (total == 0) finish(server);
        return true;
    }

    private static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        for (int i = 0; i < Config.INSTANCE.reportChunksPerTick && !QUEUE.isEmpty(); i++) {
            Job job = QUEUE.poll();
            ServerLevel level = OpacHooks.level(server, job.dimension());
            if (level == null) continue;
            List<StructureScan.Piece> pieces = StructureScan.pieces(level, job.pos().x(), job.pos().z());
            if (pieces.isEmpty()) continue;
            List<StructureScan.Piece> surface = StructureScan.surfacePieces(level, job.pos().x(), job.pos().z());
            List<StructureScan.Piece> underground = new ArrayList<>(pieces);
            underground.removeAll(surface);
            ROWS.add(String.join("\t", job.owner(), job.dimension().toString(), Integer.toString(job.pos().x()),
                Integer.toString(job.pos().z()), job.pos().getMiddleBlockX() + " " + job.pos().getMiddleBlockZ(),
                OpacHooks.names(surface), OpacHooks.names(underground)));
            if (!surface.isEmpty()) PER_OWNER.merge(job.owner(), 1, Integer::sum);
        }
        if (QUEUE.isEmpty()) finish(server);
    }

    private static void finish(MinecraftServer server) {
        Path out = FabricLoader.getInstance().getGameDir().resolve("logs/gameoverse-claims-report.tsv");
        List<String> lines = new ArrayList<>();
        lines.add("owner\tdimension\tchunk_x\tchunk_z\tblock_x_z\tsurface_structures\tunderground_structures");
        lines.addAll(ROWS);
        String written;
        try {
            Files.write(out, lines);
            written = "wrote " + out;
        } catch (IOException e) {
            written = "couldn't write " + out + ": " + e;
        }
        long surfaceChunks = PER_OWNER.values().stream().mapToInt(Integer::intValue).sum();
        String summary = "Claims report: " + total + " claimed chunks, " + ROWS.size() + " hold structures, "
            + surfaceChunks + " of them surface structures (unclaimable today); " + written
            + (PER_OWNER.isEmpty() ? "" : ". Surface chunks per owner: " + PER_OWNER);
        GameoverseClaims.LOG.info(summary);
        if (requester != null) requester.sendSuccess(() -> Component.literal(summary), false);
        requester = null;
    }
}
