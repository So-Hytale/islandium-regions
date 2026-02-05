package com.islandium.regions.model;

import com.hypixel.hytale.math.vector.Vector3i;
import org.jetbrains.annotations.NotNull;

/**
 * Représente une boîte englobante (AABB) pour une région.
 */
public class BoundingBox {

    private final int minX, minY, minZ;
    private final int maxX, maxY, maxZ;

    public BoundingBox(@NotNull Vector3i pos1, @NotNull Vector3i pos2) {
        this.minX = Math.min(pos1.getX(), pos2.getX());
        this.minY = Math.min(pos1.getY(), pos2.getY());
        this.minZ = Math.min(pos1.getZ(), pos2.getZ());
        this.maxX = Math.max(pos1.getX(), pos2.getX());
        this.maxY = Math.max(pos1.getY(), pos2.getY());
        this.maxZ = Math.max(pos1.getZ(), pos2.getZ());
    }

    public BoundingBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    /**
     * Vérifie si un point est contenu dans cette boîte.
     */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }

    /**
     * Vérifie si un point Vector3i est contenu dans cette boîte.
     */
    public boolean contains(@NotNull Vector3i pos) {
        return contains(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Vérifie si cette boîte intersecte un chunk donné.
     */
    public boolean intersectsChunk(int chunkX, int chunkZ) {
        int chunkMinX = chunkX << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunkZ << 4;
        int chunkMaxZ = chunkMinZ + 15;

        return !(maxX < chunkMinX || minX > chunkMaxX ||
                 maxZ < chunkMinZ || minZ > chunkMaxZ);
    }

    /**
     * Calcule le volume de cette boîte.
     */
    public long getVolume() {
        return (long)(maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Vérifie si cette boîte intersecte une autre boîte.
     */
    public boolean intersects(@NotNull BoundingBox other) {
        return !(maxX < other.minX || minX > other.maxX ||
                 maxY < other.minY || minY > other.maxY ||
                 maxZ < other.minZ || minZ > other.maxZ);
    }

    // Getters

    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }

    @Override
    public String toString() {
        return "BoundingBox{" +
            "min=(" + minX + "," + minY + "," + minZ + "), " +
            "max=(" + maxX + "," + maxY + "," + maxZ + ")}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BoundingBox that = (BoundingBox) o;
        return minX == that.minX && minY == that.minY && minZ == that.minZ &&
               maxX == that.maxX && maxY == that.maxY && maxZ == that.maxZ;
    }

    @Override
    public int hashCode() {
        int result = minX;
        result = 31 * result + minY;
        result = 31 * result + minZ;
        result = 31 * result + maxX;
        result = 31 * result + maxY;
        result = 31 * result + maxZ;
        return result;
    }
}
