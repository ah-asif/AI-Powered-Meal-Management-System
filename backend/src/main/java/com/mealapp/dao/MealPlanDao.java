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

    public static class CreatedPlan {
        public final String planId;
        public final java.time.LocalDate planDate;
        public CreatedPlan(String planId, java.time.LocalDate planDate) { this.planId = planId; this.planDate = planDate; }
    }

    /** Persists a finished plan (list of chosen food items) for a student. */
    public static CreatedPlan createPlan(String studentId, List<FoodItem> items, double totalCalories, double totalCost) throws SQLException {
        String planId = UUID.randomUUID().toString();
        Connection c = Database.borrow();
        try {
            c.setAutoCommit(false);
            java.time.LocalDate planDate;
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO meal_plans (plan_id, student_id, plan_date, plan_type, total_calories, total_cost, generated_by_ai) " +
                    "VALUES (?::uuid, ?::uuid, CURRENT_DATE, 'PERSONALIZED', ?, ?, true) RETURNING plan_date")) {
                ps.setString(1, planId);
                ps.setString(2, studentId);
                ps.setDouble(3, totalCalories);
                ps.setDouble(4, totalCost);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    planDate = rs.getObject("plan_date", java.time.LocalDate.class);
                }
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
            return new CreatedPlan(planId, planDate);
        } catch (SQLException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
            Database.release(c);
        }
    }

    /**
     * Admin-facing: lists meal plans across ALL students (with student name/email
     * attached), reflecting the Admin—MealPlan association in the UML — admins
     * have system-wide visibility into meal plans, not just the food catalog.
     */
    public static List<Map<String, Object>> listAll(int limit) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT mp.plan_id, mp.plan_date, mp.plan_type, mp.total_calories, mp.total_cost, " +
                "u.user_id AS student_id, u.name AS student_name, u.email AS student_email " +
                "FROM meal_plans mp JOIN users u ON u.user_id = mp.student_id " +
                "ORDER BY mp.created_at DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("planId", rs.getString("plan_id"));
                    m.put("planDate", rs.getDate("plan_date").toString());
                    m.put("planType", rs.getString("plan_type"));
                    m.put("totalCalories", rs.getDouble("total_calories"));
                    m.put("totalCost", rs.getDouble("total_cost"));
                    m.put("studentId", rs.getString("student_id"));
                    m.put("studentName", rs.getString("student_name"));
                    m.put("studentEmail", rs.getString("student_email"));
                    list.add(m);
                }
                return list;
            }
        } finally {
            Database.release(c);
        }
    }

    public static int countAll() throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM meal_plans");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        } finally {
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

    /**
     * Overview tracking summary: overall spend, calories, and protein taken
     * across every saved meal plan for this student. Backs the three stat
     * cards at the top of the Overview tab.
     */
    public static Map<String, Object> nutritionSummary(String studentId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(SUM(fi.price * mpi.quantity), 0) AS total_spent, " +
                "COALESCE(SUM(fi.calories * mpi.quantity), 0) AS total_calories, " +
                "COALESCE(SUM(fi.protein_g * mpi.quantity), 0) AS total_protein " +
                "FROM meal_plan_items mpi " +
                "JOIN meal_plans mp ON mp.plan_id = mpi.plan_id " +
                "JOIN food_items fi ON fi.item_id = mpi.item_id " +
                "WHERE mp.student_id = ?::uuid")) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("totalSpent", rs.getDouble("total_spent"));
                m.put("totalCalories", rs.getDouble("total_calories"));
                m.put("totalProtein", rs.getDouble("total_protein"));
                return m;
            }
        } finally {
            Database.release(c);
        }
    }

    /**
     * Overview drill-down: every food eaten, one row per meal-plan item,
     * with its date and category (breakfast/lunch/dinner/other) — clicking
     * the tracking summary opens this list on the frontend.
     */
    public static List<Map<String, Object>> nutritionLog(String studentId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT mp.plan_date, fi.name, fi.category, fi.calories, fi.protein_g, fi.price, mpi.quantity " +
                "FROM meal_plan_items mpi " +
                "JOIN meal_plans mp ON mp.plan_id = mpi.plan_id " +
                "JOIN food_items fi ON fi.item_id = mpi.item_id " +
                "WHERE mp.student_id = ?::uuid " +
                "ORDER BY mp.plan_date DESC, " +
                "  CASE fi.category WHEN 'breakfast' THEN 1 WHEN 'lunch' THEN 2 WHEN 'dinner' THEN 3 ELSE 4 END")) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Map<String, Object>> list = new ArrayList<>();
                while (rs.next()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("date", rs.getDate("plan_date").toString());
                    m.put("name", rs.getString("name"));
                    m.put("category", rs.getString("category"));
                    m.put("calories", rs.getInt("calories") * rs.getInt("quantity"));
                    m.put("proteinG", rs.getDouble("protein_g") * rs.getInt("quantity"));
                    m.put("price", rs.getDouble("price") * rs.getInt("quantity"));
                    m.put("quantity", rs.getInt("quantity"));
                    list.add(m);
                }
                return list;
            }
        } finally {
            Database.release(c);
        }
    }
}
