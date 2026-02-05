package com.islandium.regions.model;

import com.islandium.regions.flag.RegionFlag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache en mémoire pour les overrides de flags par groupe et par joueur.
 * Thread-safe grâce à l'utilisation de ConcurrentHashMap.
 */
public class FlagOverrideCache {

    // Rank name (lowercase) -> (Flag -> Value)
    private final Map<String, Map<RegionFlag, Object>> groupOverrides = new ConcurrentHashMap<>();

    // Player UUID -> (Flag -> Value)
    private final Map<UUID, Map<RegionFlag, Object>> playerOverrides = new ConcurrentHashMap<>();

    // ==================== GROUP OVERRIDES ====================

    /**
     * Définit un override de flag pour un groupe.
     */
    public void setGroupOverride(@NotNull String rankName, @NotNull RegionFlag flag, @Nullable Object value) {
        String key = rankName.toLowerCase();
        if (value == null) {
            Map<RegionFlag, Object> rankFlags = groupOverrides.get(key);
            if (rankFlags != null) {
                rankFlags.remove(flag);
                if (rankFlags.isEmpty()) {
                    groupOverrides.remove(key);
                }
            }
        } else {
            groupOverrides.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(flag, value);
        }
    }

    /**
     * Obtient un override de flag pour un groupe.
     * @return Optional.empty() si pas d'override défini
     */
    public Optional<Object> getGroupOverride(@NotNull String rankName, @NotNull RegionFlag flag) {
        Map<RegionFlag, Object> rankFlags = groupOverrides.get(rankName.toLowerCase());
        if (rankFlags == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(rankFlags.get(flag));
    }

    /**
     * Vérifie si un groupe a un override pour un flag.
     */
    public boolean hasGroupOverride(@NotNull String rankName, @NotNull RegionFlag flag) {
        Map<RegionFlag, Object> rankFlags = groupOverrides.get(rankName.toLowerCase());
        return rankFlags != null && rankFlags.containsKey(flag);
    }

    /**
     * Obtient tous les overrides d'un groupe.
     */
    @NotNull
    public Map<RegionFlag, Object> getGroupOverrides(@NotNull String rankName) {
        Map<RegionFlag, Object> rankFlags = groupOverrides.get(rankName.toLowerCase());
        return rankFlags != null ? new HashMap<>(rankFlags) : Collections.emptyMap();
    }

    /**
     * Obtient tous les overrides de groupe.
     * @return Map<RankName, Map<Flag, Value>>
     */
    @NotNull
    public Map<String, Map<RegionFlag, Object>> getAllGroupOverrides() {
        Map<String, Map<RegionFlag, Object>> result = new HashMap<>();
        for (Map.Entry<String, Map<RegionFlag, Object>> entry : groupOverrides.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Obtient la liste des groupes qui ont des overrides.
     */
    @NotNull
    public Set<String> getGroupsWithOverrides() {
        return new HashSet<>(groupOverrides.keySet());
    }

    /**
     * Supprime tous les overrides d'un groupe.
     */
    public void clearGroupOverrides(@NotNull String rankName) {
        groupOverrides.remove(rankName.toLowerCase());
    }

    // ==================== PLAYER OVERRIDES ====================

    /**
     * Définit un override de flag pour un joueur.
     */
    public void setPlayerOverride(@NotNull UUID playerUuid, @NotNull RegionFlag flag, @Nullable Object value) {
        if (value == null) {
            Map<RegionFlag, Object> playerFlags = playerOverrides.get(playerUuid);
            if (playerFlags != null) {
                playerFlags.remove(flag);
                if (playerFlags.isEmpty()) {
                    playerOverrides.remove(playerUuid);
                }
            }
        } else {
            playerOverrides.computeIfAbsent(playerUuid, k -> new ConcurrentHashMap<>()).put(flag, value);
        }
    }

    /**
     * Obtient un override de flag pour un joueur.
     * @return Optional.empty() si pas d'override défini
     */
    public Optional<Object> getPlayerOverride(@NotNull UUID playerUuid, @NotNull RegionFlag flag) {
        Map<RegionFlag, Object> playerFlags = playerOverrides.get(playerUuid);
        if (playerFlags == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(playerFlags.get(flag));
    }

    /**
     * Vérifie si un joueur a un override pour un flag.
     */
    public boolean hasPlayerOverride(@NotNull UUID playerUuid, @NotNull RegionFlag flag) {
        Map<RegionFlag, Object> playerFlags = playerOverrides.get(playerUuid);
        return playerFlags != null && playerFlags.containsKey(flag);
    }

    /**
     * Obtient tous les overrides d'un joueur.
     */
    @NotNull
    public Map<RegionFlag, Object> getPlayerOverrides(@NotNull UUID playerUuid) {
        Map<RegionFlag, Object> playerFlags = playerOverrides.get(playerUuid);
        return playerFlags != null ? new HashMap<>(playerFlags) : Collections.emptyMap();
    }

    /**
     * Obtient tous les overrides de joueur.
     * @return Map<PlayerUUID, Map<Flag, Value>>
     */
    @NotNull
    public Map<UUID, Map<RegionFlag, Object>> getAllPlayerOverrides() {
        Map<UUID, Map<RegionFlag, Object>> result = new HashMap<>();
        for (Map.Entry<UUID, Map<RegionFlag, Object>> entry : playerOverrides.entrySet()) {
            result.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return result;
    }

    /**
     * Obtient la liste des joueurs qui ont des overrides.
     */
    @NotNull
    public Set<UUID> getPlayersWithOverrides() {
        return new HashSet<>(playerOverrides.keySet());
    }

    /**
     * Supprime tous les overrides d'un joueur.
     */
    public void clearPlayerOverrides(@NotNull UUID playerUuid) {
        playerOverrides.remove(playerUuid);
    }

    // ==================== GLOBAL OPERATIONS ====================

    /**
     * Vide tout le cache.
     */
    public void clear() {
        groupOverrides.clear();
        playerOverrides.clear();
    }

    /**
     * Vérifie si le cache est vide.
     */
    public boolean isEmpty() {
        return groupOverrides.isEmpty() && playerOverrides.isEmpty();
    }

    /**
     * Obtient le nombre total d'overrides (groupes + joueurs).
     */
    public int getTotalOverrideCount() {
        int count = 0;
        for (Map<RegionFlag, Object> flags : groupOverrides.values()) {
            count += flags.size();
        }
        for (Map<RegionFlag, Object> flags : playerOverrides.values()) {
            count += flags.size();
        }
        return count;
    }

    @Override
    public String toString() {
        return "FlagOverrideCache{" +
                "groupOverrides=" + groupOverrides.size() + " ranks" +
                ", playerOverrides=" + playerOverrides.size() + " players" +
                ", totalFlags=" + getTotalOverrideCount() +
                '}';
    }
}
