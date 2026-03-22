package com.coolauth.storage;

import com.coolauth.CoolAuth;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class AuthStorage {

    private final CoolAuth plugin;
    private Connection connection;
    private File yamlFile;
    private FileConfiguration yamlConfig;
    private final String type;
    private final String table;

    private final boolean encrypt;
    private final String encryptionType;
    private final String secretKey;
    private final int bcryptRounds;

    public AuthStorage(CoolAuth plugin) {
        this.plugin = plugin;
        this.type = plugin.getConfig().getString("storage.type", "YAML").toUpperCase();
        this.table = plugin.getConfig().getString("storage.table_prefix", "coolauth_") + "users";

        this.encrypt = plugin.getConfig().getBoolean("security.encrypt_passwords", true);
        this.encryptionType = plugin.getConfig().getString("security.encryption_type", "BCRYPT").toUpperCase();
        this.secretKey = plugin.getConfig().getString("security.encryption_key", "ChangeMe12345678");
        this.bcryptRounds = plugin.getConfig().getInt("security.bcrypt_rounds", 10);

        if (encryptionType.equals("TWE") && secretKey.length() != 16) {
            plugin.getLogger().warning("For TWE (AES), encryption key MUST be 16 characters! Falling back to BCRYPT.");
        }

        if (encryptionType.equals("BCRYPT")) {
            plugin.getLogger().info("Using BCrypt encryption with " + bcryptRounds + " rounds.");
        }

        initialize();
    }

    private void initialize() {
        if (type.equals("YAML")) {
            yamlFile = new File(plugin.getDataFolder(), "data.yml");
            if (!yamlFile.exists()) {
                try {
                    yamlFile.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            yamlConfig = YamlConfiguration.loadConfiguration(yamlFile);
        } else {
            connectSql();
        }
    }

    private void connectSql() {
        try {
            if (type.equals("SQLITE")) {
                Class.forName("org.sqlite.JDBC");
                connection = DriverManager.getConnection("jdbc:sqlite:" + new File(plugin.getDataFolder(), "database.db").getAbsolutePath());
            } else {
                String host = plugin.getConfig().getString("storage.host");
                String port = String.valueOf(plugin.getConfig().getInt("storage.port"));
                String db = plugin.getConfig().getString("storage.database");
                String user = plugin.getConfig().getString("storage.username");
                String pass = plugin.getConfig().getString("storage.password");

                String driverUrl = type.equals("MARIADB") ? "jdbc:mariadb://" : "jdbc:mysql://";
                connection = DriverManager.getConnection(driverUrl + host + ":" + port + "/" + db + "?autoReconnect=true&useSSL=false", user, pass);
            }

            String sql = "CREATE TABLE IF NOT EXISTS " + table + " (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16), " +
                    "password TEXT, " +
                    "ip VARCHAR(45), " +
                    "last_x DOUBLE, last_y DOUBLE, last_z DOUBLE, last_world TEXT, " +
                    "registered_at BIGINT)";
            try (Statement s = connection.createStatement()) {
                s.execute(sql);
            }

            // Add columns for upgrades
            try {
                connection.createStatement().execute("ALTER TABLE " + table + " ADD COLUMN username VARCHAR(16)");
            } catch (SQLException ignored) {}
            try {
                connection.createStatement().execute("ALTER TABLE " + table + " ADD COLUMN registered_at BIGINT");
            } catch (SQLException ignored) {}

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Database connection failed", e);
        }
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {}
        }
    }

    private void ensureConnection() throws SQLException {
        if (!type.equals("YAML")) {
            if (connection == null || connection.isClosed()) {
                connectSql();
            } else {
                try {
                    if (!connection.isValid(2)) connectSql();
                } catch (AbstractMethodError | SQLException e) {
                    connectSql();
                }
            }
        }
    }

    public boolean isRegistered(UUID uuid) {
        if (type.equals("YAML")) {
            return yamlConfig.contains(uuid.toString());
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("SELECT uuid FROM " + table + " WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    return ps.executeQuery().next();
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public void savePlayer(Player player, String rawPassword) {
        String passwordToSave = encryptPassword(rawPassword);

        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        String ip = player.getAddress().getAddress().getHostAddress();
        Location loc = player.getLocation();
        long registeredAt = System.currentTimeMillis();

        if (type.equals("YAML")) {
            yamlConfig.set(uuid + ".username", name);
            yamlConfig.set(uuid + ".password", passwordToSave);
            yamlConfig.set(uuid + ".ip", ip);
            yamlConfig.set(uuid + ".registered_at", registeredAt);
            saveLocToYaml(uuid, loc);
            saveYaml();
        } else {
            String sql = "INSERT INTO " + table + " (uuid, username, password, ip, last_x, last_y, last_z, last_world, registered_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, uuid);
                    ps.setString(2, name);
                    ps.setString(3, passwordToSave);
                    ps.setString(4, ip);
                    ps.setDouble(5, loc.getX());
                    ps.setDouble(6, loc.getY());
                    ps.setDouble(7, loc.getZ());
                    ps.setString(8, loc.getWorld().getName());
                    ps.setLong(9, registeredAt);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void saveOfflinePlayer(UUID uuid, String name, String rawPassword) {
        String passwordToSave = encryptPassword(rawPassword);
        long registeredAt = System.currentTimeMillis();

        if (type.equals("YAML")) {
            yamlConfig.set(uuid.toString() + ".username", name);
            yamlConfig.set(uuid.toString() + ".password", passwordToSave);
            yamlConfig.set(uuid.toString() + ".ip", "unknown");
            yamlConfig.set(uuid.toString() + ".registered_at", registeredAt);
            saveYaml();
        } else {
            String sql = "INSERT INTO " + table + " (uuid, username, password, ip, last_x, last_y, last_z, last_world, registered_at) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setString(3, passwordToSave);
                    ps.setString(4, "unknown");
                    ps.setDouble(5, 0);
                    ps.setDouble(6, 0);
                    ps.setDouble(7, 0);
                    ps.setString(8, "world");
                    ps.setLong(9, registeredAt);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void updatePassword(UUID uuid, String newRawPassword) {
        String passwordToSave = encryptPassword(newRawPassword);

        if (type.equals("YAML")) {
            yamlConfig.set(uuid.toString() + ".password", passwordToSave);
            saveYaml();
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("UPDATE " + table + " SET password=? WHERE uuid=?")) {
                    ps.setString(1, passwordToSave);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public void removeUser(UUID uuid) {
        if (type.equals("YAML")) {
            yamlConfig.set(uuid.toString(), null);
            saveYaml();
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + table + " WHERE uuid=?")) {
                    ps.setString(1, uuid.toString());
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean checkPassword(UUID uuid, String inputPassword) {
        String storedPass;
        if (type.equals("YAML")) {
            storedPass = yamlConfig.getString(uuid.toString() + ".password");
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("SELECT password FROM " + table + " WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (!rs.next()) return false;
                    storedPass = rs.getString("password");
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return false;
            }
        }

        if (storedPass == null) return false;
        if (!encrypt) return inputPassword.equals(storedPass);

        return switch (encryptionType) {
            case "OWE" -> hashOWE(inputPassword).equals(storedPass);
            case "TWE" -> inputPassword.equals(decryptTWE(storedPass));
            case "BCRYPT" -> BCrypt.checkpw(inputPassword, storedPass);
            default -> inputPassword.equals(storedPass);
        };
    }

    public void updatePlayerInfo(Player player) {
        String uuid = player.getUniqueId().toString();
        String ip = player.getAddress().getAddress().getHostAddress();
        Location loc = player.getLocation();

        if (type.equals("YAML")) {
            yamlConfig.set(uuid + ".ip", ip);
            saveLocToYaml(uuid, loc);
            saveYaml();
        } else {
            String sql = "UPDATE " + table + " SET ip=?, last_x=?, last_y=?, last_z=?, last_world=? WHERE uuid=?";
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement(sql)) {
                    ps.setString(1, ip);
                    ps.setDouble(2, loc.getX());
                    ps.setDouble(3, loc.getY());
                    ps.setDouble(4, loc.getZ());
                    ps.setString(5, loc.getWorld().getName());
                    ps.setString(6, uuid);
                    ps.executeUpdate();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    public Map<String, String> getPlayerInfo(UUID uuid) {
        Map<String, String> info = new HashMap<>();

        if (type.equals("YAML")) {
            String path = uuid.toString();
            info.put("ip", yamlConfig.getString(path + ".ip", "Unknown"));
            double x = yamlConfig.getDouble(path + ".last_x", 0);
            double y = yamlConfig.getDouble(path + ".last_y", 0);
            double z = yamlConfig.getDouble(path + ".last_z", 0);
            String world = yamlConfig.getString(path + ".last_world", "world");
            info.put("location", String.format("%.1f, %.1f, %.1f in %s", x, y, z, world));
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM " + table + " WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        info.put("ip", rs.getString("ip"));
                        double x = rs.getDouble("last_x");
                        double y = rs.getDouble("last_y");
                        double z = rs.getDouble("last_z");
                        String world = rs.getString("last_world");
                        info.put("location", String.format("%.1f, %.1f, %.1f in %s", x, y, z, world));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return info;
    }

    public List<String> getPlayersByIp(String ip) {
        List<String> players = new ArrayList<>();

        if (type.equals("YAML")) {
            for (String key : yamlConfig.getKeys(false)) {
                String storedIp = yamlConfig.getString(key + ".ip");
                if (ip.equals(storedIp)) {
                    players.add(yamlConfig.getString(key + ".username", key));
                }
            }
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("SELECT username FROM " + table + " WHERE ip = ?")) {
                    ps.setString(1, ip);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        players.add(rs.getString("username"));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return players;
    }

    public String getPlayerIp(UUID uuid) {
        if (type.equals("YAML")) {
            return yamlConfig.getString(uuid.toString() + ".ip", "Unknown");
        } else {
            try {
                ensureConnection();
                try (PreparedStatement ps = connection.prepareStatement("SELECT ip FROM " + table + " WHERE uuid = ?")) {
                    ps.setString(1, uuid.toString());
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        return rs.getString("ip");
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return "Unknown";
    }

    private void saveLocToYaml(String uuid, Location loc) {
        yamlConfig.set(uuid + ".last_x", loc.getX());
        yamlConfig.set(uuid + ".last_y", loc.getY());
        yamlConfig.set(uuid + ".last_z", loc.getZ());
        yamlConfig.set(uuid + ".last_world", loc.getWorld().getName());
    }

    private void saveYaml() {
        try {
            yamlConfig.save(yamlFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- Encryption ---

    private String encryptPassword(String rawPassword) {
        if (!encrypt) return rawPassword;

        return switch (encryptionType) {
            case "OWE" -> hashOWE(rawPassword);
            case "TWE" -> encryptTWE(rawPassword);
            case "BCRYPT" -> BCrypt.hashpw(rawPassword, BCrypt.gensalt(bcryptRounds));
            default -> rawPassword;
        };
    }

    private String hashOWE(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = password + secretKey;
            byte[] encodedhash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (Exception e) {
            e.printStackTrace();
            return password;
        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private String encryptTWE(String strToEncrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES"));
            return Base64.getEncoder().encodeToString(cipher.doFinal(strToEncrypt.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            e.printStackTrace();
            return strToEncrypt;
        }
    }

    private String decryptTWE(String strToDecrypt) {
        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES"));
            return new String(cipher.doFinal(Base64.getDecoder().decode(strToDecrypt)));
        } catch (Exception e) {
            e.printStackTrace();
            return strToDecrypt;
        }
    }
    }
