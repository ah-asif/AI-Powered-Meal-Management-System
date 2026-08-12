package com.mealapp.controller;

import com.mealapp.dao.FoodItemDao;
import com.mealapp.dao.UserDao;
import com.mealapp.model.Admin;
import com.mealapp.model.FoodItem;
import com.mealapp.router.Router;
import com.mealapp.service.NotificationService;
import com.mealapp.util.HttpUtil;
import com.mealapp.util.JsonUtil;
import com.sun.net.httpserver.HttpExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AdminController {
    private final NotificationService notificationService = new NotificationService();

    private Admin loadAdmin(String userId) throws Exception {
        var user = UserDao.findById(userId);
        if (user instanceof Admin admin) return admin;
        return null;
    }

    public void dashboard(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Admin admin = loadAdmin(ctx.userId);
        if (admin == null) { HttpUtil.sendError(exchange, 404, "Admin not found"); return; }
        HttpUtil.sendJson(exchange, 200, admin.generateDashboard());
    }

    public void manageUsers(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Admin admin = loadAdmin(ctx.userId);
        if (admin == null) { HttpUtil.sendError(exchange, 404, "Admin not found"); return; }
        Map<String, String> query = HttpUtil.parseQuery(exchange);
        List<Map<String, Object>> users = admin.manageUsers(query.get("role"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("users", users);
        HttpUtil.sendJson(exchange, 200, result);
    }

    public void deleteUser(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        boolean deleted = UserDao.delete(params.get("userId"));
        if (!deleted) { HttpUtil.sendError(exchange, 404, "User not found"); return; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", params.get("userId"));
        HttpUtil.sendJson(exchange, 200, result);
    }

    /** GET /api/admin/notifications */
    public void listNotifications(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("notifications", notificationService.listForUser(ctx.userId));
        HttpUtil.sendJson(exchange, 200, result);
    }

    /**
     * GET /api/admin/approvals
     * Pending signups this admin is allowed to act on. A regular admin only
     * sees pending STUDENT accounts; only a super admin sees pending ADMIN
     * accounts too — enforced in Admin.pendingApprovals() / UserDao.listPending().
     */
    public void listApprovals(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Admin admin = loadAdmin(ctx.userId);
        if (admin == null) { HttpUtil.sendError(exchange, 404, "Admin not found"); return; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pending", admin.pendingApprovals());
        result.put("isSuperAdmin", admin.isSuperAdmin());
        HttpUtil.sendJson(exchange, 200, result);
    }

    /** POST /api/admin/approvals/:userId/approve */
    public void approveUser(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Admin admin = loadAdmin(ctx.userId);
        if (admin == null) { HttpUtil.sendError(exchange, 404, "Admin not found"); return; }
        String targetUserId = params.get("userId");
        admin.approveUser(targetUserId);
        notificationService.notify(targetUserId, "Your account has been approved. You can now sign in.");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("approved", targetUserId);
        HttpUtil.sendJson(exchange, 200, result);
    }

    /** POST /api/admin/approvals/:userId/reject */
    public void rejectUser(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Admin admin = loadAdmin(ctx.userId);
        if (admin == null) { HttpUtil.sendError(exchange, 404, "Admin not found"); return; }
        String targetUserId = params.get("userId");
        admin.rejectUser(targetUserId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rejected", targetUserId);
        HttpUtil.sendJson(exchange, 200, result);
    }

    /**
     * GET /api/admin/meal-plans
     * Matches the UML diagram's Admin—MealPlan association: system-wide
     * visibility into meal plans across all students.
     */
    public void listMealPlans(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Admin admin = loadAdmin(ctx.userId);
        if (admin == null) { HttpUtil.sendError(exchange, 404, "Admin not found"); return; }
        Map<String, String> query = HttpUtil.parseQuery(exchange);
        int limit = 50;
        try { if (query.get("limit") != null) limit = Integer.parseInt(query.get("limit")); } catch (NumberFormatException ignored) { }
        List<Map<String, Object>> plans = admin.manageMealPlans(limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mealPlans", plans);
        HttpUtil.sendJson(exchange, 200, result);
    }

    public void listFoodItems(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        List<FoodItem> items = FoodItemDao.findAll(null);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foodItems", items.stream().map(FoodItem::toJson).collect(Collectors.toList()));
        HttpUtil.sendJson(exchange, 200, result);
    }

    @SuppressWarnings("unchecked")
    public void createFoodItem(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        String name = JsonUtil.getString(body, "name", null);
        Double price = JsonUtil.getDouble(body, "price");
        Double caloriesD = JsonUtil.getDouble(body, "calories");
        if (name == null || name.isBlank() || price == null || caloriesD == null) {
            throw new IllegalArgumentException("name, price and calories are required");
        }
        Double proteinG = JsonUtil.getDouble(body, "proteinG");
        String category = JsonUtil.getString(body, "category", "general");
        String vendor = JsonUtil.getString(body, "vendor", null);
        List<String> tags = ((List<Object>) body.getOrDefault("dietaryTags", List.of()))
                .stream().map(String::valueOf).collect(Collectors.toList());

        FoodItem item = FoodItemDao.create(name, price, caloriesD.intValue(), proteinG == null ? 0 : proteinG, category, tags, vendor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foodItem", item.toJson());
        HttpUtil.sendJson(exchange, 201, result);
    }

    public void updateFoodItemPrice(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        Double price = JsonUtil.getDouble(body, "price");
        if (price == null || price < 0) {
            throw new IllegalArgumentException("price must be a non-negative number");
        }
        boolean updated = FoodItemDao.updatePrice(params.get("itemId"), price);
        if (!updated) { HttpUtil.sendError(exchange, 404, "Food item not found"); return; }
        FoodItem item = FoodItemDao.findById(params.get("itemId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("foodItem", item.toJson());
        HttpUtil.sendJson(exchange, 200, result);
    }

    public void deleteFoodItem(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        boolean deleted = FoodItemDao.delete(params.get("itemId"));
        if (!deleted) { HttpUtil.sendError(exchange, 404, "Food item not found"); return; }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", params.get("itemId"));
        HttpUtil.sendJson(exchange, 200, result);
    }
}
