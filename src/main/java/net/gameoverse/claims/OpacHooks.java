package net.gameoverse.claims;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import xaero.pac.common.claims.action.api.ClaimingAction;
import xaero.pac.common.claims.api.SpecialClaimOwners;
import xaero.pac.common.event.api.v3.OPACServerAddonRegister;
import xaero.pac.common.server.claims.action.listener.api.IClaimActionListenerAPI;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverride;
import xaero.pac.common.server.claims.action.listener.override.api.ClaimActionPermissionOverrideType;
import xaero.pac.common.server.claims.api.IServerClaimsManagerAPI;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverride;
import xaero.pac.common.server.claims.protection.override.api.ChunkAccessOverrideType;
import xaero.pac.common.server.claims.protection.override.api.IChunkAccessOverriderAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;

/**
 * Open Parties and Claims addons, through its own API. A claim on a chunk with a surface structure is refused (claim
 * admin mode skips addon listeners, so admins still can); a player inside an underground structure piece, well below
 * the surface, gets through the claims above it.
 */
final class OpacHooks {
    private static final ChunkAccessOverride ALLOW = new ChunkAccessOverride(ChunkAccessOverrideType.ALLOW);

    private OpacHooks() {
    }

    static void register() {
        OPACServerAddonRegister.EVENT.register(context -> {
            context.getClaimActionListenerManagerAPI().register(new StructureClaimBlocker());
            context.getChunkAccessOverriderManagerAPI().register(new UndergroundAccess());
        });
    }

    static ServerLevel level(MinecraftServer server, Identifier dimension) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
    }

    /** "Village Plains, Mineshaft" for the reason text. */
    static String names(List<StructureScan.Piece> pieces) {
        Set<String> names = new LinkedHashSet<>();
        for (StructureScan.Piece piece : pieces) names.add(StructureScan.displayName(piece.structure()));
        return String.join(", ", names);
    }

    private static final class StructureClaimBlocker implements IClaimActionListenerAPI {
        @Override
        public String getName() {
            return GameoverseClaims.MOD_ID + ":structures";
        }

        @Override
        public ClaimActionPermissionOverride overrideClaimingActionPermission(UUID playerId, Identifier dimension, int chunkX,
                int chunkZ, ClaimingAction action, IServerClaimsManagerAPI claims, ClaimActionPermissionOverride current,
                MinecraftServer server) {
            if (action != ClaimingAction.CLAIM || !Config.INSTANCE.blockStructureClaims) return current;
            if (SpecialClaimOwners.SERVER.equals(playerId) || !server.isSameThread()) return current;
            ServerLevel level = level(server, dimension);
            if (level == null) return current;
            List<StructureScan.Piece> surface = StructureScan.surfacePieces(level, chunkX, chunkZ);
            if (surface.isEmpty()) return current;
            return new ClaimActionPermissionOverride(ClaimActionPermissionOverrideType.FORBID, Component.translatable(
                "message." + GameoverseClaims.MOD_ID + ".structure", names(surface)).withStyle(ChatFormatting.RED));
        }

        @Override
        public void handleSuccessfulClaimingAction(UUID playerId, Identifier dimension, int chunkX, int chunkZ,
                ClaimingAction action, IServerClaimsManagerAPI claims, MinecraftServer server) {
        }
    }

    private static final class UndergroundAccess implements IChunkAccessOverriderAPI {
        @Override
        public String getName() {
            return GameoverseClaims.MOD_ID + ":underground";
        }

        @Override
        public ChunkAccessOverride overrideChunkAccess(Identifier dimension, int chunkX, int chunkZ, IPlayerConfigAPI claimConfig,
                Entity accessor, UUID accessorId, MinecraftServer server, ChunkAccessOverride current) {
            if (!Config.INSTANCE.openUndergroundStructures || !(accessor instanceof ServerPlayer player)) return current;
            if (claimConfig == null || SpecialClaimOwners.SERVER.equals(claimConfig.getPlayerId())) return current;
            if (!(player.level() instanceof ServerLevel level) || !level.dimension().identifier().equals(dimension)) return current;
            if (!server.isSameThread() || level.dimensionType().hasCeiling()) return current;
            BlockPos at = player.blockPosition();
            if (at.getY() > StructureScan.surface(level, at.getX(), at.getZ()) - Config.INSTANCE.undergroundAccessDepth) return current;
            // The target chunk's pieces: the player stands in one of them, reaching into the claimed chunk from inside it.
            return StructureScan.insideUndergroundPiece(level, chunkX, chunkZ, at) ? ALLOW : current;
        }
    }
}
