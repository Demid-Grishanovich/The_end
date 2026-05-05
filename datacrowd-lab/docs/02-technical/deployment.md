## 2.3 Deployment and DevOps

### Deployment architecture

The platform is deployed as a collection of Docker containers managed by Docker Compose. All containers share a single internal bridge network (`datacrowd-net`) that isolates them from the host network while permitting inter-container communication by service name. A second network (`datacrowd-network`) is declared for the frontend container. Persistent state is maintained in four named Docker volumes that survive `docker compose down` without data loss.
![ER Diagram](../assets/diagrams/DataCrowdLab_Deployment.png)


*Fig. 2.3. Deployment architecture — Docker Compose container topology*

---

Table 2.7. Environments

| Environment | URL | Branch | Notes |
|---|---|---|---|
| Local development | `http://localhost:8080` (Gateway), `http://localhost:80` (Frontend) | Any feature branch | Started via `docker compose up --build` from `infra/docker/`; uses `.env` for secrets; all services run on a single Docker host |
| CI build environment | N/A (no exposed URL) | `main`, `develop`, `master` (on push/PR) | GitHub Actions runners; PostgreSQL service container per job; `STRIPE_ENABLED=false`; coverage gate enforced; Docker image build check on `main`/`master` only |
| Staging (planned) | TBD | `main` | Target: single VM or Kubernetes namespace; requires Secrets Manager integration and real DNS; planned for Phase 2 |
| Production (planned) | TBD | Tagged releases | Target: Kubernetes cluster with Helm charts; horizontal pod autoscaling on Core Service; managed PostgreSQL (e.g., AWS RDS); CDN for frontend static assets; planned for Phase 3 |

---

### CI/CD pipeline

The CI/CD pipeline is defined in `.github/workflows/ci.yml` and is triggered on every push and pull request to the `main`, `develop`, and `master` branches. The pipeline consists of five parallel service-level jobs followed by one sequential Docker build check job that runs only on the main branch after all preceding jobs pass.
![ER Diagram](../assets/diagrams/DataCrowdLab_CICD.png)

*Fig. 2.4. CI/CD pipeline — GitHub Actions workflow*

---

Table 2.8. Pipeline steps

| Step | Tool | Actions |
|---|---|---|
| Trigger | GitHub Actions (`on: push`, `on: pull_request`) | Activated on push or pull request to `main`, `develop`, `master` branches |
| Code checkout | `actions/checkout@v4` | Clones the repository at the triggering commit SHA |
| JDK setup (Java services) | `actions/setup-java@v4` (distribution: `temurin`, version: `17`) | Installs Eclipse Temurin JDK 17; restores Maven local repository from cache using `pom.xml` hash as cache key |
| Go setup (Runner job) | `actions/setup-go@v5` (version: `1.22`) | Installs Go 1.22; restores Go module cache using `go.sum` hash as cache key |
| PostgreSQL service container | GitHub Actions `services:` block (`postgres:16` image) | Starts a PostgreSQL 16 container with per-service credentials; health-check (`pg_isready`) polled until ready before test step |
| Compile and test — Java | `mvn -B verify --no-transfer-progress` | Executes Maven lifecycle phases: `validate` → `compile` → `test` → `verify`; Testcontainers integration tests run in the `test` phase; JaCoCo `prepare-agent` and `report` goals bound to lifecycle |
| Coverage gate — Java | JaCoCo Maven Plugin (`jacoco:check`) | Enforces minimum 60% instruction coverage on business logic packages; excludes `**/*Config*`, `**/*Application*`, `**/dto/**`, `**/entity/**`, `**/model/**`; build fails on gate violation |
| Build — Go | `go build -v ./...` | Compiles all Go packages in the runner service; `-v` flag prints compiled packages for CI log visibility |
| Static analysis — Go | `go vet ./...` | Runs the Go static analyzer; reports suspicious constructs; CI fails on any `go vet` finding |
| Docker image build | `docker build` (6 invocations) | Builds images for all six services using their respective Dockerfiles; no `--push` flag (images are not published in the diploma CI configuration); verifies that multi-stage builds complete successfully |

---

Table 2.9. Environment variables

