package com.creativemd.littletiles.fabric.item;

import com.creativemd.littletiles.fabric.tile.Tile;
import com.creativemd.littletiles.fabric.tile.Tiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Pure placement plan; costs and refunds are derived from actual geometric changes. */
public final class PlacementMath {
    public record Result(List<Tile> output,List<Tile> added,List<Tile> removed,List<Tile> unused) {
        public Result {output=List.copyOf(output);added=List.copyOf(added);removed=List.copyOf(removed);unused=List.copyOf(unused);}
    }
    private PlacementMath() {}
    public static Optional<Result> plan(List<Tile> existing,List<Tile> incoming,ToolSettings.PlaceMode mode) {
        if(!Tiles.valid(existing)||!Tiles.valid(incoming)||incoming.isEmpty()) return Optional.empty();
        var added=new ArrayList<Tile>();var removed=new ArrayList<Tile>();var unused=new ArrayList<Tile>();
        List<Tile> remaining=existing;
        if(mode==ToolSettings.PlaceMode.NORMAL) added.addAll(incoming);
        else if(mode==ToolSettings.PlaceMode.FILL) {
            for(Tile tile:incoming) {
                List<Tile> fragments=List.of(tile);
                for(Tile old:existing) {
                    tile.box().intersection(old.box()).ifPresent(box -> unused.add(tile.withBox(box)));
                    fragments=subtract(fragments,old);
                }
                added.addAll(fragments);
            }
        } else {
            for(Tile tile:existing) for(Tile stamp:incoming)
                tile.box().intersection(stamp.box()).ifPresent(box -> removed.add(tile.withBox(box)));
            for(Tile stamp:incoming) remaining=subtract(remaining,stamp);
            if(mode==ToolSettings.PlaceMode.OVERWRITE) added.addAll(incoming);
            else unused.addAll(incoming);
        }
        var output=new ArrayList<>(remaining);output.addAll(added);
        if(!Tiles.valid(output)||output.equals(existing)) return Optional.empty();
        return Optional.of(new Result(output,added,removed,unused));
    }
    private static List<Tile> subtract(List<Tile> tiles,Tile cut) {
        var result=new ArrayList<Tile>();
        for(Tile tile:tiles) tile.box().subtract(cut.box()).forEach(box -> result.add(tile.withBox(box)));
        return result;
    }
}
