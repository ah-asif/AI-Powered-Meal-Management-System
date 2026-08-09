package com.mealapp.model;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.FoodItemDao;
import com.mealapp.dao.UserDao;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin extends User.
 * +generateDashboard(), +manageUsers()
 */
public class Admin extends User {

    public Admin(String userId, String name, String email, String passwordHash) {
        super(userId, name, email, passwordHash);
    }

    @Override
    public String getRole() { return "ADMIN"; }

    @Override
    public Map<String, Object> generateDashboard() throws SQLException {
        int studentCount = UserDao.countByRole("STUDENT");
        int adminCount = UserDao.countByRole("ADMIN");
        double totalSpend = BudgetDao.totalSpentAcrossAllStudents();
        int catalogSize = FoodItemDao.count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", toPublicJson());
        result.put("studentCount", studentCount);
        result.put("adminCount", adminCount);
        result.put("totalStudentSpend", totalSpend);
        result.put("foodCatalogSize", catalogSize);
        return result;
    }

    public List<Map<String, Object>> manageUsers(String roleFilter) throws SQLException {
        return UserDao.listUsers(roleFilter);
    }
}
