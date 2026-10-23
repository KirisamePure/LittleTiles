package com.creativemd.littletiles.fabric;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.item.LittleToolItem;
import com.creativemd.littletiles.fabric.item.TileItem;
import com.creativemd.littletiles.fabric.item.BagItem;
import com.creativemd.littletiles.fabric.tile.LittleBlock;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class LittleTilesContent {
    private static final List<Item> TOOLS = new ArrayList<>();
    public static final LittleBlock TILE_BLOCK = Registry.register(BuiltInRegistries.BLOCK, id("tiles"),
            new LittleBlock(BlockBehaviour.Properties.of().setId(ResourceKey.create(Registries.BLOCK, id("tiles")))
                    .strength(1.5F).dynamicShape().lightLevel(state -> state.getValue(LittleBlock.LIGHT))));
    public static final BlockEntityType<LittleBlockEntity> TILE_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
            id("tiles"), FabricBlockEntityTypeBuilder.create(LittleBlockEntity::new, TILE_BLOCK).build());
    public static final Item TILES = register("tiles", p -> new TileItem(p.stacksTo(64)));
    public static final Item HAMMER = tool("hammer", LittleToolItem.Tool.HAMMER);
    public static final Item SAW = tool("saw", LittleToolItem.Tool.SAW);
    public static final Item WRENCH = tool("wrench", LittleToolItem.Tool.WRENCH);
    public static final Item CHISEL = tool("chisel", LittleToolItem.Tool.CHISEL);
    public static final Item COLOR_TUBE = tool("color_tube", LittleToolItem.Tool.COLOR);
    public static final Item COLLISION_TOOL = tool("collision_tool", LittleToolItem.Tool.COLLISION);
    public static final Item MALLET = tool("rubber_mallet", LittleToolItem.Tool.MALLET);
    public static final Item BLUEPRINT = tool("blueprint", LittleToolItem.Tool.BLUEPRINT);
    public static final Item BAG = register("little_bag", p -> new BagItem(p.stacksTo(1)));
    public static final Item CONTAINER = register("container", p -> new BagItem(p.stacksTo(1)));

    private LittleTilesContent() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("littletiles", path); }
    private static Item register(String name, Function<Item.Properties, Item> factory) {
        return Registry.register(BuiltInRegistries.ITEM, id(name),
                factory.apply(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id(name)))));
    }
    private static Item tool(String name, LittleToolItem.Tool type) {
        Item item = register(name, p -> new LittleToolItem(p.stacksTo(1), type));
        TOOLS.add(item);
        return item;
    }
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(entries -> {
            TOOLS.forEach(entries::accept);
            entries.accept(BAG);
            entries.accept(CONTAINER);
            ItemStack tile = new ItemStack(TILES);
            Tiles.write(tile, List.of(new Tile(Blocks.STONE.defaultBlockState(), new MicroBox(0, 0, 0, 1, 1, 1), 0xFFFFFF, true)));
            entries.accept(tile);
        });
    }
}
