package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.regions.RegionsPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Commande /rg delete <name> - Supprime une région.
 */
public class RegionDeleteCommand extends RegionCommand {

    private final RequiredArg<String> nameArg;

    public RegionDeleteCommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg delete", "Supprimer une région");
        this.nameArg = withRequiredArg("name", "Nom de la région", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        String regionName = ctx.get(nameArg);
        String worldName = getWorldName(ctx);

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                return error(ctx, "Région '" + regionName + "' non trouvée.");
            }

            var region = regionOpt.get();

            // Vérifier les permissions
            if (!canModifyRegion(ctx, region)) {
                return error(ctx, "Vous n'avez pas la permission de supprimer cette région.");
            }

            return regionService.deleteRegion(worldName, regionName).thenAccept(success -> {
                if (success) {
                    sendSuccess(ctx, "Région '" + regionName + "' supprimée.");
                } else {
                    sendError(ctx, "Impossible de supprimer la région.");
                }
            });
        });
    }

    @Override
    public CompletableFuture<List<String>> tabComplete(CommandContext ctx, String partial) {
        if (!isPlayer(ctx)) {
            return CompletableFuture.completedFuture(List.of());
        }

        String worldName = getWorldName(ctx);
        String lower = partial.toLowerCase();

        return CompletableFuture.completedFuture(
            regionService.getRegionsByWorld(worldName).stream()
                .map(r -> r.getName())
                .filter(name -> name.toLowerCase().startsWith(lower))
                .collect(Collectors.toList())
        );
    }
}
