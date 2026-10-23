package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/** Exposes the source material to contextual Fabric models and tint providers. */
public record MaterialView(BlockAndTintGetter delegate, BlockPos origin, BlockState material) implements BlockAndTintGetter {
    public MaterialView { origin = origin.immutable(); }
    @Override public BlockState getBlockState(BlockPos pos) {
        if (pos.equals(origin)) return material;
        if (delegate.getBlockEntity(pos) instanceof LittleBlockEntity be && be.representativeMaterial() != null)
            return be.representativeMaterial();
        return delegate.getBlockState(pos);
    }
    @Override public BlockEntity getBlockEntity(BlockPos pos) { return pos.equals(origin) ? null : delegate.getBlockEntity(pos); }
    @Override public FluidState getFluidState(BlockPos pos) { return pos.equals(origin) ? material.getFluidState() : delegate.getFluidState(pos); }
    @Override public int getHeight() { return delegate.getHeight(); }
    @Override public int getMinY() { return delegate.getMinY(); }
    @Override public float getShade(Direction direction, boolean shade) { return delegate.getShade(direction, shade); }
    @Override public LevelLightEngine getLightEngine() { return delegate.getLightEngine(); }
    @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return delegate.getBlockTint(pos, resolver); }
}
