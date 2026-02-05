package com.islandium.regions.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.islandium.regions.RegionsPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Gestionnaire des commandes de régions.
 */
public class RegionCommandManager {

    private final RegionsPlugin plugin;
    private final List<AbstractCommand> commands = new ArrayList<>();

    public RegionCommandManager(@NotNull RegionsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre toutes les commandes.
     */
    public void registerAll() {
        plugin.log(Level.INFO, "Registering region commands...");

        try {
            // Commande principale /rg
            RgCommand rgCommand = new RgCommand(plugin);
            plugin.log(Level.INFO, "Created RgCommand instance");
            register(rgCommand);
            plugin.log(Level.INFO, "Registered " + commands.size() + " region commands successfully");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to register commands: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void register(@NotNull AbstractCommand command) {
        try {
            plugin.log(Level.INFO, "Registering command: " + command.getName());
            plugin.getCommandRegistry().registerCommand(command);
            commands.add(command);
            plugin.log(Level.INFO, "Command '" + command.getName() + "' registered successfully");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to register command '" + command.getName() + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    @NotNull
    public List<AbstractCommand> getCommands() {
        return commands;
    }
}
