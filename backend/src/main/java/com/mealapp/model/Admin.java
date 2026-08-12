package com.mealapp.model;

import com.mealapp.dao.BudgetDao;
import com.mealapp.dao.FoodItemDao;
import com.mealapp.dao.MealPlanDao;
import com.mealapp.dao.UserDao;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin extends User.
 * +generateDashboard(), +manageUsers()
 *
 * isSuperAdmin is the account-approval authority tier: a regular admin can
 * approve pending STUDENT signups, but only a super admin can approve
 * pending ADMIN signups — so admins can never approve each other in, only
 * the designated super admin can. See AdminController's approval endpoints.
 */
public class Admin extends User {
    private final boolean superAdmin;

    public Admin(String userId, String name, String email, String passwordHash, String status, boolean superAdmin) {
        super(userId, name, email, passwordHash, status);
        this.superAdmin = superAdmin;
    }

    @Override
    public String getRole() { return "ADMIN"; }

    public boolean isSuperAdmin() { return superAdmin; }

    @Override
    public Map<String, Object> generateDashboard() throws SQLException {
        int studentCount = UserDao.countByRole("STUDENT");
        int adminCount = UserDao.countByRole("ADMIN");
        double totalSpend = BudgetDao.totalSpentAcrossAllStudents();
        int catalogSize = FoodItemDao.count();
        int mealPlanCount = MealPlanDao.countAll();
        int pendingCount = UserDao.countPendingVisibleTo(superAdmin);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", toPublicJson());
        result.put("isSuperAdmin", superAdmin);
        result.put("studentCount", studentCount);
        result.put("adminCount", adminCount);
        result.put("totalStudentSpend", totalSpend);
        result.put("foodCatalogSize", catalogSize);
        result.put("mealPlanCount", mealPlanCount);
        result.put("pendingApprovals", pendingCount);
        return result;
    }

    public List<Map<String, Object>> manageUsers(String roleFilter) throws SQLException {
        return UserDao.listUsers(roleFilter);
    }

    /** System-wide view of meal plans across all students (most recent first). */
    public List<Map<String, Object>> manageMealPlans(int limit) throws SQLException {
        return MealPlanDao.listAll(limit);
    }

    /**
     * Pending signups this admin is allowed to see/approve: STUDENT
     * requests are visible to any admin; ADMIN requests are visible only
     * to a super admin.
     */
    public List<Map<String, Object>> pendingApprovals() throws SQLException {
        return UserDao.listPending(superAdmin);
    }

    /**
     * Approves a pending account. Enforces the same visibility rule
     * server-side: a regular admin cannot approve a pending ADMIN account.
     */
    public void approveUser(String targetUserId) throws SQLException {
        User target = UserDao.findById(targetUserId);
        if (target == null) throw new IllegalArgumentException("User not found");
        if ("ADMIN".equals(target.getRole()) && !superAdmin) {
            throw new SecurityException("Only a super admin can approve a new admin account");
        }
        UserDao.setStatus(targetUserId, "APPROVED");
    }

    /** Rejects a pending account, same visibility rule as approveUser(). */
    public void rejectUser(String targetUserId) throws SQLException {
        User target = UserDao.findById(targetUserId);
        if (target == null) throw new IllegalArgumentException("User not found");
        if ("ADMIN".equals(target.getRole()) && !superAdmin) {
            throw new SecurityException("Only a super admin can reject a new admin account");
        }
        UserDao.setStatus(targetUserId, "REJECTED");
    }
}
