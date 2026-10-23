package com.creativemd.littletiles.fabric.test;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.EmptyBlockAndTintGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Separate test-mod material: foreign registry ID, directional state and contextual shape. */
public final class CompatibilityFixture implements ModInitializer {
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("littletiles-test", "decorative_pillar");
    public static final RotatedPillarBlock BLOCK = Registry.register(BuiltInRegistries.BLOCK, ID,
            new RotatedPillarBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, ID))
                    .strength(1.5F).dynamicShape()) {
                @Override protected VoxelShape getOcclusionShape(BlockState state) { return Shapes.block(); }
                @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
                    if (world == EmptyBlockGetter.INSTANCE || world == EmptyBlockAndTintGetter.INSTANCE)
                        return Shapes.empty(); // Valid bootstrap fallback, not the shape in a real world.
                    return Shapes.block();
                }
            });
    public static final Item ITEM = Registry.register(BuiltInRegistries.ITEM, ID,
            new BlockItem(BLOCK, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, ID))));
    @Override public void onInitialize() {}
}
