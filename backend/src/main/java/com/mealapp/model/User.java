package com.mealapp.model;

import com.mealapp.util.PasswordUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract «abstract» User
 * -userId, -name, -email
 * +login(), +logout(), +generateDashboard()
 */
public abstract class User {
    protected String userId;
    protected String name;
    protected String email;
    protected String passwordHash;

    protected User(String userId, String name, String email, String passwordHash) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public boolean login(String plainPassword) {
        return PasswordUtil.verify(plainPassword, passwordHash);
    }

    public Map<String, Object> logout() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Logged out. Discard the client-side token.");
        return result;
    }

    /** Must be implemented by Student / Admin. */
    public abstract Map<String, Object> generateDashboard() throws Exception;

    public abstract String getRole();

    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }

    public Map<String, Object> toPublicJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("name", name);
        m.put("email", email);
        m.put("role", getRole());
        return m;
    }
}
