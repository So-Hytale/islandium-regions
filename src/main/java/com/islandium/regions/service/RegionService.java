package com.islandium.regions.service;

import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.database.RegionFlagRepository;
import com.islandium.regions.database.RegionGroupFlagRepository;
import com.islandium.regions.database.RegionMemberRepository;
import com.islandium.regions.database.RegionPlayerFlagRepository;
import com.islandium.regions.database.RegionRepository;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.model.MemberRole;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.GlobalShape;
import com.islandium.regions.shape.RegionShape;
import com.islandium.regions.spatial.SpatialIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Service principal pour la gestion des régions.
 */
public class RegionService {

    private final RegionsPlugin plugin;
    private final RegionRepository regionRepository;
    private final RegionMemberRepository memberRepository;
    private final RegionFlagRepository flagRepository;
    private final RegionGroupFlagRepository groupFlagRepository;
    private final RegionPlayerFlagRepository playerFlagRepository;
    private final SpatialIndex spatialIndex;

    public RegionService(@NotNull RegionsPlugin plugin,
                         @NotNull RegionRepository regionRepository,
                         @NotNull RegionMemberRepository memberRepository,
                         @NotNull RegionFlagRepository flagRepository,
                         @NotNull RegionGroupFlagRepository groupFlagRepository,
                         @NotNull RegionPlayerFlagRepository playerFlagRepository,
                         @NotNull SpatialIndex spatialIndex) {
        this.plugin = plugin;
        this.regionRepository = regionRepository;
        this.memberRepository = memberRepository;
        this.flagRepository = flagRepository;
        this.groupFlagRepository = groupFlagRepository;
        this.playerFlagRepository = playerFlagRepository;
        this.spatialIndex = spatialIndex;
    }

    /**
     * Charge toutes les régions depuis la base de données dans le cache.
     */
    public CompletableFuture<Void> loadAllRegions() {
        plugin.log(Level.INFO, "Loading all regions...");

        return regionRepository.findAll().thenCompose(regions -> {
            plugin.log(Level.INFO, "Found " + regions.size() + " regions to load");

            // Charger les membres et flags pour chaque région
            CompletableFuture<Void> allLoaded = CompletableFuture.completedFuture(null);

            for (RegionImpl region : regions) {
                allLoaded = allLoaded.thenCompose(v -> loadRegionData(region));
            }

            return allLoaded.thenRun(() -> {
                plugin.log(Level.INFO, "Loaded " + spatialIndex.size() + " regions into spatial index");
            });
        });
    }

    /**
     * Charge les données complètes d'une région (membres + flags).
     */
    private CompletableFuture<Void> loadRegionData(@NotNull RegionImpl region) {
        // Charger les membres
        CompletableFuture<Void> loadMembers = memberRepository.findOwnersByRegion(region.getId())
            .thenCombine(memberRepository.findMembersByRegion(region.getId()), (owners, members) -> {
                for (UUID owner : owners) {
                    region.addOwner(owner);
                }
                for (UUID member : members) {
                    region.addMember(member);
                }
                return null;
            });

        // Charger les flags de région
        CompletableFuture<Void> loadFlags = flagRepository.findByRegion(region.getId())
            .thenAccept(flags -> {
                for (var entry : flags.entrySet()) {
                    region.setFlag(entry.getKey(), entry.getValue());
                }
            });

        // Charger les overrides de groupe
        CompletableFuture<Void> loadGroupFlags = groupFlagRepository.findByRegion(region.getId())
            .thenAccept(groupFlags -> {
                for (var rankEntry : groupFlags.entrySet()) {
                    for (var flagEntry : rankEntry.getValue().entrySet()) {
                        region.setGroupFlag(rankEntry.getKey(), flagEntry.getKey(), flagEntry.getValue());
                    }
                }
            });

        // Charger les overrides de joueur
        CompletableFuture<Void> loadPlayerFlags = playerFlagRepository.findByRegion(region.getId())
            .thenAccept(playerFlags -> {
                for (var playerEntry : playerFlags.entrySet()) {
                    for (var flagEntry : playerEntry.getValue().entrySet()) {
                        region.setPlayerFlag(playerEntry.getKey(), flagEntry.getKey(), flagEntry.getValue());
                    }
                }
            });

        // Quand tout est chargé, ajouter au spatial index
        return CompletableFuture.allOf(loadMembers, loadFlags, loadGroupFlags, loadPlayerFlags)
            .thenRun(() -> spatialIndex.addRegion(region));
    }

