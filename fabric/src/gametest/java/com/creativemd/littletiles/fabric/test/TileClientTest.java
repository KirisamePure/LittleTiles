package com.creativemd.littletiles.fabric.test;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.client.TileItemRenderer;
import com.creativemd.littletiles.fabric.item.BagItem;
import com.creativemd.littletiles.fabric.edit.PreviewAction;
import com.creativemd.littletiles.fabric.client.ToolSettingsScreen;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public final class TileClientTest implements FabricClientGameTest {
    private static final BlockPos POS = new BlockPos(0, 100, 0);
    @Override public void runTest(ClientGameTestContext context) {
        List<Tile> tiles = List.of(new Tile(Blocks.STONE.defaultBlockState(),
                new MicroBox(0, 0, 0, 8, 16, 16), 0xFFFFFF, true),
                new Tile(Blocks.GLASS.defaultBlockState(), new MicroBox(8, 0, 0, 16, 8, 16), 0x44AAFF, false));
        try (var world = context.worldBuilder().create()) {
            world.getServer().runOnServer(server -> {
                for (BlockPos p : BlockPos.betweenClosed(-3, 99, -3, 3, 99, 5))
                    server.overworld().setBlockAndUpdate(p, Blocks.SMOOTH_QUARTZ.defaultBlockState());
                server.overworld().setBlockAndUpdate(POS, LittleTilesContent.TILE_BLOCK.defaultBlockState());
                ((LittleBlockEntity) server.overworld().getBlockEntity(POS)).replace(tiles);
                ItemStack item = new ItemStack(LittleTilesContent.TILES);
                Tiles.write(item, tiles);
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setItem(0, item);
                ItemStack bag = new ItemStack(LittleTilesContent.BAG);
                BagItem.contents(bag).setItem(0, new ItemStack(Items.STONE, 3));
                player.getInventory().setItem(1, bag);
            });
            world.getServer().runCommand("tp @a 0.5 100 4.5 180 10");
            context.waitFor(client -> client.level != null && client.level.getBlockEntity(POS) instanceof LittleBlockEntity be
                    && be.tiles().equals(tiles));
            context.waitFor(client -> client.player != null && client.player.getInventory().getItem(0).is(LittleTilesContent.TILES));
            context.runOnClient(client -> {
                ItemStack stack = client.player.getInventory().getItem(0);
                if (new TileItemRenderer().extractArgument(stack).outputQuads() <= 0)
                    throw new AssertionError("Item renderer must extract synchronized tile geometry");
            });
            context.waitTicks(20);
            context.takeScreenshot("littletiles-microtiles");
            RenderChecks.verify(context,world,POS,tiles);
            List<Tile> colored = List.of(tiles.getFirst().withColor(0xFF6655), tiles.getLast().withColor(0x55FFAA));
            world.getServer().runOnServer(server -> ((LittleBlockEntity) server.overworld().getBlockEntity(POS)).replace(colored));
            context.waitFor(client -> client.level.getBlockEntity(POS) instanceof LittleBlockEntity be && be.tiles().equals(colored));
            context.waitTicks(5);
            context.takeScreenshot("littletiles-tinted-update");
            context.runOnClient(client -> ClientPlayNetworking.send(new PreviewAction(false, MicroBox.Axis.Y.ordinal())));
            List<Tile> rotated = Tiles.transform(tiles, MicroBox.Axis.Y, false);
            context.waitFor(client -> Tiles.read(client.player.getMainHandItem()).map(t -> t.equals(rotated)).orElse(false));
            context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
            context.waitTicks(5);
            context.takeScreenshot("littletiles-inventory");
            context.setScreen(() -> null);
            world.getServer().runOnServer(server -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.getInventory().setSelectedSlot(1);
                LittleTilesContent.BAG.use(server.overworld(), player, InteractionHand.MAIN_HAND);
            });
            context.waitForScreen(ContainerScreen.class);
            context.runOnClient(client -> {
                if (client.player.containerMenu.getSlot(0).getItem().getCount() != 3)
                    throw new AssertionError("Portable bag contents must synchronize to the client");
            });
            context.takeScreenshot("littletiles-bag");
            context.runOnClient(client -> {
                client.player.closeContainer();client.player.getInventory().setSelectedSlot(0);
                client.player.connection.send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0));
            });
            world.getServer().runOnServer(server -> {
                var player=server.getPlayerList().getPlayers().getFirst();
                player.closeContainer();
                player.getInventory().setSelectedSlot(0);player.getInventory().setItem(0,new ItemStack(LittleTilesContent.CHISEL));
                player.inventoryMenu.broadcastFullState();
            });
            context.waitFor(client -> client.player.getMainHandItem().is(LittleTilesContent.CHISEL));
            var desired=new ToolSettings(2,3,2,4,ToolSettings.CutMode.BOX,ToolSettings.PlaceMode.NORMAL);
            context.runOnClient(client -> {
                var edited=client.player.getMainHandItem().copy();desired.write(edited);
                client.setScreen(new ToolSettingsScreen(edited));
            });
            context.waitForScreen(ToolSettingsScreen.class);
            context.takeScreenshot("littletiles-tool-settings");
            context.clickScreenButton("gui.done");
            context.waitFor(client -> ToolSettings.read(client.player.getMainHandItem()).equals(desired));
        }
    }
}
