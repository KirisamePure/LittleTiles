package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.EditHistory;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

public final class TileItem extends Item {
    public TileItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(net.minecraft.world.item.ItemStack stack,TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        var tiles=Tiles.read(stack).orElse(List.of());
        tiles.stream().map(Tile::material).distinct().limit(4).forEach(state -> tooltip.accept(
                net.minecraft.network.chat.Component.translatable("tooltip.littletiles.material",state.getBlock().getName(),
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())));
        tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.littletiles.volume",
                tiles.stream().mapToLong(t -> t.box().volume()).sum()));
        tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.littletiles.configure"));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        List<Tile> tiles = Tiles.read(context.getItemInHand()).orElse(List.of());
        if (tiles.isEmpty()) return InteractionResult.FAIL;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        boolean success = execute(context, player, tiles, false);
        return success ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    public static boolean place(UseOnContext context, ServerPlayer player, List<Tile> source) {
        return execute(context,player,source,true);
    }
    private static boolean execute(UseOnContext context, ServerPlayer player, List<Tile> source,boolean blueprint) {
        var mode=ToolSettings.read(context.getItemInHand()).placeMode();
        var located=locate(context.getLevel(),context.getClickedPos(),context.getClickLocation(),context.getClickedFace(),source,mode);
        BlockPos target=located.pos();List<Tile> shifted=located.tiles();
        if (shifted.isEmpty() || !Tiles.valid(shifted) || !EditHistory.allowed(player, target)
                || !player.mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())) return false;
        var existing=CuttingTool.source(player,target);
        if(existing==null) return false;
        var planned=PlacementMath.plan(existing,shifted,mode);
        if(planned.isEmpty()) return false;
        var plan=planned.get();
        if(!plan.added().isEmpty() && !context.getLevel().isUnobstructed(null,
                Tiles.shape(plan.added(),true).move(target.getX(),target.getY(),target.getZ()))) return false;
        var payment=blueprint && !player.getAbilities().instabuild && !plan.added().isEmpty()
                ? MaterialPayment.plan(player,plan.added()):java.util.Optional.<MaterialPayment>empty();
        if(blueprint && !player.getAbilities().instabuild && !plan.added().isEmpty() && payment.isEmpty()) return false;
        if(!EditHistory.replace(player,target,plan.output(),true)) return false;
        payment.ifPresent(p -> p.apply(player));
        if(!player.getAbilities().instabuild) {
            if(!blueprint && mode!=ToolSettings.PlaceMode.STENCIL) {
                context.getItemInHand().shrink(1);CuttingTool.give(player,plan.unused());
            }
            CuttingTool.give(player,plan.removed());
        }
        return true;
    }

    public record Target(BlockPos pos,List<Tile> tiles) { public Target {pos=pos.immutable();tiles=List.copyOf(tiles);} }
    /** Shared by server placement and the client ghost; no inventory/world mutation. */
    public static Target locate(net.minecraft.world.level.Level level,BlockPos clicked,Vec3 hit,Direction clickedFace,
                                List<Tile> source,ToolSettings.PlaceMode mode) {
        boolean inward=mode==ToolSettings.PlaceMode.OVERWRITE||mode==ToolSettings.PlaceMode.STENCIL;
        BlockPos target=inward||level.getBlockEntity(clicked) instanceof LittleBlockEntity?clicked:clicked.relative(clickedFace);
        Direction face=inward?clickedFace.getOpposite():clickedFace;
        var shifted=align(source,hit.subtract(Vec3.atLowerCornerOf(target)),face);
        if(!inward && (shifted.isEmpty() || !Tiles.valid(shifted))) {
            target=clicked.relative(clickedFace);shifted=align(source,hit.subtract(Vec3.atLowerCornerOf(target)),face);
        }
        return new Target(target,shifted);
    }

    private static List<Tile> align(List<Tile> source, Vec3 hit, Direction face) {
        int lx = source.stream().mapToInt(t -> t.box().minX()).min().orElse(0);
        int ly = source.stream().mapToInt(t -> t.box().minY()).min().orElse(0);
        int lz = source.stream().mapToInt(t -> t.box().minZ()).min().orElse(0);
        int sx = source.stream().mapToInt(t -> t.box().maxX()).max().orElse(16) - lx;
        int sy = source.stream().mapToInt(t -> t.box().maxY()).max().orElse(16) - ly;
        int sz = source.stream().mapToInt(t -> t.box().maxZ()).max().orElse(16) - lz;
        int x = anchor(hit.x * 16, sx, face.getStepX());
        int y = anchor(hit.y * 16, sy, face.getStepY());
        int z = anchor(hit.z * 16, sz, face.getStepZ());
        // Out-of-block alignment signals the caller to try the neighboring block.
        if (x < 0 || y < 0 || z < 0 || x + sx > 16 || y + sy > 16 || z + sz > 16) return List.of();
        return source.stream().map(t -> t.withBox(t.box().translate(x - lx, y - ly, z - lz))).toList();
    }

    private static int anchor(double hit, int size, int direction) {
        if (direction > 0) return (int) Math.round(hit);
        if (direction < 0) return (int) Math.round(hit) - size;
        return Math.clamp((int) Math.floor(hit), 0, 16 - size);
    }
}
