package com.islandium.regions;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.database.SQLExecutor;
import com.islandium.core.ui.IslandiumUIRegistry;
import com.islandium.regions.command.RegionCommandManager;
import com.islandium.regions.ui.RegionMainPage;
import com.islandium.regions.database.RegionFlagRepository;
import com.islandium.regions.database.RegionGroupFlagRepository;
import com.islandium.regions.database.RegionMemberRepository;
import com.islandium.regions.database.RegionPlayerFlagRepository;
import com.islandium.regions.database.BypassRepository;
import com.islandium.regions.database.RegionRepository;
import com.islandium.regions.event.BreakBlockEventSystem;
import com.islandium.regions.event.ChangeGameModeEventSystem;
import com.islandium.regions.event.CraftRecipeEventSystem;
import com.islandium.regions.event.DamageBlockEventSystem;
import com.islandium.regions.event.DamageEventSystem;
import com.islandium.regions.event.DiscoverZoneEventSystem;
import com.islandium.regions.event.DropItemEventSystem;
import com.islandium.regions.event.EventTestListener;
import com.islandium.regions.event.EntitySpawnBusListener;
import com.islandium.regions.event.HarvestBlockBusListener;
import com.islandium.regions.event.ItemPickupBusListener;
import com.islandium.regions.event.ItemPickupEventSystem;
import com.islandium.regions.event.PlaceBlockEventSystem;
import com.islandium.regions.event.PlayerMovementTracker;
import com.islandium.regions.event.SwitchActiveSlotEventSystem;
import com.islandium.regions.event.UseBlockEventSystem;
import com.islandium.regions.service.RegionService;
import com.islandium.regions.spatial.ChunkBasedSpatialIndex;
import com.islandium.regions.spatial.SpatialIndex;
import com.islandium.regions.util.RegionPermissionChecker;
import com.islandium.regions.visualization.RegionVisualizationService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Plugin principal de gestion des régions (WorldGuard-like).
 *
 * Fonctionnalités:
 * - Création de régions avec le système de sélection natif (/pos1, /pos2)
 * - Protection des blocs (cassage, placement, interaction)
 * - Système de flags (PvP, build, interact, etc.)
 * - Gestion des membres et propriétaires
 * - Priorité des régions pour les zones superposées
 */
public class RegionsPlugin extends JavaPlugin {

    private static RegionsPlugin instance;

    // Repositories
    private RegionRepository regionRepository;
    private RegionMemberRepository memberRepository;
    private RegionFlagRepository flagRepository;
    private RegionGroupFlagRepository groupFlagRepository;
    private RegionPlayerFlagRepository playerFlagRepository;
    private BypassRepository bypassRepository;

    // Services
    private SpatialIndex spatialIndex;
    private RegionService regionService;
    private RegionVisualizationService visualizationService;

    // Managers
    private RegionCommandManager commandManager;

    // Event listeners
    private PlayerMovementTracker movementTracker;
    private ItemPickupBusListener itemPickupBusListener;
    private HarvestBlockBusListener harvestBlockBusListener;
    private EntitySpawnBusListener entitySpawnBusListener;

    // État
    private String currentWorldName = "world"; // Monde par défaut
    private volatile boolean ready = false; // Plugin prêt ?

