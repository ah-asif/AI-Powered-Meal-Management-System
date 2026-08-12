-- =========================================================
-- Student Meal & Budget Planner — PostgreSQL Schema
-- Maps directly to the UML class diagram:
--   User (abstract) <|-- Student, Admin
--   Student ..|> INotifiable, IBudgetManageable
--   Student -- Budget -- Expense
--   Admin -- MealPlan (abstract) <|-- PersonalizedMealPlan
--   MealPlan -- FoodItem
--   PersonalizedMealPlan -- AIEngine (behaviour only, no table)
-- =========================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- for gen_random_uuid()

-- ---------------------------------------------------------
-- USERS  (single-table inheritance: User -> Student / Admin)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    user_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(120)  NOT NULL,
    email           VARCHAR(180)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255)  NOT NULL,
    role            VARCHAR(20)   NOT NULL CHECK (role IN ('STUDENT', 'ADMIN')),
    -- Account approval workflow: every new signup starts PENDING and can't
    -- log in until an admin approves it. Regular admins can approve pending
    -- STUDENT accounts; only a super-admin (is_super_admin = true) can
    -- approve pending ADMIN accounts, so admins can't approve each other in.
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    is_super_admin  BOOLEAN       NOT NULL DEFAULT false,
    -- Student-only fields (NULL for Admin rows)
    budget_limit        NUMERIC(10, 2) DEFAULT NULL,
    dietary_preference   VARCHAR(50)    DEFAULT NULL, -- e.g. 'vegetarian','vegan','halal','none'
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);

-- ---------------------------------------------------------
-- BUDGET  (1-1 with a Student)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS budgets (
    budget_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    total_budget    NUMERIC(10, 2) NOT NULL DEFAULT 0,
    spent_amount    NUMERIC(10, 2) NOT NULL DEFAULT 0,
    period_start    DATE NOT NULL DEFAULT CURRENT_DATE,
    period_end      DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (student_id, period_start)
);

-- ---------------------------------------------------------
-- EXPENSE  (many-to-1 with Budget)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS expenses (
    expense_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    budget_id       UUID NOT NULL REFERENCES budgets(budget_id) ON DELETE CASCADE,
    amount          NUMERIC(10, 2) NOT NULL CHECK (amount >= 0),
    category        VARCHAR(60) NOT NULL DEFAULT 'food',
    description     VARCHAR(255),
    expense_date    DATE NOT NULL DEFAULT CURRENT_DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_expenses_budget ON expenses(budget_id);

-- ---------------------------------------------------------
-- FOOD ITEM  (catalog of purchasable meals/ingredients)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS food_items (
    item_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(150) NOT NULL UNIQUE,
    price           NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    calories        INTEGER NOT NULL CHECK (calories >= 0),
    protein_g       NUMERIC(6, 2) DEFAULT 0,
    category        VARCHAR(50)  NOT NULL DEFAULT 'general', -- breakfast/lunch/dinner/other
    dietary_tags    TEXT[] NOT NULL DEFAULT '{}',             -- {'vegetarian','vegan','halal',...}
    vendor          VARCHAR(120),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_food_items_price ON food_items(price);
CREATE INDEX IF NOT EXISTS idx_food_items_tags ON food_items USING GIN (dietary_tags);

-- ---------------------------------------------------------
-- MEAL PLAN  (abstract in UML -> single-table with plan_type)
-- Every saved plan doubles as the student's "what did I eat" log —
-- meal_plan_items carries the category + calories/protein snapshot at
-- the time it was eaten, so later catalog price/nutrition edits don't
-- rewrite history.
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS meal_plans (
    plan_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_id      UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    plan_date       DATE NOT NULL DEFAULT CURRENT_DATE,
    plan_type       VARCHAR(30) NOT NULL DEFAULT 'PERSONALIZED', -- discriminator for MealPlan subclasses
    total_calories  NUMERIC(10, 2) DEFAULT 0,
    total_cost      NUMERIC(10, 2) DEFAULT 0,
    generated_by_ai BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_meal_plans_student ON meal_plans(student_id);

-- Join table: which FoodItems belong to a given MealPlan
CREATE TABLE IF NOT EXISTS meal_plan_items (
    meal_plan_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id         UUID NOT NULL REFERENCES meal_plans(plan_id) ON DELETE CASCADE,
    item_id         UUID NOT NULL REFERENCES food_items(item_id) ON DELETE CASCADE,
    quantity        INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    UNIQUE (plan_id, item_id)
);

-- ---------------------------------------------------------
-- NOTIFICATIONS  (support for INotifiable behaviour)
-- ---------------------------------------------------------
CREATE TABLE IF NOT EXISTS notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    message         VARCHAR(500) NOT NULL,
    is_read         BOOLEAN NOT NULL DEFAULT false,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

-- ---------------------------------------------------------
-- Trigger to keep updated_at fresh on users
-- ---------------------------------------------------------
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
