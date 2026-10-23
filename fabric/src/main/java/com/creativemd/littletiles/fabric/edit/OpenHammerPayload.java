package com.creativemd.littletiles.fabric.edit;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.item.HammerMenu;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record OpenHammerPayload() implements CustomPacketPayload {
    public static final Type<OpenHammerPayload> TYPE=new Type<>(LittleTilesContent.id("open_hammer"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OpenHammerPayload> CODEC=StreamCodec.unit(new OpenHammerPayload());
    @Override public Type<OpenHammerPayload> type() {return TYPE;}
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(TYPE,CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(p,c) -> c.server().execute(() -> HammerMenu.open(c.player())));
    }
}
