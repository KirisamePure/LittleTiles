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
        boolean changed;
        do {
            changed = false;
            outer: for (int i = 0; i < result.size(); i++) {
                for (int j = i + 1; j < result.size(); j++) {
                    Tile a = result.get(i), b = result.get(j);
                    if (!a.material().equals(b.material()) || a.color() != b.color()
                            || a.collision() != b.collision()) continue;
                    var merged = a.box().merge(b.box());
                    if (merged.isPresent()) {
                        result.set(i, a.withBox(merged.get())); result.remove(j);
                        changed = true; break outer;
                    }
                }
            }
        } while (changed);
        return List.copyOf(result);
    }
}
