package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.EditHistory;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import com.creativemd.littletiles.fabric.tile.MaterialRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class LittleToolItem extends Item {
    public enum Tool { HAMMER, SAW, WRENCH, CHISEL, COLOR, COLLISION, MALLET, BLUEPRINT }
    private final Tool tool;
    public LittleToolItem(Properties properties, Tool tool) { super(properties); this.tool = tool; }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
            net.minecraft.world.item.component.TooltipDisplay display,
            java.util.function.Consumer<net.minecraft.network.chat.Component> tooltip,
            net.minecraft.world.item.TooltipFlag flag) {
        tooltip.accept(net.minecraft.network.chat.Component.translatable("tooltip.littletiles."
                + tool.name().toLowerCase(java.util.Locale.ROOT)));
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        if (tool==Tool.CHISEL || tool==Tool.SAW)
            return CuttingTool.use(context,player)?InteractionResult.SUCCESS:InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos();
        if (!EditHistory.allowed(player, pos) || !player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))
            return InteractionResult.FAIL;
        if (tool == Tool.BLUEPRINT) {
            boolean done = context.isSecondaryUseActive()
                    ? Blueprint.read(context.getItemInHand()).map(b -> b.place(context, player)).orElse(false)
                    : Blueprint.copyCorner(context, player);
            return done ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (!(context.getLevel().getBlockEntity(pos) instanceof LittleBlockEntity be)) {
            var state = context.getLevel().getBlockState(pos);
            if (tool != Tool.HAMMER || !MaterialRules.canCut(state, context.getLevel(), pos)
                    || (!player.getAbilities().instabuild && state.getDestroySpeed(context.getLevel(), pos) < 0))
                return InteractionResult.FAIL;
            return EditHistory.replace(player, pos, List.of(new Tile(state,
                    new MicroBox(0, 0, 0, 16, 16, 16), 0xFFFFFF, true)), true)
                    ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (be.tiles().isEmpty()) return InteractionResult.FAIL;
        Vec3 inward = context.getClickLocation().subtract(Vec3.atLowerCornerOf(pos))
                .subtract(new Vec3(context.getClickedFace().getStepX(), context.getClickedFace().getStepY(),
                        context.getClickedFace().getStepZ()).scale(0.00001));
        Tile selected = select(be.tiles(), inward);
        if (selected == null) return InteractionResult.FAIL;
        MicroBox.Axis axis = MicroBox.Axis.valueOf(context.getClickedFace().getAxis().name());
        var replacement = new ArrayList<>(be.tiles());
        var drops = new ArrayList<Tile>();
        boolean dyeConsumed = false;
        switch (tool) {
            case HAMMER -> { replacement.remove(selected); drops.add(selected); }
            case WRENCH -> { replacement.clear(); replacement.addAll(Tiles.transform(be.tiles(), axis, context.isSecondaryUseActive())); }
            case MALLET -> { replacement.clear(); replacement.addAll(Tiles.compact(be.tiles())); }
            case COLLISION -> { replacement.remove(selected); replacement.add(selected.withCollision(!selected.collision())); }
            case COLOR -> {
                if (!(player.getOffhandItem().getItem() instanceof DyeItem dye)) return InteractionResult.FAIL;
                replacement.remove(selected);
                replacement.add(selected.withColor(dye.getDyeColor().getTextureDiffuseColor()));
                dyeConsumed = true;
            }
            default -> { return InteractionResult.FAIL; }
        }
        if (!EditHistory.replace(player, pos, replacement, false)) return InteractionResult.FAIL;
        if (dyeConsumed && !player.getAbilities().instabuild) player.getOffhandItem().shrink(1);
        // Undo is creative-only; never duplicate material drops in creative histories.
        if (!drops.isEmpty() && !player.getAbilities().instabuild) {
            ItemStack stack = new ItemStack(LittleTilesContent.TILES);
            Tiles.write(stack, drops);
            if (!player.getInventory().add(stack)) Block.popResource(context.getLevel(), pos, stack);
        }
        return InteractionResult.SUCCESS;
    }

    private static Tile select(List<Tile> tiles, Vec3 hit) {
        for (Tile tile : tiles) {
            var b = tile.box();
            if (hit.x * 16 >= b.minX() && hit.x * 16 < b.maxX() && hit.y * 16 >= b.minY()
                    && hit.y * 16 < b.maxY() && hit.z * 16 >= b.minZ() && hit.z * 16 < b.maxZ()) return tile;
        }
        return null;
    }
}
