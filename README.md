# AI-Powered Meal Management System

A full-stack app that helps students plan meals within a tight budget in
**BDT (৳)**. Its core feature is an AI agent that suggests the 10 healthiest,
lowest-cost foods you can afford — click one to add it to your plan like a
shopping cart, and it re-suggests the next best 10 the same way, until
you're happy with what you've picked. Every account (student or admin) goes
through an approval flow before it can sign in: student accounts are
auto-approved instantly, admin accounts must be approved by the designated
**super admin**.

- **Backend: plain Java, no frameworks.** `com.sun.net.httpserver.HttpServer`
  (built into the JDK) handles HTTP, raw JDBC talks to PostgreSQL, and JSON
  parsing, JWT auth, and password hashing are all hand-rolled on top of the
  JDK's own standard library (`javax.crypto`, `java.security`). The only
  external file involved is the PostgreSQL JDBC driver `.jar`, bundled in
  `backend/lib/` — unavoidable, since nothing can talk to Postgres from Java
  without a driver, and a driver is not a framework.
- **Frontend:** static HTML/CSS/JS ("Signal" design system). No build step,
  no framework, no npm.
- **Database:** PostgreSQL, accessed with hand-written SQL (no ORM).

This project was built for an Object-Oriented Programming course, so beyond
"does it work," it's also structured to make the underlying OOP design
legible — see **§4** below for exactly where each concept lives in the code.

---

## 1. Architecture at a glance

```
User (abstract)
 ├─ Student  ⋯implements⋯> INotifiable, IBudgetManageable
 └─ Admin                                    (one flagged as super admin)

Student 1───1 Budget 1───* Expense
Admin   1───* MealPlan (abstract)      Admin ──views──> MealPlan (all students)
              └─ PersonalizedMealPlan ──owns──> AIEngine
MealPlan *───* FoodItem   (via meal_plan_items)
```

This maps directly onto the project's UML diagram (`docs/architecture.png`)
and the PostgreSQL schema in `database/schema.sql`.

### The AI Engine — how the cart-style recommendation flow works

`backend/src/main/java/com/mealapp/service/AIEngine.java` is the agent.
Rather than generating a whole meal plan in one shot, it works like an
"add to cart" flow:

1. The frontend calls `POST /api/students/foods/recommendations` with
   whatever's already in the cart (`selectedItemIds`).
2. The server recomputes the student's *actual* remaining budget itself
   (their active budget minus the price of everything already in the cart)
   — the client can never spoof a bigger budget than they really have.
3. Every food item not already in the cart, that fits the remaining budget
   and the student's dietary preference, gets a **value score**:

   ```
   score = (calories + proteinG × 4 × 1.6) / price
   ```

   Multiplying protein grams by 4 converts them to calories-from-protein
   (protein is ~4 kcal/g); the 1.6 weight then rewards protein-dense food
   over plain empty calories at the same price — a simple, fully
   explainable stand-in for "healthy," not just "cheap."
4. The top 10 items by that score are returned, **re-sorted ascending by
   price** (cheapest first) for display.
5. The student clicks one to add it to the cart, and the frontend calls
   `/foods/recommendations` again — the chosen item now excluded, the
   budget now smaller — for the next 10. Repeat until done, then
   `POST /api/students/meal-plans` with the final cart to save it.

`analyzePreferences(Student): void` and `recommendMeals(Student): List` also
exist as UML-exact overloads (see §4.4) alongside the interactive version.

### Overview tab — consumption tracking

Every saved meal plan is a timestamped record of real food items with known
calories and protein. The dashboard's Overview tab aggregates all of a
student's saved plans into running totals (`GET /api/students/nutrition/summary`)
and, on click, expands into an itemized log (`GET /api/students/nutrition/log`)
grouped by date and then by meal category (breakfast / lunch / dinner /
other) — literally "what did I eat, and when."

### Account approval

- **Students are auto-approved** at registration — no waiting.
- **Admin accounts start `PENDING`** and cannot sign in until approved.
- **admin.alex@gmail.com is the seeded super admin** — the only account that
  can approve *other* admin accounts. A regular admin's approval queue only
  ever shows pending students (which, since students auto-approve, will
  normally be empty) — they cannot approve or even see pending admins.
- Approving/rejecting is done from the Admin Console's **Approvals** tab.

---

## 2. Project layout

