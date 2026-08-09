package com.mealapp;

import com.mealapp.config.Database;
import com.mealapp.config.Env;
import com.mealapp.controller.AdminController;
import com.mealapp.controller.AuthController;
import com.mealapp.controller.StudentController;
import com.mealapp.router.Router;
import com.mealapp.util.HttpUtil;
import com.mealapp.util.JwtUtil;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Main
 * ----
 * Application entry point. Boots a plain com.sun.net.httpserver.HttpServer
 * (built into the JDK — no Spring, no other web framework) and wires up
 * every route by hand through the Router.
 */
public class Main {
    public static void main(String[] args) throws Exception {
        int port = Env.getInt("PORT", 4000);
        String jwtSecret = Env.get("JWT_SECRET", "dev-secret-change-me");
        long jwtExpirySeconds = 12L * 60 * 60; // 12 hours, matches the old Node backend's token lifetime

        try {
            Database.warmUp();
            System.out.println("[startup] Connected to PostgreSQL.");
        } catch (SQLException e) {
            System.out.println("[startup] Warning: could not reach PostgreSQL after retries: " + e.getMessage());
        }

        JwtUtil jwtUtil = new JwtUtil(jwtSecret, jwtExpirySeconds);
        Router router = new Router(jwtUtil);

        AuthController authController = new AuthController(jwtUtil);
        StudentController studentController = new StudentController();
        AdminController adminController = new AdminController();

        // ---- health ----
        router.publicRoute("GET", "/api/health", (exchange, params, ctx) -> {
            boolean dbOk = Database.testConnection();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("database", dbOk ? "connected" : "unreachable");
            body.put("time", java.time.Instant.now().toString());
            HttpUtil.sendJson(exchange, 200, body);
        });

        // ---- auth ----
        router.publicRoute("POST", "/api/auth/register", authController::register);
        router.publicRoute("POST", "/api/auth/login", authController::login);
        router.authRoute("GET", "/api/auth/me", null, authController::me);

        // ---- student ----
        router.authRoute("GET", "/api/students/dashboard", "STUDENT", studentController::dashboard);
        router.authRoute("GET", "/api/students/budget", "STUDENT", studentController::getBudget);
        router.authRoute("POST", "/api/students/budget", "STUDENT", studentController::setBudget);
        router.authRoute("GET", "/api/students/expenses", "STUDENT", studentController::listExpenses);
        router.authRoute("POST", "/api/students/expenses", "STUDENT", studentController::addExpense);
        router.authRoute("POST", "/api/students/foods/suggest", "STUDENT", studentController::suggestNextFood);
        router.authRoute("POST", "/api/students/meal-plans", "STUDENT", studentController::savePlan);
        router.authRoute("GET", "/api/students/meal-plans", "STUDENT", studentController::listMealPlans);
        router.authRoute("GET", "/api/students/meal-plans/:planId", "STUDENT", studentController::getMealPlan);
        router.authRoute("GET", "/api/students/insights", "STUDENT", studentController::insights);
        router.authRoute("GET", "/api/students/notifications", "STUDENT", studentController::listNotifications);

        // ---- admin ----
        router.authRoute("GET", "/api/admin/dashboard", "ADMIN", adminController::dashboard);
        router.authRoute("GET", "/api/admin/users", "ADMIN", adminController::manageUsers);
        router.authRoute("DELETE", "/api/admin/users/:userId", "ADMIN", adminController::deleteUser);
        router.authRoute("GET", "/api/admin/food-items", "ADMIN", adminController::listFoodItems);
        router.authRoute("POST", "/api/admin/food-items", "ADMIN", adminController::createFoodItem);
        router.authRoute("PATCH", "/api/admin/food-items/:itemId/price", "ADMIN", adminController::updateFoodItemPrice);
        router.authRoute("DELETE", "/api/admin/food-items/:itemId", "ADMIN", adminController::deleteFoodItem);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", router);
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("[startup] Student Meal App API (Java) listening on port " + port);
    }
}
