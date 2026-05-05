# 4. RETROSPECTIVE

## 4.1 What went well

### Technical successes

- **Database-level concurrency eliminated an entire bug class.** Replacing optimistic locking (30%+ retry rate under 50 concurrent workers, ~200 ms task claim latency) with PostgreSQL `FOR UPDATE SKIP LOCKED` reduced the task claim latency to under 10 ms and the duplicate assignment rate to exactly zero. This single architectural decision had the largest positive impact on system correctness of any change made during the project.
- **The state machine model produced consistent behavior under adversarial testing.** Modeling every domain entity (task, answer, review, payment) as a strict finite-state machine with application-layer transition validation converted timing bugs and race conditions into explicit, testable 409 Conflict responses. Every invalid transition discovered during testing produced a clear error message rather than silent data corruption.
- **Testcontainers revealed real bugs that mocked tests could not.** Three defects were discovered exclusively through Testcontainers integration tests: a Flyway migration whose SQL syntax was valid in H2 but invalid in PostgreSQL 16, a JSONB query whose operator did not match the available index, and a transactional boundary that committed a partial state update. Each would have been a production incident.
- **The dual-runtime architecture (Java + Go) delivered measurable performance benefits.** The Go-Runner's streaming CSV parser with `ReuseRecord=true` processed a 50,000-row dataset in 18 seconds with constant ~12 MB memory usage. The equivalent Java `@Async` prototype consumed 340 MB of heap and took 47 seconds for the same file while blocking API threads.
- **Caffeine caching reduced database load on high-read endpoints.** After adding Caffeine caches for worker stats, available projects, and project public info, the number of aggregate SQL queries per worker page load dropped from 3 round-trips to near zero (cache hit) with cache eviction correctly wired to review verdict events.
- **The CI pipeline caught regressions before merging.** The JaCoCo gate in GitHub Actions blocked two PRs that added new service methods without corresponding test coverage. Both cases were fixed within the same PR cycle rather than accumulating as test debt.

### Process successes

- **Incremental schema evolution via Flyway prevented schema debt accumulation.** Every feature addition was accompanied by a versioned migration file committed in the same PR as the code requiring it. At no point did the entity definitions diverge from the database schema. The discipline of writing a migration file before writing the service code forced upfront schema design thinking.
- **The ADR (Architecture Decision Record) format prevented revisiting settled decisions.** Documenting the rationale and alternatives for each major architectural choice (SKIP LOCKED, Go for the Runner, no message broker) made it possible to refer back to the reasoning during later development without relitigating decisions from memory.
- **README-driven development kept the deployment model honest.** Maintaining the `README.md` `How to Run` section as a living document and testing it on a clean machine after every significant change caught three configuration documentation errors that would have made the evaluation setup fail.

### Personal achievements

- Designed and implemented a production-grade microservices architecture as a solo developer, including JWT authentication, role-based access control, database-per-service isolation, and inter-service token-based security.
- Mastered PostgreSQL concurrency primitives (`FOR UPDATE SKIP LOCKED`, `UNIQUE NULL` partial indexes, trigger functions, masked views) to a level sufficient to apply them to real correctness problems rather than as academic exercises.
- Delivered a complete, working demonstration of ML-assisted annotation (HuggingFace Inference API integration) within the scope of a bachelor project, including graceful degradation when the API token is absent.
- Developed proficiency in Go sufficient to implement a production-quality streaming file processing service with zero external dependencies, constant-memory operation, and correct error handling for malformed input rows.

---

## 4.2 What didn't go as planned

Table 4.1. What didn't go as planned

