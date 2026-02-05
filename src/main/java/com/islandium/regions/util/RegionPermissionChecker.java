package com.islandium.regions.util;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.permission.Rank;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Utilitaire pour vérifier les permissions dans les régions.
 * Centralise la logique de bypass (OP, creative, membres).
 *
 * Ordre de résolution des flags (priorité décroissante):
 * 1. Override joueur spécifique
 * 2. Override groupe (rank) - par priorité de rank décroissante
 * 3. Flag de région (valeur par défaut)
 */
public final class RegionPermissionChecker {

    private RegionPermissionChecker() {
        // Utility class
    }

    /**
     * Résultat d'une vérification de permission.
     */
    public enum PermissionResult {
        ALLOWED,    // Action autorisée
        DENIED,     // Action refusée
        BYPASSED    // Action autorisée via bypass (OP, creative, membre)
    }

    /**
     * Vérifie si un joueur peut effectuer une action basée sur un flag.
     * Utilise la résolution par priorité: Joueur > Groupe > Région.
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

        // === 1. Vérifier override JOUEUR (priorité max) ===
        // Les overrides joueur sont ABSOLUS - pas de bypass possible
        if (uuid != null) {
            Object playerOverride = region.getPlayerFlag(uuid, primaryFlag);
            if (playerOverride == null && fallbackFlag != null) {
                playerOverride = region.getPlayerFlag(uuid, fallbackFlag);
            }
            if (playerOverride != null) {
                // Override joueur trouvé - évaluation STRICTE (pas de bypass OP/Creative)
                return evaluateFlagValueStrict(playerOverride, region, uuid);
            }
        }

        // === 2. Vérifier override GROUPE (par priorité de rank décroissante) ===
        // Les overrides groupe sont aussi ABSOLUS - pas de bypass possible
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
                    // Override groupe trouvé - évaluation STRICTE (pas de bypass OP/Creative)
                    return evaluateFlagValueStrict(groupOverride, region, uuid);
                }
            }
        }

        // === 3. Utiliser le flag de RÉGION (fallback) ===
        // Seuls les flags de région peuvent être bypassés par OP/Creative
        Object flagValue = region.getFlags().get(primaryFlag);
        if (flagValue == null && fallbackFlag != null) {
            flagValue = region.getFlags().get(fallbackFlag);
        }

        return evaluateFlagValue(flagValue, region, player, uuid);
    }

    /**
     * Évalue la valeur d'un flag de manière STRICTE (pas de bypass OP/Creative).
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

        // Pas de bypass OP/Creative pour les overrides joueur/groupe
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
     * Évalue la valeur d'un flag et applique les bypass.
     */
    private static PermissionResult evaluateFlagValue(
            @Nullable Object flagValue,
            @NotNull RegionImpl region,
            @Nullable Player player,
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

        // Vérifier les bypass
        if (player != null) {
            // OP bypass - toujours autoriser les joueurs avec permission "*"
            try {
                if (player.hasPermission("*")) {
                    return PermissionResult.BYPASSED;
                }
            } catch (Exception ignored) {
                // Permission check failed
            }

            // Creative bypass - autoriser si le flag est activé
            Boolean creativeBypass = region.getFlag(RegionFlag.CREATIVE_BYPASS);
            if (Boolean.TRUE.equals(creativeBypass)) {
                try {
                    if (player.getGameMode() == GameMode.Creative) {
                        return PermissionResult.BYPASSED;
                    }
                } catch (Exception ignored) {
                    // GameMode check failed
                }
            }

            // Members bypass - autoriser si le flag est "members" et le joueur est membre
            if (membersOnly) {
                try {
                    UUID playerUuid = uuid != null ? uuid : player.getUuid();
                    if (region.isMember(playerUuid)) {
                        return PermissionResult.BYPASSED;
                    }
                } catch (Exception ignored) {
                    // UUID check failed
                }
            }
        } else if (uuid != null) {
            // Seulement UUID disponible - vérifier membres
            if (membersOnly && region.isMember(uuid)) {
                return PermissionResult.BYPASSED;
            }
        }

        return PermissionResult.DENIED;
    }

    /**
     * Version simplifiée pour les cas courants.
     *
     * @param region La région
     * @param player Le joueur
     * @param primaryFlag Le flag principal
     * @param fallbackFlag Le flag de fallback (peut être null)
     * @return true si l'action est autorisée, false sinon
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
     *
     * @param region La région
     * @param player Le joueur
     * @param primaryFlag Le flag principal
     * @param fallbackFlag Le flag de fallback (peut être null)
     * @param playerPermissions Permissions du joueur (peut être null)
     * @return true si l'action est autorisée, false sinon
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
     *
     * @param region La région
     * @param playerUuid UUID du joueur
     * @param primaryFlag Le flag principal
     * @param fallbackFlag Le flag de fallback (peut être null)
     * @return true si l'action est autorisée, false sinon
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
     *
     * @param region La région
     * @param playerUuid UUID du joueur
     * @param primaryFlag Le flag principal
     * @param fallbackFlag Le flag de fallback (peut être null)
     * @param playerPermissions Permissions du joueur (peut être null)
     * @return true si l'action est autorisée, false sinon
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
