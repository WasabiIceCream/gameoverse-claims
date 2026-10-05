package net.gameoverse.claims.mixin;

import java.util.UUID;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.gameoverse.claims.TargetPos;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import xaero.pac.common.server.claims.protection.ChunkProtection;
import xaero.pac.common.server.player.config.IPlayerConfig;

/**
 * Open Parties and Claims' chunk access overrider API only passes the chunk and the accessor. While OPAC checks a block
 * (breaking, using, placing) or an entity (attacking, using), this records where it is, so {@code OpacHooks} can test
 * that position instead of the player's.
 */
@Mixin(value = ChunkProtection.class, remap = false)
abstract class ChunkProtectionMixin {
    @WrapMethod(method = "blockAccessCheck", require = 0)
    private ChunkProtection.InteractionTargetResult gameoverse_claims$recordTarget(Block block, Identifier dimension, BlockPos pos,
            IPlayerConfig claimConfig, Entity accessor, Entity accessorOwner, UUID accessorId, boolean a, boolean b, boolean c,
            Operation<ChunkProtection.InteractionTargetResult> original) {
        BlockPos previous = TargetPos.get();
        TargetPos.set(pos);
        try {
            return original.call(block, dimension, pos, claimConfig, accessor, accessorOwner, accessorId, a, b, c);
        } finally {
            TargetPos.set(previous);
        }
    }

    @WrapMethod(method = "entityAccessCheck(Lxaero/pac/common/server/player/config/IPlayerConfig;Lnet/minecraft/world/entity/Entity;"
        + "Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity;Ljava/util/UUID;ZZZ)"
        + "Lxaero/pac/common/server/claims/protection/ChunkProtection$InteractionTargetResult;", require = 0)
    private ChunkProtection.InteractionTargetResult gameoverse_claims$recordEntityTarget(IPlayerConfig claimConfig, Entity target,
            Entity accessor, Entity accessorOwner, UUID accessorId, boolean a, boolean b, boolean c,
            Operation<ChunkProtection.InteractionTargetResult> original) {
        BlockPos previous = TargetPos.get();
        TargetPos.set(target == null ? null : target.blockPosition());
        try {
            return original.call(claimConfig, target, accessor, accessorOwner, accessorId, a, b, c);
        } finally {
            TargetPos.set(previous);
        }
    }
}
