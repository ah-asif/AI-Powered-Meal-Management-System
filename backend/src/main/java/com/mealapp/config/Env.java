package com.mealapp.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal .env loader — no dependency needed for something this simple.
 * Real environment variables (e.g. set by Docker) always win over .env file
 * values, matching how dotenv-style tools normally behave.
 */
public final class Env {
    private static final Map<String, String> FILE_VALUES = new HashMap<>();
    private static boolean loaded = false;

    private Env() { }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path path = Path.of(".env");
        if (!Files.exists(path)) return;
        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                int eq = trimmed.indexOf('=');
                if (eq < 0) continue;
                String key = trimmed.substring(0, eq).trim();
                String value = trimmed.substring(eq + 1).trim();
                // strip optional surrounding quotes
                if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\""))) {
                    value = value.substring(1, value.length() - 1);
                }
                FILE_VALUES.put(key, value);
            }
        } catch (IOException e) {
            System.err.println("[env] Could not read .env: " + e.getMessage());
        }
    }

    public static String get(String key, String defaultValue) {
        ensureLoaded();
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isEmpty()) return fromEnv;
        return FILE_VALUES.getOrDefault(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String v = get(key, null);
        if (v == null) return defaultValue;
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
