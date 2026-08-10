package com.mealapp.controller;

import com.mealapp.dao.FoodItemDao;
import com.mealapp.dao.MealPlanDao;
import com.mealapp.dao.UserDao;
import com.mealapp.model.Budget;
import com.mealapp.model.Expense;
import com.mealapp.model.FoodItem;
import com.mealapp.model.PersonalizedMealPlan;
import com.mealapp.model.Student;
import com.mealapp.router.Router;
import com.mealapp.service.AIEngine;
import com.mealapp.service.BudgetService;
import com.mealapp.service.NotificationService;
import com.mealapp.util.HttpUtil;
import com.mealapp.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class StudentController {
    private final AIEngine aiEngine = new AIEngine();
    private final BudgetService budgetService = new BudgetService();
    private final NotificationService notificationService = new NotificationService();

    private Student loadStudent(String userId) throws Exception {
        var user = UserDao.findById(userId);
        if (user instanceof Student student) return student;
        return null;
    }

    public void dashboard(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Student student = loadStudent(ctx.userId);
        if (student == null) { HttpUtil.sendError(exchange, 404, "Student not found"); return; }
        HttpUtil.sendJson(exchange, 200, student.generateDashboard());
    }

    public void getBudget(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Budget budget = budgetService.getActiveBudget(ctx.userId);
        if (budget == null) { HttpUtil.sendError(exchange, 404, "No active budget found"); return; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("budget", budget.toJson());
        result.put("remaining", budget.calculateRemaining());
        HttpUtil.sendJson(exchange, 200, result);
    }

    public void setBudget(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        Double totalBudget = JsonUtil.getDouble(body, "totalBudget");
        if (totalBudget == null || totalBudget < 0) {
            throw new IllegalArgumentException("totalBudget must be a non-negative number");
        }
        String periodStartStr = JsonUtil.getString(body, "periodStart", null);
        String periodEndStr = JsonUtil.getString(body, "periodEnd", null);
        LocalDate periodStart = periodStartStr == null ? null : LocalDate.parse(periodStartStr);
        LocalDate periodEnd = periodEndStr == null ? null : LocalDate.parse(periodEndStr);

        Budget budget = budgetService.createBudget(ctx.userId, totalBudget, periodStart, periodEnd);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("budget", budget.toJson());
        HttpUtil.sendJson(exchange, 201, result);
    }

    public void addExpense(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        Double amount = JsonUtil.getDouble(body, "amount");
        if (amount == null || amount < 0) {
            throw new IllegalArgumentException("amount must be a non-negative number");
        }
        String category = JsonUtil.getString(body, "category", "food");
        String description = JsonUtil.getString(body, "description", null);
        Map<String, Object> result = budgetService.addExpense(ctx.userId, amount, category, description);
        HttpUtil.sendJson(exchange, 201, result);
    }

    public void listExpenses(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        List<Expense> expenses = budgetService.listExpenses(ctx.userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("expenses", expenses.stream().map(Expense::toJson).collect(Collectors.toList()));
        HttpUtil.sendJson(exchange, 200, result);
    }

    /**
     * GET /api/students/foods/recommendations
     * Matches the UML diagram's AIEngine.recommendMeals(Student): List —
     * up to 7 low-cost, high-nutrition food items the student can currently
     * afford, sorted ascending by price (cheapest first).
     */
    public void recommendMeals(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Student student = loadStudent(ctx.userId);
        if (student == null) { HttpUtil.sendError(exchange, 404, "Student not found"); return; }
        List<FoodItem> recommendations = aiEngine.recommendMeals(student);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recommendations", recommendations.stream().map(FoodItem::toJson).collect(Collectors.toList()));
        result.put("count", recommendations.size());
        HttpUtil.sendJson(exchange, 200, result);
    }

    /**
     * POST /api/students/foods/suggest
     * body: { selectedItemIds: [...], excludedItemIds: [...] }
     *
     * The core AI agent endpoint, used in a loop by the frontend: suggest one
     * healthy, low-cost food; the student accepts (adds to selectedItemIds)
     * or skips (adds to excludedItemIds) it; call again for the next one.
     * remainingBudget is always recomputed server-side from the student's
     * active budget minus the price of everything already selected, so the
     * client can never spoof a bigger budget than they actually have left.
     */
    public void suggestNextFood(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Student student = loadStudent(ctx.userId);
        if (student == null) { HttpUtil.sendError(exchange, 404, "Student not found"); return; }

        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        List<String> selectedIds = JsonUtil.getList(body, "selectedItemIds").stream().map(String::valueOf).toList();
        List<String> excludedIds = JsonUtil.getList(body, "excludedItemIds").stream().map(String::valueOf).toList();

        double budget = student.calculateBudget();
        double spentSoFar = 0;
        List<FoodItem> selectedItems = new ArrayList<>();
        for (String id : selectedIds) {
            FoodItem item = FoodItemDao.findById(id);
            if (item != null) {
                selectedItems.add(item);
                spentSoFar += item.getPrice();
            }
        }
        double remainingBudget = Math.round((budget - spentSoFar) * 100.0) / 100.0;

        Set<String> excluded = new LinkedHashSet<>(selectedIds);
        excluded.addAll(excludedIds);

        FoodItem suggestion = aiEngine.suggestNext(student, excluded, remainingBudget);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("suggestion", suggestion == null ? null : suggestion.toJson());
        result.put("remainingBudget", remainingBudget);
        result.put("selectedCount", selectedItems.size());
        result.put("selectedTotalCost", Math.round(spentSoFar * 100.0) / 100.0);
        result.put("selectedTotalCalories", selectedItems.stream().mapToDouble(FoodItem::getCalories).sum());
        if (suggestion == null) {
            result.put("message", "No more items fit within the remaining budget and dietary preference.");
        }
        HttpUtil.sendJson(exchange, 200, result);
    }

    /**
     * POST /api/students/meal-plans
     * body: { itemIds: ["...", "..."] }
     * Finalizes and saves the plan the student built via the suggest-next loop.
     */
    public void savePlan(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        List<String> itemIds = JsonUtil.getList(body, "itemIds").stream().map(String::valueOf).toList();
        if (itemIds.isEmpty()) {
            throw new IllegalArgumentException("itemIds must contain at least one food item");
        }

        List<FoodItem> items = new ArrayList<>();
        for (String id : itemIds) {
            FoodItem item = FoodItemDao.findById(id);
            if (item == null) throw new IllegalArgumentException("Unknown food item: " + id);
            items.add(item);
        }

        PersonalizedMealPlan plan = PersonalizedMealPlan.save(ctx.userId, items, aiEngine);
        notificationService.notify(ctx.userId,
                String.format("Your meal plan is ready: %.0f kcal for $%.2f.", plan.calculateNutrition(), plan.calculateCost()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("planId", plan.getPlanId());
        result.put("planDate", plan.getDate() == null ? null : plan.getDate().toString());
        result.put("totalCalories", plan.calculateNutrition());
        result.put("totalCost", plan.calculateCost());
        result.put("items", items.stream().map(FoodItem::toJson).collect(Collectors.toList()));
        HttpUtil.sendJson(exchange, 201, result);
    }

    public void listMealPlans(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mealPlans", MealPlanDao.listForStudent(ctx.userId));
        HttpUtil.sendJson(exchange, 200, result);
    }

    public void getMealPlan(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> plan = MealPlanDao.findById(params.get("planId"));
        if (plan == null) { HttpUtil.sendError(exchange, 404, "Meal plan not found"); return; }
        HttpUtil.sendJson(exchange, 200, plan);
    }

    public void insights(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Student student = loadStudent(ctx.userId);
        if (student == null) { HttpUtil.sendError(exchange, 404, "Student not found"); return; }
        Map<String, Object> summary = aiEngine.insightsSummary(student);
        HttpUtil.sendJson(exchange, 200, summary);
    }

    /**
     * POST /api/students/insights/analyze
     * Matches the UML diagram's AIEngine.analyzePreferences(Student): void
     * exactly — triggers the analysis and sends the student a notification
     * with the result, rather than returning data directly.
     */
    public void analyzePreferences(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Student student = loadStudent(ctx.userId);
        if (student == null) { HttpUtil.sendError(exchange, 404, "Student not found"); return; }
        aiEngine.analyzePreferences(student);
        HttpUtil.sendJson(exchange, 200, HttpUtil.map("message", "Preference analysis complete — check your notifications."));
    }

    public void listNotifications(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notifications", notificationService.listForUser(ctx.userId));
        HttpUtil.sendJson(exchange, 200, result);
    }
}
