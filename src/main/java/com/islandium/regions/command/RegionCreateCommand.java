package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.model.BoundingBox;
import com.islandium.regions.util.SelectionHelper;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /rg create <name> - Crée une nouvelle région.
 */
public class RegionCreateCommand extends RegionCommand {

    private final RequiredArg<String> nameArg;

    public RegionCreateCommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg create", "Créer une nouvelle région");
        this.nameArg = withRequiredArg("name", "Nom de la région", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        Player player = requirePlayer(ctx);
        String regionName = ctx.get(nameArg);
        String worldName = getWorldName(ctx);

        // Vérifier les permissions
        if (!hasPermission(ctx, "regions.create")) {
            return error(ctx, "Vous n'avez pas la permission de créer des régions.");
        }

        // Récupérer la sélection du joueur
        Optional<BoundingBox> boundsOpt = SelectionHelper.getSelectionAsBoundingBox(player);

        if (boundsOpt.isEmpty()) {
            return error(ctx, "Vous devez d'abord sélectionner une zone avec /pos1 et /pos2.");
        }

        BoundingBox bounds = boundsOpt.get();

        // Vérifier si la région existe déjà
        return regionService.getRegion(worldName, regionName).thenCompose(existingOpt -> {
            if (existingOpt.isPresent()) {
                return error(ctx, "Une région nommée '" + regionName + "' existe déjà dans ce monde.");
            }

            // Créer la région
            return regionService.createRegion(regionName, worldName, bounds, player.getUuid())
                .thenAccept(region -> {
                    sendSuccess(ctx, "Région '" + regionName + "' créée avec succès!");
                    sendMessage(ctx, "Volume: " + bounds.getVolume() + " blocs");
                    sendMessage(ctx, "Limites: (" + bounds.getMinX() + "," + bounds.getMinY() + "," + bounds.getMinZ() +
                        ") -> (" + bounds.getMaxX() + "," + bounds.getMaxY() + "," + bounds.getMaxZ() + ")");
                });
        });
    }
}
