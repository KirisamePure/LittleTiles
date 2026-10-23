package com.creativemd.littletiles.fabric.tile;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LittleBlock extends BaseEntityBlock {
    public static final MapCodec<LittleBlock> CODEC = simpleCodec(LittleBlock::new);
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 0, 15);
    public static final BooleanProperty OPAQUE = BooleanProperty.create("opaque");
    public LittleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIGHT, 0).setValue(OPAQUE,false));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(LIGHT,OPAQUE); }
    public static BlockState stateFor(List<Tile> tiles) {
        return LittleTilesContent.TILE_BLOCK.defaultBlockState().setValue(LIGHT,
                tiles.stream().mapToInt(t -> t.material().getLightEmission()).max().orElse(0))
                .setValue(OPAQUE,!tiles.isEmpty() && tiles.stream().allMatch(t -> t.material().canOcclude())
                        && tiles.stream().mapToLong(t -> t.box().volume()).sum()==4096);
    }
    @Override protected VoxelShape getOcclusionShape(BlockState state) {return state.getValue(OPAQUE)?Shapes.block():Shapes.empty();}
    @Override protected int getLightBlock(BlockState state) {return state.getValue(OPAQUE)?15:0;}
    @Override protected boolean propagatesSkylightDown(BlockState state) {return !state.getValue(OPAQUE);}
    @Override protected MapCodec<LittleBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new LittleBlockEntity(pos, state); }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }
    @Override protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return world.getBlockEntity(pos) instanceof LittleBlockEntity be ? be.outline() : Shapes.empty();
    }
    @Override protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return world.getBlockEntity(pos) instanceof LittleBlockEntity be ? be.collision() : Shapes.empty();
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (!(params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof LittleBlockEntity be)
                || be.tiles().isEmpty()) return List.of();
        ItemStack stack = new ItemStack(LittleTilesContent.TILES);
        Tiles.write(stack, be.tiles());
        return List.of(stack);
    }
    @Override protected ItemStack getCloneItemStack(LevelReader world, BlockPos pos, BlockState state, boolean includeData) {
        if (!(world.getBlockEntity(pos) instanceof LittleBlockEntity be) || be.tiles().isEmpty()) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(LittleTilesContent.TILES);
        Tiles.write(stack, be.tiles());
        return stack;
    }
}
