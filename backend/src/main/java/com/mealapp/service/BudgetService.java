package com.mealapp.service;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.ExpenseDao;
import com.mealapp.model.Budget;
import com.mealapp.model.Expense;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BudgetService {
    private static final double LOW_BUDGET_THRESHOLD_PCT = 20.0;
    private final NotificationService notificationService = new NotificationService();

    public Budget createBudget(String studentId, double totalBudget, LocalDate periodStart, LocalDate periodEnd) throws SQLException {
        return BudgetDao.create(studentId, totalBudget, periodStart, periodEnd);
    }

    public Budget getActiveBudget(String studentId) throws SQLException {
        return BudgetDao.findActiveForStudent(studentId);
    }

    public Map<String, Object> addExpense(String studentId, double amount, String category, String description) throws SQLException {
        Budget budget = BudgetDao.findActiveForStudent(studentId);
        if (budget == null) throw new IllegalStateException("No active budget found for this student.");

        Expense expense = budget.trackExpense(amount, category, description);
        double remaining = budget.calculateRemaining();
        double pctLeft = budget.getTotalBudget() > 0 ? (remaining / budget.getTotalBudget()) * 100 : 0;
        if (pctLeft <= LOW_BUDGET_THRESHOLD_PCT) {
            notificationService.budgetWarning(studentId, remaining, budget.getTotalBudget());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expense", expense.toJson());
        result.put("remaining", remaining);
        result.put("budget", budget.toJson());
        return result;
    }

    public List<Expense> listExpenses(String studentId) throws SQLException {
        Budget budget = BudgetDao.findActiveForStudent(studentId);
        if (budget == null) return List.of();
        return ExpenseDao.listForBudget(budget.getBudgetId());
    }
}