    // === Opérations de lecture ===

    /**
     * Obtient une région par son nom dans un monde.
     */
    public CompletableFuture<Optional<RegionImpl>> getRegion(@NotNull String worldName, @NotNull String name) {
        // D'abord chercher dans le cache
        Optional<RegionImpl> cached = spatialIndex.getRegion(worldName, name);
        if (cached.isPresent()) {
            return CompletableFuture.completedFuture(cached);
        }
        // Sinon chercher en BDD (ne devrait pas arriver si le cache est correctement initialisé)
        return regionRepository.findByName(worldName, name);
    }

    /**
     * Obtient toutes les régions à une position.
     */
    @NotNull
    public List<RegionImpl> getRegionsAt(@NotNull String worldName, int x, int y, int z) {
        return spatialIndex.getRegionsAt(worldName, x, y, z);
    }

    /**
     * Obtient la région de plus haute priorité à une position.
     */
    @NotNull
    public Optional<RegionImpl> getHighestPriorityRegionAt(@NotNull String worldName, int x, int y, int z) {
        return spatialIndex.getHighestPriorityRegionAt(worldName, x, y, z);
    }

    /**
     * Obtient toutes les régions d'un monde.
     */
    @NotNull
    public List<RegionImpl> getRegionsByWorld(@NotNull String worldName) {
        return spatialIndex.getRegionsByWorld(worldName);
    }

    /**
     * Obtient toutes les régions.
     */
    @NotNull
    public List<RegionImpl> getAllRegions() {
        return spatialIndex.getAllRegions();
    }

    // === Opérations de création/modification ===

    /**
     * Crée une nouvelle région avec une forme géométrique.
     */
    public CompletableFuture<RegionImpl> createRegion(@NotNull String name, @NotNull String worldName, @NotNull RegionShape shape, @Nullable UUID createdBy) {
        RegionImpl region = new RegionImpl(
            0, name, worldName, shape, 0,
            System.currentTimeMillis(), createdBy
        );

        // Si un créateur est spécifié, l'ajouter comme owner
        if (createdBy != null) {
            region.addOwner(createdBy);
        }

        return regionRepository.save(region).thenCompose(saved -> {
            // Sauvegarder le owner
            CompletableFuture<Void> saveOwner;
            if (createdBy != null) {
                saveOwner = memberRepository.addMember(saved.getId(), createdBy, MemberRole.OWNER, null);
            } else {
                saveOwner = CompletableFuture.completedFuture(null);
            }

            return saveOwner.thenApply(v -> {
                spatialIndex.addRegion(saved);
                plugin.log(Level.INFO, "Created region '" + name + "' (" + shape.getShapeType() + ") in world '" + worldName + "'");
                return saved;
            });
        });
    }

    /**
     * Crée une nouvelle région avec une forme géométrique et une priorité.
     */
    public CompletableFuture<RegionImpl> createRegion(@NotNull String name, @NotNull String worldName, @NotNull RegionShape shape, @Nullable UUID createdBy, int priority) {
        RegionImpl region = new RegionImpl(
            0, name, worldName, shape, priority,
            System.currentTimeMillis(), createdBy
        );

        // Si un créateur est spécifié, l'ajouter comme owner
        if (createdBy != null) {
            region.addOwner(createdBy);
        }

        return regionRepository.save(region).thenCompose(saved -> {
            // Sauvegarder le owner
            CompletableFuture<Void> saveOwner;
            if (createdBy != null) {
                saveOwner = memberRepository.addMember(saved.getId(), createdBy, MemberRole.OWNER, null);
            } else {
                saveOwner = CompletableFuture.completedFuture(null);
            }

            return saveOwner.thenApply(v -> {
                spatialIndex.addRegion(saved);
                plugin.log(Level.INFO, "Created region '" + name + "' (" + shape.getShapeType() + ") in world '" + worldName + "' with priority " + priority);
                return saved;
            });
        });
    }

