package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.core.IslandiumPlugin;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import com.islandium.regions.shape.CuboidShape;
import com.islandium.regions.shape.CylinderShape;
import com.islandium.regions.shape.GlobalShape;
import com.islandium.regions.shape.RegionShape;
import com.islandium.regions.ui.RegionMainPage;
import com.islandium.regions.util.SelectionHelper;
import com.islandium.regions.visualization.RegionVisualizationService;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Commande principale /rg pour gérer les régions.
 * Usage: /rg <action> [args...]
 */
public class RgCommand extends AbstractCommand {

    private final RegionsPlugin plugin;
    private final RegionService regionService;

    private final RequiredArg<String> actionArg;
    private final OptionalArg<String> arg1;
    private final OptionalArg<String> arg2;
    private final OptionalArg<String> arg3;

    public RgCommand(@NotNull RegionsPlugin plugin) {
        super("rg", "Gestion des régions");
        this.plugin = plugin;
        this.regionService = plugin.getRegionService();

        // Ajouter des alias pour la commande
        addAliases("region", "regions");

        actionArg = withRequiredArg("action", "Action (create, delete, list, info, flag, addmember, addowner, removemember, removeowner, ui, help)", ArgTypes.STRING);
        arg1 = withOptionalArg("arg1", "Argument 1", ArgTypes.STRING);
        arg2 = withOptionalArg("arg2", "Argument 2", ArgTypes.STRING);
        arg3 = withOptionalArg("arg3", "Argument 3", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!ctx.isPlayer()) {
            ctx.sendMessage(ColorUtil.parse("&cCette commande nécessite un joueur."));
            return CompletableFuture.completedFuture(null);
        }

        String action = ctx.get(actionArg).toLowerCase();

        CompletableFuture<Void> result = switch (action) {
            case "create" -> executeCreate(ctx);
            case "delete", "remove" -> executeDelete(ctx);
            case "list" -> executeList(ctx);
            case "info", "i" -> executeInfo(ctx);
            case "flag", "f" -> executeFlag(ctx);
            case "addmember", "am" -> executeAddMember(ctx);
            case "addowner", "ao" -> executeAddOwner(ctx);
            case "removemember", "rm" -> executeRemoveMember(ctx);
            case "removeowner", "ro" -> executeRemoveOwner(ctx);
            case "global", "g" -> executeGlobal(ctx);
            case "visualize", "vis", "v" -> executeVisualize(ctx);
            case "ui", "gui" -> executeUI(ctx);
            case "help", "?" -> showHelp(ctx);
            default -> showHelp(ctx);
        };

        return result.exceptionally(ex -> {
            ctx.sendMessage(ColorUtil.parse("&cErreur: " + ex.getMessage()));
            ex.printStackTrace();
            return null;
        });
    }

    private CompletableFuture<Void> showHelp(CommandContext ctx) {
        ctx.sendMessage(ColorUtil.parse("&6=== Régions - Aide ==="));
        ctx.sendMessage(ColorUtil.parse("&e/rg create <nom> &7- Créer une région cubique (utiliser /pos1 /pos2 avant)"));
        ctx.sendMessage(ColorUtil.parse("&e/rg create <nom> cylinder <rayon> <hauteur> &7- Créer un cylindre à votre position"));
        ctx.sendMessage(ColorUtil.parse("&e/rg delete <nom> &7- Supprimer une région"));
        ctx.sendMessage(ColorUtil.parse("&e/rg list &7- Lister les régions du monde"));
        ctx.sendMessage(ColorUtil.parse("&e/rg info [nom] &7- Infos sur une région"));
        ctx.sendMessage(ColorUtil.parse("&e/rg flag <région> <flag> <valeur> &7- Définir un flag"));
        ctx.sendMessage(ColorUtil.parse("&e/rg addmember <région> <joueur> &7- Ajouter un membre"));
        ctx.sendMessage(ColorUtil.parse("&e/rg addowner <région> <joueur> &7- Ajouter un propriétaire"));
        ctx.sendMessage(ColorUtil.parse("&e/rg removemember <région> <joueur> &7- Retirer un membre"));
        ctx.sendMessage(ColorUtil.parse("&e/rg removeowner <région> <joueur> &7- Retirer un propriétaire"));
        ctx.sendMessage(ColorUtil.parse("&e/rg global &7- Voir/gérer la région globale (monde entier)"));
        ctx.sendMessage(ColorUtil.parse("&e/rg global create &7- Créer une région globale"));
        ctx.sendMessage(ColorUtil.parse("&e/rg global flag <flag> <valeur> &7- Définir un flag global"));
        ctx.sendMessage(ColorUtil.parse("&e/rg vis [nom] &7- Visualiser une région (debug shapes)"));
        ctx.sendMessage(ColorUtil.parse("&e/rg ui &7- Ouvrir l'interface graphique"));
        ctx.sendMessage(ColorUtil.parse("&7Flags disponibles: " + getAvailableFlags()));
        return CompletableFuture.completedFuture(null);
    }

