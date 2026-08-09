package com.mealapp.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Database
 * --------
 * A small hand-rolled JDBC connection pool. We deliberately avoid pulling in
 * HikariCP/c3p0/etc — this app only needs a handful of concurrent
 * connections, and a ~60-line pool keeps the dependency list at exactly one
 * jar (the PostgreSQL JDBC driver itself, which is unavoidable — it's how
 * any Java program speaks the Postgres wire protocol).
 */
public final class Database {
    private static final String URL;
    private static final String USER;
    private static final String PASSWORD;
    private static final int POOL_SIZE = Env.getInt("DB_POOL_MAX", 10);

    private static final BlockingQueue<Connection> POOL = new ArrayBlockingQueue<>(POOL_SIZE);
    private static final List<Connection> ALL_CONNECTIONS = new ArrayList<>();

    static {
        String host = Env.get("DB_HOST", "localhost");
        String port = Env.get("DB_PORT", "5432");
        String name = Env.get("DB_NAME", "student_meal_app");
        URL = "jdbc:postgresql://" + host + ":" + port + "/" + name;
        USER = Env.get("DB_USER", "postgres");
        PASSWORD = Env.get("DB_PASSWORD", "postgres");
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL JDBC driver not found on classpath", e);
        }
    }

    private Database() { }

    /**
     * Lazily fills the pool, retrying the first connection with backoff.
     * This matters most in Docker: the DB container may report "healthy"
     * to Compose a moment before it can actually accept new TCP
     * connections under first-boot load, so a single failed attempt here
     * shouldn't be fatal.
     */
    public static synchronized void warmUp() throws SQLException {
        if (!ALL_CONNECTIONS.isEmpty()) return;
        SQLException lastError = null;
        for (int attempt = 1; attempt <= 15; attempt++) {
            try {
                for (int i = 0; i < POOL_SIZE; i++) {
                    Connection c = DriverManager.getConnection(URL, USER, PASSWORD);
                    ALL_CONNECTIONS.add(c);
                    POOL.offer(c);
                }
                System.out.println("[db] Connection pool ready (" + POOL_SIZE + " connections). Attempt " + attempt + ".");
                return;
            } catch (SQLException e) {
                lastError = e;
                System.out.println("[db] Not reachable yet (attempt " + attempt + "/15): " + e.getMessage());
                try {
                    Thread.sleep(4000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw lastError;
    }

    public static Connection borrow() throws SQLException {
        try {
            Connection c = POOL.poll(10, TimeUnit.SECONDS);
            if (c == null || c.isClosed()) {
                c = DriverManager.getConnection(URL, USER, PASSWORD);
            }
            return c;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a connection", e);
        }
    }

    public static void release(Connection c) {
        if (c == null) return;
        POOL.offer(c);
    }

    public static boolean testConnection() {
        try {
            Connection c = borrow();
            try (var stmt = c.createStatement()) {
                stmt.execute("SELECT 1");
                return true;
            } finally {
                release(c);
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
