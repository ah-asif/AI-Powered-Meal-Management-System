package com.mealapp.model;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.MealPlanDao;
import com.mealapp.dao.NotificationDao;
import com.mealapp.interfaces.IBudgetManageable;
import com.mealapp.interfaces.INotifiable;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Student extends User, implements INotifiable & IBudgetManageable.
 * -budgetLimit, -dietaryPreference
 * +generateDashboard(), +calculateBudget(), +trackExpense(), +sendNotification()
 */
public class Student extends User implements INotifiable, IBudgetManageable {
    private final double budgetLimit;
    private final String dietaryPreference;

    public Student(String userId, String name, String email, String passwordHash,
                   double budgetLimit, String dietaryPreference) {
        super(userId, name, email, passwordHash);
        this.budgetLimit = budgetLimit;
        this.dietaryPreference = dietaryPreference == null ? "none" : dietaryPreference;
    }

    @Override
    public String getRole() { return "STUDENT"; }

    public double getBudgetLimit() { return budgetLimit; }
    public String getDietaryPreference() { return dietaryPreference; }

    @Override
    public Map<String, Object> generateDashboard() throws SQLException {
        Budget budget = BudgetDao.findActiveForStudent(userId);
        List<Map<String, Object>> recentPlans = MealPlanDao.listForStudent(userId);
        if (recentPlans.size() > 5) recentPlans = recentPlans.subList(0, 5);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", toPublicJson());
        result.put("budgetLimit", budgetLimit);
        result.put("dietaryPreference", dietaryPreference);
        result.put("currentBudget", budget == null ? null : budget.toJson());
        result.put("remaining", budget == null ? budgetLimit : budget.calculateRemaining());
        result.put("recentMealPlans", recentPlans);
        return result;
    }

    @Override
    public double calculateBudget() throws SQLException {
        Budget budget = BudgetDao.findActiveForStudent(userId);
        return budget == null ? budgetLimit : budget.calculateRemaining();
    }

    @Override
    public Expense trackExpense(Expense expense) throws SQLException {
        Budget budget = BudgetDao.findActiveForStudent(userId);
        if (budget == null) throw new IllegalStateException("Student has no active budget. Create one before tracking expenses.");
        return budget.trackExpense(expense.getAmount(), expense.getCategory(), expense.getDescription());
    }

    @Override
    public void sendNotification(String message) throws SQLException {
        NotificationDao.create(userId, message);
    }
}