| Variable | Description | Required | Example Value |
|---|---|---|---|
| `JWT_SECRET` | HMAC-SHA256 signing key for JWT token issuance (Auth) and verification (Core, Payments); minimum 32 characters enforced at startup | **Yes** | `dc_jwt_secret_change_me_please_32_bytes_minimum!` |
| `INTERNAL_TOKEN` | Shared secret for `X-Internal-Token` header; authenticates Core→Runner and Payments→Core internal HTTP calls | **Yes** | `super-internal-token-change-me` |
| `JWT_TTL_MINUTES` | JWT token time-to-live in minutes (Auth Service only) | No (default: `60`) | `60` |
| `ADMIN_KEY` | Value required in `X-Admin-Key` header to register a CLIENT-role user | **Yes** | `demo-admin-key` |
| `SPRING_DATASOURCE_URL` | JDBC connection URL for the service's own logical database | **Yes** | `jdbc:postgresql://postgres:5432/core_db` |
| `SPRING_DATASOURCE_USERNAME` | Database username for the service | **Yes** | `core_user` |
| `SPRING_DATASOURCE_PASSWORD` | Database password for the service | **Yes** | `core_pass` |
| `POSTGRES_USER` | Superuser username for the PostgreSQL container | **Yes** | `postgres` |
| `POSTGRES_PASSWORD` | Superuser password for the PostgreSQL container | **Yes** | `postgres_pass` |
| `AUTH_DB` / `AUTH_DB_USER` / `AUTH_DB_PASSWORD` | Auth logical database name and credentials; used by the PostgreSQL init script | **Yes** | `auth_db` / `auth_user` / `auth_pass` |
| `CORE_DB` / `CORE_DB_USER` / `CORE_DB_PASSWORD` | Core logical database name and credentials | **Yes** | `core_db` / `core_user` / `core_pass` |
| `PAYMENTS_DB` / `PAYMENTS_DB_USER` / `PAYMENTS_DB_PASSWORD` | Payments logical database name and credentials | **Yes** | `payments_db` / `payments_user` / `payments_pass` |
| `STRIPE_ENABLED` | Toggles real Stripe checkout (`true`) vs. mock payment flow (`false`) | No (default: `false`) | `false` |
| `STRIPE_SECRET_KEY` | Stripe API secret key; required only when `STRIPE_ENABLED=true` | Conditional | `sk_test_51TPJz...` |
| `STRIPE_WEBHOOK_SECRET` | Stripe webhook signing secret for `Webhook.constructEvent()` verification; required only when `STRIPE_ENABLED=true` | Conditional | `whsec_5c930bc507...` |
| `PAYMENTS_AMOUNT_CENTS` | Default checkout amount in minor currency units (cents) | No (default: `500`) | `10000` |
| `PAYMENTS_TASK_QUOTA` | Number of task quota units granted per successful payment | No (default: `100`) | `100` |
| `PAYMENTS_SUCCESS_URL` | Stripe redirect URL on successful payment | No (default: `http://localhost:80/payment.html?success=1`) | `http://localhost:80/payment.html?success=1` |
| `PAYMENTS_CANCEL_URL` | Stripe redirect URL on cancelled payment | No (default: `http://localhost:80/payment.html?cancel=1`) | `http://localhost:80/payment.html?cancel=1` |
| `CORE_INTERNAL_BASE_URL` | Base URL of the Core Service used by the Payments Service for billing grant callbacks | **Yes** | `http://core-service:8082` |
| `RUNNER_BASE_URL` | Base URL of the Go-Runner used by the Core Service to trigger dataset processing | **Yes** | `http://runner:8090` |
| `STORAGE_TYPE` | Storage backend selector: `local` (shared Docker volume) or `minio` (MinIO S3-compatible) | No (default: `local`) | `local` |
| `MINIO_ROOT_USER` | MinIO root access key | **Yes** (when `STORAGE_TYPE=minio`) | `minioadmin` |
| `MINIO_ROOT_PASSWORD` | MinIO root secret key | **Yes** (when `STORAGE_TYPE=minio`) | `minioadmin` |
| `MINIO_ENDPOINT` | MinIO server endpoint URL | **Yes** (when `STORAGE_TYPE=minio`) | `http://minio:9000` |
| `MINIO_BUCKET` | MinIO bucket name for dataset files and exports | No (default: `datacrowd-datasets`) | `datacrowd-datasets` |
| `DATA_DIR` | Local filesystem path for dataset files when `STORAGE_TYPE=local`; must be writable by the container user | No (default: `/data`) | `/data` |
| `HF_API_TOKEN` | HuggingFace Inference API token for ML pre-annotation; feature degrades gracefully when absent | No | `hf_XXXXXXXXXXXXXXXXXXXX` |
| `PGADMIN_DEFAULT_EMAIL` | pgAdmin login email | No (default: `admin@local.dev`) | `admin@local.dev` |
| `PGADMIN_DEFAULT_PASSWORD` | pgAdmin login password | No (default: `admin`) | `admin` |
| `TZ` | Timezone for all containers; affects timestamp formatting in logs | No (default: `Europe/Vilnius`) | `Europe/Vilnius` |

