package com.mealapp.tools;

import com.mealapp.config.Database;
import com.mealapp.config.Env;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * InitDb
 * ------
 * Applies database/schema.sql then database/seed.sql against the
 * configured PostgreSQL instance. Pure Java + JDBC — no external tools
 * needed. Run with: java -cp "out:lib/*" com.mealapp.tools.InitDb
 */
public final class InitDb {
    public static void main(String[] args) {
        try {
            Database.warmUp();

            Path schemaPath = resolveSqlPath("schema.sql");
            Path seedPath = resolveSqlPath("seed.sql");

            System.out.println("[initDb] Applying schema.sql ...");
            runSqlFile(schemaPath);
            System.out.println("[initDb] Schema applied.");

            if (!"true".equalsIgnoreCase(Env.get("SKIP_SEED", "false"))) {
                System.out.println("[initDb] Applying seed.sql ...");
                runSqlFile(seedPath);
                System.out.println("[initDb] Seed data applied.");
            }

            System.out.println("[initDb] Done.");
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[initDb] Failed: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Looks for database/<name> relative to a few likely working
     * directories, since this may run from backend/ locally or from
     * /app in Docker where database/ is copied alongside.
     */
    private static Path resolveSqlPath(String name) throws Exception {
        String[] candidates = {
                "database/" + name,
                "../database/" + name,
                "/app/database/" + name,
        };
        for (String candidate : candidates) {
            Path p = Path.of(candidate);
            if (Files.exists(p)) return p;
        }
        throw new IllegalStateException("Could not locate database/" + name + " (looked in: " + String.join(", ", candidates) + ")");
    }

    private static void runSqlFile(Path path) throws Exception {
        String sql = Files.readString(path);
        Connection c = Database.borrow();
        try (Statement stmt = c.createStatement()) {
            stmt.execute(sql);
        } finally {
            Database.release(c);
        }
    }
}
