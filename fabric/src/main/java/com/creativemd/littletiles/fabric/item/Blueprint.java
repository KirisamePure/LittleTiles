package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.EditHistory;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import com.creativemd.littletiles.fabric.tile.MaterialRules;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/** New-format multi-block blueprints. Two right-clicks select corners; sneak-use pastes. */
public record Blueprint(List<Blueprint.Part> parts) {
    public record Part(BlockPos offset, List<Tile> tiles) {
        public Part { offset = offset.immutable(); tiles = List.copyOf(tiles); }
        private static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Part::offset),
                Tiles.CODEC.fieldOf("tiles").forGetter(Part::tiles)).apply(i, Part::new));
    }
    public Blueprint { parts = List.copyOf(parts); }
    public static final Codec<Blueprint> CODEC = Part.CODEC.listOf(1, 512).xmap(Blueprint::new, Blueprint::parts)
            .validate(b -> b.valid() ? DataResult.success(b) : DataResult.error(() -> "Invalid blueprint extent or contents"));

    public boolean valid() {
        if (parts.isEmpty() || parts.size() > 512 || tiles().size() > Tiles.MAX_TILES) return false;
        var positions = new HashSet<BlockPos>();
        for (Part part : parts) {
            BlockPos p = part.offset;
            if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0 || p.getX() >= 32 || p.getY() >= 32 || p.getZ() >= 32
                    || !positions.add(p) || part.tiles.isEmpty() || !Tiles.valid(part.tiles)) return false;
        }
        return true;
    }
    public List<Tile> tiles() { return parts.stream().flatMap(p -> p.tiles.stream()).toList(); }

    public static Optional<Blueprint> read(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("blueprint") ? CODEC.parse(NbtOps.INSTANCE, tag.get("blueprint")).result() : Optional.empty();
    }
    public void write(ItemStack stack) {
        var encoded = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put("blueprint", encoded));
    }

    public static boolean copyCorner(UseOnContext context, ServerPlayer player) {
        ItemStack stack = context.getItemInHand();
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String dimension = player.level().dimension().location().toString();
        Optional<BlockPos> first = dimension.equals(tag.getStringOr("selection_dimension", ""))
                && tag.contains("selection_start") ? BlockPos.CODEC.parse(NbtOps.INSTANCE, tag.get("selection_start")).result()
                : Optional.empty();
        BlockPos clicked = context.getClickedPos();
        BlockPos start = first.orElse(clicked);
        if(Math.abs((long)start.getX()-clicked.getX())>=32 || Math.abs((long)start.getY()-clicked.getY())>=32
                || Math.abs((long)start.getZ()-clicked.getZ())>=32) return false;
        int x0 = Math.min(start.getX(), clicked.getX()), y0 = Math.min(start.getY(), clicked.getY()), z0 = Math.min(start.getZ(), clicked.getZ());
        int sx = Math.abs(start.getX() - clicked.getX()) + 1, sy = Math.abs(start.getY() - clicked.getY()) + 1,
                sz = Math.abs(start.getZ() - clicked.getZ()) + 1;
        if (sx > 32 || sy > 32 || sz > 32 || (long) sx * sy * sz > 512) return false;
        var parts = new ArrayList<Part>();
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x0 + sx - 1, y0 + sy - 1, z0 + sz - 1)) {
            if (!player.level().hasChunkAt(p)) return false;
            List<Tile> tiles;
            if (player.level().getBlockEntity(p) instanceof LittleBlockEntity be) tiles = be.tiles();
            else {
                var state = player.level().getBlockState(p);
                if (state.isAir()) continue;
                if (!MaterialRules.canCut(state, player.level(), p)) return false;
                tiles = List.of(new Tile(state, new MicroBox(0, 0, 0, 16, 16, 16), 0xFFFFFF, true));
            }
            if (!tiles.isEmpty()) parts.add(new Part(p.subtract(new BlockPos(x0, y0, z0)), tiles));
        }
        Blueprint blueprint = new Blueprint(parts);
        if (!blueprint.valid()) return false;
        blueprint.write(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> {
            if (first.isPresent()) { data.remove("selection_start"); data.remove("selection_dimension"); }
            else {
                data.put("selection_start", BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, clicked).getOrThrow());
                data.putString("selection_dimension", dimension);
            }
        });
        return true;
    }

    public boolean place(UseOnContext context, ServerPlayer player) {
        if (!valid()) return false;
        if (parts.size() == 1 && parts.getFirst().offset.equals(BlockPos.ZERO)) {
            return TileItem.place(context, player, parts.getFirst().tiles);
        } else {
            var mode=ToolSettings.read(context.getItemInHand()).placeMode();
            BlockPos anchor = mode==ToolSettings.PlaceMode.OVERWRITE||mode==ToolSettings.PlaceMode.STENCIL
                    ?context.getClickedPos():context.getClickedPos().relative(context.getClickedFace());
            Map<BlockPos, List<Tile>> changes = new LinkedHashMap<>();
            var costs=new ArrayList<Part>();var refunds=new ArrayList<List<Tile>>();
            for (Part part : parts) {
                BlockPos pos = anchor.offset(part.offset);
                var existing=CuttingTool.source(player,pos);
                if(existing==null) return false;
                var planned=PlacementMath.plan(existing,part.tiles,mode);
                if(planned.isEmpty()) {
                    if(mode==ToolSettings.PlaceMode.FILL||mode==ToolSettings.PlaceMode.STENCIL) continue;
                    return false;
                }
                var plan=planned.get();
                if(!player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()) || (!plan.added().isEmpty()
                        && !player.level().isUnobstructed(null,Tiles.shape(plan.added(),true).move(pos.getX(),pos.getY(),pos.getZ())))) return false;
                changes.put(pos,plan.output());
                if(!plan.added().isEmpty()) costs.add(new Part(part.offset,plan.added()));
                if(!plan.removed().isEmpty()) refunds.add(plan.removed());
            }
            var payment=!player.getAbilities().instabuild && !costs.isEmpty()
                    ?MaterialPayment.plan(player,new Blueprint(costs)):Optional.<MaterialPayment>empty();
            if(!player.getAbilities().instabuild && !costs.isEmpty() && payment.isEmpty()) return false;
            if (!EditHistory.replaceAll(player, anchor, changes, true)) return false;
            payment.ifPresent(p -> p.apply(player));
            if(!player.getAbilities().instabuild) refunds.forEach(tiles -> CuttingTool.give(player,tiles));
        }
        return true;
    }

    public Blueprint transform(MicroBox.Axis axis, boolean mirror) {
        int sx = parts.stream().mapToInt(p -> p.offset.getX() + 1).max().orElse(1) * 16;
        int sy = parts.stream().mapToInt(p -> p.offset.getY() + 1).max().orElse(1) * 16;
        int sz = parts.stream().mapToInt(p -> p.offset.getZ() + 1).max().orElse(1) * 16;
        record Positioned(Tile tile, MicroBox box) {}
        var boxes = new ArrayList<Positioned>();
        for (Part part : parts) for (Tile tile : part.tiles) {
            MicroBox global = tile.box().translate(part.offset.getX() * 16, part.offset.getY() * 16, part.offset.getZ() * 16);
            global = mirror ? global.mirror(axis, 0) : global.rotate(axis, 1, 0, 0, 0);
            boxes.add(new Positioned(tile.transformMaterial(axis,mirror), global));
        }
        MicroBox extent = new MicroBox(0, 0, 0, sx, sy, sz);
        extent = mirror ? extent.mirror(axis, 0) : extent.rotate(axis, 1, 0, 0, 0);
        int x0 = Math.floorDiv(extent.minX(), 16), y0 = Math.floorDiv(extent.minY(), 16), z0 = Math.floorDiv(extent.minZ(), 16);
        Map<BlockPos, List<Tile>> split = new LinkedHashMap<>();
        for (var positioned : boxes) {
            MicroBox b = positioned.box;
            for (int x = Math.floorDiv(b.minX(), 16); x <= Math.floorDiv(b.maxX() - 1, 16); x++)
                for (int y = Math.floorDiv(b.minY(), 16); y <= Math.floorDiv(b.maxY() - 1, 16); y++)
                    for (int z = Math.floorDiv(b.minZ(), 16); z <= Math.floorDiv(b.maxZ() - 1, 16); z++) {
                        MicroBox local = b.intersection(new MicroBox(x * 16, y * 16, z * 16, (x + 1) * 16, (y + 1) * 16, (z + 1) * 16))
                                .orElseThrow().translate(-x * 16, -y * 16, -z * 16);
                        split.computeIfAbsent(new BlockPos(x - x0, y - y0, z - z0), p -> new ArrayList<>())
                                .add(positioned.tile.withBox(local));
                    }
        }
        return new Blueprint(split.entrySet().stream().map(e -> new Part(e.getKey(), e.getValue())).toList());
    }
}
