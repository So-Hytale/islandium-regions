package com.islandium.regions.event;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.entity.EntityRemoveEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityInventoryChangeEvent;
import com.hypixel.hytale.server.core.event.events.entity.LivingEntityUseBlockEvent;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerCraftEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerInteractEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.permissions.GroupPermissionChangeEvent;
import com.hypixel.hytale.server.core.event.events.permissions.PlayerPermissionChangeEvent;
import com.islandium.regions.RegionsPlugin;

import java.util.logging.Level;

/**
 * Listener de test pour tous les events non-ECS.
 * Affiche juste 1 ligne de log par event pour voir lesquels se declenchent.
 */
public class EventTestListener {

    private final RegionsPlugin plugin;

    public EventTestListener(RegionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register(EventRegistry registry) {
        // Player events (KeyType = Void -> register)
        registry.register(PlayerConnectEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerConnectEvent declenche !"));
        registry.register(PlayerDisconnectEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerDisconnectEvent declenche !"));
        registry.register(PlayerMouseButtonEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerMouseButtonEvent declenche !"));

        // Player events (KeyType = String -> registerGlobal)
        registry.registerGlobal(PlayerInteractEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerInteractEvent declenche !"));
        registry.registerGlobal(PlayerCraftEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerCraftEvent declenche !"));
        registry.registerGlobal(PlayerReadyEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerReadyEvent declenche !"));
        registry.registerGlobal(AddPlayerToWorldEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] AddPlayerToWorldEvent declenche !"));
        registry.registerGlobal(DrainPlayerFromWorldEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] DrainPlayerFromWorldEvent declenche !"));

        // Player chat (async)
        registry.registerAsyncGlobal(PlayerChatEvent.class, eventFuture ->
            eventFuture.thenApply(e -> {
                plugin.log(Level.INFO, "[TEST] PlayerChatEvent declenche !");
                return e;
            }));

        // Entity events (KeyType = String -> registerGlobal)
        registry.registerGlobal(LivingEntityInventoryChangeEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] LivingEntityInventoryChangeEvent declenche !"));
        registry.registerGlobal(LivingEntityUseBlockEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] LivingEntityUseBlockEvent declenche !"));
        registry.registerGlobal(EntityRemoveEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] EntityRemoveEvent declenche !"));

        // Permission events (KeyType = Void -> register)
        registry.register(GroupPermissionChangeEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] GroupPermissionChangeEvent declenche !"));
        registry.register(PlayerPermissionChangeEvent.class, e ->
            plugin.log(Level.INFO, "[TEST] PlayerPermissionChangeEvent declenche !"));

        plugin.log(Level.INFO, "[TEST] 14 event test listeners registered");
    }
}
