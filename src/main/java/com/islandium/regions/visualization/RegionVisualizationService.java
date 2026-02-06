package com.islandium.regions.visualization;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.CylinderShape;
import com.islandium.regions.shape.GlobalShape;
import com.islandium.regions.shape.RegionShape;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service de visualisation des régions via le système de debug shapes.
 * Affiche les contours des régions avec des couleurs différentes selon le type.
 */
public class RegionVisualizationService {

    // Couleurs par type de forme
    private static final Vector3f CUBOID_COLOR = new Vector3f(0.2f, 0.8f, 0.2f);      // Vert
    private static final Vector3f CYLINDER_COLOR = new Vector3f(0.2f, 0.6f, 1.0f);    // Bleu
    private static final Vector3f GLOBAL_COLOR = new Vector3f(1.0f, 0.8f, 0.2f);      // Or/Jaune
    private static final Vector3f DEFAULT_COLOR = new Vector3f(1.0f, 0.6f, 0.2f);     // Orange

    // Durée d'affichage en secondes (5 minutes)
    private static final float DISPLAY_DURATION = 300.0f;

    // Épaisseur des lignes
    private static final double LINE_THICKNESS = 0.08;

    // Suivi des joueurs avec visualisation active
    private final Map<UUID, Boolean> activeVisualizations = new ConcurrentHashMap<>();

    /**
     * Toggle la visualisation d'une région pour un joueur.
     *
     * @param player Le joueur
     * @param region La région à visualiser
     * @return true si la visualisation est maintenant active, false si désactivée
     */
    public boolean toggleVisualization(@NotNull Player player, @NotNull RegionImpl region) {
        UUID playerId = player.getUuid();
        boolean wasActive = activeVisualizations.getOrDefault(playerId, false);

        if (wasActive) {
            clearVisualization(player);
            activeVisualizations.put(playerId, false);
            return false;
        } else {
            sendVisualization(player, region);
            activeVisualizations.put(playerId, true);
            return true;
        }
    }

    /**
     * Envoie la visualisation d'une région au joueur.
     */
    public void sendVisualization(@NotNull Player player, @NotNull RegionImpl region) {
        RegionShape shape = region.getShape();

        // Ne pas visualiser les régions globales (trop grand)
        if (shape instanceof GlobalShape) {
            return;
        }

        // Effacer les anciennes formes
        clearVisualization(player);

        if (shape instanceof CylinderShape cylinder) {
            sendCylinderVisualization(player, cylinder);
        } else if (shape instanceof CuboidShape cuboid) {
            sendCuboidVisualization(player, cuboid);
        } else {
            // Fallback: utiliser le bounding box
            sendBoundingBoxVisualization(player, shape.getEnclosingBounds(), DEFAULT_COLOR);
        }

        activeVisualizations.put(player.getUuid(), true);
    }

    /**
     * Efface la visualisation pour un joueur.
     */
    @SuppressWarnings("deprecation")
    public void clearVisualization(@NotNull Player player) {
        var connection = player.getPlayerConnection();
        if (connection != null) {
            connection.write(new ClearDebugShapes());
        }
        activeVisualizations.put(player.getUuid(), false);
    }

    /**
     * Vérifie si un joueur a une visualisation active.
     */
    public boolean isVisualizationActive(@NotNull Player player) {
        return activeVisualizations.getOrDefault(player.getUuid(), false);
    }

    // ==================== Visualisation Cuboid ====================

    @SuppressWarnings("deprecation")
    private void sendCuboidVisualization(@NotNull Player player, @NotNull CuboidShape cuboid) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        BoundingBox bounds = cuboid.getBounds();

        double minX = bounds.getMinX();
        double minY = bounds.getMinY();
        double minZ = bounds.getMinZ();
        double maxX = bounds.getMaxX() + 1;
        double maxY = bounds.getMaxY() + 1;
        double maxZ = bounds.getMaxZ() + 1;

        List<DisplayDebug> packets = buildBoxEdges(minX, minY, minZ, maxX, maxY, maxZ, CUBOID_COLOR);

        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    // ==================== Visualisation Cylindre ====================

    @SuppressWarnings("deprecation")
    private void sendCylinderVisualization(@NotNull Player player, @NotNull CylinderShape cylinder) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        int cx = cylinder.getCenterX();
        int cy = cylinder.getCenterY();
        int cz = cylinder.getCenterZ();
        int radius = cylinder.getRadius();
        int height = cylinder.getHeight();
        double radiusAdjust = cylinder.getRadiusAdjust();

        // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
        int halfW = radius;
        int halfH = radius;
        double rX = halfW + radiusAdjust;
        double rZ = halfH + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        List<DisplayDebug> packets = new ArrayList<>();
        int edgeCount = 0;

        // Parcourir la grille de -radius à +radius pour trouver les blocs de contour
        for (int dz = -halfH; dz <= halfH; dz++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
                double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                if (distSq >= 1.0) continue; // Hors du cercle

                // Vérifier si c'est un bloc de contour (au moins un voisin hors du cercle)
                boolean isEdge = false;
                for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = dx + offset[0];
                    int nz = dz + offset[1];
                    double nDistSq = (nx * nx) / rXSq + (nz * nz) / rZSq;
                    if (nDistSq >= 1.0) {
                        isEdge = true;
                        break;
                    }
                }

                if (isEdge) {
                    edgeCount++;
                    // Position du bloc dans le monde (centre du bloc)
                    double bx = cx + dx + 0.5;
                    double bz = cz + dz + 0.5;

                    // Point en bas
                    packets.add(createSmallCube(bx, cy + 0.1, bz, CYLINDER_COLOR));
                    // Point en haut
                    packets.add(createSmallCube(bx, cy + height - 0.1, bz, CYLINDER_COLOR));

                    // Ligne verticale (tous les 4 blocs pour ne pas surcharger)
                    if ((dx + dz) % 4 == 0) {
                        packets.add(createEdge(bx, cy + height / 2.0, bz, LINE_THICKNESS, height, LINE_THICKNESS, CYLINDER_COLOR));
                    }
                }
            }
        }

        System.out.println("[DEBUG] Cylinder visualization: radius=" + radius + ", height=" + height +
                          ", radiusAdjust=" + radiusAdjust + ", edgeBlocks=" + edgeCount + ", totalPackets=" + packets.size());

        // Marqueur au centre
        packets.add(createEdge(cx + 0.5, cy + height / 2.0, cz + 0.5, 0.3, height + 2, 0.3, CYLINDER_COLOR));

        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    // ==================== Visualisation BoundingBox générique ====================

    @SuppressWarnings("deprecation")
    private void sendBoundingBoxVisualization(@NotNull Player player, @NotNull BoundingBox bounds, @NotNull Vector3f color) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        double minX = bounds.getMinX();
        double minY = bounds.getMinY();
        double minZ = bounds.getMinZ();
        double maxX = bounds.getMaxX() + 1;
        double maxY = bounds.getMaxY() + 1;
        double maxZ = bounds.getMaxZ() + 1;

        List<DisplayDebug> packets = buildBoxEdges(minX, minY, minZ, maxX, maxY, maxZ, color);

        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    // ==================== Utilitaires ====================

    /**
     * Construit les 12 arêtes d'une boîte 3D.
     */
    private List<DisplayDebug> buildBoxEdges(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ,
                                              Vector3f color) {
        List<DisplayDebug> packets = new ArrayList<>();

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;

        // 4 arêtes horizontales en bas (Y = minY)
        packets.add(createEdge(minX + sizeX/2, minY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS, color));
        packets.add(createEdge(minX + sizeX/2, minY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS, color));
        packets.add(createEdge(minX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ, color));
        packets.add(createEdge(maxX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ, color));

        // 4 arêtes horizontales en haut (Y = maxY)
        packets.add(createEdge(minX + sizeX/2, maxY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS, color));
        packets.add(createEdge(minX + sizeX/2, maxY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS, color));
        packets.add(createEdge(minX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ, color));
        packets.add(createEdge(maxX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ, color));

        // 4 arêtes verticales (piliers)
        packets.add(createEdge(minX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS, color));
        packets.add(createEdge(maxX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS, color));
        packets.add(createEdge(minX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS, color));
        packets.add(createEdge(maxX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS, color));

        return packets;
    }

    /**
     * Crée un petit cube pour marquer un point.
     */
    private DisplayDebug createSmallCube(double x, double y, double z, Vector3f color) {
        Matrix4d matrix = new Matrix4d()
                .identity()
                .translate(x, y, z)
                .scale(0.2, 0.2, 0.2);

        return new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                color,
                DISPLAY_DURATION,
                true,
                null
        );
    }

    /**
     * Crée un packet DisplayDebug pour une arête (cube allongé).
     */
    private DisplayDebug createEdge(double x, double y, double z, double scaleX, double scaleY, double scaleZ, Vector3f color) {
        Matrix4d matrix = new Matrix4d()
                .identity()
                .translate(x, y, z)
                .scale(scaleX, scaleY, scaleZ);

        return new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                color,
                DISPLAY_DURATION,
                true,
                null
        );
    }
}
