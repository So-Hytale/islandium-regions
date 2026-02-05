package com.islandium.regions.shape;

import com.islandium.regions.model.BoundingBox;
import org.jetbrains.annotations.NotNull;

/**
 * Forme cubique (parallelepipede rectangle aligne aux axes).
 * Encapsule un BoundingBox existant.
 */
public class CuboidShape implements RegionShape {

    public static final String TYPE = "cuboid";

    private final BoundingBox bounds;

    public CuboidShape(@NotNull BoundingBox bounds) {
        this.bounds = bounds;
    }

    public CuboidShape(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.bounds = new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
    }

    @Override
    @NotNull
    public String getShapeType() {
        return TYPE;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return bounds.contains(x, y, z);
    }

    @Override
    @NotNull
    public BoundingBox getEnclosingBounds() {
        return bounds;
    }

    @Override
    public long getVolume() {
        return bounds.getVolume();
    }

    @Override
    @NotNull
    public String getDescription() {
        return String.format("Cuboid{min=(%d,%d,%d), max=(%d,%d,%d)}",
            bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(),
            bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());
    }

    @Override
    public int getCenterX() {
        return (bounds.getMinX() + bounds.getMaxX()) / 2;
    }

    @Override
    public int getCenterY() {
        return (bounds.getMinY() + bounds.getMaxY()) / 2;
    }

    @Override
    public int getCenterZ() {
        return (bounds.getMinZ() + bounds.getMaxZ()) / 2;
    }

    /**
     * Acces direct au BoundingBox pour compatibilite.
     */
    @NotNull
    public BoundingBox getBounds() {
        return bounds;
    }

    // Getters pour les coordonnees individuelles (utile pour la serialisation)

    public int getMinX() {
        return bounds.getMinX();
    }

    public int getMinY() {
        return bounds.getMinY();
    }

    public int getMinZ() {
        return bounds.getMinZ();
    }

    public int getMaxX() {
        return bounds.getMaxX();
    }

    public int getMaxY() {
        return bounds.getMaxY();
    }

    public int getMaxZ() {
        return bounds.getMaxZ();
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
