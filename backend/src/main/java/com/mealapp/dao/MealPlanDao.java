package com.mealapp.dao;

import com.mealapp.config.Database;
import com.mealapp.model.FoodItem;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MealPlanDao {
    private MealPlanDao() { }

    public static class PlanItem {
        public final FoodItem item;
        public final int quantity;
        public PlanItem(FoodItem item, int quantity) { this.item = item; this.quantity = quantity; }
    }

    /** Persists a finished plan (list of chosen food items) for a student. */
    public static String createPlan(String studentId, List<FoodItem> items, double totalCalories, double totalCost) throws SQLException {
        String planId = UUID.randomUUID().toString();
        Connection c = Database.borrow();
        try {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO meal_plans (plan_id, student_id, plan_date, plan_type, total_calories, total_cost, generated_by_ai) " +
                    "VALUES (?::uuid, ?::uuid, CURRENT_DATE, 'PERSONALIZED', ?, ?, true)")) {
                ps.setString(1, planId);
                ps.setString(2, studentId);
                ps.setDouble(3, totalCalories);
                ps.setDouble(4, totalCost);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO meal_plan_items (meal_plan_item_id, plan_id, item_id, quantity) VALUES (?::uuid, ?::uuid, ?::uuid, ?) " +
                    "ON CONFLICT (plan_id, item_id) DO UPDATE SET quantity = meal_plan_items.quantity + EXCLUDED.quantity")) {
                for (FoodItem item : items) {
                    ps.setString(1, UUID.randomUUID().toString());
                    ps.setString(2, planId);
                    ps.setString(3, item.getItemId());
                    ps.setInt(4, 1);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            c.commit();
            return planId;
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
            Database.release(c);
        }
    }

    public static List<Map<String, Object>> listForStudent(String studentId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT plan_id, plan_date, plan_type, total_calories, total_cost " +
                "FROM meal_plans WHERE student_id = ?::uuid ORDER BY plan_date DESC, created_at DESC")) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("planId", rs.getString("plan_id"));
                    m.put("planDate", rs.getDate("plan_date").toString());
                    m.put("planType", rs.getString("plan_type"));
                    m.put("totalCalories", rs.getDouble("total_calories"));
                    m.put("totalCost", rs.getDouble("total_cost"));
                    list.add(m);
                }
                return list;
            }
        } finally {
            Database.release(c);
        }
    }

    public static Map<String, Object> findById(String planId) throws SQLException {
        Connection c = Database.borrow();
        try {
            Map<String, Object> plan;
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM meal_plans WHERE plan_id = ?::uuid")) {
                ps.setString(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;
                    plan = new LinkedHashMap<>();
                    plan.put("planId", rs.getString("plan_id"));
                    plan.put("planDate", rs.getDate("plan_date").toString());
                    plan.put("totalCalories", rs.getDouble("total_calories"));
                    plan.put("totalCost", rs.getDouble("total_cost"));
                }
            }
            List<Map<String, Object>> items = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT fi.*, mpi.quantity FROM meal_plan_items mpi " +
                    "JOIN food_items fi ON fi.item_id = mpi.item_id WHERE mpi.plan_id = ?::uuid")) {
                ps.setString(1, planId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("itemId", rs.getString("item_id"));
                        item.put("name", rs.getString("name"));
                        item.put("price", rs.getDouble("price"));
                        item.put("calories", rs.getInt("calories"));
                        item.put("category", rs.getString("category"));
                        item.put("quantity", rs.getInt("quantity"));
                        items.add(item);
                    }
                }
            }
            plan.put("items", items);
            return plan;
        } finally {
            Database.release(c);
        }
    }
}
