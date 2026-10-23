package com.creativemd.littletiles.fabric.client;

import com.creativemd.littletiles.core.FaceVisibility;
import com.creativemd.littletiles.core.MicroBox;
import com.creativemd.littletiles.core.SurfaceClipper;
import com.creativemd.littletiles.fabric.tile.LittleBlockEntity;
import com.creativemd.littletiles.fabric.tile.Tile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.renderer.v1.Renderer;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadView;
import net.fabricmc.fabric.api.renderer.v1.model.FabricBlockStateModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderLayerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** World-aware Fabric mesh capture, exact UV cropping, occlusion and render-layer batching. */
public final class MaterialMeshBuilder {
    public record Vertex(float x, float y, float z, float u, float v, float r, float g, float b, float a,
                         float nx, float ny, float nz, int light) {
        public Vertex interpolate(Vertex other, double fraction) {
            float t = (float) fraction;
            return new Vertex(lerp(x, other.x, t), lerp(y, other.y, t), lerp(z, other.z, t),
                    lerp(u, other.u, t), lerp(v, other.v, t), lerp(r, other.r, t), lerp(g, other.g, t),
                    lerp(b, other.b, t), lerp(a, other.a, t), lerp(nx, other.nx, t), lerp(ny, other.ny, t),
                    lerp(nz, other.nz, t), combineLight(light, other.light));
        }
        private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
        Vertex axis(int axis, float value) {
            return new Vertex(axis == 0 ? value : x, axis == 1 ? value : y, axis == 2 ? value : z,
                    u, v, r, g, b, a, nx, ny, nz, light);
        }
        Vertex color(int tint, int tileColor, float shade) {
            return new Vertex(x, y, z, u, v, r * ((tint >> 16) & 255) / 255F * ((tileColor >> 16) & 255) / 255F * shade,
                    g * ((tint >> 8) & 255) / 255F * ((tileColor >> 8) & 255) / 255F * shade,
                    b * (tint & 255) / 255F * (tileColor & 255) / 255F * shade, a, nx, ny, nz, light);
        }
    }
    public record DrawQuad(List<Vertex> vertices) { public DrawQuad { vertices = List.copyOf(vertices); } }
    public record Batch(RenderType layer, List<DrawQuad> quads) { public Batch { quads = List.copyOf(quads); } }
    public record Mesh(List<Batch> batches, int sourceQuads, int outputQuads) {
        public Mesh { batches = List.copyOf(batches); }
    }
    private record RawQuad(List<Vertex> vertices, RenderType layer, Direction face, boolean boundary, int tint, boolean shade) {}
    private record GeometryKey(BlockState material, Object key) {}
    public record Occluder(BlockState material, MicroBox box, int color) {}
    public static final class Prepared {
        private final Object key;
        private final List<RawQuad> quads;
        private final Map<Integer, Integer> tints;
        private Prepared(Object key, List<RawQuad> quads, Map<Integer, Integer> tints) {
            this.key = key; this.quads = quads; this.tints = tints;
        }
        public Object signature() { return List.of(key, tints); }
    }
    private final Map<GeometryKey, List<RawQuad>> geometryCache = new LinkedHashMap<>(32, 0.75F, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<GeometryKey, List<RawQuad>> entry) { return size() > 256; }
    };
    private long captures;
    public long captures() { return captures; }

    public Prepared prepare(BlockState material, BlockAndTintGetter world, BlockPos pos, boolean item) {
        var model = Minecraft.getInstance().getBlockRenderer().getBlockModel(material);
        var fabric = (FabricBlockStateModel) model;
        var view = new MaterialView(world, pos, material);
        long seed = material.getSeed(pos);
        Object key = fabric.createGeometryKey(view, pos, material, RandomSource.create(seed));
        GeometryKey cacheKey = key == null ? null : new GeometryKey(material, key);
        List<RawQuad> quads = cacheKey == null ? null : geometryCache.get(cacheKey);
        if (quads == null) {
            var captured = new ArrayList<RawQuad>();
            var mesh = Renderer.get().mutableMesh();
            var emitter = mesh.emitter();
            emitter.pushTransform(quad -> { captured.add(capture(quad, material)); return false; });
            try { fabric.emitQuads(emitter, view, pos, material, RandomSource.create(seed), face -> false); }
            finally { emitter.popTransform(); }
            captures++;
            quads = List.copyOf(captured);
            if (cacheKey != null) geometryCache.put(cacheKey, quads);
        }
        Map<Integer, Integer> tints = new LinkedHashMap<>();
        for (RawQuad quad : quads) if (quad.tint >= 0 && !tints.containsKey(quad.tint))
            tints.put(quad.tint, Minecraft.getInstance().getBlockColors().getColor(material, item ? null : view,
                    item ? null : pos, quad.tint));
        // Null geometry keys explicitly opt out of caching across context checks.
        return new Prepared(cacheKey == null ? new Object() : cacheKey, quads, Map.copyOf(tints));
    }

    private static RawQuad capture(QuadView quad, BlockState material) {
        var vertices = new ArrayList<Vertex>(4);
        Direction face = quad.lightFace();
        int axis = face.getAxis().ordinal();
        float plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : 0;
        boolean boundary = true;
        for (int i = 0; i < 4; i++) {
            int c = quad.color(i);
            var n = quad.faceNormal();
            vertices.add(new Vertex(quad.x(i), quad.y(i), quad.z(i), quad.u(i), quad.v(i),
                    ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, ((c >>> 24) & 255) / 255F,
                    quad.hasNormal(i) ? quad.normalX(i) : n.x(), quad.hasNormal(i) ? quad.normalY(i) : n.y(),
                    quad.hasNormal(i) ? quad.normalZ(i) : n.z(), quad.emissive() ? 0xF000F0 : quad.lightmap(i)));
            boundary &= Math.abs(quad.posByIndex(i, axis) - plane) < 1E-5;
        }
        var layer = quad.renderLayer() == null ? ItemBlockRenderTypes.getChunkRenderType(material) : quad.renderLayer();
        return new RawQuad(List.copyOf(vertices), RenderLayerHelper.getEntityBlockLayer(layer), face, boundary,
                quad.tintIndex(), quad.diffuseShade());
    }

    public Mesh build(List<Tile> tiles, Map<BlockState, Prepared> prepared, List<Occluder> neighbors,
                       BlockAndTintGetter world, boolean item) {
        Map<RenderType, List<DrawQuad>> batches = new LinkedHashMap<>();
        int source = 0;
        for (Tile tile : tiles) {
            Prepared input = prepared.get(tile.material());
            List<RawQuad> quads = input.quads;
            source += quads.size();
            Map<Direction, List<FaceVisibility.Rect>> visible = new java.util.EnumMap<>(Direction.class);
            for (Direction face : Direction.values()) {
                var occluders = new ArrayList<MicroBox>();
                for (Tile other : tiles) if (other != tile && occludes(tile, other.material(), other.color(), face)) occluders.add(other.box());
                for (Occluder other : neighbors) if (occludes(tile, other.material(), other.color(), face)) occluders.add(other.box());
                visible.put(face, FaceVisibility.visible(tile.box(), FaceVisibility.Face.valueOf(face.name()), occluders));
            }
            for (RawQuad quad : quads) {
                int tint = input.tints.getOrDefault(quad.tint, 0xFFFFFF);
                float shade = quad.shade ? world.getShade(quad.face, true) : 1;
                List<Vertex> colored = quad.vertices.stream().map(v -> v.color(tint, tile.color(), shade)).toList();
                if (quad.boundary) {
                    int axis = quad.face.getAxis().ordinal();
                    float plane = FaceVisibility.plane(tile.box(), FaceVisibility.Face.valueOf(quad.face.name())) / 16F;
                    colored = colored.stream().map(v -> v.axis(axis, plane)).toList();
                    for (var rect : visible.get(quad.face)) {
                        var polygon = clipRectangle(colored, axis, rect);
                        append(batches, quad.layer, polygon);
                    }
                } else append(batches, quad.layer, clipBox(colored, tile.box()));
            }
        }
        List<Batch> result = batches.entrySet().stream().map(e -> new Batch(e.getKey(), e.getValue())).toList();
        return new Mesh(result, source, result.stream().mapToInt(b -> b.quads.size()).sum());
    }
    private static boolean occludes(Tile source, BlockState other, int otherColor, Direction face) {
        return other.canOcclude() || (source.color() == otherColor && source.material().skipRendering(other, face));
    }
    private static List<Vertex> clipRectangle(List<Vertex> polygon, int axis, FaceVisibility.Rect rect) {
        ToDoubleFunction<Vertex> u = axis == 0 ? Vertex::z : Vertex::x;
        ToDoubleFunction<Vertex> v = axis == 1 ? Vertex::z : Vertex::y;
        polygon = SurfaceClipper.clip(polygon, u, rect.minU() / 16.0, true, Vertex::interpolate);
        polygon = SurfaceClipper.clip(polygon, u, rect.maxU() / 16.0, false, Vertex::interpolate);
        polygon = SurfaceClipper.clip(polygon, v, rect.minV() / 16.0, true, Vertex::interpolate);
        return SurfaceClipper.clip(polygon, v, rect.maxV() / 16.0, false, Vertex::interpolate);
    }
    private static List<Vertex> clipBox(List<Vertex> polygon, MicroBox b) {
        polygon = SurfaceClipper.clip(polygon, Vertex::x, b.minX() / 16.0, true, Vertex::interpolate);
        polygon = SurfaceClipper.clip(polygon, Vertex::x, b.maxX() / 16.0, false, Vertex::interpolate);
        polygon = SurfaceClipper.clip(polygon, Vertex::y, b.minY() / 16.0, true, Vertex::interpolate);
        polygon = SurfaceClipper.clip(polygon, Vertex::y, b.maxY() / 16.0, false, Vertex::interpolate);
        polygon = SurfaceClipper.clip(polygon, Vertex::z, b.minZ() / 16.0, true, Vertex::interpolate);
        return SurfaceClipper.clip(polygon, Vertex::z, b.maxZ() / 16.0, false, Vertex::interpolate);
    }
    private static void append(Map<RenderType, List<DrawQuad>> batches, RenderType layer, List<Vertex> polygon) {
        if (polygon.size() < 3) return;
        // Skip zero-area polygons produced when a clipping plane touches a vertex/edge.
        var a = polygon.getFirst();
        double area = 0;
        for (int i = 1; i + 1 < polygon.size(); i++) {
            var b = polygon.get(i); var c = polygon.get(i + 1);
            double x = (b.y - a.y) * (c.z - a.z) - (b.z - a.z) * (c.y - a.y);
            double y = (b.z - a.z) * (c.x - a.x) - (b.x - a.x) * (c.z - a.z);
            double z = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
            area += x*x + y*y + z*z;
        }
        if (area < 1E-15) return;
        var output = batches.computeIfAbsent(layer, key -> new ArrayList<>());
        if (polygon.size() == 4) output.add(new DrawQuad(polygon));
        else for (int i = 1; i + 1 < polygon.size(); i++)
            output.add(new DrawQuad(List.of(a, polygon.get(i), polygon.get(i + 1), polygon.get(i + 1))));
    }
    public static int combineLight(int a, int b) { return Math.max(a & 0xFFFF, b & 0xFFFF) | (Math.max(a >>> 16, b >>> 16) << 16); }
}