    /**
     * Crée une nouvelle région cubique (compatibilité avec l'ancienne API).
     */
    public CompletableFuture<RegionImpl> createRegion(@NotNull String name, @NotNull String worldName, @NotNull BoundingBox bounds, @Nullable UUID createdBy) {
        return createRegion(name, worldName, new CuboidShape(bounds), createdBy);
    }

    /**
     * Nom réservé pour la région globale.
     */
    public static final String GLOBAL_REGION_NAME = "__global__";

    /**
     * Priorité de la région globale (très basse pour que les autres régions aient la priorité).
     */
    public static final int GLOBAL_REGION_PRIORITY = -1000;

    /**
     * Crée ou obtient la région globale pour un monde.
     * La région globale couvre tout le monde et a une priorité très basse.
     * Elle permet de définir des règles par défaut pour tout le monde.
     *
     * @param worldName Nom du monde
     * @return La région globale (existante ou nouvellement créée)
     */
    public CompletableFuture<RegionImpl> getOrCreateGlobalRegion(@NotNull String worldName) {
        return getRegion(worldName, GLOBAL_REGION_NAME).thenCompose(existingOpt -> {
            if (existingOpt.isPresent()) {
                return CompletableFuture.completedFuture(existingOpt.get());
            }

            // Créer la région globale
            GlobalShape globalShape = new GlobalShape();
            RegionImpl region = new RegionImpl(
                0, GLOBAL_REGION_NAME, worldName, globalShape, GLOBAL_REGION_PRIORITY,
                System.currentTimeMillis(), null
            );

            return regionRepository.save(region).thenApply(saved -> {
                spatialIndex.addRegion(saved);
                plugin.log(Level.INFO, "Created global region for world '" + worldName + "'");
                return saved;
            });
        });
    }

    /**
     * Obtient la région globale d'un monde si elle existe.
     */
    @NotNull
    public Optional<RegionImpl> getGlobalRegion(@NotNull String worldName) {
        return spatialIndex.getRegion(worldName, GLOBAL_REGION_NAME);
    }

    /**
     * Vérifie si une région est la région globale.
     */
    public static boolean isGlobalRegion(@NotNull RegionImpl region) {
        return GLOBAL_REGION_NAME.equals(region.getName());
    }

    /**
     * Supprime une région.
     */
    public CompletableFuture<Boolean> deleteRegion(@NotNull String worldName, @NotNull String name) {
        return getRegion(worldName, name).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }

            RegionImpl region = regionOpt.get();
            spatialIndex.removeRegion(region);

