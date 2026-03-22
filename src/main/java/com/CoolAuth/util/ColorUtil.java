package com.coolauth.util;

import org.bukkit.ChatColor;

public class ColorUtil {

    public static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String stripColor(String s) {
        if (s == null) return "";
        return ChatColor.stripColor(color(s));
    }

    public static String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
