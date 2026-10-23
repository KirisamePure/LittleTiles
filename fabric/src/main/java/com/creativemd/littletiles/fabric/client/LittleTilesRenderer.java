package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Cached, world-aware meshes batched by render layer, including cross-block surface occlusion. */
public final class LittleTilesRenderer implements BlockEntityRenderer<LittleBlockEntity, LittleTilesRenderer.State> {
    private static final MaterialMeshBuilder.Mesh EMPTY = new MaterialMeshBuilder.Mesh(List.of(), 0, 0);
    private static final class Cached {
        List<Tile> source;
        long checkedTick = Long.MIN_VALUE;
        List<Object> signature = List.of();
        MaterialMeshBuilder.Mesh mesh = EMPTY;
    }
    private final MaterialMeshBuilder builder = new MaterialMeshBuilder();
    private final Map<LittleBlockEntity, Cached> cache = new WeakHashMap<>();
    private long meshBuilds;
    public long meshBuilds() { return meshBuilds; }
    public long modelCaptures() { return builder.captures(); }
    public static final class State extends BlockEntityRenderState {
        public MaterialMeshBuilder.Mesh mesh = EMPTY;
    }
    public LittleTilesRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public State createRenderState() { return new State(); }
    public MaterialMeshBuilder.Mesh meshFor(LittleBlockEntity entity) {
        if (entity.getLevel() == null) return EMPTY;
        var cached = cache.computeIfAbsent(entity, key -> new Cached());
        var world = entity.getLevel();
        long tick = world.getGameTime();
        if (cached.source == entity.tiles() && cached.checkedTick == tick) return cached.mesh;
        Map<BlockState, MaterialMeshBuilder.Prepared> prepared = new LinkedHashMap<>();
        for (Tile tile : entity.tiles()) prepared.computeIfAbsent(tile.material(),
                state -> builder.prepare(state, world, entity.getBlockPos(), false));
        var signature = new ArrayList<Object>();
        for (var entry : prepared.entrySet()) { signature.add(entry.getKey()); signature.add(entry.getValue().signature()); }
        for (Direction face : Direction.values()) {
            BlockPos pos = entity.getBlockPos().relative(face);
            signature.add(world.getBlockState(pos));
            if (world.getBlockEntity(pos) instanceof LittleBlockEntity be) signature.add(be.tiles());
        }
        if (cached.source != entity.tiles() || !cached.signature.equals(signature)) {
            cached.mesh = builder.build(entity.tiles(), prepared, neighbors(entity), world, false);
            cached.signature = List.copyOf(signature);
            cached.source = entity.tiles();
            meshBuilds++;
        }
        cached.checkedTick = tick;
        return cached.mesh;
    }
    private static List<MaterialMeshBuilder.Occluder> neighbors(LittleBlockEntity entity) {
        var result = new ArrayList<MaterialMeshBuilder.Occluder>();
        var world = entity.getLevel();
        for (Direction face : Direction.values()) {
            BlockPos pos = entity.getBlockPos().relative(face);
            int x = face.getStepX() * 16, y = face.getStepY() * 16, z = face.getStepZ() * 16;
            if (world.getBlockEntity(pos) instanceof LittleBlockEntity be) {
                for (Tile tile : be.tiles()) result.add(new MaterialMeshBuilder.Occluder(tile.material(), tile.box().translate(x,y,z), tile.color()));
            } else {
                BlockState state = world.getBlockState(pos);
                if (!state.isAir() && Block.isShapeFullBlock(state.getShape(world, pos)))
                    result.add(new MaterialMeshBuilder.Occluder(state, new MicroBox(x,y,z,x+16,y+16,z+16), 0xFFFFFF));
            }
        }
        return result;
    }
    @Override public void extractRenderState(LittleBlockEntity entity, State state, float tick, Vec3 camera,
                                             ModelFeatureRenderer.CrumblingOverlay breaking) {
        BlockEntityRenderer.super.extractRenderState(entity, state, tick, camera, breaking);
        state.mesh = meshFor(entity);
        // A full opaque group blocks light at its own position. Its visible surface
        // receives light from the surrounding cells, as ordinary terrain does.
        if (entity.getLevel() != null) for (Direction face : Direction.values())
            state.lightCoords = MaterialMeshBuilder.combineLight(state.lightCoords,
                    net.minecraft.client.renderer.LevelRenderer.getLightColor(entity.getLevel(), entity.getBlockPos().relative(face)));
    }
    @Override public void submit(State state, PoseStack poses, SubmitNodeCollector collector, CameraRenderState camera) {
        submitMesh(state.mesh, poses, collector, state.lightCoords, OverlayTexture.NO_OVERLAY);
    }
    public static void submitMesh(MaterialMeshBuilder.Mesh mesh, PoseStack poses, SubmitNodeCollector collector, int light, int overlay) {
        for (var batch : mesh.batches()) collector.submitCustomGeometry(poses, batch.layer(), (pose, vertices) -> {
            for (var quad : batch.quads()) for (var v : quad.vertices()) {
                vertices.addVertex(pose, v.x(), v.y(), v.z()).setColor(v.r(), v.g(), v.b(), v.a()).setUv(v.u(), v.v())
                        .setOverlay(overlay).setLight(MaterialMeshBuilder.combineLight(light, v.light()))
                        .setNormal(pose, v.nx(), v.ny(), v.nz());
            }
        });
    }
}
