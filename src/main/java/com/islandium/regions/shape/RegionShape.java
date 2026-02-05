package com.islandium.regions.shape;

import com.islandium.regions.model.BoundingBox;
import org.jetbrains.annotations.NotNull;

/**
 * Interface d'abstraction pour les formes geometriques de regions.
 * Chaque forme doit fournir un test de contenance precis et un AABB englobant
 * pour l'indexation spatiale par chunks.
 */
public interface RegionShape {

    /**
     * Identifiant unique du type de forme pour la serialisation.
     * Ex: "cuboid", "cylinder"
     */
    @NotNull
    String getShapeType();

    /**
     * Verifie si un point est contenu dans cette forme.
     */
    boolean contains(int x, int y, int z);

    /**
     * Retourne le bounding box (AABB) englobant cette forme.
     * Utilise par le spatial index pour le filtrage par chunks.
     */
    @NotNull
    BoundingBox getEnclosingBounds();

    /**
     * Volume approximatif en blocs.
     */
    long getVolume();

    /**
     * Description lisible pour l'affichage dans les commandes/UI.
     */
    @NotNull
    String getDescription();

    /**
     * Coordonnee X du centre de la forme.
     */
    int getCenterX();

    /**
     * Coordonnee Y du centre (ou base pour cylindre) de la forme.
     */
    int getCenterY();

    /**
     * Coordonnee Z du centre de la forme.
     */
    int getCenterZ();
}
