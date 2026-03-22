package com.coolauth.listeners;

import com.coolauth.CoolAuth;
import com.coolauth.managers.AuthManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;

public class RestrictionListener implements Listener {

    private final CoolAuth plugin;
    private final AuthManager authManager;

    public RestrictionListener(CoolAuth plugin) {
        this.plugin = plugin;
        this.authManager = plugin.getAuthManager();
    }

    private boolean isUnauth(Player player) {
        return !authManager.isAuthenticated(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        if (isUnauth(event.getPlayer())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            // Allow head rotation, block X/Y/Z movement
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location newTo = from.clone();
                newTo.setYaw(to.getYaw());
                newTo.setPitch(to.getPitch());
                event.setTo(newTo);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();

        // Skip if awaiting recovery input (handled separately)
        if (authManager.isAwaitingRecoveryInput(player.getUniqueId())) return;

        if (isUnauth(player)) {
            event.setCancelled(true);
        } else {
            // Remove unauth players from recipients
            event.getRecipients().removeIf(this::isUnauth);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (isUnauth(event.getPlayer())) {
            String cmd = event.getMessage().split(" ")[0].toLowerCase();
            if (!cmd.equals("/login") && !cmd.equals("/register") && !cmd.equals("/passwordrecovery") && !cmd.equals("/pr")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (isUnauth(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isUnauth(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isUnauth(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (isUnauth(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && isUnauth(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isUnauth(player)) {
            // Allow recovery GUI clicks
            String title = event.getView().getTitle();
            if (title.contains("Recovery")) return;
            event.setCancelled(true);
        }
    }
}
