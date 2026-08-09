# ---- Student Meal App: Backend API (pure Java, no frameworks) ----
# NOTE: build context for this Dockerfile is the REPO ROOT (see docker-compose.yml),
# because it needs both backend/ and database/ (schema.sql, seed.sql).

# ---------- Stage 1: compile ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

COPY backend/lib ./lib
COPY backend/src ./src

RUN find src -name "*.java" > sources.txt \
    && mkdir -p out \
    && javac -d out -cp "lib/*" @sources.txt

# ---------- Stage 2: run ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /build/out ./out
COPY backend/lib ./lib
# InitDb looks for database/schema.sql relative to a few candidate paths,
# including /app/database — this lands it exactly there.
COPY database ./database

ENV PORT=4000
EXPOSE 4000

# Alpine's busybox wget is available out of the box — no curl install needed.
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD wget -qO- http://localhost:4000/api/health >/dev/null 2>&1 || exit 1

CMD ["java", "-cp", "out:lib/*", "com.mealapp.Main"]
