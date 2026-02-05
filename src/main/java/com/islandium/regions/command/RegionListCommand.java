package com.islandium.regions.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.model.RegionImpl;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /rg list - Liste toutes les régions du monde actuel.
 */
public class RegionListCommand extends RegionCommand {

    public RegionListCommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg list", "Lister les régions");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        String worldName = getWorldName(ctx);
        List<RegionImpl> regions = regionService.getRegionsByWorld(worldName);

        if (regions.isEmpty()) {
            sendMessage(ctx, "Aucune région dans ce monde.");
            return complete();
        }

        ctx.sendMessage(ColorUtil.parse("&6=== Régions dans " + worldName + " (" + regions.size() + ") ==="));

        for (RegionImpl region : regions) {
            String info = String.format("&e%s &7(priorité: %d, volume: %d)",
                region.getName(),
                region.getPriority(),
                region.getBounds().getVolume()
            );
            ctx.sendMessage(ColorUtil.parse(info));
        }

        return complete();
    }
}
