package com.coolauth.managers;

import com.coolauth.CoolAuth;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class AuthManager {

    private final CoolAuth plugin;

    private final Set<UUID> authenticatedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Integer> timeoutTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> loginAttempts = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();

    // Recovery chat input
    private final Set<UUID> awaitingRecoveryInput = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> pendingRecoveryReason = new ConcurrentHashMap<>();

    private final int timeoutSeconds;
    private final int maxAttempts;
    private final int sessionMinutes;

    public record Session(String ip, long expiryTime) {}

    public AuthManager(CoolAuth plugin) {
        this.plugin = plugin;
        this.timeoutSeconds = plugin.getConfig().getInt("security.auth_timeout", 30);
        this.maxAttempts = plugin.getConfig().getInt("security.max_login_attempts", 3);
        this.sessionMinutes = plugin.getConfig().getInt("security.session_timeout", 10);
    }

    // --- Authentication State ---

    public boolean isAuthenticated(UUID uuid) {
        return authenticatedPlayers.contains(uuid);
    }

    public boolean isAuthenticated(Player player) {
        return isAuthenticated(player.getUniqueId());
    }

    public void setAuthenticated(UUID uuid, boolean authenticated) {
        if (authenticated) {
            authenticatedPlayers.add(uuid);
        } else {
            authenticatedPlayers.remove(uuid);
        }
    }

    // --- Login Attempts ---

    public int incrementLoginAttempts(UUID uuid) {
        int attempts = loginAttempts.getOrDefault(uuid, 0) + 1;
        loginAttempts.put(uuid, attempts);
        return attempts;
    }

    public void resetLoginAttempts(UUID uuid) {
        loginAttempts.remove(uuid);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    // --- Sessions ---

    public boolean hasValidSession(Player player) {
        UUID uuid = player.getUniqueId();
        if (!sessions.containsKey(uuid)) return false;

        Session session = sessions.get(uuid);
        String currentIp = player.getAddress().getAddress().getHostAddress();

        if (System.currentTimeMillis() < session.expiryTime && session.ip.equals(currentIp)) {
            return true;
        }

        sessions.remove(uuid);
        return false;
    }

    public void createSession(Player player) {
        if (sessionMinutes <= 0) return;

        long expiry = System.currentTimeMillis() + (sessionMinutes * 60 * 1000L);
        String ip = player.getAddress().getAddress().getHostAddress();
        sessions.put(player.getUniqueId(), new Session(ip, expiry));
    }

    public void removeSession(UUID uuid) {
        sessions.remove(uuid);
    }

    // --- Timeout Tasks ---

    public void startTimeoutTask(Player player) {
        UUID uuid = player.getUniqueId();

        int taskId = new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline() && !isAuthenticated(uuid)) {
                    player.kickPlayer(plugin.color(plugin.getMessage("kick_timeout")));
                }
                timeoutTasks.remove(uuid);
            }
        }.runTaskLater(plugin, timeoutSeconds * 20L).getTaskId();

        timeoutTasks.put(uuid, taskId);
    }

    public void cancelTimeoutTask(UUID uuid) {
        if (timeoutTasks.containsKey(uuid)) {
            Bukkit.getScheduler().cancelTask(timeoutTasks.remove(uuid));
        }
    }

    // --- Finish Authentication ---

    public void finishAuth(Player player, boolean sessionResumed) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            UUID uuid = player.getUniqueId();

            authenticatedPlayers.add(uuid);
            resetLoginAttempts(uuid);
            cancelTimeoutTask(uuid);

            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.resetTitle();

            createSession(player);

            if (sessionResumed) {
                player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("session_restored")));
            } else {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> plugin.getStorage().updatePlayerInfo(player));
                player.sendMessage(plugin.color(plugin.getPrefix() + plugin.getMessage("success")));
            }
        });
    }

    // --- Recovery Input ---

    public boolean isAwaitingRecoveryInput(UUID uuid) {
        return awaitingRecoveryInput.contains(uuid);
    }

    public void setAwaitingRecoveryInput(UUID uuid, boolean awaiting) {
        if (awaiting) {
            awaitingRecoveryInput.add(uuid);
        } else {
            awaitingRecoveryInput.remove(uuid);
        }
    }

    public void setPendingRecoveryReason(UUID uuid, String reason) {
        pendingRecoveryReason.put(uuid, reason);
    }

    public String getPendingRecoveryReason(UUID uuid) {
        return pendingRecoveryReason.remove(uuid);
    }

    // --- Cleanup ---

    public void cleanupPlayer(UUID uuid) {
        authenticatedPlayers.remove(uuid);
        loginAttempts.remove(uuid);
        awaitingRecoveryInput.remove(uuid);
        pendingRecoveryReason.remove(uuid);
        cancelTimeoutTask(uuid);
    }

    public void shutdown() {
        timeoutTasks.values().forEach(Bukkit.getScheduler()::cancelTask);
        timeoutTasks.clear();
    }
}
