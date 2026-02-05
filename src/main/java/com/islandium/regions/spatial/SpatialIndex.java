package com.islandium.regions.spatial;

import com.islandium.regions.model.RegionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Interface pour l'index spatial des régions.
 */
public interface SpatialIndex {

    /**
     * Ajoute une région à l'index.
     */
    void addRegion(@NotNull RegionImpl region);

    /**
     * Supprime une région de l'index.
     */
    void removeRegion(@NotNull RegionImpl region);

    /**
     * Met à jour une région dans l'index (après modification des bounds).
     */
    void updateRegion(@NotNull RegionImpl region);

    /**
     * Obtient toutes les régions à une position donnée, triées par priorité décroissante.
     */
    @NotNull
    List<RegionImpl> getRegionsAt(@NotNull String worldName, int x, int y, int z);

    /**
     * Obtient la région de plus haute priorité à une position donnée.
     */
    @NotNull
    Optional<RegionImpl> getHighestPriorityRegionAt(@NotNull String worldName, int x, int y, int z);

    /**
     * Obtient une région par son nom dans un monde.
     */
    @NotNull
    Optional<RegionImpl> getRegion(@NotNull String worldName, @NotNull String name);

    /**
     * Obtient toutes les régions d'un monde.
     */
    @NotNull
    List<RegionImpl> getRegionsByWorld(@NotNull String worldName);

    /**
     * Obtient toutes les régions.
     */
    @NotNull
    List<RegionImpl> getAllRegions();

    /**
     * Vide l'index.
     */
    void clear();

    /**
     * Nombre total de régions dans l'index.
     */
    int size();
}
