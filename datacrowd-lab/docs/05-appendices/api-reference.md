## 5.2 API Reference

### Overview

**Base URL (all services via API Gateway):** `http://localhost:8080`

| Service | Direct Port | Gateway Prefix |
|---|---|---|
| Auth Service | `:8081` | `/api/auth/**` |
| Core Service | `:8082` | `/api/core/**` |
| Payments Service | `:8083` | `/api/payments/**` |
| Go-Runner (internal) | `:8090` | Not routed via gateway |

**Authentication**

All non-public endpoints require a Bearer JWT in the `Authorization` header:
Authorization: Bearer <token>

text

Tokens are issued by `POST /api/auth/login` and carry `userId` and `role` claims. Default TTL is 60 minutes. Internal service-to-service endpoints use a separate shared secret:
X-Internal-Token: <INTERNAL_TOKEN>

text

Internal endpoints (`/internal/**`) are not routed by the API Gateway and are unreachable from public traffic.

---

### Endpoint Details

#### POST /api/core/projects

Creates a new labeling project. The project is created with `status=NEW` and `billingStatus=UNPAID`. Workers cannot claim tasks until the project is funded.

**Required role:** `CLIENT`

**Request body:**

```json
{
  "name": "Sentiment Analysis Q1 2026",
  "dataType": "TEXT",
  "reviewersCount": 2,
  "rewardPoints": 10,
  "minAnswerSeconds": 5,
  "description": "Classify customer reviews as positive, negative, or neutral."
}
```

**Response — 201 Created:**

```json
{
  "id": "a3f1c820-11b2-4d3e-95a7-0011befc3921",
  "name": "Sentiment Analysis Q1 2026",
  "dataType": "TEXT",
  "status": "NEW",
  "billingStatus": "UNPAID",
  "reviewersCount": 2,
  "rewardPoints": 10,
  "minAnswerSeconds": 5,
  "taskQuota": 0,
  "totalTasks": 0,
  "completedTasks": 0,
  "progressPercent": 0.0,
  "createdAt": "2026-04-27T10:15:00Z"
}
```

---

#### GET /api/core/tasks/next

Returns the next available task for the authenticated worker. Applies `FOR UPDATE SKIP LOCKED` at the database level to guarantee exclusive, contention-free task distribution. Returns HTTP 204 if no tasks are available. Returns HTTP 403 if the worker's Trust Score is below the block threshold (30).

**Required role:** `WORKER`

**Query parameters:**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `projectId` | UUID | No | Restricts the queue to a specific project. Omit to pull from any available project. |

**Response — 200 OK:**

```json
{
  "id": "7bc09d4e-33a1-4f8e-b21c-5599aab71100",
  "projectId": "a3f1c820-11b2-4d3e-95a7-0011befc3921",
  "dataType": "TEXT",
  "status": "NEW",
  "payloadJson": {
    "id": "42",
    "text": "The product arrived on time and works perfectly.",
    "aiSuggestedLabel": "positive",
    "aiConfidence": 0.94
  },
  "lockedByUserId": null,
  "lockedAt": null
}
```

**Response — 204 No Content:** No tasks currently available for the specified project.

**Response — 403 Forbidden:**

```json
{
  "error": "ACCESS_DENIED",
  "message": "Worker trust score (22) is below the required threshold (30). Account is BLOCKED."
}
```

---

#### POST /api/payments/checkout

Creates a Stripe Checkout Session (or mock equivalent if `STRIPE_ENABLED=false`). On success the session URL is returned. When the payment completes, Payments Service calls Core Service via the internal billing grant endpoint to set `billingStatus=PAID` and increment `taskQuota`.

**Required role:** `CLIENT`

**Request body:**

```json
{
  "projectId": "a3f1c820-11b2-4d3e-95a7-0011befc3921",
  "amountCents": 5000,
  "taskQuota": 500
}
```

**Response — 200 OK:**

```json
{
  "paymentId": "f7e2a109-9c3b-4d01-8f44-12345abc6789",
  "sessionUrl": "https://checkout.stripe.com/pay/cs_test_a1B2c3...",
  "status": "PENDING",
  "mockPayUrl": null
}
```

**Response — 200 OK (mock mode, `STRIPE_ENABLED=false`):**

```json
{
  "paymentId": "f7e2a109-9c3b-4d01-8f44-12345abc6789",
  "sessionUrl": null,
  "status": "PENDING",
  "mockPayUrl": "http://localhost:8080/api/payments/mock/pay/f7e2a109-9c3b-4d01-8f44-12345abc6789"
}
```

---

#### POST /api/core/tasks/{id}/submit

Submits a labeled answer for a locked task. Validates that the caller holds the lock, that `minAnswerSeconds` has elapsed since `locked_at`, and (if the task is a honeypot) that the answer matches the known correct label. On validation failure the Trust Score is adjusted and a 409 is returned.

**Required role:** `WORKER` (must be the lock holder)

**Path parameter:** `id` — UUID of the locked task.

**Request body:**

```json
{
  "answerJson": "{\"label\": \"positive\"}"
}
```

**Response — 200 OK:**

```json
{
  "answerId": "d91b0443-8812-4e00-bc3a-77f3c1d2e889",
  "taskId": "7bc09d4e-33a1-4f8e-b21c-5599aab71100",
  "status": "SUBMITTED",
  "message": "Answer submitted. Pending review by 2 reviewer(s)."
}
```

**Response — 409 Conflict (too fast):**

```json
{
  "error": "ANSWER_TOO_FAST",
  "message": "Answer submitted too fast (1s < minAnswerSeconds=5). Trust score decreased.",
  "newTrustScore": 85
}
```

---

Table 5.3. Standard error responses

| HTTP Code | Error Key | Description |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Request body fails field validation (missing required field, invalid enum value, constraint violation). |
| 401 | `UNAUTHORIZED` | Missing or expired Bearer JWT. Client must re-authenticate via `POST /api/auth/login`. |
| 403 | `ACCESS_DENIED` | Authenticated user does not have the required role for the endpoint, or a worker attempts to access a resource owned by another user. Also returned when Trust Score < 30 on task acquisition. |
| 404 | `NOT_FOUND` | Requested resource (project, task, dataset, export, answer) does not exist or is not visible to the caller. |
| 409 | `STATE_CONFLICT` | Invalid state machine transition (e.g. submitting an already-submitted answer, re-reviewing an already-reviewed answer, answer submitted faster than `minAnswerSeconds`). |
| 500 | `INTERNAL_ERROR` | Unexpected server-side failure. Details are logged server-side; the response body contains only a correlation identifier. |

Table 5.4. Rate limiting tiers

| Tier | Role | Requests / Minute | Requests / Day | Notes |
|---|---|---|---|---|
| Public (unauthenticated) | — | 20 | 500 | Registration and login endpoints only. |
| Worker | `WORKER` | 120 | 5,000 | Covers task polling, lock, submit, and stats calls. |
| Client | `CLIENT` | 60 | 2,000 | Covers project management, dataset upload, and export. |
| Reviewer | `REVIEWER` | 60 | 2,000 | Covers review-queue polling and approve/reject calls. |
| Admin | `ADMIN` | 300 | 20,000 | Elevated limit for bulk user-management operations. |
| Internal service | X-Internal-Token | 1,000 | Unlimited | Service-to-service calls on `/internal/**` paths. |