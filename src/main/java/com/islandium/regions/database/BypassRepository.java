package com.islandium.regions.database;

import com.islandium.core.database.SQLExecutor;
import org.jetbrains.annotations.NotNull;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository pour persister l'état du bypass par joueur.
 * Table simple: si le joueur est présent, le bypass est actif.
 */
public class BypassRepository {

    private final SQLExecutor sql;

    public BypassRepository(@NotNull SQLExecutor sql) {
        this.sql = sql;
    }

    /**
     * Crée la table bypass.
     */
    public CompletableFuture<Void> createTables() {
        return sql.execute("""
            CREATE TABLE IF NOT EXISTS region_bypass_players (
                player_uuid CHAR(36) PRIMARY KEY
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """);
    }

    /**
     * Charge tous les joueurs en bypass depuis la DB.
     * @return Set de UUID des joueurs avec bypass actif
     */
    public CompletableFuture<Set<UUID>> loadAll() {
        return sql.queryList(
            "SELECT player_uuid FROM region_bypass_players",
            this::mapRow
        ).thenApply(list -> {
            Set<UUID> result = ConcurrentHashMap.newKeySet();
            for (UUID uuid : list) {
                if (uuid != null) {
                    result.add(uuid);
                }
            }
            return result;
        });
    }

    /**
     * Ajoute un joueur en bypass.
     */
    public CompletableFuture<Void> addBypass(@NotNull UUID playerUuid) {
        return sql.execute("""
            INSERT IGNORE INTO region_bypass_players (player_uuid) VALUES (?)
        """, playerUuid.toString());
    }

    /**
     * Retire un joueur du bypass.
     */
    public CompletableFuture<Void> removeBypass(@NotNull UUID playerUuid) {
        return sql.execute(
            "DELETE FROM region_bypass_players WHERE player_uuid = ?",
            playerUuid.toString()
        );
    }

    private UUID mapRow(ResultSet rs) {
        try {
            String uuidStr = rs.getString("player_uuid");
            return uuidStr != null ? UUID.fromString(uuidStr) : null;
        } catch (SQLException | IllegalArgumentException e) {
            return null;
        }
    }
}
