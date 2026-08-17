package io.github.sql1024.goldstock.storage;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import io.github.sql1024.goldstock.GoldStockPlugin;
import io.github.sql1024.goldstock.portfolio.Holding;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/**
 * SQLite storage. Everything goes through one connection owned by a single writer thread, so
 * gameplay code never blocks on disk; the few reads (startup, reload, log lookups) submit a task
 * and wait for it.
 */
public final class Database {

    private static final int TRANSACTION_LOG_LIMIT = 20_000;

    private final GoldStockPlugin plugin;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "GoldStock-DB");
                thread.setDaemon(true);
                return thread;
            });

    private Connection connection;
    private volatile boolean closed;

    public Database(GoldStockPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ lifecycle

    public void open() throws SQLException {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new SQLException("無法建立資料夾 " + folder.getAbsolutePath());
        }
        File file = new File(folder, "goldstock.db");

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
        config.enforceForeignKeys(true);

        SQLiteDataSource source = new SQLiteDataSource(config);
        source.setUrl("jdbc:sqlite:" + file.getAbsolutePath());
        connection = source.getConnection();

        createTables();
        prune();
    }

    private void createTables() throws SQLException {
        String[] ddl = {
            """
            CREATE TABLE IF NOT EXISTS players (
                uuid TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS holdings (
                uuid     TEXT    NOT NULL,
                symbol   TEXT    NOT NULL,
                shares   INTEGER NOT NULL,
                invested INTEGER NOT NULL,
                PRIMARY KEY (uuid, symbol)
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS prices (
                symbol         TEXT PRIMARY KEY NOT NULL,
                price          REAL NOT NULL,
                previous_price REAL NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS price_history (
                id     INTEGER PRIMARY KEY AUTOINCREMENT,
                symbol TEXT    NOT NULL,
                ts     INTEGER NOT NULL,
                price  REAL    NOT NULL
            )
            """,
            """
            CREATE TABLE IF NOT EXISTS transactions (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                uuid       TEXT    NOT NULL,
                name       TEXT    NOT NULL,
                symbol     TEXT    NOT NULL,
                type       TEXT    NOT NULL,
                shares     INTEGER NOT NULL,
                unit_price REAL    NOT NULL,
                gold       INTEGER NOT NULL,
                ts         INTEGER NOT NULL
            )
            """,
            "CREATE INDEX IF NOT EXISTS idx_history_symbol ON price_history (symbol, id)",
            "CREATE INDEX IF NOT EXISTS idx_txn_uuid ON transactions (uuid, id)",
        };
        try (Statement statement = connection.createStatement()) {
            for (String sql : ddl) {
                statement.executeUpdate(sql);
            }
        }
    }

    private void prune() {
        String sql = """
                DELETE FROM transactions
                WHERE id NOT IN (SELECT id FROM transactions ORDER BY id DESC LIMIT ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, TRANSACTION_LOG_LIMIT);
            statement.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "整理交易紀錄時發生錯誤", e);
        }
    }

    /** Waits for queued writes, then closes the connection. */
    public void close() {
        closed = true;
        worker.shutdown();
        try {
            if (!worker.awaitTermination(15L, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("資料庫寫入未在 15 秒內完成，可能有資料未寫入。");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "關閉資料庫時發生錯誤", e);
            }
        }
    }

    // ------------------------------------------------------------------ writes

    public void savePlayerName(UUID uuid, String name) {
        submit("記錄玩家名稱", () -> {
            String sql = """
                    INSERT INTO players (uuid, name) VALUES (?, ?)
                    ON CONFLICT (uuid) DO UPDATE SET name = excluded.name
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.executeUpdate();
            }
        });
    }

    public void saveHolding(UUID uuid, String symbol, Holding holding) {
        int shares = holding.shares();
        long invested = holding.invested();
        submit("儲存持股", () -> {
            String sql = """
                    INSERT INTO holdings (uuid, symbol, shares, invested) VALUES (?, ?, ?, ?)
                    ON CONFLICT (uuid, symbol) DO UPDATE SET
                        shares = excluded.shares, invested = excluded.invested
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, symbol);
                statement.setInt(3, shares);
                statement.setLong(4, invested);
                statement.executeUpdate();
            }
        });
    }

    public void deleteHolding(UUID uuid, String symbol) {
        submit("刪除持股", () -> {
            try (PreparedStatement statement =
                         connection.prepareStatement("DELETE FROM holdings WHERE uuid = ? AND symbol = ?")) {
                statement.setString(1, uuid.toString());
                statement.setString(2, symbol);
                statement.executeUpdate();
            }
        });
    }

    public void savePrice(String symbol, double price, double previousPrice) {
        submit("儲存股價", () -> {
            String sql = """
                    INSERT INTO prices (symbol, price, previous_price) VALUES (?, ?, ?)
                    ON CONFLICT (symbol) DO UPDATE SET
                        price = excluded.price, previous_price = excluded.previous_price
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, symbol);
                statement.setDouble(2, price);
                statement.setDouble(3, previousPrice);
                statement.executeUpdate();
            }
        });
    }

    public void appendHistory(String symbol, long timestamp, double price, int keepPoints) {
        submit("寫入歷史股價", () -> {
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO price_history (symbol, ts, price) VALUES (?, ?, ?)")) {
                insert.setString(1, symbol);
                insert.setLong(2, timestamp);
                insert.setDouble(3, price);
                insert.executeUpdate();
            }
            String sql = """
                    DELETE FROM price_history
                    WHERE symbol = ? AND id NOT IN (
                        SELECT id FROM price_history WHERE symbol = ? ORDER BY id DESC LIMIT ?
                    )
                    """;
            try (PreparedStatement delete = connection.prepareStatement(sql)) {
                delete.setString(1, symbol);
                delete.setString(2, symbol);
                delete.setInt(3, Math.max(2, keepPoints));
                delete.executeUpdate();
            }
        });
    }

    public void logTransaction(UUID uuid,
                               String name,
                               String symbol,
                               String type,
                               int shares,
                               double unitPrice,
                               long gold) {
        long now = System.currentTimeMillis();
        submit("寫入交易紀錄", () -> {
            String sql = """
                    INSERT INTO transactions (uuid, name, symbol, type, shares, unit_price, gold, ts)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, name);
                statement.setString(3, symbol);
                statement.setString(4, type);
                statement.setInt(5, shares);
                statement.setDouble(6, unitPrice);
                statement.setLong(7, gold);
                statement.setLong(8, now);
                statement.executeUpdate();
            }
        });
    }

    // ------------------------------------------------------------------ reads

    public Map<UUID, Map<String, Holding>> loadHoldings() {
        return read("讀取持股", Map.of(), () -> {
            Map<UUID, Map<String, Holding>> result = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT uuid, symbol, shares, invested FROM holdings WHERE shares > 0")) {
                while (rows.next()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(rows.getString("uuid"));
                    } catch (IllegalArgumentException e) {
                        continue;
                    }
                    result.computeIfAbsent(uuid, key -> new LinkedHashMap<>())
                            .put(rows.getString("symbol"),
                                    new Holding(rows.getInt("shares"), rows.getLong("invested")));
                }
            }
            return result;
        });
    }

    public Map<String, double[]> loadPrices() {
        return read("讀取股價", Map.of(), () -> {
            Map<String, double[]> result = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT symbol, price, previous_price FROM prices")) {
                while (rows.next()) {
                    result.put(rows.getString("symbol"),
                            new double[] {rows.getDouble("price"), rows.getDouble("previous_price")});
                }
            }
            return result;
        });
    }

    public Map<String, List<Double>> loadHistory(int keepPoints) {
        return read("讀取歷史股價", Map.of(), () -> {
            Map<String, List<Double>> result = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery(
                         "SELECT symbol, price FROM price_history ORDER BY id ASC")) {
                while (rows.next()) {
                    result.computeIfAbsent(rows.getString("symbol"), key -> new ArrayList<>())
                            .add(rows.getDouble("price"));
                }
            }
            for (List<Double> points : result.values()) {
                while (points.size() > Math.max(2, keepPoints)) {
                    points.remove(0);
                }
            }
            return result;
        });
    }

    public List<TxnRecord> recentTransactions(UUID uuid, int limit) {
        return read("讀取交易紀錄", List.of(), () -> {
            List<TxnRecord> result = new ArrayList<>();
            String sql = """
                    SELECT name, symbol, type, shares, unit_price, gold, ts FROM transactions
                    WHERE uuid = ? ORDER BY id DESC LIMIT ?
                    """;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, Math.clamp(limit, 1, 100));
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        result.add(new TxnRecord(
                                rows.getString("name"),
                                rows.getString("symbol"),
                                rows.getString("type"),
                                rows.getInt("shares"),
                                rows.getDouble("unit_price"),
                                rows.getLong("gold"),
                                rows.getLong("ts")));
                    }
                }
            }
            return result;
        });
    }

    public Map<UUID, String> loadPlayerNames() {
        return read("讀取玩家名稱", Map.of(), () -> {
            Map<UUID, String> result = new HashMap<>();
            try (Statement statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT uuid, name FROM players")) {
                while (rows.next()) {
                    try {
                        result.put(UUID.fromString(rows.getString("uuid")), rows.getString("name"));
                    } catch (IllegalArgumentException ignored) {
                        // Skip malformed rows rather than failing the whole load.
                    }
                }
            }
            return result;
        });
    }

    // ------------------------------------------------------------------ plumbing

    private interface SqlTask {
        void run() throws SQLException;
    }

    private void submit(String what, SqlTask task) {
        if (closed) {
            return;
        }
        worker.execute(() -> {
            try {
                task.run();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, what + "失敗", e);
            }
        });
    }

    private <T> T read(String what, T fallback, Callable<T> task) {
        try {
            return worker.submit(task).get(20L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, what + "失敗", e);
            return fallback;
        }
    }
}
