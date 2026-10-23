package com.creativemd.littletiles.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Integer micro-grid geometry extracted from legacy LittleTileBox semantics.
 * Bounds are half-open; touching faces do not overlap. Coordinates may extend
 * outside one block, as placement previews and structures require.
 */
public record MicroBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public static final int GRID = 16;

    public enum Axis { X, Y, Z }

    public MicroBox {
        if (minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("Micro-box must have positive extent on every axis");
        }
    }

    public long volume() {
        return Math.multiplyExact(Math.multiplyExact((long) maxX - minX, (long) maxY - minY),
                (long) maxZ - minZ);
    }

    public double blockVolume() {
        return volume() / (double) (GRID * GRID * GRID);
    }

    public boolean isInsideBlock() {
        return minX >= 0 && minY >= 0 && minZ >= 0 && maxX <= GRID && maxY <= GRID && maxZ <= GRID;
    }

    public boolean contains(MicroBox other) {
        return minX <= other.minX && minY <= other.minY && minZ <= other.minZ
                && maxX >= other.maxX && maxY >= other.maxY && maxZ >= other.maxZ;
    }

    public boolean intersects(MicroBox other) {
        return minX < other.maxX && maxX > other.minX && minY < other.maxY && maxY > other.minY
                && minZ < other.maxZ && maxZ > other.minZ;
    }

    public Optional<MicroBox> intersection(MicroBox other) {
        if (!intersects(other)) return Optional.empty();
        return Optional.of(new MicroBox(Math.max(minX, other.minX), Math.max(minY, other.minY),
                Math.max(minZ, other.minZ), Math.min(maxX, other.maxX), Math.min(maxY, other.maxY),
                Math.min(maxZ, other.maxZ)));
    }

    public MicroBox translate(int x, int y, int z) {
        return new MicroBox(Math.addExact(minX, x), Math.addExact(minY, y), Math.addExact(minZ, z),
                Math.addExact(maxX, x), Math.addExact(maxY, y), Math.addExact(maxZ, z));
    }

    /** A right-handed quarter turn around an integer grid point. */
    public MicroBox rotate(Axis axis, int turns, int centerX, int centerY, int centerZ) {
        Objects.requireNonNull(axis);
        int count = Math.floorMod(turns, 4);
        if (count == 0) return this;
        long lx = Long.MAX_VALUE, ly = Long.MAX_VALUE, lz = Long.MAX_VALUE;
        long hx = Long.MIN_VALUE, hy = Long.MIN_VALUE, hz = Long.MIN_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            long x = (long) ((corner & 1) == 0 ? minX : maxX) - centerX;
            long y = (long) ((corner & 2) == 0 ? minY : maxY) - centerY;
            long z = (long) ((corner & 4) == 0 ? minZ : maxZ) - centerZ;
            for (int turn = 0; turn < count; turn++) {
                long oldX = x, oldY = y, oldZ = z;
                switch (axis) {
                    case X -> { y = -oldZ; z = oldY; }
                    case Y -> { x = oldZ; z = -oldX; }
                    case Z -> { x = -oldY; y = oldX; }
                }
            }
            x += centerX; y += centerY; z += centerZ;
            lx = Math.min(lx, x); ly = Math.min(ly, y); lz = Math.min(lz, z);
            hx = Math.max(hx, x); hy = Math.max(hy, y); hz = Math.max(hz, z);
        }
        return new MicroBox(Math.toIntExact(lx), Math.toIntExact(ly), Math.toIntExact(lz),
                Math.toIntExact(hx), Math.toIntExact(hy), Math.toIntExact(hz));
    }

    public MicroBox mirror(Axis axis, int center) {
        Objects.requireNonNull(axis);
        long doubledCenter = 2L * center;
        return switch (axis) {
            case X -> new MicroBox(Math.toIntExact(doubledCenter - maxX), minY, minZ,
                    Math.toIntExact(doubledCenter - minX), maxY, maxZ);
            case Y -> new MicroBox(minX, Math.toIntExact(doubledCenter - maxY), minZ,
                    maxX, Math.toIntExact(doubledCenter - minY), maxZ);
            case Z -> new MicroBox(minX, minY, Math.toIntExact(doubledCenter - maxZ),
                    maxX, maxY, Math.toIntExact(doubledCenter - minZ));
        };
    }

    /** Exact union only when it is itself one cuboid, matching legacy combineBoxes. */
    public Optional<MicroBox> merge(MicroBox other) {
        if (equals(other)) return Optional.of(this);
        boolean x = minX == other.minX && maxX == other.maxX;
        boolean y = minY == other.minY && maxY == other.maxY;
        boolean z = minZ == other.minZ && maxZ == other.maxZ;
        if ((x && y && (maxZ == other.minZ || minZ == other.maxZ))
                || (x && z && (maxY == other.minY || minY == other.maxY))
                || (y && z && (maxX == other.minX || minX == other.maxX))) {
            return Optional.of(new MicroBox(Math.min(minX, other.minX), Math.min(minY, other.minY),
                    Math.min(minZ, other.minZ), Math.max(maxX, other.maxX), Math.max(maxY, other.maxY),
                    Math.max(maxZ, other.maxZ)));
        }
        return Optional.empty();
    }

    /** Legacy splitByBox: remove a cuboid, returning up to six disjoint slabs. */
    public List<MicroBox> subtract(MicroBox cutter) {
        var overlap = intersection(cutter);
        if (overlap.isEmpty()) return List.of(this);
        MicroBox i = overlap.get();
        var pieces = new ArrayList<MicroBox>(6);
        if (minX < i.minX) pieces.add(new MicroBox(minX, minY, minZ, i.minX, maxY, maxZ));
        if (i.maxX < maxX) pieces.add(new MicroBox(i.maxX, minY, minZ, maxX, maxY, maxZ));
        if (minY < i.minY) pieces.add(new MicroBox(i.minX, minY, minZ, i.maxX, i.minY, maxZ));
        if (i.maxY < maxY) pieces.add(new MicroBox(i.minX, i.maxY, minZ, i.maxX, maxY, maxZ));
        if (minZ < i.minZ) pieces.add(new MicroBox(i.minX, i.minY, minZ, i.maxX, i.maxY, i.minZ));
        if (i.maxZ < maxZ) pieces.add(new MicroBox(i.minX, i.minY, i.maxZ, i.maxX, i.maxY, maxZ));
        return List.copyOf(pieces);
    }
}
