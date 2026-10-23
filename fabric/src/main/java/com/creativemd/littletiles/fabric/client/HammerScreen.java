package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.HammerPayload;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/** Uses the real inventory as input: nothing can become stranded in a temporary GUI slot. */
public final class HammerScreen extends Screen {
    private int sx,sy,sz,count=1,slot=-1;
    private Button material,craft;
    private List<Integer> slots=List.of();
    public HammerScreen(ItemStack hammer) {
        super(Component.translatable("hammer.littletiles.title"));
        var s=ToolSettings.read(hammer);sx=s.width();sy=s.height();sz=s.depth();
    }
    @Override protected void init() {
        int x=width/2-145,y=height/2-105;
        material=addRenderableWidget(Button.builder(Component.empty(),b -> {
            refresh();if (!slots.isEmpty()) slot=slots.get((slots.indexOf(slot)+1)%slots.size());refresh();
        }).bounds(x,y+20,290,20).build());
        dimension(x,y+48,"X",sx,v -> sx=v);dimension(x+98,y+48,"Y",sy,v -> sy=v);dimension(x+196,y+48,"Z",sz,v -> sz=v);
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v.toString()))
                .withValues(IntStream.rangeClosed(1,64).boxed().toList()).withInitialValue(count)
                .create(x,y+76,140,20,Component.translatable("hammer.littletiles.count"),(b,v) -> {count=v;refresh();}));
        craft=addRenderableWidget(Button.builder(Component.translatable("hammer.littletiles.craft"),b -> {
            if (slot>=0 && ClientPlayNetworking.canSend(HammerPayload.TYPE)) {
                var stack=minecraft.player.getInventory().getItem(slot);
                ClientPlayNetworking.send(new HammerPayload(slot,BuiltInRegistries.ITEM.getKey(stack.getItem()),sx,sy,sz,count));
            }
        }).bounds(x,y+165,140,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b -> onClose()).bounds(x+150,y+165,140,20).build());
        refresh();
    }
    private void dimension(int x,int y,String label,int initial,java.util.function.IntConsumer update) {
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v+" / 16"))
                .withValues(IntStream.rangeClosed(1,16).boxed().toList()).withInitialValue(initial)
                .create(x,y,94,20,Component.literal(label),(b,v) -> {update.accept(v);refresh();}));
    }
    private void refresh() {
        if (minecraft==null || minecraft.player==null || material==null) return;
        var found=new ArrayList<Integer>();
        for(int i=0;i<=40;i++) if ((i<36 || i==40) && HammerPayload.plainBlock(minecraft.player.getInventory().getItem(i))) {
            var state=((BlockItem)minecraft.player.getInventory().getItem(i).getItem()).getBlock().defaultBlockState();
            if(com.creativemd.littletiles.fabric.tile.MaterialRules.canCut(state,minecraft.level,minecraft.player.blockPosition())) found.add(i);
        }
        slots=List.copyOf(found);
        if(!slots.contains(slot)) slot=slots.isEmpty()?-1:slots.getFirst();
        var input=slot<0?ItemStack.EMPTY:minecraft.player.getInventory().getItem(slot);
        material.setMessage(slot<0?Component.translatable("hammer.littletiles.no_material"):
                Component.translatable("hammer.littletiles.material",input.getHoverName(),input.getCount()));
        if(craft!=null) craft.active=slot>=0 && (minecraft.player.getAbilities().instabuild || input.getCount()>=cost());
    }
    private int cost() { return (sx*sy*sz*count+4095)/4096; }
    @Override public void tick() { refresh();if(minecraft.player==null || !minecraft.player.getMainHandItem().is(LittleTilesContent.HAMMER)) onClose(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        super.render(g,mouseX,mouseY,delta);
        int x=width/2-145,y=height/2-105;
        g.drawCenteredString(font,title,width/2,y+4,0xFFFFFFFF);
        g.drawString(font,Component.translatable("hammer.littletiles.cost",cost()),x,y+104,0xFFFFFFFF);
        g.drawString(font,Component.translatable("hammer.littletiles.refund",cost()*4096-sx*sy*sz*count),x,y+118,0xFFBBBBBB);
        int px=x+172,py=y+80;
        g.fill(px,py,px+49,py+49,0xCC161D25);
        for(int i=0;i<=16;i++) {
            g.fill(px+i*3,py,px+i*3+1,py+49,0xFF384351);
            g.fill(px,py+i*3,px+49,py+i*3+1,0xFF384351);
        }
        g.fill(px,py+48-sy*3,px+sx*3,py+48,0xCC65D9AD);
        g.drawString(font,Component.literal("XY"),px+54,py,0xFFBBBBBB);
        g.drawString(font,Component.translatable("hammer.littletiles.hint"),x,y+140,0xFFBBBBBB);
        if(slot>=0) {
            var input=minecraft.player.getInventory().getItem(slot);
            if(input.getItem() instanceof BlockItem) {
                g.renderItem(input,x+250,y+100);
            }
        }
    }
}
