package com.creativemd.littletiles.fabric.edit;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.LittleBlock;
import com.creativemd.littletiles.fabric.tile.MaterialRules;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Server-thread-only edits. Undo refuses to overwrite intervening world changes. */
public final class EditHistory {
    private static final int LIMIT = 30;
    private static final Map<UUID, Deque<Action>> UNDO = new HashMap<>(), REDO = new HashMap<>();

    public record Snapshot(BlockState state, List<Tile> tiles) {
        public Snapshot { tiles = List.copyOf(tiles); }
        public static Snapshot capture(ServerLevel world, BlockPos pos) {
            return new Snapshot(world.getBlockState(pos), world.getBlockEntity(pos) instanceof LittleBlockEntity be
                    ? be.tiles() : List.of());
        }
        private boolean restore(ServerLevel world, BlockPos pos) {
            if (!world.setBlock(pos, state, 3) && !world.getBlockState(pos).equals(state)) return false;
            if (state.is(LittleTilesContent.TILE_BLOCK)) {
                if (!(world.getBlockEntity(pos) instanceof LittleBlockEntity be)) return false;
                be.replace(tiles);
            }
            return true;
        }
    }

    private record Action(ResourceKey<Level> dimension, BlockPos anchor,
                          Map<BlockPos, Snapshot> before, Map<BlockPos, Snapshot> after) {}

    private EditHistory() {}

    public static boolean allowed(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.level();
        double reach = player.blockInteractionRange() + 1;
        return permitted(player, pos)
                && player.getEyePosition().distanceToSqr(pos.getCenter()) <= reach * reach;
    }

    private static boolean permitted(ServerPlayer player, BlockPos pos) {
        ServerLevel world = player.level();
        return player.mayBuild() && world.hasChunkAt(pos) && world.isInWorldBounds(pos)
                && world.getWorldBorder().isWithinBounds(pos) && world.mayInteract(player, pos);
    }

    public static boolean replace(ServerPlayer player, BlockPos pos, List<Tile> tiles, boolean convertFullBlock) {
        return replaceAll(player, pos, Map.of(pos.immutable(), List.copyOf(tiles)), convertFullBlock);
    }

    /** All targets are preflighted before changing anything; one action restores the whole group. */
    public static boolean replaceAll(ServerPlayer player, BlockPos anchor, Map<BlockPos, List<Tile>> changes,
                                     boolean convertFullBlock) {
        if (!allowed(player, anchor) || changes.isEmpty() || changes.size() > 512
                || changes.values().stream().mapToInt(List::size).sum() > Tiles.MAX_TILES) return false;
        ServerLevel world = player.level();
        Map<BlockPos, Snapshot> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
        for (var entry : changes.entrySet()) {
            BlockPos pos = entry.getKey().immutable();
            List<Tile> tiles = entry.getValue();
            if (!permitted(player, pos) || pos.distSqr(anchor) > 64 * 64 || !Tiles.valid(tiles)) return false;
            Snapshot old = Snapshot.capture(world, pos);
            if (!old.state.is(LittleTilesContent.TILE_BLOCK) && !old.state.isAir()
                    && (!convertFullBlock || !MaterialRules.canCut(old.state, world, pos)
                    || (!player.getAbilities().instabuild && old.state.getDestroySpeed(world, pos) < 0))) return false;
            before.put(pos, old);
            after.put(pos, new Snapshot(tiles.isEmpty() ? Blocks.AIR.defaultBlockState() : LittleBlock.stateFor(tiles), tiles));
        }
        if (before.equals(after)) return false;
        if (!restoreAll(world, after)) { restoreAll(world, before); return false; }
        if (player.getAbilities().instabuild) {
            Deque<Action> undo = UNDO.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>());
            undo.push(new Action(world.dimension(), anchor.immutable(), Map.copyOf(before), Map.copyOf(after)));
            while (undo.size() > LIMIT) undo.removeLast();
            REDO.remove(player.getUUID());
        }
        return true;
    }

    private static boolean restoreAll(ServerLevel world, Map<BlockPos, Snapshot> snapshots) {
        for (var entry : snapshots.entrySet()) if (!entry.getValue().restore(world, entry.getKey())) return false;
        return true;
    }

    public static boolean undo(ServerPlayer player) { return move(player, UNDO, REDO, true); }
    public static boolean redo(ServerPlayer player) { return move(player, REDO, UNDO, false); }

    private static boolean move(ServerPlayer player, Map<UUID, Deque<Action>> from,
                                Map<UUID, Deque<Action>> to, boolean backwards) {
        if (!player.getAbilities().instabuild) return false;
        Deque<Action> stack = from.get(player.getUUID());
        if (stack == null || stack.isEmpty()) return false;
        Action action = stack.peek();
        ServerLevel world = player.level();
        if (!action.dimension.equals(world.dimension()) || !allowed(player, action.anchor)) return false;
        Map<BlockPos, Snapshot> expected = backwards ? action.after : action.before;
        Map<BlockPos, Snapshot> target = backwards ? action.before : action.after;
        for (var entry : expected.entrySet()) {
            if (!permitted(player, entry.getKey()) || !Snapshot.capture(world, entry.getKey()).equals(entry.getValue())) return false;
        }
        if (!restoreAll(world, target)) { restoreAll(world, expected); return false; }
        stack.pop();
        to.computeIfAbsent(player.getUUID(), id -> new ArrayDeque<>()).push(action);
        return true;
    }

    public static void clear(UUID player) { UNDO.remove(player); REDO.remove(player); }
    public static void clear() { UNDO.clear(); REDO.clear(); }
}
