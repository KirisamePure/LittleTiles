package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.LittleTilesContent;
import com.creativemd.littletiles.fabric.edit.EditHistory;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.MaterialRules;
import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class CuttingTool {
    private CuttingTool() {}
    public static List<Tile> source(ServerPlayer player, BlockPos pos) {
        if (!player.level().hasChunkAt(pos)) return null;
        if (player.level().getBlockEntity(pos) instanceof LittleBlockEntity be) return be.tiles();
        var state = player.level().getBlockState(pos);
        if (state.isAir()) return List.of();
        if (!MaterialRules.canCut(state, player.level(), pos) || (!player.getAbilities().instabuild
                && state.getDestroySpeed(player.level(),pos)<0)) return null;
        return List.of(new Tile(state,new MicroBox(0,0,0,16,16,16),0xFFFFFF,true));
    }
    public static boolean use(UseOnContext context, ServerPlayer player) {
        var settings = ToolSettings.read(context.getItemInHand());
        BlockPos pos = context.getClickedPos();
        if (!EditHistory.allowed(player,pos) || !player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())) return false;
        var source = source(player,pos);
        if (source==null || source.isEmpty()) return false;
        Direction face = context.getClickedFace();
        Vec3 hit = context.getClickLocation().subtract(Vec3.atLowerCornerOf(pos))
                .subtract(new Vec3(face.getStepX(),face.getStepY(),face.getStepZ()).scale(0.00001));
        int x=Math.clamp((int)Math.floor(hit.x*16),0,15), y=Math.clamp((int)Math.floor(hit.y*16),0,15),
                z=Math.clamp((int)Math.floor(hit.z*16),0,15);
        Tile selected=source.stream().filter(t -> t.box().minX()<=x && t.box().maxX()>x && t.box().minY()<=y
                && t.box().maxY()>y && t.box().minZ()<=z && t.box().maxZ()>z).findFirst().orElse(null);
        if (selected==null) return false;
        int gx=x/settings.grid()*settings.grid(), gy=y/settings.grid()*settings.grid(), gz=z/settings.grid()*settings.grid();
        if (settings.cutMode()==ToolSettings.CutMode.REGION) return region(context,player,settings,gx,gy,gz);
        if (settings.cutMode()==ToolSettings.CutMode.SPLIT) {
            var b=selected.box();var axis=face.getAxis();
            int min=axis==Direction.Axis.X?b.minX():axis==Direction.Axis.Y?b.minY():b.minZ();
            int max=axis==Direction.Axis.X?b.maxX():axis==Direction.Axis.Y?b.maxY():b.maxZ();
            int plane=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?max-settings.width():min+settings.width();
            if (plane<=min || plane>=max) return false;
            MicroBox low=new MicroBox(b.minX(),b.minY(),b.minZ(),axis==Direction.Axis.X?plane:b.maxX(),
                    axis==Direction.Axis.Y?plane:b.maxY(),axis==Direction.Axis.Z?plane:b.maxZ());
            var output=new ArrayList<>(source);output.remove(selected);output.add(selected.withBox(low));
            b.subtract(low).forEach(part -> output.add(selected.withBox(part)));
            return EditHistory.replace(player,pos,output,true);
        }
        MicroBox cut=switch(settings.cutMode()) {
            case EXTRACT -> selected.box();
            case BOX -> sizedBox(gx,gy,gz,settings,face);
            case SLICE -> switch(face.getAxis()) {
                case X -> new MicroBox(sliceStart(gx,settings,face),0,0,sliceEnd(gx,settings,face),16,16);
                case Y -> new MicroBox(0,sliceStart(gy,settings,face),0,16,sliceEnd(gy,settings,face),16);
                case Z -> new MicroBox(0,0,sliceStart(gz,settings,face),16,16,sliceEnd(gz,settings,face));
            };
            default -> new MicroBox(gx,gy,gz,Math.min(16,gx+settings.grid()),Math.min(16,gy+settings.grid()),Math.min(16,gz+settings.grid()));
        };
        return cutGroup(context,player,pos,new MicroBox(pos.getX()*16+cut.minX(),pos.getY()*16+cut.minY(),pos.getZ()*16+cut.minZ(),
                pos.getX()*16+cut.maxX(),pos.getY()*16+cut.maxY(),pos.getZ()*16+cut.maxZ()));
    }
    private static MicroBox sizedBox(int x,int y,int z,ToolSettings s,Direction face) {
        int x1=Math.min(16,x+s.width()),y1=Math.min(16,y+s.height()),z1=Math.min(16,z+s.depth());
        if(face.getAxisDirection()==Direction.AxisDirection.POSITIVE) switch(face.getAxis()) {
            case X -> {x1=Math.min(16,x+s.grid());x=Math.max(0,x1-s.width());}
            case Y -> {y1=Math.min(16,y+s.grid());y=Math.max(0,y1-s.height());}
            case Z -> {z1=Math.min(16,z+s.grid());z=Math.max(0,z1-s.depth());}
        }
        return new MicroBox(x,y,z,x1,y1,z1);
    }
    private static int sliceStart(int cell,ToolSettings s,Direction face) {
        return face.getAxisDirection()==Direction.AxisDirection.POSITIVE?Math.max(0,Math.min(16,cell+s.grid())-s.width()):cell;
    }
    private static int sliceEnd(int cell,ToolSettings s,Direction face) {
        return Math.min(16,cell+(face.getAxisDirection()==Direction.AxisDirection.POSITIVE?s.grid():s.width()));
    }
    private static boolean region(UseOnContext context, ServerPlayer player, ToolSettings settings,int x,int y,int z) {
        ItemStack stack=context.getItemInHand();var tag=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        String dimension=player.level().dimension().location().toString();
        int[] start=dimension.equals(tag.getStringOr("cut_dimension",""))?tag.getIntArray("cut_corner").orElse(null):null;
        BlockPos pos=context.getClickedPos();int wx=pos.getX()*16+x, wy=pos.getY()*16+y, wz=pos.getZ()*16+z;
        if (start==null || start.length!=3) {
            CustomData.update(DataComponents.CUSTOM_DATA,stack,t -> {t.putIntArray("cut_corner",new int[]{wx,wy,wz});t.putString("cut_dimension",dimension);});
            player.displayClientMessage(Component.translatable("littletiles.selection.first"),true);return true;
        }
        if(Math.abs((long)wx-start[0])>512 || Math.abs((long)wy-start[1])>512 || Math.abs((long)wz-start[2])>512) return false;
        MicroBox cut=new MicroBox(Math.min(wx,start[0]),Math.min(wy,start[1]),Math.min(wz,start[2]),
                Math.max(wx,start[0])+settings.grid(),Math.max(wy,start[1])+settings.grid(),Math.max(wz,start[2])+settings.grid());
        boolean applied=cutGroup(context,player,pos,cut);
        if (applied) CustomData.update(DataComponents.CUSTOM_DATA,stack,t -> {t.remove("cut_corner");t.remove("cut_dimension");});
        return applied;
    }
    private static boolean cutGroup(UseOnContext context,ServerPlayer player,BlockPos anchor,MicroBox global) {
        int x0=Math.floorDiv(global.minX(),16),y0=Math.floorDiv(global.minY(),16),z0=Math.floorDiv(global.minZ(),16);
        int x1=Math.floorDiv(global.maxX()-1,16),y1=Math.floorDiv(global.maxY()-1,16),z1=Math.floorDiv(global.maxZ()-1,16);
        if(x1-x0>=32 || y1-y0>=32 || z1-z0>=32 || (long)(x1-x0+1)*(y1-y0+1)*(z1-z0+1)>512) return false;
        var changes=new LinkedHashMap<BlockPos,List<Tile>>();var drops=new ArrayList<List<Tile>>();
        for(BlockPos mutable:BlockPos.betweenClosed(x0,y0,z0,x1,y1,z1)) {
            BlockPos pos=mutable.immutable();var tiles=source(player,pos);
            if(tiles==null || !player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand())) return false;
            if(tiles.isEmpty()) continue;
            var block=new MicroBox(pos.getX()*16,pos.getY()*16,pos.getZ()*16,(pos.getX()+1)*16,(pos.getY()+1)*16,(pos.getZ()+1)*16);
            var cut=global.intersection(block).orElseThrow().translate(-pos.getX()*16,-pos.getY()*16,-pos.getZ()*16);
            var remaining=new ArrayList<Tile>();var removed=new ArrayList<Tile>();
            for(Tile tile:tiles) {
                tile.box().intersection(cut).ifPresent(box -> removed.add(tile.withBox(box)));
                tile.box().subtract(cut).forEach(box -> remaining.add(tile.withBox(box)));
            }
            if(!removed.isEmpty()) {changes.put(pos,remaining);drops.add(removed);}
        }
        if(!EditHistory.replaceAll(player,anchor,changes,true)) return false;
        if(!player.getAbilities().instabuild) for(var removed:drops) give(player,removed);
        return true;
    }
    public static void give(ServerPlayer player,List<Tile> tiles) {
        for(int from=0;from<tiles.size();from+=Tiles.MAX_TILES) {
            ItemStack stack=new ItemStack(LittleTilesContent.TILES);
            Tiles.write(stack,tiles.subList(from,Math.min(tiles.size(),from+Tiles.MAX_TILES)));
            if(!player.getInventory().add(stack)) player.drop(stack,false);
        }
    }
}