            // Les membres et flags sont supprimés automatiquement via ON DELETE CASCADE
            return regionRepository.deleteById(region.getId()).thenApply(success -> {
                if (success) {
                    plugin.log(Level.INFO, "Deleted region '" + name + "' from world '" + worldName + "'");
                }
                return success;
            });
        });
    }

    /**
     * Redéfinit la forme d'une région.
     */
    public CompletableFuture<RegionImpl> redefineRegion(@NotNull RegionImpl region, @NotNull RegionShape newShape) {
        region.setShape(newShape);
        return regionRepository.save(region).thenApply(saved -> {
            spatialIndex.updateRegion(saved);
            return saved;
        });
    }

    /**
     * Redéfinit les limites d'une région cubique (compatibilité).
     */
    public CompletableFuture<RegionImpl> redefineRegion(@NotNull RegionImpl region, @NotNull BoundingBox newBounds) {
        return redefineRegion(region, new CuboidShape(newBounds));
    }

    /**
     * Définit la priorité d'une région.
     */
    public CompletableFuture<RegionImpl> setPriority(@NotNull RegionImpl region, int priority) {
        region.setPriority(priority);
        return regionRepository.save(region).thenApply(saved -> {
            // La priorité n'affecte pas l'index spatial, juste le tri
            return saved;
        });
    }

    // === Gestion des membres ===

    /**
     * Ajoute un propriétaire à une région.
     */
    public CompletableFuture<Void> addOwner(@NotNull RegionImpl region, @NotNull UUID playerUuid, @Nullable UUID addedBy) {
        region.addOwner(playerUuid);
        return memberRepository.addMember(region.getId(), playerUuid, MemberRole.OWNER, addedBy);
    }

    /**
     * Supprime un propriétaire d'une région.
     */
    public CompletableFuture<Void> removeOwner(@NotNull RegionImpl region, @NotNull UUID playerUuid) {
        region.removeOwner(playerUuid);
        return memberRepository.removeMember(region.getId(), playerUuid);
    }

    /**
     * Ajoute un membre à une région.
     */
    public CompletableFuture<Void> addMember(@NotNull RegionImpl region, @NotNull UUID playerUuid, @Nullable UUID addedBy) {
        region.addMember(playerUuid);
        return memberRepository.addMember(region.getId(), playerUuid, MemberRole.MEMBER, addedBy);
    }

    /**
     * Supprime un membre d'une région.
     */
    public CompletableFuture<Void> removeMember(@NotNull RegionImpl region, @NotNull UUID playerUuid) {
        region.removeMember(playerUuid);
        return memberRepository.removeMember(region.getId(), playerUuid);
    }

    // === Gestion des flags ===

    /**
     * Définit un flag sur une région.
     */
    public CompletableFuture<Void> setFlag(@NotNull RegionImpl region, @NotNull RegionFlag flag, @Nullable Object value) {
        region.setFlag(flag, value);
        return flagRepository.setFlag(region.getId(), flag, value);
    }

    /**
     * Supprime un flag d'une région.
     */
    public CompletableFuture<Void> clearFlag(@NotNull RegionImpl region, @NotNull RegionFlag flag) {
        region.clearFlag(flag);
        return flagRepository.clearFlag(region.getId(), flag);
    }

    // === Gestion des flags de groupe ===

    /**
     * Définit un override de flag pour un groupe (rank) sur une région.
     */
    public CompletableFuture<Void> setGroupFlag(@NotNull RegionImpl region, @NotNull String rankName,
                                                 @NotNull RegionFlag flag, @Nullable Object value) {
        region.setGroupFlag(rankName, flag, value);
        return groupFlagRepository.setFlag(region.getId(), rankName, flag, value);
    }

    /**
     * Supprime un override de flag pour un groupe sur une région.
     */
    public CompletableFuture<Void> clearGroupFlag(@NotNull RegionImpl region, @NotNull String rankName,
                                                   @NotNull RegionFlag flag) {
        region.setGroupFlag(rankName, flag, null);
        return groupFlagRepository.clearFlag(region.getId(), rankName, flag);
    }

    /**
     * Supprime tous les overrides d'un groupe sur une région.
     */
    public CompletableFuture<Void> clearGroupFlags(@NotNull RegionImpl region, @NotNull String rankName) {
        region.getOverrideCache().clearGroupOverrides(rankName);
        return groupFlagRepository.clearByRank(region.getId(), rankName);
    }

    /**
     * Obtient le repository des flags de groupe.
     */
    @NotNull
    public RegionGroupFlagRepository getGroupFlagRepository() {
        return groupFlagRepository;
    }

    // === Gestion des flags de joueur ===

    /**
     * Définit un override de flag pour un joueur sur une région.
     */
    public CompletableFuture<Void> setPlayerFlag(@NotNull RegionImpl region, @NotNull UUID playerUuid,
                                                  @NotNull RegionFlag flag, @Nullable Object value) {
        region.setPlayerFlag(playerUuid, flag, value);
        return playerFlagRepository.setFlag(region.getId(), playerUuid, flag, value);
    }

    /**
     * Supprime un override de flag pour un joueur sur une région.
     */
    public CompletableFuture<Void> clearPlayerFlag(@NotNull RegionImpl region, @NotNull UUID playerUuid,
                                                    @NotNull RegionFlag flag) {
        region.setPlayerFlag(playerUuid, flag, null);
        return playerFlagRepository.clearFlag(region.getId(), playerUuid, flag);
    }

    /**
     * Supprime tous les overrides d'un joueur sur une région.
     */
    public CompletableFuture<Void> clearPlayerFlags(@NotNull RegionImpl region, @NotNull UUID playerUuid) {
        region.getOverrideCache().clearPlayerOverrides(playerUuid);
        return playerFlagRepository.clearByPlayer(region.getId(), playerUuid);
    }

    /**
     * Obtient le repository des flags de joueur.
     */
    @NotNull
    public RegionPlayerFlagRepository getPlayerFlagRepository() {
        return playerFlagRepository;
    }

    // === Vérifications de permissions ===

    /**
     * Vérifie si un joueur peut casser un bloc à une position.
     */
    public boolean canBreak(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true; // Pas de région = autorisé
        }

        // Vérifier la région de plus haute priorité
        RegionImpl region = regions.get(0);
        return region.canBreak(playerUuid);
    }

    /**
     * Vérifie si un joueur peut placer un bloc à une position.
     */
    public boolean canPlace(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        return region.canPlace(playerUuid);
    }

    /**
     * Vérifie si un joueur peut interagir à une position.
     */
    public boolean canInteract(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        return region.canInteract(playerUuid);
    }

    /**
     * Vérifie si le PvP est autorisé à une position.
     */
    public boolean isPvPAllowed(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        Boolean pvp = region.getFlag(RegionFlag.PVP);
        return pvp == null || pvp;
    }

    /**
     * Vérifie si un joueur est invincible à une position (flag INVINCIBLE).
     */
    public boolean isInvincible(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return false;
        }

        RegionImpl region = regions.get(0);
        Boolean invincible = region.getFlag(RegionFlag.INVINCIBLE);
        return invincible != null && invincible;
    }

    /**
     * Vérifie si les dégâts de mob sont autorisés à une position.
     */
    public boolean isMobDamageAllowed(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        Boolean mobDamage = region.getFlag(RegionFlag.MOB_DAMAGE);
        return mobDamage == null || mobDamage;
    }

    /**
     * Vérifie si le spawn de mobs est autorisé à une position.
     */
    public boolean isMobSpawningAllowed(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        Boolean mobSpawning = region.getFlag(RegionFlag.MOB_SPAWNING);
        return mobSpawning == null || mobSpawning;
    }

    /**
     * Vérifie si le drop d'items est autorisé pour un joueur à une position.
     */
    public boolean isItemDropAllowed(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        // Les membres peuvent toujours drop
        if (region.isMember(playerUuid)) {
            return true;
        }

        Boolean itemDrop = region.getFlag(RegionFlag.ITEM_DROP);
        return itemDrop == null || itemDrop;
    }

    /**
     * Vérifie si le ramassage d'items est autorisé pour un joueur à une position.
     */
    public boolean isItemPickupAllowed(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        // Les membres peuvent toujours ramasser
        if (region.isMember(playerUuid)) {
            return true;
        }

        Boolean itemPickup = region.getFlag(RegionFlag.ITEM_PICKUP);
        return itemPickup == null || itemPickup;
    }

    /**
     * Vérifie si l'entrée est autorisée pour un joueur dans une région.
     */
    public boolean isEntryAllowed(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        // Les membres peuvent toujours entrer
        if (region.isMember(playerUuid)) {
            return true;
        }

        Boolean entry = region.getFlag(RegionFlag.ENTRY);
        return entry == null || entry;
    }

    /**
     * Vérifie si la sortie est autorisée pour un joueur d'une région.
     */
    public boolean isExitAllowed(@NotNull String worldName, int x, int y, int z, @NotNull UUID playerUuid) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        // Les membres peuvent toujours sortir
        if (region.isMember(playerUuid)) {
            return true;
        }

        Boolean exit = region.getFlag(RegionFlag.EXIT);
        return exit == null || exit;
    }

    /**
     * Vérifie si la téléportation est autorisée à une position.
     */
    public boolean isTeleportAllowed(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return true;
        }

        RegionImpl region = regions.get(0);
        Boolean teleport = region.getFlag(RegionFlag.TELEPORT);
        return teleport == null || teleport;
    }

    /**
     * Obtient le message de bienvenue d'une région.
     */
    @Nullable
    public String getGreetingMessage(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return null;
        }

        RegionImpl region = regions.get(0);
        return region.getFlag(RegionFlag.GREETING_MESSAGE);
    }

    /**
     * Obtient le message d'au revoir d'une région.
     */
    @Nullable
    public String getFarewellMessage(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            return null;
        }

        RegionImpl region = regions.get(0);
        return region.getFlag(RegionFlag.FAREWELL_MESSAGE);
    }

    /**
     * Obtient le spatial index (pour les listeners).
     */
    @NotNull
    public SpatialIndex getSpatialIndex() {
        return spatialIndex;
    }
}
