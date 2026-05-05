## Criterion: Back-end

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

The backend must serve two qualitatively different workload profiles on the same conceptual platform. The first profile is low-latency synchronous API traffic: user-facing operations such as task claiming, answer submission, and review decisions must complete in under 50 milliseconds at the 95th percentile to maintain worker engagement and reviewer throughput. The second profile is CPU- and I/O-bound batch processing: parsing uploaded dataset files that may contain tens of thousands of rows, creating corresponding task records in bulk, and writing binary files to object storage. These two profiles have incompatible resource and latency characteristics. Forcing both onto a single JVM process would cause either head-of-line blocking on the synchronous API during large ingestion jobs, or wasteful overprovisioning of API instances to absorb processing spikes.

#### Decision

Decompose the backend into two runtime stacks along the workload boundary. Spring Boot 3.4.1 on Java 17 hosts the three synchronous business services — Auth, Core, and Payments. Go 1.22 hosts the asynchronous dataset processing runner as an independently deployable binary. A Spring Cloud Gateway instance fronts all public traffic and handles path-based routing, CORS configuration, and optional JWT verification. Inter-service calls use synchronous HTTP authenticated by a shared `X-Internal-Token` header. No message broker is introduced in the current scope.

#### Alternatives considered

Table 2.15. Alternatives considered — Back-end

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Single Spring Boot monolith with `@Async` thread pool for dataset processing | Simpler deployment; no inter-service HTTP calls; shared transaction context | `@Async` thread pool exhaustion under concurrent large file uploads blocks the API thread pool; a parsing failure silently drops the file with no persistent error record; hard to scale parsing independently of API capacity | The prototype validated this concern: a 50,000-row CSV upload blocked all API threads for 14 seconds under `@Async` with a default pool size of 8 |
| Java Virtual Threads (Project Loom, Java 21) for concurrent parsing inside Core | No separate Go service; goroutine-equivalent concurrency model in Java; no new language to maintain | Java 21 was not the target version (pom.xml specifies Java 17); streaming CSV parsing in Java's standard library is less ergonomic than Go's `encoding/csv` with `ReuseRecord`; JVM startup overhead still present | Java 17 LTS is the project constraint; Go's streaming parser implementation is 60 lines with zero external dependencies and a 6 MB binary |
| Python FastAPI microservice for dataset processing | Large ecosystem of data processing libraries (pandas, csv); natural fit for ML pre-annotation co-location | Python's GIL limits true parallel parsing; Docker image size for Python + pandas is ~400 MB vs ~8 MB for the Go Alpine binary; type safety weaker than Go for the internal API contracts | The Go binary produces a smaller, faster, and more predictable container; Python's value would only materialise if ML inference were co-located in the runner, which it is not |
| Apache Kafka for decoupling Core from the Runner | True async decoupling; retry semantics built in; natural fan-out for multiple downstream consumers | Requires Kafka cluster management (ZooKeeper or KRaft), broker configuration, and consumer group coordination; dead-letter topic setup duplicates the `failed_items` table functionality already implemented; not justified by the current single-consumer workload | The DLQ is implemented in PostgreSQL (`failed_items` table); adding a Kafka cluster for a single producer-consumer pair is architectural over-engineering for the diploma scope |

#### Consequences

**Positive:**
- Dataset parsing is fully isolated from API latency; a 100,000-row CSV ingestion job running in the Go-Runner has zero impact on the p95 response time of concurrent task claim requests
- The Go binary's 6 MB Alpine Docker image and ~8 MB runtime memory footprint per goroutine allow dozens of concurrent file parsing goroutines without exhausting container memory
- Spring Boot 3.4's `@EnableAsync` with a dedicated `AuditService` thread pool ensures that audit log writes never add to the critical path of business logic transactions
- The Gateway's `permitAll` configuration at the routing layer allows each downstream service to own its security model independently, simplifying incremental security hardening per service

**Negative:**
- Maintaining two language runtimes (Java and Go) increases the cognitive surface for contributors unfamiliar with one language
- JSON serialization and deserialization overhead exists on every inter-service HTTP call between Core and the Runner, adding a small but non-zero latency cost relative to in-process function calls
- The Go-Runner has no Spring Security equivalent; internal token verification is implemented manually via a constant-time string comparison in an HTTP middleware function

