package com.mealapp.model;

import com.mealapp.dao.MealPlanDao;

import java.sql.SQLException;
import java.util.List;

/**
 * PersonalizedMealPlan extends MealPlan.
 * -aiEngine: AIEngine (association — the plan doesn't own the engine,
 * it's just built with one; see service/AIEngine.java)
 */
public class PersonalizedMealPlan extends MealPlan {

    public PersonalizedMealPlan(String planId, String studentId, List<FoodItem> items) {
        super(planId, studentId, items);
    }

    /** Persists this plan's chosen items for the student. */
    public static PersonalizedMealPlan save(String studentId, List<FoodItem> items) throws SQLException {
        double totalCalories = items.stream().mapToDouble(FoodItem::getCalories).sum();
        double totalCost = Math.round(items.stream().mapToDouble(FoodItem::getPrice).sum() * 100.0) / 100.0;
        String planId = MealPlanDao.createPlan(studentId, items, totalCalories, totalCost);
        return new PersonalizedMealPlan(planId, studentId, items);
    }
}
