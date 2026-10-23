package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.HammerPayload;
import com.creativemd.littletiles.fabric.tile.MaterialRules;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Real synchronized input slot, player inventory and ordinary vanilla click/drag semantics. */
public final class HammerMenu extends AbstractContainerMenu {
    private final SimpleContainer input=new SimpleContainer(1);
    private final Inventory inventory;
    private final ItemStack hammer;
    private final int boundSlot;
    public HammerMenu(int id,Inventory inventory) {
        super(LittleTilesContent.HAMMER_MENU,id);
        this.inventory=inventory;boundSlot=inventory.getSelectedSlot();hammer=inventory.getItem(boundSlot);
        addSlot(new Slot(input,0,18,33) {
            @Override public boolean mayPlace(ItemStack stack) { return accepts(stack); }
        });
        for(int row=0;row<3;row++) for(int col=0;col<9;col++) playerSlot(col+row*9+9,69+col*18,132+row*18);
        for(int col=0;col<9;col++) playerSlot(col,69+col*18,190);
    }
    private void playerSlot(int index,int x,int y) {
        final boolean locked=index==boundSlot;
        addSlot(new Slot(inventory,index,x,y) {
            @Override public boolean mayPickup(Player player) {return !locked;}
            @Override public boolean mayPlace(ItemStack stack) {return !locked;}
        });
    }
    public boolean accepts(ItemStack stack) {
        return HammerPayload.plainBlock(stack) && MaterialRules.canCut(((BlockItem)stack.getItem()).getBlock().defaultBlockState(),
                inventory.player.level(),inventory.player.blockPosition());
    }
    public ItemStack input() {return input.getItem(0);}
    public void inputChanged() {input.setChanged();broadcastChanges();}
    @Override public boolean stillValid(Player player) {
        if(player.isSpectator() || inventory.getSelectedSlot()!=boundSlot) return false;
        // Slot synchronization replaces client stack objects after dimensions are
        // saved. Only the server uses identity to bind this menu to its tool.
        if(player.level().isClientSide()) return player.getMainHandItem().is(LittleTilesContent.HAMMER);
        return hammer.is(LittleTilesContent.HAMMER) && inventory.getItem(boundSlot)==hammer
                && player.getMainHandItem()==hammer;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(!stillValid(player) || (type==ClickType.SWAP && button==boundSlot)) return;
        super.clicked(slot,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(!stillValid(player) || index<0 || index>=slots.size()) return ItemStack.EMPTY;
        Slot slot=slots.get(index);ItemStack stack=slot.getItem();
        if(stack.isEmpty() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack original=stack.copy();
        if(index==0) {if(!moveItemStackTo(stack,1,slots.size(),true)) return ItemStack.EMPTY;}
        else if(!accepts(stack) || !moveItemStackTo(stack,0,1,false)) return ItemStack.EMPTY;
        if(stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return original;
    }
    @Override public void removed(Player player) {
        super.removed(player);
        if(!player.level().isClientSide()) clearContainer(player,input);
    }
    public static boolean open(ServerPlayer player) {
        if(!player.getMainHandItem().is(LittleTilesContent.HAMMER) || player.isSpectator()) return false;
        player.openMenu(new SimpleMenuProvider((id,inventory,owner) -> new HammerMenu(id,inventory),
                Component.translatable("hammer.littletiles.title")));return true;
    }
}
