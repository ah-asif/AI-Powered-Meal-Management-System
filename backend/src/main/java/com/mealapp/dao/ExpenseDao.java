package com.mealapp.dao;

import com.mealapp.config.Database;
import com.mealapp.model.Expense;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class ExpenseDao {
    private ExpenseDao() { }

    public static Expense create(String budgetId, double amount, String category, String description, LocalDate expenseDate) throws SQLException {
        String expenseId = UUID.randomUUID().toString();
        LocalDate date = expenseDate == null ? LocalDate.now() : expenseDate;
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO expenses (expense_id, budget_id, amount, category, description, expense_date) " +
                "VALUES (?::uuid, ?::uuid, ?, ?, ?, ?) RETURNING *")) {
            ps.setString(1, expenseId);
            ps.setString(2, budgetId);
            ps.setDouble(3, amount);
            ps.setString(4, category == null ? "food" : category);
            ps.setString(5, description);
            ps.setObject(6, date);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return fromRow(rs);
            }
        } finally {
            Database.release(c);
        }
    }

    public static List<Expense> listForBudget(String budgetId) throws SQLException {
        Connection c = Database.borrow();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM expenses WHERE budget_id = ?::uuid ORDER BY expense_date DESC, created_at DESC")) {
            ps.setString(1, budgetId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Expense> list = new ArrayList<>();
                while (rs.next()) list.add(fromRow(rs));
                return list;
            }
        } finally {
            Database.release(c);
        }
    }

    private static Expense fromRow(ResultSet rs) throws SQLException {
        return new Expense(
                rs.getString("expense_id"),
                rs.getString("budget_id"),
                rs.getDouble("amount"),
                rs.getString("category"),
                rs.getString("description"),
                rs.getObject("expense_date", LocalDate.class)
        );
    }
}
