package com.islandium.regions.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
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

public class DamageEventSystem extends EntityEventSystem<EntityStore, Damage> {

    public DamageEventSystem() {
        super(Damage.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {

        RegionsPlugin plugin = RegionsPlugin.get();
        if (plugin == null || plugin.getRegionService() == null) return;

        Player targetPlayer = null;
        try {
            targetPlayer = archetypeChunk.getComponent(index, Player.getComponentType());
        } catch (Exception e) {
            return;
        }
        if (targetPlayer == null) return;

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

        int x, y, z;
        try {
            var transform = targetPlayer.getTransformComponent();
            if (transform != null) {
                x = (int) transform.getPosition().getX();
                y = (int) transform.getPosition().getY();
                z = (int) transform.getPosition().getZ();
            } else {
                return;
            }
        } catch (Exception e) {
            return;
        }

        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) return;

        RegionImpl region = regions.get(0);

        PlayerPermissions playerPermissions = null;
        try {
            UUID playerUuid = targetPlayer.getUuid();
            if (IslandiumPlugin.get() != null) {
                playerPermissions = IslandiumPlugin.get()
                    .getServiceManager()
                    .getPermissionService()
                    .getPlayerPermissions(playerUuid)
                    .getNow(null);
            }
        } catch (Exception ignored) {
        }

        // 1. INVINCIBLE
        Object invincible = region.getFlag(RegionFlag.INVINCIBLE);
        if (Boolean.TRUE.equals(invincible)) {
            damage.setCancelled(true);
            return;
        }

        // 2. PVP / MOB_DAMAGE
        Damage.Source source = damage.getSource();
        if (source instanceof Damage.EntitySource entitySource) {
            Player attackerPlayer = null;
            try {
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef != null) {
                    attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
                }
            } catch (Exception ignored) {
            }

            if (attackerPlayer != null) {
                // PVP
                boolean pvpAllowed = RegionPermissionChecker.isAllowed(region, targetPlayer, RegionFlag.PVP, null, playerPermissions);
                if (!pvpAllowed) {
                    damage.setCancelled(true);
                    String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
                    NotificationUtil.send(attackerPlayer, NotificationType.WARNING, "Le PvP est desactive dans " + regionDisplayName + " (pvp)");
                }
            } else {
                // MOB_DAMAGE
                boolean mobDamageAllowed = RegionPermissionChecker.isAllowed(region, targetPlayer, RegionFlag.MOB_DAMAGE, null, playerPermissions);
                if (!mobDamageAllowed) {
                    damage.setCancelled(true);
                }
            }
        }
    }

    @Nullable @Override
    public Query<EntityStore> getQuery() { return PlayerRef.getComponentType(); }

    @Nonnull @Override
    public Set<Dependency<EntityStore>> getDependencies() { return Collections.singleton(RootDependency.first()); }
}
