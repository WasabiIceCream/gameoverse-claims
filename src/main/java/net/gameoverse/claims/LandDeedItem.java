package net.gameoverse.claims;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Used up on right-click: the player's claim limit goes up by {@link Config#deedClaims} chunks, for good. */
final class LandDeedItem extends Item {
    private static final boolean OPAC = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("openpartiesandclaims");

    LandDeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (!OPAC) return InteractionResult.PASS;
        var server = serverPlayer.level().getServer();
        int amount = Config.INSTANCE.deedClaims;
        if (Config.INSTANCE.deedNeedsBaseLimit && BonusClaims.baseLimit(server, serverPlayer) <= 0) {
            serverPlayer.sendSystemMessage(Component.translatable("message." + GameoverseClaims.MOD_ID + ".deed_unverified")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        int bonus = BonusClaims.get(server, serverPlayer.getUUID());
        if (!BonusClaims.set(server, serverPlayer.getUUID(), bonus + amount)) {
            serverPlayer.sendSystemMessage(Component.translatable("message." + GameoverseClaims.MOD_ID + ".deed_failed")
                .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        ItemStack stack = player.getItemInHand(hand);
        stack.consume(1, player);
        level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        serverPlayer.sendSystemMessage(Component.translatable("message." + GameoverseClaims.MOD_ID + ".deed_used",
            amount, BonusClaims.fullLimit(server, serverPlayer)).withStyle(ChatFormatting.GREEN));
        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out,
            TooltipFlag flag) {
        // No number: the config is the server's, and clients don't have it.
        out.accept(Component.translatable("item." + GameoverseClaims.MOD_ID + ".land_deed.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
