package com.creativemd.littletiles.core;

import java.util.List;
import java.util.Random;

/** No downloaded test framework is needed: failures always throw, even without -ea. */
public final class MicroBoxTest {
    private static int checks;

    public static void main(String[] args) {
        var block = new MicroBox(0, 0, 0, 16, 16, 16);
        check(block.volume() == 4096 && block.blockVolume() == 1, "legacy 16-unit grid");
        check(block.isInsideBlock(), "full block fits");
        check(!block.translate(-1, 0, 0).isInsideBlock(), "negative preview extends outside block");
        check(!block.intersects(block.translate(16, 0, 0)), "touching faces do not collide");
        check(block.intersects(block.translate(15, 0, 0)), "one micro-unit overlap collides");
        expect(IllegalArgumentException.class, () -> new MicroBox(0, 0, 0, 0, 1, 1));
        expect(IllegalArgumentException.class, () -> new MicroBox(1, 0, 0, 0, 1, 1));
        expect(ArithmeticException.class, () -> block.translate(Integer.MAX_VALUE, 0, 0));
        var inner = new MicroBox(2, 3, 4, 12, 13, 14);
        check(block.contains(inner), "contains inner box");
        check(block.intersection(inner).orElseThrow().equals(inner), "intersection bounds");
        check(block.subtract(inner).size() == 6, "interior removal leaves six slabs");
        check(block.subtract(block).isEmpty(), "complete removal");
        check(block.subtract(block.translate(16, 0, 0)).equals(List.of(block)), "touching removal is no-op");
        var left = new MicroBox(0, 0, 0, 8, 16, 16);
        var right = new MicroBox(8, 0, 0, 16, 16, 16);
        check(left.merge(right).orElseThrow().equals(block), "adjacent cuboids merge");
        check(right.merge(left).equals(left.merge(right)), "merge is symmetric");
        check(left.merge(left).orElseThrow().equals(left), "identical boxes merge");
        check(left.merge(right.translate(0, 1, 0)).isEmpty(), "L-shaped union cannot merge");
        check(left.merge(right.translate(1, 0, 0)).isEmpty(), "gapped boxes cannot merge");
        check(new MicroBox(1, 2, 3, 4, 6, 8).rotate(MicroBox.Axis.Y, 1, 8, 8, 8)
                .equals(new MicroBox(3, 2, 12, 8, 6, 15)), "known Y rotation");

        // Seeded voxel oracle checks topology, not just volume, against the legacy subtraction contract.
        var random = new Random(12110);
        for (int sample = 0; sample < 1000; sample++) {
            MicroBox box = randomBox(random), cut = randomBox(random);
            var pieces = box.subtract(cut);
            long removed = box.intersection(cut).map(MicroBox::volume).orElse(0L);
            check(pieces.stream().mapToLong(MicroBox::volume).sum() + removed == box.volume(),
                    "subtraction conserves volume");
            for (int i = 0; i < pieces.size(); i++) {
                var piece = pieces.get(i);
                check(box.contains(piece) && !piece.intersects(cut), "piece is inside original and outside cutter");
                for (int j = i + 1; j < pieces.size(); j++) {
                    check(!piece.intersects(pieces.get(j)), "subtraction pieces are disjoint");
                }
            }
            for (int x = box.minX(); x < box.maxX(); x++) {
                for (int y = box.minY(); y < box.maxY(); y++) {
                    for (int z = box.minZ(); z < box.maxZ(); z++) {
                        var voxel = new MicroBox(x, y, z, x + 1, y + 1, z + 1);
                        long count = pieces.stream().filter(p -> p.contains(voxel)).count();
                        check(count == (cut.contains(voxel) ? 0 : 1), "voxel coverage of subtraction");
                    }
                }
            }
            for (var axis : MicroBox.Axis.values()) {
                check(box.rotate(axis, 4, 8, 8, 8).equals(box), "four turns return original");
                check(box.rotate(axis, 1, 8, 8, 8).rotate(axis, -1, 8, 8, 8).equals(box),
                        "rotation inverse");
                var turned = box;
                for (int i = 0; i < 4; i++) turned = turned.rotate(axis, 1, 8, 8, 8);
                check(turned.equals(box), "four successive rotations return original");
                check(box.rotate(axis, 1, 8, 8, 8).volume() == box.volume(), "rotation conserves volume");
                check(box.mirror(axis, -2).mirror(axis, -2).equals(box), "mirror inverse");
                check(box.mirror(axis, -2).volume() == box.volume(), "mirror conserves volume");
            }
        }
        System.out.println("MicroBox behavioral suite passed: " + checks + " checks, 1000 seeded geometry cases.");
    }

    private static MicroBox randomBox(Random random) {
        int x = random.nextInt(16) - 8, y = random.nextInt(16) - 8, z = random.nextInt(16) - 8;
        return new MicroBox(x, y, z, x + 1 + random.nextInt(8), y + 1 + random.nextInt(8),
                z + 1 + random.nextInt(8));
    }

    private static void check(boolean condition, String description) {
        checks++;
        if (!condition) throw new AssertionError(description);
    }

    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
        } catch (Throwable thrown) {
            check(type.isInstance(thrown), "expected " + type.getSimpleName() + ", got " + thrown);
            return;
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
}
