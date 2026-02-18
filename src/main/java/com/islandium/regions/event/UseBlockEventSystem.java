package com.islandium.regions.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
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

/**
 * Système ECS qui intercepte les interactions avec les blocs interactifs
 * (portes, leviers, coffres, boutons, etc.) et vérifie les permissions de la région.
 *
 * Utilise le flag INTERACT (fallback vers USE).
 */
public class UseBlockEventSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    public UseBlockEventSystem() {
        super(UseBlockEvent.Pre.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull UseBlockEvent.Pre event) {

        RegionsPlugin plugin = RegionsPlugin.get();
        if (plugin == null || plugin.getRegionService() == null) return;

        BlockType blockType = event.getBlockType();
        if (blockType == null || blockType == BlockType.EMPTY) return;

        Vector3i pos = event.getTargetBlock();

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
        if (regions.isEmpty()) return;

        RegionImpl region = regions.get(0);

        // Récupérer le joueur
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());

        // Récupérer les permissions du joueur pour la résolution par groupe
        PlayerPermissions playerPermissions = null;
        if (player != null) {
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
        }

        // Vérifier la permission: INTERACT, fallback vers USE
        boolean allowed = RegionPermissionChecker.isAllowed(region, player, RegionFlag.INTERACT, null, playerPermissions);

        if (!allowed) {
            event.setCancelled(true);

            if (player != null) {
                String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
                NotificationUtil.send(player, NotificationType.WARNING, "Vous ne pouvez pas interagir dans " + regionDisplayName);
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
