## 1.4 Features and Requirements

Table 1.15. Epics Overview

| Epic | Description | Stories Count | Status |
|---|---|---|---|
| E1: Project and Dataset Management | Create and configure annotation projects; upload CSV, JSONL, and ZIP datasets; monitor ingestion status via SSE | 6 | Implemented |
| E2: Task Generation and Distribution | Asynchronous dataset splitting by Go-Runner; atomic task assignment via SKIP LOCKED; DLQ for parse failures | 5 | Implemented |
| E3: Answer Submission and Review | Structured answer submission with time-tracking; queue-based reviewer workflow; N-of-M consensus aggregation | 7 | Implemented |
| E4: Trust and Anti-Fraud | Dynamic Trust Score; honeypot task injection; bot detection via minAnswerSeconds; automatic task-access gating | 5 | Implemented |
| E5: Billing | Mock and real Stripe checkout; ledger accounting; billing grant from Payments to Core via internal API | 4 | Implemented |
| E6: Dataset Export | JSONL and typed CSV export of verified answers; per-dataType column schemas; authenticated download endpoint | 3 | Implemented |
| E7: ML Pre-Annotation | Optional HuggingFace zero-shot classification; aiSuggestedLabel and aiConfidence written to task payload | 3 | Implemented |
| E8: Observability and Audit | Prometheus Micrometer metrics; Grafana dashboards; immutable audit_logs table; Caffeine caching | 4 | Implemented |

---

### User Stories

Table 1.16. User Stories — Epics E1, E3, E4

| ID | User Story | Acceptance Criteria | Priority | Status |
|---|---|---|---|---|
| E1-US-01 | As a **Client**, I want to create an annotation project with configurable reviewers count and reward points, so that I can define the quality and cost parameters of my labeling campaign before uploading data | Project is persisted with status=NEW and billingStatus=UNPAID; all parameters (reviewersCount, rewardPoints, minAnswerSeconds, dataType) are stored and retrievable via GET /api/core/projects/{id} | Must Have | Done |
| E1-US-02 | As a **Client**, I want to upload a CSV or JSONL dataset file to my project, so that the platform can automatically split it into atomic labeling tasks | File is stored in MinIO or local volume; Go-Runner is triggered asynchronously; dataset status transitions UPLOADED → GENERATING → READY; total_items reflects the parsed row count; failed rows appear in failed_items | Must Have | Done |
| E1-US-03 | As a **Client**, I want to monitor the real-time progress of task completion on my project dashboard, so that I can estimate when the verified dataset will be ready for export | SSE stream at /api/core/projects/{id}/progress/stream emits {completed, total, progress, status} every 2 seconds; stream closes automatically when progress reaches 100% | Should Have | Done |
| E3-US-01 | As a **Worker**, I want to claim the next available task from the queue, so that I can start labeling without coordinating with other workers | GET /api/core/tasks/next returns a unique task not held by any other worker; two concurrent calls to this endpoint never return the same task ID; 403 is returned if Trust Score < 30 | Must Have | Done |
| E3-US-02 | As a **Worker**, I want to submit my answer after reading the task for at least the minimum required time, so that the system accepts my contribution and credits my points | POST /api/core/tasks/{id}/submit succeeds if time since locked_at ≥ minAnswerSeconds; returns 409 with trust penalty if submitted too fast; answer is persisted with status=SUBMITTED; task transitions to IN_REVIEW when reviewersCount > 0 | Must Have | Done |
| E3-US-03 | As a **Reviewer**, I want to load the next pending answer from the review queue and see both the original task content and the worker's submission side by side, so that I can make an informed approve/reject decision | GET /api/core/reviews/next returns taskPayloadJson and answerContent together; reviewer cannot load an answer they previously reviewed; reviewer cannot review their own answer (403) | Must Have | Done |
| E4-US-01 | As a **Platform Administrator**, I want the system to automatically reduce a worker's Trust Score when they submit answers too quickly or fail honeypot tasks, so that low-quality contributors are progressively gated from the annotation queue without manual intervention | Trust Score decreases by 15 on bot detection (too-fast submission); decreases by 20 on honeypot failure; decreases by 10 on reviewer rejection; worker with score < 30 receives 403 on GET /api/core/tasks/next with message "trust score too low" | Must Have | Done |
| E4-US-02 | As a **Client**, I want to embed hidden honeypot tasks with known correct answers into my dataset, so that I can automatically detect workers who are clicking randomly or using bots | Tasks with isHoneypot=true and expectedAnswer in payloadJson are checked at submit time; incorrect honeypot answers trigger Trust Score penalty of 20, task returns to NEW, and a HONEYPOT_FAILED audit log entry is created | Must Have | Done |
| E4-US-03 | As a **Worker**, I want to view my current Trust Score, accuracy rate, and total points on a personal performance dashboard, so that I can understand my standing and improve my labeling behavior | GET /api/core/workers/me/stats returns trustScore (0–100), trustLevel (HIGH/MEDIUM/LOW/BLOCKED), completedTasks, rejectedTasks, totalPoints; values are cached for 60 s and invalidated on every review verdict | Should Have | Done |

