package com.islandium.regions.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
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

public class PlaceBlockEventSystem extends EntityEventSystem<EntityStore, PlaceBlockEvent> {

    public PlaceBlockEventSystem() {
        super(PlaceBlockEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull PlaceBlockEvent event) {

        RegionsPlugin plugin = RegionsPlugin.get();
        if (plugin == null || plugin.getRegionService() == null) {
            return;
        }

        Vector3i pos = event.getTargetBlock();

        // Obtenir le monde depuis le store
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

        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, pos.getX(), pos.getY(), pos.getZ());
        if (regions.isEmpty()) {
            return;
        }

        RegionImpl region = regions.get(0);

        // Récupérer le joueur
        Player player = null;
        try {
            player = archetypeChunk.getComponent(index, Player.getComponentType());
        } catch (Exception ignored) {
        }

        // Récupérer les permissions du joueur pour la résolution par groupe
        PlayerPermissions playerPermissions = null;
        if (player != null) {
            try {
                UUID playerUuid = player.getUuid();
                if (IslandiumPlugin.get() != null) {
                    // Le cache du PermissionService devrait rendre cet appel quasi-instantané
                    playerPermissions = IslandiumPlugin.get()
                        .getServiceManager()
                        .getPermissionService()
                        .getPlayerPermissions(playerUuid)
                        .getNow(null); // Non-bloquant, retourne null si pas en cache
                }
            } catch (Exception ignored) {
            }
        }

        // Vérifier la permission avec la résolution Joueur > Groupe > Région
        if (!RegionPermissionChecker.isAllowed(region, player, RegionFlag.BLOCK_PLACE, RegionFlag.BUILD, playerPermissions)) {
            event.setCancelled(true);

            // Envoyer un message au joueur
            if (player != null) {
                String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
                NotificationUtil.send(player, NotificationType.WARNING, "Vous ne pouvez pas placer de blocs dans " + regionDisplayName + " (block-place)");
            }
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
