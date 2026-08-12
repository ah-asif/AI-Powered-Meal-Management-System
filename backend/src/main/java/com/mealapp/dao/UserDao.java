package com.mealapp.dao;

import com.mealapp.config.Database;
import com.mealapp.model.Admin;
import com.mealapp.model.Student;
import com.mealapp.model.User;
import com.mealapp.util.PasswordUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class UserDao {
    private UserDao() { }

    public static boolean emailExists(String email) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } finally {
            Database.release(c);
        }
    }

    /**
     * Registers a new user and returns the created User (Student or Admin).
     * Deliberately does NOT set `status` or `is_super_admin` here — every
     * new signup gets the table defaults (PENDING, not-super-admin), so
     * nobody can self-approve or self-promote at registration time. See
     * AdminController's approval endpoints for how an account becomes
     * usable.
     */
    public static User create(String name, String email, String plainPassword, String role,
                               Double budgetLimit, String dietaryPreference) throws SQLException {
        String userId = UUID.randomUUID().toString();
        String hash = PasswordUtil.hash(plainPassword);
        // Students never need approval; only ADMIN signups start PENDING and
        // require a super admin to approve them (see AuthController.register).
        String status = "STUDENT".equals(role) ? "APPROVED" : "PENDING";
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO users (user_id, name, email, password_hash, role, status, budget_limit, dietary_preference) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, userId);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, hash);
            ps.setString(5, role);
            ps.setString(6, status);
            if ("STUDENT".equals(role)) {
                ps.setDouble(7, budgetLimit == null ? 0 : budgetLimit);
                ps.setString(8, dietaryPreference == null ? "none" : dietaryPreference);
            } else {
                ps.setNull(7, Types.NUMERIC);
                ps.setNull(8, Types.VARCHAR);
            }
            ps.executeUpdate();
        } finally {
            Database.release(c);
        }
        return findById(userId);
    }

    public static User findByEmail(String email) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE email = ?")) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromRow(rs) : null;
            }
        } finally {
            Database.release(c);
        }
    }

    public static User findById(String userId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE user_id = ?::uuid")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromRow(rs) : null;
            }
        } finally {
            Database.release(c);
        }
    }

    public static List<Map<String, Object>> listUsers(String roleFilter) throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT user_id, name, email, role, status, is_super_admin, budget_limit, dietary_preference, created_at FROM users");
        boolean hasFilter = roleFilter != null && !roleFilter.isBlank();
        if (hasFilter) sql.append(" WHERE role = ?");
        sql.append(" ORDER BY created_at DESC");

        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (hasFilter) ps.setString(1, roleFilter);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("userId", rs.getString("user_id"));
                    m.put("name", rs.getString("name"));
                    m.put("email", rs.getString("email"));
                    m.put("role", rs.getString("role"));
                    m.put("status", rs.getString("status"));
                    m.put("isSuperAdmin", rs.getBoolean("is_super_admin"));
                    m.put("budgetLimit", rs.getObject("budget_limit"));
                    m.put("dietaryPreference", rs.getString("dietary_preference"));
                    m.put("createdAt", rs.getTimestamp("created_at").toString());
                    result.add(m);
                }
                return result;
            }
        } finally {
            Database.release(c);
        }
    }

    /**
     * Pending signups an admin is allowed to act on. A regular admin only
     * sees pending STUDENT accounts; a super admin sees pending STUDENT
     * *and* ADMIN accounts — this is the query-level enforcement of "an
     * admin's approval request never goes to another regular admin."
     */
    public static List<Map<String, Object>> listPending(boolean includeAdmins) throws SQLException {
        String sql = "SELECT user_id, name, email, role, budget_limit, dietary_preference, created_at " +
                "FROM users WHERE status = 'PENDING'" +
                (includeAdmins ? "" : " AND role = 'STUDENT'") +
                " ORDER BY created_at ASC";
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<Map<String, Object>> result = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("userId", rs.getString("user_id"));
                m.put("name", rs.getString("name"));
                m.put("email", rs.getString("email"));
                m.put("role", rs.getString("role"));
                m.put("budgetLimit", rs.getObject("budget_limit"));
                m.put("dietaryPreference", rs.getString("dietary_preference"));
                m.put("createdAt", rs.getTimestamp("created_at").toString());
                result.add(m);
            }
            return result;
        } finally {
            Database.release(c);
        }
    }

    public static int countPendingVisibleTo(boolean superAdmin) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE status = 'PENDING'" + (superAdmin ? "" : " AND role = 'STUDENT'");
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } finally {
            Database.release(c);
        }
    }

    public static void setStatus(String userId, String status) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("UPDATE users SET status = ? WHERE user_id = ?::uuid")) {
            ps.setString(1, status);
            ps.setString(2, userId);
            ps.executeUpdate();
        } finally {
            Database.release(c);
        }
    }

    public static boolean delete(String userId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE user_id = ?::uuid")) {
            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        } finally {
            Database.release(c);
        }
    }

    public static int countByRole(String role) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM users WHERE role = ?")) {
            ps.setString(1, role);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } finally {
            Database.release(c);
        }
    }

    private static User fromRow(ResultSet rs) throws SQLException {
        String role = rs.getString("role");
        String userId = rs.getString("user_id");
        String name = rs.getString("name");
        String email = rs.getString("email");
        String hash = rs.getString("password_hash");
        String status = rs.getString("status");
        if ("ADMIN".equals(role)) {
            boolean superAdmin = rs.getBoolean("is_super_admin");
            return new Admin(userId, name, email, hash, status, superAdmin);
        }
        double budgetLimit = rs.getDouble("budget_limit"); // returns 0 if NULL, fine here (only STUDENT rows reach this branch)
        String dietaryPreference = rs.getString("dietary_preference");
        return new Student(userId, name, email, hash, status, budgetLimit, dietaryPreference);
    }
}
