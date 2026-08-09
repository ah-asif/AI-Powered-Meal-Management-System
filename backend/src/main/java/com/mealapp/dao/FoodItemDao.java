package com.mealapp.dao;

import com.mealapp.config.Database;
import com.mealapp.model.FoodItem;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FoodItemDao {
    private FoodItemDao() { }

    public static List<FoodItem> findAll(String dietaryPreference) throws SQLException {
        StringBuilder sql = new StringBuilder("SELECT * FROM food_items");
        boolean filterByDiet = dietaryPreference != null && !dietaryPreference.isBlank()
                && !dietaryPreference.equalsIgnoreCase("none");
        if (filterByDiet) sql.append(" WHERE ? = ANY(dietary_tags)");
        sql.append(" ORDER BY price ASC");

        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (filterByDiet) ps.setString(1, dietaryPreference);
            try (ResultSet rs = ps.executeQuery()) {
                List<FoodItem> items = new ArrayList<>();
                while (rs.next()) items.add(fromRow(rs));
                return items;
            }
        } finally {
            Database.release(c);
        }
    }

    public static FoodItem findById(String itemId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM food_items WHERE item_id = ?::uuid")) {
            ps.setString(1, itemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromRow(rs) : null;
            }
        } finally {
            Database.release(c);
        }
    }

    public static FoodItem create(String name, double price, int calories, double proteinG,
                                   String category, List<String> dietaryTags, String vendor) throws SQLException {
        String itemId = UUID.randomUUID().toString();
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO food_items (item_id, name, price, calories, protein_g, category, dietary_tags, vendor) " +
                "VALUES (?::uuid, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, itemId);
            ps.setString(2, name);
            ps.setDouble(3, price);
            ps.setInt(4, calories);
            ps.setDouble(5, proteinG);
            ps.setString(6, category == null ? "general" : category);
            Array tagsArray = c.createArrayOf("text", dietaryTags == null ? new String[0] : dietaryTags.toArray());
            ps.setArray(7, tagsArray);
            ps.setString(8, vendor);
            ps.executeUpdate();
            return findById(itemId);
        } finally {
            Database.release(c);
        }
    }

    public static boolean updatePrice(String itemId, double price) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("UPDATE food_items SET price = ? WHERE item_id = ?::uuid")) {
            ps.setDouble(1, price);
            ps.setString(2, itemId);
            return ps.executeUpdate() > 0;
        } finally {
            Database.release(c);
        }
    }

    public static boolean delete(String itemId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM food_items WHERE item_id = ?::uuid")) {
            ps.setString(1, itemId);
            return ps.executeUpdate() > 0;
        } finally {
            Database.release(c);
        }
    }

    public static int count() throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM food_items");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } finally {
            Database.release(c);
        }
    }

    private static FoodItem fromRow(ResultSet rs) throws SQLException {
        List<String> tags = new ArrayList<>();
        Array arr = rs.getArray("dietary_tags");
        if (arr != null) {
            String[] raw = (String[]) arr.getArray();
            for (String t : raw) tags.add(t);
        }
        return new FoodItem(
                rs.getString("item_id"),
                rs.getString("name"),
                rs.getDouble("price"),
                rs.getInt("calories"),
                rs.getDouble("protein_g"),
                rs.getString("category"),
                tags,
                rs.getString("vendor")
        );
    }
}
