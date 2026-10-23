package com.creativemd.littletiles.fabric.tile;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class LittleBlockEntity extends BlockEntity {
    private List<Tile> tiles = List.of();
    private VoxelShape outline = Shapes.empty(), collision = Shapes.empty();
    private BlockState representativeMaterial;

    public LittleBlockEntity(BlockPos pos, BlockState state) { super(LittleTilesContent.TILE_ENTITY, pos, state); }

    public List<Tile> tiles() { return tiles; }
    public VoxelShape outline() { return outline; }
    public VoxelShape collision() { return collision; }
    public BlockState representativeMaterial() { return representativeMaterial; }

    private void assign(List<Tile> replacement) {
        tiles = List.copyOf(replacement);
        outline = Tiles.shape(tiles, false);
        collision = Tiles.shape(tiles, true);
        representativeMaterial = !tiles.isEmpty() && tiles.stream().mapToLong(t -> t.box().volume()).sum() == 4096
                && tiles.stream().allMatch(t -> t.material().equals(tiles.getFirst().material()))
                ? tiles.getFirst().material() : null;
    }

    public void replace(List<Tile> replacement) {
        if (level == null || level.isClientSide()) throw new IllegalStateException("Edits require the logical server");
        if (!Tiles.valid(replacement)) throw new IllegalArgumentException("Invalid micro-tile snapshot");
        assign(replacement);
        BlockState state = LittleBlock.stateFor(tiles);
        if (!getBlockState().equals(state)) level.setBlock(worldPosition, state, 3);
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("format", 1);
        output.store("tiles", Tiles.CODEC, tiles);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        assign(input.read("tiles", Tiles.CODEC).orElse(List.of()));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveCustomOnly(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
