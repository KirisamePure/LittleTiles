package com.creativemd.littletiles.fabric.test;

import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.block.model.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Emission-only model with world-dependent geometry key, tint and multiple render layers. */
public final class CompatibilityFixtureClient implements ClientModInitializer {
    private record Key(Object model, BlockState neighbor) {}
    @Override public void onInitializeClient() {
        ColorProviderRegistry.BLOCK.register((state, world, pos, tint) -> world == null ? 0x88CC44
                : world.getBlockState(pos.east(2)).is(Blocks.GOLD_BLOCK) ? 0xFF6633 : 0x33AAEE, CompatibilityFixture.BLOCK);
        ModelLoadingPlugin.register(plugin -> plugin.modifyBlockModelAfterBake().register((model, context) -> {
            if (!context.state().is(CompatibilityFixture.BLOCK)) return model;
            return new BlockStateModel() {
                @Override public void collectParts(RandomSource random, List<BlockModelPart> output) {
                    throw new AssertionError("Must use Fabric emitQuads, not vanilla-only collectParts");
                }
                @Override public TextureAtlasSprite particleIcon() { return model.particleIcon(); }
                @Override public Object createGeometryKey(BlockAndTintGetter world, BlockPos pos, BlockState state, RandomSource random) {
                    return new Key(((FabricBlockStateModel) model).createGeometryKey(world, pos, state, random), world.getBlockState(pos.east(2)));
                }
                @Override public void emitQuads(QuadEmitter emitter, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, RandomSource random, Predicate<Direction> cullTest) {
                    if (!world.getBlockState(pos).is(CompatibilityFixture.BLOCK))
                        throw new AssertionError("Model must see its own material state, not the micro-tile container");
                    emitter.pushTransform(quad -> {
                        quad.tintIndex(0);
                        quad.color(-1, -1, -1, -1);
                        if (quad.lightFace() == Direction.UP) quad.renderLayer(ChunkSectionLayer.TRANSLUCENT);
                        if (quad.lightFace() == Direction.NORTH) quad.emissive(true);
                        return true;
                    });
                    try { ((FabricBlockStateModel) model).emitQuads(emitter, world, pos, state, random, cullTest); }
                    finally { emitter.popTransform(); }
                }
            };
        }));
    }
}
