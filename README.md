# Student Meal & Budget Planner

A full-stack app that helps students plan meals within a tight budget. The
core feature is an **AI agent that suggests one healthy, low-cost food at a
time** — accept it or skip it, and it suggests the next one the same way,
until you've built a plan you're happy with.

- **Backend: plain Java, no frameworks.** `com.sun.net.httpserver.HttpServer`
  (built into the JDK) for HTTP, raw JDBC for PostgreSQL, a hand-rolled JSON
  parser/serializer, a hand-rolled JWT implementation (HMAC-SHA256 via
  `javax.crypto`), and PBKDF2 password hashing (also JDK-native). The only
  external file involved is the PostgreSQL JDBC driver `.jar` — unavoidable,
  since nothing can talk to Postgres from Java without a driver, and it's a
  driver, not a framework.
- **Frontend:** static HTML/CSS/JS ("Price Tag Ledger" design). No build
  step, no framework.
- **Database:** PostgreSQL, accessed with hand-written SQL (no ORM).

---

## 1. Architecture at a glance

```
User (abstract)
 ├─ Student  ⋯implements⋯> INotifiable, IBudgetManageable
 └─ Admin

Student 1───1 Budget 1───* Expense
Admin   1───* MealPlan (abstract)      Admin ──views──> MealPlan (all students)
              └─ PersonalizedMealPlan ──owns──> AIEngine
MealPlan *───* FoodItem   (via meal_plan_items)
```

This maps directly onto the project's UML diagram (`docs/architecture.png`)
and the PostgreSQL schema in `database/schema.sql`. `backend/src/main/java/com/mealapp/interfaces/`
holds `INotifiable` and `IBudgetManageable` as real Java interfaces,
implemented by `Student`. `MealPlan` carries `-planId`/`-date`;
`PersonalizedMealPlan` carries `-aiEngine: AIEngine` as a real field, not
just an association in a comment.

### The AI Engine — three ways to get suggestions

`backend/src/main/java/com/mealapp/service/AIEngine.java` has three public
methods. Two match the UML diagram's signatures exactly; one is an addition
for the one-at-a-time interactive flow.

**`recommendMeals(Student): List<FoodItem>`** *(matches the diagram)* —
returns the top 7 low-cost, high-nutrition items in the whole catalog the
student can currently afford, sorted **ascending by price** (cheapest
first). Backing `GET /api/students/foods/recommendations`, shown as the
"Top 7 picks" panel on the dashboard.

**`analyzePreferences(Student): void`** *(matches the diagram)* — analyzes
the catalog against the student's dietary preference and sends them a
notification with the result. Since the diagram specifies `void`, this
method does not return data directly — `POST /api/students/insights/analyze`
triggers it. The JSON the dashboard's Insights tab actually reads comes
from a separate `insightsSummary(Student): Map` helper (Java can't overload
a method by return type alone, so this had to be a different method name).

**`suggestNext(Student, excludedIds, remainingBudget): FoodItem`**
*(not in the diagram — added for the interactive flow)* — doesn't generate
a whole plan in one shot; suggests **one** food at a time:

1. The frontend calls `POST /api/students/foods/suggest` with whatever's
   already been accepted (`selectedItemIds`) and skipped (`excludedItemIds`).
2. The server recomputes the student's *actual* remaining budget itself
   (active budget minus the price of everything already accepted) — the
   client can never spoof a bigger budget than the student really has.
3. Every food item the student hasn't seen yet, that fits the remaining
   budget and their dietary preference, gets a **value score**:

   ```
   score = (calories + proteinG * 4 * 1.6) / price
   ```

   Multiplying protein grams by 4 converts them to calories-from-protein
   (protein is ~4 kcal/g); the 1.6 weight then rewards protein-dense food
   over plain empty calories at the same price — a simple, fully
   explainable stand-in for "healthy," not just "cheap." `recommendMeals`
   uses the same value score to pick its top 7.
4. The highest-scoring item is returned as the one suggestion.
5. The student accepts (it's added to `selectedItemIds`) or skips (added to
   `excludedItemIds`), and the frontend calls `/foods/suggest` again for the
   next one. Repeat until happy, then `POST /api/students/meal-plans` with
   the final `itemIds` list to save it.

### Admin ↔ MealPlan

