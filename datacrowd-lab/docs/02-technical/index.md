# 2. TECHNICAL IMPLEMENTATION

## 2.1 Solution architecture

### High-level architecture

The platform is composed of five independently deployable application services — API Gateway, Auth Service, Core Service, Payments Service, and Go-Runner — supported by three isolated PostgreSQL logical databases and one S3-compatible object store (MinIO). All external client traffic enters exclusively through the API Gateway, which performs path-based routing to downstream services. Services communicate with each other through synchronous HTTP calls authenticated by a shared internal token. No service shares a database with any other service.
![ER Diagram](../assets/diagrams/DataCrowdLab_Architecture.png)


*Fig. 2.1. High-level architecture of DataCrowd Lab*

---

Table 2.1. System components

| Component | Description | Technology |
|---|---|---|
| API Gateway | Single public ingress point; performs path-based routing to downstream services; handles CORS; configured as `permitAll` at the gateway level for the demonstration environment | Spring Cloud Gateway 2024.0.0 on Spring Boot 3.4.1; WebFlux (reactive) |
| Auth Service | Manages user registration, login, and JWT token issuance; enforces BCrypt password hashing; exposes internal role-change endpoint | Spring Boot 3.4.1; Spring Security; jjwt 0.12.5; Flyway; PostgreSQL |
| Core Service | Implements all annotation business logic: project lifecycle, dataset ingestion orchestration, task distribution (SKIP LOCKED), answer submission, review workflow, Trust Score management, export generation | Spring Boot 3.4.1; Spring Security; Spring Data JPA; Caffeine; Micrometer; Flyway; PostgreSQL; MinIO SDK |
| Payments Service | Creates Stripe checkout sessions (or mock payment flows); handles Stripe webhooks; calls Core's internal billing grant endpoint on payment success; maintains a double-entry ledger | Spring Boot 3.4.1; Stripe Java SDK 28.0.0; Flyway; PostgreSQL |
| Go-Runner | Asynchronously parses uploaded dataset files (CSV, JSONL, ZIP+manifest); bulk-creates tasks via Core's internal API; updates dataset status; writes unparseable rows to the Dead Letter Queue | Go 1.22; standard library only (no external Go dependencies) |
| auth\_db | Stores user identities, credentials, and roles; owns Flyway migration history for the Auth Service | PostgreSQL 16 logical database; owner: `auth_user` |
| core\_db | Stores projects, datasets, tasks, answers, reviews, points\_ledger, worker\_profiles, audit\_logs, exports, failed\_items; owns Flyway migration history for the Core Service | PostgreSQL 16 logical database; owner: `core_user` |
| payments\_db | Stores payment sessions, ledger entries, and Stripe identifiers; owns Flyway migration history for the Payments Service | PostgreSQL 16 logical database; owner: `payments_user` |
| MinIO | S3-compatible object store for binary dataset source files and generated export files; eliminates binary blob storage in PostgreSQL | MinIO latest; accessed via MinIO Java SDK from Core Service and via HTTP from Go-Runner |
| Prometheus | Scrapes `/actuator/prometheus` endpoints from Auth, Core, and Payments every 5 seconds; stores time-series metrics | prom/prometheus:v2.45.0 |
| Grafana | Provides dashboards over Prometheus metrics; Prometheus datasource auto-provisioned via Docker volume mount | grafana/grafana:10.2.0 |

---

### Data flow

The following sequence diagram illustrates the most complex synchronous data flow in the system: a Worker claiming and submitting a task, followed by a Reviewer approving the answer. This flow exercises the API Gateway, the Core Service, the PostgreSQL database, and the asynchronous audit logging mechanism.
![ER Diagram](../assets/diagrams/DataCrowdLab_DataFlow.png)

*Fig. 2.2. Data flow — task claim, answer submission, and review approval sequence*

---

Table 2.2. Key technical decisions

