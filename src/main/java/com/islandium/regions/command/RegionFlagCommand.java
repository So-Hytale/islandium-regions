package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Commande /rg flag <region> <flag> <value> - Définit un flag sur une région.
 */
public class RegionFlagCommand extends RegionCommand {

    private final RequiredArg<String> regionArg;
    private final RequiredArg<String> flagArg;
    private final RequiredArg<String> valueArg;

    public RegionFlagCommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg flag", "Définir un flag sur une région");
        this.regionArg = withRequiredArg("region", "Nom de la région", ArgTypes.STRING);
        this.flagArg = withRequiredArg("flag", "Nom du flag", ArgTypes.STRING);
        this.valueArg = withRequiredArg("value", "Valeur du flag", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        String regionName = ctx.get(regionArg);
        String flagName = ctx.get(flagArg);
        String valueStr = ctx.get(valueArg);
        String worldName = getWorldName(ctx);

        // Trouver le flag
        RegionFlag flag = RegionFlag.fromName(flagName);
        if (flag == null) {
            return error(ctx, "Flag inconnu: " + flagName + ". Flags disponibles: " + getAvailableFlags());
        }

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                return error(ctx, "Région '" + regionName + "' non trouvée.");
            }

            var region = regionOpt.get();

            // Vérifier les permissions
            if (!canModifyRegion(ctx, region)) {
                return error(ctx, "Vous n'avez pas la permission de modifier cette région.");
            }

            // Parser la valeur
            Object value = flag.parseValue(valueStr);

            return regionService.setFlag(region, flag, value).thenRun(() -> {
                if (value == null) {
                    sendSuccess(ctx, "Flag '" + flagName + "' supprimé de la région '" + regionName + "'.");
                } else {
                    sendSuccess(ctx, "Flag '" + flagName + "' défini à '" + value + "' pour la région '" + regionName + "'.");
                }
            });
        });
    }

    private String getAvailableFlags() {
        return Arrays.stream(RegionFlag.values())
            .map(RegionFlag::getName)
            .collect(Collectors.joining(", "));
    }

    @Override
    public CompletableFuture<List<String>> tabComplete(CommandContext ctx, String partial) {
        // TODO: Implémenter la complétion contextuelle basée sur l'argument actuel
        return CompletableFuture.completedFuture(List.of());
    }
}
