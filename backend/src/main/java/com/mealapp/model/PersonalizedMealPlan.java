package com.mealapp.model;

import com.mealapp.dao.MealPlanDao;
import com.mealapp.service.AIEngine;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * PersonalizedMealPlan extends MealPlan.
 * -aiEngine: AIEngine — the plan owns the engine instance that built it,
 * matching the UML composition. AIEngine itself is stateless (no DB/session
 * state of its own), so sharing one instance across plans is safe and cheap.
 */
public class PersonalizedMealPlan extends MealPlan {
    private final AIEngine aiEngine;

    public PersonalizedMealPlan(String planId, String studentId, LocalDate date, List<FoodItem> items, AIEngine aiEngine) {
        super(planId, studentId, date, items);
        this.aiEngine = aiEngine;
    }

    public AIEngine getAiEngine() { return aiEngine; }

    /** Persists this plan's chosen items for the student. */
    public static PersonalizedMealPlan save(String studentId, List<FoodItem> items, AIEngine aiEngine) throws SQLException {
        double totalCalories = items.stream().mapToDouble(FoodItem::getCalories).sum();
        double totalCost = Math.round(items.stream().mapToDouble(FoodItem::getPrice).sum() * 100.0) / 100.0;
        MealPlanDao.CreatedPlan created = MealPlanDao.createPlan(studentId, items, totalCalories, totalCost);
        return new PersonalizedMealPlan(created.planId, studentId, created.planDate, items, aiEngine);
    }
}
