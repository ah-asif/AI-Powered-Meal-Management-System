package com.mealapp.model;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FoodItem
 * -itemId, -name, -price, -calories
 * +getPrice(), +setPrice()
 */
public class FoodItem {
    private final String itemId;
    private final String name;
    private double price;
    private final int calories;
    private final double proteinG;
    private final String category;
    private final List<String> dietaryTags;
    private final String vendor;

    public FoodItem(String itemId, String name, double price, int calories, double proteinG,
                     String category, List<String> dietaryTags, String vendor) {
        this.itemId = itemId;
        this.name = name;
        this.price = price;
        this.calories = calories;
        this.proteinG = proteinG;
        this.category = category;
        this.dietaryTags = dietaryTags;
        this.vendor = vendor;
    }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public int getCalories() { return calories; }
    public double getProteinG() { return proteinG; }
    public String getCategory() { return category; }
    public List<String> getDietaryTags() { return dietaryTags; }
    public String getVendor() { return vendor; }

    /**
     * "Value" score the AI Engine ranks on: calories delivered per currency
     * unit spent. Higher = more filling for the money = more nutritious
     * per dollar at a glance (a reasonable low-cost/high-value proxy since
     * the catalog is pre-curated with real meals, not junk-calorie fillers).
     */
    public double costEfficiency() {
        return price > 0 ? calories / price : Double.POSITIVE_INFINITY;
    }

    /** A simple 0-100 "healthiness" signal: protein density relative to calories. */
    public double nutritionScore() {
        if (calories <= 0) return 0;
        double proteinRatio = (proteinG * 4.0) / calories; // fraction of calories from protein
        return Math.min(100, proteinRatio * 250); // scaled to a friendly 0-100ish range
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("itemId", itemId);
        m.put("name", name);
        m.put("price", price);
        m.put("calories", calories);
        m.put("proteinG", proteinG);
        m.put("category", category);
        m.put("dietaryTags", dietaryTags);
        m.put("vendor", vendor);
        return m;
    }
}
