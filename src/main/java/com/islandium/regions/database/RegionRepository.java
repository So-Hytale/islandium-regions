package com.islandium.regions.database;

import com.islandium.core.database.SQLExecutor;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.CylinderShape;
import com.islandium.regions.shape.RegionShape;
import com.islandium.regions.shape.ShapeSerializer;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository pour les régions.
 */
public class RegionRepository {

    private final SQLExecutor sql;

    public RegionRepository(@NotNull SQLExecutor sql) {
        this.sql = sql;
    }

    /**
     * Crée les tables nécessaires.
     */
    public CompletableFuture<Void> createTables() {
        return sql.execute("""
            CREATE TABLE IF NOT EXISTS regions (
                id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(64) NOT NULL,
                world_name VARCHAR(64) NOT NULL,
                min_x INT NOT NULL,
                min_y INT NOT NULL,
                min_z INT NOT NULL,
                max_x INT NOT NULL,
                max_y INT NOT NULL,
                max_z INT NOT NULL,
                priority INT DEFAULT 0,
                created_by CHAR(36),
                created_at BIGINT NOT NULL,
                shape_type VARCHAR(32) NOT NULL DEFAULT 'cuboid',
                center_x INT DEFAULT NULL,
                center_y INT DEFAULT NULL,
                center_z INT DEFAULT NULL,
                radius INT DEFAULT NULL,
                height INT DEFAULT NULL,
                radius_adjust DOUBLE DEFAULT NULL,
                UNIQUE KEY uk_world_name (world_name, name),
                INDEX idx_world (world_name),
                INDEX idx_priority (priority)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """).thenCompose(v -> migrateExistingTables());
    }

