package com.coolauth.managers;

import com.coolauth.CoolAuth;

public class PasswordValidator {

    private final CoolAuth plugin;
    private final boolean enabled;
    private final int minLength;
    private final int maxLength;
    private final boolean requireUppercase;
    private final boolean requireLowercase;
    private final boolean requireNumbers;
    private final boolean requireSpecial;
    private final String specialChars;

    public PasswordValidator(CoolAuth plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("password.enabled", true);
        this.minLength = plugin.getConfig().getInt("password.min_length", 8);
        this.maxLength = plugin.getConfig().getInt("password.max_length", 32);
        this.requireUppercase = plugin.getConfig().getBoolean("password.require_uppercase", true);
        this.requireLowercase = plugin.getConfig().getBoolean("password.require_lowercase", true);
        this.requireNumbers = plugin.getConfig().getBoolean("password.require_numbers", true);
        this.requireSpecial = plugin.getConfig().getBoolean("password.require_special", false);
        this.specialChars = plugin.getConfig().getString("password.special_characters", "!@#$%^&*()_+-=[]{}|;:,.<>?");
    }

    /**
     * Validates a password against the configured requirements.
     * @param password The password to validate
     * @return null if valid, or an error message if invalid
     */
    public String validate(String password) {
        if (!enabled) return null;

        if (password.length() < minLength) {
            return plugin.getMessage("password_too_short").replace("%min%", String.valueOf(minLength));
        }

        if (password.length() > maxLength) {
            return plugin.getMessage("password_too_long").replace("%max%", String.valueOf(maxLength));
        }

        if (requireUppercase && !containsUppercase(password)) {
            return plugin.getMessage("password_needs_uppercase");
        }

        if (requireLowercase && !containsLowercase(password)) {
            return plugin.getMessage("password_needs_lowercase");
        }

        if (requireNumbers && !containsNumber(password)) {
            return plugin.getMessage("password_needs_number");
        }

        if (requireSpecial && !containsSpecial(password)) {
            return plugin.getMessage("password_needs_special").replace("%chars%", specialChars);
        }

        return null;
    }

    private boolean containsUppercase(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isUpperCase(c)) return true;
        }
        return false;
    }

    private boolean containsLowercase(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isLowerCase(c)) return true;
        }
        return false;
    }

    private boolean containsNumber(String s) {
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) return true;
        }
        return false;
    }

    private boolean containsSpecial(String s) {
        for (char c : s.toCharArray()) {
            if (specialChars.indexOf(c) != -1) return true;
        }
        return false;
    }

    // Getters
    public int getMinLength() { return minLength; }
    public int getMaxLength() { return maxLength; }
    public boolean isRequireUppercase() { return requireUppercase; }
    public boolean isRequireLowercase() { return requireLowercase; }
    public boolean isRequireNumbers() { return requireNumbers; }
    public boolean isRequireSpecial() { return requireSpecial; }
    public String getSpecialChars() { return specialChars; }
}
