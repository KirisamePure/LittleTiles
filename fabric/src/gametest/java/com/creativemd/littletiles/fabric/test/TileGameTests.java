package com.creativemd.littletiles.fabric.test;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.EditHistory;
import com.creativemd.littletiles.fabric.item.BagItem;
import com.creativemd.littletiles.fabric.item.MaterialPayment;
import com.creativemd.littletiles.fabric.item.Blueprint;
import com.creativemd.littletiles.fabric.edit.PreviewAction;
import com.creativemd.littletiles.fabric.edit.SettingsPayload;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import com.creativemd.littletiles.fabric.item.PlacementMath;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class TileGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private static Tile half() {
        return new Tile(Blocks.STONE.defaultBlockState(), new MicroBox(0, 0, 0, 8, 16, 16), 0xAABBCC, true);
    }
    private static void check(GameTestHelper h, boolean value, String message) { h.assertTrue(value, Component.literal(message)); }
    private static LittleBlockEntity block(GameTestHelper h, List<Tile> tiles) {
        h.setBlock(POS, LittleTilesContent.TILE_BLOCK);
        LittleBlockEntity be = h.getBlockEntity(POS, LittleBlockEntity.class);
        be.replace(tiles);
        return be;
    }
    private static ServerPlayer player(GameTestHelper h, boolean creative) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(creative ? GameType.CREATIVE : GameType.SURVIVAL);
        Vec3 pos = Vec3.atLowerCornerOf(h.absolutePos(POS)).add(0.5, 0, 2);
        player.setPos(pos);
        return player;
    }
    private static UseOnContext context(GameTestHelper h, ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atLowerCornerOf(h.absolutePos(POS)).add(0.25, 1, 0.5), Direction.UP,
                        h.absolutePos(POS), false));
    }

    @GameTest public void persistenceAndUpdateTag(GameTestHelper h) {
        List<Tile> tiles = List.of(half(), new Tile(Blocks.GLASS.defaultBlockState(),
                new MicroBox(8, 0, 0, 16, 8, 16), 0x123456, false));
        LittleBlockEntity be = block(h, tiles);
        var saved = be.saveCustomOnly(h.getLevel().registryAccess());
        LittleBlockEntity restored = new LittleBlockEntity(be.getBlockPos(), be.getBlockState());
        restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved));
        check(h, restored.tiles().equals(tiles), "Save/load must preserve material, bounds, color and collision");
        LittleBlockEntity client = new LittleBlockEntity(be.getBlockPos(), be.getBlockState());
        client.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(),
                be.getUpdateTag(h.getLevel().registryAccess())));
        check(h, client.tiles().equals(tiles), "Initial chunk/update NBT must contain complete tile data");
        check(h, be.getUpdatePacket() != null, "Block entity must emit a synchronization packet");
        check(h, restored.outline().bounds().maxX == 1 && restored.collision().bounds().maxX == 0.5,
                "Noncollidable tiles remain selectable but do not collide");
        h.succeed();
    }

    @GameTest public void itemAndBreakDropsPreserveData(GameTestHelper h) {
        LittleBlockEntity be = block(h, List.of(half()));
        ItemStack stack = new ItemStack(LittleTilesContent.TILES);
        Tiles.write(stack, be.tiles());
        check(h, Tiles.read(stack.copy()).orElseThrow().equals(be.tiles()), "Item copy must retain components");
        var drops = Block.getDrops(be.getBlockState(), h.getLevel(), be.getBlockPos(), be);
        check(h, drops.size() == 1 && Tiles.read(drops.getFirst()).orElseThrow().equals(be.tiles()),
                "Breaking must return the exact microtile contents");
        h.succeed();
    }

    @GameTest public void rejectOverlapsAndInvalidGeometry(GameTestHelper h) {
        Tile one = half();
        check(h, !Tiles.valid(List.of(one, one)), "Overlap must be rejected");
        var list = new net.minecraft.nbt.ListTag();
        for (int v : new int[]{0, 0, 0, 17, 16, 16}) list.add(net.minecraft.nbt.IntTag.valueOf(v));
        check(h, Tile.BOX_CODEC.parse(NbtOps.INSTANCE, list).error().isPresent(), "Out-of-block input must fail codec validation");
        check(h, !Tile.validMaterial(Blocks.CHEST.defaultBlockState()), "Do not erase material block-entity inventories");
        h.succeed();
    }

    @GameTest public void historyAndConflictProtection(GameTestHelper h) {
        var be = block(h, List.of(half()));
        ServerPlayer player = player(h, true);
        EditHistory.clear(player.getUUID());
        List<Tile> changed = List.of(half().withColor(0xFF0000));
        check(h, EditHistory.replace(player, be.getBlockPos(), changed, false), "Edit should succeed");
        check(h, EditHistory.undo(player), "Undo should succeed");
        check(h, be.tiles().equals(List.of(half())), "Undo restores exact data");
        check(h, EditHistory.redo(player) && be.tiles().equals(changed), "Redo restores exact data");
        be.replace(List.of(half().withColor(0x00FF00)));
        check(h, !EditHistory.undo(player), "Undo must not overwrite another player's edits");
        check(h, be.tiles().getFirst().color() == 0x00FF00, "Conflicting data must remain untouched");
        EditHistory.clear(player.getUUID());
        h.succeed();
    }

    @GameTest public void chiselConservesSurvivalMaterial(GameTestHelper h) {
        LittleBlockEntity be = block(h, List.of(half()));
        ServerPlayer player = player(h, false);
        ItemStack chisel = new ItemStack(LittleTilesContent.CHISEL);
        var result = chisel.getItem().useOn(context(h, player, chisel));
        check(h, result.consumesAction(), "Chisel interaction must execute");
        long remaining = be.tiles().stream().mapToLong(t -> t.box().volume()).sum();
        long returned = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item.is(LittleTilesContent.TILES)) returned += Tiles.read(item).orElseThrow().stream()
                    .mapToLong(t -> t.box().volume()).sum() * item.getCount();
        }
        check(h, remaining == half().box().volume() - 1 && returned == 1, "Cutting must conserve exact material volume");
        check(h, !EditHistory.undo(player), "Survival cannot duplicate returned material through creative undo");
        h.succeed();
    }

    @GameTest public void survivalPlacementRejectsOverlapWithoutConsuming(GameTestHelper h) {
        LittleBlockEntity be = block(h, List.of(half()));
        ServerPlayer player = player(h, false);
        Tile small = new Tile(Blocks.STONE.defaultBlockState(), new MicroBox(0, 0, 0, 1, 1, 1), 0xFFFFFF, true);
        ItemStack stack = new ItemStack(LittleTilesContent.TILES, 3);
        Tiles.write(stack, List.of(small));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // Eastern face of the left half: add a voxel in the empty right half.
        var hit = new BlockHitResult(Vec3.atLowerCornerOf(h.absolutePos(POS)).add(0.5, 0.5, 0.5),
                Direction.EAST, h.absolutePos(POS), false);
        var use = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        check(h, stack.getItem().useOn(use).consumesAction(), "Place into free micro-space");
        check(h, stack.getCount() == 2 && be.tiles().size() == 2, "Successful placement consumes one item");
        check(h, !stack.getItem().useOn(use).consumesAction(), "Overlapping placement fails");
        check(h, stack.getCount() == 2 && be.tiles().size() == 2, "Failed placement leaves inventory/world intact");
        h.succeed();
    }

    @GameTest public void materialPaymentIsAtomicAndRefundsExactVolume(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        var design = List.of(half().withColor(0xFFFFFF));
        check(h, MaterialPayment.plan(player, design).isEmpty(), "Insufficient material fails");
        player.getInventory().setItem(1, new ItemStack(Items.STONE));
        var payment = MaterialPayment.plan(player, design).orElseThrow();
        check(h, player.getInventory().getItem(1).is(Items.STONE), "Planning must not consume materials");
        payment.apply(player);
        check(h, player.getInventory().getItem(1).isEmpty(), "Payment consumes source block");
        long volume = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item.is(LittleTilesContent.TILES)) volume += Tiles.read(item).orElseThrow().stream()
                    .mapToLong(t -> t.box().volume()).sum() * item.getCount();
        }
        check(h, volume == 2048, "Half a block of exact material must be refunded");
        h.succeed();
    }

    @GameTest public void bagPersistsAndCannotMoveIntoItself(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        ItemStack bag = new ItemStack(LittleTilesContent.BAG);
        int bound = player.getInventory().getSelectedSlot();
        player.getInventory().setItem(bound, bag);
        var contents = BagItem.contents(bag);
        contents.setItem(0, new ItemStack(Items.STONE, 3));
        check(h, BagItem.contents(bag.copy()).getItem(0).getCount() == 3, "Bag copy retains persisted inventory");
        check(h, !contents.canPlaceItem(1, bag), "Nested bags must be rejected");
        var menu = new BagItem.BagMenu(42, player.getInventory(), contents, bag, bound);
        int source = 54 + 27 + bound;
        check(h, menu.quickMoveStack(player, source).isEmpty(), "Cannot shift-click the bound bag");
        menu.clicked(0, bound, ClickType.SWAP, player);
        check(h, player.getInventory().getItem(bound) == bag && contents.getItem(0).is(Items.STONE),
                "Hotbar swap cannot move bag into storage");
        contents.setItem(0, ItemStack.EMPTY);
        check(h, BagItem.contents(bag).isEmpty(), "Removing items persists immediately");
        h.succeed();
    }

    @GameTest public void paymentCanUseBagWithoutConsumingBlueprint(GameTestHelper h) {
        ServerPlayer player = player(h, false);
        ItemStack design = new ItemStack(LittleTilesContent.BLUEPRINT);
        Tiles.write(design, List.of(half().withColor(0xFFFFFF)));
        player.getInventory().setItem(0, design);
        check(h, MaterialPayment.plan(player, List.of(half().withColor(0xFFFFFF))).isEmpty(),
                "Blueprint data must never count as physical material");
        ItemStack bag = new ItemStack(LittleTilesContent.BAG);
        BagItem.contents(bag).setItem(0, new ItemStack(Items.STONE));
        player.getInventory().setItem(1, bag);
        MaterialPayment.plan(player, List.of(half().withColor(0xFFFFFF))).orElseThrow().apply(player);
        check(h, BagItem.contents(bag).isEmpty(), "Material debit must persist inside bag");
        check(h, Tiles.read(design).orElseThrow().equals(List.of(half().withColor(0xFFFFFF))), "Blueprint remains reusable");
        h.succeed();
    }

    @GameTest public void batchEditPreflightAndHistoryAreAtomic(GameTestHelper h) {
        LittleBlockEntity be = block(h, List.of(half()));
        ServerPlayer player = player(h, true);
        BlockPos second = h.absolutePos(POS.east());
        h.getLevel().setBlockAndUpdate(second, Blocks.CHEST.defaultBlockState());
        var changes = new java.util.LinkedHashMap<BlockPos, List<Tile>>();
        changes.put(be.getBlockPos(), List.of(half().withColor(0xFF0000)));
        changes.put(second, List.of(half()));
        check(h, !EditHistory.replaceAll(player, be.getBlockPos(), changes, false), "One blocked target must reject the entire edit");
        check(h, be.tiles().equals(List.of(half())) && h.getLevel().getBlockState(second).is(Blocks.CHEST),
                "Preflight failure must not partially modify the world");
        h.getLevel().setBlockAndUpdate(second, Blocks.AIR.defaultBlockState());
        check(h, EditHistory.replaceAll(player, be.getBlockPos(), changes, false), "Multi-block edit succeeds");
        check(h, EditHistory.undo(player) && h.getLevel().getBlockState(second).isAir()
                && be.tiles().equals(List.of(half())), "Undo must restore all blocks together");
        check(h, EditHistory.redo(player) && h.getLevel().getBlockEntity(second) instanceof LittleBlockEntity,
                "Redo must restore the whole group");
        EditHistory.clear(player.getUUID()); h.succeed();
    }

    @GameTest public void multiblockBlueprintTransformsPreserveMaterial(GameTestHelper h) {
        var original = new Blueprint(List.of(new Blueprint.Part(BlockPos.ZERO, List.of(half())),
                new Blueprint.Part(new BlockPos(1, 0, 0), List.of(half().withColor(0xFFFFFF)))));
        check(h, original.valid(), "Two block-local boxes may overlap in local coordinates in different blocks");
        long volume = original.tiles().stream().mapToLong(t -> t.box().volume()).sum();
        for (var axis : MicroBox.Axis.values()) {
            var changed = original;
            for (int i = 0; i < 4; i++) changed = changed.transform(axis, false);
            check(h, changed.valid() && changed.tiles().stream().mapToLong(t -> t.box().volume()).sum() == volume,
                    "Four turns preserve valid geometry and material");
            check(h, new java.util.HashSet<>(changed.parts()).equals(new java.util.HashSet<>(original.parts())),
                    "Four turns must return the exact block offsets, shapes, material, color and collision");
            var mirrored = original.transform(axis, true).transform(axis, true);
            check(h, mirrored.valid() && mirrored.tiles().stream().mapToLong(t -> t.box().volume()).sum() == volume,
                    "Double mirror preserves valid geometry and material");
            check(h, new java.util.HashSet<>(mirrored.parts()).equals(new java.util.HashSet<>(original.parts())),
                    "Double mirror must return the exact design");
        }
        ItemStack stack = new ItemStack(LittleTilesContent.BLUEPRINT);
        original.write(stack);
        check(h, Blueprint.read(stack.copy()).orElseThrow().equals(original), "Multi-block design persists on an item");
        ServerPlayer player = player(h, true);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        check(h, !PreviewAction.apply(player, false, 99), "Malformed axis request must be rejected");
        check(h, Blueprint.read(stack).orElseThrow().equals(original), "Malformed request leaves item untouched");
        h.succeed();
    }

    @GameTest public void multiblockBlueprintPaysOnceAndFailedPasteCostsNothing(GameTestHelper h) {
        h.setBlock(POS, Blocks.STONE);
        ServerPlayer player = player(h, false);
        var white = half().withColor(0xFFFFFF);
        var design = new Blueprint(List.of(new Blueprint.Part(BlockPos.ZERO, List.of(white)),
                new Blueprint.Part(new BlockPos(1, 0, 0), List.of(white))));
        ItemStack blueprint = new ItemStack(LittleTilesContent.BLUEPRINT);
        design.write(blueprint);
        player.getInventory().setItem(1, new ItemStack(Items.STONE));
        var use = context(h, player, blueprint);
        check(h, design.place(use, player), "Multi-block survival paste succeeds with exact material");
        check(h, player.getInventory().getItem(1).isEmpty(), "Two half-block parts consume one full block, once");
        for (BlockPos p : List.of(h.absolutePos(POS.above()), h.absolutePos(POS.above().east()))) {
            check(h, h.getLevel().getBlockEntity(p) instanceof LittleBlockEntity be && be.tiles().equals(List.of(white)),
                    "Every target must contain its intended tiles");
        }
        player.getInventory().setItem(1, new ItemStack(Items.STONE));
        check(h, !design.place(use, player), "Overlapping group paste must fail");
        check(h, player.getInventory().getItem(1).is(Items.STONE), "Failed paste must not charge materials");
        h.succeed();
    }

    @GameTest public void emissiveMaterialsUpdateLightAndUndoRestoresIt(GameTestHelper h) {
        var glow = new Tile(Blocks.GLOWSTONE.defaultBlockState(), new MicroBox(0, 0, 0, 8, 16, 16), 0xFFFFFF, true);
        LittleBlockEntity be = block(h, List.of(glow));
        check(h, be.getBlockState().getLightEmission() == 15, "Micro-tiles must retain emissive material light");
        ServerPlayer player = player(h, true);
        check(h, EditHistory.replace(player, be.getBlockPos(), List.of(half()), false), "Replace emissive material");
        check(h, be.getBlockState().getLightEmission() == 0, "Removing light source updates block light");
        check(h, EditHistory.undo(player) && be.getBlockState().getLightEmission() == 15,
                "Undo must restore emission as well as tile geometry");
        EditHistory.clear(player.getUUID());
        h.succeed();
    }

    @GameTest public void foreignContextualBlockCanBeCutAndPersisted(GameTestHelper h) {
        var foreign = CompatibilityFixture.BLOCK.defaultBlockState()
                .setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X);
        h.setBlock(POS, foreign);
        ServerPlayer player = player(h, false);
        ItemStack hammer = new ItemStack(LittleTilesContent.HAMMER);
        check(h, hammer.getItem().useOn(context(h, player, hammer)).consumesAction(), "Foreign full cube must be cuttable");
        LittleBlockEntity be = h.getBlockEntity(POS, LittleBlockEntity.class);
        check(h, be.tiles().getFirst().material().equals(foreign), "Foreign block ID and directional properties must survive conversion");
        var saved = be.saveCustomOnly(h.getLevel().registryAccess());
        var restored = new LittleBlockEntity(be.getBlockPos(), be.getBlockState());
        restored.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, h.getLevel().registryAccess(), saved));
        check(h, restored.tiles().equals(be.tiles()), "Foreign contextual material must persist without empty-world shape queries");
        ItemStack chisel = new ItemStack(LittleTilesContent.CHISEL);
        check(h, chisel.getItem().useOn(context(h, player, chisel)).consumesAction(), "Foreign material must support cutting");
        check(h, be.tiles().stream().mapToLong(t -> t.box().volume()).sum() == 4095, "Cut must remove one voxel from foreign material");
        h.succeed();
    }

    @GameTest public void settingsAreBoundedAndPreserveItemGeometry(GameTestHelper h) {
        ServerPlayer player=player(h,false);
        ItemStack tool=new ItemStack(LittleTilesContent.CHISEL);player.setItemInHand(InteractionHand.MAIN_HAND,tool);
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(tool.getItem());
        var settings=new ToolSettings(2,3,2,4,ToolSettings.CutMode.BOX,ToolSettings.PlaceMode.NORMAL);
        check(h,!new SettingsPayload(id,0,3,2,4,1,0).apply(player),"Grid zero must be rejected");
        check(h,!new SettingsPayload(id,2,3,2,4,99,0).apply(player),"Unknown tool mode must be rejected");
        check(h,!SettingsPayload.of(net.minecraft.resources.ResourceLocation.withDefaultNamespace("stone"),settings).apply(player),
                "Configuration for another held item must be rejected");
        check(h,SettingsPayload.of(id,settings).apply(player) && ToolSettings.read(tool.copy()).equals(settings),
                "Valid settings must persist through item copies");
        ItemStack tiles=new ItemStack(LittleTilesContent.TILES);Tiles.write(tiles,List.of(half()));settings.write(tiles);
        check(h,Tiles.read(tiles).orElseThrow().equals(List.of(half())),"Settings must not overwrite stored material geometry");
        h.succeed();
    }

    @GameTest public void configuredBoxCutWorksDirectlyOnForeignBlock(GameTestHelper h) {
        h.setBlock(POS,CompatibilityFixture.BLOCK);
        ServerPlayer player=player(h,false);ItemStack chisel=new ItemStack(LittleTilesContent.CHISEL);
        new ToolSettings(1,3,2,4,ToolSettings.CutMode.BOX,ToolSettings.PlaceMode.NORMAL).write(chisel);
        check(h,chisel.getItem().useOn(context(h,player,chisel)).consumesAction(),"Configured box cut executes directly on a foreign cube");
        var be=h.getBlockEntity(POS,LittleBlockEntity.class);
        check(h,be.tiles().stream().mapToLong(t -> t.box().volume()).sum()==4096-24,"Cut depth must extend inward from the clicked top surface");
        check(h,inventoryTileVolume(player)==24,"All 24 removed microvoxels must be returned");h.succeed();
    }

    @GameTest public void regionCutSpansTwoForeignBlocksAtomically(GameTestHelper h) {
        h.setBlock(POS,CompatibilityFixture.BLOCK);h.setBlock(POS.east(),CompatibilityFixture.BLOCK);
        ServerPlayer player=player(h,false);ItemStack chisel=new ItemStack(LittleTilesContent.CHISEL);
        new ToolSettings(2,1,1,1,ToolSettings.CutMode.REGION,ToolSettings.PlaceMode.NORMAL).write(chisel);
        player.setItemInHand(InteractionHand.MAIN_HAND,chisel);
        for(BlockPos relative:List.of(POS,POS.east())) {
            var hit=new BlockHitResult(Vec3.atLowerCornerOf(h.absolutePos(relative)).add(0.125,1,0.125),Direction.UP,h.absolutePos(relative),false);
            check(h,chisel.getItem().useOn(new UseOnContext(player,InteractionHand.MAIN_HAND,hit)).consumesAction(),"Region selection corner executes");
        }
        long worldVolume=0;
        for(BlockPos relative:List.of(POS,POS.east())) worldVolume+=h.getBlockEntity(relative,LittleBlockEntity.class).tiles()
                .stream().mapToLong(t -> t.box().volume()).sum();
        check(h,worldVolume==8192-72 && inventoryTileVolume(player)==72,"Cross-block region cut must conserve exact foreign material volume");
        h.succeed();
    }

    @GameTest public void placementModesComputeOnlyActualCostsAndRefunds(GameTestHelper h) {
        var old=List.of(half().withColor(0xFFFFFF));
        var incoming=List.of(new Tile(Blocks.GLASS.defaultBlockState(),new MicroBox(0,0,0,16,16,16),0xFFFFFF,true));
        check(h,PlacementMath.plan(old,incoming,ToolSettings.PlaceMode.NORMAL).isEmpty(),"Normal mode rejects overlap");
        var fill=PlacementMath.plan(old,incoming,ToolSettings.PlaceMode.FILL).orElseThrow();
        check(h,volume(fill.added())==2048 && volume(fill.unused())==2048 && fill.removed().isEmpty(),"Fill mode only charges for empty volume");
        var overwrite=PlacementMath.plan(old,incoming,ToolSettings.PlaceMode.OVERWRITE).orElseThrow();
        check(h,volume(overwrite.added())==4096 && volume(overwrite.removed())==2048 && overwrite.output().equals(incoming),"Overwrite refunds displaced material");
        var stencil=PlacementMath.plan(old,incoming,ToolSettings.PlaceMode.STENCIL).orElseThrow();
        check(h,stencil.output().isEmpty() && stencil.added().isEmpty() && volume(stencil.removed())==2048,"Stencil removes existing volume without placing or charging material");
        h.succeed();
    }

    @GameTest public void stencilDoesNotConsumeStampAndReturnsRemovedMaterial(GameTestHelper h) {
        block(h,List.of(half()));ServerPlayer player=player(h,false);
        ItemStack stamp=new ItemStack(LittleTilesContent.TILES,3);
        Tiles.write(stamp,List.of(new Tile(Blocks.GLASS.defaultBlockState(),new MicroBox(0,0,0,16,16,16),0xFFFFFF,true)));
        new ToolSettings(1,1,1,1,ToolSettings.CutMode.VOXEL,ToolSettings.PlaceMode.STENCIL).write(stamp);
        check(h,stamp.getItem().useOn(context(h,player,stamp)).consumesAction(),"Stencil action executes");
        check(h,h.getLevel().getBlockState(h.absolutePos(POS)).isAir() && stamp.getCount()==3,"Stencil clears the existing group without consuming stamp");
        check(h,inventoryTileVolume(player)==3*4096+2048,"Removed material is refunded exactly once");h.succeed();
    }

    @GameTest public void directionalMaterialTransformsFollowGeometry(GameTestHelper h) {
        var state=CompatibilityFixture.BLOCK.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.X);
        var original=new Tile(state,new MicroBox(0,0,0,8,16,16),0xFFFFFF,true);
        var rotated=Tiles.transform(List.of(original),MicroBox.Axis.Y,false).getFirst();
        check(h,rotated.material().getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS)==Direction.Axis.Z,
                "Rotating a foreign directional material must also rotate its texture axis");
        var roundTrip=List.of(original);
        for(int i=0;i<4;i++) roundTrip=Tiles.transform(roundTrip,MicroBox.Axis.Y,false);
        check(h,roundTrip.equals(List.of(original)),"Four turns restore geometry and mod block properties");h.succeed();
    }

    @GameTest public void solidGroupsOccludeButPartialAndGlassGroupsDoNot(GameTestHelper h) {
        var full=new Tile(Blocks.STONE.defaultBlockState(),new MicroBox(0,0,0,16,16,16),0xFFFFFF,true);
        var be=block(h,List.of(full));
        check(h,be.getBlockState().getLightBlock()==15 && net.minecraft.world.level.block.Block.isShapeFullBlock(be.getBlockState().getOcclusionShape()),
                "Complete opaque groups should occlude light and neighboring terrain faces");
        be.replace(List.of(half()));
        check(h,be.getBlockState().getLightBlock()==0 && be.getBlockState().getOcclusionShape().isEmpty(),"Partial microtiles must not hide an entire neighbor face");
        be.replace(List.of(new Tile(Blocks.GLASS.defaultBlockState(),full.box(),0xFFFFFF,true)));
        check(h,be.getBlockState().getLightBlock()==0 && be.getBlockState().getOcclusionShape().isEmpty(),"Full transparent material must remain transparent to occlusion");h.succeed();
    }

    @GameTest public void remainingCutModesConserveMaterial(GameTestHelper h) {
        ServerPlayer player=player(h,false);
        for(var mode:List.of(ToolSettings.CutMode.VOXEL,ToolSettings.CutMode.BOX,ToolSettings.CutMode.SLICE,ToolSettings.CutMode.EXTRACT,ToolSettings.CutMode.SPLIT)) {
            h.setBlock(POS,CompatibilityFixture.BLOCK);
            ItemStack tool=new ItemStack(LittleTilesContent.CHISEL);
            new ToolSettings(2,3,2,4,mode,ToolSettings.PlaceMode.NORMAL).write(tool);
            long before=inventoryTileVolume(player);
            check(h,tool.getItem().useOn(context(h,player,tool)).consumesAction(),"Every configured mode must execute: "+mode);
            long removed=switch(mode) {case VOXEL->8;case BOX->24;case SLICE->768;case EXTRACT->4096;default->0;};
            long remaining=h.getLevel().getBlockEntity(h.absolutePos(POS)) instanceof LittleBlockEntity be?volume(be.tiles()):0;
            check(h,remaining==4096-removed && inventoryTileVolume(player)-before==removed,"Mode must conserve exact material volume: "+mode);
            if(mode==ToolSettings.CutMode.SPLIT) check(h,h.getBlockEntity(POS,LittleBlockEntity.class).tiles().size()==2,"Split must create two independently selectable pieces");
        }
        h.succeed();
    }

    private static long volume(List<Tile> tiles) {return tiles.stream().mapToLong(t -> t.box().volume()).sum();}
    private static long inventoryTileVolume(ServerPlayer player) {
        long volume=0;
        for(int i=0;i<player.getInventory().getContainerSize();i++) {
            ItemStack stack=player.getInventory().getItem(i);
            if(stack.is(LittleTilesContent.TILES)) volume+=Tiles.read(stack).map(TileGameTests::volume).orElse(0L)*stack.getCount();
        }
        return volume;
    }

    @GameTest public void hammerManufacturingConservesRemainder(GameTestHelper h) {
        var player=player(h,false);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(LittleTilesContent.HAMMER));
        player.getInventory().setItem(9,new ItemStack(Items.STONE,2));
        var packet=new com.creativemd.littletiles.fabric.edit.HammerPayload(9,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.STONE),3,5,7,17);
        check(h,packet.apply(player),"Hammer must manufacture from real inventory blocks");
        long volume=0;int produced=0;
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
            var stack=player.getInventory().getItem(slot);
            if(stack.is(Items.STONE)) volume+=4096L*stack.getCount();
            if(stack.is(LittleTilesContent.TILES)) {
                var tiles=Tiles.read(stack).orElseThrow();volume+=tiles.stream().mapToLong(t -> t.box().volume()).sum()*stack.getCount();
                if(tiles.size()==1 && tiles.getFirst().box().equals(new MicroBox(0,0,0,3,5,7))) produced+=stack.getCount();
            }
        }
        check(h,volume==8192 && produced==17,"Manufactured pieces plus exact refunds must conserve all input volume");
        check(h,ToolSettings.read(player.getMainHandItem()).width()==3,"Hammer remembers successful dimensions");h.succeed();
    }
    @GameTest public void hammerRejectsForgedAndStaleRequests(GameTestHelper h) {
        var player=player(h,false);player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(LittleTilesContent.HAMMER));
        player.getInventory().setItem(9,new ItemStack(Items.STONE));
        var stone=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.STONE);
        var dirt=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.DIRT);
        for(var request:List.of(
                new com.creativemd.littletiles.fabric.edit.HammerPayload(9,dirt,1,1,1,1),
                new com.creativemd.littletiles.fabric.edit.HammerPayload(9,stone,0,1,1,1),
                new com.creativemd.littletiles.fabric.edit.HammerPayload(9,stone,16,16,16,2),
                new com.creativemd.littletiles.fabric.edit.HammerPayload(9,stone,1,1,1,65),
                new com.creativemd.littletiles.fabric.edit.HammerPayload(39,stone,1,1,1,1)))
            check(h,!request.apply(player),"Reject invalid dimensions/count/slot, insufficient stock and stale material");
        check(h,player.getInventory().getItem(9).getCount()==1,"Rejected requests must not consume input");
        player.getInventory().setItem(9,new ItemStack(Items.CHEST));
        check(h,!new com.creativemd.littletiles.fabric.edit.HammerPayload(9,
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Items.CHEST),1,1,1,1).apply(player),"Never cut block-entity inventory materials");
        h.succeed();
    }
    @GameTest public void fastCompactionPreservesHolesAndIdentity(GameTestHelper h) {
        var stone=Blocks.STONE.defaultBlockState();var list=new java.util.ArrayList<Tile>();
        for(int x=0;x<16;x+=2) for(int y=0;y<16;y+=2) for(int z=0;z<16;z+=2)
            list.add(new Tile(stone,new MicroBox(x,y,z,x+2,y+2,z+2),0xFFFFFF,true));
        check(h,Tiles.compact(list).size()==1,"512 adjacent equal cubes compact to one without altering original");
        check(h,list.size()==512,"Compaction must not mutate editable pieces");
        list.removeFirst();list.set(0,list.getFirst().withColor(0xFF0000));
        var compacted=Tiles.compact(list);
        check(h,compacted.stream().mapToLong(t -> t.box().volume()).sum()==4088 && Tiles.valid(compacted),"Hole and volume must survive compaction");
        check(h,compacted.stream().filter(t -> t.color()==0xFF0000).mapToLong(t -> t.box().volume()).sum()==8,"Color boundaries must survive");h.succeed();
    }
}