```
student-meal-app/
├── backend/
│   ├── lib/postgresql-42.7.4.jar     The one external dependency: the JDBC driver
│   ├── src/main/java/com/mealapp/
│   │   ├── Main.java                 Entry point — boots HttpServer, wires routes
│   │   ├── config/                   Env loader + hand-rolled JDBC connection pool
│   │   ├── util/                     JSON, JWT, password hashing, HTTP helpers
│   │   ├── interfaces/               INotifiable, IBudgetManageable
│   │   ├── model/                    User/Student/Admin, Budget/Expense, MealPlan/FoodItem
│   │   ├── dao/                      Raw JDBC data access (one class per table)
│   │   ├── service/                  AIEngine, BudgetService, NotificationService
│   │   ├── controller/               HTTP handlers
│   │   ├── router/                   Small path router + JWT auth middleware
│   │   └── tools/InitDb.java         Applies schema.sql + seed.sql — no Node needed
│   └── Dockerfile                    Multi-stage: JDK to compile, JRE to run
├── frontend/                         Static HTML/CSS/JS client (index / dashboard / admin)
├── database/
│   ├── schema.sql                    Full PostgreSQL schema
│   └── seed.sql                      Demo accounts + BDT-priced food catalog
├── docker-compose.yml                postgres + db-init + backend + frontend
├── .github/workflows/ci.yml          Compile check + real-Postgres integration smoke test
└── docs/                             UML diagram
```

---

## 3. Running it

### Option A — Docker Compose (recommended, zero local install)

```bash
cp .env.example .env          # edit if you want different creds/ports
docker compose up --build
```

This starts, in order: `postgres` (with a healthcheck) → `db-init` (a
one-shot container that applies the schema and seed data, then exits) →
`backend` (waits for `db-init` to succeed) → `frontend` (nginx,
reverse-proxying `/api/*` to the backend).

Open **http://localhost:8080**. Demo logins:

| Role | Email | Password | Notes |
|---|---|---|---|
| Student | student@meal.app | Student@123 | Pre-approved |
| Admin | admin@meal.app | Admin@123 | Regular admin — can't approve other admins |
| Super admin | admin.alex@gmail.com | 12324404 | Can approve **any** account |

If `docker compose up` fails with `address already in use` on port 5432,
something on your machine (often a local PostgreSQL install) already holds
that port — set `DB_PORT=5433` in `.env` and re-run
`docker compose down -v && docker compose up`.

### Option B — Run it directly with the JDK (no Docker)

You need JDK 21+ and a running PostgreSQL instance. The JDBC driver is
already bundled, so there's nothing else to download.

```bash
# 1. Database
createdb student_meal_app

# 2. Configure
cd backend
cp .env.example .env        # edit DB_USER / DB_PASSWORD / DB_PORT if needed

# 3. Compile
find src/main/java -name "*.java" > sources.txt
mkdir -p out
javac -d out -cp "lib/*" @sources.txt

# 4. Load schema + seed data (idempotent — safe to re-run)
java -cp "out:lib/*" com.mealapp.tools.InitDb

# 5. Run the API
java -cp "out:lib/*" com.mealapp.Main
# -> [startup] Student Meal App API (Java) listening on port 4000

# 6. Serve the frontend (separate terminal, any static file server works)
cd ../frontend
python3 -m http.server 5500
# -> open http://localhost:5500
```

### Resetting a stale database

