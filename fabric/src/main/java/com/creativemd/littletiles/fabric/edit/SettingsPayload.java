package com.creativemd.littletiles.fabric.edit;

import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.item.ToolSettings;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record SettingsPayload(ResourceLocation item, int grid, int width, int height, int depth, int cut, int place)
        implements CustomPacketPayload {
    public static final Type<SettingsPayload> TYPE = new Type<>(LittleTilesContent.id("tool_settings"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SettingsPayload> CODEC = new StreamCodec<>() {
        @Override public SettingsPayload decode(RegistryFriendlyByteBuf b) {
            return new SettingsPayload(b.readResourceLocation(),b.readVarInt(),b.readVarInt(),b.readVarInt(),
                    b.readVarInt(),b.readVarInt(),b.readVarInt());
        }
        @Override public void encode(RegistryFriendlyByteBuf b, SettingsPayload s) {
            b.writeResourceLocation(s.item);b.writeVarInt(s.grid);b.writeVarInt(s.width);b.writeVarInt(s.height);
            b.writeVarInt(s.depth);b.writeVarInt(s.cut);b.writeVarInt(s.place);
        }
    };
    @Override public Type<SettingsPayload> type() { return TYPE; }
    public static SettingsPayload of(ResourceLocation item, ToolSettings s) {
        return new SettingsPayload(item,s.grid(),s.width(),s.height(),s.depth(),s.cutMode().ordinal(),s.placeMode().ordinal());
    }
    public boolean apply(ServerPlayer player) {
        var stack = player.getMainHandItem();
        if (!ToolSettings.configurable(stack) || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item)
                || cut<0 || cut>=ToolSettings.CutMode.values().length || place<0 || place>=ToolSettings.PlaceMode.values().length) return false;
        var settings = new ToolSettings(grid,width,height,depth,ToolSettings.CutMode.values()[cut],ToolSettings.PlaceMode.values()[place]);
        if (!settings.valid()) return false;
        settings.write(stack);player.getInventory().setChanged();return true;
    }
    public static void initialize() {
        PayloadTypeRegistry.playC2S().register(TYPE,CODEC);
        ServerPlayNetworking.registerGlobalReceiver(TYPE,(payload,context) -> context.server().execute(() -> payload.apply(context.player())));
    }
}
