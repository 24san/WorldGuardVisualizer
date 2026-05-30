package org.san24.wgregionvisualizer.client;

import java.util.List;

public record VisualizedRegion(
        String world,
        String dimension,
        String regionId,
        Shape shape,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        List<PointXZ> points
) {
    public enum Shape {
        CUBOID,
        POLYGON
    }

    public record PointXZ(int x, int z) {
    }

    public static VisualizedRegion cuboid(String world, String dimension, String regionId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        return new VisualizedRegion(world, dimension, regionId, Shape.CUBOID, minX, minY, minZ, maxX, maxY, maxZ, List.of());
    }

    public static VisualizedRegion polygon(String world, String dimension, String regionId, int minY, int maxY, List<PointXZ> points) {
        int minX = points.stream().mapToInt(PointXZ::x).min().orElse(0);
        int minZ = points.stream().mapToInt(PointXZ::z).min().orElse(0);
        int maxX = points.stream().mapToInt(PointXZ::x).max().orElse(0);
        int maxZ = points.stream().mapToInt(PointXZ::z).max().orElse(0);
        return new VisualizedRegion(world, dimension, regionId, Shape.POLYGON, minX, minY, minZ, maxX, maxY, maxZ, List.copyOf(points));
    }

    public String key() {
        String scope = dimension.isBlank() ? world : dimension;
        return scope + ":" + regionId;
    }
}
