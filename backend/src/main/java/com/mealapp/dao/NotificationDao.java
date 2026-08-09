package com.mealapp.dao;

import com.mealapp.config.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NotificationDao {
    private NotificationDao() { }

    public static Map<String, Object> create(String userId, String message) throws SQLException {
        String id = UUID.randomUUID().toString();
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO notifications (notification_id, user_id, message) VALUES (?::uuid, ?::uuid, ?) RETURNING *")) {
            ps.setString(1, id);
            ps.setString(2, userId);
            ps.setString(3, message);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return fromRow(rs);
            }
        } finally {
            Database.release(c);
        }
    }

    public static List<Map<String, Object>> listForUser(String userId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM notifications WHERE user_id = ?::uuid ORDER BY created_at DESC")) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) list.add(fromRow(rs));
                return list;
            }
        } finally {
            Database.release(c);
        }
    }

    private static Map<String, Object> fromRow(ResultSet rs) throws SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("notificationId", rs.getString("notification_id"));
        m.put("userId", rs.getString("user_id"));
        m.put("message", rs.getString("message"));
        m.put("isRead", rs.getBoolean("is_read"));
        m.put("createdAt", rs.getTimestamp("created_at").toString());
        return m;
    }
}