The diagram draws an association from `Admin` down to `MealPlan`. This is
implemented as `Admin.manageMealPlans()`, backing `GET /api/admin/meal-plans`
— admins get system-wide visibility into every saved plan (with the
student's name/email attached), not just users and the food catalog.

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
│   │   ├── controller/                HTTP handlers
│   │   ├── router/                   Small path router + JWT auth middleware
│   │   └── tools/InitDb.java         Applies schema.sql + seed.sql — no Node needed
│   └── Dockerfile                    Multi-stage: JDK to compile, JRE to run
├── frontend/                         Static HTML/CSS/JS client
├── database/
│   ├── schema.sql                    Full PostgreSQL schema
│   └── seed.sql                      Demo accounts + 16 sample food items
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
one-shot container that runs `com.mealapp.tools.InitDb`, applying the
schema and seed data, then exits) → `backend` (waits for `db-init` to
succeed) → `frontend` (nginx, reverse-proxying `/api/*` to the backend).

Open **http://localhost:8080**. Demo logins:

| Role    | Email             | Password    |
|---------|-------------------|-------------|
| Student | student@meal.app  | Student@123 |
| Admin   | admin@meal.app    | Admin@123   |

To reset the database completely: `docker compose down -v`.

### Option B — Run it directly with the JDK (no Docker)

You need JDK 21+ and a running PostgreSQL instance. The PostgreSQL JDBC
driver is already bundled at `backend/lib/postgresql-42.7.4.jar`, so there's
nothing to download.

```bash
# 1. Database
createdb student_meal_app   # or: psql -c "CREATE DATABASE student_meal_app;"

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

### Schema notes

- **UUID primary keys** everywhere (`gen_random_uuid()` via `pgcrypto`).
- `users` uses **single-table inheritance** for `User → Student/Admin`
  (`role` column + nullable Student-only columns).
- `food_items.name` is **unique**, so re-running `InitDb` never duplicates
  the catalog. `food_items.dietary_tags` is a Postgres `TEXT[]` with a GIN
  index, so the AI Engine's dietary-preference filter
  (`? = ANY(dietary_tags)`) stays fast even with a large catalog.
- `budgets` has a unique `(student_id, period_start)` constraint; setting a
  budget on a day one already exists **updates it (upsert)** rather than
  erroring, since registration auto-creates a starter budget for "today."
- `meal_plan_items` is the join table between a saved `meal_plans` row and
  the `food_items` the student accepted into it.

---

## 4. API summary

All routes are prefixed `/api`. Student/Admin routes require
`Authorization: Bearer <token>` from `/auth/login`.

| Method | Route | Description |
|---|---|---|
| GET | `/health` | DB connectivity check |
| POST | `/auth/register` | Create a Student or Admin account |
| POST | `/auth/login` | Get a JWT |
| GET | `/auth/me` | Current user |
| GET | `/students/dashboard` | Budget + recent plans summary |
| GET/POST | `/students/budget` | View / start a budget period |
| GET/POST | `/students/expenses` | List / log an expense |
| **POST** | **`/students/foods/suggest`** | **AIEngine.suggestNext — suggest one next food** |
| **GET** | **`/students/foods/recommendations`** | **AIEngine.recommendMeals — top 7, low cost first** |
| POST | `/students/meal-plans` | Save the plan the student built |
| GET | `/students/meal-plans` / `/:planId` | Plan history / detail |
| GET | `/students/insights` | Insights JSON for the dashboard |
| POST | `/students/insights/analyze` | AIEngine.analyzePreferences — notifies the student |
| GET | `/students/notifications` | Notification inbox |
| GET | `/admin/dashboard` | System-wide stats |
| GET/DELETE | `/admin/users` | List / remove users |
| **GET** | **`/admin/meal-plans`** | **Admin—MealPlan association — view all students' plans** |
| GET/POST/PATCH/DELETE | `/admin/food-items` | Manage the catalog the AI Engine draws from |

**Example: the suggestion loop**

```bash
# First suggestion (nothing picked yet)
curl -X POST http://localhost:4000/api/students/foods/suggest \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"selectedItemIds": [], "excludedItemIds": []}'

# Accept it, ask for the next one
curl -X POST http://localhost:4000/api/students/foods/suggest \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"selectedItemIds": ["<itemId-from-above>"], "excludedItemIds": []}'

# Once happy, save the plan
curl -X POST http://localhost:4000/api/students/meal-plans \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"itemIds": ["<id1>", "<id2>"]}'
```

---

## 5. Testing

`.github/workflows/ci.yml` compiles every source file, then spins up a real
PostgreSQL service container and smoke-tests the live API end to end
(health check → login → sequential AI suggestion), plus a basic frontend
asset-reference check. There's no separate unit-test framework in play
(matching the "plain Java, nothing else" brief) — the integration test
against a real database is the source of truth.
