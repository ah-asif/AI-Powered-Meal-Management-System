package com.mealapp.dao;

import com.mealapp.config.Database;
import com.mealapp.model.Budget;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.UUID;

public final class BudgetDao {
    private BudgetDao() { }

    /**
     * Creates a budget for (student, period_start), upserting if one already
     * exists for that exact day — e.g. the starter budget auto-created at
     * registration — instead of colliding with the unique constraint.
     */
    public static Budget create(String studentId, double totalBudget, LocalDate periodStart, LocalDate periodEnd) throws SQLException {
        LocalDate start = periodStart == null ? LocalDate.now() : periodStart;
        String budgetId = UUID.randomUUID().toString();
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO budgets (budget_id, student_id, total_budget, period_start, period_end) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?) " +
                "ON CONFLICT (student_id, period_start) " +
                "DO UPDATE SET total_budget = EXCLUDED.total_budget, period_end = EXCLUDED.period_end " +
                "RETURNING *")) {
            ps.setString(1, budgetId);
            ps.setString(2, studentId);
            ps.setDouble(3, totalBudget);
            ps.setObject(4, start);
            ps.setObject(5, periodEnd);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return fromRow(rs);
            }
        } finally {
            Database.release(c);
        }
    }

    public static Budget findActiveForStudent(String studentId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM budgets WHERE student_id = ?::uuid ORDER BY period_start DESC LIMIT 1")) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? fromRow(rs) : null;
            }
        } finally {
            Database.release(c);
        }
    }

    public static void addToSpent(String budgetId, double amount) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE budgets SET spent_amount = spent_amount + ? WHERE budget_id = ?::uuid")) {
            ps.setDouble(1, amount);
            ps.setString(2, budgetId);
            ps.executeUpdate();
        } finally {
            Database.release(c);
        }
    }

    public static double totalSpentAcrossAllStudents() throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement("SELECT COALESCE(SUM(spent_amount), 0) FROM budgets");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getDouble(1);
        } finally {
            Database.release(c);
        }
    }

    private static Budget fromRow(ResultSet rs) throws SQLException {
        return new Budget(
                rs.getString("budget_id"),
                rs.getString("student_id"),
                rs.getDouble("total_budget"),
                rs.getDouble("spent_amount"),
                rs.getObject("period_start", LocalDate.class),
                rs.getObject("period_end", LocalDate.class)
        );
    }
}
