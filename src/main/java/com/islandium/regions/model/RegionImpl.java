package com.islandium.regions.model;

import com.islandium.regions.api.Region;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.RegionShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implémentation d'une région protégée.
 */
public class RegionImpl implements Region {

    private int id;
    private String name;
    private String worldName;
    private RegionShape shape;
    private int priority;
    private final long createdAt;
    private final UUID createdBy;

    // Données en cache (chargées depuis la BDD)
    private final Set<UUID> owners = ConcurrentHashMap.newKeySet();
    private final Set<UUID> members = ConcurrentHashMap.newKeySet();
    private final Map<RegionFlag, Object> flags = new ConcurrentHashMap<>();

    // Cache des overrides de flags par groupe et par joueur
    private final FlagOverrideCache overrideCache = new FlagOverrideCache();

    /**
     * Constructeur avec RegionShape (nouvelle version).
     */
    public RegionImpl(int id, @NotNull String name, @NotNull String worldName,
                      @NotNull RegionShape shape, int priority,
                      long createdAt, @Nullable UUID createdBy) {
        this.id = id;
        this.name = name;
        this.worldName = worldName;
        this.shape = shape;
        this.priority = priority;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    /**
     * Constructeur de compatibilité avec BoundingBox (ancienne version).
     * Crée automatiquement un CuboidShape.
     */
    public RegionImpl(int id, @NotNull String name, @NotNull String worldName,
                      @NotNull BoundingBox bounds, int priority,
                      long createdAt, @Nullable UUID createdBy) {
        this(id, name, worldName, new CuboidShape(bounds), priority, createdAt, createdBy);
    }

    // === Identité ===

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    @NotNull
    public String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    @Override
    @NotNull
    public String getWorldName() {
        return worldName;
    }

    // === Forme et limites ===

    @Override
    @NotNull
    public RegionShape getShape() {
        return shape;
    }

    public void setShape(@NotNull RegionShape shape) {
        this.shape = shape;
    }

    @Override
    @NotNull
    public BoundingBox getBounds() {
        return shape.getEnclosingBounds();
    }

    /**
     * @deprecated Utiliser setShape() avec un CuboidShape à la place.
     */
    @Deprecated
    public void setBounds(@NotNull BoundingBox bounds) {
        this.shape = new CuboidShape(bounds);
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return shape.contains(x, y, z);
    }

    // === Priorité ===

    @Override
    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    // === Métadonnées ===

    @Override
    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    @Nullable
    public UUID getCreatedBy() {
        return createdBy;
    }

    // === Membres et propriétaires ===

    @Override
    @NotNull
    public Set<UUID> getOwners() {
        return Collections.unmodifiableSet(owners);
    }

    @Override
    @NotNull
    public Set<UUID> getMembers() {
        return Collections.unmodifiableSet(members);
    }

    @Override
    public boolean isOwner(@NotNull UUID playerUuid) {
        return owners.contains(playerUuid);
    }

    @Override
    public boolean isMember(@NotNull UUID playerUuid) {
        return members.contains(playerUuid) || owners.contains(playerUuid);
    }

    public void addOwner(@NotNull UUID playerUuid) {
        owners.add(playerUuid);
        members.remove(playerUuid); // Un owner n'est pas aussi member
    }

    public void removeOwner(@NotNull UUID playerUuid) {
        owners.remove(playerUuid);
    }

    public void addMember(@NotNull UUID playerUuid) {
        if (!owners.contains(playerUuid)) {
            members.add(playerUuid);
        }
    }

    public void removeMember(@NotNull UUID playerUuid) {
        members.remove(playerUuid);
    }

    public void clearMembers() {
        owners.clear();
        members.clear();
    }

    // === Flags ===

    @Override
    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getFlag(@NotNull RegionFlag flag) {
        Object value = flags.get(flag);
        if (value == null) {
            return flag.getDefaultValue();
        }
        return (T) value;
    }

    /**
     * Obtient la valeur brute d'un flag (sans valeur par défaut).
     */
    @Nullable
    public Object getRawFlag(@NotNull RegionFlag flag) {
        return flags.get(flag);
    }

    public void setFlag(@NotNull RegionFlag flag, @Nullable Object value) {
        if (value == null) {
            flags.remove(flag);
        } else {
            flags.put(flag, value);
        }
    }

    public void clearFlag(@NotNull RegionFlag flag) {
        flags.remove(flag);
    }

    @NotNull
    public Map<RegionFlag, Object> getFlags() {
        return Collections.unmodifiableMap(flags);
    }

    public void clearFlags() {
        flags.clear();
    }

    // === Overrides de flags (groupe/joueur) ===

    /**
     * Obtient le cache des overrides de flags.
     */
    @NotNull
    public FlagOverrideCache getOverrideCache() {
        return overrideCache;
    }

    /**
     * Définit un override de flag pour un groupe (rank).
     */
    public void setGroupFlag(@NotNull String rankName, @NotNull RegionFlag flag, @Nullable Object value) {
        overrideCache.setGroupOverride(rankName, flag, value);
    }

    /**
     * Obtient un override de flag pour un groupe.
     * @return null si pas d'override défini
     */
    @Nullable
    public Object getGroupFlag(@NotNull String rankName, @NotNull RegionFlag flag) {
        return overrideCache.getGroupOverride(rankName, flag).orElse(null);
    }

    /**
     * Vérifie si un groupe a un override pour un flag.
     */
    public boolean hasGroupFlag(@NotNull String rankName, @NotNull RegionFlag flag) {
        return overrideCache.hasGroupOverride(rankName, flag);
    }

    /**
     * Définit un override de flag pour un joueur.
     */
    public void setPlayerFlag(@NotNull UUID playerUuid, @NotNull RegionFlag flag, @Nullable Object value) {
        overrideCache.setPlayerOverride(playerUuid, flag, value);
    }

    /**
     * Obtient un override de flag pour un joueur.
     * @return null si pas d'override défini
     */
    @Nullable
    public Object getPlayerFlag(@NotNull UUID playerUuid, @NotNull RegionFlag flag) {
        return overrideCache.getPlayerOverride(playerUuid, flag).orElse(null);
    }

    /**
     * Vérifie si un joueur a un override pour un flag.
     */
    public boolean hasPlayerFlag(@NotNull UUID playerUuid, @NotNull RegionFlag flag) {
        return overrideCache.hasPlayerOverride(playerUuid, flag);
    }

    // === Permissions ===

    @Override
    public boolean canBuild(@NotNull UUID playerUuid) {
        // Les owners et members peuvent toujours construire
        if (isMember(playerUuid)) {
            return true;
        }

        // Sinon, vérifier le flag BUILD
        Boolean buildFlag = getFlag(RegionFlag.BUILD);
        return buildFlag != null && buildFlag;
    }

    @Override
    public boolean canBreak(@NotNull UUID playerUuid) {
        if (isMember(playerUuid)) {
            return true;
        }

        // Vérifie BLOCK_BREAK, puis BUILD en fallback
        Boolean breakFlag = getFlag(RegionFlag.BLOCK_BREAK);
        if (breakFlag != null) {
            return breakFlag;
        }
        return canBuild(playerUuid);
    }

    @Override
    public boolean canPlace(@NotNull UUID playerUuid) {
        if (isMember(playerUuid)) {
            return true;
        }

        // Vérifie BLOCK_PLACE, puis BUILD en fallback
        Boolean placeFlag = getFlag(RegionFlag.BLOCK_PLACE);
        if (placeFlag != null) {
            return placeFlag;
        }
        return canBuild(playerUuid);
    }

    @Override
    public boolean canInteract(@NotNull UUID playerUuid) {
        if (isMember(playerUuid)) {
            return true;
        }

        Boolean interactFlag = getFlag(RegionFlag.INTERACT);
        return interactFlag != null && interactFlag;
    }

    // === Utilitaires ===

    @Override
    public String toString() {
        return "Region{" +
            "id=" + id +
            ", name='" + name + '\'' +
            ", world='" + worldName + '\'' +
            ", priority=" + priority +
            ", shape=" + shape.getDescription() +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionImpl region = (RegionImpl) o;
        return id == region.id;
    }

    @Override
    public int hashCode() {
        return id;
    }
}
