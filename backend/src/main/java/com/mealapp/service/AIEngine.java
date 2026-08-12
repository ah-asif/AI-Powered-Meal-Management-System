package com.mealapp.service;

import com.mealapp.dao.FoodItemDao;
import com.mealapp.model.FoodItem;
import com.mealapp.model.Student;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AIEngine
 * --------
 * +recommendMeals(Student): List  — matches the UML diagram: the top 10
 *                        low-cost, high-nutrition food items the student
 *                        can afford, sorted ascending by price (cheapest
 *                        first). Ranked by value score first (to pick the
 *                        BEST 10, not just the cheapest 10), then the chosen
 *                        10 are re-sorted by price ascending for display.
 * +recommendMeals(Student, excluded, budgetOverride): List — an overload
 *                        (same method name, different parameters — this is
 *                        legitimate Java overloading, not a UML deviation)
 *                        that powers the "add to cart" flow: each time the
 *                        student clicks an item to add it, the frontend
 *                        calls this again with that item's id excluded and
 *                        the reduced remaining budget, so the list refreshes
 *                        with 10 fresh options that still fit what's left.
 * +analyzePreferences(Student): void — matches the UML diagram: analyzes
 *                        the student's dietary preference against the
 *                        catalog and notifies them with a summary. It does
 *                        not return the data directly (the diagram's return
 *                        type is void) — see insightsSummary() below for
 *                        the JSON payload the dashboard's Insights tab
 *                        actually reads.
 * +suggestNext(...)   — a third, complementary AI capability beyond the
 *                        diagram: single best next item, kept for API
 *                        completeness even though the primary UX is now
 *                        the list-based "add to cart" flow above.
 *
 * Ranking model: rather than pure "cheapest calories" (which favors junk
 * food), each candidate gets a composite VALUE SCORE that rewards protein
 * density (a simple, defensible proxy for "nutritious") per currency unit
 * spent, not just raw calories per currency unit:
 *
 *   score = (calories + proteinG * 4 * PROTEIN_WEIGHT) / price
 *
 * Multiplying protein grams by 4 converts them to calories-from-protein
 * (protein has ~4 kcal/g), then PROTEIN_WEIGHT up-weights that portion so
 * two items with identical calories-per-dollar are ranked apart by how
 * much of that energy is protein. This keeps the model simple and fully
 * explainable — no black box, no external AI service call. Prices are in
 * BDT, but the ranking math is currency-agnostic — it only compares prices
 * to each other and to the student's own budget, never to a hardcoded
 * threshold.
 */
public class AIEngine {
    private static final double PROTEIN_WEIGHT = 1.6;
    private static final int RECOMMENDATION_LIST_SIZE = 10;
    private final NotificationService notificationService = new NotificationService();

    /**
     * Returns up to 10 low-cost, high-nutrition food items the student can
     * currently afford, matching their dietary preference — the best value
     * picks in the whole catalog, sorted ascending by price (cheapest
     * first) for display. Equivalent to calling the cart-aware overload
     * with no items excluded and the student's full remaining budget.
     */
    public List<FoodItem> recommendMeals(Student student) throws SQLException {
        return recommendMeals(student, Set.of(), student.calculateBudget());
    }

    /**
     * Cart-aware version: excludes items already added to the cart and
     * ranks against whatever budget is left after those items' cost.
     */
    public List<FoodItem> recommendMeals(Student student, Set<String> excludedItemIds, double remainingBudget) throws SQLException {
        List<FoodItem> catalog = FoodItemDao.findAll(student.getDietaryPreference());

        return catalog.stream()
                .filter(f -> !excludedItemIds.contains(f.getItemId()))
                .filter(f -> f.getPrice() > 0 && f.getPrice() <= remainingBudget)
                .sorted((a, b) -> Double.compare(valueScore(b), valueScore(a))) // best value first...
                .limit(RECOMMENDATION_LIST_SIZE)
                .sorted((a, b) -> Double.compare(a.getPrice(), b.getPrice()))   // ...then ascending by price for display
                .collect(Collectors.toList());
    }

