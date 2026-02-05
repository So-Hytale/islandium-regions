package com.islandium.regions.shape;

import com.islandium.regions.model.BoundingBox;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Utilitaire pour serialiser/deserialiser les formes depuis/vers la base de donnees.
 */
public final class ShapeSerializer {

    private ShapeSerializer() {}

    /**
     * Reconstruit une RegionShape depuis un ResultSet.
     *
     * Le ResultSet doit contenir les colonnes :
     * - shape_type (VARCHAR, peut etre null -> "cuboid" par defaut)
     * - min_x, min_y, min_z, max_x, max_y, max_z (INT, toujours presents)
     * - center_x, center_y, center_z (INT, null pour cuboid)
     * - radius (INT, null pour cuboid)
     * - shape_height (INT, null pour cuboid)
     * - radius_adjust (DOUBLE, null -> defaut 0.46)
     */
    @NotNull
    public static RegionShape fromResultSet(@NotNull ResultSet rs) throws SQLException {
        String shapeType = rs.getString("shape_type");

        // Fallback pour les anciennes regions sans shape_type
        if (shapeType == null || shapeType.isBlank()) {
            shapeType = CuboidShape.TYPE;
        }

        return switch (shapeType.toLowerCase()) {
            case "cylinder" -> cylinderFromResultSet(rs);
            case "global" -> new GlobalShape();
            default -> cuboidFromResultSet(rs);
        };
    }

    /**
     * Cree un CuboidShape depuis le ResultSet.
     */
    @NotNull
    private static CuboidShape cuboidFromResultSet(@NotNull ResultSet rs) throws SQLException {
        return new CuboidShape(
            rs.getInt("min_x"),
            rs.getInt("min_y"),
            rs.getInt("min_z"),
            rs.getInt("max_x"),
            rs.getInt("max_y"),
            rs.getInt("max_z")
        );
    }

    /**
     * Cree un CylinderShape depuis le ResultSet.
     */
    @NotNull
    private static CylinderShape cylinderFromResultSet(@NotNull ResultSet rs) throws SQLException {
        int centerX = rs.getInt("center_x");
        int centerY = rs.getInt("center_y");
        int centerZ = rs.getInt("center_z");
        int radius = rs.getInt("radius");
        int height = rs.getInt("shape_height");

        double radiusAdjust = rs.getDouble("radius_adjust");
        if (rs.wasNull() || radiusAdjust <= 0) {
            radiusAdjust = CylinderShape.DEFAULT_RADIUS_ADJUST;
        }

        return new CylinderShape(centerX, centerY, centerZ, radius, height, radiusAdjust);
    }

    /**
     * Cree un CuboidShape depuis un BoundingBox (pour compatibilite).
     */
    @NotNull
    public static CuboidShape fromBoundingBox(@NotNull BoundingBox bounds) {
        return new CuboidShape(bounds);
    }
}
