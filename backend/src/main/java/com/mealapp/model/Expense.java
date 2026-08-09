package com.mealapp.model;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Expense
 * -expenseId, -amount, -category, -date
 */
public class Expense {
    private String expenseId;
    private String budgetId;
    private double amount;
    private String category;
    private String description;
    private LocalDate expenseDate;

    public Expense(String expenseId, String budgetId, double amount, String category,
                    String description, LocalDate expenseDate) {
        this.expenseId = expenseId;
        this.budgetId = budgetId;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.expenseDate = expenseDate;
    }

    public String getExpenseId() { return expenseId; }
    public String getBudgetId() { return budgetId; }
    public double getAmount() { return amount; }
    public String getCategory() { return category; }
    public String getDescription() { return description; }
    public LocalDate getExpenseDate() { return expenseDate; }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expenseId", expenseId);
        m.put("budgetId", budgetId);
        m.put("amount", amount);
        m.put("category", category);
        m.put("description", description);
        m.put("expenseDate", expenseDate == null ? null : expenseDate.toString());
        return m;
    }
}
