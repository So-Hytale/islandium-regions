package com.islandium.regions.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.permission.Rank;
import com.islandium.regions.database.BypassRepository;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilitaire pour vérifier les permissions dans les régions.
 * Le bypass est géré par un toggle manuel par joueur (via le GUI).
 * L'état bypass est persisté en base de données (survit aux redémarrages).
 *
 * Ordre de résolution des flags (priorité décroissante):
 * 1. Bypass toggle actif (passe tout)
 * 2. Override joueur spécifique
 * 3. Override groupe (rank) - par priorité de rank décroissante
 * 4. Flag de région (valeur par défaut)
 */
public final class RegionPermissionChecker {

    private RegionPermissionChecker() {
        // Utility class
    }

    // === Bypass toggle par joueur (cache mémoire + persistance DB) ===
    private static final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
    private static BypassRepository bypassRepository;

    /**
     * Initialise le système de bypass en chargeant l'état depuis la DB.
     * Doit être appelé au démarrage du plugin après la création des tables.
     */
    public static void init(@NotNull BypassRepository repository) {
        bypassRepository = repository;
        // Charger les joueurs en bypass depuis la DB
        Set<UUID> loaded = repository.loadAll().join();
        bypassPlayers.clear();
        bypassPlayers.addAll(loaded);
    }

    /**
     * Active ou désactive le mode bypass pour un joueur.
     * Persiste le changement en base de données.
     * @return true si le bypass est maintenant actif, false sinon
     */
    public static boolean toggleBypass(UUID playerUuid) {
        if (bypassPlayers.contains(playerUuid)) {
            bypassPlayers.remove(playerUuid);
            // Persister en DB (async, fire-and-forget)
            if (bypassRepository != null) {
                bypassRepository.removeBypass(playerUuid);
            }
            return false;
        } else {
            bypassPlayers.add(playerUuid);
            // Persister en DB (async, fire-and-forget)
            if (bypassRepository != null) {
                bypassRepository.addBypass(playerUuid);
            }
            return true;
        }
    }

    /**
     * Vérifie si un joueur a le mode bypass actif.
     */
    public static boolean isBypassing(UUID playerUuid) {
        return bypassPlayers.contains(playerUuid);
    }

    /**
     * Résultat d'une vérification de permission.
     */
    public enum PermissionResult {
        ALLOWED,    // Action autorisée
        DENIED,     // Action refusée
        BYPASSED    // Action autorisée via bypass (toggle ou membre)
    }

    /**
     * Vérifie si un joueur peut effectuer une action basée sur un flag.
     * Utilise la résolution par priorité: Bypass > Joueur > Groupe > Région.
     */
    public static PermissionResult checkPermission(
            @NotNull RegionImpl region,
            @Nullable Player player,
            @Nullable UUID playerUuid,
            @NotNull RegionFlag primaryFlag,
            @Nullable RegionFlag fallbackFlag,
            @Nullable PlayerPermissions playerPermissions) {

        UUID uuid = playerUuid != null ? playerUuid : (player != null ? player.getUuid() : null);

        // === 0. Vérifier le bypass toggle ===
        if (uuid != null && bypassPlayers.contains(uuid)) {
            return PermissionResult.BYPASSED;
        }

        // === 1. Vérifier override JOUEUR (priorité max) ===
        // Les overrides joueur sont ABSOLUS
        if (uuid != null) {
            Object playerOverride = region.getPlayerFlag(uuid, primaryFlag);
            if (playerOverride == null && fallbackFlag != null) playerOverride = region.getPlayerFlag(uuid, fallbackFlag);
            if (playerOverride != null) return evaluateFlagValueStrict(playerOverride, region, uuid);
        }

        // === 2. Vérifier override GROUPE (par priorité de rank décroissante) ===
        // Les overrides groupe sont aussi ABSOLUS
        if (playerPermissions != null && uuid != null) {
            List<Rank> sortedRanks = playerPermissions.getRanks().stream()
                .sorted(Comparator.comparingInt(Rank::getPriority).reversed())
                .toList();

            for (Rank rank : sortedRanks) {
                Object groupOverride = region.getGroupFlag(rank.getName(), primaryFlag);
                if (groupOverride == null && fallbackFlag != null) {
                    groupOverride = region.getGroupFlag(rank.getName(), fallbackFlag);
                }
                if (groupOverride != null) {
                    return evaluateFlagValueStrict(groupOverride, region, uuid);
                }
            }
        }

        // === 3. Utiliser le flag de RÉGION (fallback) ===
        Object flagValue = region.getFlags().get(primaryFlag);
        if (flagValue == null && fallbackFlag != null) flagValue = region.getFlags().get(fallbackFlag);
        return evaluateFlagValue(flagValue, region, uuid);
    }

