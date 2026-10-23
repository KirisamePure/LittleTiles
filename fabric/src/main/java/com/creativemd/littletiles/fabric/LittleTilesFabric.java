package com.creativemd.littletiles.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.creativemd.littletiles.fabric.edit.EditHistory;
import com.creativemd.littletiles.fabric.edit.PreviewAction;
import com.creativemd.littletiles.fabric.edit.SettingsPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Fabric lifecycle and server-only edit commands. */
public final class LittleTilesFabric implements ModInitializer {
    public static final String MOD_ID = "littletiles";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LittleTilesContent.initialize();
        PreviewAction.initialize();
        SettingsPayload.initialize();
        com.creativemd.littletiles.fabric.edit.HammerPayload.initialize();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EditHistory.clear());
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> EditHistory.clear(handler.player.getUUID()));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                Commands.literal("littletiles")
                        .then(Commands.literal("undo").executes(ctx -> {
                            boolean done = EditHistory.undo(ctx.getSource().getPlayerOrException());
                            if (!done) ctx.getSource().sendFailure(Component.translatable("littletiles.history.undo_failed"));
                            return done ? 1 : 0;
                        }))
                        .then(Commands.literal("redo").executes(ctx -> {
                            boolean done = EditHistory.redo(ctx.getSource().getPlayerOrException());
                            if (!done) ctx.getSource().sendFailure(Component.translatable("littletiles.history.redo_failed"));
                            return done ? 1 : 0;
                        }))));
        LOGGER.info("LittleTiles Fabric development build: micro-tile editing registered; full feature migration is in progress.");
    }
}
