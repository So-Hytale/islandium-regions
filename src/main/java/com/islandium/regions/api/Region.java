package com.islandium.regions.api;

import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.shape.RegionShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Interface publique pour une région protégée.
 */
public interface Region {

    /**
     * Obtient l'ID unique de la région.
     */
    int getId();

    /**
     * Obtient le nom de la région.
     */
    @NotNull
    String getName();

    /**
     * Obtient le nom du monde de la région.
     */
    @NotNull
    String getWorldName();

    /**
     * Obtient la forme géométrique de la région.
     */
    @NotNull
    RegionShape getShape();

    /**
     * Obtient les limites englobantes de la région (AABB).
     * Pour un cuboid, c'est la forme elle-même.
     * Pour un cylindre, c'est le rectangle englobant.
     */
    @NotNull
    BoundingBox getBounds();

    /**
     * Obtient la priorité de la région (plus haute = prioritaire).
     */
    int getPriority();

    /**
     * Obtient le timestamp de création.
     */
    long getCreatedAt();

    /**
     * Obtient l'UUID du créateur.
     */
    @Nullable
    UUID getCreatedBy();

    /**
     * Vérifie si un point est dans cette région.
     */
    boolean contains(int x, int y, int z);

    /**
     * Obtient la liste des propriétaires.
     */
    @NotNull
    Set<UUID> getOwners();

    /**
     * Obtient la liste des membres.
     */
    @NotNull
    Set<UUID> getMembers();

    /**
     * Vérifie si un joueur est propriétaire.
     */
    boolean isOwner(@NotNull UUID playerUuid);

    /**
     * Vérifie si un joueur est membre (ou propriétaire).
     */
    boolean isMember(@NotNull UUID playerUuid);

    /**
     * Obtient la valeur d'un flag.
     */
    @Nullable
    <T> T getFlag(@NotNull RegionFlag flag);

    /**
     * Vérifie si un joueur peut construire.
     */
    boolean canBuild(@NotNull UUID playerUuid);

    /**
     * Vérifie si un joueur peut casser des blocs.
     */
    boolean canBreak(@NotNull UUID playerUuid);

    /**
     * Vérifie si un joueur peut placer des blocs.
     */
    boolean canPlace(@NotNull UUID playerUuid);

    /**
     * Vérifie si un joueur peut interagir.
     */
    boolean canInteract(@NotNull UUID playerUuid);
}
