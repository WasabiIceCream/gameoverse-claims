package net.gameoverse.claims;

import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.player.config.api.v2.IPlayerConfigAPI;
import xaero.pac.common.server.player.config.api.v2.PlayerConfigOptions;

/**
 * Open Parties and Claims' per-player {@code claims.bonusChunkClaims}, which adds to the limit LuckPerms sets
 * ({@code xaero.pac_max_claims} meta: vouched and Discord-verified players).
 */
final class BonusClaims {
    private BonusClaims() {
    }

    static int get(MinecraftServer server, UUID player) {
        IPlayerConfigAPI config = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(player);
        Integer value = config.getRaw(PlayerConfigOptions.BONUS_CHUNK_CLAIMS);
        return value == null ? 0 : value;
    }

    /** Sets the bonus; false if Open Parties and Claims refused the value. */
    static boolean set(MinecraftServer server, UUID player, int value) {
        IPlayerConfigAPI config = OpenPACServerAPI.get(server).getPlayerConfigManager().getLoadedConfig(player);
        return config.tryToSet(PlayerConfigOptions.BONUS_CHUNK_CLAIMS, Math.max(0, value)) == IPlayerConfigAPI.SetResult.SUCCESS;
    }

    static int baseLimit(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
        return OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerBaseClaimLimit(player);
    }

    static int fullLimit(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
        return OpenPACServerAPI.get(server).getServerClaimsManager().getPlayerFullClaimLimit(player);
    }
}