---

### How to run locally

The following commands reproduce the complete local environment. All commands are executed from the `infra/docker/` directory of the repository.

```bash
# 1. Clone the repository
git clone https://github.com/Demid-Grishanovich/The_end
cd datacrowd-lab

# 2. Navigate to the Docker infrastructure directory
cd infra/docker

# 3. Copy the environment template and populate secrets
cp .env.example .env
# Edit .env: set JWT_SECRET (min 32 chars), INTERNAL_TOKEN,
# and optionally HF_API_TOKEN and STRIPE_* variables

# 4. Build all images and start the full stack
docker compose up --build
# First run: ~3 minutes (downloads base images, compiles all services)
# Subsequent runs: ~25-30 seconds (layer cache reuse)

# 5. Verify all services are healthy
curl http://localhost:8081/actuator/health   # Auth Service  → {"status":"UP"}
curl http://localhost:8082/actuator/health   # Core Service  → {"status":"UP"}
curl http://localhost:8083/actuator/health   # Payments Service → {"status":"UP"}
curl http://localhost:8090/healthz           # Go-Runner     → ok

# 6. Access the frontend
# Open http://localhost:80 in a browser
# Register a Worker at: POST http://localhost:8080/api/auth/register
# Register a Client at:  POST http://localhost:8080/api/auth/register-client
#   (Header: X-Admin-Key: demo-admin-key)

# 7. Run per-service tests locally (requires Docker for Testcontainers)
cd services/auth-service && mvn test
cd services/core-service && mvn test
cd services/payments-service && mvn test

# 8. Run Go Runner tests and static analysis
cd services/runner
go build -v ./...
go vet ./...

# 9. Stop the environment (preserves named volumes and data)
docker compose down

# 10. Stop and destroy all data (full reset)
docker compose down -v
```

---

Table 2.10. Monitoring and logging

| Aspect | Tool | Dashboard / Endpoint | Notes |
|---|---|---|---|
| Metrics collection | Micrometer (embedded in Spring Boot Actuator) | `/actuator/prometheus` on each service (`:8081`, `:8082`, `:8083`) | Auto-registers JVM, HTTP, datasource connection pool, and custom annotation counters (`datacrowd_tasks_submitted_total`, `datacrowd_tasks_approved_total`, `datacrowd_honeypot_failed_total`, `datacrowd_bot_detected_total`, `datacrowd_tasks_active_locks`, `datacrowd_projects_created_total`) |
| Metrics storage | Prometheus | `http://localhost:9090` | Scrape interval: 5 seconds; evaluation interval: 5 seconds; targets: `core-service:8082`, `auth-service:8081`, `payments-service:8083`; retention: default (15 days) |
| Dashboards | Grafana | `http://localhost:3000` (admin / admin) | Prometheus datasource auto-provisioned from `infra/docker/grafana/datasources/prometheus.yml`; panels: request rate, p95 latency, active task locks gauge, honeypot failure rate, bot detection rate |
| Health checks | Spring Boot Actuator | `/actuator/health` on each service | Exposes DB connectivity status; used by Docker Compose `healthcheck` directives to gate dependent service startup |
| Application logging | Spring Boot default (Logback) | `docker compose logs -f <service>` | Log level: INFO by default; SQL logging enabled in Core Service (`format_sql: true`); structured JSON logging not yet configured (planned for Phase 2) |
| Database inspection | pgAdmin 4 | `http://localhost:5050` (admin@local.dev / admin) | Direct query access to all three logical databases; used for DLQ (`failed_items`) inspection and `audit_logs` querying during development and demonstration |
| Audit trail | PostgreSQL `audit_logs` table (core\_db) | Queryable via pgAdmin or any PostgreSQL client | Immutable append-only log of all sensitive state transitions: `PROJECT_CREATED`, `DATASET_UPLOADED`, `TASKS_GENERATED`, `TASK_LOCKED`, `TASK_SUBMITTED`, `ANSWER_APPROVED`, `ANSWER_REJECTED`, `HONEYPOT_FAILED`, `BOT_DETECTED`, `EXPORT_CREATED`; written asynchronously via `@Async AuditService` to avoid blocking business logic |