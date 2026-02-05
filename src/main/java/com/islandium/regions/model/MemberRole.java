package com.islandium.regions.model;

/**
 * Rôle d'un membre dans une région.
 */
public enum MemberRole {
    /**
     * Propriétaire de la région - tous les droits.
     */
    OWNER,

    /**
     * Membre de la région - droits de construction.
     */
    MEMBER;

    /**
     * Vérifie si ce rôle a au moins les permissions d'un autre rôle.
     */
    public boolean hasAtLeast(MemberRole other) {
        return this.ordinal() <= other.ordinal();
    }

    /**
     * Parse un rôle depuis une chaîne.
     */
    public static MemberRole fromString(String value) {
        if (value == null) return null;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
