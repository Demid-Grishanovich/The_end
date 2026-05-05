## 2.2 Technology stack

The technology stack was selected according to three principles: fitness for the specific workload characteristics of each service, long-term support stability, and compatibility with the project's containerized deployment model. No technology was adopted for novelty alone. The following tables document each layer of the stack, the development tooling used, and the external services integrated.

Table 2.4. Stack overview

| Layer | Technology | Version | Justification |
|---|---|---|---|
| Core backend language | Java | 17 (LTS) | Long-term support until 2029; virtual thread availability; first-class Spring Boot integration; mature testing ecosystem |
| Backend framework | Spring Boot | 3.4.1 | Actuator Prometheus endpoint; Spring Security declarative RBAC; Spring Data JPA transaction management; auto-configured Flyway; Caffeine cache integration via Spring Cache abstraction |
| API Gateway | Spring Cloud Gateway | 2024.0.0 (Leyton) | Reactive WebFlux-based routing; path-rewrite filters; CORS configuration; compatible with Spring Boot 3.4.x parent POM |
| Asynchronous file runner | Go | 1.22 | Lightweight goroutines for parallel streaming file parsing; negligible memory footprint per goroutine; streaming-friendly standard library (`encoding/csv`, `bufio`); no external dependency required |
| Relational database | PostgreSQL | 16 | `FOR UPDATE SKIP LOCKED` for concurrent queue semantics; JSONB column type for schema-flexible task payloads; ACID compliance; `uuid-ossp` extension for UUID primary key generation |
| Schema migration | Flyway | Spring Boot BOM managed | Versioned SQL migration files; per-database migration history tables; startup-time application enforces schema correctness before service accepts traffic |
| Object storage | MinIO | Latest (S3-compatible) | S3-compatible API enables transparent migration to AWS S3 in production; eliminates binary blob storage in PostgreSQL; high-throughput streaming upload and download; bucket auto-provisioned by `MinioStorageService` |
| JWT library | jjwt | 0.12.5 | HS256/HS384/HS512 signing; strongly typed `Claims` parsing; actively maintained; compatible with Java 17 |
| Password hashing | Spring Security BCrypt | Spring Boot BOM managed | Industry-standard adaptive hashing; cost factor configurable; salt embedded in hash; no additional library required |
| API documentation | springdoc-openapi | 2.7.0 | OpenAPI 3.x specification generated from controller annotations; per-service Swagger UI at `/swagger-ui.html`; dual security schemes (`bearerAuth` + `internalToken`); Gateway-level aggregation of all service specs |
| In-memory caching | Caffeine | Spring Boot BOM managed | Non-blocking in-process cache; TTL and maximum size configurable per named cache; `@Cacheable` / `@CacheEvict` annotations on service methods; no external infrastructure required |
| Metrics instrumentation | Micrometer | Spring Boot BOM managed | Vendor-neutral instrumentation API; custom `Counter` and `Gauge` beans in `MetricsService`; auto-registers with Prometheus `MeterRegistry`; exposes `/actuator/prometheus` scrape endpoint |
| Metrics storage | Prometheus | 2.45.0 | Pull-based scraping from all three Spring Boot services every 5 seconds; persistent TSDB for alerting and dashboards |
| Dashboards | Grafana | 10.2.0 | Prometheus datasource auto-provisioned via Docker volume mount; supports time-series panels for RPS, latency, and custom annotation counters |
| Billing integration | Stripe Java SDK | 28.0.0 | `Session.create()` for hosted checkout; webhook signature verification via `Webhook.constructEvent()`; `STRIPE_ENABLED` flag activates mock mode for local testing |
| ML inference | HuggingFace Inference API | REST (no SDK) | Zero-shot classification and pre-annotation for TEXT and IMAGE datasets; invoked via standard `RestTemplate`; `HF_API_TOKEN` environment variable; degrades gracefully when token is absent |
| Containerization | Docker | Engine 24.0+ | Multi-stage Dockerfiles: `maven:3.9-eclipse-temurin-21` builder → `eclipse-temurin:21-jre-alpine` runtime; `golang:1.22-alpine` builder → `alpine:3.20` runtime; non-root `appuser` in all images |
| Container orchestration | Docker Compose | v2 | Declarative multi-service topology; named volumes; health checks; dependency ordering (`condition: service_healthy`); environment variable injection from `.env` |
| CI/CD | GitHub Actions | N/A | Per-service parallel jobs; PostgreSQL service containers; Maven and Go module caching; JaCoCo gate; Docker build check on main branch |
| Frontend | Vanilla HTML5 / CSS3 / JavaScript (ES2020) | N/A | No build toolchain required; served by `nginx:alpine`; inter-page communication via `localStorage` for JWT token; SSE via `EventSource` API |

