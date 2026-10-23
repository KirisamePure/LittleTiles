package com.creativemd.littletiles.fabric.tile;

import com.creativemd.littletiles.core.MicroBox;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Validation, immutable snapshots, shape generation and item persistence. */
public final class Tiles {
    public static final int MAX_TILES = 512;
    public static final Codec<List<Tile>> CODEC = Tile.CODEC.listOf(0, MAX_TILES).validate(tiles ->
            valid(tiles) ? DataResult.success(List.copyOf(tiles))
                    : DataResult.error(() -> "Overlapping or invalid micro-tiles"));

    private Tiles() {}

    public static boolean valid(List<Tile> tiles) {
        if (tiles.size() > MAX_TILES) return false;
        for (int i = 0; i < tiles.size(); i++) {
            Tile tile = tiles.get(i);
            if (!tile.box().isInsideBlock() || !Tile.validMaterial(tile.material())) return false;
            for (int j = i + 1; j < tiles.size(); j++) {
                if (tile.box().intersects(tiles.get(j).box())) return false;
            }
        }
        return true;
    }

    public static VoxelShape shape(List<Tile> tiles, boolean collisionOnly) {
        VoxelShape shape = Shapes.empty();
        for (Tile tile : tiles) {
            if (collisionOnly && !tile.collision()) continue;
            MicroBox b = tile.box();
            shape = Shapes.or(shape, Shapes.box(b.minX() / 16.0, b.minY() / 16.0, b.minZ() / 16.0,
                    b.maxX() / 16.0, b.maxY() / 16.0, b.maxZ() / 16.0));
        }
        return shape.optimize();
    }

    public static Optional<List<Tile>> read(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("tiles")) return Optional.empty();
        return CODEC.parse(NbtOps.INSTANCE, tag.get("tiles")).result().map(List::copyOf);
    }

    public static void write(ItemStack stack, List<Tile> tiles) {
        var encoded = CODEC.encodeStart(NbtOps.INSTANCE, tiles).getOrThrow();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put("tiles", encoded));
    }

    public static List<Tile> transform(List<Tile> tiles, MicroBox.Axis axis, boolean mirror) {
        return tiles.stream().map(t -> t.transformMaterial(axis,mirror).withBox(mirror ? t.box().mirror(axis, 8)
                : t.box().rotate(axis, 1, 8, 8, 8))).toList();
    }

    /** Merge only matching materials, color and collision; never fill gaps. */
    public static List<Tile> compact(List<Tile> tiles) {
        var result = new ArrayList<>(tiles);
        int previous;
        do {
            previous=result.size();
            for (int axis=0;axis<3;axis++) {
                final int selectedAxis=axis;
                var groups=new java.util.LinkedHashMap<List<Object>,List<Tile>>();
                for(Tile tile:result) {
                    var b=tile.box();
                    List<Object> key=switch(axis) {
                        case 0 -> List.of(tile.material(),tile.color(),tile.collision(),b.minY(),b.maxY(),b.minZ(),b.maxZ());
                        case 1 -> List.of(tile.material(),tile.color(),tile.collision(),b.minX(),b.maxX(),b.minZ(),b.maxZ());
                        default -> List.of(tile.material(),tile.color(),tile.collision(),b.minX(),b.maxX(),b.minY(),b.maxY());
                    };
                    groups.computeIfAbsent(key,k -> new ArrayList<>()).add(tile);
                }
                result=new ArrayList<>();
                for(var group:groups.values()) {
                    group.sort(java.util.Comparator.comparingInt(t -> switch(selectedAxis) {
                        case 0 -> t.box().minX();case 1 -> t.box().minY();default -> t.box().minZ();
                    }));
                    Tile current=null;
                    for(Tile next:group) {
                        if(current==null) {current=next;continue;}
                        var merged=current.box().merge(next.box());
                        if(merged.isPresent()) current=current.withBox(merged.get());
                        else {result.add(current);current=next;}
                    }
                    if(current!=null) result.add(current);
                }
            }
        } while(result.size()<previous);
        return List.copyOf(result);
    }
}