| Planned | Actual Outcome | Cause | Impact |
|---|---|---|---|
| Dataset parsing would run inside the Core Service using Spring's `@Async` thread pool | A separate Go microservice was required; the architecture was redesigned mid-project | The `@Async` prototype caused JVM thread pool exhaustion and API Gateway timeouts on files > 5,000 rows; the problem was not anticipated during initial design | 5 days of additional development time; forced a re-evaluation of the architecture; ultimately produced a superior solution |
| A single PostgreSQL database with schema-per-service prefixes would be used for simplicity | Three separate logical databases with dedicated users were implemented | Early prototype showed that schema-prefix isolation was easy to accidentally break; a migration for Core could be applied to the Auth schema by mistake | Additional Flyway configuration per service; slightly more complex Docker init scripts; stronger isolation guarantees as a result |
| `@Version`-based optimistic locking would handle concurrent task assignment | PostgreSQL `FOR UPDATE SKIP LOCKED` replaced optimistic locking entirely | Load testing at 50 concurrent workers revealed a 30%+ retry rate with optimistic locking, causing 200 ms+ task claim latency | 3 days of refactoring; required rewriting `WorkerTaskService.lock()` and adding a `@Modifying` JPQL query; performance improved by 20× |
| The frontend would be a React SPA with a shared component library | Three separate static HTML pages with a shared CSS file were used | Setting up a React build toolchain (Vite/Webpack) would have shifted CI effort away from backend quality; the diploma timeline did not support two separate build pipelines | Code duplication in JavaScript utility functions across pages; stronger role isolation guarantees; zero build toolchain dependency |
| Automated E2E tests would be implemented with Playwright | E2E testing is documented as a manual Postman collection procedure | Frontend test automation requires stable locators for dynamic content; implementing Playwright within the diploma timeline would have required reducing backend test coverage | The E2E layer is documented but not automated; the gap is acknowledged as a known limitation in the testing pyramid |
| Stripe live integration would be demonstrated | Mock payment flow (`/api/payments/mock/pay/{id}`) is used for the diploma demonstration | Live Stripe webhooks require a publicly accessible URL; the diploma environment runs on localhost without a tunnel | The complete data model and code path for real Stripe is implemented; only the credentials and webhook URL are missing |

---

## 4.3 Challenges encountered

**Challenge 1: Asynchronous file processing without a message broker**

*Problem:* The first design parsed datasets inside the Core HTTP request handler. For a 10,000-row CSV file, the request took 14 seconds and caused API Gateway timeouts. Moving parsing to a Spring `@Async` thread pool improved API response time but caused thread pool exhaustion under concurrent uploads.

*Impact:* Three architecture design iterations were required before settling on the Go-Runner approach. The problem was fundamentally that CPU-bound and IO-bound work was mixed with latency-sensitive API work in a single process.

*Resolution:* A standalone Go 1.22 service was introduced as a separate microservice. The Core Service dispatches to the Runner with a fire-and-forget HTTP call and receives results via callbacks. The Runner's streaming parser operates at constant memory with `ReuseRecord=true`. The `failed_items` Dead Letter Queue captures individual row parse failures without interrupting the processing of remaining rows.

---

**Challenge 2: Cross-service consistency without distributed transactions**

*Problem:* Funding a project requires an atomic update across two databases: the `payments_db` payment record must be set to `SUCCEEDED`, and the `core_db` project record must be set to `PAID`. Without two-phase commit, a failure between these two writes leaves the system in an inconsistent state.

*Impact:* During development, a network interruption test produced a payment recorded as `SUCCEEDED` in `payments_db` while the project remained `UNPAID` in `core_db`. The Client would see a billing discrepancy.

*Resolution:* The idempotent billing grant pattern was implemented: the Payments Service records `SUCCEEDED` and calls `CoreBillingClient.grantPaidAccess()` synchronously; if the call fails, the exception propagates and the payment status rolls back in the Payments transaction. The mock payment endpoint additionally checks `status == SUCCEEDED` before any write, preventing double-billing on retry.

---

**Challenge 3: Trust Score calibration under adversarial testing**

*Problem:* Initial Trust Score penalty values (−5 per rejection, −10 per bot detection) were too lenient; simulated adversarial workers could submit 20 random answers before being blocked, polluting the dataset with unverified annotations. Values that were too harsh (−30 per rejection) caused legitimate workers with domain expertise gaps to be permanently blocked after a small number of honest mistakes.

*Impact:* Three calibration iterations were required: the final values (−10 per rejection, −15 per bot detection, −20 per honeypot failure, +2 per approval, block threshold 30) were arrived at empirically through simulation rather than formal analysis.

*Resolution:* The final calibration was documented in `WorkerTaskService` and `ReviewWorkflowService` as named constants (`TRUST_SCORE_PENALTY`, `TRUST_SCORE_REWARD`, `TRUST_SCORE_BLOCK`) rather than magic numbers. The Trust Score algorithm is acknowledged as a pragmatic approximation rather than an optimal solution; formal optimization is deferred to Phase 2.

---

**Challenge 4: ZIP dataset support for IMAGE and AUDIO tasks**

*Problem:* Supporting ZIP archives containing binary assets (images, audio clips) required implementing ZIP extraction with path traversal (zip-slip) protection, locating the `manifest.jsonl` file in multiple possible locations within the archive, and serving binary assets via authenticated HTTP Range requests for streaming audio in the browser.

*Impact:* ZIP support added approximately 8 days of development effort — significantly more than estimated. The zip-slip vulnerability was identified during a security review of the extraction code and required a `safeResolve()` wrapper around all path operations.

