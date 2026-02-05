package com.islandium.regions.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Représente un membre d'une région.
 */
public class RegionMember {

    private final int regionId;
    private final UUID playerUuid;
    private final MemberRole role;
    private final long addedAt;
    private final UUID addedBy;

    public RegionMember(int regionId, @NotNull UUID playerUuid, @NotNull MemberRole role,
                        long addedAt, @Nullable UUID addedBy) {
        this.regionId = regionId;
        this.playerUuid = playerUuid;
        this.role = role;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
    }

    public int getRegionId() {
        return regionId;
    }

    @NotNull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @NotNull
    public MemberRole getRole() {
        return role;
    }

    public long getAddedAt() {
        return addedAt;
    }

    @Nullable
    public UUID getAddedBy() {
        return addedBy;
    }

    public boolean isOwner() {
        return role == MemberRole.OWNER;
    }

    public boolean isMember() {
        return role == MemberRole.MEMBER;
    }

    @Override
    public String toString() {
        return "RegionMember{" +
            "regionId=" + regionId +
            ", playerUuid=" + playerUuid +
            ", role=" + role +
            '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RegionMember that = (RegionMember) o;
        return regionId == that.regionId && playerUuid.equals(that.playerUuid);
    }

    @Override
    public int hashCode() {
        return 31 * regionId + playerUuid.hashCode();
    }
}
