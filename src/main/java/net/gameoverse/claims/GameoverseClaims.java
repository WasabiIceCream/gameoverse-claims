package net.gameoverse.claims;

import java.util.Collection;
import java.util.List;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Land claims for Gameoverse, on top of Open Parties and Claims: structures that reach the surface can't be claimed,
 * underground ones stay open to explorers under other players' claims ({@link OpacHooks}), and Land Deeds or an admin
 * command raise a player's claim limit ({@link BonusClaims}).
 */
public final class GameoverseClaims implements ModInitializer {
    public static final String MOD_ID = "gameoverse_claims";
    static final Logger LOG = LoggerFactory.getLogger(MOD_ID);
    public static Item LAND_DEED;

    @Override
    public void onInitialize() {
        Config.load();
        Identifier deedId = id("land_deed");
        LAND_DEED = Registry.register(BuiltInRegistries.ITEM, deedId, new LandDeedItem(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, deedId)).stacksTo(16).rarity(Rarity.RARE)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(LAND_DEED));
        LootTableEvents.MODIFY.register((key, table, source, registries) -> {
            Identifier tableId = key.identifier();
            if (!tableId.getNamespace().equals("gameoverse_locked_chests") || !tableId.getPath().startsWith("chests/")) return;
            Double chance = Config.INSTANCE.lockedChestDeedChance.get(tableId.getPath().substring("chests/".length()));
            if (chance == null || chance <= 0) return;
            table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(LAND_DEED))
                .when(LootItemRandomChanceCondition.randomChance(chance.floatValue())));
        });
        // The item exists everywhere (clients need it registered); the rules only where Open Parties and Claims runs.
        if (!FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            LOG.warn("Open Parties and Claims isn't loaded: Land Deeds do nothing, no claim rules");
            return;
        }
        OpacHooks.register();
        ClaimReport.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> dispatcher.register(
            Commands.literal(MOD_ID).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("check").executes(GameoverseClaims::check))
                .then(Commands.literal("report").executes(ctx -> {
                    if (!ClaimReport.start(ctx.getSource().getServer(), ctx.getSource())) {
                        ctx.getSource().sendFailure(Component.literal("A report is already running."));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("reload").executes(ctx -> {
                    Config.load();
                    ctx.getSource().sendSuccess(() -> Component.literal("Reloaded config/gameoverse_claims.json"), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("bonus").then(Commands.argument("player", GameProfileArgument.gameProfile())
                    .executes(ctx -> bonus(ctx, null, 0))
                    .then(Commands.literal("add").then(Commands.argument("chunks", IntegerArgumentType.integer())
                        .executes(ctx -> bonus(ctx, "add", IntegerArgumentType.getInteger(ctx, "chunks")))))
                    .then(Commands.literal("set").then(Commands.argument("chunks", IntegerArgumentType.integer(0))
                        .executes(ctx -> bonus(ctx, "set", IntegerArgumentType.getInteger(ctx, "chunks")))))))));
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    private static int bonus(CommandContext<CommandSourceStack> ctx, String op, int amount) throws CommandSyntaxException {
        Collection<NameAndId> players = GameProfileArgument.getGameProfiles(ctx, "player");
        var server = ctx.getSource().getServer();
        for (NameAndId player : players) {
            int before = BonusClaims.get(server, player.id());
            if (op != null) {
                int after = op.equals("add") ? before + amount : amount;
                if (!BonusClaims.set(server, player.id(), after)) {
                    ctx.getSource().sendFailure(Component.literal("Open Parties and Claims refused bonus " + after + " for " + player.name()));
                    continue;
                }
                ctx.getSource().sendSuccess(() -> Component.literal(player.name() + ": bonus claims " + before + " -> "
                    + BonusClaims.get(server, player.id())), true);
            } else {
                ctx.getSource().sendSuccess(() -> Component.literal(player.name() + ": bonus claims " + before), false);
            }
        }
        return players.size();
    }

    /** What the claim rules see in the chunk the command runs in. */
    private static int check(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        BlockPos at = BlockPos.containing(ctx.getSource().getPosition());
        ChunkPos chunk = ChunkPos.containing(at);
        List<StructureScan.Piece> pieces = StructureScan.pieces(level, chunk.x(), chunk.z());
        List<StructureScan.Piece> surface = StructureScan.surfacePieces(level, chunk.x(), chunk.z());
        boolean inside = StructureScan.insideUndergroundPiece(level, chunk.x(), chunk.z(), at, 1);
        int depth = StructureScan.surface(level, at.getX(), at.getZ()) - at.getY();
        StringBuilder out = new StringBuilder("Chunk " + chunk.x() + ", " + chunk.z() + ": ");
        if (pieces.isEmpty()) out.append("no structures, claimable.");
        else {
            out.append(surface.isEmpty() ? "claimable" : "unclaimable (" + OpacHooks.names(surface) + " reaches the surface)");
            List<StructureScan.Piece> under = pieces.stream().filter(p -> !surface.contains(p)).toList();
            if (!under.isEmpty()) out.append("; underground: ").append(OpacHooks.names(under));
            out.append(". Here: ").append(inside ? "inside an underground piece" : "not inside an underground piece").append(", ").append(depth)
                .append(" blocks below the surface -> ").append(inside && depth >= Config.INSTANCE.undergroundAccessDepth
                    && !level.dimensionType().hasCeiling() ? "claims don't stop you" : "claims apply").append('.');
        }
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()), false);
        return Command.SINGLE_SUCCESS;
    }
}
