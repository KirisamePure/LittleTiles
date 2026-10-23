package com.creativemd.littletiles.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class FaceVisibilityTest {
    public static void main(String[] args) {
        record Vertex(double x, double y, double u, double v) {
            Vertex interpolate(Vertex b, double t) { return new Vertex(x + (b.x - x) * t, y + (b.y - y) * t,
                    u + (b.u - u) * t, v + (b.v - v) * t); }
        }
        List<Vertex> square = List.of(new Vertex(0, 0, 0, 0), new Vertex(1, 0, 1, 0),
                new Vertex(1, 1, 1, 1), new Vertex(0, 1, 0, 1));
        var clipped = SurfaceClipper.clip(square, Vertex::x, 0.25, true, Vertex::interpolate);
        clipped = SurfaceClipper.clip(clipped, Vertex::x, 0.75, false, Vertex::interpolate);
        clipped = SurfaceClipper.clip(clipped, Vertex::y, 0.5, false, Vertex::interpolate);
        if (clipped.size() != 4 || clipped.stream().anyMatch(v -> Math.abs(v.x - v.u) > 1E-9
                || Math.abs(v.y - v.v) > 1E-9 || v.x < 0.25 || v.x > 0.75 || v.y > 0.5))
            throw new AssertionError("Clipping must crop texture coordinates without stretching");
        MicroBox block = new MicroBox(0, 0, 0, 16, 16, 16);
        for (var face : FaceVisibility.Face.values()) {
            int dx = face == FaceVisibility.Face.WEST ? -16 : face == FaceVisibility.Face.EAST ? 16 : 0;
            int dy = face == FaceVisibility.Face.DOWN ? -16 : face == FaceVisibility.Face.UP ? 16 : 0;
            int dz = face == FaceVisibility.Face.NORTH ? -16 : face == FaceVisibility.Face.SOUTH ? 16 : 0;
            if (!FaceVisibility.visible(block, face, List.of(block.translate(dx, dy, dz))).isEmpty())
                throw new AssertionError("Shared full face should be culled: " + face);
            if (FaceVisibility.visible(block, face, List.of(block.translate(dx * 2, dy * 2, dz * 2)))
                    .stream().mapToLong(FaceVisibility.Rect::area).sum() != 256)
                throw new AssertionError("Separated boxes must not hide faces");
        }
        var random = new Random(121101);
        int checks = 0;
        for (int sample = 0; sample < 1000; sample++) {
            var cuts = new ArrayList<FaceVisibility.Rect>();
            var boxes = new ArrayList<MicroBox>();
            for (int i = 0; i < 8; i++) {
                int x = random.nextInt(16), y = random.nextInt(16);
                int x1 = x + 1 + random.nextInt(16 - x), y1 = y + 1 + random.nextInt(16 - y);
                cuts.add(new FaceVisibility.Rect(x, y, x1, y1));
                boxes.add(new MicroBox(x, 16, y, x1, 17, y1));
            }
            var visible = FaceVisibility.visible(block, FaceVisibility.Face.UP, boxes);
            for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) {
                final int u = x, v = y;
                boolean hidden = cuts.stream().anyMatch(r -> r.contains(u, v));
                long coverage = visible.stream().filter(r -> r.contains(u, v)).count();
                if (coverage != (hidden ? 0 : 1)) throw new AssertionError("Surface subtraction mismatch");
                checks++;
            }
        }
        System.out.println("Face visibility suite passed: " + checks + " surface coverage checks, 12 adjacency cases, and UV clipping.");
    }
}
