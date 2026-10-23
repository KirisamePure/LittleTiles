package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.SettingsPayload;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class ToolSettingsScreen extends Screen {
    private final ResourceLocation item;
    private final boolean cutting;
    private ToolSettings settings;
    public ToolSettingsScreen(ItemStack stack) {
        super(Component.translatable("screen.littletiles.tools"));
        item=BuiltInRegistries.ITEM.getKey(stack.getItem());settings=ToolSettings.read(stack);
        cutting=stack.is(LittleTilesContent.CHISEL)||stack.is(LittleTilesContent.SAW);
    }
    @Override protected void init() {
        int x=width/2-110,y=Math.max(30,height/2-80);
        number(x,y,220,"screen.littletiles.grid",settings.grid(),v -> settings=new ToolSettings(v,settings.width(),settings.height(),settings.depth(),settings.cutMode(),settings.placeMode()));
        number(x,y+25,70,"screen.littletiles.width",settings.width(),v -> settings=new ToolSettings(settings.grid(),v,settings.height(),settings.depth(),settings.cutMode(),settings.placeMode()));
        number(x+75,y+25,70,"screen.littletiles.height",settings.height(),v -> settings=new ToolSettings(settings.grid(),settings.width(),v,settings.depth(),settings.cutMode(),settings.placeMode()));
        number(x+150,y+25,70,"screen.littletiles.depth",settings.depth(),v -> settings=new ToolSettings(settings.grid(),settings.width(),settings.height(),v,settings.cutMode(),settings.placeMode()));
        var cut=addRenderableWidget(CycleButton.<ToolSettings.CutMode>builder(v -> Component.translatable("cut.littletiles."+v.name().toLowerCase(java.util.Locale.ROOT)))
                .withValues(ToolSettings.CutMode.values()).withInitialValue(settings.cutMode())
                .create(x,y+50,220,20,Component.translatable("screen.littletiles.cut"),(button,v) -> settings=new ToolSettings(settings.grid(),settings.width(),settings.height(),settings.depth(),v,settings.placeMode())));
        cut.active=cutting;
        var place=addRenderableWidget(CycleButton.<ToolSettings.PlaceMode>builder(v -> Component.translatable("place.littletiles."+v.name().toLowerCase(java.util.Locale.ROOT)))
                .withValues(ToolSettings.PlaceMode.values()).withInitialValue(settings.placeMode())
                .create(x,y+75,220,20,Component.translatable("screen.littletiles.place"),(button,v) -> settings=new ToolSettings(settings.grid(),settings.width(),settings.height(),settings.depth(),settings.cutMode(),v)));
        place.active=!cutting;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),button -> {
            if(ClientPlayNetworking.canSend(SettingsPayload.TYPE)) ClientPlayNetworking.send(SettingsPayload.of(item,settings));
            onClose();
        }).bounds(x,y+110,105,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),button -> onClose()).bounds(x+115,y+110,105,20).build());
    }
    private void number(int x,int y,int width,String label,int initial,IntConsumer update) {
        var button=addRenderableWidget(CycleButton.<Integer>builder(v -> Component.literal(v.toString()))
                .withValues(IntStream.rangeClosed(1,16).boxed().toList()).withInitialValue(initial)
                .create(x,y,width,20,Component.translatable(label),(b,v) -> update.accept(v)));
        button.active=cutting;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float delta) {
        super.render(graphics,mouseX,mouseY,delta);
        graphics.drawCenteredString(font,title,width/2,Math.max(8,height/2-106),0xFFFFFFFF);
    }
}
