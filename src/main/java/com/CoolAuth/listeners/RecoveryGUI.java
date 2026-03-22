package com.coolauth.listeners;

import com.coolauth.CoolAuth;
import com.coolauth.managers.AuthManager;
import com.coolauth.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class RecoveryGUI implements Listener {

    private final CoolAuth plugin;
    private final AuthManager authManager;
    private final String guiTitle;

    public RecoveryGUI(CoolAuth plugin) {
        this.plugin = plugin;
        this.authManager = plugin.getAuthManager();
        this.guiTitle = plugin.color(plugin.getConfig().getString("recovery.gui_title", "&8&lPassword Recovery"));
    }

    public void openGUI(Player player) {
        List<String> reasons = plugin.getConfig().getStringList("recovery.reasons");

        int size = Math.max(9, (int) Math.ceil(reasons.size() / 9.0) * 9);
        size = Math.min(size, 54);

        Inventory gui = Bukkit.createInventory(null, size, guiTitle);

        Material[] materials = {
                Material.PAPER,
                Material.BOOK,
                Material.WRITABLE_BOOK,
                Material.NAME_TAG,
                Material.COMPASS,
                Material.CLOCK,
                Material.MAP,
                Material.FILLED_MAP,
                Material.KNOWLEDGE_BOOK
        };

        for (int i = 0; i < reasons.size() && i < size - 1; i++) {
            String reason = reasons.get(i);

            ItemStack item = new ItemStack(materials[i % materials.length]);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(plugin.color("&e" + reason));

            List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(plugin.color("&7Click to select this reason"));
            lore.add(plugin.color("&7You will then type more details"));
            meta.setLore(lore);

            item.setItemMeta(meta);
            gui.setItem(i, item);
        }

        // Cancel button
        ItemStack cancel = new ItemStack(Material.BARRIER);
        ItemMeta cancelMeta = cancel.getItemMeta();
        cancelMeta.setDisplayName(plugin.color("&c&lCancel"));
        List<String> cancelLore = new ArrayList<>();
        cancelLore.add("");
        cancelLore.add(plugin.color("&7Click to cancel recovery"));
        cancelMeta.setLore(cancelLore);
        cancel.setItemMeta(cancelMeta);
        gui.setItem(size - 1, cancel);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle() == null) return;
        if (!event.getView().getTitle().equals(guiTitle)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Cancel button
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("recovery_cancelled")));
            return;
        }

        // Get the reason from item name
        if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
            String reason = ColorUtil.stripColor(clicked.getItemMeta().getDisplayName());

            player.closeInventory();

            // Store reason and await chat input
            authManager.setPendingRecoveryReason(player.getUniqueId(), reason);
            authManager.setAwaitingRecoveryInput(player.getUniqueId(), true);

            player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("recovery_enter_details")));
        }
    }
}
