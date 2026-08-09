-- Sample food catalog so the AI Engine has something to recommend from.
-- Prices are intentionally varied (low-cost campus/grocery style meals).

INSERT INTO food_items (name, price, calories, protein_g, category, dietary_tags, vendor) VALUES
('Rice & Lentil Bowl',        1.80, 520, 18, 'lunch',     '{vegetarian,vegan,halal}', 'Campus Canteen'),
('Chicken & Rice Plate',      3.20, 650, 40, 'lunch',     '{halal}',                  'Campus Canteen'),
('Egg Sandwich',              1.20, 320, 14, 'breakfast', '{vegetarian}',             'Corner Cafe'),
('Peanut Butter Toast',       0.90, 300, 10, 'breakfast', '{vegetarian,vegan}',       'Corner Cafe'),
('Banana',                    0.30, 105,  1, 'snack',     '{vegetarian,vegan,halal}', 'Grocery Mart'),
('Boiled Eggs (2 pcs)',       0.60, 155, 13, 'snack',     '{vegetarian,halal}',       'Grocery Mart'),
('Vegetable Stir Fry',        2.10, 380, 12, 'dinner',    '{vegetarian,vegan,halal}', 'Campus Canteen'),
('Beef Noodle Soup',          3.80, 700, 35, 'dinner',    '{halal}',                  'Night Market'),
('Instant Oatmeal Cup',       0.75, 260,  8, 'breakfast', '{vegetarian,vegan}',       'Grocery Mart'),
('Grilled Fish & Veg',        4.00, 520, 38, 'dinner',    '{halal}',                  'Night Market'),
('Fruit Salad Cup',           1.50, 180,  2, 'snack',     '{vegetarian,vegan,halal}', 'Corner Cafe'),
('Tofu & Rice Bowl',          1.95, 480, 20, 'lunch',     '{vegetarian,vegan,halal}', 'Campus Canteen'),
('Cheese Sandwich',           1.40, 350, 15, 'breakfast', '{vegetarian}',             'Corner Cafe'),
('Chicken Salad',             2.60, 420, 30, 'lunch',     '{halal}',                  'Corner Cafe'),
('Milk (500ml)',              0.70, 250,  8, 'snack',     '{vegetarian}',             'Grocery Mart'),
('Mixed Nuts (small pack)',   1.10, 210,  7, 'snack',     '{vegetarian,vegan,halal}', 'Grocery Mart')
ON CONFLICT (name) DO NOTHING;

-- Demo admin (password: Admin@123 — PBKDF2WithHmacSHA256, matches
-- backend's PasswordUtil format: "iterations:base64(salt):base64(hash)")
INSERT INTO users (name, email, password_hash, role)
VALUES ('System Admin', 'admin@meal.app', '120000:A/MG2kYYmESE/qZJuu71+w==:JYuMsbd5VsJXXU8B6WAWvduuVqmbYAk+BK6AULwiPX0=', 'ADMIN')
ON CONFLICT (email) DO NOTHING;

-- Demo student (password: Student@123)
INSERT INTO users (name, email, password_hash, role, budget_limit, dietary_preference)
VALUES ('Demo Student', 'student@meal.app', '120000:YC9y7O3my9EhhUTxeUquXQ==:G8WhT3LYsrUR8ibmEx9nqY3EQDXifbVpFY/1oU/v47A=', 'STUDENT', 50.00, 'none')
ON CONFLICT (email) DO NOTHING;