*Resolution:* The `StorageService.extractDatasetZipAndFindManifest()` method was implemented with explicit path traversal protection (`resolved.startsWith(rootDir)` check). The `TasksController.getAsset()` endpoint supports HTTP Range requests via `ResourceRegion` for streaming audio/video without loading the entire file into memory.

---

## 4.4 Technical debt and known issues

Table 4.2. Technical debt and known issues

| ID | Issue | Severity | Description | Potential Fix |
|---|---|---|---|---|
| TD-01 | JWT stored in `localStorage` (XSS vulnerability) | High | The frontend stores JWT tokens in `localStorage`, making them accessible to any JavaScript running on the page; an XSS attack via injected script in task payload content could steal the token | Migrate to `HttpOnly` SameSite=Strict cookies for JWT storage; implement Content Security Policy headers in `nginx.conf` |
| TD-02 | No distributed tracing (OpenTelemetry) | Medium | Debugging requests that span Gateway → Core → Runner requires manual log correlation by timestamp; there is no trace ID propagated across service boundaries | Add Micrometer Tracing with OTLP exporter to all services; deploy Jaeger or Zipkin; propagate `traceparent` header through `RunnerClient` and `CoreBillingClient` |
| TD-03 | Go-Runner has no automated tests | Medium | The file parsing logic (CSV, JSONL, ZIP) is tested only through manual uploads during development; regressions in parsing are not caught by CI | Add Go table-driven unit tests for `parseCSV`, `parseJSONL`, `extractZip`; add an `httptest`-based integration test for the bulk task creation callback |
| TD-04 | `RestTemplate` synchronous HTTP in `RunnerClient` | Medium | Each concurrent dataset ingestion dispatch consumes a JVM thread for the duration of the HTTP call; under high concurrency, this may exhaust the Core Service thread pool | Migrate to Spring `WebClient` (reactive) or upgrade to Java 21 virtual threads; alternatively, implement a simple connection pool with a fixed-size executor |
| TD-05 | Payments → Core temporal coupling | Medium | If the Core Service is temporarily unavailable during Stripe webhook processing, the payment is recorded as `SUCCEEDED` but the project remains `UNPAID`; there is no retry mechanism | Implement a scheduled reconciliation job in the Payments Service that retries `grantPaidAccess()` for all `SUCCEEDED` payments where Core acknowledgement is missing |
| TD-06 | No automatic JWT refresh in the frontend | Low | Workers performing long annotation sessions are silently logged out when the 60-minute token TTL expires; the next API call returns 401 and the page must be manually refreshed | Implement a background token refresh using `POST /api/auth/refresh` at TTL − 5 minutes; store the expiry timestamp in `localStorage` alongside the token |
| TD-07 | Two Docker bridge networks (`datacrowd-net` and `datacrowd-network`) | Low | The frontend container is on `datacrowd-network` while all backend services are on `datacrowd-net`; the frontend communicates via published host ports rather than the container network | Consolidate all containers onto a single bridge network; remove the `datacrowd-network` definition |
| TD-08 | `v_users_masked` view not yet used by any API endpoint | Low | The masked email view was created in `V4__views_triggers_masking.sql` but is not queried by any current endpoint; it provides no active privacy protection | Use `v_users_masked` in admin-facing user listing endpoints; remove the raw `email` field from any diagnostic query that currently accesses `auth_db.users` directly |

---

## 4.5 Future improvements (backlog)

### High priority

| Description | Value | Effort |
|---|---|---|
| Kubernetes migration with Helm charts | Enables horizontal scaling, rolling updates, and production-grade secrets management (Vault/Secrets Manager) | High (2–4 weeks) |
| OpenTelemetry distributed tracing | Reduces mean time to diagnose inter-service failures from hours to minutes; essential for production operations | Medium (1 week) |
| Go-Runner automated test suite | Closes the largest gap in the current testing pyramid; prevents silent regressions in dataset parsing logic | Medium (3–5 days) |
| JWT migration to `HttpOnly` cookies | Eliminates the primary XSS attack vector in the current frontend authentication model | Medium (3–5 days) |
| Automatic honeypot generation from verified answers | Eliminates the requirement for Clients to manually mark honeypot rows; increases anti-fraud coverage automatically | High (1–2 weeks) |

### Medium priority

| Description | Value | Effort |
|---|---|---|
| Federated identity (OAuth2/OIDC via Keycloak) | Enables enterprise SSO integration; removes password management from the platform | High (2–3 weeks) |
| Kafka-based event bus for Payments → Core billing events | Eliminates the temporal coupling between Payments and Core; enables future billing event consumers | Medium (1–2 weeks) |
| Playwright E2E test suite for the three critical user flows | Closes the E2E layer of the testing pyramid; enables UI regression detection in CI | Medium (1 week) |
| Worker reputation recovery mechanism | Allows legitimately blocked workers to restore access through a probationary period | Low (3–5 days) |
| PgBouncer connection pooler | Mitigates PostgreSQL `max_connections` bottleneck when running multiple Core Service replicas | Low (1–2 days) |

