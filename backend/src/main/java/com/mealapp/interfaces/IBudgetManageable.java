package com.mealapp.interfaces;

import com.mealapp.model.Expense;

import java.sql.SQLException;

/**
 * IBudgetManageable
 * -----------------
 * Contract for anything that manages its own budget.
 * Implemented by Student (see model/Student.java).
 */
public interface IBudgetManageable {
    double calculateBudget() throws SQLException;
    Expense trackExpense(Expense expense) throws SQLException;
}
