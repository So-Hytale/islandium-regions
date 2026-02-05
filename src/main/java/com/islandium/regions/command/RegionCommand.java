package com.islandium.regions.command;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.regions.RegionsPlugin;
import com.islandium.regions.model.RegionImpl;
import com.islandium.regions.service.RegionService;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Classe de base pour les commandes de régions.
 */
public abstract class RegionCommand extends AbstractCommand {

    protected final RegionsPlugin plugin;
    protected final RegionService regionService;

    protected RegionCommand(@NotNull RegionsPlugin plugin, @NotNull String name, @NotNull String description) {
        super(name, description);
        this.plugin = plugin;
        this.regionService = plugin.getRegionService();
    }

    /**
     * Envoie un message formaté.
     */
    protected void sendMessage(@NotNull CommandContext ctx, @NotNull String message) {
        ctx.sendMessage(ColorUtil.parse("&8[&6Regions&8] &7" + message));
    }

    /**
     * Envoie un message d'erreur.
     */
    protected void sendError(@NotNull CommandContext ctx, @NotNull String message) {
        ctx.sendMessage(ColorUtil.parse("&8[&6Regions&8] &c" + message));
    }

    /**
     * Envoie un message de succès.
     */
    protected void sendSuccess(@NotNull CommandContext ctx, @NotNull String message) {
        ctx.sendMessage(ColorUtil.parse("&8[&6Regions&8] &a" + message));
    }

    /**
     * Vérifie si le sender est un joueur.
     */
    protected boolean isPlayer(@NotNull CommandContext ctx) {
        return ctx.isPlayer();
    }

    /**
     * Récupère le joueur sender.
     */
    @NotNull
    protected Player requirePlayer(@NotNull CommandContext ctx) {
        if (!ctx.isPlayer()) {
            sendError(ctx, "Cette commande nécessite un joueur.");
            throw new IllegalStateException("Command requires player");
        }
        return ctx.senderAs(Player.class);
    }

    /**
     * Vérifie si le sender a une permission.
     * Utilise le système de permissions natif Hytale qui est synchronisé avec la BDD Islandium.
     */
    protected boolean hasPermission(@NotNull CommandContext ctx, @NotNull String permission) {
        UUID uuid = ctx.sender().getUuid();
        PermissionsModule perms = PermissionsModule.get();

        // Les opérateurs ont toutes les permissions
        if (perms.getGroupsForUser(uuid).contains("OP")) {
            return true;
        }

        // Vérifier la permission via le système natif
        return perms.hasPermission(uuid, permission);
    }

    /**
     * Vérifie si un joueur peut modifier une région.
     */
    protected boolean canModifyRegion(@NotNull CommandContext ctx, @NotNull RegionImpl region) {
        UUID uuid = ctx.sender().getUuid();
        // Admin ou owner de la région
        return hasPermission(ctx, "regions.admin") || region.isOwner(uuid);
    }

    /**
     * Récupère le nom du monde du joueur.
     */
    @NotNull
    protected String getWorldName(@NotNull CommandContext ctx) {
        Player player = requirePlayer(ctx);
        return player.getWorld().getName();
    }

    /**
     * Retourne un CompletableFuture vide.
     */
    protected CompletableFuture<Void> complete() {
        return CompletableFuture.completedFuture(null);
    }

    /**
     * Retourne un CompletableFuture d'erreur.
     */
    protected CompletableFuture<Void> error(@NotNull CommandContext ctx, @NotNull String message) {
        sendError(ctx, message);
        return complete();
    }

    /**
     * Tab completion par défaut.
     */
    public CompletableFuture<List<String>> tabComplete(CommandContext ctx, String partial) {
        return CompletableFuture.completedFuture(List.of());
    }
}