### Nice to have

- Real-time WebSocket push notifications to Workers when a new task becomes available in a project they are working in
- Native iOS and Android mobile applications for Workers performing image or audio labeling tasks on mobile devices
- Bounding-box annotation editor for IMAGE tasks with visual polygon and rectangle drawing tools
- Multi-region data residency with configurable PostgreSQL read replicas for geographically distributed annotation teams
- Annotation quality analytics dashboard for Clients showing per-worker accuracy rates, label distribution histograms, and inter-annotator agreement scores

---

## 4.6 Lessons learned

Table 4.3. Technical lessons

| Lesson | Context | Application |
|---|---|---|
| Database-level concurrency primitives outperform application-level alternatives for queue semantics | The transition from `@Version` optimistic locking to `FOR UPDATE SKIP LOCKED` reduced task claim latency by 20× and eliminated all duplicate assignments | When designing any concurrent queue-style workload, default to the database's native locking semantics; reach for application-level retry logic only when the database primitive is not available |
| Technology choice must be driven by workload characteristics, not language familiarity | Go was chosen for the Runner because streaming file parsing with constant memory and goroutine-based concurrency was a better fit than JVM-based `@Async`; this required learning Go during the project | Before choosing a language or framework, characterize the workload (latency-sensitive vs. throughput-sensitive, memory profile, concurrency model); match the runtime to the workload rather than the other way around |
| Invest in test infrastructure in the first week, not the last | Testcontainers discovered three defects that would have been production incidents; the setup cost was two development days | Test infrastructure setup should be the first technical task on a new service, not a retrospective addition; the Testcontainers base class can be written once and reused across all integration tests in the service |
| Architectural discipline is an enabling force, not a constraint | Every shortcut taken (ad-hoc cross-service DB query, hardcoded credential, skipped state validation) produced a bug within days; disciplined code required the least ongoing maintenance | Document architecture decisions in ADRs at the time the decision is made; refer back to them when tempted to take shortcuts; the discipline of justifying an exception forces the evaluation of whether the exception is truly necessary |
| A finite state machine model is worth the upfront design cost | Modeling task, answer, and payment lifecycle as state machines with explicit transition validation eliminated a category of timing and concurrency bugs | For any domain entity that changes status over time with business rules governing transitions, model it as a formal state machine before writing any service code |

---

Table 4.4. Process lessons

| Lesson | Context | Application |
|---|---|---|
| README-driven deployment validation prevents evaluation failures | Testing the `docker compose up --build` flow on a clean machine after every significant change caught three documentation errors | Treat deployment documentation as a test: execute it verbatim on a clean environment at least once per sprint; automate it in CI where possible |
| Incremental database migration prevents schema debt | Writing Flyway migration files in the same PR as the code that requires the schema change enforced schema design discipline and prevented accumulation of ad-hoc `ALTER TABLE` statements | Never merge a feature branch that requires a schema change without a corresponding migration file; treat migration files as first-class code artifacts |
| Architecture Decision Records prevent decision thrash | Documenting the rationale for SKIP LOCKED, Go for the Runner, and no message broker prevented those decisions from being revisited repeatedly during later development | Write a brief ADR (context, decision, alternatives, consequences) for every decision that would take more than 30 minutes to reverse; store ADRs in the repository next to the code they govern |
| Solo development on a microservices project requires stricter discipline than team development | In a team, informal knowledge sharing compensates for underdocumented interfaces; as a solo developer, every undocumented interface is a future debugging session | Document internal API contracts explicitly (OpenAPI for HTTP, inline comments for Go); maintain a architecture diagram that is updated with every structural change |

---

Table 4.5. What would be done differently

