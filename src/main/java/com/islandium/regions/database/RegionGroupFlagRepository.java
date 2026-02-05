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
 * Repository pour les flags de régions par groupe (rank).
 * Permet de définir des overrides de flags pour des ranks spécifiques.
 */
public class RegionGroupFlagRepository {

    private final SQLExecutor sql;

    public RegionGroupFlagRepository(@NotNull SQLExecutor sql) {
        this.sql = sql;
    }

    /**
     * Crée la table des flags par groupe.
     */
    public CompletableFuture<Void> createTables() {
        return sql.execute("""
            CREATE TABLE IF NOT EXISTS region_group_flags (
                id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                region_id INT UNSIGNED NOT NULL,
                rank_name VARCHAR(64) NOT NULL,
                flag_name VARCHAR(64) NOT NULL,
                flag_value VARCHAR(255) NOT NULL,
                UNIQUE KEY uk_region_rank_flag (region_id, rank_name, flag_name),
                FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE,
                INDEX idx_region (region_id),
                INDEX idx_rank (rank_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
    }

    /**
     * Obtient tous les flags de groupe d'une région.
     * @return Map<RankName, Map<Flag, Value>>
     */
    public CompletableFuture<Map<String, Map<RegionFlag, Object>>> findByRegion(int regionId) {
        return sql.queryList(
            "SELECT rank_name, flag_name, flag_value FROM region_group_flags WHERE region_id = ?",
            this::mapRow,
            regionId
        ).thenApply(list -> {
            Map<String, Map<RegionFlag, Object>> result = new HashMap<>();
            for (GroupFlagEntry entry : list) {
                if (entry != null && entry.flag != null) {
                    result.computeIfAbsent(entry.rankName, k -> new HashMap<>())
                          .put(entry.flag, entry.value);
                }
            }
            return result;
        });
    }

    /**
     * Obtient les flags d'un rank spécifique pour une région.
     */
    public CompletableFuture<Map<RegionFlag, Object>> findByRegionAndRank(int regionId, @NotNull String rankName) {
        return sql.queryList(
            "SELECT flag_name, flag_value FROM region_group_flags WHERE region_id = ? AND rank_name = ?",
            rs -> {
                try {
                    String flagName = rs.getString("flag_name");
                    String flagValue = rs.getString("flag_value");
                    RegionFlag flag = RegionFlag.fromName(flagName);
                    if (flag == null) return null;
                    return new SimpleFlagEntry(flag, flag.parseValue(flagValue));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId,
            rankName.toLowerCase()
        ).thenApply(list -> {
            Map<RegionFlag, Object> flags = new HashMap<>();
            for (SimpleFlagEntry entry : list) {
                if (entry != null && entry.flag != null) {
                    flags.put(entry.flag, entry.value);
                }
            }
            return flags;
        });
    }

    /**
     * Définit un flag pour un rank sur une région.
     */
    public CompletableFuture<Void> setFlag(int regionId, @NotNull String rankName, @NotNull RegionFlag flag, @Nullable Object value) {
        if (value == null) {
            return clearFlag(regionId, rankName, flag);
        }

        String serialized = flag.getType().serialize(value);
        if (serialized == null) {
            return clearFlag(regionId, rankName, flag);
        }

        return sql.execute("""
            INSERT INTO region_group_flags (region_id, rank_name, flag_name, flag_value)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)
        """,
            regionId,
            rankName.toLowerCase(),
            flag.getName(),
            serialized
        );
    }

    /**
     * Supprime un flag d'un rank sur une région.
     */
    public CompletableFuture<Void> clearFlag(int regionId, @NotNull String rankName, @NotNull RegionFlag flag) {
        return sql.execute(
            "DELETE FROM region_group_flags WHERE region_id = ? AND rank_name = ? AND flag_name = ?",
            regionId,
            rankName.toLowerCase(),
            flag.getName()
        );
    }

    /**
     * Supprime tous les flags de groupe d'une région.
     */
    public CompletableFuture<Void> clearByRegion(int regionId) {
        return sql.execute("DELETE FROM region_group_flags WHERE region_id = ?", regionId);
    }

    /**
     * Supprime tous les flags d'un rank spécifique sur une région.
     */
    public CompletableFuture<Void> clearByRank(int regionId, @NotNull String rankName) {
        return sql.execute(
            "DELETE FROM region_group_flags WHERE region_id = ? AND rank_name = ?",
            regionId,
            rankName.toLowerCase()
        );
    }

    /**
     * Obtient la liste des ranks qui ont des overrides sur une région.
     */
    public CompletableFuture<java.util.Set<String>> getRanksWithOverrides(int regionId) {
        return sql.queryList(
            "SELECT DISTINCT rank_name FROM region_group_flags WHERE region_id = ?",
            rs -> {
                try {
                    return rs.getString("rank_name");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId
        ).thenApply(list -> new java.util.HashSet<>(list));
    }

    private GroupFlagEntry mapRow(ResultSet rs) {
        try {
            String rankName = rs.getString("rank_name");
            String flagName = rs.getString("flag_name");
            String flagValue = rs.getString("flag_value");

            RegionFlag flag = RegionFlag.fromName(flagName);
            if (flag == null) {
                return null;
            }

            Object value = flag.parseValue(flagValue);
            return new GroupFlagEntry(rankName, flag, value);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map group flag row", e);
        }
    }

    private record GroupFlagEntry(String rankName, RegionFlag flag, Object value) {}
    private record SimpleFlagEntry(RegionFlag flag, Object value) {}
}
