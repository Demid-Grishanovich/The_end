## 5.3 Database Schema

Table 5.5. Database overview

| Attribute | Value |
|---|---|
| Engine | PostgreSQL 16 |
| Logical databases | 3 (`auth_db`, `core_db`, `payments_db`) |
| Schema management | Flyway versioned migrations, applied at service startup |
| Isolation model | Database-per-service; no cross-database foreign keys |
| Concurrency primitive | FOR UPDATE SKIP LOCKED (task queue, review queue) |
| JSON storage | TEXT columns with application-layer ObjectMapper (JSONB semantics) |
| UUID generation | `uuid_generate_v4()` extension |
| Timezone | All `TIMESTAMPTZ` columns stored in UTC |
| Cross-service references | UUID columns (logical references); no database-level FK constraints across services |

### Entity Relationship Diagram
![ER Diagram](../assets/diagrams/PlantUML_diagram.png)

Fig. 5.1. Database ER Diagram

---

### Table Details

Table 5.6. `projects` table — `core_db`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Auto-generated identifier. |
| `name` | VARCHAR(255) | NOT NULL | Human-readable project name. |
| `description` | TEXT | NULL | Optional project description. |
| `owner_user_id` | UUID | NULL | Logical reference to `auth_db.users.id`. No database-level FK. |
| `status` | VARCHAR(50) | NOT NULL DEFAULT `'DRAFT'` | Lifecycle state (e.g., DRAFT, ACTIVE). |
| `data_type` | VARCHAR(20) | NOT NULL DEFAULT `'TEXT'` | Type of data for the project. |
| `billing_status` | VARCHAR(20) | NOT NULL DEFAULT `'UNPAID'` | `UNPAID` or `PAID`. Tasks are claimable only when PAID. |
| `reviewers_count` | INT | NOT NULL DEFAULT `1` | Number of independent approvals required. |
| `reward_points` | INT | NOT NULL DEFAULT `0` | Points awarded to a worker per task. |
| `task_quota` | INT | NOT NULL DEFAULT `0` | Maximum claimable tasks. |
| `min_answer_seconds` | INT | NOT NULL DEFAULT `3` | Minimum elapsed time (seconds) between lock and submit. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Set on INSERT. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Updated by trigger or ORM. |

*Index:* `idx_owner_user_id`

Table 5.7. `tasks` table — `core_db`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Auto-generated identifier. |
| `project_id` | UUID | FK → `projects.id` | Owning project. |
| `dataset_id` | UUID | FK → `datasets.id` | Parent dataset. |
| `title` | VARCHAR(255) | NULL | Short title for the task. |
| `description` | TEXT | NULL | Detailed instructions or task description. |
| `status` | VARCHAR(50) | NOT NULL DEFAULT `'NEW'` | State machine: `NEW`, `LOCKED`, `IN_REVIEW`, etc. |
| `payload_json` | TEXT | NULL | Serialized JSON containing item content. |
| `assigned_user_id` | UUID | NULL | User explicitly assigned to this task (if any). |
| `locked_by_user_id` | UUID | NULL | Logical reference to the worker holding the lock. |
| `locked_at` | TIMESTAMPTZ | NULL | Timestamp of lock acquisition. |
| `created_at` | TIMESTAMPTZ | NOT NULL | Set on INSERT. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Updated by trigger or ORM. |

*Index:* `idx_status_locked_by_user_id`

Table 5.8. `users` table — `auth_db`

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | UUID | PK, NOT NULL | Auto-generated via `uuid_generate_v4()`. |
| `username` | VARCHAR(64) | NOT NULL, UNIQUE | User's display name. |
| `email` | VARCHAR(255) | NOT NULL, UNIQUE | User's login credential. |
| `password_hash` | VARCHAR(255) | NOT NULL | BCrypt hash. |
| `role` | VARCHAR(50) | NOT NULL | E.g., `CLIENT`, `WORKER`, `REVIEWER`. |
| `status` | VARCHAR(16) | NOT NULL DEFAULT `'ACTIVE'` | Account status (`ACTIVE` or `DISABLED`). |
| `created_at` | TIMESTAMPTZ | NOT NULL | Set on INSERT. |
| `updated_at` | TIMESTAMPTZ | NOT NULL | Maintained by trigger `trg_users_set_updated_at`. |

---

Table 5.9. Entity relationships

| Relationship | Type | Description |
|---|---|---|
| `users` → `projects` | One-to-Many | Client owns projects (via `owner_user_id`). Logical reference. |
| `projects` → `datasets` | One-to-Many | Project contains uploaded datasets. |
| `projects` → `tasks` | One-to-Many | Project contains labeling tasks. |
| `datasets` → `tasks` | One-to-Many | Dataset generates atomic labeling tasks. |
| `datasets` → `failed_items` | One-to-Many | Dataset generates DLQ entries for unparseable rows. |
| `tasks` → `answers` | One-to-Many | Task receives answer attempts. |
| `answers` → `reviews` | One-to-Many | Answer receives review decisions. |
| `users` → `worker_profiles`| One-to-One | Worker has a Trust Score profile (via `worker_id`). |
| `users` → `answers` | One-to-Many | Worker submits answers (via `user_id`). Logical reference. |
| `users` → `reviews` | One-to-Many | Reviewer creates reviews (via `reviewer_id`). Logical reference. |
| `tasks` → `points_ledger` | One-to-Many | Task produces ledger credit entries. |
| `payments` → `ledger` | One-to-Many | Payment generates financial journal entries (DEPOSIT, CHARGE, etc.). |

---

Table 5.10. Flyway migration history — `core-service`

| Version | Description | Approximate Date |
|---|---|---|
| V1 | Base schema: `projects`, `datasets`, `tasks`, `answers`, `reviews`. | 2025-11-10 |
| V2 | Add `owner_user_id`, `billing_status`, `task_quota`, `reviewers_count`, `data_type` to `projects`. | 2025-11-18 |
| V5 | Performance indexes: `idx_status_locked_by_user_id` on `tasks`; views for masking. | 2025-12-05 |
| V7 | ZIP dataset support: add `source_type` and `manifest_path` columns to `datasets`. | 2025-12-20 |
| V9 | Add `min_answer_seconds` to `projects`. | 2026-01-08 |
| V10 | Create `failed_items` Dead Letter Queue table. | 2026-01-15 |
| V11 | Create immutable `audit_logs` table. | 2026-01-22 |
| V13 | Create `worker_profiles` table for Trust Score persistence. | 2026-02-10 |
| V14 | Create `exports` table for materialised export file references. | 2026-02-20 |