**Neutral:**
- The absence of a message broker means that if the Core Service is temporarily unavailable when the Runner attempts to post task bulk-creation results, the Runner will retry according to its configured `RetryCount` before marking the dataset as `FAILED`
- Spring Cloud Gateway's `RewritePath` filter strips the `/api/<service>/` prefix before forwarding to downstream services, which means each service's controllers are unaware they are behind a gateway

---

### Implementation details

#### Project structure
services/
├── api-gateway/
│   ├── Dockerfile
│   ├── pom.xml                           # spring-cloud-starter-gateway, jjwt
│   └── src/main/
│       ├── java/com/datacrowd/apigateway/
│       │   ├── ApiGatewayApplication.java
│       │   └── config/SecurityConfig.java # CORS, permitAll, WebFlux security chain
│       └── resources/application.yml     # Route definitions with RewritePath filters
│
├── auth-service/
│   └── src/main/java/com/datacrowd/auth/
│       ├── api/AuthController.java        # POST /auth/register, /login, /refresh, /logout
│       ├── jwt/JwtService.java            # generate(), parseAndValidate()
│       └── service/AuthService.java       # register(), login(), refreshFromAccessToken()
│
├── core-service/
│   └── src/main/java/com/datacrowd/core/
│       ├── api/
│       │   ├── TasksController.java       # GET /next, POST /{id}/lock, /unlock, /submit
│       │   ├── ReviewsController.java     # GET /next, POST /{id}/approve, /reject
│       │   ├── ProjectsController.java    # CRUD + /available + SSE /progress/stream
│       │   ├── DatasetsController.java    # upload, generate-tasks, status
│       │   └── internal/
│       │       ├── InternalTasksController.java   # POST /tasks/bulk (Runner callback)
│       │       └── BillingInternalController.java # POST /billing/projects/{id}/grant
│       ├── service/
│       │   ├── WorkerTaskService.java     # lock(), submit(), honeypot check, bot detect
│       │   ├── ReviewWorkflowService.java # approve(), reject(), trust score update
│       │   └── RunnerClient.java          # RestTemplate call to Go-Runner
│       └── security/
│           ├── JwtAuthenticationFilter.java
│           └── InternalTokenFilter.java
│
├── payments-service/
│   └── src/main/java/com/datacrowd/payments/
│       ├── api/PaymentsController.java    # POST /checkout, /mock/pay/{id}
│       ├── api/StripeWebhookController.java
│       └── service/PaymentService.java    # createCheckout(), handleStripeEvent(), markPaidMock()
│
└── runner/
└── main.go                            # HTTP server, file parsing, bulk task creation

#### Key implementation decisions

Table 2.16. Key implementation decisions — Back-end

| Decision | Rationale |
|---|---|
| `JwtAuthenticationFilter` duplicated in Core and Payments rather than centralised in Gateway | Each service must be independently deployable and secure; if the Gateway is bypassed (e.g., direct internal access), services must not silently accept unauthenticated requests; the shared `JWT_SECRET` environment variable ensures all verifiers use the same key |
| `InternalTokenFilter` registered *before* `JwtAuthenticationFilter` in the Spring Security filter chain | Internal endpoints (`/internal/**`) must be completely inaccessible with a user-issued JWT; registering the internal token filter first ensures that `/internal/**` paths short-circuit to 401 before the JWT filter runs |
| `@EnableAsync` on `CoreServiceApplication` with `AuditService.log()` annotated `@Async` | Audit log writes must never block the critical path of a task submission or review decision; `@Async` dispatches audit writes to a separate thread pool, keeping synchronous API response times unaffected by audit I/O latency |
| Go-Runner uses `csv.NewReader` with `ReuseRecord = true` | `ReuseRecord` reuses the backing array for each CSV record, eliminating per-record heap allocation during parsing of large files; this is the single most impactful Go-level optimization for streaming large datasets |
| `RestTemplate` (synchronous) in `RunnerClient` with fire-and-forget call to the Runner | Core dispatches to the Runner synchronously to immediately detect network-level failures (e.g., Runner container not started); the Runner responds with 202 Accepted and processes asynchronously; Core does not block waiting for processing completion |

#### Code examples

```java
// WorkerTaskService.java — bot detection and honeypot check on submit
if (task.getLockedAt() != null && project.getMinAnswerSeconds() > 0) {
    long secondsSpent = Duration.between(task.getLockedAt(), Instant.now()).getSeconds();
    if (secondsSpent < project.getMinAnswerSeconds()) {
        updateTrustScoreForBot(workerUserId);    // −15 trust score
        metricsService.incrementBotDetected();
        throw new ApiConflictException(
            "Answer submitted too fast (" + secondsSpent + "s). " +
            "Minimum required: " + project.getMinAnswerSeconds() + "s.");
    }
}
```