If you've been iterating and hit schema errors like `column "X" does not
exist`, your Postgres data volume predates the current `schema.sql`.
`CREATE TABLE IF NOT EXISTS` never adds missing columns to an existing
table, so a full reset is the fix:
```bash
docker compose down -v && docker compose up --build
```

---

## 4. Where each OOP concept lives

This is an Object-Oriented Programming project, so here's a direct map from
theory to the actual code, file by file.

### 4.1 Abstraction — `User`, `MealPlan`

```java
// model/User.java
public abstract class User {
    public abstract Map<String, Object> generateDashboard() throws Exception;
    public abstract String getRole();
}
```
`User` and `MealPlan` (`model/MealPlan.java`) are both `abstract`: they
define the *shape* every subtype must have (`generateDashboard()`,
`calculateNutrition()`, `calculateCost()`) without saying how — you can
never instantiate a bare `User` or `MealPlan`, only a concrete subtype.

### 4.2 Encapsulation — `FoodItem`, every model class

```java
// model/FoodItem.java
private double price;                 // private field
public double getPrice() { return price; }
public void setPrice(double price) {  // controlled write access
    ...
    FoodItemDao.updatePrice(itemId, price);  // keeps DB in sync
}
```
Every model class keeps its fields `private`/`protected` and exposes
behavior through methods, not raw field access — `setPrice()` isn't just an
assignment, it also persists the change, which a public field could never
enforce.

### 4.3 Inheritance — two hierarchies

```java
public class Student extends User implements INotifiable, IBudgetManageable
public class Admin extends User
public class PersonalizedMealPlan extends MealPlan
```
`Student` and `Admin` both extend `User`, inheriting `userId`/`name`/`email`
and the `login()`/`logout()` logic, while each provides its own
`generateDashboard()`. `PersonalizedMealPlan` extends the abstract
`MealPlan`, inheriting `calculateNutrition()`/`calculateCost()` for free.

### 4.4 Polymorphism — one call, different behavior

```java
User user = UserDao.findById(id);       // could be a Student or an Admin
user.generateDashboard();                 // runs Student's or Admin's version
```
`StudentController` and `AdminController` both call `generateDashboard()`
on a `User` reference without knowing (or caring) which concrete subtype
they're holding — a student gets budget/meal-plan data back, an admin gets
system-wide stats, purely because Java dispatches to the right overridden
method at runtime.

**Method overloading** (compile-time polymorphism) shows up in
`AIEngine.java`, which has two `recommendMeals` methods with different
parameter lists:
```java
public List<FoodItem> recommendMeals(Student student)                                   // UML-exact
public List<FoodItem> recommendMeals(Student student, Set<String> excludedItemIds, double remainingBudget)  // cart-flow version
```

### 4.5 Interfaces — `INotifiable`, `IBudgetManageable`

```java
// interfaces/INotifiable.java
public interface INotifiable {
    void sendNotification(String message) throws SQLException;
}
// interfaces/IBudgetManageable.java
public interface IBudgetManageable {
    double calculateBudget() throws SQLException;
    Expense trackExpense(Expense expense) throws SQLException;
}
```
`Student` is the only class that implements both, matching the UML diagram
exactly — these interfaces name *capabilities* ("can be notified," "manages
a budget") independently of the class hierarchy, so nothing about `Admin`
needs to change if it never needs them.

### 4.6 Composition & Association

- **Composition** (strong "owns-a," matches the UML's `-aiEngine: AIEngine`
  field): `PersonalizedMealPlan` holds a real `AIEngine` instance passed in
  at construction (`model/PersonalizedMealPlan.java`) — not just a
  passing reference, but a field it's built with.
- **Aggregation** ("has-many"): `Budget` aggregates many `Expense` records;
  `MealPlan` aggregates many `FoodItem`s via the `meal_plan_items` join
  table — each can exist and be queried independently of the other.
- **Association** (`Admin ──views──> MealPlan`): `Admin.manageMealPlans()`
  reads across *all* students' plans — a relationship without ownership.

### 4.7 Design patterns built on top of these fundamentals

- **DAO (Data Access Object)** — every table gets its own `*Dao` class
  (`UserDao`, `BudgetDao`, `FoodItemDao`, ...) with static methods, keeping
  raw SQL completely out of the model and controller layers.
- **Static factory / resource pool** — `config/Database.java` manages a
  hand-rolled JDBC connection pool (`borrow()`/`release()`) behind static
  methods, so callers never construct connections themselves.
- **MVC-flavored layering** — `model/` (state + behavior) →
  `service/` (business rules: `AIEngine`, `BudgetService`) →
  `controller/` (HTTP-facing handlers) → `router/` (dispatch + auth
  middleware), each layer only talking to the one below it.
- **Strategy-like ranking** — `AIEngine`'s value-score formula is isolated
  in one private method (`valueScore()`), so the ranking rule could be
  swapped without touching any calling code.

---

## 5. API summary

All routes are prefixed `/api`. Student/Admin routes require
`Authorization: Bearer <token>` from `/auth/login`.

| Method | Route | Description |
|---|---|---|
| GET | `/health` | DB connectivity check |
| POST | `/auth/register` | Create a Student (auto-approved) or Admin (pending) account |
| POST | `/auth/login` | Get a JWT (blocked if account is still pending) |
| GET | `/auth/me` | Current user |
| GET | `/students/dashboard` | Budget + recent plans summary |
| GET/POST | `/students/budget` | View / start a budget period |
| GET/POST | `/students/expenses` | List / log an expense |
| **POST** | **`/students/foods/recommendations`** | **AI Engine — top 10, ascending price, cart-aware** |
| POST | `/students/meal-plans` | Save the plan the student built |
| GET | `/students/meal-plans` / `/:planId` | Plan history / detail |
| **GET** | **`/students/nutrition/summary`** | **Total calories & protein consumed** |
| **GET** | **`/students/nutrition/log`** | **Itemized food log, by date & meal category** |
| GET | `/students/insights` | Catalog insights JSON for the dashboard |
| POST | `/students/insights/analyze` | AIEngine.analyzePreferences — notifies the student |
| GET | `/students/notifications` | Notification inbox |
| GET | `/admin/dashboard` | System-wide stats |
| GET | `/admin/notifications` | Admin's notification inbox (new-signup alerts) |
| **GET** | **`/admin/approvals`** | **Pending signups this admin can act on** |
| **POST** | **`/admin/approvals/:userId/approve`** \| **`/reject`** | **Approve/reject an account** |
| GET/DELETE | `/admin/users` | List / remove users |
| GET | `/admin/meal-plans` | System-wide view of all saved plans |
| GET/POST/PATCH/DELETE | `/admin/food-items` | Manage the catalog the AI Engine draws from |

---

## 6. Testing

`.github/workflows/ci.yml` compiles every source file, then spins up a real
PostgreSQL service container and smoke-tests the live API end to end
(health check → login → AI recommendation call), plus a basic frontend
asset-reference check. There's no separate unit-test framework in play,
matching the "plain Java, nothing else" brief — the integration test
against a real database is the source of truth.
