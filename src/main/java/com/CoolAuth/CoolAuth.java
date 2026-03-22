package com.coolauth;

import com.coolauth.commands.AdminCommands;
import com.coolauth.commands.AuthCommands;
import com.coolauth.commands.RecoveryCommand;
import com.coolauth.listeners.AuthListener;
import com.coolauth.listeners.RecoveryGUI;
import com.coolauth.listeners.RestrictionListener;
import com.coolauth.managers.AuthManager;
import com.coolauth.managers.PasswordValidator;
import com.coolauth.managers.RecoveryManager;
import com.coolauth.storage.AuthStorage;
import com.coolauth.util.ColorUtil;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class CoolAuth extends JavaPlugin {

    private static CoolAuth instance;

    private AuthStorage storage;
    private AuthManager authManager;
    private PasswordValidator passwordValidator;
    private RecoveryManager recoveryManager;
    private List<String> bannedIps;

    @Override
    public void onEnable() {
        instance = this;

        // Config
        saveDefaultConfig();
        loadConfigValues();

        // Storage
        try {
            storage = new AuthStorage(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize storage! Disabling.");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Managers
        authManager = new AuthManager(this);
        passwordValidator = new PasswordValidator(this);
        recoveryManager = new RecoveryManager(this, storage);

        // Listeners
        getServer().getPluginManager().registerEvents(new AuthListener(this), this);
        getServer().getPluginManager().registerEvents(new RestrictionListener(this), this);
        getServer().getPluginManager().registerEvents(new RecoveryGUI(this), this);

        // Commands
        AuthCommands authCommands = new AuthCommands(this);
        getCommand("register").setExecutor(authCommands);
        getCommand("login").setExecutor(authCommands);
        getCommand("changepassword").setExecutor(authCommands);

        AdminCommands adminCommands = new AdminCommands(this);
        getCommand("coolauth").setExecutor(adminCommands);
        getCommand("coolauth").setTabCompleter(adminCommands);

        getCommand("passwordrecovery").setExecutor(new RecoveryCommand(this));

        getLogger().info("CoolAuth v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.close();
        if (recoveryManager != null) recoveryManager.saveRecoveryData();
        if (authManager != null) authManager.shutdown();
        instance = null;
        getLogger().info("CoolAuth disabled!");
    }

    public void loadConfigValues() {
        bannedIps = new ArrayList<>(getConfig().getStringList("security.banned_ips"));
    }

    public void reload() {
        reloadConfig();
        loadConfigValues();
        passwordValidator = new PasswordValidator(this);
        if (storage != null) storage.close();
        storage = new AuthStorage(this);
        recoveryManager = new RecoveryManager(this, storage);
    }

    // --- Getters ---

    public static CoolAuth getInstance() {
        return instance;
    }

    public AuthStorage getStorage() {
        return storage;
    }

    public AuthManager getAuthManager() {
        return authManager;
    }

    public PasswordValidator getPasswordValidator() {
        return passwordValidator;
    }

    public RecoveryManager getRecoveryManager() {
        return recoveryManager;
    }

    public List<String> getBannedIps() {
        return bannedIps;
    }

    public void saveBannedIps() {
        getConfig().set("security.banned_ips", bannedIps);
        saveConfig();
    }

    // --- Utility ---

    public String color(String s) {
        return ColorUtil.color(s);
    }

    public String getMessage(String path) {
        return getConfig().getString("messages." + path, "&cMissing message: " + path);
    }

    public String getPrefix() {
        return getMessage("prefix");
    }
}
