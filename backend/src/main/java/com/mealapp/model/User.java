package com.mealapp.model;

import com.mealapp.util.PasswordUtil;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Abstract «abstract» User
 * -userId, -name, -email
 * +login(), +logout(), +generateDashboard()
 *
 * Also carries the account-approval `status` (PENDING/APPROVED/REJECTED) —
 * every new signup starts PENDING and can't log in until an admin approves
 * it (see AuthController.login and AdminController's approval endpoints).
 */
public abstract class User {
    protected String userId;
    protected String name;
    protected String email;
    protected String passwordHash;
    protected String status;

    protected User(String userId, String name, String email, String passwordHash, String status) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.status = status == null ? "PENDING" : status;
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
    public String getStatus() { return status; }
    public boolean isApproved() { return "APPROVED".equals(status); }
    public boolean isPending() { return "PENDING".equals(status); }

    public Map<String, Object> toPublicJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", userId);
        m.put("name", name);
        m.put("email", email);
        m.put("role", getRole());
        m.put("status", status);
        return m;
    }
}
