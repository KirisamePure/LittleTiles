package com.creativemd.littletiles.fabric.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

/** Namespace-independent material support; no vanilla or per-mod allowlist. */
public final class MaterialRules {
    public static final TagKey<Block> UNCUTTABLE = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("littletiles", "uncuttable"));
    private MaterialRules() {}
    // Persistence must not depend on querying a mod's contextual shape in an empty/fake world.
    public static boolean serializable(BlockState state) {
        return !state.isAir() && !state.hasBlockEntity() && state.getFluidState().isEmpty()
                && state.getRenderShape() == RenderShape.MODEL;
    }
    public static boolean canCut(BlockState state, BlockGetter world, BlockPos pos) {
        return serializable(state) && !state.is(UNCUTTABLE) && Block.isShapeFullBlock(state.getShape(world, pos));
    }
}
