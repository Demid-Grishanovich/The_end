## Criterion: Microservices

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

A monolithic deployment of all annotation platform functionality would create three undesired couplings that conflict with the platform's quality and scalability requirements. First, a shared release cadence means that a schema migration for the payments feature requires redeploying the authentication logic and vice versa. Second, a shared failure domain means that a memory leak in the dataset parsing code can degrade authentication response times for all concurrent users. Third, a shared scaling envelope means that the high-throughput dataset parsing workload cannot be scaled independently of the low-throughput authentication workload, forcing overprovisioning of the entire monolith. The platform's four principal concerns — identity management, annotation business logic, financial transactions, and asynchronous file processing — have divergent operational characteristics that justify independent deployment boundaries.

#### Decision

Decompose the system into five independently deployable services: API Gateway (routing and cross-cutting concerns), Auth Service (identity and JWT issuance), Core Service (all annotation business logic), Payments Service (billing and Stripe integration), and Go-Runner (asynchronous file processing). Each business service owns exactly one logical database. No service reads from or writes to another service's database. Cross-service communication uses synchronous HTTP authenticated by the `X-Internal-Token` header for service-to-service calls and JWT Bearer tokens for user-facing calls. The system operates without a message broker.

#### Alternatives considered

Table 2.23. Alternatives considered — Microservices

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Modular monolith with in-process module boundaries | Single deployment unit simplifies CI/CD; no network serialization overhead; shared transaction context available across modules | Cannot scale annotation processing independently of authentication; a bug in one module can corrupt shared state across all modules; Flyway migration coordination across modules in a single schema is complex | The processing workload (Go-Runner) has fundamentally different runtime characteristics from the authentication workload (Auth Service); in-process boundaries cannot isolate JVM thread pool exhaustion between modules |
| Shared database between Core and Auth Services | Simplifies user identity resolution (direct FK from `core_db.answers.user_id` to `auth_db.users`); eliminates logical reference management | Breaks service ownership; Core could accidentally mutate `auth_db.users`; Auth migrations and Core migrations must be coordinated; a slow Core query can hold locks that degrade Auth performance | The database-per-service pattern is the explicit foundation of the microservices architecture; even if a shared database is used in practice, it must be treated as separate for migration and ownership purposes |
| Event-driven architecture with Apache Kafka for all inter-service communication | True temporal decoupling; built-in retry and dead-letter semantics; natural fan-out for future consumers | Kafka cluster requires ZooKeeper or KRaft, broker configuration, topic management, and consumer group coordination; the current workload has exactly one producer and one consumer per event type, which does not justify the operational overhead | The single async boundary (Core → Runner) is adequately served by a fire-and-forget HTTP call combined with the `failed_items` DLQ; all other inter-service calls are intentionally synchronous for simplicity and debuggability |
| Service mesh (Istio/Linkerd) for inter-service communication | Transparent mTLS between services; circuit breaking; distributed tracing; load balancing | Requires Kubernetes as a prerequisite; significant operational complexity; not deployable via Docker Compose; out of scope for the diploma deployment target | The platform targets Docker Compose for the diploma demonstration; service mesh capabilities are deferred to the Kubernetes migration phase |

#### Consequences

**Positive:**
- Auth Service remains available and issues tokens even when Core Service is degraded; user login is not blocked by annotation processing failures
- The Go-Runner can be scaled horizontally by adding Runner replicas behind a load balancer without any changes to the Core Service, provided `RUNNER_BASE_URL` points to the load balancer
- Each service has an independent Flyway migration history, enabling Auth and Payments schema changes to be deployed without coordination with Core
- The Payments Service can be replaced with a different billing provider without touching the Core Service code; only `CoreBillingClient` needs to be updated

**Negative:**
- Debugging a request that spans multiple services requires correlating logs from Gateway, Core, and potentially Runner; there is no distributed tracing (OpenTelemetry) configured in the current version
- The synchronous HTTP coupling between Payments and Core means that if Core is temporarily unavailable during a billing grant callback, the payment is recorded as `SUCCEEDED` in `payments_db` but the project remains `UNPAID` in `core_db` until the next successful callback attempt
- Five separate Docker images must be built and kept in sync; each service has its own `pom.xml` with dependency versions that may drift over time

**Neutral:**
- The Go-Runner is the only service not implemented in Java; this means Go debugging tools (Delve) and profiling approaches differ from the four Java services
- The API Gateway's `permitAll` configuration is documented as a deliberate diploma-scope simplification; adding JWT verification at the Gateway layer is a one-PR change

---

### Implementation details

#### Project structure
services/
├── api-gateway/                           # Spring Cloud Gateway :8080
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/resources/application.yml # Route definitions (3 routes + RewritePath)
│
├── auth-service/                          # Spring Boot :8081 → auth_db
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/datacrowd/auth/
│       ├── api/AuthController.java
│       ├── api/internal/InternalUsersController.java  # PATCH /internal/users/{id}/role
│       ├── jwt/JwtService.java
│       └── service/AuthService.java
│
├── core-service/                          # Spring Boot :8082 → core_db + MinIO
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/datacrowd/core/
│       ├── api/                           # 8 public controllers
│       ├── api/internal/                  # 4 internal controllers (Runner + Payments callbacks)
│       ├── service/                       # 10 service classes
│       └── security/                      # JwtAuthFilter + InternalTokenFilter
│
├── payments-service/                      # Spring Boot :8083 → payments_db
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/main/java/com/datacrowd/payments/
│       ├── api/PaymentsController.java
│       ├── api/StripeWebhookController.java
│       └── service/CoreBillingClient.java  # Calls /internal/billing/projects/{id}/grant
│
└── runner/                                # Go 1.22 :8090 (stateless)
├── Dockerfile
├── go.mod
└── main.go                            # HTTP server + CSV/JSONL/ZIP parser + bulk task creator

