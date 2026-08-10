package com.mealapp.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Abstract «abstract» MealPlan
 * -planId, -date
 * +calculateNutrition(), +calculateCost()
 */
public abstract class MealPlan {
    protected String planId;
    protected String studentId;
    protected LocalDate date;
    protected List<FoodItem> items;

    protected MealPlan(String planId, String studentId, LocalDate date, List<FoodItem> items) {
        this.planId = planId;
        this.studentId = studentId;
        this.date = date;
        this.items = items;
    }

    public double calculateNutrition() {
        return items.stream().mapToDouble(FoodItem::getCalories).sum();
    }

    public double calculateCost() {
        double total = items.stream().mapToDouble(FoodItem::getPrice).sum();
        return Math.round(total * 100.0) / 100.0;
    }

    public String getPlanId() { return planId; }
    public String getStudentId() { return studentId; }
    public LocalDate getDate() { return date; }
    public List<FoodItem> getItems() { return items; }
}
