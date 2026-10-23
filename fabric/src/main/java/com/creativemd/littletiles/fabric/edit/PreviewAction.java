package com.creativemd.littletiles.fabric.edit;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.item.Blueprint;
import com.creativemd.littletiles.fabric.tile.Tiles;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Clients request an operation, never supply authoritative tile data. */
public record PreviewAction(boolean mirror, int axis) implements CustomPacketPayload {
    public static final Type<PreviewAction> TYPE = new Type<>(LittleTilesContent.id("preview_transform"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PreviewAction> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, PreviewAction::mirror, ByteBufCodecs.VAR_INT, PreviewAction::axis, PreviewAction::new);
    @Override public Type<PreviewAction> type() { return TYPE; }

    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(TYPE, CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, context) ->
                context.server().execute(() -> apply(context.player(), payload.mirror, payload.axis)));
    }

    public static boolean apply(ServerPlayer player, boolean mirror, int index) {
        if (index < 0 || index >= MicroBox.Axis.values().length) return false;
        MicroBox.Axis axis = MicroBox.Axis.values()[index];
        var stack = player.getMainHandItem();
        if (stack.is(LittleTilesContent.BLUEPRINT)) {
            var old = Blueprint.read(stack);
            if (old.isEmpty()) return false;
            Blueprint changed = old.get().transform(axis, mirror);
            if (!changed.valid()) return false;
            changed.write(stack);
        } else if (stack.is(LittleTilesContent.TILES)) {
            var tiles = Tiles.read(stack);
            if (tiles.isEmpty()) return false;
            Tiles.write(stack, Tiles.transform(tiles.get(), axis, mirror));
        } else return false;
        player.getInventory().setChanged();
        return true;
    }
}
