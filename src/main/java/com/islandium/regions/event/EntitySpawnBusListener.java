package com.islandium.regions.event;

import com.islandium.core.api.event.IslandiumEventBus;
import com.islandium.core.api.event.entity.EntitySpawnEvent;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;

import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Listener sur IslandiumEventBus pour intercepter les EntitySpawnEvent
 * fires par le HookRegistrar via le mixin EntitySpawnMixin.
 *
 * Verifie le flag MOB_SPAWNING de la region et cancel si pas autorise.
 * Pas de resolution par joueur — les spawns de mobs sont globaux.
 */
public class EntitySpawnBusListener {

    private final RegionsPlugin plugin;
    private Consumer<EntitySpawnEvent> handler;

    public EntitySpawnBusListener(RegionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!IslandiumEventBus.isAvailable()) {
            plugin.log(Level.WARNING, "IslandiumEventBus not available, EntitySpawnBusListener not registered");
            return;
        }

        handler = this::onEntitySpawn;
        IslandiumEventBus.get().register(EntitySpawnEvent.class, handler);
        plugin.log(Level.INFO, "EntitySpawnBusListener registered on IslandiumEventBus");
    }

    public void unregister() {
        if (handler != null && IslandiumEventBus.isAvailable()) {
            IslandiumEventBus.get().unregister(EntitySpawnEvent.class, handler);
            handler = null;
        }
    }

    private void onEntitySpawn(EntitySpawnEvent event) {
        if (plugin.getRegionService() == null) return;

        int x = (int) event.getX();
        int y = (int) event.getY();
        int z = (int) event.getZ();

        String worldName = plugin.getCurrentWorldName();

        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) return;

        RegionImpl region = regions.get(0);

        // Verifier le flag MOB_SPAWNING (pas de resolution par joueur, flag global)
        Object flagValue = region.getFlag(RegionFlag.MOB_SPAWNING);
        if (flagValue == null) {
            flagValue = RegionFlag.MOB_SPAWNING.getDefaultValue();
        }

        if (Boolean.FALSE.equals(flagValue)) {
            event.setCancelled(true);
        }
    }
}
