package fr.townyconflict.managers;

import fr.townyconflict.TownyConflict;
import fr.townyconflict.models.Assault;
import fr.townyconflict.models.War;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.*;

public class DatabaseManager {

    private final TownyConflict plugin;
    private Connection connection;
    private String dbType;

    public DatabaseManager(TownyConflict plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        FileConfiguration cfg = plugin.getConfig();
        dbType = cfg.getString("database.type", "sqlite").toLowerCase();

        try {
            if (dbType.equals("mysql")) {
                String host = cfg.getString("database.mysql.host", "localhost");
                int port = cfg.getInt("database.mysql.port", 3306);
                String db = cfg.getString("database.mysql.database", "townyconflict");
                String user = cfg.getString("database.mysql.username", "root");
                String pass = cfg.getString("database.mysql.password", "");
                connection = DriverManager.getConnection(
                        "jdbc:mysql://" + host + ":" + port + "/" + db + "?autoReconnect=true", user, pass);
            } else {
                File dbFile = new File(plugin.getDataFolder(), cfg.getString("database.sqlite.file", "townyconflict.db"));
                plugin.getDataFolder().mkdirs();
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            }
            createTables();
            plugin.getLogger().info("Base de données initialisée (" + dbType + ").");
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur DB : " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tc_wars (
                    id TEXT PRIMARY KEY,
                    attacker_town TEXT NOT NULL,
                    defender_town TEXT NOT NULL,
                    status TEXT NOT NULL,
                    declared_at BIGINT NOT NULL,
                    started_at BIGINT,
                    ended_at BIGINT,
                    end_reason TEXT,
                    winner_town TEXT,
                    allow_reinforcements INTEGER DEFAULT 1,
                    allow_mercenaries INTEGER DEFAULT 1,
                    allow_allied INTEGER DEFAULT 0,
                    victory_condition_type TEXT DEFAULT 'assault_wins',
                    victory_condition_value INTEGER DEFAULT 5,
                    attacker_war_points INTEGER DEFAULT 0,
                    defender_war_points INTEGER DEFAULT 0,
                    attacker_assault_wins INTEGER DEFAULT 0,
                    defender_assault_wins INTEGER DEFAULT 0,
                    rewards_json TEXT DEFAULT '[]'
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tc_assaults (
                    id TEXT PRIMARY KEY,
                    war_id TEXT NOT NULL,
                    attacker_town TEXT NOT NULL,
                    defender_town TEXT NOT NULL,
                    started_at BIGINT NOT NULL,
                    ended_at BIGINT,
                    result TEXT,
                    captured_points INTEGER DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tc_reputation (
                    town_name TEXT PRIMARY KEY,
                    reputation INTEGER DEFAULT 0
                )
            """);

            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS tc_cooldowns (
                    cooldown_key TEXT PRIMARY KEY,
                    expires_at BIGINT NOT NULL
                )
            """);
        }
    }

    // ─────────────────────────────────────────
    //  WARS
    // ─────────────────────────────────────────

    public void saveWar(War war) {
        String sql = """
            INSERT OR REPLACE INTO tc_wars
            (id, attacker_town, defender_town, status, declared_at, started_at, ended_at,
             end_reason, winner_town, allow_reinforcements, allow_mercenaries, allow_allied,
             victory_condition_type, victory_condition_value,
             attacker_war_points, defender_war_points, attacker_assault_wins, defender_assault_wins)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, war.getId().toString());
            ps.setString(2, war.getAttackerTown());
            ps.setString(3, war.getDefenderTown());
            ps.setString(4, war.getStatus().name());
            ps.setLong(5, war.getDeclaredAt());
            ps.setLong(6, war.getStartedAt());
            ps.setLong(7, war.getEndedAt());
            ps.setString(8, war.getEndReason() != null ? war.getEndReason().name() : null);
            ps.setString(9, war.getWinnerTown());
            ps.setInt(10, war.isAllowNationReinforcements() ? 1 : 0);
            ps.setInt(11, war.isAllowMercenaries() ? 1 : 0);
            ps.setInt(12, war.isAllowAlliedNations() ? 1 : 0);
            ps.setString(13, war.getVictoryConditionType());
            ps.setInt(14, war.getVictoryConditionValue());
            ps.setInt(15, war.getAttackerWarPoints());
            ps.setInt(16, war.getDefenderWarPoints());
            ps.setInt(17, war.getAttackerAssaultWins());
            ps.setInt(18, war.getDefenderAssaultWins());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur saveWar: " + e.getMessage());
        }
    }

    public List<War> loadActiveWars() {
        List<War> wars = new ArrayList<>();
        String sql = "SELECT * FROM tc_wars WHERE status != 'ENDED'";
        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                War war = new War(rs.getString("attacker_town"), rs.getString("defender_town"));
                war.setStatus(War.Status.valueOf(rs.getString("status")));
                war.setStartedAt(rs.getLong("started_at"));
                war.setAllowNationReinforcements(rs.getInt("allow_reinforcements") == 1);
                war.setAllowMercenaries(rs.getInt("allow_mercenaries") == 1);
                war.setAllowAlliedNations(rs.getInt("allow_allied") == 1);
                war.setVictoryConditionType(rs.getString("victory_condition_type"));
                war.setVictoryConditionValue(rs.getInt("victory_condition_value"));
                wars.add(war);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur loadActiveWars: " + e.getMessage());
        }
        return wars;
    }

    // ─────────────────────────────────────────
    //  ASSAULTS
    // ─────────────────────────────────────────

    public void saveAssault(Assault assault) {
        String sql = """
            INSERT OR REPLACE INTO tc_assaults
            (id, war_id, attacker_town, defender_town, started_at, ended_at, result, captured_points)
            VALUES (?,?,?,?,?,?,?,?)
        """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, assault.getId().toString());
            ps.setString(2, assault.getWarId().toString());
            ps.setString(3, assault.getAttackerTown());
            ps.setString(4, assault.getDefenderTown());
            ps.setLong(5, assault.getStartedAt());
            ps.setLong(6, System.currentTimeMillis());
            ps.setString(7, assault.getResult().name());
            ps.setInt(8, assault.getCapturedPoints());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur saveAssault: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────
    //  REPUTATION
    // ─────────────────────────────────────────

    public void saveReputation(String townName, int value) {
        String sql = "INSERT OR REPLACE INTO tc_reputation (town_name, reputation) VALUES (?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, townName.toLowerCase());
            ps.setInt(2, value);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur saveReputation: " + e.getMessage());
        }
    }

    public Map<String, Integer> loadReputations() {
        Map<String, Integer> map = new HashMap<>();
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM tc_reputation")) {
            while (rs.next()) map.put(rs.getString("town_name"), rs.getInt("reputation"));
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur loadReputations: " + e.getMessage());
        }
        return map;
    }

    // ─────────────────────────────────────────
    //  CLOSE
    // ─────────────────────────────────────────

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Erreur fermeture DB: " + e.getMessage());
        }
    }
}
