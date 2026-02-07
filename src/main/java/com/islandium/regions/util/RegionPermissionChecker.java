package com.islandium.regions.util;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.permission.Rank;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utilitaire pour vérifier les permissions dans les régions.
 * Le bypass est géré par un toggle manuel par joueur (via le GUI).
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

    // === Bypass toggle par joueur ===
    private static final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();

    /**
     * Active ou désactive le mode bypass pour un joueur.
     * @return true si le bypass est maintenant actif, false sinon
     */
    public static boolean toggleBypass(UUID playerUuid) {
        if (bypassPlayers.contains(playerUuid)) {
            bypassPlayers.remove(playerUuid);
            return false;
        } else {
            bypassPlayers.add(playerUuid);
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
     * Désactive le bypass pour un joueur (ex: à la déconnexion).
     */
    public static void removeBypassing(UUID playerUuid) {
        bypassPlayers.remove(playerUuid);
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
     *
     * @param region La région à vérifier
     * @param player Le joueur (peut être null)
     * @param playerUuid UUID du joueur (utilisé si player est null)
     * @param primaryFlag Le flag principal à vérifier
     * @param fallbackFlag Le flag de fallback si le principal n'est pas défini (peut être null)
     * @param playerPermissions Permissions du joueur pour la résolution par groupe (peut être null)
     * @return Le résultat de la vérification
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
            // Trier les ranks par priorité décroissante
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

        // Si flag non défini ou ALLOW (true), autoriser
        if (flagValue == null || Boolean.TRUE.equals(flagValue)) {
            return PermissionResult.ALLOWED;
        }

        // Flag est soit FALSE (deny all), soit "members" (deny non-members)
        boolean membersOnly = "members".equals(flagValue);
        boolean denyAll = Boolean.FALSE.equals(flagValue);

        if (!membersOnly && !denyAll) {
            return PermissionResult.ALLOWED;
        }

        // Pour "members", vérifier si le joueur est membre
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
     * Plus de bypass OP ni Creative - seul le toggle bypass passe avant.
     */
    private static PermissionResult evaluateFlagValue(
            @Nullable Object flagValue,
            @NotNull RegionImpl region,
            @Nullable UUID uuid) {

        // Si flag non défini ou ALLOW (true), autoriser
        if (flagValue == null || Boolean.TRUE.equals(flagValue)) {
            return PermissionResult.ALLOWED;
        }

        // Flag est soit FALSE (deny all), soit "members" (deny non-members)
        boolean membersOnly = "members".equals(flagValue);
        boolean denyAll = Boolean.FALSE.equals(flagValue);

        if (!membersOnly && !denyAll) {
            return PermissionResult.ALLOWED;
        }

        // Members bypass - autoriser si le flag est "members" et le joueur est membre
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
     * Version avec UUID uniquement (pour les cas où on n'a pas accès au Player).
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
