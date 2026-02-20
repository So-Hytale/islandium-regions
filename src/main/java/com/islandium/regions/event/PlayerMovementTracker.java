package com.islandium.regions.event;

import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import com.islandium.regions.util.RegionPermissionChecker;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Traque les mouvements des joueurs pour detecter les entrees/sorties de regions.
 * Utilise un systeme de polling via IslandiumPlugin.getOnlinePlayersLocal().
 *
 * Fonctionnalites:
 * - Detection des entrees/sorties de regions
 * - Verification des flags ENTRY et EXIT
 * - Envoi des messages GREETING_MESSAGE et FAREWELL_MESSAGE
 * - Teleportation en arriere si l'entree/sortie est refusee
 */
public class PlayerMovementTracker {

    private static final long POLL_INTERVAL_MS = 250; // 250ms = 4 fois par seconde

    private final RegionsPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollTask;

    // Regions actuelles par joueur (UUID joueur -> Set d'IDs de region)
    private final Map<UUID, Set<Integer>> playerCurrentRegions = new ConcurrentHashMap<>();

    // Position precedente par joueur (pour detecter le mouvement et teleport-back)
    private final Map<UUID, int[]> playerLastPosition = new ConcurrentHashMap<>();

    // Monde du joueur
    private final Map<UUID, String> playerWorld = new ConcurrentHashMap<>();

