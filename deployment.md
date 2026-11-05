# Deployment

- **Repository:** https://github.com/ManiraM-1/isocode-township
- **Live app:** https://isocode-township.onrender.com (Render, free tier; see the cold-start note below)

The app is one Docker image. A Spring Boot backend (Java 21) serves the REST API and the built React frontend from the same origin, so the httpOnly session cookie works without any cross-site setup. The data lives in PostgreSQL 16 (Neon in production, Docker locally).

---

## Demo logins (for reviewers)

The deployed app runs in **demo mode**. Sign-in is by mobile number and a one-time code. No SMS is sent: the code is shown on screen in a green banner, and the login page lists these accounts, so one tap fills the number.

| Who | Mobile number | What to look at |
|---|---|---|
| Neeraj Kulkarni, resident, T07-1203 | +91 90000 00001 | All dues paid |
| Swati Joshi, resident (tenant), T12-0408 | +91 90000 00002 | 47 days overdue, an open leak complaint |
| Aamir Shaikh, resident, T21-1601 | +91 90000 00003 | Part-paid bill, 18 days overdue |
| Anjali Deshmukh, help desk | +91 90000 01101 | Complaint board, payments, committee |
| Kiran Shinde, help desk (duty manager) | +91 90000 01106 | Receives safety escalations |

Each number can request 3 codes per 10 minutes; after that, wait or use another account.

`docs/DEMO.md` is a five-minute walkthrough. As Anjali, **Reset demo data** (desk sidebar, or the top bar on a phone) restores the starting scenario relative to today. All names and numbers are fictional.

---

## Environment variables (names only)

| Name | Required | Purpose |
|---|---|---|
| `DATABASE_URL` | yes | JDBC URL, e.g. `jdbc:postgresql://<host>/<db>?sslmode=require` |
| `DATABASE_USERNAME` | yes | Database user |
| `DATABASE_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | Signs session tokens; at least 32 bytes, random |
| `GEMINI_API_KEY` | no | Google Gemini key for complaint classification; without it the keyword fallback classifies and complaints go to "needs review" |
| `GEMINI_MODEL` | no | Defaults to `gemini-3.5-flash-lite` |
| `APP_DEMO_MODE` | no | `true` shows codes on screen, lists demo accounts and enables demo reset; default `false` |
| `APP_TIMEZONE` | no | Defaults to `Asia/Kolkata` |
| `PORT` | set by Render | HTTP port; defaults to 8080 |
| `DB_PORT` | local only | Host port for the Docker database in `docker-compose.yml`; default 5432 |

Never commit `.env`. `.env.example` lists the same names with dummy values.

---

## Run locally

### Prerequisites
- Docker Desktop (for PostgreSQL and the backend tests)
- JDK 21 and Node.js 22 (only to run backend and frontend separately; the Docker image needs neither)
- Git

### 1. Clone and create `.env`

**Windows (PowerShell)**
```powershell
git clone https://github.com/ManiraM-1/isocode-township.git
cd isocode-township
Copy-Item .env.example .env
# Generate a JWT secret and paste it into .env as JWT_SECRET:
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
notepad .env
```

**macOS / Linux**
```bash
git clone https://github.com/ManiraM-1/isocode-township.git
cd isocode-township
cp .env.example .env
# Generate a JWT secret and paste it into .env as JWT_SECRET:
openssl rand -base64 48
${EDITOR:-nano} .env
```

In `.env`, set `DATABASE_PASSWORD` to any value, `JWT_SECRET` to the generated value and `APP_DEMO_MODE=true`. `GEMINI_API_KEY` is optional. If another PostgreSQL already uses port 5432, set `DB_PORT=5433` and use `localhost:5433` in `DATABASE_URL`.

### 2. Start the database
```bash
docker compose up -d db
```

### Option A: one container (closest to production)

Run these from the repository root.

**Windows (PowerShell)**
```powershell
docker build -t township .
docker run --rm -p 8080:8080 --env-file .env `
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/township township
```

**macOS / Linux**
```bash
docker build -t township .
docker run --rm -p 8080:8080 --env-file .env \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/township township
```
On Linux, add `--add-host=host.docker.internal:host-gateway`. Use your `DB_PORT` instead of 5432 if you changed it.

Open **http://localhost:8080**. The first start creates the schema and seeds the demo data, which takes a few seconds.

### Option B: backend and frontend separately (for development)

Terminal 1, the backend (reads `../.env`):
```powershell
cd backend
.\mvnw.cmd spring-boot:run        # Windows
```
```bash
cd backend
./mvnw spring-boot:run            # macOS / Linux
```

Terminal 2, the frontend (proxies `/api` to `localhost:8080`):
```bash
cd frontend
npm ci
npm run dev
```
Open **http://localhost:5173**.

### Run the tests
```bash
cd backend && ./mvnw verify          # Windows: .\mvnw.cmd verify   (Docker must be running: Testcontainers)
cd frontend && npm run build && npm test -- --run
```

The live Gemini accuracy check is excluded by default. It needs a real key:
`./mvnw test -Dtest=ClassifierEvalTest -DexcludedGroups= -Dsurefire.failIfNoSpecifiedTests=false`

---

## How it is deployed

- **Image:** the multi-stage `Dockerfile` builds the frontend with Node and runs its tests. Maven then packages the Spring Boot jar with the built frontend in `static/`. The runtime is a slim JRE 21 image running as a non-root user. Spring serves files that exist and falls back to `index.html` for client routes; `/api/**` is never shadowed.
- **Web service:** Render, from the `Dockerfile` (blueprint in `render.yaml`), health check `GET /api/health`, demo mode on.
- **Database:** Neon PostgreSQL 16 over TLS (`sslmode=require`). Flyway creates the schema on start. With demo mode on, an empty database is seeded automatically.
- **Secrets:** set only in the Render dashboard. `JWT_SECRET` is generated by Render.
- **Security headers:** Content-Security-Policy (`default-src 'self'`), X-Frame-Options DENY, nosniff, a strict referrer policy and a Permissions-Policy. Sessions use an httpOnly, Secure, SameSite=Lax cookie, with CSRF double-submit on every write.

### Free-tier cold start

Render's free web services sleep after about 15 minutes without traffic. The first request after that takes **about a minute** while the container and JVM start. Open the live link once and wait for the login page before a demo; after that it is fast. Neon's free database also pauses when idle and wakes in a second or two on the first query.

### Reset demo data on the live app

"Reset demo data" takes **1–2 minutes** on the free tier. During that time the demo login list is empty; wait for it to return, then sign in.
