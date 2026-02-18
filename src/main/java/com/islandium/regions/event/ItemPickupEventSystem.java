package com.islandium.regions.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.InteractivelyPickupItemEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import com.islandium.regions.util.RegionPermissionChecker;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gere les evenements de ramassage d'items pour le flag ITEM_PICKUP.
 *
 * Utilise le systeme ECS InteractivelyPickupItemEvent (CancellableEcsEvent)
 * pour intercepter et annuler le ramassage d'items selon les flags de la region.
 *
 * Logique:
 * - ITEM_PICKUP = false -> Annule le ramassage d'items dans la region
 * - Membres/owners de la region peuvent toujours ramasser (selon permission)
 */
public class ItemPickupEventSystem extends EntityEventSystem<EntityStore, InteractivelyPickupItemEvent> {

    public ItemPickupEventSystem() {
        super(InteractivelyPickupItemEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull InteractivelyPickupItemEvent event) {

        RegionsPlugin plugin = RegionsPlugin.get();
        if (plugin == null || plugin.getRegionService() == null) {
            return;
        }

        plugin.log(Level.INFO, "[ItemPickup] DEBUG: >>> InteractivelyPickupItemEvent received!");

        // Recuperer le joueur qui ramasse
        Player player = null;
        try {
            player = archetypeChunk.getComponent(index, Player.getComponentType());
        } catch (Exception e) {
            plugin.log(Level.INFO, "[ItemPickup] DEBUG: Exception getting Player component: " + e.getMessage());
        }

        if (player == null) {
            plugin.log(Level.INFO, "[ItemPickup] DEBUG: Not a player, skipping");
            return;
        }

        plugin.log(Level.INFO, "[ItemPickup] DEBUG: Player: " + player.getUuid());

        // Determiner le monde
        String worldName;
        try {
            var externalData = store.getExternalData();
            if (externalData instanceof EntityStore entityStore) {
                worldName = entityStore.getWorld().getName();
            } else {
                worldName = plugin.getCurrentWorldName();
            }
        } catch (Exception e) {
            worldName = plugin.getCurrentWorldName();
        }

        // Recuperer la position du joueur
        int x, y, z;
        try {
            var transform = player.getTransformComponent();
            if (transform != null) {
                x = (int) transform.getPosition().getX();
                y = (int) transform.getPosition().getY();
                z = (int) transform.getPosition().getZ();
            } else {
                plugin.log(Level.WARNING, "[ItemPickup] DEBUG: Transform is null for player " + player.getUuid());
                return;
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "[ItemPickup] DEBUG: Exception getting position: " + e.getMessage());
            return;
        }

        plugin.log(Level.INFO, "[ItemPickup] DEBUG: Player pos: " + x + "," + y + "," + z + " world: " + worldName);

        // Trouver les regions a cette position
        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            plugin.log(Level.INFO, "[ItemPickup] DEBUG: No region at position, allowing pickup");
            return;
        }

        RegionImpl region = regions.get(0);
        plugin.log(Level.INFO, "[ItemPickup] DEBUG: Player in region: " + region.getName() + " (id=" + region.getId() + ")");

        // Recuperer les permissions du joueur
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
        } catch (Exception ignored) {
        }

        // Verifier le flag ITEM_PICKUP avec la resolution Joueur > Groupe > Region
        Object itemPickupFlag = region.getFlag(RegionFlag.ITEM_PICKUP);
        plugin.log(Level.INFO, "[ItemPickup] DEBUG: Flag ITEM_PICKUP = " + itemPickupFlag);

        boolean allowed = RegionPermissionChecker.isAllowed(
            region, player, RegionFlag.ITEM_PICKUP, null, playerPermissions);
        plugin.log(Level.INFO, "[ItemPickup] DEBUG: Allowed (after permission check) = " + allowed);

        if (!allowed) {
            event.setCancelled(true);

            String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
            NotificationUtil.send(player, NotificationType.WARNING, "Vous ne pouvez pas ramasser d'items dans " + regionDisplayName + " (item-pickup)");

            plugin.log(Level.INFO, "[ItemPickup] CANCELLED - Player: " + player.getUuid()
                + " in region: " + region.getName());
        } else {
            plugin.log(Level.INFO, "[ItemPickup] ALLOWED - Player: " + player.getUuid()
                + " in region: " + region.getName());
        }
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