    public PlayerMovementTracker(@NotNull RegionsPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RegionMovementTracker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Demarre le tracking des mouvements.
     */
    public void start() {
        if (pollTask != null && !pollTask.isCancelled()) {
            return;
        }

        pollTask = scheduler.scheduleAtFixedRate(this::pollPlayers, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        plugin.log(Level.INFO, "[PlayerMovementTracker] Started (polling every " + POLL_INTERVAL_MS + "ms)");
    }

    /**
     * Polling de tous les joueurs en ligne.
     */
    private void pollPlayers() {
        try {
            IslandiumPlugin core = IslandiumPlugin.get();
            if (core == null) return;

            Collection<IslandiumPlayer> players = core.getPlayerManager().getOnlinePlayersLocal();
            for (IslandiumPlayer player : players) {
                try {
                    ServerLocation loc = player.getLocation();
                    if (loc == null) continue;

                    updatePlayerPosition(
                        player,
                        loc.world(),
                        (int) loc.x(),
                        (int) loc.y(),
                        (int) loc.z()
                    );
                } catch (Exception e) {
                    // Silently skip player if location retrieval fails
                }
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "[PlayerMovementTracker] Error polling players: " + e.getMessage());
        }
    }

    /**
     * Arrete le tracking des mouvements.
     */
    public void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        scheduler.shutdown();
        playerCurrentRegions.clear();
        playerLastPosition.clear();
        playerWorld.clear();
        plugin.log(Level.INFO, "[PlayerMovementTracker] Stopped");
    }

    /**
     * Supprime un joueur du tracking (lors de la deconnexion).
     */
    public void removePlayer(@NotNull UUID playerUuid) {
        playerCurrentRegions.remove(playerUuid);
        playerLastPosition.remove(playerUuid);
        playerWorld.remove(playerUuid);
    }

    /**
     * Met a jour la position d'un joueur et detecte les entrees/sorties de regions.
     */
    public void updatePlayerPosition(@NotNull IslandiumPlayer player, @NotNull String worldName, int x, int y, int z) {
        UUID playerUuid = player.getUniqueId();
        RegionService regionService = plugin.getRegionService();
        if (regionService == null) return;

        // Verifier si le joueur a bouge (meme bloc)
        int[] lastPos = playerLastPosition.get(playerUuid);
        String lastWorld = playerWorld.get(playerUuid);
        if (lastPos != null && lastWorld != null && lastWorld.equals(worldName)
                && lastPos[0] == x && lastPos[1] == y && lastPos[2] == z) {
            return; // Pas de mouvement
        }

        // Recuperer les regions actuelles
        Set<Integer> oldRegions = playerCurrentRegions.getOrDefault(playerUuid, Collections.emptySet());
        List<RegionImpl> currentRegionsList = regionService.getRegionsAt(worldName, x, y, z);

        Set<Integer> newRegions = new HashSet<>();
        for (RegionImpl region : currentRegionsList) {
            newRegions.add(region.getId());
        }

        // Calculer les entrees et sorties
        Set<Integer> entered = new HashSet<>(newRegions);
        entered.removeAll(oldRegions);

        Set<Integer> exited = new HashSet<>(oldRegions);
        exited.removeAll(newRegions);

        // Traiter les entrees
        for (Integer regionId : entered) {
            RegionImpl region = findRegionById(currentRegionsList, regionId);
            if (region != null) {
                if (!handleRegionEntry(player, region, worldName, x, y, z, regionService)) {
                    // Entree refusee -> teleporter en arriere et ne pas mettre a jour la position
                    teleportBack(player, lastPos, lastWorld);
                    return;
                }
            }
        }

        // Traiter les sorties
        for (Integer regionId : exited) {
            Optional<RegionImpl> regionOpt = findRegionByIdInWorld(regionService, worldName, regionId);
            if (regionOpt.isPresent()) {
                if (!handleRegionExit(player, regionOpt.get(), worldName, regionService)) {
                    // Sortie refusee -> teleporter en arriere et ne pas mettre a jour la position
                    teleportBack(player, lastPos, lastWorld);
                    return;
                }
            }
        }

        // Mettre a jour le cache
        playerLastPosition.put(playerUuid, new int[]{x, y, z});
        playerWorld.put(playerUuid, worldName);
        if (newRegions.isEmpty()) {
            playerCurrentRegions.remove(playerUuid);
        } else {
            playerCurrentRegions.put(playerUuid, newRegions);
        }
    }

    /**
     * Gere l'entree dans une region.
     * @return true si l'entree est autorisee, false sinon
     */
    private boolean handleRegionEntry(@NotNull IslandiumPlayer player, @NotNull RegionImpl region,
                                      @NotNull String worldName, int x, int y, int z,
                                      @NotNull RegionService regionService) {
        UUID playerUuid = player.getUniqueId();

        // Bypass check
        if (RegionPermissionChecker.isBypassing(playerUuid)) {
            return true;
        }

        // Verifier si l'entree est autorisee
        if (!regionService.isEntryAllowed(worldName, x, y, z, playerUuid)) {
            String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
            player.sendNotification(NotificationType.WARNING,
                "Vous ne pouvez pas entrer dans " + regionDisplayName);
            plugin.log(Level.INFO, "[Movement] Entry DENIED for " + player.getName() + " in region: " + region.getName());
            return false;
        }

        // Envoyer le message de bienvenue
        String greeting = (String) region.getFlag(RegionFlag.GREETING_MESSAGE);
        if (greeting != null && !greeting.isEmpty()) {
            player.sendNotification(NotificationType.INFO, greeting);
        }

        plugin.log(Level.FINE, "[Movement] " + player.getName() + " entered region: " + region.getName());
        return true;
    }

    /**
     * Gere la sortie d'une region.
     * @return true si la sortie est autorisee, false sinon
     */
    private boolean handleRegionExit(@NotNull IslandiumPlayer player, @NotNull RegionImpl region,
                                     @NotNull String worldName, @NotNull RegionService regionService) {
        UUID playerUuid = player.getUniqueId();

        // Bypass check
        if (RegionPermissionChecker.isBypassing(playerUuid)) {
            return true;
        }

        int cx = region.getShape().getCenterX();
        int cy = region.getShape().getCenterY();
        int cz = region.getShape().getCenterZ();

        // Verifier si la sortie est autorisee
        if (!regionService.isExitAllowed(worldName, cx, cy, cz, playerUuid)) {
            String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
            player.sendNotification(NotificationType.WARNING,
                "Vous ne pouvez pas quitter " + regionDisplayName);
            plugin.log(Level.INFO, "[Movement] Exit DENIED for " + player.getName() + " from region: " + region.getName());
            return false;
        }

        // Envoyer le message d'au revoir
        String farewell = (String) region.getFlag(RegionFlag.FAREWELL_MESSAGE);
        if (farewell != null && !farewell.isEmpty()) {
            player.sendNotification(NotificationType.INFO, farewell);
        }

        plugin.log(Level.FINE, "[Movement] " + player.getName() + " exited region: " + region.getName());
        return true;
    }

    /**
     * Teleporte le joueur a sa position precedente (quand entry/exit est refuse).
     */
    private void teleportBack(@NotNull IslandiumPlayer player, int[] lastPos, String lastWorld) {
        if (lastPos == null || lastWorld == null) return;

        try {
            ServerLocation backLoc = ServerLocation.of(
                IslandiumPlugin.get().getServerName(),
                lastWorld,
                lastPos[0] + 0.5, lastPos[1], lastPos[2] + 0.5
            );
            player.teleport(backLoc);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "[Movement] Failed to teleport back " + player.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Trouve une region par ID dans une liste.
     */
    private RegionImpl findRegionById(List<RegionImpl> regions, int id) {
        for (RegionImpl region : regions) {
            if (region.getId() == id) {
                return region;
            }
        }
        return null;
    }

    /**
     * Trouve une region par ID dans le monde.
     */
    private Optional<RegionImpl> findRegionByIdInWorld(RegionService regionService, String worldName, int id) {
        for (RegionImpl region : regionService.getRegionsByWorld(worldName)) {
            if (region.getId() == id) {
                return Optional.of(region);
            }
        }
        return Optional.empty();
    }

    /**
     * Verifie si le tracker est actif.
     */
    public boolean isRunning() {
        return pollTask != null && !pollTask.isCancelled();
    }

    /**
     * Obtient les regions actuelles d'un joueur.
     */
    @NotNull
    public Set<Integer> getPlayerRegions(@NotNull UUID playerUuid) {
        return playerCurrentRegions.getOrDefault(playerUuid, Collections.emptySet());
    }
}
