package com.islandium.regions.event;

import com.islandium.regions.RegionsPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Gère les événements de drop d'items pour le flag ITEM_DROP.
 *
 * NOTE: Cette classe est un STUB en attente de l'API Hytale.
 * L'event ItemDropEvent n'existe pas encore dans l'API.
 *
 * Quand l'API sera disponible, implémenter EntityEventSystem<EntityStore, ItemDropEvent>
 * et vérifier le flag ITEM_DROP.
 */
public class ItemDropEventListener {

    private final RegionsPlugin plugin;

    public ItemDropEventListener(@NotNull RegionsPlugin plugin) {
        this.plugin = plugin;
        plugin.log(java.util.logging.Level.INFO, "[ItemDropEventListener] Stub created - waiting for Hytale API ItemDropEvent");
    }

    /**
     * A implémenter quand l'API ItemDropEvent sera disponible.
     *
     * Logique prévue:
     * 1. Récupérer joueur qui drop
     * 2. Récupérer sa position
     * 3. Si joueur est membre de la région => ALLOW
     * 4. Sinon vérifier flag ITEM_DROP
     * 5. Si flag = false => CANCEL
     */
    // public void handle(...) { }
}
