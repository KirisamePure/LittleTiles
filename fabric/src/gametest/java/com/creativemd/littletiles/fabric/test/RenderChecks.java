package com.creativemd.littletiles.fabric.test;

import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.fabric.client.LittleTilesRenderer;
import com.creativemd.littletiles.fabric.client.MaterialMeshBuilder;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

public final class RenderChecks {
    private RenderChecks() {}
    private static void check(boolean value,String reason) { if(!value) throw new AssertionError(reason); }
    public static void verify(ClientGameTestContext context,TestSingleplayerContext world,BlockPos pos,List<Tile> original) {
        context.runOnClient(client -> {
            var builder=new MaterialMeshBuilder();var stone=Blocks.STONE.defaultBlockState();
            var prepared=Map.of(stone,builder.prepare(stone,client.level,pos,false));
            var slabs=new ArrayList<Tile>();
            for(int x=0;x<16;x+=2) slabs.add(new Tile(stone,new MicroBox(x,0,0,x+2,16,16),0xFFFFFF,true));
            var mesh=builder.build(slabs,prepared,List.of(),client.level,false);
            check(mesh.sourceQuads()==48 && mesh.outputQuads()==34,"Eight slabs: cull 14 internal faces (48 -> 34)");
            check(mesh.batches().size()==1,"Same render layer should be submitted in one batch, not eight");
            var dense=new ArrayList<Tile>();
            for(int x=0;x<16;x+=2) for(int y=0;y<16;y+=2) for(int z=0;z<16;z+=2)
                dense.add(new Tile(stone,new MicroBox(x,y,z,x+2,y+2,z+2),0xFFFFFF,true));
            var denseMesh=builder.build(dense,prepared,List.of(),client.level,false);
            check(denseMesh.sourceQuads()==3072 && denseMesh.outputQuads()==384 && denseMesh.batches().size()==1,
                    "512 microtiles: cull interior faces (3072 -> 384) and batch one render layer");
            var neighbor=new MaterialMeshBuilder.Occluder(stone,new MicroBox(16,0,0,32,16,16),0xFFFFFF);
            check(builder.build(slabs,prepared,List.of(neighbor),client.level,false).outputQuads()==33,
                    "Neighboring full block must hide the shared boundary face");
            var cube=new Tile(stone,new MicroBox(0,0,0,16,16,16),0xFFFFFF,true);
            var full=builder.build(List.of(cube),prepared,List.of(),client.level,false);
            var half=builder.build(List.of(cube.withBox(new MicroBox(8,0,0,16,16,16))),prepared,List.of(),client.level,false);
            double ratio=uvArea(half)/uvArea(full);
            check(Math.abs(ratio-0.5)<0.001,"Half-width cut should retain half the original UV area, not stretch a full texture");
            var glass=Blocks.GLASS.defaultBlockState();
            var glassPrepared=Map.of(glass,builder.prepare(glass,client.level,pos,false));
            var left=new Tile(glass,new MicroBox(0,0,0,8,16,16),0xFFFFFF,true);
            var right=left.withBox(new MicroBox(8,0,0,16,16,16));
            check(builder.build(List.of(left,right),glassPrepared,List.of(),client.level,false).outputQuads()==10,
                    "Same transparent material should hide its internal interface");
            check(builder.build(List.of(left,right.withColor(0xFF0000)),glassPrepared,List.of(),client.level,false).outputQuads()==12,
                    "Different transparent tints must keep their interface");
        });
        var foreign=new Tile(CompatibilityFixture.BLOCK.defaultBlockState(),new MicroBox(0,0,0,16,16,16),0xFFFFFF,true);
        world.getServer().runOnServer(server -> ((LittleBlockEntity)server.overworld().getBlockEntity(pos)).replace(List.of(foreign)));
        context.waitFor(client -> client.level.getBlockEntity(pos) instanceof LittleBlockEntity be && be.tiles().equals(List.of(foreign)));
        AtomicReference<LittleTilesRenderer> renderer=new AtomicReference<>();
        AtomicReference<MaterialMeshBuilder.Mesh> first=new AtomicReference<>();
        context.runOnClient(client -> {
            var r=new LittleTilesRenderer(null);renderer.set(r);
            var mesh=r.meshFor((LittleBlockEntity)client.level.getBlockEntity(pos));first.set(mesh);
            check(mesh.sourceQuads()==6 && mesh.batches().size()==2,"Foreign Fabric emission-only model must retain multiple render layers");
            var top=mesh.batches().stream().flatMap(b -> b.quads().stream()).flatMap(q -> q.vertices().stream())
                    .filter(v -> v.ny()>0.9).findFirst().orElseThrow();
            check(Math.abs(top.r()-0x33/255F)<0.01 && Math.abs(top.g()-0xAA/255F)<0.01,
                    "Source material's world tint provider must be applied");
            check(mesh.batches().stream().flatMap(b -> b.quads().stream()).flatMap(q -> q.vertices().stream())
                    .anyMatch(v -> v.nz()<-0.9 && v.light()==0xF000F0),"Emissive Fabric quad metadata must survive");
        });
        context.waitTicks(3);
        context.runOnClient(client -> {
            var r=renderer.get();check(r.meshFor((LittleBlockEntity)client.level.getBlockEntity(pos))==first.get(),"Unchanged world should reuse the compiled mesh across ticks");
            check(r.meshBuilds()==1 && r.modelCaptures()==1,"Stable model must not re-emit or rebuild each frame/tick");
        });
        context.takeScreenshot("littletiles-foreign-frapi-material");
        world.getServer().runOnServer(server -> server.overworld().setBlockAndUpdate(pos.east(2),Blocks.GOLD_BLOCK.defaultBlockState()));
        context.waitFor(client -> client.level.getBlockState(pos.east(2)).is(Blocks.GOLD_BLOCK));context.waitTicks(2);
        context.runOnClient(client -> {
            var r=renderer.get();var mesh=r.meshFor((LittleBlockEntity)client.level.getBlockEntity(pos));
            check(r.meshBuilds()==2 && r.modelCaptures()==2,"A contextual geometry key change must invalidate caches even beyond immediate neighbors");
            var top=mesh.batches().stream().flatMap(b -> b.quads().stream()).flatMap(q -> q.vertices().stream())
                    .filter(v -> v.ny()>0.9).findFirst().orElseThrow();
            check(top.r()>0.99 && Math.abs(top.g()-0x66/255F)<0.01,"Changing contextual source tint must update rendered color");
        });
        context.takeScreenshot("littletiles-foreign-context-updated");
        world.getServer().runOnServer(server -> {
            server.overworld().setBlockAndUpdate(pos.east(2),Blocks.AIR.defaultBlockState());
            ((LittleBlockEntity)server.overworld().getBlockEntity(pos)).replace(original);
        });
        context.waitFor(client -> client.level.getBlockEntity(pos) instanceof LittleBlockEntity be && be.tiles().equals(original));
        System.out.println("Render checks passed: 512 microtiles 3072 -> 384 quads, 512 -> 1 layer submissions; cross-block culling, cropped UVs, transparent interfaces, FRAPI emission-only model, contextual tint and cache invalidation.");
    }
    private static double uvArea(MaterialMeshBuilder.Mesh mesh) {
        var vertices=mesh.batches().stream().flatMap(b -> b.quads().stream()).flatMap(q -> q.vertices().stream())
                .filter(v -> v.nz()<-0.9 && Math.abs(v.z())<1E-6).toList();
        return (vertices.stream().mapToDouble(MaterialMeshBuilder.Vertex::u).max().orElseThrow()
                -vertices.stream().mapToDouble(MaterialMeshBuilder.Vertex::u).min().orElseThrow())
                *(vertices.stream().mapToDouble(MaterialMeshBuilder.Vertex::v).max().orElseThrow()
                -vertices.stream().mapToDouble(MaterialMeshBuilder.Vertex::v).min().orElseThrow());
    }
}
