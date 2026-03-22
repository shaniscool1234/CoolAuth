package com.coolauth.commands;

import com.coolauth.CoolAuth;
import com.coolauth.listeners.RecoveryGUI;
import com.coolauth.managers.AuthManager;
import com.coolauth.managers.RecoveryManager;
import com.coolauth.storage.AuthStorage;
import com.coolauth.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RecoveryCommand implements CommandExecutor {

    private final CoolAuth plugin;
    private final AuthStorage storage;
    private final AuthManager authManager;
    private final RecoveryManager recoveryManager;

    public RecoveryCommand(CoolAuth plugin) {
        this.plugin = plugin;
        this.storage = plugin.getStorage();
        this.authManager = plugin.getAuthManager();
        this.recoveryManager = plugin.getRecoveryManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (!plugin.getConfig().getBoolean("recovery.enabled", false)) {
            player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("recovery_disabled")));
            return true;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Must be registered
            if (!storage.isRegistered(player.getUniqueId())) {
                player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("recovery_not_registered")));
                return;
            }

            // Must NOT be logged in
            if (authManager.isAuthenticated(player)) {
                player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("recovery_already_logged_in")));
                return;
            }

            // Check cooldown
            long cooldownRemaining = recoveryManager.getCooldownRemaining(player.getUniqueId());
            if (cooldownRemaining > 0) {
                String timeStr = ColorUtil.formatTime(cooldownRemaining);
                player.sendMessage(plugin.color(plugin.getPrefix() +
                        plugin.getMessage("recovery_cooldown").replace("%time%", timeStr)));
                return;
            }

            // Open GUI on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                new RecoveryGUI(plugin).openGUI(player);
            });
        });

        return true;
    }
}
