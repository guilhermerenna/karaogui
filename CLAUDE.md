# KaraoGUI — Claude instructions

## Monorepo layout

- `backend/` — Spring Boot 4.1 / Java 25 / Gradle
- `frontend/` — Angular 22 monorepo (projects: `phone`, `tv`, `api`, `realtime`, `contracts`, `ui`)
- `docker-compose.yml` — Postgres + backend container

## Starting services for browser testing

**Always run this before any browser test session.** Skipping it means stale containers or dev servers from a previous session will serve old code and waste debugging time.

### 1. Backend + Postgres (Docker)

```bash
# From repo root  (use docker-compose, not docker compose — v1 CLI on this machine)
docker-compose down && docker-compose up --build -d
# Wait for healthy
docker-compose ps   # backend should show "healthy"
# Or watch logs
docker-compose logs -f backend
```

The `--build` flag is mandatory — without it Docker reuses a cached image with old code.

Backend is ready when `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

### 2. Frontend dev servers

```bash
# Kill any stale ng serve processes first
pkill -f "ng serve" 2>/dev/null; sleep 1

# Start both (from frontend/)
cd frontend
npx ng serve --project phone --port 4201 --no-open &
npx ng serve --project tv    --port 4200 --no-open &
```

Wait for both to print "Application bundle generation complete" before opening the browser.

| Service  | URL                        |
|----------|----------------------------|
| Phone    | http://localhost:4201      |
| TV       | http://localhost:4200      |
| Backend  | http://localhost:8080      |

## Running backend tests

```bash
cd backend
./gradlew test
```

Tests use Testcontainers (ephemeral Postgres spun up per test run) — no local Postgres needed.

## Before starting a new phase

1. Stop all services: `docker-compose down` + `pkill -f "ng serve"`
2. Run all backend tests: `./gradlew test`
3. Rebuild and restart: follow "Starting services" above
4. Verify health endpoint before opening browser

## Frontend build validation

The correct way to type-check the phone/tv apps (not individual libraries):

```bash
cd frontend
npx ng build --project phone
npx ng build --project tv
```

`ng build --project realtime` in isolation will fail with a `rootDir` TS error — this is a known ng-packagr issue in this workspace; ignore it and use the app builds above instead.
