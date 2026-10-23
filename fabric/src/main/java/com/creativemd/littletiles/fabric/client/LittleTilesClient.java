package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import com.creativemd.littletiles.fabric.edit.PreviewAction;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

public final class LittleTilesClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        BlockEntityRenderers.register(LittleTilesContent.TILE_ENTITY, LittleTilesRenderer::new);
        SpecialModelRenderers.ID_MAPPER.put(LittleTilesContent.id("tiles"), TileItemRenderer.Unbaked.CODEC);
        PlacementPreview.initialize();
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,level,hand) -> {
            if(level.isClientSide() && hand==net.minecraft.world.InteractionHand.MAIN_HAND
                    && player.getMainHandItem().is(LittleTilesContent.HAMMER) && !player.isShiftKeyDown()) {
                net.minecraft.client.Minecraft.getInstance().setScreen(new HammerScreen(player.getMainHandItem()));
                return net.minecraft.world.InteractionResult.SUCCESS;
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        var category = KeyMapping.Category.register(LittleTilesContent.id("tools"));
        var rotate = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.littletiles.rotate", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_R, category));
        var mirror = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.littletiles.mirror", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F, category));
        var configure = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.littletiles.configure", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_G, category));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (rotate.consumeClick()) send(client, false);
            while (mirror.consumeClick()) send(client, true);
            while (configure.consumeClick()) if(client.player!=null && client.screen==null
                    && ToolSettings.configurable(client.player.getMainHandItem()))
                client.setScreen(client.player.getMainHandItem().is(LittleTilesContent.HAMMER)
                        ? new HammerScreen(client.player.getMainHandItem()) : new ToolSettingsScreen(client.player.getMainHandItem()));
        });
    }

    private static void send(net.minecraft.client.Minecraft client, boolean mirror) {
        if (client.player == null || client.screen != null || !ClientPlayNetworking.canSend(PreviewAction.TYPE)) return;
        int axis = Direction.getApproximateNearest(client.player.getLookAngle()).getAxis().ordinal();
        ClientPlayNetworking.send(new PreviewAction(mirror, axis));
    }
}
