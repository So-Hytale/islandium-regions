package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.ui.RegionMainPage;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Commande /rg ui - Ouvre l'interface graphique de gestion des régions.
 */
public class RegionUICommand extends RegionCommand {

    public RegionUICommand(@NotNull RegionsPlugin plugin) {
        super(plugin, "rg ui", "Ouvrir l'interface de gestion des regions");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            return error(ctx, "Cette commande nécessite un joueur.");
        }

        Player player = requirePlayer(ctx);
        String worldName = getWorldName(ctx);

        var ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return error(ctx, "Erreur: impossible d'ouvrir l'interface.");
        }

        var store = ref.getStore();
        var world = ((com.hypixel.hytale.server.core.universe.world.storage.EntityStore) store.getExternalData()).getWorld();

        return CompletableFuture.runAsync(() -> {
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());

            if (playerRef == null) {
                sendError(ctx, "Erreur: PlayerRef non trouve.");
                return;
            }

            RegionMainPage page = new RegionMainPage(playerRef, plugin, worldName);
            player.getPageManager().openCustomPage(ref, store, page);
        }, world);
    }
}
