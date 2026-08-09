package com.mealapp.service;

import com.mealapp.dao.NotificationDao;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class NotificationService {

    public Map<String, Object> notify(String userId, String message) throws SQLException {
        return NotificationDao.create(userId, message);
    }

    public List<Map<String, Object>> listForUser(String userId) throws SQLException {
        return NotificationDao.listForUser(userId);
    }

    public Map<String, Object> budgetWarning(String userId, double remaining, double totalBudget) throws SQLException {
        double pctLeft = totalBudget > 0 ? (remaining / totalBudget) * 100 : 0;
        String message = String.format(
                "Heads up: you have %.2f left (%.0f%% of your budget).", remaining, pctLeft);
        return notify(userId, message);
    }
}
