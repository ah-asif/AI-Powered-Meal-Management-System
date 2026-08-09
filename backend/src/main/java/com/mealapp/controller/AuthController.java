package com.mealapp.controller;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.UserDao;
import com.mealapp.model.User;
import com.mealapp.router.Router;
import com.mealapp.util.HttpUtil;
import com.mealapp.util.JsonUtil;
import com.mealapp.util.JwtUtil;
import com.sun.net.httpserver.HttpExchange;

import java.util.LinkedHashMap;
import java.util.Map;

public class AuthController {
    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

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

        String token = jwtUtil.sign(claimsFor(user));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
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

    private Map<String, Object> claimsFor(User user) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", user.getRole());
        claims.put("email", user.getEmail());
        return claims;
    }
}
