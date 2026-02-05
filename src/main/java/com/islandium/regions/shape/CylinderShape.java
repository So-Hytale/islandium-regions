package com.islandium.regions.shape;

import com.islandium.regions.model.BoundingBox;
import org.jetbrains.annotations.NotNull;

/**
 * Forme cylindrique verticale.
 * Definie par un centre (base du cylindre), un rayon, une hauteur et un ajustement de rayon.
 *
 * L'algorithme de contenance utilise une formule d'ellipse avec radiusAdjust
 * pour avoir des bords parfaits (copie du systeme Prison).
 */
public class CylinderShape implements RegionShape {

    public static final String TYPE = "cylinder";
    public static final double DEFAULT_RADIUS_ADJUST = 0.46;

    private final int centerX;
    private final int centerY;  // Base du cylindre
    private final int centerZ;
    private final int radius;
    private final int height;
    private final double radiusAdjust;

    // Cache du bounding box englobant
    private final BoundingBox enclosingBounds;
    // Cache du volume
    private final long volume;

    public CylinderShape(int centerX, int centerY, int centerZ, int radius, int height) {
        this(centerX, centerY, centerZ, radius, height, DEFAULT_RADIUS_ADJUST);
    }

    public CylinderShape(int centerX, int centerY, int centerZ, int radius, int height, double radiusAdjust) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
        this.radius = Math.max(1, radius);
        this.height = Math.max(1, height);
        this.radiusAdjust = radiusAdjust;

        // Pre-calcul du bounding box englobant
        this.enclosingBounds = new BoundingBox(
            centerX - this.radius,
            centerY,
            centerZ - this.radius,
            centerX + this.radius,
            centerY + this.height - 1,
            centerZ + this.radius
        );

        // Pre-calcul du volume (formule ellipse comme Prison)
        this.volume = calculateVolume();
    }

    @Override
    @NotNull
    public String getShapeType() {
        return TYPE;
    }

    /**
     * Verifie si un point est dans le cylindre.
     * Utilise la formule d'ellipse avec radiusAdjust (copie de Prison Mine.java).
     */
    @Override
    public boolean contains(int x, int y, int z) {
        // Verifier la hauteur (Y)
        if (y < centerY || y >= centerY + height) {
            return false;
        }

        // Grille (2*radius+1) x (2*radius+1), de -radius a +radius
        double rX = radius + radiusAdjust;
        double rZ = radius + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        int dx = x - centerX;
        int dz = z - centerZ;

        // Verifier les bornes de la grille
        if (dx < -radius || dx > radius || dz < -radius || dz > radius) {
            return false;
        }

        // Formule de l'ellipse: (x^2/rX^2) + (z^2/rZ^2) < 1
        double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
        return distSq < 1.0;
    }

    @Override
    @NotNull
    public BoundingBox getEnclosingBounds() {
        return enclosingBounds;
    }

    @Override
    public long getVolume() {
        return volume;
    }

    /**
     * Calcule le volume en comptant les blocs dans l'ellipse.
     * (Copie de Prison Mine.recalculateTotalBlocks())
     */
    private long calculateVolume() {
        double rX = radius + radiusAdjust;
        double rZ = radius + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        int count = 0;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                if (distSq < 1.0) {
                    count++;
                }
            }
        }
        return (long) count * height;
    }

    @Override
    @NotNull
    public String getDescription() {
        return String.format("Cylinder{center=(%d,%d,%d), r=%d, h=%d}",
            centerX, centerY, centerZ, radius, height);
    }

    @Override
    public int getCenterX() {
        return centerX;
    }

    @Override
    public int getCenterY() {
        return centerY;
    }

    @Override
    public int getCenterZ() {
        return centerZ;
    }

    // Getters specifiques au cylindre

    public int getRadius() {
        return radius;
    }

    public int getHeight() {
        return height;
    }

    public double getRadiusAdjust() {
        return radiusAdjust;
    }

    /**
     * Cree une copie avec un nouveau rayon.
     */
    public CylinderShape withRadius(int newRadius) {
        return new CylinderShape(centerX, centerY, centerZ, newRadius, height, radiusAdjust);
    }

    /**
     * Cree une copie avec une nouvelle hauteur.
     */
    public CylinderShape withHeight(int newHeight) {
        return new CylinderShape(centerX, centerY, centerZ, radius, newHeight, radiusAdjust);
    }

    /**
     * Cree une copie avec un nouveau centre.
     */
    public CylinderShape withCenter(int newCenterX, int newCenterY, int newCenterZ) {
        return new CylinderShape(newCenterX, newCenterY, newCenterZ, radius, height, radiusAdjust);
    }

    /**
     * Cree une copie avec un nouvel ajustement de rayon.
     */
    public CylinderShape withRadiusAdjust(double newRadiusAdjust) {
        return new CylinderShape(centerX, centerY, centerZ, radius, height, newRadiusAdjust);
    }

    @Override
    public String toString() {
        return getDescription();
    }
}
