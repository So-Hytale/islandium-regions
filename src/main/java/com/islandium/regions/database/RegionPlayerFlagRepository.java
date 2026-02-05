package com.islandium.regions.database;

import com.islandium.core.database.SQLExecutor;
import com.islandium.regions.flag.RegionFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Repository pour les flags de régions par joueur.
 * Permet de définir des overrides de flags pour des joueurs spécifiques.
 */
public class RegionPlayerFlagRepository {

    private final SQLExecutor sql;

    public RegionPlayerFlagRepository(@NotNull SQLExecutor sql) {
        this.sql = sql;
    }

    /**
     * Crée la table des flags par joueur.
     */
    public CompletableFuture<Void> createTables() {
        return sql.execute("""
            CREATE TABLE IF NOT EXISTS region_player_flags (
                id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                region_id INT UNSIGNED NOT NULL,
                player_uuid CHAR(36) NOT NULL,
                flag_name VARCHAR(64) NOT NULL,
                flag_value VARCHAR(255) NOT NULL,
                UNIQUE KEY uk_region_player_flag (region_id, player_uuid, flag_name),
                FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE,
                INDEX idx_region (region_id),
                INDEX idx_player (player_uuid)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
    }

    /**
     * Obtient tous les flags de joueur d'une région.
     * @return Map<PlayerUUID, Map<Flag, Value>>
     */
    public CompletableFuture<Map<UUID, Map<RegionFlag, Object>>> findByRegion(int regionId) {
        return sql.queryList(
            "SELECT player_uuid, flag_name, flag_value FROM region_player_flags WHERE region_id = ?",
            this::mapRow,
            regionId
        ).thenApply(list -> {
            Map<UUID, Map<RegionFlag, Object>> result = new HashMap<>();
            for (PlayerFlagEntry entry : list) {
                if (entry != null && entry.flag != null) {
                    result.computeIfAbsent(entry.playerUuid, k -> new HashMap<>())
                          .put(entry.flag, entry.value);
                }
            }
            return result;
        });
    }

    /**
     * Obtient les flags d'un joueur spécifique pour une région.
     */
    public CompletableFuture<Map<RegionFlag, Object>> findByRegionAndPlayer(int regionId, @NotNull UUID playerUuid) {
        return sql.queryList(
            "SELECT flag_name, flag_value FROM region_player_flags WHERE region_id = ? AND player_uuid = ?",
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
            playerUuid.toString()
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
     * Définit un flag pour un joueur sur une région.
     */
    public CompletableFuture<Void> setFlag(int regionId, @NotNull UUID playerUuid, @NotNull RegionFlag flag, @Nullable Object value) {
        if (value == null) {
            return clearFlag(regionId, playerUuid, flag);
        }

        String serialized = flag.getType().serialize(value);
        if (serialized == null) {
            return clearFlag(regionId, playerUuid, flag);
        }

        return sql.execute("""
            INSERT INTO region_player_flags (region_id, player_uuid, flag_name, flag_value)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE flag_value = VALUES(flag_value)
        """,
            regionId,
            playerUuid.toString(),
            flag.getName(),
            serialized
        );
    }

    /**
     * Supprime un flag d'un joueur sur une région.
     */
    public CompletableFuture<Void> clearFlag(int regionId, @NotNull UUID playerUuid, @NotNull RegionFlag flag) {
        return sql.execute(
            "DELETE FROM region_player_flags WHERE region_id = ? AND player_uuid = ? AND flag_name = ?",
            regionId,
            playerUuid.toString(),
            flag.getName()
        );
    }

    /**
     * Supprime tous les flags de joueur d'une région.
     */
    public CompletableFuture<Void> clearByRegion(int regionId) {
        return sql.execute("DELETE FROM region_player_flags WHERE region_id = ?", regionId);
    }

    /**
     * Supprime tous les flags d'un joueur spécifique sur une région.
     */
    public CompletableFuture<Void> clearByPlayer(int regionId, @NotNull UUID playerUuid) {
        return sql.execute(
            "DELETE FROM region_player_flags WHERE region_id = ? AND player_uuid = ?",
            regionId,
            playerUuid.toString()
        );
    }

    /**
     * Obtient la liste des joueurs qui ont des overrides sur une région.
     */
    public CompletableFuture<java.util.Set<UUID>> getPlayersWithOverrides(int regionId) {
        return sql.queryList(
            "SELECT DISTINCT player_uuid FROM region_player_flags WHERE region_id = ?",
            rs -> {
                try {
                    return UUID.fromString(rs.getString("player_uuid"));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId
        ).thenApply(list -> new java.util.HashSet<>(list));
    }

    private PlayerFlagEntry mapRow(ResultSet rs) {
        try {
            UUID playerUuid = UUID.fromString(rs.getString("player_uuid"));
            String flagName = rs.getString("flag_name");
            String flagValue = rs.getString("flag_value");

            RegionFlag flag = RegionFlag.fromName(flagName);
            if (flag == null) {
                return null;
            }

            Object value = flag.parseValue(flagValue);
            return new PlayerFlagEntry(playerUuid, flag, value);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map player flag row", e);
        }
    }

    private record PlayerFlagEntry(UUID playerUuid, RegionFlag flag, Object value) {}
    private record SimpleFlagEntry(RegionFlag flag, Object value) {}
}
