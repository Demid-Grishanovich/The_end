# 5. APPENDICES

## 5.1 Glossary and Acronyms

Table 5.1. Glossary of key domain and platform terms

| Term | Definition |
|---|---|
| Human-in-the-Loop (HITL) | An architectural pattern where a machine learning system delegates decisions to human reviewers, either to bootstrap training data or to handle low-confidence predictions in a deployed model. |
| Trust Score | A numerical reputation value (0–100) maintained per worker. Increases by +2 on answer approval; decreases by −10 on reviewer rejection, −15 on too-fast submission (bot detection), and −20 on honeypot failure. Workers below threshold 30 are automatically BLOCKED and cannot claim new tasks. |
| Honeypot | A task with a known correct answer, indistinguishable to the worker from a real labeling task. Used to detect low-effort annotators and automated bots. A failed honeypot decreases the worker's Trust Score by 20 and the task is returned to NEW status. |
| Dead Letter Queue (DLQ) | The `failed_items` table in `core_db`. Stores dataset rows that the Go-Runner could not parse, recording line number, raw content, and error message for later inspection and reprocessing. |
| Consensus Mechanism | The rule that an answer reaches APPROVED status only after N independent reviewer approvals, where N equals the project-level `reviewersCount` parameter. Prevents single-reviewer bias and enforces inter-annotator agreement. |
| State Machine | A formal model in which an entity exists in exactly one state at a time, with restricted valid transitions. Applied to tasks (NEW → LOCKED → IN_REVIEW → APPROVED), answers (SUBMITTED → APPROVED \| REJECTED), datasets (UPLOADED → GENERATING → READY \| FAILED), and payments (PENDING → SUCCEEDED \| FAILED). |
| Pre-annotation | Populating task payloads with `aiSuggestedLabel` and `aiConfidence` from the HuggingFace Inference API before a worker views the task. Reduces cognitive load for validation-oriented tasks and improves throughput on well-defined classification workloads. |
| FOR UPDATE SKIP LOCKED | PostgreSQL query clause that acquires a row-level write lock and silently skips rows already locked by another concurrent transaction. Used in `lockIfAvailable` and `findPendingForReview` to guarantee contention-free, fair task distribution without application-layer retry logic. |
| Internal Token | A shared secret transmitted in the `X-Internal-Token` header for service-to-service calls (Core → Runner, Payments → Core). Never accepted on public-facing endpoints; verified by `InternalTokenFilter` ahead of `JwtAuthenticationFilter` in the Spring Security filter chain. |
| minAnswerSeconds | Project-level configuration parameter (default: 3 s). If a worker submits an answer faster than this value relative to `locked_at`, the system classifies the submission as bot behavior, decreases Trust Score by 15, and returns HTTP 409 Conflict. |
| Export | A materialised snapshot of all APPROVED answers for a project, serialised to JSONL or typed CSV (column schema dependent on `dataType`) and stored in the local filesystem or MinIO for client download. |
| Billing Grant | The internal operation (POST /internal/billing/projects/{id}/grant) by which Payments Service notifies Core Service that a payment has succeeded, triggering billingStatus → PAID and incrementing the project's `taskQuota`. |

Table 5.2. Acronyms

| Acronym | Full Form | Description |
|---|---|---|
| JWT | JSON Web Token | Stateless, self-contained authentication token (RFC 7519). Used with HS256 signing in all DataCrowd Lab services. |
| API | Application Programming Interface | Defined contract for communication between software components. All inter-service and client-server communication in the platform uses REST APIs over HTTP/HTTPS. |
| MVCC | Multi-Version Concurrency Control | PostgreSQL's concurrency mechanism that maintains multiple versions of rows to allow readers and writers to proceed without blocking each other. Underpins the correctness of SKIP LOCKED semantics. |
| JSONB | JSON Binary | PostgreSQL's binary-encoded JSON column type that supports efficient indexing and querying of semi-structured data. Used for task `payload_json` and answer `content` storage. |
| CI/CD | Continuous Integration / Continuous Delivery | Automated pipeline (GitHub Actions) that compiles, tests, and gates every commit. Enforces JaCoCo coverage ≥60 % across all four Spring Boot services. |
| RBAC | Role-Based Access Control | Authorization model where permissions are assigned to roles (CLIENT, WORKER, REVIEWER, ADMIN), not to individual users. Enforced at three layers: JWT claim, API Gateway routing, and database `users.role` column. |
| SSE | Server-Sent Events | HTTP-based unidirectional streaming protocol (W3C EventSource). Used in DataCrowd Lab for real-time project progress push from Core Service to the frontend. |
| DLQ | Dead Letter Queue | Pattern for isolating unprocessable messages or records for later inspection. Implemented as the `failed_items` table in `core_db`. |
| ORM | Object-Relational Mapping | Technique for converting between relational database rows and object-oriented language types. Implemented via Spring Data JPA / Hibernate in all three Spring Boot services. |
| HITL | Human-in-the-Loop | Machine learning design pattern where human judgment is embedded in the model training or inference pipeline. Core concept of the DataCrowd Lab platform. |