#### Key implementation decisions

Table 2.24. Key implementation decisions — Microservices

| Decision | Rationale |
|---|---|
| Auth Service issues JWTs; Core and Payments only verify them | JWT verification requires only the shared `JWT_SECRET`; there is no need for Core or Payments to call Auth on every request; this keeps Auth's failure domain isolated from the request path of annotation operations |
| `CoreBillingClient` in Payments calls Core's `/internal/billing/projects/{id}/grant` synchronously | The billing grant must be acknowledged before the `PaymentService.markPaidMock()` method returns; if the grant fails, the payment status should not be set to `SUCCEEDED`; synchronous call allows transactional rollback on failure |
| Go-Runner posts task creation results to Core's `/internal/tasks/bulk` in configurable batch sizes | A single HTTP call per task would create thousands of round trips for large datasets; batching (default 500 tasks per request) reduces the number of HTTP calls by 500× while keeping individual request body sizes manageable |
| All five services share a single Docker Compose network (`datacrowd-net`) for inter-container DNS resolution | Docker Compose's built-in DNS allows services to reference each other by container name (e.g., `http://core-service:8082`) without hardcoding IP addresses; the frontend uses a second network (`datacrowd-network`) to isolate it from direct database access |
| No service discovery or load balancer between Core and Runner in the current scope | A single Runner instance is sufficient for the diploma demonstration; the `RUNNER_BASE_URL` environment variable externalises the Runner endpoint, making it trivial to replace with a load balancer URL when horizontal scaling is needed |

#### Code examples

```java
// CoreBillingClient.java (Payments Service) — internal HTTP call to Core
public void grantPaidAccess(UUID projectId, int taskQuotaDelta) {
    BillingGrantRequest body = new BillingGrantRequest(taskQuotaDelta, "PAID");
    restClient.post()
        .uri("/internal/billing/projects/{projectId}/grant", projectId)
        .header("X-Internal-Token", internalToken)
        .body(body)
        .retrieve()
        .toBodilessEntity();
}
```

```go
// main.go (Go-Runner) — bulk task creation HTTP call to Core
func flushTasks(tasks []TaskItem, datasetId, projectId, coreURL, token string) {
    body := BulkCreateRequest{DatasetId: datasetId, ProjectId: projectId, Tasks: tasks}
    data, _ := json.Marshal(body)
    req, _ := http.NewRequest("POST", coreURL+"/internal/tasks/bulk", bytes.NewReader(data))
    req.Header.Set("Content-Type", "application/json")
    req.Header.Set("X-Internal-Token", token)
    resp, err := http.DefaultClient.Do(req)
    if err != nil || resp.StatusCode >= 300 {
        log.Printf("bulk flush failed: %v", err)
    }
}
```

#### Diagram
![ER Diagram](../../assets/diagrams/Microservices_Component.png)


*Fig. 2.8. Microservices component diagram — service boundaries, communication protocols, and data ownership*

---

### Requirements checklist

Table 2.25. Requirements checklist — Microservices

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | Each service must own exactly one database and must not access another service's database directly | ✅ | Three separate `SPRING_DATASOURCE_URL` environment variables; no cross-database JDBC connections configured; services reference each other's data only via logical UUIDs and internal API calls |
| 2 | Inter-service communication must be authenticated; unauthenticated internal calls must be rejected | ✅ | `InternalTokenFilter` verifies `X-Internal-Token` on all `/internal/**` paths; user JWTs are structurally rejected on internal endpoints |
| 3 | A failure in one service must not cascade to prevent authentication or task submission operations | ✅ | Auth and Core are independent; if Payments is unavailable, task submission and review continue unaffected; if Runner is unavailable, task claiming and answer submission continue unaffected |
| 4 | Services must be independently buildable and testable without requiring other services to be running | ✅ | Each service has its own `pom.xml` (or `go.mod`); CI runs per-service jobs in parallel; Testcontainers provides PostgreSQL without requiring other services |
| 5 | The Go-Runner must process dataset files asynchronously without blocking the Core Service API thread pool | ✅ | Core dispatches to Runner with `RestTemplate.exchange()` and expects 202 Accepted; Runner processes and posts callbacks; Core does not block on processing completion |

### Known limitations

Table 2.26. Known limitations — Microservices

| Limitation | Impact | Potential Solution |
|---|---|---|
| No distributed tracing (OpenTelemetry) is configured; debugging a request that spans Gateway → Core → Runner requires manual log correlation by timestamp and request ID | Medium impact during debugging of inter-service failures; the absence of a trace ID in logs makes it difficult to correlate a failed Runner callback with the original Client upload request | Add Micrometer Tracing with OpenTelemetry exporter to all five services; propagate `traceparent` header through `RunnerClient` and `CoreBillingClient`; deploy Jaeger or Zipkin as a trace aggregator |
| The synchronous Payments → Core billing grant call creates a temporal coupling: if Core is temporarily unavailable during a webhook processing window, the payment is recorded as `SUCCEEDED` but the project remains `UNPAID` | Low probability under normal operation; if it occurs, the Client sees a paid state in the Payments portal but an unpaid state in the project dashboard | Implement a reconciliation background job in Payments that retries `CoreBillingClient.grantPaidAccess()` for all `SUCCEEDED` payments where the Core confirmation has not been acknowledged; alternatively, migrate to a Kafka-based event for this specific path |