package com.islandium.regions.event;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.event.IslandiumEventBus;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.core.hook.HarvestBlockSimpleEvent;
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
 * Listener sur IslandiumEventBus pour intercepter les HarvestBlockSimpleEvent
 * fires par le HookRegistrar via le mixin BlockHarvestMixin.
 *
 * Verifie le flag HARVEST de la region du bloc et cancel si pas autorise.
 */
public class HarvestBlockBusListener {

    private final RegionsPlugin plugin;
    private Consumer<HarvestBlockSimpleEvent> handler;

    public HarvestBlockBusListener(RegionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        if (!IslandiumEventBus.isAvailable()) {
            plugin.log(Level.WARNING, "IslandiumEventBus not available, HarvestBlockBusListener not registered");
            return;
        }

        handler = this::onHarvestBlock;
        IslandiumEventBus.get().register(HarvestBlockSimpleEvent.class, handler);
        plugin.log(Level.INFO, "HarvestBlockBusListener registered on IslandiumEventBus");
    }

    public void unregister() {
        if (handler != null && IslandiumEventBus.isAvailable()) {
            IslandiumEventBus.get().unregister(HarvestBlockSimpleEvent.class, handler);
            handler = null;
        }
    }

    private void onHarvestBlock(HarvestBlockSimpleEvent event) {
        if (plugin.getRegionService() == null) return;

        UUID playerUuid = event.getPlayerUuid();
        if (playerUuid == null) return;

        int x = event.getX();
        int y = event.getY();
        int z = event.getZ();

        // Obtenir le monde
        String worldName = plugin.getCurrentWorldName();

        // Essayer de resoudre le Player pour les permissions et la notification
        Player player = null;
        try {
            if (IslandiumPlugin.isInitialized()) {
                var optPlayer = IslandiumPlugin.get().getPlayerManager().getOnlinePlayer(playerUuid);
                if (optPlayer.isPresent()) {
                    player = optPlayer.get().getHytalePlayer();
                    if (player != null) {
                        var world = player.getWorld();
                        if (world != null) {
                            worldName = world.getName();
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        // Verifier les regions
        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) return;

        RegionImpl region = regions.get(0);

        // Permissions du joueur
        PlayerPermissions playerPermissions = null;
        try {
            if (IslandiumPlugin.isInitialized()) {
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
            if (player != null) {
                NotificationUtil.send(player, NotificationType.WARNING,
                    "Vous ne pouvez pas harvest dans " + regionDisplayName + " (harvest)");
            }
        }
    }
}
