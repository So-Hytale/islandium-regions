package com.islandium.regions.shape;

import com.islandium.regions.model.BoundingBox;
import org.jetbrains.annotations.NotNull;

/**
 * Forme qui couvre tout le monde (région globale/par défaut).
 * Cette forme contient TOUTES les positions possibles.
 *
 * Utilisée pour créer une région __global__ qui s'applique à tout le monde
 * avec une priorité très basse (-1000) pour que les autres régions aient la priorité.
 *
 * IMPORTANT: Cette forme n'est PAS indexée par chunk dans le spatial index.
 * Elle est stockée séparément et toujours incluse dans les résultats de recherche.
 * Cela évite de créer des millions d'entrées dans l'index.
 */
public class GlobalShape implements RegionShape {

    public static final String TYPE = "global";

    // BoundingBox symbolique (0,0,0) - utilisé uniquement pour la sérialisation DB
    // Ne PAS utiliser pour l'indexation spatiale !
    private static final BoundingBox SYMBOLIC_BOUNDS = new BoundingBox(0, 0, 0, 0, 0, 0);

    public GlobalShape() {
        // Pas de calcul, pas de stockage - forme symbolique
    }

    @Override
    @NotNull
    public String getShapeType() {
        return TYPE;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        // La région globale contient TOUT - pas de calcul
        return true;
    }

    @Override
    @NotNull
    public BoundingBox getEnclosingBounds() {
        // Retourne un bounds symbolique - ne PAS utiliser pour l'indexation !
        return SYMBOLIC_BOUNDS;
    }

    @Override
    public long getVolume() {
        // Infini - pas de calcul
        return Long.MAX_VALUE;
    }

    @Override
    @NotNull
    public String getDescription() {
        return "Global (monde entier)";
    }

    @Override
    public int getCenterX() {
        return 0;
    }

    @Override
    public int getCenterY() {
        return 64; // Niveau de la mer symbolique
    }

    @Override
    public int getCenterZ() {
        return 0;
    }

    @Override
    public String toString() {
        return "GlobalShape{world=entire}";
    }
}
