package com.islandium.regions.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.permission.PlayerPermissions;
import com.islandium.core.api.util.ColorUtil;
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

public class BreakBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakBlockEventSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {

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
        Player player = player = archetypeChunk.getComponent(index, Player.getComponentType());
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
        boolean allowed = RegionPermissionChecker.isAllowed(region, player, RegionFlag.BLOCK_BREAK, RegionFlag.BUILD, playerPermissions);

        if (!allowed) {
            event.setCancelled(true);

            // Envoyer un message au joueur
            if (player != null) {
                String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
                player.sendMessage(ColorUtil.parse("&c&lProtection! &7Vous ne pouvez pas &ccasser de blocs &7dans &e" + regionDisplayName + "&7."));
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
