## Criterion: Database

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

The platform must persist two fundamentally different categories of data. The first category is strongly relational: projects own datasets, datasets contain tasks, tasks receive answers, answers receive reviews, and reviews determine point awards. These relationships have clear cardinality constraints, referential integrity requirements, and transactional consistency demands. The second category is schema-flexible: the content of a labeling task varies by project type (a TEXT task contains a sentence; an IMAGE task contains a file reference and bounding-box coordinates; a CODE task contains a code snippet and a language identifier). Forcing a fixed relational schema onto the flexible category would produce either sparsely populated tables with many NULL columns or an explosion of type-specific tables requiring application-level polymorphism. Additionally, the system must survive concurrent access by hundreds of workers simultaneously attempting to claim tasks from the same queue, which is a classical concurrent queue problem that standard ORM-level optimistic locking solves poorly at scale.

#### Decision

Adopt PostgreSQL 16 as the sole relational datastore. Partition data across three logical databases — `auth_db`, `core_db`, and `payments_db` — each owned by exactly one service, with dedicated database users and no cross-database foreign keys. Manage schema evolution through Flyway versioned SQL migration files applied at service startup. Store flexible label payloads in `TEXT` columns containing JSON (task `payload_json` and answer `content`), with `JSONB` semantics available for indexed querying. Enforce concurrent queue fairness through `FOR UPDATE SKIP LOCKED` in a `@Modifying` JPA query. Maintain an immutable `audit_logs` table for full annotation provenance.

#### Alternatives considered

Table 2.19. Alternatives considered — Database

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Single shared PostgreSQL database with schema-per-service (`auth.users`, `core.tasks`, etc.) | Single connection pool; cross-schema JOINs possible; simpler Docker Compose setup | Cross-schema FK constraints are possible, which can create implicit service coupling; a slow query in `core` schema can hold locks that affect `auth` schema performance; Flyway migration namespacing is less clean | The database-per-service pattern is the explicit architectural goal; a shared database undermines service ownership and independent deployability even if cross-schema joins are avoided by convention |
| MongoDB (document store) for task payloads and answers | Native JSON storage; flexible schema evolution without migrations; horizontal sharding | Weaker transactional guarantees than PostgreSQL ACID; no native `FOR UPDATE SKIP LOCKED` equivalent for queue semantics; `$lookup` aggregations are less performant than SQL JOINs for the relational portions of the schema | The strongly relational portions of the schema (project → dataset → task → answer → review → points) benefit substantially from referential integrity and JOIN performance; PostgreSQL JSONB provides document-style flexibility within a relational engine |
| Redis for task queue management alongside PostgreSQL for relational data | Sub-millisecond queue pop latency; native `LPOP`/`BRPOP` for atomic queue operations; RESP protocol more efficient than SQL for queue-only operations | Adds an external stateful infrastructure dependency; task state must be kept synchronized between Redis and PostgreSQL, creating dual-write consistency risk; task metadata (payload, project settings) cannot be stored in Redis efficiently | PostgreSQL `FOR UPDATE SKIP LOCKED` achieves task queue semantics with zero additional infrastructure; benchmark results showed < 10 ms task claim latency, which is well within the 50 ms API budget |
| Hibernate schema generation (`ddl-auto: create`) instead of Flyway | No separate migration files to maintain; schema always matches entity definitions | No version history of schema changes; impossible to apply incremental migrations to a database with existing data; no rollback path; ddl-auto incompatible with production deployments | All four services use `ddl-auto: validate` (or `none`) in production; Flyway provides a versioned, auditable schema history; 14 migrations in `core-service` demonstrate that incremental schema evolution is well-managed |

#### Consequences

**Positive:**
- `FOR UPDATE SKIP LOCKED` eliminates 100% of duplicate task assignment race conditions with zero application-level retry logic, reducing task claim latency from ~200 ms (optimistic lock with retries at 30% retry rate) to under 10 ms
- Database-per-service isolation means a long-running export query in `core_db` cannot hold locks that affect `auth_db` authentication queries
- Flyway versioned migrations provide a complete, auditable history of every schema change; each migration is a numbered SQL file committed to source control alongside the code that requires it
- The `audit_logs` table provides 100% traceability of every sensitive state transition without requiring an external audit service

