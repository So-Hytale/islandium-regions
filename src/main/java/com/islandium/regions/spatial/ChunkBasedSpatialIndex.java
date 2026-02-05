package com.islandium.regions.spatial;

import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.shape.GlobalShape;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Index spatial basé sur les chunks pour un lookup O(1).
 *
 * Structure: world -> chunkKey -> Set<Region>
 *
 * Les régions globales sont stockées séparément car elles couvrent tout le monde
 * et ne peuvent pas être indexées par chunk (trop de chunks).
 */
public class ChunkBasedSpatialIndex implements SpatialIndex {

    // world -> chunkKey -> Set<Region>
    private final Map<String, Map<Long, Set<RegionImpl>>> worldChunkIndex = new ConcurrentHashMap<>();

    // world -> regionName (lowercase) -> Region
    private final Map<String, Map<String, RegionImpl>> regionsByName = new ConcurrentHashMap<>();

    // Toutes les régions par ID
    private final Map<Integer, RegionImpl> regionsById = new ConcurrentHashMap<>();

    // Régions globales par monde (stockées séparément, pas indexées par chunk)
    private final Map<String, RegionImpl> globalRegions = new ConcurrentHashMap<>();

    @Override
    public void addRegion(@NotNull RegionImpl region) {
        // Ajouter à l'index par ID
        regionsById.put(region.getId(), region);

        String world = region.getWorldName();

        // Ajouter à l'index par nom
        regionsByName.computeIfAbsent(world, k -> new ConcurrentHashMap<>())
            .put(region.getName().toLowerCase(), region);

        // Les régions globales sont stockées séparément (pas d'indexation par chunk)
        if (region.getShape() instanceof GlobalShape) {
            globalRegions.put(world, region);
            return;
        }

        // Ajouter à l'index par chunk
        addToChunkIndex(region);
    }

    @Override
    public void removeRegion(@NotNull RegionImpl region) {
        // Supprimer de l'index par ID
        regionsById.remove(region.getId());

        String world = region.getWorldName();

        // Supprimer de l'index par nom
        Map<String, RegionImpl> nameMap = regionsByName.get(world);
        if (nameMap != null) {
            nameMap.remove(region.getName().toLowerCase());
            if (nameMap.isEmpty()) {
                regionsByName.remove(world);
            }
        }

        // Les régions globales sont stockées séparément
        if (region.getShape() instanceof GlobalShape) {
            globalRegions.remove(world);
            return;
        }

        // Supprimer de l'index par chunk
        removeFromChunkIndex(region);
    }

    @Override
    public void updateRegion(@NotNull RegionImpl region) {
        // Les régions globales n'ont pas d'index par chunk
        if (region.getShape() instanceof GlobalShape) {
            return;
        }

        // Supprimer des anciens chunks et réajouter
        removeFromChunkIndex(region);
        addToChunkIndex(region);
    }

    private void addToChunkIndex(@NotNull RegionImpl region) {
        String world = region.getWorldName();
        Map<Long, Set<RegionImpl>> chunkIndex = worldChunkIndex.computeIfAbsent(world, k -> new ConcurrentHashMap<>());

        // Calculer les chunks affectés
        int minChunkX = region.getBounds().getMinX() >> 4;
        int maxChunkX = region.getBounds().getMaxX() >> 4;
        int minChunkZ = region.getBounds().getMinZ() >> 4;
        int maxChunkZ = region.getBounds().getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = chunkKey(cx, cz);
                chunkIndex.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet())
                    .add(region);
            }
        }
    }

    private void removeFromChunkIndex(@NotNull RegionImpl region) {
        String world = region.getWorldName();
        Map<Long, Set<RegionImpl>> chunkIndex = worldChunkIndex.get(world);
        if (chunkIndex == null) {
            return;
        }

        int minChunkX = region.getBounds().getMinX() >> 4;
        int maxChunkX = region.getBounds().getMaxX() >> 4;
        int minChunkZ = region.getBounds().getMinZ() >> 4;
        int maxChunkZ = region.getBounds().getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                long chunkKey = chunkKey(cx, cz);
                Set<RegionImpl> regions = chunkIndex.get(chunkKey);
                if (regions != null) {
                    regions.remove(region);
                    if (regions.isEmpty()) {
                        chunkIndex.remove(chunkKey);
                    }
                }
            }
        }

        if (chunkIndex.isEmpty()) {
            worldChunkIndex.remove(world);
        }
    }

    @Override
    @NotNull
    public List<RegionImpl> getRegionsAt(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> result = new ArrayList<>();

        // Chercher dans l'index par chunk
        Map<Long, Set<RegionImpl>> chunkIndex = worldChunkIndex.get(worldName);
        if (chunkIndex != null) {
            long chunkKey = chunkKey(x >> 4, z >> 4);
            Set<RegionImpl> candidates = chunkIndex.get(chunkKey);
            if (candidates != null && !candidates.isEmpty()) {
                // Filtrer par contenance réelle
                for (RegionImpl region : candidates) {
                    if (region.contains(x, y, z)) {
                        result.add(region);
                    }
                }
            }
        }

        // Toujours ajouter la région globale si elle existe (elle contient tout)
        RegionImpl globalRegion = globalRegions.get(worldName);
        if (globalRegion != null) {
            result.add(globalRegion);
        }

        // Trier par priorité décroissante
        result.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        return result;
    }

    @Override
    @NotNull
    public Optional<RegionImpl> getHighestPriorityRegionAt(@NotNull String worldName, int x, int y, int z) {
        List<RegionImpl> regions = getRegionsAt(worldName, x, y, z);
        return regions.isEmpty() ? Optional.empty() : Optional.of(regions.get(0));
    }

    @Override
    @NotNull
    public Optional<RegionImpl> getRegion(@NotNull String worldName, @NotNull String name) {
        Map<String, RegionImpl> nameMap = regionsByName.get(worldName);
        if (nameMap == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(nameMap.get(name.toLowerCase()));
    }

    @Override
    @NotNull
    public List<RegionImpl> getRegionsByWorld(@NotNull String worldName) {
        Map<String, RegionImpl> nameMap = regionsByName.get(worldName);
        if (nameMap == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(nameMap.values());
    }

    @Override
    @NotNull
    public List<RegionImpl> getAllRegions() {
        return new ArrayList<>(regionsById.values());
    }

    @Override
    public void clear() {
        worldChunkIndex.clear();
        regionsByName.clear();
        regionsById.clear();
        globalRegions.clear();
    }

    @Override
    public int size() {
        return regionsById.size();
    }

    /**
     * Génère une clé unique pour un chunk.
     */
    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }
}
