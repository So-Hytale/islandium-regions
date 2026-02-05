package com.islandium.regions.event;

import com.islandium.regions.RegionsPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Gère les événements de ramassage d'items pour le flag ITEM_PICKUP.
 *
 * NOTE: Cette classe est un STUB en attente de l'API Hytale.
 * L'event ItemPickupEvent n'existe pas encore dans l'API.
 *
 * Quand l'API sera disponible, implémenter EntityEventSystem<EntityStore, ItemPickupEvent>
 * et vérifier le flag ITEM_PICKUP.
 */
public class ItemPickupEventListener {

    private final RegionsPlugin plugin;

    public ItemPickupEventListener(@NotNull RegionsPlugin plugin) {
        this.plugin = plugin;
        plugin.log(java.util.logging.Level.INFO, "[ItemPickupEventListener] Stub created - waiting for Hytale API ItemPickupEvent");
    }

    /**
     * A implémenter quand l'API ItemPickupEvent sera disponible.
     *
     * Logique prévue:
     * 1. Récupérer joueur qui ramasse
     * 2. Récupérer sa position
     * 3. Si joueur est membre de la région => ALLOW
     * 4. Sinon vérifier flag ITEM_PICKUP
     * 5. Si flag = false => CANCEL
     */
    // public void handle(...) { }
}