---

Table 2.5. Development tools

| Tool | Purpose | Notes |
|---|---|---|
| IntelliJ IDEA (Ultimate) | Primary IDE for Java/Spring Boot service development; database query inspection via DataGrip plugin; HTTP client for manual API testing | Spring Boot DevTools configured for hot-reload in local development |
| GoLand | IDE for Go-Runner development; integrated `go vet` and `gofmt` checks | Alternatively: VS Code with Go extension |
| Git | Version control; feature-branch workflow with pull requests to `main` | `.gitignore` excludes `.env`, `target/`, and Go build artifacts |
| Apache Maven 3.9 | Build tool for all four Java services; dependency management via Spring Boot parent POM; plugin lifecycle for compile, test, JaCoCo, and Spring Boot packaging | Maven Wrapper (`mvnw`) committed to each service; ensures reproducible builds without global Maven installation |
| JaCoCo Maven Plugin | Code coverage measurement and quality gate enforcement; generates HTML reports at `target/site/jacoco/index.html`; configured with minimum 60% instruction coverage on non-DTO/non-config packages | Version: 0.8.12; executed in `verify` phase; CI fails on coverage gate violation |
| JUnit 5 | Unit and integration test framework for Java services; parameterized tests; assertions via AssertJ | Included via `spring-boot-starter-test` |
| Mockito | Mock object framework for unit tests; `@MockitoBean` in `@WebMvcTest` slices; `@ExtendWith(MockitoExtension.class)` for pure unit tests | Version managed by Spring Boot BOM |
| Testcontainers | Ephemeral PostgreSQL 16 containers for integration tests; `@DynamicPropertySource` for datasource URL injection; `@Testcontainers` + `@Container` annotations | Modules: `junit-jupiter`, `postgresql` |
| MockMvc | Spring MVC test framework for controller-layer tests without starting a full HTTP server; `@WebMvcTest` slices with `@AutoConfigureMockMvc(addFilters = false)` for security filter bypass in focused tests | Included via `spring-boot-starter-test` |
| Docker Desktop | Local container runtime; used for Testcontainers and full `docker compose up` local environment | Alternatively: Docker Engine + Compose Plugin on Linux |
| Postman | Manual API exploration and E2E scenario validation; collection documents the happy-path Client and Worker flows | Postman Collection exportable for Newman-based CI integration (future phase) |
| pgAdmin 4 | PostgreSQL database inspection; schema browsing; DLQ (`failed_items`) and `audit_logs` query during development | Exposed at `http://localhost:5050` in the Compose environment |
| GitHub Actions | CI/CD platform; `.github/workflows/ci.yml` defines the full pipeline | Free tier sufficient for the diploma project's build frequency |

---

Table 2.6. External services and APIs

| Service | Purpose | Pricing Model |
|---|---|---|
| Stripe (stripe.com) | Payment processing; hosted checkout session creation (`Session.create()`); webhook delivery for `checkout.session.completed` events; `STRIPE_ENABLED=false` activates mock mode for local testing | Pay-as-you-go: 1.4% + €0.25 per successful European card transaction; no monthly fee; test mode (sandbox) is free and unlimited |
| HuggingFace Inference API (api-inference.huggingface.co) | Zero-shot text and image classification for ML pre-annotation; `aiSuggestedLabel` and `aiConfidence` fields are written into task `payloadJson` before worker assignment; feature degrades gracefully when `HF_API_TOKEN` is absent | Free tier: rate-limited inference on public models (sufficient for diploma demonstration); PRO tier: $9/month for higher rate limits and GPU-backed inference; Enterprise: custom pricing |
| Docker Hub (hub.docker.com) | Base image registry for `postgres:16`, `prom/prometheus:v2.45.0`, `grafana/grafana:10.2.0`, `minio/minio:latest`, `dpage/pgadmin4:8`, `nginx:alpine`, `maven:3.9-eclipse-temurin-21`, `eclipse-temurin:21-jre-alpine`, `golang:1.22-alpine`, `alpine:3.20` | Free tier: unlimited public image pulls; pull rate limit of 100 pulls/6 hours for unauthenticated requests (mitigated by Docker Hub login in CI) |
| GitHub (github.com) | Source code hosting; GitHub Actions CI/CD; pull request workflow | Free for public repositories; Actions: 2000 minutes/month on free tier (sufficient for the project's CI frequency) |