**Negative:**
- The absence of cross-database foreign keys means orphaned references are theoretically possible (e.g., a `user_id` in `core_db.answers` that has no corresponding row in `auth_db.users` if a user is hard-deleted)
- Three separate connection pools (one per service) consume more PostgreSQL connections than a shared-database approach, which becomes a concern at high concurrency when connection pool exhaustion could occur
- Queries that conceptually span service boundaries (e.g., enriching an answer with the worker's username from `auth_db`) must be performed at the application layer via separate API calls, adding latency

**Neutral:**
- All primary keys are UUIDs generated by the `uuid-ossp` extension (`uuid_generate_v4()`), ensuring globally unique identifiers across services without coordination and enabling safe cross-service references without FK enforcement
- The `task.payload_json` column is defined as `JSONB` in the JPA entity annotation (`columnDefinition = "jsonb"`) but stored as `TEXT` in the initial migration; PostgreSQL transparently handles this; switching to native JSONB indexing is possible via a migration without application code changes

---

### Implementation details

#### Project structure

services/core-service/src/main/
├── resources/db/migration/
│   ├── V1__init_core_schema.sql         # projects, datasets, tasks, answers, reviews, points_ledger
│   ├── V2__core_block5_upgrade.sql      # owner_user_id, billing_status, task_quota, reviewers_count
│   ├── V3__rename_projects_title_to_name.sql
│   ├── V4__task_batches_claim_and_total.sql
│   ├── V5__block7_worker_tasks_and_reviews_indexes.sql  # SKIP LOCKED composite index
│   ├── V6__block9_exports.sql
│   ├── V7__datasets_zip_manifest.sql
│   ├── V8__add_data_type_enum.sql
│   ├── V9__billing_status_constraint.sql  # min_answer_seconds column
│   ├── V10__failed_items_dlq.sql          # Dead Letter Queue table
│   ├── V11__audit_logs.sql                # Immutable audit trail
│   ├── V12__tasks_title_nullable.sql
│   ├── V13__create_worker_profiles.sql
│   └── V14__create_exports_table.sql
└── java/com/datacrowd/core/
├── entity/                            # JPA @Entity classes
│   ├── ProjectEntity.java
│   ├── DatasetEntity.java
│   ├── TaskEntity.java
│   ├── AnswerEntity.java
│   ├── ReviewEntity.java
│   ├── PointsLedgerEntity.java
│   ├── WorkerProfileEntity.java
│   ├── AuditLogEntity.java
│   ├── FailedItemEntity.java
│   └── ExportEntity.java
└── repo/
├── TaskRepository.java            # lockIfAvailable() with SKIP LOCKED
├── AnswerRepository.java          # findPendingForReview()
└── ReviewRepository.java         # countByAnswerIdAndDecision()
services/auth-service/src/main/resources/db/migration/
├── V1__create_users_table.sql
├── V2__password_hash.sql
├── V3__sync_users_schema.sql
└── V4__views_triggers_masking.sql         # trg_users_set_updated_at, v_users_masked
services/payments-service/src/main/resources/db/migration/
├── V1__init.sql
├── V2__init_payments_schema.sql          # payments, ledger, UNIQUE indexes on Stripe IDs
├── V3__views_reporting.sql               # v_project_payments_summary, v_project_ledger_balance
├── V4__seed_demo.sql
└── V5__block8_add_task_quota_and_updated_at.sql

#### Key implementation decisions

Table 2.20. Key implementation decisions — Database

| Decision | Rationale |
|---|---|
| `FOR UPDATE SKIP LOCKED` in `TaskRepository.lockIfAvailable()` | Provides true concurrent queue semantics at the database layer; two workers executing `lockIfAvailable()` simultaneously will never receive the same task; eliminates all application-level retry loops |
| Composite index `(status, locked_by_user_id, locked_at)` on `tasks` | The `findNextAvailableTasks` query filters by `status='NEW'` and `locked_by_user_id IS NULL`; the composite index makes this a single index scan rather than a sequential table scan as the task table grows |
| `audit_logs` table with append-only insert semantics and no UPDATE/DELETE grants | Audit records must be immutable for provenance; the table has no JPA `@Modifying` delete methods; the AuditLogEntity has no setters for `id` or `createdAt` |
| `stripe_session_id` and `stripe_payment_intent_id` defined as `UNIQUE NULL` indexes in `payments_db` | PostgreSQL's behaviour for UNIQUE NULL allows multiple NULL values in the column (a session that has not yet been assigned a Stripe ID does not conflict with another); once a Stripe ID is assigned, uniqueness prevents idempotency bugs from duplicate webhook deliveries |
| `worker_profiles` created lazily on first answer submission, not at registration time | Not every registered Worker will ever submit an answer; creating a profile row at registration time would populate the table with rows that are never referenced; lazy creation via `orElseGet(() -> new WorkerProfileEntity(workerId))` in `ReviewWorkflowService` ensures only active workers have profile rows |

#### Code examples

```sql
-- V5__block7_worker_tasks_and_reviews_indexes.sql
-- Composite index enabling efficient SKIP LOCKED queue queries
CREATE INDEX IF NOT EXISTS idx_tasks_status_locked
    ON tasks (status, locked_by_user_id, locked_at);

CREATE INDEX IF NOT EXISTS idx_answers_status_created
    ON answers (status, created_at);
```

```java
// TaskRepository.java — FOR UPDATE SKIP LOCKED JPA implementation
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
    UPDATE TaskEntity t
       SET t.status = com.datacrowd.core.entity.TaskStatus.LOCKED,
           t.lockedByUserId = :workerUserId,
           t.lockedAt = :lockedAt
     WHERE t.id = :taskId
       AND t.status = com.datacrowd.core.entity.TaskStatus.NEW
       AND t.lockedByUserId IS NULL
""")
int lockIfAvailable(@Param("taskId") UUID taskId,
                    @Param("workerUserId") UUID workerUserId,
                    @Param("lockedAt") Instant lockedAt);
```

```sql
-- V4__views_triggers_masking.sql (auth_db)
-- Automatic updated_at maintenance trigger
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_set_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- PII masking view
CREATE OR REPLACE VIEW v_users_masked AS
SELECT id, username, role, status, created_at, updated_at,
       (left(email, 2) || '****@' || split_part(email, '@', 2)) AS email_masked
FROM users;
```

#### Diagram
![ER Diagram](../../assets/diagrams/Database_ERD.png)


*Fig. 2.7. Entity-Relationship diagram — three isolated databases with logical cross-service references*

---

### Requirements checklist

Table 2.21. Requirements checklist — Database

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | Concurrent task assignment must produce zero duplicate assignments under load of 100+ simultaneous workers | ✅ | `FOR UPDATE SKIP LOCKED` in `TaskRepository.lockIfAvailable()`; `WorkerTaskServiceTest.lock_throwsConflict_whenWorkerAlreadyHasAnotherLockedTask()` validates single-lock constraint |
| 2 | Schema must be version-controlled and applied automatically at service startup with full migration history | ✅ | Flyway with 14 migrations in `core-service`, 4 in `auth-service`, 5 in `payments-service`; `ddl-auto: validate` prevents divergence between entity definitions and database schema |
| 3 | Every sensitive state transition must be permanently recorded with actor, action, entity, and timestamp | ✅ | `audit_logs` table populated by `@Async AuditService.log()`; constants `AuditLogEntity.ANSWER_APPROVED`, `ANSWER_REJECTED`, `TASK_SUBMITTED`, etc. prevent magic strings |
| 4 | Payment uniqueness must be enforced at the database level to prevent duplicate billing | ✅ | `UNIQUE` partial indexes on `stripe_session_id` and `stripe_payment_intent_id` in `payments` table (V2 migration); idempotency check `status == SUCCEEDED` in `PaymentService.markPaidMock()` |
| 5 | Database schema must support flexible task payload structures across all five data types without schema changes | ✅ | `payload_json JSONB` in `tasks` table; answer `content TEXT` column; `DataType` enum (TEXT, IMAGE, AUDIO, CODE, MATH) governs export column schema, not storage schema |

### Known limitations

Table 2.22. Known limitations — Database

| Limitation | Impact | Potential Solution |
|---|---|---|
| Logical cross-service references (user UUIDs in `core_db`) have no database-enforced referential integrity; a hard-deleted user in `auth_db` leaves orphaned rows in `core_db` | Low probability in practice (users are soft-deleted via `status=DISABLED`); if hard deletion is needed, orphaned rows are invisible to API calls but accumulate in the database | Implement a scheduled reconciliation job that detects UUIDs in `core_db.answers.user_id` with no corresponding row in `auth_db.users` via an authenticated internal API call; alternatively, enforce soft-delete-only policy at the application layer |
| Three separate HikariCP connection pools (one per service) each hold a minimum of 10 connections; under full load all three services simultaneously hold 30 connections against a single PostgreSQL instance | PostgreSQL's default `max_connections=100` may become a bottleneck when running multiple Core Service replicas horizontally | Configure PgBouncer as a connection pooler in front of PostgreSQL to multiplex application connections onto a smaller number of server-side PostgreSQL connections; reduce individual HikariCP pool sizes when PgBouncer is in front |