    /**
     * Suggests the single best next food item: highest value score among
     * items that are (a) not already chosen/skipped, (b) within what's left
     * of the budget, and (c) compatible with the student's dietary
     * preference. Returns null if nothing fits.
     */
    public FoodItem suggestNext(Student student, Set<String> excludedItemIds, double remainingBudget) throws SQLException {
        if (remainingBudget <= 0) return null;
        List<FoodItem> catalog = FoodItemDao.findAll(student.getDietaryPreference());
        return catalog.stream()
                .filter(f -> !excludedItemIds.contains(f.getItemId()))
                .filter(f -> f.getPrice() <= remainingBudget && f.getPrice() > 0)
                .max((a, b) -> {
                    double cmp = valueScore(a) - valueScore(b);
                    if (Math.abs(cmp) > 1e-9) return cmp > 0 ? 1 : -1;
                    return Double.compare(b.getPrice(), a.getPrice()); // tie-break: cheaper wins (b vs a reversed = ascending)
                })
                .orElse(null);
    }

    private double valueScore(FoodItem item) {
        double proteinCalories = item.getProteinG() * 4.0 * PROTEIN_WEIGHT;
        return (item.getCalories() + proteinCalories) / item.getPrice();
    }

    /**
     * Matches the UML diagram exactly: analyzes the student's dietary
     * preference against the catalog and notifies them — no return value.
     */
    public void analyzePreferences(Student student) throws SQLException {
        Map<String, Object> summary = insightsSummary(student);
        String message;
        if (summary.containsKey("insight")) {
            message = String.valueOf(summary.get("insight"));
        } else {
            @SuppressWarnings("unchecked")
            Map<String, Object> bestValue = (Map<String, Object>) summary.get("bestValueOption");
            message = String.format("Best value pick right now: %s at %s per serving.",
                    bestValue.get("name"), bestValue.get("price"));
        }
        notificationService.notify(student.getUserId(), message);
    }

    /**
     * The JSON payload behind analyzePreferences()'s analysis — used by the
     * API layer (StudentController) to answer the dashboard's Insights tab.
     * Kept as a separate method (rather than analyzePreferences's return
     * type) because the UML specifies analyzePreferences returns void.
     */
    public Map<String, Object> insightsSummary(Student student) throws SQLException {
        List<FoodItem> catalog = FoodItemDao.findAll(student.getDietaryPreference());
        Map<String, Object> result = new LinkedHashMap<>();
        if (catalog.isEmpty()) {
            result.put("insight", "No matching food items found for the current dietary preference.");
            return result;
        }

        FoodItem cheapest = catalog.stream().min((a, b) -> Double.compare(a.getPrice(), b.getPrice())).orElseThrow();
        FoodItem bestValue = catalog.stream().max((a, b) -> Double.compare(valueScore(a), valueScore(b))).orElseThrow();
        double avgPrice = catalog.stream().mapToDouble(FoodItem::getPrice).average().orElse(0);

        result.put("dietaryPreference", student.getDietaryPreference());
        result.put("catalogSize", catalog.size());
        result.put("averagePrice", Math.round(avgPrice * 100.0) / 100.0);

        Map<String, Object> cheapestJson = new LinkedHashMap<>();
        cheapestJson.put("name", cheapest.getName());
        cheapestJson.put("price", cheapest.getPrice());
        result.put("cheapestOption", cheapestJson);

        Map<String, Object> bestValueJson = new LinkedHashMap<>();
        bestValueJson.put("name", bestValue.getName());
        bestValueJson.put("price", bestValue.getPrice());
        bestValueJson.put("caloriesPerCurrencyUnit", Math.round(bestValue.costEfficiency() * 10.0) / 10.0);
        bestValueJson.put("nutritionScore", Math.round(bestValue.nutritionScore() * 10.0) / 10.0);
        result.put("bestValueOption", bestValueJson);

        return result;
    }

    public static List<Map<String, Object>> withScores(List<FoodItem> items) {
        return items.stream().map(FoodItem::toJson).collect(Collectors.toList());
    }
}
