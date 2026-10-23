package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.item.*;
import com.creativemd.littletiles.fabric.tile.*;
import java.util.List;
import java.util.ArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;

/** Immutable extraction snapshots. Drawing never touches world, inventory or hit-test state. */
public final class PlacementPreview {
    public record Snapshot(List<AABB> boxes,boolean valid,boolean removal) {
        public Snapshot { boxes=List.copyOf(boxes); }
    }
    private static Snapshot snapshot=new Snapshot(List.of(),false,false);
    public static Snapshot current() {return snapshot;}
    private static net.minecraft.world.item.Item decodedItem;
    private static net.minecraft.world.item.component.CustomData decodedData;
    private static List<Tile> decodedTiles=List.of();
    private static Blueprint decodedBlueprint;
    private PlacementPreview() {}
    public static void initialize() {
        WorldRenderEvents.END_EXTRACTION.register(context -> snapshot=extract(Minecraft.getInstance()));
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(context -> {
            var state=snapshot;
            if(state.boxes().isEmpty()) return;
            var camera=context.worldState().cameraRenderState.pos;
            var poses=context.matrices();poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
            var consumer=context.consumers().getBuffer(RenderType.lines());
            float r=state.valid()?(state.removal()?1:0.2F):1;
            float g=state.valid()?(state.removal()?0.65F:1):0.15F;
            for(var box:state.boxes()) ShapeRenderer.renderLineBox(poses.last(),consumer,box.inflate(0.002),r,g,0.25F,0.9F);
            var fill=context.consumers().getBuffer(RenderType.debugFilledBox());
            for(var box:state.boxes()) ShapeRenderer.addChainedFilledBoxVertices(poses,fill,
                    box.minX,box.minY,box.minZ,box.maxX,box.maxY,box.maxZ,r,g,0.25F,0.15F);
            poses.popPose();
        });
    }
    public static Snapshot extract(Minecraft client) {
        if(client.player==null || client.level==null || client.screen!=null || client.options.hideGui
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType()!=HitResult.Type.BLOCK)
            return new Snapshot(List.of(),false,false);
        var stack=client.player.getMainHandItem();var mode=ToolSettings.read(stack).placeMode();
        var data=stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if(decodedItem!=stack.getItem() || decodedData!=data) {
            decodedItem=stack.getItem();decodedData=data;
            decodedTiles=stack.is(LittleTilesContent.TILES)?Tiles.read(stack).orElse(List.of()):List.of();
            decodedBlueprint=stack.is(LittleTilesContent.BLUEPRINT)?Blueprint.read(stack).orElse(null):null;
        }
        boolean removal=mode==ToolSettings.PlaceMode.STENCIL;
        var parts=new ArrayList<TileItem.Target>();
        if(stack.is(LittleTilesContent.TILES)) {
            var tiles=decodedTiles;if(tiles.isEmpty()) return new Snapshot(List.of(),false,false);
            parts.add(TileItem.locate(client.level,hit.getBlockPos(),hit.getLocation(),hit.getDirection(),tiles,mode));
        } else if(stack.is(LittleTilesContent.BLUEPRINT) && client.player.isShiftKeyDown()) {
            var bp=decodedBlueprint;if(bp==null) return new Snapshot(List.of(),false,false);
            if(bp.parts().size()==1 && bp.parts().getFirst().offset().equals(BlockPos.ZERO))
                parts.add(TileItem.locate(client.level,hit.getBlockPos(),hit.getLocation(),hit.getDirection(),bp.parts().getFirst().tiles(),mode));
            else {
                BlockPos anchor=mode==ToolSettings.PlaceMode.OVERWRITE||removal?hit.getBlockPos():hit.getBlockPos().relative(hit.getDirection());
                for(var part:bp.parts()) parts.add(new TileItem.Target(anchor.offset(part.offset()),part.tiles()));
            }
        } else return new Snapshot(List.of(),false,false);
        boolean valid=true,changed=false;var boxes=new ArrayList<AABB>();
        for(var part:parts) {
            BlockPos pos=part.pos();
            if(part.tiles().isEmpty()) {valid=false;continue;}
            if(!client.level.hasChunkAt(pos) || !client.level.getWorldBorder().isWithinBounds(pos)
                    || pos.getY()<client.level.getMinY() || pos.getY()>=client.level.getMaxY()) {valid=false;continue;}
            List<Tile> existing;
            if(client.level.getBlockEntity(pos) instanceof LittleBlockEntity be) existing=be.tiles();
            else {
                var state=client.level.getBlockState(pos);
                if(state.isAir()) existing=List.of();
                else if(MaterialRules.canCut(state,client.level,pos)) existing=List.of(new Tile(state,new MicroBox(0,0,0,16,16,16),0xFFFFFF,true));
                else {existing=List.of();valid=false;}
            }
            var plan=PlacementMath.plan(existing,part.tiles(),mode);
            List<Tile> visible=part.tiles();
            if(plan.isPresent()) {
                var p=plan.get();visible=removal?p.removed():p.added();changed|=!visible.isEmpty();
                if(!p.added().isEmpty() && !client.level.isUnobstructed(null,Tiles.shape(p.added(),true).move(pos.getX(),pos.getY(),pos.getZ()))) valid=false;
            } else if(mode!=ToolSettings.PlaceMode.FILL && !removal) valid=false;
            for(var tile:visible) {
                var box=tile.box();boxes.add(new AABB(pos.getX()+box.minX()/16.0,pos.getY()+box.minY()/16.0,pos.getZ()+box.minZ()/16.0,
                        pos.getX()+box.maxX()/16.0,pos.getY()+box.maxY()/16.0,pos.getZ()+box.maxZ()/16.0));
            }
        }
        return new Snapshot(boxes,valid && changed,removal);
    }
}