| Decision | Rationale | Alternatives Considered |
|---|---|---|
| Database-per-service isolation (three logical PostgreSQL databases) | Each service owns its schema absolutely; independent migration cadence; failure isolation between services; no cross-service joins possible at the database layer, enforcing application-layer consistency | Single shared PostgreSQL schema with schema-per-service prefixes (rejected: cross-schema FK still possible, harder to enforce ownership); separate PostgreSQL instances per service (rejected: excessive resource overhead for diploma deployment) |
| PostgreSQL `FOR UPDATE SKIP LOCKED` for task assignment | Eliminates data race conditions under concurrent worker load at the database layer without application-level retry loops; reduces task claim latency from ~200 ms (optimistic lock with retries) to < 10 ms | Optimistic locking with `@Version` column and retry-on-conflict (rejected: 30%+ retry rate under 50 concurrent workers in prototype); Redis-based distributed lock (rejected: additional infrastructure dependency) |
| Go for the dataset processing runner | Lightweight goroutines handle streaming file parsing without blocking API threads; small runtime memory footprint per concurrent operation; standard library CSV and JSON streaming without external dependencies | Java-based async processing inside Core Service (rejected: JVM thread pool exhaustion under large file loads; caused API Gateway timeouts in the prototype); Python subprocess (rejected: startup overhead, no type safety for the internal API contracts) |
| Synchronous HTTP for inter-service communication (no message broker) | Reduces operational complexity; sufficient for the current workload; the single async boundary (Core → Runner) is handled by fire-and-forget HTTP + DLQ; appropriate for a diploma deployment target | Apache Kafka or RabbitMQ message broker (rejected: adds cluster management overhead, dead-letter topic configuration, and consumer group coordination that is not justified by the current workload scale) |
| Finite state machine enforced at the service layer (not database CHECK constraints) | Permits forward-compatible addition of new status values without schema migrations; state transition validation co-located with business logic; invalid transitions produce explicit 409 Conflict responses | Database-level CHECK constraints per status column (rejected: every status addition requires a migration; harder to produce user-friendly error messages); no state enforcement (rejected: allows data corruption under concurrent failures) |
| Caffeine in-memory cache for high-read endpoints | Reduces PostgreSQL load on endpoints queried frequently (worker stats, available projects list, project public info) without introducing Redis infrastructure; TTL-based expiry with event-driven eviction on reviewer verdicts | Redis (rejected: adds external infrastructure dependency and network hop latency for a single-node deployment); no caching (rejected: each worker page load triggers three aggregate DB queries per request) |
| Stateless JWT authentication (no refresh token rotation) | Horizontally scalable without session affinity; token issuance isolated in Auth Service; verification duplicated in Core and Payments using the shared `JWT_SECRET` | Stateful session cookies with server-side session store (rejected: requires sticky sessions or shared Redis; conflicts with stateless service design); separate refresh token flow with token rotation (deferred to Phase 2 as unnecessary complexity for the diploma scope) |

---

Table 2.3. Security overview

| Aspect | Implementation |
|---|---|
| Authentication | Stateless HS256 JWT tokens issued exclusively by the Auth Service; tokens carry `userId` and `role` claims; TTL configurable via `JWT_TTL_MINUTES` (default: 60 minutes); `JwtAuthenticationFilter` (`OncePerRequestFilter`) verifies signature and expiry in Core and Payments on every request |
| Authorization | Role-Based Access Control enforced at three independent layers: (1) the `role` JWT claim extracted by `JwtAuthenticationFilter` sets Spring Security authorities (`ROLE_CLIENT`, `ROLE_WORKER`, etc.); (2) service-level checks in business logic (e.g., `getOwnedOrThrow` verifies project ownership before any mutation); (3) internal endpoints (`/internal/**`) require `X-Internal-Token` header verified by `InternalTokenFilter` before the JWT filter — user-issued JWTs are structurally rejected on internal paths |
| Data protection | Passwords stored exclusively as BCrypt hashes via `BCryptPasswordEncoder`; plaintext passwords never logged, cached, or returned in API responses; `v_users_masked` database view exposes only masked email addresses (`ab****@domain.com`) to internal diagnostic tooling; dataset binary files stored in MinIO rather than in PostgreSQL BYTEA columns, reducing sensitive data exposure surface in the DB |
| Input validation | Jakarta Validation (`@NotBlank`, `@NotNull`, `@Min`, `@Max`, `@Email`, `@Pattern`) on all request DTOs; global `@RestControllerAdvice` exception handlers map `ConstraintViolationException` to RFC 7807 Problem Detail 400 responses; ZIP file extraction uses `safeResolve()` with path traversal (zip-slip) protection; JWT secret minimum length of 32 characters enforced at service startup |
| Secrets management | All secrets (`JWT_SECRET`, `INTERNAL_TOKEN`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET`, database passwords, MinIO credentials) externalized as environment variables; `.env.example` committed to the repository; `.env` (containing real values) excluded via `.gitignore`; JWT secret and internal token minimum-length validation performed in constructors of `JwtService` and `InternalTokenFilter` at startup — service refuses to start if secrets are absent or too short |