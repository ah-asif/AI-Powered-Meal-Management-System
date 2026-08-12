-- Sample food catalog so the AI Engine has something to recommend from.
-- Prices are in BDT (Bangladeshi Taka) — realistic campus/grocery pricing.
-- Categories: breakfast / lunch / dinner / other.

INSERT INTO food_items (name, price, calories, protein_g, category, dietary_tags, vendor) VALUES
('Rice & Lentil Bowl',        45.00, 520, 18, 'lunch',     '{vegetarian,vegan,halal}', 'Campus Canteen'),
('Chicken & Rice Plate',      95.00, 650, 40, 'lunch',     '{halal}',                  'Campus Canteen'),
('Egg Sandwich',              35.00, 320, 14, 'breakfast', '{vegetarian}',             'Corner Cafe'),
('Peanut Butter Toast',       25.00, 300, 10, 'breakfast', '{vegetarian,vegan}',       'Corner Cafe'),
('Banana',                     8.00, 105,  1, 'other',     '{vegetarian,vegan,halal}', 'Grocery Mart'),
('Boiled Eggs (2 pcs)',        18.00, 155, 13, 'other',    '{vegetarian,halal}',       'Grocery Mart'),
('Vegetable Stir Fry',        55.00, 380, 12, 'dinner',    '{vegetarian,vegan,halal}', 'Campus Canteen'),
('Beef Noodle Soup',         110.00, 700, 35, 'dinner',    '{halal}',                  'Night Market'),
('Instant Oatmeal Cup',       22.00, 260,  8, 'breakfast', '{vegetarian,vegan}',       'Grocery Mart'),
('Grilled Fish & Veg',       120.00, 520, 38, 'dinner',    '{halal}',                  'Night Market'),
('Fruit Salad Cup',           40.00, 180,  2, 'other',     '{vegetarian,vegan,halal}', 'Corner Cafe'),
('Tofu & Rice Bowl',          50.00, 480, 20, 'lunch',     '{vegetarian,vegan,halal}', 'Campus Canteen'),
('Cheese Sandwich',           38.00, 350, 15, 'breakfast', '{vegetarian}',             'Corner Cafe'),
('Chicken Salad',             70.00, 420, 30, 'lunch',     '{halal}',                  'Corner Cafe'),
('Milk (500ml)',              20.00, 250,  8, 'other',     '{vegetarian}',             'Grocery Mart'),
('Mixed Nuts (small pack)',   30.00, 210,  7, 'other',     '{vegetarian,vegan,halal}', 'Grocery Mart')
ON CONFLICT (name) DO NOTHING;

-- Super admin — the sole account that can approve new ADMIN signups.
-- (email: admin.alex@gmail.com / password: 12324404)
INSERT INTO users (name, email, password_hash, role, status, is_super_admin)
VALUES ('Alex (Super Admin)', 'admin.alex@gmail.com',
        '120000:WCKPNB+4x7fWWYcldugFpw==:CII6IQOFCD6OEqnYxta5Y/5VN0/NUtWm7d95jWp3J+A=',
        'ADMIN', 'APPROVED', true)
ON CONFLICT (email) DO NOTHING;

-- Demo admin (regular — password: Admin@123). Pre-approved so it works
-- out of the box; can approve pending STUDENT accounts but not pending
-- ADMIN accounts (only the super admin above can do that).
INSERT INTO users (name, email, password_hash, role, status, is_super_admin)
VALUES ('System Admin', 'admin@meal.app',
        '120000:A/MG2kYYmESE/qZJuu71+w==:JYuMsbd5VsJXXU8B6WAWvduuVqmbYAk+BK6AULwiPX0=',
        'ADMIN', 'APPROVED', false)
ON CONFLICT (email) DO NOTHING;

-- Demo student (password: Student@123) — pre-approved so it works out of the box.
INSERT INTO users (name, email, password_hash, role, status, budget_limit, dietary_preference)
VALUES ('Demo Student', 'student@meal.app',
        '120000:YC9y7O3my9EhhUTxeUquXQ==:G8WhT3LYsrUR8ibmEx9nqY3EQDXifbVpFY/1oU/v47A=',
        'STUDENT', 'APPROVED', 2000.00, 'none')
ON CONFLICT (email) DO NOTHING;