    /**
     * Ajoute les nouvelles colonnes si la table existe déjà (migration).
     */
    private CompletableFuture<Void> migrateExistingTables() {
        // On ignore les erreurs si les colonnes existent déjà
        return sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS shape_type VARCHAR(32) NOT NULL DEFAULT 'cuboid'")
            .exceptionally(e -> null)
            .thenCompose(v -> sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS center_x INT DEFAULT NULL"))
            .exceptionally(e -> null)
            .thenCompose(v -> sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS center_y INT DEFAULT NULL"))
            .exceptionally(e -> null)
            .thenCompose(v -> sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS center_z INT DEFAULT NULL"))
            .exceptionally(e -> null)
            .thenCompose(v -> sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS radius INT DEFAULT NULL"))
            .exceptionally(e -> null)
            .thenCompose(v -> sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS height INT DEFAULT NULL"))
            .exceptionally(e -> null)
            .thenCompose(v -> sql.execute("ALTER TABLE regions ADD COLUMN IF NOT EXISTS radius_adjust DOUBLE DEFAULT NULL"))
            .exceptionally(e -> null)
            .thenApply(v -> null);
    }

    /**
     * Trouve une région par ID.
     */
    public CompletableFuture<Optional<RegionImpl>> findById(int id) {
        return sql.queryOne(
            "SELECT * FROM regions WHERE id = ?",
            this::mapRow,
            id
        );
    }

    /**
     * Trouve une région par nom et monde.
     */
    public CompletableFuture<Optional<RegionImpl>> findByName(@NotNull String worldName, @NotNull String name) {
        return sql.queryOne(
            "SELECT * FROM regions WHERE world_name = ? AND LOWER(name) = LOWER(?)",
            this::mapRow,
            worldName, name
        );
    }

    /**
     * Liste toutes les régions d'un monde.
     */
    public CompletableFuture<List<RegionImpl>> findByWorld(@NotNull String worldName) {
        return sql.queryList(
            "SELECT * FROM regions WHERE world_name = ? ORDER BY priority DESC, name",
            this::mapRow,
            worldName
        );
    }

    /**
     * Liste toutes les régions.
     */
    public CompletableFuture<List<RegionImpl>> findAll() {
        return sql.queryList(
            "SELECT * FROM regions ORDER BY world_name, priority DESC, name",
            this::mapRow
        );
    }

    /**
     * Liste les régions créées par un joueur.
     */
    public CompletableFuture<List<RegionImpl>> findByCreator(@NotNull UUID creatorUuid) {
        return sql.queryList(
            "SELECT * FROM regions WHERE created_by = ? ORDER BY world_name, name",
            this::mapRow,
            creatorUuid.toString()
        );
    }

    /**
     * Sauvegarde une région (insert ou update).
     */
    public CompletableFuture<RegionImpl> save(@NotNull RegionImpl region) {
        RegionShape shape = region.getShape();
        BoundingBox bounds = shape.getEnclosingBounds();

        // Paramètres spécifiques au cylindre
        Integer centerX = null, centerY = null, centerZ = null, radius = null, height = null;
        Double radiusAdjust = null;

        if (shape instanceof CylinderShape cylinder) {
            centerX = cylinder.getCenterX();
            centerY = cylinder.getCenterY();
            centerZ = cylinder.getCenterZ();
            radius = cylinder.getRadius();
            height = cylinder.getHeight();
            radiusAdjust = cylinder.getRadiusAdjust();
        }

        if (region.getId() > 0) {
            // Update
            return sql.execute("""
                UPDATE regions SET
                    name = ?,
                    world_name = ?,
                    min_x = ?, min_y = ?, min_z = ?,
                    max_x = ?, max_y = ?, max_z = ?,
                    priority = ?,
                    shape_type = ?,
                    center_x = ?, center_y = ?, center_z = ?,
                    radius = ?, height = ?, radius_adjust = ?
                WHERE id = ?
            """,
                region.getName(),
                region.getWorldName(),
                bounds.getMinX(),
                bounds.getMinY(),
                bounds.getMinZ(),
                bounds.getMaxX(),
                bounds.getMaxY(),
                bounds.getMaxZ(),
                region.getPriority(),
                shape.getShapeType(),
                centerX, centerY, centerZ,
                radius, height, radiusAdjust,
                region.getId()
            ).thenApply(v -> region);
        } else {
            // Insert
            return sql.executeAndGetId("""
                INSERT INTO regions (name, world_name, min_x, min_y, min_z, max_x, max_y, max_z, priority, created_by, created_at, shape_type, center_x, center_y, center_z, radius, height, radius_adjust)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                region.getName(),
                region.getWorldName(),
                bounds.getMinX(),
                bounds.getMinY(),
                bounds.getMinZ(),
                bounds.getMaxX(),
                bounds.getMaxY(),
                bounds.getMaxZ(),
                region.getPriority(),
                region.getCreatedBy() != null ? region.getCreatedBy().toString() : null,
                region.getCreatedAt(),
                shape.getShapeType(),
                centerX, centerY, centerZ,
                radius, height, radiusAdjust
            ).thenApply(id -> {
                region.setId(id.intValue());
                return region;
            });
        }
    }

    /**
     * Supprime une région par ID.
     */
    public CompletableFuture<Boolean> deleteById(int id) {
        return sql.execute("DELETE FROM regions WHERE id = ?", id)
            .thenApply(v -> true)
            .exceptionally(e -> false);
    }

    /**
     * Supprime une région par nom et monde.
     */
    public CompletableFuture<Boolean> deleteByName(@NotNull String worldName, @NotNull String name) {
        return sql.execute(
            "DELETE FROM regions WHERE world_name = ? AND LOWER(name) = LOWER(?)",
            worldName, name
        ).thenApply(v -> true).exceptionally(e -> false);
    }

    /**
     * Vérifie si une région existe.
     */
    public CompletableFuture<Boolean> exists(@NotNull String worldName, @NotNull String name) {
        return sql.queryExists(
            "SELECT 1 FROM regions WHERE world_name = ? AND LOWER(name) = LOWER(?)",
            worldName, name
        );
    }

    /**
     * Compte le nombre de régions.
     */
    public CompletableFuture<Long> count() {
        return sql.queryLong("SELECT COUNT(*) FROM regions");
    }

    /**
     * Compte le nombre de régions créées par un joueur.
     */
    public CompletableFuture<Long> countByCreator(@NotNull UUID creatorUuid) {
        return sql.queryLong(
            "SELECT COUNT(*) FROM regions WHERE created_by = ?",
            creatorUuid.toString()
        );
    }

    private RegionImpl mapRow(ResultSet rs) {
        try {
            // Utilise ShapeSerializer pour reconstruire la forme
            RegionShape shape = ShapeSerializer.fromResultSet(rs);

            String createdByStr = rs.getString("created_by");
            UUID createdBy = createdByStr != null ? UUID.fromString(createdByStr) : null;

            return new RegionImpl(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("world_name"),
                shape,
                rs.getInt("priority"),
                rs.getLong("created_at"),
                createdBy
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map region row", e);
        }
    }
}