    public RegionsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;
        getLogger().at(Level.INFO).log("Regions plugin setup...");
        getLogger().at(Level.INFO).log("Block events registered!");
    }

    @Override
    protected void start() {
        log(Level.INFO, "Starting Regions plugin...");
        scheduleDelayedInit();

        // Enregistrer les systèmes ECS pour les events
        getEntityStoreRegistry().registerSystem(new BreakBlockEventSystem());
        getEntityStoreRegistry().registerSystem(new PlaceBlockEventSystem());
        getEntityStoreRegistry().registerSystem(new UseBlockEventSystem());
        getEntityStoreRegistry().registerSystem(new DamageEventSystem());
        getEntityStoreRegistry().registerSystem(new DropItemEventSystem());
        getEntityStoreRegistry().registerSystem(new ItemPickupEventSystem());
        // Events de test (logs uniquement)
        getEntityStoreRegistry().registerSystem(new CraftRecipeEventSystem());
        getEntityStoreRegistry().registerSystem(new DamageBlockEventSystem());
        getEntityStoreRegistry().registerSystem(new DiscoverZoneEventSystem());
        getEntityStoreRegistry().registerSystem(new SwitchActiveSlotEventSystem());
        getEntityStoreRegistry().registerSystem(new ChangeGameModeEventSystem());
        log(Level.INFO, "ECS Event systems registered (11 listeners)");

        // Enregistrer les listeners non-ECS (player/entity events) pour test
        new EventTestListener(this).register(getEventRegistry());
    }

    /**
     * Schedule l'initialisation avec un délai pour attendre que Core soit chargé.
     */
    private void scheduleDelayedInit() {
        // Utiliser un thread séparé pour attendre que Core soit disponible
        new Thread(() -> {
            int attempts = 0;
            int maxAttempts = 50; // 5 secondes max (50 * 100ms)

            while (attempts < maxAttempts) {
                try {
                    Thread.sleep(100);
                    attempts++;

                    if (IslandiumPlugin.get() != null) {
                        log(Level.INFO, "Islandium Core detected after " + (attempts * 100) + "ms, initializing...");
                        initializePlugin();
                        return;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log(Level.SEVERE, "Initialization thread interrupted");
                    return;
                }
            }

            log(Level.SEVERE, "Islandium Core not found after " + (maxAttempts * 100) + "ms. Regions plugin will not function.");
        }, "Regions-Init").start();
    }

    /**
     * Initialisation réelle du plugin après que Core soit disponible.
     */
    private synchronized void initializePlugin() {
        try {
            // Récupérer le SQLExecutor depuis Islandium Core
            SQLExecutor sql = IslandiumPlugin.get().getDatabaseManager().getExecutor();

            // 3. Initialiser les repositories
            log(Level.INFO, "Initializing repositories...");
            this.regionRepository = new RegionRepository(sql);
            this.memberRepository = new RegionMemberRepository(sql);
            this.flagRepository = new RegionFlagRepository(sql);
            this.groupFlagRepository = new RegionGroupFlagRepository(sql);
            this.playerFlagRepository = new RegionPlayerFlagRepository(sql);
            this.bypassRepository = new BypassRepository(sql);

            // 4. Créer les tables
            log(Level.INFO, "Running database migrations...");
            runMigrations().join();

            // 4b. Charger les bypass depuis la DB
            log(Level.INFO, "Loading bypass states...");
            RegionPermissionChecker.init(bypassRepository);

            // 5. Initialiser le spatial index
            log(Level.INFO, "Initializing spatial index...");
            this.spatialIndex = new ChunkBasedSpatialIndex();

            // 6. Initialiser les services
            log(Level.INFO, "Initializing services...");
            this.regionService = new RegionService(
                this,
                regionRepository,
                memberRepository,
                flagRepository,
                groupFlagRepository,
                playerFlagRepository,
                spatialIndex
            );
            this.visualizationService = new RegionVisualizationService();

            // 7. Charger toutes les régions
            log(Level.INFO, "Loading regions...");
            regionService.loadAllRegions().join();

            // 8. Auto-créer la région globale si elle n'existe pas
            log(Level.INFO, "Ensuring global region exists...");
            regionService.getOrCreateGlobalRegion(currentWorldName).join();
            log(Level.INFO, "Global region ready for world '" + currentWorldName + "'");

            // 9. Enregistrer les commandes
            log(Level.INFO, "Registering commands...");
            log(Level.INFO, "CommandRegistry available: " + (getCommandRegistry() != null));
            this.commandManager = new RegionCommandManager(this);
            commandManager.registerAll();
            log(Level.INFO, "Commands registration completed");

            // 10. Démarrer le movement tracker (pour ENTRY/EXIT et messages)
            log(Level.INFO, "Starting movement tracker...");
            this.movementTracker = new PlayerMovementTracker(this);
            this.movementTracker.start();

            // 10b. Enregistrer le listener ItemPickup sur IslandiumEventBus
            // Intercepte TOUS les pickups via le mixin PlayerGiveItemMixin
            log(Level.INFO, "Registering ItemPickupBusListener...");
            this.itemPickupBusListener = new ItemPickupBusListener(this);
            this.itemPickupBusListener.register();

            // 10c. Enregistrer le listener HarvestBlock sur IslandiumEventBus
            // Intercepte les harvest (touche F) via le mixin BlockHarvestMixin
            log(Level.INFO, "Registering HarvestBlockBusListener...");
            this.harvestBlockBusListener = new HarvestBlockBusListener(this);
            this.harvestBlockBusListener.register();

            // 10d. Enregistrer le listener EntitySpawn sur IslandiumEventBus
            // Intercepte les spawns de mobs via le mixin EntitySpawnMixin
            log(Level.INFO, "Registering EntitySpawnBusListener...");
            this.entitySpawnBusListener = new EntitySpawnBusListener(this);
            this.entitySpawnBusListener.register();

            // 11. Enregistrer dans le menu principal
            log(Level.INFO, "Registering in main menu...");
            registerMenuEntry();

            // Marquer le plugin comme prêt
            this.ready = true;

            log(Level.INFO, "Regions plugin initialized successfully! Loaded " + spatialIndex.size() + " regions.");

        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize Regions plugin: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Exécute les migrations de base de données.
     */
    private CompletableFuture<Void> runMigrations() {
        return regionRepository.createTables()
            .thenCompose(v -> memberRepository.createTables())
            .thenCompose(v -> flagRepository.createTables())
            .thenCompose(v -> groupFlagRepository.createTables())
            .thenCompose(v -> playerFlagRepository.createTables())
            .thenCompose(v -> bypassRepository.createTables())
            .thenRun(() -> log(Level.INFO, "Database migrations completed!"));
    }

    private void registerMenuEntry() {
        // Bouton menu desactive pour le moment
        // IslandiumUIRegistry.getInstance().register(new IslandiumUIRegistry.Entry(
        //         "regions",
        //         "REGIONS",
        //         "Protection et gestion des zones",
        //         "#4fc3f7",
        //         playerRef -> new RegionMainPage(playerRef, this, currentWorldName),
        //         false
        // ));
    }

    /**
     * Arrête le plugin.
     */
    public void teardown() {
        log(Level.INFO, "Shutting down Regions plugin...");

        // Arrêter les listeners
        if (itemPickupBusListener != null) {
            itemPickupBusListener.unregister();
        }
        if (harvestBlockBusListener != null) {
            harvestBlockBusListener.unregister();
        }
        if (entitySpawnBusListener != null) {
            entitySpawnBusListener.unregister();
        }

        // Arrêter le movement tracker
        if (movementTracker != null) {
            movementTracker.stop();
        }

        this.ready = false;
        log(Level.INFO, "Regions plugin shut down successfully!");
        instance = null;
    }

    // === Getters ===

    @NotNull
    public static RegionsPlugin get() {
        return instance;
    }

    @NotNull
    public RegionRepository getRegionRepository() {
        return regionRepository;
    }

    @NotNull
    public RegionMemberRepository getMemberRepository() {
        return memberRepository;
    }

    @NotNull
    public RegionFlagRepository getFlagRepository() {
        return flagRepository;
    }

    @NotNull
    public RegionGroupFlagRepository getGroupFlagRepository() {
        return groupFlagRepository;
    }

    @NotNull
    public RegionPlayerFlagRepository getPlayerFlagRepository() {
        return playerFlagRepository;
    }

    @NotNull
    public SpatialIndex getSpatialIndex() {
        return spatialIndex;
    }

    @NotNull
    public RegionService getRegionService() {
        return regionService;
    }

    /**
     * Obtient le service de visualisation des régions.
     */
    @NotNull
    public RegionVisualizationService getVisualizationService() {
        return visualizationService;
    }

    /**
     * Obtient le nom du monde actuel (pour les listeners qui n'ont pas de contexte de monde).
     * Dans une vraie implémentation, ceci devrait être obtenu du contexte de l'événement.
     */
    @Nullable
    public String getCurrentWorldName() {
        return currentWorldName;
    }

    public void setCurrentWorldName(@NotNull String worldName) {
        this.currentWorldName = worldName;
    }

    /**
     * Obtient le movement tracker.
     */
    @Nullable
    public PlayerMovementTracker getMovementTracker() {
        return movementTracker;
    }

    /**
     * Vérifie si le plugin est prêt.
     */
    public boolean isReady() {
        return ready;
    }

    // === Logging ===

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("Regions");

    public void log(Level level, String message) {
        LOGGER.log(level, "[Regions] " + message);
    }

    public void log(Level level, String message, Object... args) {
        LOGGER.log(level, "[Regions] " + String.format(message.replace("{}", "%s"), args));
    }
}
