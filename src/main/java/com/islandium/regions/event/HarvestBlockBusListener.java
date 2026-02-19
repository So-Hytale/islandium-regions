package com.islandium.regions.event;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.event.IslandiumEventBus;
import com.islandium.core.api.event.block.HarvestBlockEvent;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import com.islandium.regions.util.RegionPermissionChecker;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Listener sur IslandiumEventBus pour intercepter les HarvestBlockEvent
 * fires par le mixin BlockHarvestMixin.
 *
 * Verifie le flag HARVEST de la region du bloc et cancel si pas autorise.
 */
public class HarvestBlockBusListener {

    private final RegionsPlugin plugin;
    private Consumer<HarvestBlockEvent> handler;

    public HarvestBlockBusListener(RegionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!IslandiumEventBus.isAvailable()) {
            plugin.log(Level.WARNING, "IslandiumEventBus not available, HarvestBlockBusListener not registered");
            return;
        }

        handler = this::onHarvestBlock;
        IslandiumEventBus.get().register(HarvestBlockEvent.class, handler);
        plugin.log(Level.INFO, "HarvestBlockBusListener registered on IslandiumEventBus");
    }

    public void unregister() {
        if (handler != null && IslandiumEventBus.isAvailable()) {
            IslandiumEventBus.get().unregister(HarvestBlockEvent.class, handler);
            handler = null;
        }
    }

    private void onHarvestBlock(HarvestBlockEvent event) {
        if (plugin.getRegionService() == null) return;

        // Position du bloc
        int x = event.getBlockPos().getX();
        int y = event.getBlockPos().getY();
        int z = event.getBlockPos().getZ();

        // Obtenir le Player directement depuis l'event (résolu dans le mixin)
        Player player = event.getPlayerEntity();
        if (player == null) return;

        // Obtenir le monde
        String worldName;
        try {
            var world = player.getWorld();
            worldName = (world != null) ? world.getName() : plugin.getCurrentWorldName();
        } catch (Exception e) {
            worldName = plugin.getCurrentWorldName();
        }

        // Verifier les regions
        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) return;

        RegionImpl region = regions.get(0);

        // Permissions du joueur
        PlayerPermissions playerPermissions = null;
        try {
            UUID playerUuid = player.getUuid();
            if (IslandiumPlugin.get() != null) {
                playerPermissions = IslandiumPlugin.get()
                    .getServiceManager()
                    .getPermissionService()
                    .getPlayerPermissions(playerUuid)
                    .getNow(null);
            }
        } catch (Exception ignored) {}

        boolean allowed = RegionPermissionChecker.isAllowed(
            region, player, RegionFlag.HARVEST, null, playerPermissions
        );

        if (!allowed) {
            event.setCancelled(true);
            String regionDisplayName = RegionService.isGlobalRegion(region)
                ? "Region Globale" : region.getName();
            NotificationUtil.send(player, NotificationType.WARNING,
                "Vous ne pouvez pas harvest dans " + regionDisplayName + " (harvest)");
        }
    }
}
