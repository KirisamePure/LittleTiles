package com.creativemd.littletiles.core;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/** Sutherland-Hodgman half-plane clipping, preserving caller-defined vertex attributes. */
public final class SurfaceClipper {
    @FunctionalInterface public interface Interpolator<V> { V interpolate(V a, V b, double fraction); }
    private SurfaceClipper() {}
    public static <V> List<V> clip(List<V> polygon, ToDoubleFunction<V> coordinate, double boundary,
                                   boolean keepGreater, Interpolator<V> interpolate) {
        if (polygon.isEmpty()) return List.of();
        var output = new ArrayList<V>();
        V previous = polygon.getLast();
        double previousDistance = coordinate.applyAsDouble(previous) - boundary;
        boolean previousInside = keepGreater ? previousDistance >= -1E-8 : previousDistance <= 1E-8;
        for (V current : polygon) {
            double currentDistance = coordinate.applyAsDouble(current) - boundary;
            boolean currentInside = keepGreater ? currentDistance >= -1E-8 : currentDistance <= 1E-8;
            if (currentInside != previousInside) {
                double ratio = previousDistance / (previousDistance - currentDistance);
                output.add(interpolate.interpolate(previous, current, Math.clamp(ratio, 0, 1)));
            }
            if (currentInside) output.add(current);
            previous = current; previousDistance = currentDistance; previousInside = currentInside;
        }
        return List.copyOf(output);
    }
}
