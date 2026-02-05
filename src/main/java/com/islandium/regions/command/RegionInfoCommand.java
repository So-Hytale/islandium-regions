package com.islandium.regions.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.flag.RegionFlag;
import com.islandium.regions.model.RegionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Commande /rg info [region] - Affiche les informations d'une région.
 */
public class RegionInfoCommand extends RegionCommand {

    private final OptionalArg<String> regionArg;

    public RegionInfoCommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg info", "Afficher les informations d'une région");
        this.regionArg = withOptionalArg("region", "Nom de la région", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        Player player = requirePlayer(ctx);
        String worldName = getWorldName(ctx);

        if (ctx.provided(regionArg)) {
            // Afficher une région spécifique
            String regionName = ctx.get(regionArg);
            return regionService.getRegion(worldName, regionName).thenAccept(regionOpt -> {
                if (regionOpt.isEmpty()) {
                    sendError(ctx, "Région '" + regionName + "' non trouvée.");
                    return;
                }
                displayRegionInfo(ctx, regionOpt.get());
            });
        } else {
            // Afficher la région à la position actuelle
            var transform = player.getTransformComponent();
            var pos = transform.getPosition();
            int x = (int) Math.floor(pos.getX());
            int y = (int) Math.floor(pos.getY());
            int z = (int) Math.floor(pos.getZ());

            List<RegionImpl> regions = regionService.getRegionsAt(worldName, x, y, z);

            if (regions.isEmpty()) {
                sendMessage(ctx, "Aucune région à votre position.");
                return complete();
            }

            // Afficher toutes les régions à cette position
            sendMessage(ctx, "Régions à votre position (" + regions.size() + "):");
            for (RegionImpl region : regions) {
                displayRegionInfo(ctx, region);
            }
            return complete();
        }
    }

    private void displayRegionInfo(@NotNull CommandContext ctx, @NotNull RegionImpl region) {
        ctx.sendMessage(ColorUtil.parse("&6=== Région: &e" + region.getName() + " &6==="));
        ctx.sendMessage(ColorUtil.parse("&7Monde: &f" + region.getWorldName()));
        ctx.sendMessage(ColorUtil.parse("&7Priorité: &f" + region.getPriority()));
        ctx.sendMessage(ColorUtil.parse("&7Limites: &f(" +
            region.getBounds().getMinX() + "," +
            region.getBounds().getMinY() + "," +
            region.getBounds().getMinZ() + ") -> (" +
            region.getBounds().getMaxX() + "," +
            region.getBounds().getMaxY() + "," +
            region.getBounds().getMaxZ() + ")"));
        ctx.sendMessage(ColorUtil.parse("&7Volume: &f" + region.getBounds().getVolume() + " blocs"));

        // Owners
        if (!region.getOwners().isEmpty()) {
            String owners = region.getOwners().stream()
                .map(UUID::toString)
                .collect(Collectors.joining(", "));
            ctx.sendMessage(ColorUtil.parse("&7Propriétaires: &f" + owners));
        }

        // Members
        if (!region.getMembers().isEmpty()) {
            String members = region.getMembers().stream()
                .map(UUID::toString)
                .collect(Collectors.joining(", "));
            ctx.sendMessage(ColorUtil.parse("&7Membres: &f" + members));
        }

        // Flags non-defaults
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
}
