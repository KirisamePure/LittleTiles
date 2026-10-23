package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Bounded server-owned settings. Dimensions and grid are in sixteenths of a block. */
public record ToolSettings(int grid, int width, int height, int depth, CutMode cutMode, PlaceMode placeMode) {
    public enum CutMode { VOXEL, BOX, SLICE, REGION, EXTRACT, SPLIT }
    public enum PlaceMode { NORMAL, FILL, OVERWRITE, STENCIL }
    public static final ToolSettings DEFAULT = new ToolSettings(1,1,1,1,CutMode.VOXEL,PlaceMode.NORMAL);
    public static final Codec<ToolSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(1,16).fieldOf("grid").forGetter(ToolSettings::grid),
            Codec.intRange(1,16).fieldOf("width").forGetter(ToolSettings::width),
            Codec.intRange(1,16).fieldOf("height").forGetter(ToolSettings::height),
            Codec.intRange(1,16).fieldOf("depth").forGetter(ToolSettings::depth),
            Codec.intRange(0,CutMode.values().length-1).xmap(n -> CutMode.values()[n], Enum::ordinal)
                    .fieldOf("cut_mode").forGetter(ToolSettings::cutMode),
            Codec.intRange(0,PlaceMode.values().length-1).xmap(n -> PlaceMode.values()[n], Enum::ordinal)
                    .fieldOf("place_mode").forGetter(ToolSettings::placeMode)).apply(i, ToolSettings::new));
    public boolean valid() { return grid>=1 && grid<=16 && width>=1 && width<=16 && height>=1 && height<=16
            && depth>=1 && depth<=16 && cutMode!=null && placeMode!=null; }
    public static boolean configurable(ItemStack stack) {
        return stack.is(LittleTilesContent.HAMMER) || stack.is(LittleTilesContent.CHISEL) || stack.is(LittleTilesContent.SAW)
                || stack.is(LittleTilesContent.BLUEPRINT) || stack.is(LittleTilesContent.TILES);
    }
    public static ToolSettings read(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (tag.contains("tool_settings")) {
            var result = CODEC.parse(NbtOps.INSTANCE, tag.get("tool_settings")).result();
            if (result.isPresent()) return result.get();
        }
        return stack.is(LittleTilesContent.SAW) ? new ToolSettings(1,1,1,1,CutMode.SPLIT,PlaceMode.NORMAL) : DEFAULT;
    }
    public void write(ItemStack stack) {
        if (!valid()) throw new IllegalArgumentException("Invalid tool settings");
        var encoded = CODEC.encodeStart(NbtOps.INSTANCE, this).getOrThrow();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.put("tool_settings", encoded); tag.remove("cut_corner"); tag.remove("cut_dimension");
        });
    }
}