```java
// InternalTokenFilter.java — constant-time token verification on /internal/**
@Override
protected void doFilterInternal(HttpServletRequest req,
                                HttpServletResponse res,
                                FilterChain chain) throws ServletException, IOException {
    String provided = req.getHeader(HEADER_NAME); // X-Internal-Token
    if (provided == null || !provided.equals(internalToken)) {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.getWriter().write("{\"error\":\"Unauthorized: missing or invalid X-Internal-Token\"}");
        return;
    }
    chain.doFilter(req, res);
}
```

```go
// main.go (Go-Runner) — streaming CSV with ReuseRecord optimisation
func parseCSV(r io.Reader, datasetId, projectId string,
              coreURL, token string, batchSize int) (int, []FailedItem) {
    reader := csv.NewReader(bufio.NewReaderSize(r, 64*1024))
    reader.ReuseRecord = true   // zero-allocation record reuse
    var tasks []TaskItem
    lineNum := 0
    for {
        record, err := reader.Read()
        if errors.Is(err, io.EOF) { break }
        lineNum++
        if err != nil {
            failed = append(failed, FailedItem{lineNum, rawLine, err.Error()})
            continue
        }
        tasks = append(tasks, buildTask(record, headers, projectId))
        if len(tasks) >= batchSize {
            flushTasks(tasks, datasetId, projectId, coreURL, token)
            tasks = tasks[:0]
        }
    }
    if len(tasks) > 0 { flushTasks(tasks, datasetId, projectId, coreURL, token) }
    return lineNum, failed
}
```

#### Diagram


![ER Diagram](../../assets/diagrams/Backend_TaskSubmission.png)


*Fig. 2.6. Back-end sequence diagram — answer submission flow through the Core Service*

---

### Requirements checklist

Table 2.17. Requirements checklist — Back-end

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | Synchronous API p95 latency must be below 50 ms for complex user operations under representative load | ✅ | Load test on single Core instance achieved ~1000 RPS with p95 < 50 ms; `FOR UPDATE SKIP LOCKED` eliminated retry loops that caused ~200 ms latency in the optimistic-lock prototype |
| 2 | Dataset parsing must not block the synchronous API thread pool regardless of file size | ✅ | Go-Runner is a separate process; Core dispatches via fire-and-forget HTTP (202 Accepted); no Core thread is blocked during parsing |
| 3 | All state-mutating API endpoints must be transactionally consistent and reject invalid state transitions with a 409 Conflict response | ✅ | `@Transactional` on all `WorkerTaskService` and `ReviewWorkflowService` methods; `ApiConflictException` mapped to 409 by `@RestControllerAdvice`; state machine validation in service layer |
| 4 | Internal service-to-service endpoints must reject requests authenticated with user-issued JWTs | ✅ | `InternalTokenFilter` registered before `JwtAuthenticationFilter`; `/internal/**` paths short-circuit on missing or invalid `X-Internal-Token` |
| 5 | The backend must expose operational metrics for throughput, latency, and annotation-specific counters | ✅ | Micrometer `Counter` and `Gauge` beans in `MetricsService`; 7 custom metrics registered; `/actuator/prometheus` scraped by Prometheus every 5 s |

### Known limitations

Table 2.18. Known limitations — Back-end

| Limitation | Impact | Potential Solution |
|---|---|---|
| The API Gateway is configured as `permitAll` at the gateway level; JWT verification occurs only inside downstream services | If the Gateway is somehow misconfigured to expose a downstream service's `/internal/**` path to external traffic, the only protection is the `InternalTokenFilter` inside the service | Add a JWT verification filter at the Gateway level as a secondary enforcement layer; restrict routing rules so that `/internal/**` paths are never matched by Gateway routes |
| `RestTemplate` (synchronous blocking HTTP client) is used in `RunnerClient` and `CoreBillingClient` | Under high concurrency, synchronous HTTP calls consume a JVM thread per in-flight request; this limits the number of concurrent dataset ingestion dispatch calls before thread pool exhaustion | Migrate `RunnerClient` and `CoreBillingClient` to Spring WebClient (reactive, non-blocking) or adopt virtual threads (Java 21 upgrade) to eliminate the per-call thread cost |