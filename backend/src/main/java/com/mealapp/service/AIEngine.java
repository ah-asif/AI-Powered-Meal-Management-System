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
 * +recommendMeals(Student): List  — top 10 best-value items
 * +recommendMeals(Student, excluded, budgetOverride): List — cart-aware version
 * +searchMeals(...)               — fuzzy search + ranking (Top Picks search bar)
 * +analyzePreferences(Student): void
 * +suggestNext(...)
 *
 * Ranking model:
 *   score = (calories + proteinG * 4 * PROTEIN_WEIGHT) / price
 */
public class AIEngine {
    private static final double PROTEIN_WEIGHT = 1.6;
    private static final int RECOMMENDATION_LIST_SIZE = 10;
    private final NotificationService notificationService = new NotificationService();

    // ---------------------------------------------------------------
    // Recommendation / Search Methods
    // ---------------------------------------------------------------

    /**
     * Returns up to 10 low-cost, high-nutrition food items the student can
     * currently afford. Equivalent to calling searchMeals with no query.
     */
    public List<FoodItem> recommendMeals(Student student) throws SQLException {
        return searchMeals(student, null, Set.of(), student.calculateBudget());
    }

    /**
     * Cart-aware version: excludes items already added to the cart and
     * ranks against whatever budget is left.
     */
    public List<FoodItem> recommendMeals(Student student, Set<String> excludedItemIds, double remainingBudget) throws SQLException {
        return searchMeals(student, null, excludedItemIds, remainingBudget);
    }

    /**
     * Fuzzy-search aware recommendation.
     *
     * @param searchQuery  free-text query (supports typos). Pass null/blank for normal Top Picks.
     */
    public List<FoodItem> searchMeals(Student student,
                                      String searchQuery,
                                      Set<String> excludedItemIds,
                                      double remainingBudget) throws SQLException {

        List<FoodItem> catalog = FoodItemDao.findAll(student.getDietaryPreference());
        String query = (searchQuery == null) ? "" : searchQuery.trim().toLowerCase();

        return catalog.stream()
                .filter(f -> !excludedItemIds.contains(f.getItemId()))
                .filter(f -> f.getPrice() > 0 && f.getPrice() <= remainingBudget)
                .filter(f -> query.isEmpty() ||
                             fuzzyMatch(f.getName(), query) ||
                             (f.getCategory() != null && fuzzyMatch(f.getCategory(), query)))
                .sorted((a, b) -> {
                    if (query.isEmpty()) {
                        // No search → pure value ranking
                        return Double.compare(valueScore(b), valueScore(a));
                    }
                    // With search → Relevance first, then value score
                    double scoreA = Math.max(
                            fuzzyScore(a.getName(), query),
                            a.getCategory() != null ? fuzzyScore(a.getCategory(), query) : 0.0
                    );
                    double scoreB = Math.max(
                            fuzzyScore(b.getName(), query),
                            b.getCategory() != null ? fuzzyScore(b.getCategory(), query) : 0.0
                    );

                    int relevanceCompare = Double.compare(scoreB, scoreA);
                    if (relevanceCompare != 0) return relevanceCompare;

                    return Double.compare(valueScore(b), valueScore(a));
                })
                .limit(RECOMMENDATION_LIST_SIZE)
                .sorted((a, b) -> Double.compare(a.getPrice(), b.getPrice())) // final display order
                .collect(Collectors.toList());
    }

    /**
     * Convenience overload – most common frontend call.
     */
    public List<FoodItem> searchMeals(Student student, String searchQuery) throws SQLException {
        return searchMeals(student, searchQuery, Set.of(), student.calculateBudget());
    }

    /**
     * Suggests the single best next food item.
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
                    return Double.compare(b.getPrice(), a.getPrice());
                })
                .orElse(null);
    }

    // ---------------------------------------------------------------
    // Value Score
    // ---------------------------------------------------------------

    private double valueScore(FoodItem item) {
        double proteinCalories = item.getProteinG() * 4.0 * PROTEIN_WEIGHT;
        return (item.getCalories() + proteinCalories) / item.getPrice();
    }

    // ---------------------------------------------------------------
    // Insights / Analysis
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // Fuzzy Search Helpers
    // ---------------------------------------------------------------

    private boolean fuzzyMatch(String text, String query) {
        return fuzzyScore(text, query) >= 0.55;
    }

    /**
     * Returns relevance score between 0.0 and 1.0
     */
    private double fuzzyScore(String text, String query) {
        if (text == null || query == null || query.isEmpty()) return 0.0;

        text = text.toLowerCase().trim();
        query = query.toLowerCase().trim();

        // Exact match
        if (text.equals(query)) return 1.0;

        // Contains
        if (text.contains(query)) {
            return 0.85 + (0.15 * ((double) query.length() / text.length()));
        }

        // Word-level matching
        String[] textWords = text.split("\\s+");
        String[] queryWords = query.split("\\s+");

        double totalWordScore = 0.0;
        int matchedWords = 0;

        for (String qWord : queryWords) {
            double bestWordScore = 0.0;
            for (String tWord : textWords) {
                if (tWord.equals(qWord)) {
                    bestWordScore = 1.0;
                } else if (tWord.startsWith(qWord)) {
                    bestWordScore = Math.max(bestWordScore, 0.9);
                } else if (tWord.contains(qWord)) {
                    bestWordScore = Math.max(bestWordScore, 0.75);
                } else {
                    int distance = levenshteinDistance(tWord, qWord);
                    int maxLen = Math.max(tWord.length(), qWord.length());
                    double similarity = 1.0 - ((double) distance / maxLen);
                    if (similarity > 0.6) {
                        bestWordScore = Math.max(bestWordScore, similarity * 0.8);
                    }
                }
            }
            if (bestWordScore > 0) {
                matchedWords++;
                totalWordScore += bestWordScore;
            }
        }

        if (matchedWords == queryWords.length && queryWords.length > 0) {
            return totalWordScore / queryWords.length;
        }

        // Full string Levenshtein fallback
        int distance = levenshteinDistance(text, query);
        int maxLen = Math.max(text.length(), query.length());
        double similarity = 1.0 - ((double) distance / maxLen);

        return similarity > 0.55 ? similarity * 0.7 : 0.0;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}