package com.creativemd.littletiles.fabric.edit;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.item.MaterialPayment;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import com.creativemd.littletiles.fabric.tile.MaterialRules;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.List;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Bound to the active server menu: clients cannot manufacture from arbitrary inventory slots. */
public record HammerPayload(int menuId, ResourceLocation expectedItem, int width, int height, int depth, int count)
        implements CustomPacketPayload {
    public static final Type<HammerPayload> TYPE = new Type<>(LittleTilesContent.id("hammer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,HammerPayload> CODEC = new StreamCodec<>() {
        public HammerPayload decode(RegistryFriendlyByteBuf b) {
            return new HammerPayload(b.readVarInt(), b.readResourceLocation(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt());
        }
        public void encode(RegistryFriendlyByteBuf b,HammerPayload p) {
            b.writeVarInt(p.menuId);b.writeResourceLocation(p.expectedItem);b.writeVarInt(p.width);b.writeVarInt(p.height);b.writeVarInt(p.depth);b.writeVarInt(p.count);
        }
    };
    @Override public Type<HammerPayload> type() { return TYPE; }
    public static boolean plainBlock(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof BlockItem
                && !stack.has(DataComponents.BLOCK_ENTITY_DATA) && !stack.has(DataComponents.BLOCK_STATE)
                && !stack.has(DataComponents.CUSTOM_DATA);
    }
    public boolean apply(ServerPlayer player) {
        if (!player.getMainHandItem().is(LittleTilesContent.HAMMER) || player.isSpectator()
                || !(player.containerMenu instanceof com.creativemd.littletiles.fabric.item.HammerMenu menu)
                || menu.containerId!=menuId || !menu.stillValid(player) || width<1 || width>16 || height<1 || height>16
                || depth<1 || depth>16 || count<1 || count>64) return false;
        ItemStack input=menu.input();
        if (!plainBlock(input) || !BuiltInRegistries.ITEM.getKey(input.getItem()).equals(expectedItem)) return false;
        var state=((BlockItem)input.getItem()).getBlock().defaultBlockState();
        if (!MaterialRules.canCut(state,player.level(),player.blockPosition())) return false;
        int volume=width*height*depth*count;
        int cost=(volume+4095)/4096;
        if (!player.getAbilities().instabuild && input.getCount()<cost) return false;
        ItemStack output=new ItemStack(LittleTilesContent.TILES,count);
        Tiles.write(output,List.of(new Tile(state,new MicroBox(0,0,0,width,height,depth),0xFFFFFF,true)));
        if (!player.getAbilities().instabuild) input.shrink(cost);
        var old=ToolSettings.read(player.getMainHandItem());
        new ToolSettings(old.grid(),width,height,depth,old.cutMode(),old.placeMode()).write(player.getMainHandItem());
        if (!player.getInventory().add(output)) player.drop(output,false);
        if (!player.getAbilities().instabuild) for (ItemStack refund:MaterialPayment.refund(state.getBlock(),cost*4096-volume))
            if (!player.getInventory().add(refund)) player.drop(refund,false);
        player.getInventory().setChanged();menu.inputChanged();player.inventoryMenu.broadcastChanges();
        return true;
    }
    public static void initialize() {
        OpenHammerPayload.initialize();
        PayloadTypeRegistry.playC2S().register(TYPE,CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(p,c) -> c.server().execute(() -> {
            if (!p.apply(c.player())) c.player().displayClientMessage(net.minecraft.network.chat.Component.translatable("hammer.littletiles.failed"),true);
        }));
    }
}
