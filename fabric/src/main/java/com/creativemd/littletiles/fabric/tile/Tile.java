package com.creativemd.littletiles.fabric.tile;

import com.creativemd.littletiles.core.MicroBox;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;

/** One material cuboid in block-local sixteenth coordinates. */
public record Tile(BlockState material, MicroBox box, int color, boolean collision) {
    public static final Codec<MicroBox> BOX_CODEC = Codec.INT.listOf(6, 6).comapFlatMap(values -> {
        try {
            MicroBox b = new MicroBox(values.get(0), values.get(1), values.get(2),
                    values.get(3), values.get(4), values.get(5));
            return b.isInsideBlock() ? DataResult.success(b) : DataResult.error(() -> "Box is outside its block");
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Invalid micro-box: " + e.getMessage());
        }
    }, b -> java.util.List.of(b.minX(), b.minY(), b.minZ(), b.maxX(), b.maxY(), b.maxZ()));

    public static final Codec<Tile> CODEC = RecordCodecBuilder.<Tile>create(i -> i.group(
            BlockState.CODEC.fieldOf("material").forGetter(Tile::material),
            BOX_CODEC.fieldOf("box").forGetter(Tile::box),
            Codec.INT.optionalFieldOf("color", 0xFFFFFF).forGetter(Tile::color),
            Codec.BOOL.optionalFieldOf("collision", true).forGetter(Tile::collision)
    ).apply(i, Tile::new)).validate(tile -> validMaterial(tile.material)
            ? DataResult.success(tile) : DataResult.error(() -> "Unsupported micro-tile material"));

    public Tile {
        java.util.Objects.requireNonNull(material);
        java.util.Objects.requireNonNull(box);
        if (!box.isInsideBlock()) throw new IllegalArgumentException("Box is outside its block");
        color &= 0xFFFFFF;
    }

    public static boolean validMaterial(BlockState state) {
        return MaterialRules.serializable(state);
    }

    public Tile withBox(MicroBox replacement) { return new Tile(material, replacement, color, collision); }
    public Tile withColor(int replacement) { return new Tile(material, box, replacement, collision); }
    public Tile withCollision(boolean replacement) { return new Tile(material, box, color, replacement); }

    /** Use mod-provided horizontal rotation/mirroring hooks and standard vertical directional properties. */
    public Tile transformMaterial(MicroBox.Axis axis,boolean mirror) {
        var changed=material;
        var mcAxis=net.minecraft.core.Direction.Axis.valueOf(axis.name());
        if(mirror && axis!=MicroBox.Axis.Y) changed=material.mirror(axis==MicroBox.Axis.X
                ?net.minecraft.world.level.block.Mirror.FRONT_BACK:net.minecraft.world.level.block.Mirror.LEFT_RIGHT);
        else if(!mirror && axis==MicroBox.Axis.Y) changed=material.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
        else {
            if(!mirror && changed.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)) {
                var old=changed.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS);
                var next=old==mcAxis?old:net.minecraft.core.Direction.Axis.values()[3-old.ordinal()-mcAxis.ordinal()];
                changed=changed.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,next);
            }
            for(var property:material.getProperties()) if(property.getValueClass()==net.minecraft.core.Direction.class) {
                @SuppressWarnings("unchecked")
                var direction=(net.minecraft.world.level.block.state.properties.Property<net.minecraft.core.Direction>)(net.minecraft.world.level.block.state.properties.Property<?>)property;
                var old=changed.getValue(direction);
                var next=mirror?(old.getAxis()==mcAxis?old.getOpposite():old)
                        :old.getAxis()==mcAxis?old:old.getCounterClockWise(mcAxis);
                if(direction.getPossibleValues().contains(next)) changed=changed.setValue(direction,next);
            }
        }
        return new Tile(changed,box,color,collision);
    }
}
