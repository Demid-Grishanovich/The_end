# 3. USER GUIDE

## 3.1 Getting started

### System requirements

The following table lists the minimum and recommended hardware and software requirements for running DataCrowd Lab locally. No local installation of Java, Go, PostgreSQL, or MinIO is required beyond Docker Desktop.

Table 3.1. System requirements

| Component | Minimum | Recommended | Notes |
|---|---|---|---|
| Operating System | Linux (Ubuntu 20.04+), macOS 12+, Windows 10 with WSL2 | Linux (Ubuntu 22.04 LTS) | Windows users must enable WSL2 and use Docker Desktop with WSL2 backend |
| CPU | 4 physical cores | 8 cores | Go-Runner dataset parsing is CPU-bound; fewer cores increase ingestion time |
| RAM available to Docker | 8 GB | 16 GB | PostgreSQL + 4 JVM services each consume ~512 MB; MinIO adds ~256 MB |
| Free disk space | 10 GB | 20 GB | Includes Docker image layers (~4 GB), named volumes, and uploaded dataset files |
| Docker Engine | 24.0+ | Latest stable | Must support `depends_on: condition: service_healthy` (Compose v2 feature) |
| Docker Compose | v2.20+ | Latest stable | Compose v2 is bundled with Docker Desktop; install separately on headless Linux |
| Web browser | Chrome 120+, Firefox 121+, Safari 17+ | Chrome (latest stable) | The frontend uses `EventSource` (SSE), `localStorage`, and CSS custom properties; Internet Explorer is not supported |
| Network | Outbound HTTPS to Docker Hub | Same + outbound HTTPS to api-inference.huggingface.co | HuggingFace access required only when `HF_API_TOKEN` is set; Stripe access required only when `STRIPE_ENABLED=true` |
| Screen resolution | 1280 × 720 | 1920 × 1080 | The dashboard grid layout collapses to 2-column below 900 px and 1-column below 580 px |

---

### Initial setup

The following steps bring the complete system up from a cold start on a machine with Docker Desktop installed. All commands are executed from the repository root unless specified otherwise.

**Step 1 — Clone the repository**

```bash
git clone https://github.com/<owner>/datacrowd-lab.git
cd datacrowd-lab
```

**Step 2 — Configure environment variables**

```bash
cd infra/docker
cp .env.example .env
```

Open `.env` in a text editor. The two values that **must** be changed before starting are:

- `JWT_SECRET` — set to any random string of at least 32 characters (e.g., `openssl rand -base64 32`)
- `INTERNAL_TOKEN` — set to any random string of at least 32 characters

All other values have functional defaults for local development and can be left unchanged.

**Step 3 — Start the full stack**

```bash
docker compose up --build
```

On the first run, Docker downloads base images and compiles all services. This takes approximately 3–5 minutes depending on network speed and CPU. On subsequent runs with warm layer cache, startup completes in under 30 seconds.

**Step 4 — Verify all services are healthy**

Open a second terminal and run the following health checks:

```bash
curl http://localhost:8081/actuator/health   # Auth Service   → {"status":"UP"}
curl http://localhost:8082/actuator/health   # Core Service   → {"status":"UP"}
curl http://localhost:8083/actuator/health   # Payments Svc   → {"status":"UP"}
curl http://localhost:8090/healthz           # Go-Runner      → ok
```

All four endpoints must return the values shown above before proceeding. If any service returns an error, inspect its logs with `docker compose logs <service-name>`.

**Step 5 — Access the platform**

Open a web browser and navigate to `http://localhost:80`. The DataCrowd Lab login page is displayed.

---

### Registration

The platform supports two self-service registration paths and two administrator-managed paths. The following table describes each path.

Table 3.2. Registration paths

| Role | Endpoint | Required Headers | Self-Service? | Notes |
|---|---|---|---|---|
| Worker | `POST /api/auth/register` | `Content-Type: application/json` | Yes | Navigate to `http://localhost:80/register.html` and select the Worker tile; no additional credentials required |
| Client | `POST /api/auth/register-client` | `Content-Type: application/json`, `X-Admin-Key: <ADMIN_KEY>` | Restricted | Navigate to `register.html`, select the Client tile, and enter the admin key (default: `demo-admin-key`); the admin key is configured via the `ADMIN_KEY` environment variable |
| Reviewer | Internal API call | `X-Internal-Token: <INTERNAL_TOKEN>` | No | Requires an existing account to be promoted via `PATCH /internal/users/{userId}/role` with body `{"role":"REVIEWER"}` |
| Admin | Internal API call | `X-Internal-Token: <INTERNAL_TOKEN>` | No | Same promotion mechanism as Reviewer; Admin users can modify roles via the Swagger UI internal endpoint |

**Registration request body (Worker and Client):**

```json
{
  "username": "johndoe",
  "email": "johndoe@example.com",
  "password": "StrongPassword123!"
}
```

Password requirements: minimum 8 characters. Username must be unique across the platform. Email must be a valid format and unique.

**Successful registration response:**

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "role": "WORKER"
}
```

The returned JWT token is stored in `localStorage` by the frontend and is used for all subsequent API calls. The token expires after 60 minutes (configurable via `JWT_TTL_MINUTES`). After expiry, the user must log in again.

---

### Platform access summary

Table 3.3. Service access URLs

| Service | URL | Credentials | Purpose |
|---|---|---|---|
| Frontend application | `http://localhost:80` | Email + password (set during registration) | Primary user interface for Clients, Workers, and Reviewers |
| API Gateway (REST API) | `http://localhost:8080` | JWT Bearer token | Direct API access for advanced users and Swagger UI |
| Auth Service Swagger UI | `http://localhost:8081/swagger-ui.html` | None (browse); JWT (execute) | Interactive documentation for authentication endpoints |
| Core Service Swagger UI | `http://localhost:8082/swagger-ui.html` | None (browse); JWT (execute) | Interactive documentation for annotation workflow endpoints |
| Payments Swagger UI | `http://localhost:8083/swagger-ui.html` | None (browse); JWT (execute) | Interactive documentation for billing endpoints |
| Go-Runner health endpoint | `http://localhost:8090/healthz` | None | Verify Runner is alive; no UI |
| MinIO Console | `http://localhost:9001` | `minioadmin` / `minioadmin` | Browse uploaded dataset files and generated export files |
| pgAdmin 4 | `http://localhost:5050` | `admin@local.dev` / `admin` | Inspect all three logical databases; query `audit_logs` and `failed_items` |
| Prometheus | `http://localhost:9090` | None | Query raw metrics time series |
| Grafana | `http://localhost:3000` | `admin` / `admin` | Pre-provisioned dashboards for annotation throughput and system health |