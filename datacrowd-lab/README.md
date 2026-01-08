# DataCrowd Lab

Microservice system for dataset annotation: projects/datasets/tasks, worker processing, approvals and export.  
Entry point for clients is **API Gateway**.

---

## Architecture

Services:
- **api-gateway** (Spring Cloud Gateway) — single external entrypoint `/api/**`
- **auth-service** (Spring Boot) — registration/login, JWT issuing
- **core-service** (Spring Boot) — projects/datasets/tasks/answers/export, internal API for runner/workers
- **payments-service** (Spring Boot) — payments module (Stripe optional, mock-payment supported)
- **runner** (Go) — periodic orchestration / background flow (creates/updates tasks via internal API)

Diagrams: `docs/diagrams/*.puml`

---

## How to run (Docker)

Go to docker folder and start:

```bash
cd infra/docker
docker compose up --build
```

Environment is configured in:
- `infra/docker/.env` (the only real env for docker)
- `.env.example` (example template, no secrets)

---

## External API (Gateway)

Client should call gateway only:

- `http://localhost:8080/api/auth/**` → auth-service `/auth/**`
- `http://localhost:8080/api/core/**` → core-service `/core/**`
- `http://localhost:8080/api/payments/**` → payments-service `/payments/**`

Internal services also expose `/auth`, `/core`, `/payments` for container network.

---

## Security note (Gateway)

For diploma demo gateway is configured as `permitAll` and does not enforce JWT at the gateway level.  
Authorization is enforced inside services (core/auth/payments). This is a documented simplification.

---

## Database users / roles note

Database init scripts use fixed DB users:
- `auth_user`
- `core_user`
- `payments_user`

They are treated as constants of the project to keep initdb SQL deterministic.

---

## Payments note

Stripe integration is optional.  
If Stripe is disabled, payments-service supports mock payment:

```
/api/payments/mock/pay/{paymentId}
```

---

## Tests

Run tests per service:

```bash
cd services/auth-service
mvn test
```

JaCoCo report:
```
services/<service>/target/site/jacoco/index.html
```
