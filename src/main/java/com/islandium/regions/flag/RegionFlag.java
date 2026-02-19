package com.islandium.regions.flag;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enum des flags de région disponibles.
 *
 * Statut d'implémentation:
 * - IMPLEMENTED: Flag entièrement fonctionnel et testé
 * - PARTIAL: Flag partiellement implémenté (en attente d'API Hytale)
 * - NOT_IMPLEMENTED: Flag prévu mais pas encore implémenté
 */
public enum RegionFlag {

    // === Flags de construction ===

    /**
     * Autorise la construction globale (par défaut pour break/place si non défini).
     */
    BUILD("build", Boolean.TRUE, FlagType.BOOLEAN, "Autorise la construction", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise le cassage de blocs.
     */
    BLOCK_BREAK("block-break", null, FlagType.BOOLEAN, "Autorise le cassage de blocs", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise le placement de blocs.
     */
    BLOCK_PLACE("block-place", null, FlagType.BOOLEAN, "Autorise le placement de blocs", ImplementationStatus.IMPLEMENTED),

    // === Flags d'interaction ===

    /**
     * Autorise les interactions avec les blocs (portes, leviers, etc.).
     */
    INTERACT("interact", Boolean.TRUE, FlagType.BOOLEAN, "Autorise les interactions", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise l'accès aux coffres.
     */
    CHEST_ACCESS("chest-access", Boolean.TRUE, FlagType.BOOLEAN, "Autorise l'acces aux coffres", ImplementationStatus.IMPLEMENTED),

    // === Flags de combat ===

    /**
     * Autorise le PvP dans la région.
     */
    PVP("pvp", Boolean.TRUE, FlagType.BOOLEAN, "Autorise le PvP", ImplementationStatus.IMPLEMENTED),

    /**
     * Rend les joueurs invincibles dans la région.
     */
    INVINCIBLE("invincible", Boolean.FALSE, FlagType.BOOLEAN, "Joueurs invincibles", ImplementationStatus.IMPLEMENTED),

    // === Flags d'entités ===

    /**
     * Autorise le spawn de mobs.
     */
    MOB_SPAWNING("mob-spawning", Boolean.TRUE, FlagType.BOOLEAN, "Autorise le spawn de mobs", ImplementationStatus.NOT_IMPLEMENTED),

    /**
     * Autorise les dégâts aux mobs.
     */
    MOB_DAMAGE("mob-damage", Boolean.TRUE, FlagType.BOOLEAN, "Autorise les degats aux mobs", ImplementationStatus.IMPLEMENTED),

    // === Flags de mouvement ===

    /**
     * Autorise l'entrée dans la région.
     */
    ENTRY("entry", Boolean.TRUE, FlagType.BOOLEAN, "Autorise l'entree", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise la sortie de la région.
     */
    EXIT("exit", Boolean.TRUE, FlagType.BOOLEAN, "Autorise la sortie", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise la téléportation dans la région.
     */
    TELEPORT("teleport", Boolean.TRUE, FlagType.BOOLEAN, "Autorise la teleportation", ImplementationStatus.NOT_IMPLEMENTED),

    // === Flags d'items ===

    /**
     * Autorise le drop d'items.
     */
    ITEM_DROP("item-drop", Boolean.TRUE, FlagType.BOOLEAN, "Autorise le drop d'items", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise le ramassage d'items.
     */
    ITEM_PICKUP("item-pickup", Boolean.TRUE, FlagType.BOOLEAN, "Autorise le ramassage d'items", ImplementationStatus.IMPLEMENTED),

    /**
     * Autorise le harvest de blocs (touche F sur rubble, etc.).
     */
    HARVEST("harvest", Boolean.TRUE, FlagType.BOOLEAN, "Autorise le harvest (touche F)", ImplementationStatus.IMPLEMENTED),

    // === Flags de messages ===

    /**
     * Message affiché à l'entrée de la région.
     */
    GREETING_MESSAGE("greeting-message", null, FlagType.STRING, "Message d'entree", ImplementationStatus.IMPLEMENTED),

    /**
     * Message affiché à la sortie de la région.
     */
    FAREWELL_MESSAGE("farewell-message", null, FlagType.STRING, "Message de sortie", ImplementationStatus.IMPLEMENTED),

    // === Flags divers ===

    /**
     * Passe les événements aux régions de priorité inférieure.
     */
    PASSTHROUGH("passthrough", Boolean.FALSE, FlagType.BOOLEAN, "Passe aux regions inferieures", ImplementationStatus.NOT_IMPLEMENTED),

    /**
     * Liste des ranks autorisés (séparés par des virgules).
     * Ex: "admin,moderator,vip"
     */
    ALLOWED_RANKS("allowed-ranks", null, FlagType.STRING, "Ranks autorises", ImplementationStatus.NOT_IMPLEMENTED);

    private final String name;
    private final Object defaultValue;
    private final FlagType type;
    private final String description;
    private final ImplementationStatus status;

    RegionFlag(String name, Object defaultValue, FlagType type, String description, ImplementationStatus status) {
        this.name = name;
        this.defaultValue = defaultValue;
        this.type = type;
        this.description = description;
        this.status = status;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public <T> T getDefaultValue() {
        return (T) defaultValue;
    }

    @NotNull
    public FlagType getType() {
        return type;
    }

    @NotNull
    public String getDescription() {
        return description;
    }

    /**
     * Obtient le statut d'implémentation du flag.
     */
    @NotNull
    public ImplementationStatus getStatus() {
        return status;
    }

    /**
     * Vérifie si le flag est entièrement implémenté.
     */
    public boolean isImplemented() {
        return status == ImplementationStatus.IMPLEMENTED;
    }

    /**
     * Vérifie si le flag est au moins partiellement implémenté.
     */
    public boolean isAtLeastPartial() {
        return status == ImplementationStatus.IMPLEMENTED || status == ImplementationStatus.PARTIAL;
    }

    /**
     * Parse une valeur pour ce flag.
     */
    @Nullable
    public Object parseValue(@NotNull String value) {
        return type.parse(value);
    }

    /**
     * Trouve un flag par son nom.
     */
    @Nullable
    public static RegionFlag fromName(@NotNull String name) {
        for (RegionFlag flag : values()) {
            if (flag.name.equalsIgnoreCase(name)) {
                return flag;
            }
        }
        return null;
    }

    /**
     * Valeurs possibles pour les flags tri-state.
     * ALLOW = tout le monde peut
     * MEMBERS = seulement les membres/owners
     * DENY = personne (sauf bypass toggle actif)
     */
    public enum FlagValue {
        ALLOW,   // ON - tout le monde
        MEMBERS, // OFF membres - seulement membres/owners
        DENY;    // OFF tous - personne

        public static FlagValue fromString(String value) {
            if (value == null) return null;
            String lower = value.toLowerCase();
            if (lower.equals("allow") || lower.equals("true") || lower.equals("yes") || lower.equals("1") || lower.equals("on")) {
                return ALLOW;
            }
            if (lower.equals("members") || lower.equals("member")) {
                return MEMBERS;
            }
            if (lower.equals("deny") || lower.equals("false") || lower.equals("no") || lower.equals("0") || lower.equals("off")) {
                return DENY;
            }
            return null;
        }
    }

    /**
     * Type de valeur d'un flag.
     */
    public enum FlagType {
        BOOLEAN {
            @Override
            public Object parse(String value) {
                if (value == null) return null;
                String lower = value.toLowerCase();
                if (lower.equals("true") || lower.equals("allow") || lower.equals("yes") || lower.equals("1")) {
                    return Boolean.TRUE;
                }
                if (lower.equals("false") || lower.equals("deny") || lower.equals("no") || lower.equals("0")) {
                    return Boolean.FALSE;
                }
                if (lower.equals("members") || lower.equals("member")) {
                    return "members"; // Support members pour les flags boolean aussi
                }
                if (lower.equals("none") || lower.equals("null") || lower.equals("clear")) {
                    return null;
                }
                return null;
            }

            @Override
            public String serialize(Object value) {
                if (value == null) return null;
                return value.toString();
            }
        },
        STRING {
            @Override
            public Object parse(String value) {
                if (value == null || value.equalsIgnoreCase("none") || value.equalsIgnoreCase("null")) {
                    return null;
                }
                return value;
            }

            @Override
            public String serialize(Object value) {
                return value != null ? value.toString() : null;
            }
        },
        INTEGER {
            @Override
            public Object parse(String value) {
                if (value == null || value.equalsIgnoreCase("none") || value.equalsIgnoreCase("null")) {
                    return null;
                }
                try {
                    return Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            }

            @Override
            public String serialize(Object value) {
                return value != null ? value.toString() : null;
            }
        };

        public abstract Object parse(String value);
        public abstract String serialize(Object value);
    }

    /**
     * Statut d'implémentation d'un flag.
     */
    public enum ImplementationStatus {
        /**
         * Flag entièrement fonctionnel et testé.
         */
        IMPLEMENTED("Fonctionnel", "#5adf5a"),  // Vert

        /**
         * Flag partiellement implémenté (en attente d'API Hytale ou stub).
         */
        PARTIAL("Partiel", "#dfdf5a"),  // Jaune

        /**
         * Flag prévu mais pas encore implémenté.
         */
        NOT_IMPLEMENTED("Non implemente", "#808080");  // Gris

        private final String displayName;
        private final String color;

        ImplementationStatus(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        /**
         * Couleur hexadécimale pour l'affichage UI.
         */
        public String getColor() {
            return color;
        }

        /**
         * Icône pour l'affichage UI.
         */
        public String getIcon() {
            return switch (this) {
                case IMPLEMENTED -> "[OK]";
                case PARTIAL -> "[~]";
                case NOT_IMPLEMENTED -> "[X]";
            };
        }
    }
}
