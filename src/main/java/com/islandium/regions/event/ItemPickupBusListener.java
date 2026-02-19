package com.islandium.regions.event;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.event.IslandiumEventBus;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.core.hook.ItemPickupSimpleEvent;
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
 * Listener sur IslandiumEventBus pour intercepter les ItemPickupSimpleEvent
 * fires par le HookRegistrar via le mixin PlayerGiveItemMixin.
 *
 * Verifie le flag ITEM_PICKUP de la region du joueur et cancel si pas autorise.
 * Couvre TOUS les pickups: passif (marcher dessus), interactif (F), harvest, etc.
 */
public class ItemPickupBusListener {

    private final RegionsPlugin plugin;
    private Consumer<ItemPickupSimpleEvent> handler;

    public ItemPickupBusListener(RegionsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre le listener sur l'EventBus.
     */
    public void register() {
        if (!IslandiumEventBus.isAvailable()) {
            plugin.log(Level.WARNING, "IslandiumEventBus not available, ItemPickupBusListener not registered");
            return;
        }

        handler = this::onItemPickup;
        IslandiumEventBus.get().register(ItemPickupSimpleEvent.class, handler);
        plugin.log(Level.INFO, "ItemPickupBusListener registered on IslandiumEventBus");
    }

    /**
     * Desenregistre le listener.
     */
    public void unregister() {
        if (handler != null && IslandiumEventBus.isAvailable()) {
            IslandiumEventBus.get().unregister(ItemPickupSimpleEvent.class, handler);
            handler = null;
        }
    }

    private void onItemPickup(ItemPickupSimpleEvent event) {
        if (plugin.getRegionService() == null) return;

        UUID playerUuid = event.getPlayerUuid();
        if (playerUuid == null) return;

        // Resoudre le Player via le PlayerManager
        Player player = null;
        try {
            if (IslandiumPlugin.isInitialized()) {
                var optPlayer = IslandiumPlugin.get().getPlayerManager().getOnlinePlayer(playerUuid);
                if (optPlayer.isPresent()) {
                    player = optPlayer.get().getHytalePlayer();
                }
            }
        } catch (Exception ignored) {}

        if (player == null) return;

        // Obtenir la position du joueur
        TransformComponent transform;
        try {
            transform = player.getTransformComponent();
        } catch (Exception e) {
            return;
        }
        if (transform == null || transform.getPosition() == null) return;

        int x = (int) transform.getPosition().getX();
        int y = (int) transform.getPosition().getY();
        int z = (int) transform.getPosition().getZ();

        // Obtenir le nom du monde
        String worldName;
        try {
            World world = player.getWorld();
            worldName = (world != null) ? world.getName() : plugin.getCurrentWorldName();
        } catch (Exception e) {
            worldName = plugin.getCurrentWorldName();
        }

        // Verifier les regions
        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) return;

        RegionImpl region = regions.get(0);

        // Recuperer les permissions du joueur
        PlayerPermissions playerPermissions = null;
        try {
            if (IslandiumPlugin.isInitialized()) {
                playerPermissions = IslandiumPlugin.get()
                    .getServiceManager()
                    .getPermissionService()
                    .getPlayerPermissions(playerUuid)
                    .getNow(null);
            }
        } catch (Exception ignored) {
        }

        // Verifier la permission ITEM_PICKUP
        boolean allowed = RegionPermissionChecker.isAllowed(
            region, player, RegionFlag.ITEM_PICKUP, null, playerPermissions
        );

        if (!allowed) {
            event.setCancelled(true);
            String regionDisplayName = RegionService.isGlobalRegion(region)
                ? "Region Globale" : region.getName();
            NotificationUtil.send(player, NotificationType.WARNING,
                "Vous ne pouvez pas ramasser d'items dans " + regionDisplayName + " (item-pickup)");
        }
    }
}
