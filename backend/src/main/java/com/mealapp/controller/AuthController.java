package com.mealapp.controller;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.UserDao;
import com.mealapp.model.User;
import com.mealapp.router.Router;
import com.mealapp.service.NotificationService;
import com.mealapp.util.HttpUtil;
import com.mealapp.util.JsonUtil;
import com.mealapp.util.JwtUtil;
import com.sun.net.httpserver.HttpExchange;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AuthController {
    private final JwtUtil jwtUtil;
    private final NotificationService notificationService = new NotificationService();

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Every new account starts PENDING (see UserDao.create) and does NOT
     * receive a login token here — it has to be approved first. STUDENT
     * signups notify every admin; ADMIN signups notify only super admins,
     * so a regular admin never even sees another admin's approval request.
     */
    public void register(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        String name = JsonUtil.getString(body, "name", null);
        String email = JsonUtil.getString(body, "email", null);
        String password = JsonUtil.getString(body, "password", null);
        String role = JsonUtil.getString(body, "role", "STUDENT");

        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("name, email and password are required");
        }
        if (!"STUDENT".equals(role) && !"ADMIN".equals(role)) {
            throw new IllegalArgumentException("role must be STUDENT or ADMIN");
        }
        if (UserDao.emailExists(email)) {
            HttpUtil.sendError(exchange, 409, "An account with this email already exists");
            return;
        }

        Double budgetLimit = JsonUtil.getDouble(body, "budgetLimit");
        String dietaryPreference = JsonUtil.getString(body, "dietaryPreference", "none");

        User user = UserDao.create(name, email, password, role, budgetLimit, dietaryPreference);

        if ("STUDENT".equals(role)) {
            BudgetDao.create(user.getUserId(), budgetLimit == null ? 0 : budgetLimit, null, null);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if ("ADMIN".equals(role)) {
            notifyAdminsOfPendingApproval(user);
            result.put("message", "Registration submitted. A super admin needs to approve your account before you can sign in.");
        } else {
            result.put("message", "Registration successful. You can now sign in.");
        }
        result.put("user", user.toPublicJson());
        HttpUtil.sendJson(exchange, 201, result);
    }

    public void login(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        Map<String, Object> body = HttpUtil.readJsonBody(exchange);
        String email = JsonUtil.getString(body, "email", null);
        String password = JsonUtil.getString(body, "password", null);
        if (email == null || password == null) {
            throw new IllegalArgumentException("email and password are required");
        }

        User user = UserDao.findByEmail(email);
        if (user == null || !user.login(password)) {
            HttpUtil.sendError(exchange, 401, "Invalid email or password");
            return;
        }
        if (user.isPending()) {
            HttpUtil.sendError(exchange, 403, "Your account is pending admin approval. Please check back soon.");
            return;
        }
        if (!user.isApproved()) {
            HttpUtil.sendError(exchange, 403, "Your account was not approved. Contact an administrator.");
            return;
        }

        String token = jwtUtil.sign(claimsFor(user));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("user", user.toPublicJson());
        HttpUtil.sendJson(exchange, 200, result);
    }

    public void me(HttpExchange exchange, Map<String, String> params, Router.RequestContext ctx) throws Exception {
        User user = UserDao.findById(ctx.userId);
        if (user == null) {
            HttpUtil.sendError(exchange, 404, "User not found");
            return;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", user.toPublicJson());
        HttpUtil.sendJson(exchange, 200, result);
    }

    private void notifyAdminsOfPendingApproval(User newUser) throws Exception {
        List<Map<String, Object>> admins = UserDao.listUsers("ADMIN");
        boolean newUserIsAdmin = "ADMIN".equals(newUser.getRole());
        String message = String.format("New %s signup awaiting approval: %s (%s).",
                newUser.getRole().toLowerCase(), newUser.getName(), newUser.getEmail());
        for (Map<String, Object> admin : admins) {
            boolean recipientIsSuperAdmin = Boolean.TRUE.equals(admin.get("isSuperAdmin"));
            // Regular admins are notified about pending students only — a
            // new admin's approval request never goes to another regular admin.
            if (newUserIsAdmin && !recipientIsSuperAdmin) continue;
            notificationService.notify(String.valueOf(admin.get("userId")), message);
        }
    }

    private Map<String, Object> claimsFor(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", user.getRole());
        claims.put("email", user.getEmail());
        return claims;
    }
}
