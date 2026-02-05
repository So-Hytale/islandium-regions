package com.islandium.regions.database;

import com.islandium.core.database.SQLExecutor;
import com.islandium.regions.model.MemberRole;
import com.islandium.regions.model.RegionMember;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Repository pour les membres de régions.
 */
public class RegionMemberRepository {

    private final SQLExecutor sql;

    public RegionMemberRepository(@NotNull SQLExecutor sql) {
        this.sql = sql;
    }

    /**
     * Crée la table des membres.
     */
    public CompletableFuture<Void> createTables() {
        return sql.execute("""
            CREATE TABLE IF NOT EXISTS region_members (
                id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                region_id INT UNSIGNED NOT NULL,
                player_uuid CHAR(36) NOT NULL,
                role ENUM('OWNER', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
                added_at BIGINT NOT NULL,
                added_by CHAR(36),
                UNIQUE KEY uk_region_player (region_id, player_uuid),
                FOREIGN KEY (region_id) REFERENCES regions(id) ON DELETE CASCADE,
                INDEX idx_player (player_uuid),
                INDEX idx_region (region_id)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
    }

    /**
     * Liste tous les membres d'une région.
     */
    public CompletableFuture<List<RegionMember>> findByRegion(int regionId) {
        return sql.queryList(
            "SELECT * FROM region_members WHERE region_id = ? ORDER BY role, added_at",
            this::mapRow,
            regionId
        );
    }

    /**
     * Liste les propriétaires d'une région.
     */
    public CompletableFuture<Set<UUID>> findOwnersByRegion(int regionId) {
        return sql.queryList(
            "SELECT player_uuid FROM region_members WHERE region_id = ? AND role = 'OWNER'",
            rs -> {
                try {
                    return UUID.fromString(rs.getString("player_uuid"));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId
        ).thenApply(HashSet::new);
    }

    /**
     * Liste les membres (non-owners) d'une région.
     */
    public CompletableFuture<Set<UUID>> findMembersByRegion(int regionId) {
        return sql.queryList(
            "SELECT player_uuid FROM region_members WHERE region_id = ? AND role = 'MEMBER'",
            rs -> {
                try {
                    return UUID.fromString(rs.getString("player_uuid"));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId
        ).thenApply(HashSet::new);
    }

    /**
     * Liste toutes les régions où un joueur est membre ou owner.
     */
    public CompletableFuture<List<Integer>> findRegionIdsByPlayer(@NotNull UUID playerUuid) {
        return sql.queryList(
            "SELECT region_id FROM region_members WHERE player_uuid = ?",
            rs -> {
                try {
                    return rs.getInt("region_id");
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            playerUuid.toString()
        );
    }

    /**
     * Ajoute un membre à une région.
     */
    public CompletableFuture<Void> addMember(int regionId, @NotNull UUID playerUuid,
                                             @NotNull MemberRole role, @Nullable UUID addedBy) {
        long now = System.currentTimeMillis();
        return sql.execute("""
            INSERT INTO region_members (region_id, player_uuid, role, added_at, added_by)
            VALUES (?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                role = VALUES(role),
                added_at = VALUES(added_at),
                added_by = VALUES(added_by)
        """,
            regionId,
            playerUuid.toString(),
            role.name(),
            now,
            addedBy != null ? addedBy.toString() : null
        );
    }

    /**
     * Supprime un membre d'une région.
     */
    public CompletableFuture<Void> removeMember(int regionId, @NotNull UUID playerUuid) {
        return sql.execute(
            "DELETE FROM region_members WHERE region_id = ? AND player_uuid = ?",
            regionId,
            playerUuid.toString()
        );
    }

    /**
     * Vérifie si un joueur est membre d'une région.
     */
    public CompletableFuture<Boolean> isMember(int regionId, @NotNull UUID playerUuid) {
        return sql.queryExists(
            "SELECT 1 FROM region_members WHERE region_id = ? AND player_uuid = ?",
            regionId,
            playerUuid.toString()
        );
    }

    /**
     * Vérifie si un joueur est owner d'une région.
     */
    public CompletableFuture<Boolean> isOwner(int regionId, @NotNull UUID playerUuid) {
        return sql.queryExists(
            "SELECT 1 FROM region_members WHERE region_id = ? AND player_uuid = ? AND role = 'OWNER'",
            regionId,
            playerUuid.toString()
        );
    }

    /**
     * Obtient le rôle d'un joueur dans une région.
     */
    public CompletableFuture<Optional<MemberRole>> getRole(int regionId, @NotNull UUID playerUuid) {
        return sql.queryOne(
            "SELECT role FROM region_members WHERE region_id = ? AND player_uuid = ?",
            rs -> {
                try {
                    return MemberRole.valueOf(rs.getString("role"));
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            },
            regionId,
            playerUuid.toString()
        );
    }

    /**
     * Supprime tous les membres d'une région.
     */
    public CompletableFuture<Void> clearByRegion(int regionId) {
        return sql.execute("DELETE FROM region_members WHERE region_id = ?", regionId);
    }

    private RegionMember mapRow(ResultSet rs) {
        try {
            String addedByStr = rs.getString("added_by");
            UUID addedBy = addedByStr != null ? UUID.fromString(addedByStr) : null;

            return new RegionMember(
                rs.getInt("region_id"),
                UUID.fromString(rs.getString("player_uuid")),
                MemberRole.valueOf(rs.getString("role")),
                rs.getLong("added_at"),
                addedBy
            );
        } catch (SQLException e) {
            throw new RuntimeException("Failed to map region member row", e);
        }
    }
}
