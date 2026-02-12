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

/**
 * Gere les evenements de degats pour les flags INVINCIBLE, PVP et MOB_DAMAGE.
 *
 * Utilise le systeme ECS Damage (CancellableEcsEvent) avec le FilterDamageGroup
 * pour intercepter et annuler les degats selon les flags de la region.
 *
 * Logique:
 * 1. INVINCIBLE = true -> Annule TOUS les degats dans la region
 * 2. PVP = false -> Annule les degats Joueur vs Joueur
 * 3. MOB_DAMAGE = false -> Annule les degats Mob vs Joueur et Joueur vs Mob
 */
public class DamageEventSystem extends EntityEventSystem<EntityStore, Damage> {

    public DamageEventSystem() {
        super(Damage.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull Damage damage) {

        RegionsPlugin plugin = RegionsPlugin.get();
        if (plugin == null || plugin.getRegionService() == null) {
            return;
        }

        plugin.log(Level.INFO, "[Damage] DEBUG: >>> Damage event received! Amount: " + damage.getAmount());

        // Recuperer le joueur victime (si c'est un joueur)
        Player targetPlayer = null;
        try {
            targetPlayer = archetypeChunk.getComponent(index, Player.getComponentType());
        } catch (Exception e) {
            plugin.log(Level.INFO, "[Damage] DEBUG: Exception getting Player component: " + e.getMessage());
        }

        if (targetPlayer == null) {
            plugin.log(Level.INFO, "[Damage] DEBUG: Target is not a player, skipping");
            return;
        }

        plugin.log(Level.INFO, "[Damage] DEBUG: Target player: " + targetPlayer.getUuid());

        // Determiner la position de la victime pour trouver la region
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
            var transform = targetPlayer.getTransformComponent();
            if (transform != null) {
                x = (int) transform.getPosition().getX();
                y = (int) transform.getPosition().getY();
                z = (int) transform.getPosition().getZ();
            } else {
                plugin.log(Level.WARNING, "[Damage] DEBUG: Transform is null for player " + targetPlayer.getUuid());
                return;
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "[Damage] DEBUG: Exception getting position: " + e.getMessage());
            return;
        }

        plugin.log(Level.INFO, "[Damage] DEBUG: Player pos: " + x + "," + y + "," + z + " world: " + worldName);

        // Trouver les regions a cette position
        List<RegionImpl> regions = plugin.getRegionService().getRegionsAt(worldName, x, y, z);
        if (regions.isEmpty()) {
            plugin.log(Level.INFO, "[Damage] DEBUG: No region at player position, allowing damage");
            return;
        }

        RegionImpl region = regions.get(0);
        plugin.log(Level.INFO, "[Damage] DEBUG: Player in region: " + region.getName() + " (id=" + region.getId() + ")");

        // Recuperer les permissions du joueur
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

        // === 1. Verifier INVINCIBLE ===
        Boolean invincible = region.getFlag(RegionFlag.INVINCIBLE);
        plugin.log(Level.INFO, "[Damage] DEBUG: Flag INVINCIBLE = " + invincible);

        if (invincible != null && invincible) {
            damage.setCancelled(true);
            plugin.log(Level.INFO, "[Damage] CANCELLED (INVINCIBLE) - Player: " + targetPlayer.getUuid()
                + " in region: " + region.getName() + " | Damage: " + damage.getAmount());
            return;
        }

        // Recuperer la source des degats
        Damage.Source source = damage.getSource();
        plugin.log(Level.INFO, "[Damage] DEBUG: Source type: " + (source != null ? source.getClass().getSimpleName() : "null"));

        // === 2. Verifier PVP (Joueur vs Joueur) ===
        if (source instanceof Damage.EntitySource entitySource) {
            // Verifier si l'attaquant est un joueur
            Player attackerPlayer = null;
            try {
                Ref<EntityStore> attackerRef = entitySource.getRef();
                if (attackerRef != null) {
                    attackerPlayer = store.getComponent(attackerRef, Player.getComponentType());
                }
            } catch (Exception e) {
                plugin.log(Level.INFO, "[Damage] DEBUG: Exception getting attacker: " + e.getMessage());
            }

            if (attackerPlayer != null) {
                // C'est du PVP (joueur vs joueur)
                plugin.log(Level.INFO, "[Damage] DEBUG: PVP detected! Attacker: " + attackerPlayer.getUuid()
                    + " -> Target: " + targetPlayer.getUuid());

                Boolean pvpFlag = region.getFlag(RegionFlag.PVP);
                plugin.log(Level.INFO, "[Damage] DEBUG: Flag PVP = " + pvpFlag);

                boolean pvpAllowed = RegionPermissionChecker.isAllowed(
                    region, targetPlayer, RegionFlag.PVP, null, playerPermissions);
                plugin.log(Level.INFO, "[Damage] DEBUG: PVP allowed (after permission check) = " + pvpAllowed);

                if (!pvpAllowed) {
                    damage.setCancelled(true);
                    String regionDisplayName = RegionService.isGlobalRegion(region) ? "Region Globale" : region.getName();
                    NotificationUtil.send(attackerPlayer, NotificationType.WARNING, "Le PvP est desactive dans " + regionDisplayName);
                    plugin.log(Level.INFO, "[Damage] CANCELLED (PVP) - Attacker: " + attackerPlayer.getUuid()
                        + " -> Target: " + targetPlayer.getUuid() + " in region: " + region.getName());
                    return;
                }

                plugin.log(Level.INFO, "[Damage] ALLOWED (PVP) - Attacker: " + attackerPlayer.getUuid()
                    + " -> Target: " + targetPlayer.getUuid());
            } else {
                // C'est un mob qui attaque un joueur -> verifier MOB_DAMAGE
                plugin.log(Level.INFO, "[Damage] DEBUG: MOB -> Player detected! Target: " + targetPlayer.getUuid());

                Boolean mobDamageFlag = region.getFlag(RegionFlag.MOB_DAMAGE);
                plugin.log(Level.INFO, "[Damage] DEBUG: Flag MOB_DAMAGE = " + mobDamageFlag);

                boolean mobDamageAllowed = RegionPermissionChecker.isAllowed(
                    region, targetPlayer, RegionFlag.MOB_DAMAGE, null, playerPermissions);
                plugin.log(Level.INFO, "[Damage] DEBUG: MOB_DAMAGE allowed (after permission check) = " + mobDamageAllowed);

                if (!mobDamageAllowed) {
                    damage.setCancelled(true);
                    plugin.log(Level.INFO, "[Damage] CANCELLED (MOB_DAMAGE) - Mob -> Player: "
                        + targetPlayer.getUuid() + " in region: " + region.getName());
                    return;
                }

                plugin.log(Level.INFO, "[Damage] ALLOWED (MOB_DAMAGE) - Mob -> Player: " + targetPlayer.getUuid());
            }
        } else {
            // Degats environnementaux (chute, noyade, etc.)
            plugin.log(Level.INFO, "[Damage] DEBUG: Environment damage on " + targetPlayer.getUuid()
                + " - Not cancelled (no specific flag)");
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
