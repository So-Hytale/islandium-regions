package com.islandium.regions.event;

import com.islandium.regions.RegionsPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Gère les événements de dégâts pour les flags PVP, MOB_DAMAGE et INVINCIBLE.
 *
 * NOTE: Cette classe est un STUB en attente de l'API Hytale.
 * L'event DamageEvent n'existe pas encore dans l'API.
 *
 * Quand l'API sera disponible, implémenter EntityEventSystem<EntityStore, DamageEvent>
 * et vérifier les flags:
 * - INVINCIBLE: Annule tous les dégâts
 * - PVP: Joueur vs Joueur
 * - MOB_DAMAGE: Mob vs Joueur ou Joueur vs Mob
 */
public class DamageEventListener {

    private final RegionsPlugin plugin;

    public DamageEventListener(@NotNull RegionsPlugin plugin) {
        this.plugin = plugin;
        plugin.log(java.util.logging.Level.INFO, "[DamageEventListener] Stub created - waiting for Hytale API DamageEvent");
    }

    /**
     * A implémenter quand l'API DamageEvent sera disponible.
     *
     * Logique prévue:
     * 1. Récupérer position de la victime (target)
     * 2. Si target n'est pas un joueur => ignorer
     * 3. Vérifier INVINCIBLE => si true, CANCEL
     * 4. Si source ET target sont des joueurs => vérifier PVP
     * 5. Sinon (mob impliqué) => vérifier MOB_DAMAGE
     */
    // public void handle(...) { }
}
