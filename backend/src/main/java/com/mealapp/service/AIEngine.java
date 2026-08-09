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
 * +suggestNext(...)   — the core "AI agent": given what's already been
 *                        chosen and how much budget is left, recommend the
 *                        single best next food (healthy + low-cost). The
 *                        caller accepts/rejects it and asks again, building
 *                        a plan one pick at a time — exactly the
 *                        select-one-then-suggest-the-next-one flow.
 * +analyzePreferences(...) — spending/value insights for the dashboard.
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
 * explainable — no black box, no external AI service call.
 */
public class AIEngine {
    private static final double PROTEIN_WEIGHT = 1.6;

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
     * Looks at the student's dietary preference and the current catalog to
     * surface simple, explainable insights (cheapest item, best all-round
     * value pick) for the dashboard's Insights tab.
     */
    public Map<String, Object> analyzePreferences(Student student) throws SQLException {
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