    /**
     * Évalue la valeur d'un flag de manière STRICTE (pas de bypass).
     * Utilisé pour les overrides joueur et groupe qui sont absolus.
     */
    private static PermissionResult evaluateFlagValueStrict(
            @Nullable Object flagValue,
            @NotNull RegionImpl region,
            @Nullable UUID uuid) {

        if (flagValue == null || Boolean.TRUE.equals(flagValue)) {
            return PermissionResult.ALLOWED;
        }

        boolean membersOnly = "members".equals(flagValue);
        boolean denyAll = Boolean.FALSE.equals(flagValue);

        if (!membersOnly && !denyAll) {
            return PermissionResult.ALLOWED;
        }

        if (membersOnly && uuid != null && region.isMember(uuid)) {
            return PermissionResult.BYPASSED;
        }

        return PermissionResult.DENIED;
    }

    /**
     * Version rétrocompatible sans PlayerPermissions.
     */
    public static PermissionResult checkPermission(
            @NotNull RegionImpl region,
            @Nullable Player player,
            @Nullable UUID playerUuid,
            @NotNull RegionFlag primaryFlag,
            @Nullable RegionFlag fallbackFlag) {
        return checkPermission(region, player, playerUuid, primaryFlag, fallbackFlag, null);
    }

    /**
     * Évalue la valeur d'un flag avec vérification membres.
     */
    private static PermissionResult evaluateFlagValue(
            @Nullable Object flagValue,
            @NotNull RegionImpl region,
            @Nullable UUID uuid) {

        if (flagValue == null || Boolean.TRUE.equals(flagValue)) {
            return PermissionResult.ALLOWED;
        }

        boolean membersOnly = "members".equals(flagValue);
        boolean denyAll = Boolean.FALSE.equals(flagValue);

        if (!membersOnly && !denyAll) {
            return PermissionResult.ALLOWED;
        }

        if (membersOnly && uuid != null && region.isMember(uuid)) {
            return PermissionResult.BYPASSED;
        }

        return PermissionResult.DENIED;
    }

    /**
     * Version simplifiée pour les cas courants.
     */
    public static boolean isAllowed(
            @NotNull RegionImpl region,
            @Nullable Player player,
            @NotNull RegionFlag primaryFlag,
            @Nullable RegionFlag fallbackFlag) {

        UUID uuid = null;
        if (player != null) {
            try {
                uuid = player.getUuid();
            } catch (Exception ignored) {
            }
        }

        PermissionResult result = checkPermission(region, player, uuid, primaryFlag, fallbackFlag);
        return result != PermissionResult.DENIED;
    }

    /**
     * Version avec PlayerPermissions pour la résolution par groupe.
     */
    public static boolean isAllowed(
            @NotNull RegionImpl region,
            @Nullable Player player,
            @NotNull RegionFlag primaryFlag,
            @Nullable RegionFlag fallbackFlag,
            @Nullable PlayerPermissions playerPermissions) {

        UUID uuid = null;
        if (player != null) {
            try {
                uuid = player.getUuid();
            } catch (Exception ignored) {
            }
        }

        PermissionResult result = checkPermission(region, player, uuid, primaryFlag, fallbackFlag, playerPermissions);
        return result != PermissionResult.DENIED;
    }

    /**
     * Version avec UUID uniquement.
     */
    public static boolean isAllowedByUuid(
            @NotNull RegionImpl region,
            @Nullable UUID playerUuid,
            @NotNull RegionFlag primaryFlag,
            @Nullable RegionFlag fallbackFlag) {

        PermissionResult result = checkPermission(region, null, playerUuid, primaryFlag, fallbackFlag);
        return result != PermissionResult.DENIED;
    }

    /**
     * Version avec UUID et PlayerPermissions.
     */
    public static boolean isAllowedByUuid(
            @NotNull RegionImpl region,
            @Nullable UUID playerUuid,
            @NotNull RegionFlag primaryFlag,
            @Nullable RegionFlag fallbackFlag,
            @Nullable PlayerPermissions playerPermissions) {

        PermissionResult result = checkPermission(region, null, playerUuid, primaryFlag, fallbackFlag, playerPermissions);
        return result != PermissionResult.DENIED;
    }
}
