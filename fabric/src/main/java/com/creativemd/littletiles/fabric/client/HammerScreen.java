package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.fabric.edit.HammerPayload;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import com.creativemd.littletiles.fabric.item.HammerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

/** Standard container screen: vanilla handles pickup, drag, shift-click and inventory synchronization. */
public final class HammerScreen extends AbstractContainerScreen<HammerMenu> {
    private int sx,sy,sz,count=1;
    private Button craft;
    public HammerScreen(HammerMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);
        var s=ToolSettings.read(inventory.player.getMainHandItem());sx=s.width();sy=s.height();sz=s.depth();
        imageWidth=300;imageHeight=222;inventoryLabelX=69;inventoryLabelY=120;
    }
    @Override protected void init() {
        super.init();
        int x=leftPos,y=topPos;
        dimension(x+60,y+22,"X",sx,v -> sx=v);dimension(x+138,y+22,"Y",sy,v -> sy=v);dimension(x+216,y+22,"Z",sz,v -> sz=v);
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v.toString()))
                .withValues(IntStream.rangeClosed(1,64).boxed().toList()).withInitialValue(count)
                .create(x+60,y+48,90,20,Component.translatable("hammer.littletiles.count"),(b,v) -> {count=v;refresh();}));
        craft=addRenderableWidget(Button.builder(Component.translatable("hammer.littletiles.craft"),b -> {
            if (!menu.input().isEmpty() && ClientPlayNetworking.canSend(HammerPayload.TYPE)) {
                var stack=menu.input();
                ClientPlayNetworking.send(new HammerPayload(menu.containerId,BuiltInRegistries.ITEM.getKey(stack.getItem()),sx,sy,sz,count));
            }
        }).bounds(x+60,y+76,90,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b -> onClose()).bounds(x+240,y+76,52,20).build());
        refresh();
    }
    private void dimension(int x,int y,String label,int initial,java.util.function.IntConsumer update) {
        addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v+" / 16"))
                .withValues(IntStream.rangeClosed(1,16).boxed().toList()).withInitialValue(initial)
                .create(x,y,76,20,Component.literal(label),(b,v) -> {update.accept(v);refresh();}));
    }
    private void refresh() {
        if(minecraft==null || minecraft.player==null || craft==null) return;
        var input=menu.input();
        craft.active=menu.accepts(input) && (minecraft.player.getAbilities().instabuild || input.getCount()>=cost());
    }
    private int cost() { return (sx*sy*sz*count+4095)/4096; }
    @Override protected void containerTick() { super.containerTick();refresh(); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float delta) {
        super.render(g,mouseX,mouseY,delta);renderTooltip(g,mouseX,mouseY);
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mouseX,int mouseY) {
        int x=leftPos,y=topPos;
        g.fill(x-2,y-2,x+imageWidth+2,y+imageHeight+2,0xFF10151C);
        g.fill(x,y,x+imageWidth,y+imageHeight,0xFF252E39);
        for(var slot:menu.slots) {
            g.fill(x+slot.x-1,y+slot.y-1,x+slot.x+17,y+slot.y+17,0xFF0E131B);
            g.fill(x+slot.x,y+slot.y,x+slot.x+16,y+slot.y+16,0xFF596473);
        }
        int px=x+174,py=y+50;
        g.fill(px,py,px+49,py+49,0xFF161D25);
        for(int i=0;i<=16;i++) {
            g.fill(px+i*3,py,px+i*3+1,py+49,0xFF384351);
            g.fill(px,py+i*3,px+49,py+i*3+1,0xFF384351);
        }
        g.fill(px,py+48-sy*3,px+sx*3,py+48,0xCC65D9AD);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        g.drawString(font,title,8,6,0xFFFFFFFF);
        g.drawString(font,Component.translatable("hammer.littletiles.input"),8,21,0xFFBBBBBB);
        g.drawString(font,Component.literal("XY"),226,51,0xFFBBBBBB);
        g.drawString(font,Component.translatable("hammer.littletiles.cost",cost()),8,106,0xFFFFFFFF);
        g.drawString(font,Component.translatable("hammer.littletiles.refund",cost()*4096-sx*sy*sz*count),154,106,0xFFBBBBBB);
        g.drawString(font,playerInventoryTitle,inventoryLabelX,inventoryLabelY,0xFFBBBBBB);
    }
}
