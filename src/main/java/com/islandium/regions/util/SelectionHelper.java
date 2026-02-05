package com.islandium.regions.util;

import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.prefab.selection.standard.BlockSelection;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.RegionShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Helper pour récupérer la sélection du joueur via BuilderTools.
 *
 * Les joueurs utilisent /pos1 et /pos2 pour définir leur sélection.
 */
public final class SelectionHelper {

    private SelectionHelper() {}

    /**
     * Obtient le PlayerRef depuis un Player.
     */
    @Nullable
    public static PlayerRef getPlayerRef(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                return null;
            }
            var store = ref.getStore();
            return store.getComponent(ref, PlayerRef.getComponentType());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtient la sélection actuelle du joueur.
     */
    @Nullable
    public static BlockSelection getSelection(@NotNull Player player) {
        try {
            PlayerRef playerRef = getPlayerRef(player);
            if (playerRef == null) {
                return null;
            }
            BuilderToolsPlugin.BuilderState state = BuilderToolsPlugin.getState(player, playerRef);
            if (state == null) {
                return null;
            }
            return state.getSelection();
        } catch (NoClassDefFoundError | Exception e) {
            // BuilderTools n'est pas disponible ou erreur
            return null;
        }
    }

    /**
     * Vérifie si le joueur a une sélection valide.
     */
    public static boolean hasValidSelection(@NotNull Player player) {
        BlockSelection selection = getSelection(player);
        return selection != null && selection.hasSelectionBounds();
    }

    /**
     * Obtient le point minimum de la sélection.
     */
    @Nullable
    public static Vector3i getSelectionMin(@NotNull Player player) {
        BlockSelection selection = getSelection(player);
        if (selection == null || !selection.hasSelectionBounds()) {
            return null;
        }
        return selection.getSelectionMin();
    }

    /**
     * Obtient le point maximum de la sélection.
     */
    @Nullable
    public static Vector3i getSelectionMax(@NotNull Player player) {
        BlockSelection selection = getSelection(player);
        if (selection == null || !selection.hasSelectionBounds()) {
            return null;
        }
        return selection.getSelectionMax();
    }

    /**
     * Convertit la sélection du joueur en BoundingBox.
     */
    @NotNull
    public static Optional<BoundingBox> getSelectionAsBoundingBox(@NotNull Player player) {
        Vector3i min = getSelectionMin(player);
        Vector3i max = getSelectionMax(player);

        if (min == null || max == null) {
            return Optional.empty();
        }

        return Optional.of(new BoundingBox(min, max));
    }

    /**
     * Convertit la sélection du joueur en RegionShape (CuboidShape).
     * Pour les cylindres, utiliser CylinderShape directement avec les paramètres appropriés.
     */
    @NotNull
    public static Optional<RegionShape> getSelectionAsShape(@NotNull Player player) {
        return getSelectionAsBoundingBox(player)
            .map(CuboidShape::new);
    }

    /**
     * Calcule le volume de la sélection actuelle.
     */
    public static long getSelectionVolume(@NotNull Player player) {
        return getSelectionAsBoundingBox(player)
            .map(BoundingBox::getVolume)
            .orElse(0L);
    }

    /**
     * Définit la sélection du joueur à partir d'une BoundingBox.
     * Utilise l'API BuilderTools pour définir pos1/pos2.
     *
     * @param player Le joueur
     * @param bounds La bounding box à utiliser comme sélection
     * @return true si la sélection a été définie avec succès
     */
    public static boolean setSelection(@NotNull Player player, @NotNull BoundingBox bounds) {
        try {
            PlayerRef playerRef = getPlayerRef(player);
            if (playerRef == null) {
                return false;
            }

            BuilderToolsPlugin.BuilderState state = BuilderToolsPlugin.getState(player, playerRef);
            if (state == null) {
                return false;
            }

            BlockSelection selection = state.getSelection();
            if (selection == null) {
                return false;
            }

            // Définir les positions min et max via l'API BlockSelection
            Vector3i min = new Vector3i(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ());
            Vector3i max = new Vector3i(bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());

            // Utiliser setSelectionArea qui est la méthode correcte
            selection.setSelectionArea(min, max);

            return true;
        } catch (NoClassDefFoundError | Exception e) {
            // BuilderTools n'est pas disponible ou erreur
            return false;
        }
    }

    /**
     * Définit la sélection du joueur à partir de deux points.
     *
     * @param player Le joueur
     * @param pos1 Premier point (min)
     * @param pos2 Second point (max)
     * @return true si la sélection a été définie avec succès
     */
    public static boolean setSelection(@NotNull Player player, @NotNull Vector3i pos1, @NotNull Vector3i pos2) {
        try {
            PlayerRef playerRef = getPlayerRef(player);
            if (playerRef == null) {
                return false;
            }

            BuilderToolsPlugin.BuilderState state = BuilderToolsPlugin.getState(player, playerRef);
            if (state == null) {
                return false;
            }

            BlockSelection selection = state.getSelection();
            if (selection == null) {
                return false;
            }

            selection.setSelectionArea(pos1, pos2);
            return true;
        } catch (NoClassDefFoundError | Exception e) {
            return false;
        }
    }
}
