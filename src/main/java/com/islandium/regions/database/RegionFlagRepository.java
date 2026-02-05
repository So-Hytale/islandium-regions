package com.islandium.regions.database;

import com.islandium.core.database.SQLExecutor;
import com.islandium.regions.flag.RegionFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Repository pour les flags de régions.
 */
public class RegionFlagRepository {

    private final SQLExecutor sql;

    public RegionFlagRepository(@NotNull SQLExecutor sql) {
        this.sql = sql;
    }

    /**
     * Crée la table des flags.
     */
    public CompletableFuture<Void> createTables() {
        return sql.execute("""
            CREATE TABLE IF NOT EXISTS region_flags (
                id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                region_id INT UNSIGNED NOT NULL,
                flag_name VARCHAR(64) NOT NULL,
                flag_value VARCHAR(255) NOT NULL,
                UNIQUE KEY uk_region_flag (region_id, flag_name),
                FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE,
                INDEX idx_region (region_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
    }

    /**
     * Obtient tous les flags d'une région.
     */
    public CompletableFuture<Map<RegionFlag, Object>> findByRegion(int regionId) {
        return sql.queryList(
            "SELECT flag_name, flag_value FROM region_flags WHERE region_id = ?",
            this::mapRow,
            regionId
        ).thenApply(list -> {
            Map<RegionFlag, Object> flags = new HashMap<>();
            for (FlagEntry entry : list) {
                if (entry != null && entry.flag != null) {
                    flags.put(entry.flag, entry.value);
                }
            }
            return flags;
        });
    }

    /**
     * Obtient la valeur d'un flag spécifique.
     */
    public CompletableFuture<@Nullable Object> getFlag(int regionId, @NotNull RegionFlag flag) {
        return sql.queryOne(
            "SELECT flag_value FROM region_flags WHERE region_id = ? AND flag_name = ?",
            rs -> {
                try {
                    String value = rs.getString("flag_value");
                    return flag.parseValue(value);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId,
            flag.getName()
        ).thenApply(opt -> opt.orElse(null));
    }

    /**
     * Définit un flag sur une région.
     */
    public CompletableFuture<Void> setFlag(int regionId, @NotNull RegionFlag flag, @Nullable Object value) {
        if (value == null) {
            // Supprimer le flag
            return sql.execute(
                "DELETE FROM region_flags WHERE region_id = ? AND flag_name = ?",
                regionId,
                flag.getName()
            );
        }

        String serialized = flag.getType().serialize(value);
        if (serialized == null) {
            return sql.execute(
                "DELETE FROM region_flags WHERE region_id = ? AND flag_name = ?",
                regionId,
                flag.getName()
            );
        }

        return sql.execute("""
            INSERT INTO region_flags (region_id, flag_name, flag_value)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)
        """,
            regionId,
            flag.getName(),
            serialized
        );
    }

    /**
     * Supprime un flag d'une région.
     */
    public CompletableFuture<Void> clearFlag(int regionId, @NotNull RegionFlag flag) {
        return sql.execute(
            "DELETE FROM region_flags WHERE region_id = ? AND flag_name = ?",
            regionId,
            flag.getName()
        );
    }

    /**
     * Supprime tous les flags d'une région.
     */
    public CompletableFuture<Void> clearByRegion(int regionId) {
        return sql.execute("DELETE FROM region_flags WHERE region_id = ?", regionId);
    }

    /**
     * Sauvegarde tous les flags d'une région (remplace les existants).
     */
    public CompletableFuture<Void> saveAll(int regionId, @NotNull Map<RegionFlag, Object> flags) {
        // Supprimer les anciens flags puis insérer les nouveaux
        return clearByRegion(regionId).thenCompose(v -> {
            if (flags.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }

            // Insérer chaque flag
            CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
            for (Map.Entry<RegionFlag, Object> entry : flags.entrySet()) {
                if (entry.getValue() != null) {
                    result = result.thenCompose(x -> setFlag(regionId, entry.getKey(), entry.getValue()));
                }
            }
            return result;
        });
    }

    private FlagEntry mapRow(ResultSet rs) {
        try {
            String flagName = rs.getString("flag_name");
            String flagValue = rs.getString("flag_value");

            RegionFlag flag = RegionFlag.fromName(flagName);
            if (flag == null) {
                return null;
            }

            Object value = flag.parseValue(flagValue);
            return new FlagEntry(flag, value);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map flag row", e);
        }
    }

    private record FlagEntry(RegionFlag flag, Object value) {}
}
