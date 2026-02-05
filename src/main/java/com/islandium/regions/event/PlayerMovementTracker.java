package com.islandium.regions.event;

import com.islandium.core.api.util.ColorUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Traque les mouvements des joueurs pour détecter les entrées/sorties de régions.
 * Utilise un système de polling car l'API Hytale ne fournit pas d'événement de mouvement.
 *
 * NOTE: Cette classe nécessite une méthode getOnlinePlayers() dans RegionsPlugin
 * qui doit retourner une collection de PlayerRef. L'implémentation dépend de l'API
 * Hytale disponible.
 *
 * Fonctionnalités:
 * - Détection des entrées/sorties de régions
 * - Vérification des flags ENTRY et EXIT
 * - Envoi des messages GREETING_MESSAGE et FAREWELL_MESSAGE
 */
public class PlayerMovementTracker {

    private static final long POLL_INTERVAL_MS = 250; // 250ms = 4 fois par seconde

    private final RegionsPlugin plugin;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> pollTask;

    // Régions actuelles par joueur (UUID joueur -> Set d'IDs de région)
    private final Map<UUID, Set<Integer>> playerCurrentRegions = new ConcurrentHashMap<>();

    // Position précédente par joueur (pour détecter le mouvement)
    private final Map<UUID, int[]> playerLastPosition = new ConcurrentHashMap<>();

    public PlayerMovementTracker(@NotNull RegionsPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "RegionMovementTracker");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Démarre le tracking des mouvements.
     */
    public void start() {
        if (pollTask != null && !pollTask.isCancelled()) {
            return;
        }

        // Le polling est désactivé pour l'instant car l'API getOnlinePlayers() n'est pas disponible
        // pollTask = scheduler.scheduleAtFixedRate(this::pollPlayers, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        plugin.log(Level.INFO, "[PlayerMovementTracker] Started (polling disabled - waiting for Hytale API)");
    }

    /**
     * Arrête le tracking des mouvements.
     */
    public void stop() {
        if (pollTask != null) {
            pollTask.cancel(false);
            pollTask = null;
        }
        playerCurrentRegions.clear();
        playerLastPosition.clear();
        plugin.log(Level.INFO, "[PlayerMovementTracker] Stopped");
    }

    /**
     * Supprime un joueur du tracking (lors de la déconnexion).
     */
    public void removePlayer(@NotNull UUID playerUuid) {
        playerCurrentRegions.remove(playerUuid);
        playerLastPosition.remove(playerUuid);
    }

    /**
     * Met à jour manuellement la position d'un joueur.
     * Cette méthode peut être appelée depuis un autre système qui connaît la position.
     *
     * @param playerUuid UUID du joueur
     * @param worldName Nom du monde
     * @param x Position X
     * @param y Position Y
     * @param z Position Z
     */
    public void updatePlayerPosition(@NotNull UUID playerUuid, @NotNull String worldName, int x, int y, int z) {
        RegionService regionService = plugin.getRegionService();
        if (regionService == null) {
            return;
        }

        // Vérifier si le joueur a bougé
        int[] lastPos = playerLastPosition.get(playerUuid);
        if (lastPos != null && lastPos[0] == x && lastPos[1] == y && lastPos[2] == z) {
            return; // Pas de mouvement
        }
        playerLastPosition.put(playerUuid, new int[]{x, y, z});

        // Récupérer les régions actuelles
        Set<Integer> oldRegions = playerCurrentRegions.getOrDefault(playerUuid, Collections.emptySet());
        List<RegionImpl> currentRegionsList = regionService.getRegionsAt(worldName, x, y, z);

        Set<Integer> newRegions = new HashSet<>();
        for (RegionImpl region : currentRegionsList) {
            newRegions.add(region.getId());
        }

        // Calculer les entrées et sorties
        Set<Integer> entered = new HashSet<>(newRegions);
        entered.removeAll(oldRegions);

        Set<Integer> exited = new HashSet<>(oldRegions);
        exited.removeAll(newRegions);

        // Traiter les entrées
        for (Integer regionId : entered) {
            RegionImpl region = findRegionById(currentRegionsList, regionId);
            if (region != null) {
                handleRegionEntry(playerUuid, region, worldName, x, y, z, regionService, lastPos);
            }
        }

        // Traiter les sorties
        for (Integer regionId : exited) {
            Optional<RegionImpl> regionOpt = findRegionByIdInWorld(regionService, worldName, regionId);
            regionOpt.ifPresent(region -> handleRegionExit(playerUuid, region, worldName, regionService));
        }

        // Mettre à jour le cache
        if (newRegions.isEmpty()) {
            playerCurrentRegions.remove(playerUuid);
        } else {
            playerCurrentRegions.put(playerUuid, newRegions);
        }
    }

    /**
     * Gère l'entrée dans une région.
     */
    private void handleRegionEntry(@NotNull UUID playerUuid, @NotNull RegionImpl region,
                                   @NotNull String worldName, int x, int y, int z,
                                   @NotNull RegionService regionService, int[] lastPos) {

        // Vérifier si l'entrée est autorisée
        if (!regionService.isEntryAllowed(worldName, x, y, z, playerUuid)) {
            // Note: La téléportation nécessite une référence au joueur
            // Pour l'instant, on log juste l'événement
            plugin.log(Level.FINE, "[Movement] Entry DENIED for player " + playerUuid + " in region: " + region.getName());
            return;
        }

        // Envoyer le message de bienvenue
        String greeting = regionService.getGreetingMessage(worldName, x, y, z);
        if (greeting != null && !greeting.isEmpty()) {
            // Note: L'envoi de message nécessite une référence au joueur
            plugin.log(Level.FINE, "[Movement] Would send greeting to " + playerUuid + ": " + greeting);
        }

        plugin.log(Level.FINE, "[Movement] Player " + playerUuid + " entered region: " + region.getName());
    }

    /**
     * Gère la sortie d'une région.
     */
    private void handleRegionExit(@NotNull UUID playerUuid, @NotNull RegionImpl region,
                                  @NotNull String worldName, @NotNull RegionService regionService) {

        int cx = region.getShape().getCenterX();
        int cy = region.getShape().getCenterY();
        int cz = region.getShape().getCenterZ();

        // Vérifier si la sortie est autorisée
        if (!regionService.isExitAllowed(worldName, cx, cy, cz, playerUuid)) {
            // Note: La téléportation nécessite une référence au joueur
            plugin.log(Level.FINE, "[Movement] Exit DENIED for player " + playerUuid + " from region: " + region.getName());
            return;
        }

        // Envoyer le message d'au revoir
        String farewell = regionService.getFarewellMessage(worldName, cx, cy, cz);
        if (farewell != null && !farewell.isEmpty()) {
            // Note: L'envoi de message nécessite une référence au joueur
            plugin.log(Level.FINE, "[Movement] Would send farewell to " + playerUuid + ": " + farewell);
        }

        plugin.log(Level.FINE, "[Movement] Player " + playerUuid + " exited region: " + region.getName());
    }

    /**
     * Trouve une région par ID dans une liste.
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
     * Trouve une région par ID dans le monde.
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
     * Vérifie si le tracker est actif.
     */
    public boolean isRunning() {
        return pollTask != null && !pollTask.isCancelled();
    }

    /**
     * Obtient les régions actuelles d'un joueur.
     */
    @NotNull
    public Set<Integer> getPlayerRegions(@NotNull UUID playerUuid) {
        return playerCurrentRegions.getOrDefault(playerUuid, Collections.emptySet());
    }
}