| Area | Current Approach | What Would Change | Why |
|---|---|---|---|
| Frontend architecture | Three separate static HTML pages with shared CSS; `localStorage` JWT storage | React or Next.js SPA with `HttpOnly` cookie authentication; shared component library | Component reuse would eliminate JavaScript duplication; `HttpOnly` cookies eliminate the XSS attack vector from day one; React DevTools improve debugging velocity |
| Testing strategy | Unit tests and Testcontainers integration tests added incrementally as features were built | Write all Testcontainers base classes and JaCoCo configuration in the first PR of each service, before any business logic | Test infrastructure retrofitted after the fact is incomplete; front-loading it ensures all business logic is written in a testable style from the start |
| Inter-service communication | Synchronous HTTP for all service-to-service calls including Payments → Core billing grant | Use an event-driven approach (Kafka or a lightweight alternative like Redpanda) for the billing grant event from the beginning | The synchronous Payments → Core coupling is the most fragile integration point; an event bus would have required the same implementation effort but produced a more resilient system |
| Go-Runner design | Single `main.go` with all parsing logic, HTTP server, and Core API client | Separate packages: `cmd/runner` (main), `internal/parser` (CSV/JSONL/ZIP), `internal/client` (Core API), `internal/config` | The monolithic `main.go` is hard to unit test; package separation would have enabled isolated unit tests for each parsing function from day one |
| Trust Score algorithm | Empirically calibrated fixed penalty and reward values | Implement a configurable penalty/reward table as a project-level parameter; allow Clients to define their own quality thresholds | Different annotation domains have different quality tolerance levels; a hard-coded global Trust Score algorithm is too rigid for a multi-tenant platform |

---

Table 4.6. Skills developed

| Skill | Before Project | After Project |
|---|---|---|
| Spring Boot microservices architecture | Able to build simple CRUD REST APIs with Spring Boot; no experience with multi-service authentication or inter-service security | Designed and implemented a 4-service Spring Boot architecture with shared JWT verification, internal token security, Flyway migrations, and Caffeine caching across services |
| PostgreSQL advanced features | Familiar with basic SQL (SELECT, JOIN, INDEX); no experience with concurrency primitives or PostgreSQL-specific features | Proficient with `FOR UPDATE SKIP LOCKED`, `UNIQUE NULL` partial indexes, trigger functions (`set_updated_at`), masked views (`v_users_masked`), and JSONB column type |
| Go programming language | Zero prior experience; familiar with Go syntax from documentation | Implemented a production-quality Go microservice with streaming CSV/JSONL/ZIP parsing, HTTP server, environment-based configuration, and constant-memory batch processing |
| Docker and Docker Compose | Able to run pre-built Docker images; no experience writing multi-stage Dockerfiles or composing multi-service environments | Authored multi-stage Dockerfiles for 5 services (Java + Go); designed a 11-container Compose topology with health checks, named volumes, network isolation, and init script provisioning |
| Test-driven development with Testcontainers | Used JUnit 5 for unit tests; no experience with integration testing against real databases | Implemented `@SpringBootTest` + Testcontainers integration tests; configured `@DynamicPropertySource` for ephemeral container URL injection; set up JaCoCo with a CI quality gate |
| Stripe payments integration | No prior experience with payment APIs | Integrated Stripe Java SDK 28.0.0; implemented Stripe Checkout session creation, webhook signature verification, and a mock payment fallback; designed the idempotent payment recording pattern |
| HuggingFace Inference API | Familiar with ML concepts; no experience with inference APIs | Implemented zero-shot classification pre-annotation via HuggingFace Inference API; designed graceful degradation when the API token is absent; stored pre-annotation results as fields in task payloads |
| GitHub Actions CI/CD | Used manual build scripts; no CI/CD pipeline experience | Designed and implemented a parallel 6-job GitHub Actions pipeline with per-service PostgreSQL service containers, Maven and Go module caching, JaCoCo gate enforcement, and conditional Docker build steps |

---

## 4.7 Key takeaways

1. **Correctness requires database-level primitives, not application-level workarounds.** The most impactful architectural decision in the entire project was replacing optimistic locking with `FOR UPDATE SKIP LOCKED`. This is a generalizable principle: for any concurrent shared-resource problem, always investigate what the database natively provides before building an application-layer coordination mechanism. The database's primitive will be faster, simpler, and correct by construction.

2. **Architectural discipline compounds over time.** The parts of the system built with strict adherence to the principles (state machines, database-per-service, test pyramid, Flyway migrations) required progressively less maintenance as the project grew. The parts built with shortcuts (localStorage JWT, single main.go, no Go tests) accumulated debugging debt that consumed disproportionate time later. The lesson is not that shortcuts are always wrong, but that their cost must be explicitly acknowledged and scheduled for resolution.

3. **Technology choice is a workload decision, not a preference decision.** The choice of Go for the Runner was initially resisted because it added a second language runtime to maintain. In retrospect, it was the correct decision because the workload — streaming file parsing with constant memory and goroutine-based concurrency — maps directly to Go's strengths and poorly to the JVM's. Letting the workload select the technology produces components that are each excellent at their specific job; forcing a single technology to do everything produces components that are mediocre at most jobs.