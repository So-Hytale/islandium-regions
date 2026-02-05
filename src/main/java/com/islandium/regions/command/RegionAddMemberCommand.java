package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.core.IslandiumPlugin;
import com.islandium.regions.RegionsPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /rg addmember <region> <player> - Ajoute un membre à une région.
 */
public class RegionAddMemberCommand extends RegionCommand {

    private final RequiredArg<String> regionArg;
    private final RequiredArg<String> playerArg;

    public RegionAddMemberCommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg addmember", "Ajouter un membre à une région");
        this.regionArg = withRequiredArg("region", "Nom de la région", ArgTypes.STRING);
        this.playerArg = withRequiredArg("player", "Nom du joueur", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        String regionName = ctx.get(regionArg);
        String playerName = ctx.get(playerArg);
        String worldName = getWorldName(ctx);
        UUID senderUuid = ctx.sender().getUuid();

        return regionService.getRegion(worldName, regionName).thenCompose(regionOpt -> {
            if (regionOpt.isEmpty()) {
                return error(ctx, "Région '" + regionName + "' non trouvée.");
            }

            var region = regionOpt.get();

            // Vérifier les permissions
            if (!canModifyRegion(ctx, region)) {
                return error(ctx, "Vous n'avez pas la permission de modifier cette région.");
            }

            // Trouver le joueur cible via PlayerManager
            return IslandiumPlugin.get().getPlayerManager().getPlayerUUID(playerName).thenCompose(uuidOpt -> {
                if (uuidOpt.isEmpty()) {
                    return error(ctx, "Joueur '" + playerName + "' non trouvé.");
                }

                UUID targetUuid = uuidOpt.get();

                // Vérifier si déjà membre
                if (region.isMember(targetUuid)) {
                    return error(ctx, "Ce joueur est déjà membre de cette région.");
                }

                return regionService.addMember(region, targetUuid, senderUuid).thenRun(() -> {
                    sendSuccess(ctx, "Joueur '" + playerName + "' ajouté comme membre de la région '" + regionName + "'.");
                });
            });
        });
    }
}
