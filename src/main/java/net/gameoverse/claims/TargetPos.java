package net.gameoverse.claims;

import net.minecraft.core.BlockPos;

/**
 * Where the thing Open Parties and Claims is checking right now is: the block, or the entity's block position (see
 * ChunkProtectionMixin). Null outside those checks.
 */
public final class TargetPos {
    private static final ThreadLocal<BlockPos> CURRENT = new ThreadLocal<>();

    private TargetPos() {
    }

    public static BlockPos get() {
        return CURRENT.get();
    }

    public static void set(BlockPos pos) {
        CURRENT.set(pos);
    }
}