    // ========== CREATE ==========
    private CompletableFuture<Void> executeCreate(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        if (regionName == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg create <nom> [cylinder <rayon> <hauteur>]"));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        // Vérifier si c'est une création de cylindre
        String shapeType = ctx.provided(arg2) ? ctx.get(arg2).toLowerCase() : null;

        RegionShape shape;
        if ("cylinder".equals(shapeType) || "cylindre".equals(shapeType) || "cyl".equals(shapeType)) {
            // Création d'un cylindre
            String radiusStr = ctx.provided(arg3) ? ctx.get(arg3) : null;
            if (radiusStr == null) {
                ctx.sendMessage(ColorUtil.parse("&cUsage: /rg create <nom> cylinder <rayon> <hauteur>"));
                ctx.sendMessage(ColorUtil.parse("&7Exemple: /rg create spawn cylinder 50 20"));
                return CompletableFuture.completedFuture(null);
            }

            // Parser rayon et hauteur (format: "rayon" ou "rayon hauteur")
            int radius;
            int height;
            try {
                String[] parts = radiusStr.split("\\s+");
                radius = Integer.parseInt(parts[0]);
                height = parts.length > 1 ? Integer.parseInt(parts[1]) : 20; // Hauteur par défaut: 20
            } catch (NumberFormatException e) {
                ctx.sendMessage(ColorUtil.parse("&cRayon et hauteur doivent être des nombres entiers."));
                return CompletableFuture.completedFuture(null);
            }

            if (radius <= 0 || height <= 0) {
                ctx.sendMessage(ColorUtil.parse("&cLe rayon et la hauteur doivent être positifs."));
                return CompletableFuture.completedFuture(null);
            }

            // Centre = position du joueur
            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int centerX = (int) Math.floor(pos.getX());
            int centerY = (int) Math.floor(pos.getY());
            int centerZ = (int) Math.floor(pos.getZ());

            shape = new CylinderShape(centerX, centerY, centerZ, radius, height);
            ctx.sendMessage(ColorUtil.parse("&7Création d'un cylindre: centre=(" + centerX + "," + centerY + "," + centerZ + "), r=" + radius + ", h=" + height));

        } else {
            // Création d'un cuboid depuis la sélection
            Optional<BoundingBox> boundsOpt;
            try {
                boundsOpt = SelectionHelper.getSelectionAsBoundingBox(player);
            } catch (Exception e) {
                ctx.sendMessage(ColorUtil.parse("&cErreur lors de la récupération de la sélection: " + e.getMessage()));
                ctx.sendMessage(ColorUtil.parse("&7Assurez-vous d'utiliser /pos1 et /pos2 pour définir une zone."));
                return CompletableFuture.completedFuture(null);
            }

            if (boundsOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cVous devez d'abord sélectionner une zone avec /pos1 et /pos2."));
                ctx.sendMessage(ColorUtil.parse("&7Ou utilisez: /rg create <nom> cylinder <rayon> <hauteur>"));
                return CompletableFuture.completedFuture(null);
            }

            shape = new CuboidShape(boundsOpt.get());
        }

        final RegionShape finalShape = shape;

        return regionService.getRegion(worldName, regionName).thenCompose(existingOpt -> {
            if (existingOpt.isPresent()) {
                ctx.sendMessage(ColorUtil.parse("&cUne région nommée '" + regionName + "' existe déjà."));
                return CompletableFuture.completedFuture(null);
            }

            return regionService.createRegion(regionName, worldName, finalShape, player.getUuid())
                .thenAccept(region -> {
                    ctx.sendMessage(ColorUtil.parse("&aRégion '" + regionName + "' créée avec succès!"));
                    ctx.sendMessage(ColorUtil.parse("&7Forme: " + finalShape.getShapeType()));
                    ctx.sendMessage(ColorUtil.parse("&7Volume: " + finalShape.getVolume() + " blocs"));
                });
        });
    }

    // ========== DELETE ==========
    private CompletableFuture<Void> executeDelete(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        if (regionName == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg delete <nom>"));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                return CompletableFuture.completedFuture(null);
            }

            var region = regionOpt.get();
            if (!region.isOwner(player.getUuid())) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'êtes pas propriétaire de cette région."));
                return CompletableFuture.completedFuture(null);
            }

            return regionService.deleteRegion(worldName, regionName).thenAccept(success -> {
                if (success) {
                    ctx.sendMessage(ColorUtil.parse("&aRégion '" + regionName + "' supprimée."));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&cImpossible de supprimer la région."));
                }
            });
        });
    }

    // ========== LIST ==========
    private CompletableFuture<Void> executeList(CommandContext ctx) {
        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        List<RegionImpl> regions = regionService.getRegionsByWorld(worldName);

        if (regions.isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&7Aucune région dans ce monde."));
            return CompletableFuture.completedFuture(null);
        }

        ctx.sendMessage(ColorUtil.parse("&6=== Régions dans " + worldName + " (" + regions.size() + ") ==="));
        for (RegionImpl region : regions) {
            String shapeIcon = region.getShape().getShapeType().equals("cylinder") ? "⬤" : "▢";
            ctx.sendMessage(ColorUtil.parse("&e" + shapeIcon + " " + region.getName() + " &7(" + region.getShape().getShapeType() + ", priorité: " + region.getPriority() + ", volume: " + region.getShape().getVolume() + ")"));
        }

        return CompletableFuture.completedFuture(null);
    }

    // ========== INFO ==========
    private CompletableFuture<Void> executeInfo(CommandContext ctx) {
        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        if (ctx.provided(arg1)) {
            String regionName = ctx.get(arg1);
            return regionService.getRegion(worldName, regionName).thenAccept(regionOpt -> {
                if (regionOpt.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                    return;
                }
                displayRegionInfo(ctx, regionOpt.get());
            });
        } else {
            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int x = (int) Math.floor(pos.getX());
            int y = (int) Math.floor(pos.getY());
            int z = (int) Math.floor(pos.getZ());

            List<RegionImpl> regions = regionService.getRegionsAt(worldName, x, y, z);

            if (regions.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&7Aucune région à votre position."));
            } else {
                ctx.sendMessage(ColorUtil.parse("&6Régions à votre position (" + regions.size() + "):"));
                for (RegionImpl region : regions) {
                    displayRegionInfo(ctx, region);
                }
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    private void displayRegionInfo(CommandContext ctx, RegionImpl region) {
        ctx.sendMessage(ColorUtil.parse("&6=== " + region.getName() + " ==="));
        ctx.sendMessage(ColorUtil.parse("&7Monde: &f" + region.getWorldName()));
        ctx.sendMessage(ColorUtil.parse("&7Priorité: &f" + region.getPriority()));

        // Affichage adapté selon la forme
        RegionShape shape = region.getShape();
        ctx.sendMessage(ColorUtil.parse("&7Forme: &f" + shape.getShapeType()));
        ctx.sendMessage(ColorUtil.parse("&7Détails: &f" + shape.getDescription()));
        ctx.sendMessage(ColorUtil.parse("&7Volume: &f" + shape.getVolume() + " blocs"));

        // AABB englobant (utile pour debug)
        BoundingBox bounds = shape.getEnclosingBounds();
        ctx.sendMessage(ColorUtil.parse("&7AABB: &f(" + bounds.getMinX() + "," + bounds.getMinY() + "," + bounds.getMinZ() +
            ") -> (" + bounds.getMaxX() + "," + bounds.getMaxY() + "," + bounds.getMaxZ() + ")"));

        if (!region.getOwners().isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&7Propriétaires: &f" + region.getOwners().size()));
        }
        if (!region.getMembers().isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&7Membres: &f" + region.getMembers().size()));
        }

        StringBuilder flagsStr = new StringBuilder();
        for (RegionFlag flag : RegionFlag.values()) {
            Object value = region.getRawFlag(flag);
            if (value != null) {
                if (flagsStr.length() > 0) flagsStr.append(", ");
                flagsStr.append(flag.getName()).append("=").append(value);
            }
        }
        if (flagsStr.length() > 0) {
            ctx.sendMessage(ColorUtil.parse("&7Flags: &f" + flagsStr));
        }
    }

    // ========== FLAG ==========
    private CompletableFuture<Void> executeFlag(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        String flagName = ctx.provided(arg2) ? ctx.get(arg2) : null;
        String valueStr = ctx.provided(arg3) ? ctx.get(arg3) : null;

        if (regionName == null || flagName == null || valueStr == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg flag <région> <flag> <valeur>"));
            ctx.sendMessage(ColorUtil.parse("&7Flags: " + getAvailableFlags()));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        RegionFlag flag = RegionFlag.fromName(flagName);
        if (flag == null) {
            ctx.sendMessage(ColorUtil.parse("&cFlag inconnu: " + flagName));
            ctx.sendMessage(ColorUtil.parse("&7Flags disponibles: " + getAvailableFlags()));
            return CompletableFuture.completedFuture(null);
        }

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                return CompletableFuture.completedFuture(null);
            }

            var region = regionOpt.get();
            if (!region.isOwner(player.getUuid())) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'êtes pas propriétaire de cette région."));
                return CompletableFuture.completedFuture(null);
            }

            Object value = flag.parseValue(valueStr);

            return regionService.setFlag(region, flag, value).thenRun(() -> {
                if (value == null) {
                    ctx.sendMessage(ColorUtil.parse("&aFlag '" + flagName + "' supprimé de '" + regionName + "'."));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&aFlag '" + flagName + "' défini à '" + value + "' pour '" + regionName + "'."));
                }
            });
        });
    }

    // ========== ADD MEMBER ==========
    private CompletableFuture<Void> executeAddMember(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        String playerName = ctx.provided(arg2) ? ctx.get(arg2) : null;

        if (regionName == null || playerName == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg addmember <région> <joueur>"));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                return CompletableFuture.completedFuture(null);
            }

            var region = regionOpt.get();
            if (!region.isOwner(player.getUuid())) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'êtes pas propriétaire de cette région."));
                return CompletableFuture.completedFuture(null);
            }

            return IslandiumPlugin.get().getPlayerManager().getPlayerUUID(playerName).thenCompose(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&cJoueur '" + playerName + "' non trouvé."));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetUuid = uuidOpt.get();
                if (region.isMember(targetUuid)) {
                    ctx.sendMessage(ColorUtil.parse("&cCe joueur est déjà membre."));
                    return CompletableFuture.completedFuture(null);
                }

                return regionService.addMember(region, targetUuid, player.getUuid()).thenRun(() -> {
                    ctx.sendMessage(ColorUtil.parse("&a" + playerName + " ajouté comme membre de '" + regionName + "'."));
                });
            });
        });
    }

    // ========== ADD OWNER ==========
    private CompletableFuture<Void> executeAddOwner(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        String playerName = ctx.provided(arg2) ? ctx.get(arg2) : null;

        if (regionName == null || playerName == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg addowner <région> <joueur>"));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                return CompletableFuture.completedFuture(null);
            }

            var region = regionOpt.get();
            if (!region.isOwner(player.getUuid())) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'êtes pas propriétaire de cette région."));
                return CompletableFuture.completedFuture(null);
            }

            return IslandiumPlugin.get().getPlayerManager().getPlayerUUID(playerName).thenCompose(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&cJoueur '" + playerName + "' non trouvé."));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetUuid = uuidOpt.get();
                if (region.isOwner(targetUuid)) {
                    ctx.sendMessage(ColorUtil.parse("&cCe joueur est déjà propriétaire."));
                    return CompletableFuture.completedFuture(null);
                }

                return regionService.addOwner(region, targetUuid, player.getUuid()).thenRun(() -> {
                    ctx.sendMessage(ColorUtil.parse("&a" + playerName + " ajouté comme propriétaire de '" + regionName + "'."));
                });
            });
        });
    }

    // ========== REMOVE MEMBER ==========
    private CompletableFuture<Void> executeRemoveMember(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        String playerName = ctx.provided(arg2) ? ctx.get(arg2) : null;

        if (regionName == null || playerName == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg removemember <région> <joueur>"));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                return CompletableFuture.completedFuture(null);
            }

            var region = regionOpt.get();
            if (!region.isOwner(player.getUuid())) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'êtes pas propriétaire de cette région."));
                return CompletableFuture.completedFuture(null);
            }

            return IslandiumPlugin.get().getPlayerManager().getPlayerUUID(playerName).thenCompose(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&cJoueur '" + playerName + "' non trouvé."));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetUuid = uuidOpt.get();

                return regionService.removeMember(region, targetUuid).thenRun(() -> {
                    ctx.sendMessage(ColorUtil.parse("&a" + playerName + " retiré des membres de '" + regionName + "'."));
                });
            });
        });
    }

    // ========== REMOVE OWNER ==========
    private CompletableFuture<Void> executeRemoveOwner(CommandContext ctx) {
        String regionName = ctx.provided(arg1) ? ctx.get(arg1) : null;
        String playerName = ctx.provided(arg2) ? ctx.get(arg2) : null;

        if (regionName == null || playerName == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg removeowner <région> <joueur>"));
            return CompletableFuture.completedFuture(null);
        }

        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                return CompletableFuture.completedFuture(null);
            }

            var region = regionOpt.get();
            if (!region.isOwner(player.getUuid())) {
                ctx.sendMessage(ColorUtil.parse("&cVous n'êtes pas propriétaire de cette région."));
                return CompletableFuture.completedFuture(null);
            }

            return IslandiumPlugin.get().getPlayerManager().getPlayerUUID(playerName).thenCompose(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&cJoueur '" + playerName + "' non trouvé."));
                    return CompletableFuture.completedFuture(null);
                }

                UUID targetUuid = uuidOpt.get();

                return regionService.removeOwner(region, targetUuid).thenRun(() -> {
                    ctx.sendMessage(ColorUtil.parse("&a" + playerName + " retiré des propriétaires de '" + regionName + "'."));
                });
            });
        });
    }

    // ========== GLOBAL ==========
    private CompletableFuture<Void> executeGlobal(CommandContext ctx) {
        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        String subAction = ctx.provided(arg1) ? ctx.get(arg1).toLowerCase() : "info";

        return switch (subAction) {
            case "create" -> executeGlobalCreate(ctx, worldName);
            case "delete", "remove" -> executeGlobalDelete(ctx, worldName);
            case "flag", "f" -> executeGlobalFlag(ctx, worldName);
            default -> executeGlobalInfo(ctx, worldName);
        };
    }

    private CompletableFuture<Void> executeGlobalInfo(CommandContext ctx, String worldName) {
        Optional<RegionImpl> globalOpt = regionService.getGlobalRegion(worldName);

        if (globalOpt.isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&7Aucune région globale dans ce monde."));
            ctx.sendMessage(ColorUtil.parse("&7Utilisez &e/rg global create &7pour en créer une."));
            return CompletableFuture.completedFuture(null);
        }

        RegionImpl global = globalOpt.get();
        ctx.sendMessage(ColorUtil.parse("&6=== Région Globale (" + worldName + ") ==="));
        ctx.sendMessage(ColorUtil.parse("&7ID: &f" + global.getId()));
        ctx.sendMessage(ColorUtil.parse("&7Priorité: &f" + global.getPriority() + " &7(très basse, les autres régions ont priorité)"));
        ctx.sendMessage(ColorUtil.parse("&7Forme: &f" + global.getShape().getDescription()));

        // Afficher les flags configurés
        StringBuilder flagsStr = new StringBuilder();
        for (RegionFlag flag : RegionFlag.values()) {
            Object value = global.getRawFlag(flag);
            if (value != null) {
                if (flagsStr.length() > 0) flagsStr.append(", ");
                flagsStr.append(flag.getName()).append("=").append(value);
            }
        }
        if (flagsStr.length() > 0) {
            ctx.sendMessage(ColorUtil.parse("&7Flags: &f" + flagsStr));
        } else {
            ctx.sendMessage(ColorUtil.parse("&7Flags: &f(aucun - valeurs par défaut)"));
        }

        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Void> executeGlobalCreate(CommandContext ctx, String worldName) {
        Optional<RegionImpl> existingOpt = regionService.getGlobalRegion(worldName);

        if (existingOpt.isPresent()) {
            ctx.sendMessage(ColorUtil.parse("&cUne région globale existe déjà dans ce monde."));
            ctx.sendMessage(ColorUtil.parse("&7Utilisez &e/rg global &7pour voir ses détails."));
            return CompletableFuture.completedFuture(null);
        }

        return regionService.getOrCreateGlobalRegion(worldName).thenAccept(global -> {
            ctx.sendMessage(ColorUtil.parse("&aRégion globale créée pour le monde '" + worldName + "'!"));
            ctx.sendMessage(ColorUtil.parse("&7Cette région couvre tout le monde avec une priorité très basse (" + RegionService.GLOBAL_REGION_PRIORITY + ")."));
            ctx.sendMessage(ColorUtil.parse("&7Les autres régions auront toujours priorité sur elle."));
            ctx.sendMessage(ColorUtil.parse("&7Utilisez &e/rg global flag <flag> <valeur> &7pour configurer les flags par défaut."));
        });
    }

    private CompletableFuture<Void> executeGlobalDelete(CommandContext ctx, String worldName) {
        Optional<RegionImpl> globalOpt = regionService.getGlobalRegion(worldName);

        if (globalOpt.isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&cAucune région globale dans ce monde."));
            return CompletableFuture.completedFuture(null);
        }

        return regionService.deleteRegion(worldName, RegionService.GLOBAL_REGION_NAME).thenAccept(success -> {
            if (success) {
                ctx.sendMessage(ColorUtil.parse("&aRégion globale supprimée."));
            } else {
                ctx.sendMessage(ColorUtil.parse("&cImpossible de supprimer la région globale."));
            }
        });
    }

    private CompletableFuture<Void> executeGlobalFlag(CommandContext ctx, String worldName) {
        String flagName = ctx.provided(arg2) ? ctx.get(arg2) : null;
        String valueStr = ctx.provided(arg3) ? ctx.get(arg3) : null;

        if (flagName == null || valueStr == null) {
            ctx.sendMessage(ColorUtil.parse("&cUsage: /rg global flag <flag> <valeur>"));
            ctx.sendMessage(ColorUtil.parse("&7Flags: " + getAvailableFlags()));
            return CompletableFuture.completedFuture(null);
        }

        Optional<RegionImpl> globalOpt = regionService.getGlobalRegion(worldName);
        if (globalOpt.isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&cAucune région globale dans ce monde."));
            ctx.sendMessage(ColorUtil.parse("&7Utilisez &e/rg global create &7pour en créer une."));
            return CompletableFuture.completedFuture(null);
        }

        RegionFlag flag = RegionFlag.fromName(flagName);
        if (flag == null) {
            ctx.sendMessage(ColorUtil.parse("&cFlag inconnu: " + flagName));
            ctx.sendMessage(ColorUtil.parse("&7Flags disponibles: " + getAvailableFlags()));
            return CompletableFuture.completedFuture(null);
        }

        RegionImpl global = globalOpt.get();
        Object value = flag.parseValue(valueStr);

        return regionService.setFlag(global, flag, value).thenRun(() -> {
            if (value == null) {
                ctx.sendMessage(ColorUtil.parse("&aFlag '" + flagName + "' supprimé de la région globale."));
            } else {
                ctx.sendMessage(ColorUtil.parse("&aFlag '" + flagName + "' défini à '" + value + "' pour la région globale."));
            }
        });
    }

    // ========== VISUALIZE ==========
    private CompletableFuture<Void> executeVisualize(CommandContext ctx) {
        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        // Si un nom de région est spécifié, visualiser cette région
        if (ctx.provided(arg1)) {
            String regionName = ctx.get(arg1);
            return regionService.getRegion(worldName, regionName).thenAccept(regionOpt -> {
                if (regionOpt.isEmpty()) {
                    ctx.sendMessage(ColorUtil.parse("&cRégion '" + regionName + "' non trouvée."));
                    return;
                }

                RegionImpl region = regionOpt.get();

                // Vérifier si c'est une région globale
                if (RegionService.isGlobalRegion(region)) {
                    ctx.sendMessage(ColorUtil.parse("&cLa région globale ne peut pas être visualisée (trop grande)."));
                    return;
                }

                RegionVisualizationService vizService = plugin.getVisualizationService();
                boolean nowActive = vizService.toggleVisualization(player, region);

                if (nowActive) {
                    ctx.sendMessage(ColorUtil.parse("&aVisualisation de '" + regionName + "' activée (5 min)."));
                    ctx.sendMessage(ColorUtil.parse("&7Forme: " + region.getShape().getShapeType()));
                } else {
                    ctx.sendMessage(ColorUtil.parse("&7Visualisation désactivée."));
                }
            });
        }

        // Sinon, visualiser la région à la position du joueur
        var transform = player.getTransformComponent();
        var pos = transform.getPosition();
        int x = (int) Math.floor(pos.getX());
        int y = (int) Math.floor(pos.getY());
        int z = (int) Math.floor(pos.getZ());

        List<RegionImpl> regions = regionService.getRegionsAt(worldName, x, y, z);

        // Exclure les régions globales
        regions = regions.stream()
            .filter(r -> !RegionService.isGlobalRegion(r))
            .toList();

        if (regions.isEmpty()) {
            ctx.sendMessage(ColorUtil.parse("&7Aucune région visualisable à votre position."));
            ctx.sendMessage(ColorUtil.parse("&7Usage: /rg vis <nom> pour visualiser une région spécifique."));
            return CompletableFuture.completedFuture(null);
        }

        // Visualiser la première région (plus haute priorité)
        RegionImpl region = regions.get(0);
        RegionVisualizationService vizService = plugin.getVisualizationService();
        boolean nowActive = vizService.toggleVisualization(player, region);

        if (nowActive) {
            ctx.sendMessage(ColorUtil.parse("&aVisualisation de '" + region.getName() + "' activée (5 min)."));
            ctx.sendMessage(ColorUtil.parse("&7Forme: " + region.getShape().getShapeType()));
        } else {
            ctx.sendMessage(ColorUtil.parse("&7Visualisation désactivée."));
        }

        return CompletableFuture.completedFuture(null);
    }

    // ========== UI ==========
    private CompletableFuture<Void> executeUI(CommandContext ctx) {
        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        var ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            ctx.sendMessage(ColorUtil.parse("&cErreur: impossible d'ouvrir l'interface."));
            return CompletableFuture.completedFuture(null);
        }

        var store = ref.getStore();
        var world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerRef == null) {
                ctx.sendMessage(ColorUtil.parse("&cErreur: PlayerRef non trouvé."));
                return;
            }

            RegionMainPage page = new RegionMainPage(playerRef, plugin, worldName);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }

    private String getAvailableFlags() {
        return Arrays.stream(RegionFlag.values())
            .map(RegionFlag::getName)
            .collect(Collectors.joining(", "));
    }

    public CompletableFuture<List<String>> tabComplete(CommandContext ctx, String partial) {
        if (!ctx.provided(arg1)) {
            return CompletableFuture.completedFuture(
                List.of("create", "delete", "list", "info", "flag", "addmember", "addowner", "removemember", "removeowner", "global", "visualize", "ui")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList()
            );
        }

        String action = ctx.get(actionArg).toLowerCase();
        Player player = ctx.senderAs(Player.class);
        String worldName = player.getWorld().getName();

        // Complétion pour global
        if (action.equals("global") && !ctx.provided(arg2)) {
            return CompletableFuture.completedFuture(
                List.of("info", "create", "delete", "flag")
                    .stream()
                    .filter(s -> s.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList()
            );
        }

        // Complétion des flags pour global
        if (action.equals("global") && ctx.provided(arg1) && ctx.get(arg1).equalsIgnoreCase("flag") && !ctx.provided(arg3)) {
            return CompletableFuture.completedFuture(
                Arrays.stream(RegionFlag.values())
                    .map(RegionFlag::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList()
            );
        }

        // Complétion des noms de régions (inclut visualize)
        if (!ctx.provided(arg2) && List.of("delete", "info", "flag", "addmember", "addowner", "removemember", "removeowner", "visualize", "vis").contains(action)) {
            return CompletableFuture.completedFuture(
                regionService.getRegionsByWorld(worldName).stream()
                    .map(RegionImpl::getName)
                    // Exclure les régions globales pour visualize
                    .filter(n -> !action.startsWith("vis") || !n.equals(RegionService.GLOBAL_REGION_NAME))
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList()
            );
        }

        // Complétion des flags
        if (ctx.provided(arg1) && !ctx.provided(arg3) && action.equals("flag")) {
            return CompletableFuture.completedFuture(
                Arrays.stream(RegionFlag.values())
                    .map(RegionFlag::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial.toLowerCase()))
                    .toList()
            );
        }

        return CompletableFuture.completedFuture(List.of());
    }
}
