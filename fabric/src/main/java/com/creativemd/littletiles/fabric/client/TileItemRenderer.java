package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.fabric.tile.Tiles;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

public final class TileItemRenderer implements SpecialModelRenderer<MaterialMeshBuilder.Mesh> {
    private final MaterialMeshBuilder builder = new MaterialMeshBuilder();
    private final Map<CustomData, MaterialMeshBuilder.Mesh> cache = new LinkedHashMap<>(16, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<CustomData, MaterialMeshBuilder.Mesh> entry) { return size() > 64; }
    };
    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> CODEC = MapCodec.unit(Unbaked::new);
        @Override public MapCodec<Unbaked> type() { return CODEC; }
        @Override public SpecialModelRenderer<?> bake(BakingContext context) { return new TileItemRenderer(); }
    }
    @Override public MaterialMeshBuilder.Mesh extractArgument(ItemStack stack) {
        return cache.computeIfAbsent(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY), ignored -> {
            var tiles = Tiles.read(stack).orElse(List.of());
            Map<BlockState, MaterialMeshBuilder.Prepared> prepared = new LinkedHashMap<>();
            var world = EmptyBlockAndTintGetter.INSTANCE;
            for (var tile : tiles) prepared.computeIfAbsent(tile.material(),
                    state -> builder.prepare(state, world, BlockPos.ZERO, true));
            return builder.build(tiles, prepared, List.of(), world, true);
        });
    }
    @Override public void getExtents(Set<Vector3f> extents) {
        extents.add(new Vector3f(0,0,0)); extents.add(new Vector3f(1,1,1));
    }
    @Override public void submit(MaterialMeshBuilder.Mesh mesh, ItemDisplayContext display, PoseStack poses,
                                  SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
        if (mesh != null) LittleTilesRenderer.submitMesh(mesh, poses, collector, light, overlay);
    }
}
