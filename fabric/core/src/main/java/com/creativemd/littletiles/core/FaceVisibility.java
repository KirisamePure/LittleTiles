package com.creativemd.littletiles.core;

import java.util.ArrayList;
import java.util.List;

/** Exact rectangle subtraction for cuboid surface occlusion, in integer micro-grid units. */
public final class FaceVisibility {
    public enum Face {
        WEST(0, -1), EAST(0, 1), DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1);
        final int axis, step;
        Face(int axis, int step) { this.axis = axis; this.step = step; }
    }
    public record Rect(int minU, int minV, int maxU, int maxV) {
        public Rect {
            if (minU >= maxU || minV >= maxV) throw new IllegalArgumentException("Empty surface rectangle");
        }
        public long area() { return (long) (maxU - minU) * (maxV - minV); }
        public boolean contains(int u, int v) { return u >= minU && u < maxU && v >= minV && v < maxV; }
        public List<Rect> subtract(Rect cut) {
            int u0 = Math.max(minU, cut.minU), v0 = Math.max(minV, cut.minV);
            int u1 = Math.min(maxU, cut.maxU), v1 = Math.min(maxV, cut.maxV);
            if (u0 >= u1 || v0 >= v1) return List.of(this);
            var out = new ArrayList<Rect>(4);
            if (minU < u0) out.add(new Rect(minU, minV, u0, maxV));
            if (u1 < maxU) out.add(new Rect(u1, minV, maxU, maxV));
            if (minV < v0) out.add(new Rect(u0, minV, u1, v0));
            if (v1 < maxV) out.add(new Rect(u0, v1, u1, maxV));
            return List.copyOf(out);
        }
    }
    private FaceVisibility() {}
    public static Rect project(MicroBox b, Face face) {
        return switch (face.axis) {
            case 0 -> new Rect(b.minZ(), b.minY(), b.maxZ(), b.maxY());
            case 1 -> new Rect(b.minX(), b.minZ(), b.maxX(), b.maxZ());
            default -> new Rect(b.minX(), b.minY(), b.maxX(), b.maxY());
        };
    }
    public static int plane(MicroBox b, Face face) {
        return switch (face.axis) {
            case 0 -> face.step < 0 ? b.minX() : b.maxX();
            case 1 -> face.step < 0 ? b.minY() : b.maxY();
            default -> face.step < 0 ? b.minZ() : b.maxZ();
        };
    }
    public static List<Rect> visible(MicroBox source, Face face, List<MicroBox> occluders) {
        List<Rect> fragments = List.of(project(source, face));
        int plane = plane(source, face);
        for (MicroBox other : occluders) {
            int adjacent = switch (face.axis) {
                case 0 -> face.step > 0 ? other.minX() : other.maxX();
                case 1 -> face.step > 0 ? other.minY() : other.maxY();
                default -> face.step > 0 ? other.minZ() : other.maxZ();
            };
            if (adjacent != plane) continue;
            Rect cut = project(other, face);
            var next = new ArrayList<Rect>();
            for (Rect fragment : fragments) next.addAll(fragment.subtract(cut));
            fragments = List.copyOf(next);
            if (fragments.isEmpty()) break;
        }
        return fragments;
    }
}