---

### Use Case Diagram


![ER Diagram](../assets/diagrams/DataCrowdLab_UseCases.png)



---

### Non-Functional Requirements

Table 1.17. Performance Requirements

| Requirement | Metric | Target Value | Measurement Method |
|---|---|---|---|
| Synchronous API response time | p95 latency for complex user actions (task claim, answer submit, review decision) | < 50 ms | Load test with 100 concurrent virtual users via k6 or Apache JMeter |
| System throughput | Sustained requests per second on a single Core service instance | ~ 1000 RPS | Load test measuring requests/s at saturation point |
| Dataset ingestion throughput | Time to parse and create tasks from a 10,000-row CSV file | < 90 seconds | Timed integration test via Go-Runner |
| Export generation time | Time to generate and write a 10,000-answer JSONL export | < 60 seconds | Timed integration test via ExportService |
| Cache hit ratio on high-read endpoints | Percentage of worker stats and available projects requests served from Caffeine cache | > 85% under normal load | Micrometer cache hit/miss counters exposed via Prometheus |

Table 1.18. Reliability Requirements

| Requirement | Target | Implementation Mechanism |
|---|---|---|
| No duplicate task assignments under concurrent load | 0 duplicates with 100+ concurrent workers | PostgreSQL FOR UPDATE SKIP LOCKED in lockIfAvailable JPA query |
| No data loss on dataset ingestion failure | 100% of unparseable rows preserved for re-processing | Dead Letter Queue (failed_items table) populated by Go-Runner on parse error |
| Idempotent point awards | A worker receives points for a given task at most once | existsByUserIdAndTaskIdAndReason check in PointsService.awardTaskApprovedOnce() |
| Idempotent payment processing | A project is funded at most once per Stripe session | UNIQUE constraint on stripe_session_id in payments table; SUCCEEDED status check before any write |
| Service startup resilience | Services refuse to start with missing or invalid configuration rather than failing silently at runtime | JWT_SECRET length validation in JwtService constructor; Flyway migration failure causes startup abort |

Table 1.19. Compatibility Requirements

| Layer | Requirement | Specification |
|---|---|---|
| Runtime — Java services | JRE version | Eclipse Temurin 21 (LTS) in Docker runtime image |
| Runtime — Go Runner | Go version | 1.22 (Alpine runtime image) |
| Database | PostgreSQL version | 16 (required for FOR UPDATE SKIP LOCKED stability and JSONB support) |
| Container runtime | Docker Engine and Compose | Docker Engine 24.0+; Docker Compose v2.20+ |
| Browser (frontend) | Web standards | ES2020+; tested on Chrome 120+, Firefox 121+, Safari 17+ |
| API contract | HTTP protocol | REST over HTTP/1.1; JSON request and response bodies; RFC 7807 Problem Details for error responses |
| Dataset input formats | File formats accepted by Go-Runner | UTF-8 encoded .csv (with header row), .jsonl (one JSON object per line), .zip (containing manifest.jsonl + asset files) |
| Dataset output formats | Export formats produced by ExportService | .jsonl (one JSON object per verified answer); .csv with dataType-specific column schemas |

#### Security Requirements

- All public API endpoints (except `/auth/register`, `/auth/login`, and actuator health) require a valid HS256 JWT Bearer token; token signature is verified in a `OncePerRequestFilter` in each downstream service
- Internal endpoints (`/internal/**`) require the `X-Internal-Token` header; this header is verified before the JWT filter in the Spring Security filter chain; user-issued JWTs are not accepted on internal paths
- Passwords are stored exclusively as BCrypt hashes; plaintext passwords are never logged, persisted, or returned in API responses
- The `v_users_masked` database view exposes only masked email addresses (first two characters + `****@domain`) to internal diagnostic tooling
- Role promotion to CLIENT, REVIEWER, or ADMIN requires the `X-Admin-Key` header at registration or a privileged internal API call; self-promotion is not possible
- All CORS origins are permitted in the API Gateway's `SecurityConfig` with `allowedOriginPatterns("*")` for the diploma demonstration environment; this must be restricted to known domains before production deployment
- JWT secret and internal token values are validated at service startup to have a minimum length of 32 characters; startup is aborted if this constraint is not met

#### Accessibility Requirements

- The frontend implements semantic HTML5 elements (nav, button, label, input) to support screen reader navigation
- All interactive controls have explicit `label` elements or `aria-label` attributes
- Color is not used as the sole indicator of status (Trust Score levels are indicated by both color and text label: HIGH, MEDIUM, LOW, BLOCKED)
- Minimum touch target size for mobile-compatible interaction is 44×44 CSS pixels
- The application passes WCAG 2.1 Level A contrast requirements for body text and interactive elements