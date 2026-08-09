package com.mealapp.model;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.ExpenseDao;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Budget
 * -totalBudget, -spentAmount
 * +calculateRemaining(), +trackExpense()
 */
public class Budget {
    private final String budgetId;
    private final String studentId;
    private final double totalBudget;
    private double spentAmount;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;

    public Budget(String budgetId, String studentId, double totalBudget, double spentAmount,
                  LocalDate periodStart, LocalDate periodEnd) {
        this.budgetId = budgetId;
        this.studentId = studentId;
        this.totalBudget = totalBudget;
        this.spentAmount = spentAmount;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public double calculateRemaining() {
        return Math.round((totalBudget - spentAmount) * 100.0) / 100.0;
    }

    public Expense trackExpense(double amount, String category, String description) throws SQLException {
        Expense expense = ExpenseDao.create(budgetId, amount, category, description, null);
        BudgetDao.addToSpent(budgetId, amount);
        this.spentAmount += amount;
        return expense;
    }

    public String getBudgetId() { return budgetId; }
    public String getStudentId() { return studentId; }
    public double getTotalBudget() { return totalBudget; }
    public double getSpentAmount() { return spentAmount; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("budgetId", budgetId);
        m.put("studentId", studentId);
        m.put("totalBudget", totalBudget);
        m.put("spentAmount", spentAmount);
        m.put("periodStart", periodStart == null ? null : periodStart.toString());
        m.put("periodEnd", periodEnd == null ? null : periodEnd.toString());
        return m;
    }
}
