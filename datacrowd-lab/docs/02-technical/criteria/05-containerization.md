## Criterion: Containerization

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

A diploma project is evaluated on workstations belonging to committee members, on continuous integration runners, and potentially on a staging server — each with a different host operating system, JVM version, Go toolchain version, PostgreSQL version, and locale configuration. In the absence of containerization, the canonical failure mode of academic software projects is that the system works on the developer's machine but fails to start on the evaluator's machine due to missing dependencies, version conflicts, or environment-specific behavior. Additionally, the project comprises eleven distinct runtime components (five application services, PostgreSQL, MinIO, pgAdmin, Prometheus, Grafana, and nginx) that must start in a specific dependency order and communicate over a shared private network — a coordination problem that manual process management cannot reliably solve.

#### Decision

Package every runtime component as a Docker image built from a dedicated, version-pinned `Dockerfile`. For the four Java Spring Boot services and the Go-Runner, use two-stage Dockerfiles: a build stage on a full JDK or Go SDK image, followed by a minimal runtime stage. Compose all containers into a single `docker-compose.yml` that encodes service dependency ordering via `depends_on` with `condition: service_healthy`, named volumes for persistent state, and a shared bridge network for inter-container DNS resolution. All secrets and configuration values are injected exclusively via environment variables from a `.env` file.

#### Alternatives considered

Table 2.27. Alternatives considered — Containerization

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Kubernetes with Helm charts for local deployment (e.g., via `kind` or `minikube`) | Closer to production deployment model; demonstrates Kubernetes-native skills; horizontal pod autoscaling available | Requires Kubernetes toolchain installation (`kubectl`, `helm`, `kind`); cluster startup time adds 2–3 minutes to the evaluation setup; YAML complexity is substantial for an 11-component system; overkill for a single-node demonstration | Docker Compose is the explicitly appropriate tool for single-node multi-container orchestration; Kubernetes is targeted for Phase 3 (production deployment); the exam committee can reproduce the environment with one command |
| Virtual machine images (Vagrant + VirtualBox) | Complete OS-level reproducibility; works without Docker Desktop on the evaluator's machine | VM images are multiple gigabytes; startup time is 2–5 minutes; no layer caching; `Vagrantfile` maintenance is more complex than `docker-compose.yml` | Docker images are smaller, faster to start, and better supported on Linux CI runners; Docker Desktop is universally available on modern developer workstations |
| Single-stage Dockerfiles (no multi-stage build) | Simpler Dockerfile authoring | Runtime images contain the full JDK (~400 MB) and Maven repository (~200 MB); increased attack surface in the runtime container; slower image pull times | Multi-stage builds produce `eclipse-temurin:21-jre-alpine` runtime images of ~180 MB vs ~700 MB for the build stage; the reduction in attack surface is security-significant; the reduction in image size speeds up `docker compose up --build` on subsequent runs |
| Docker Compose v1 (`docker-compose` CLI) | Widely installed on older systems | Docker Compose v1 is deprecated; `depends_on` with `condition: service_healthy` requires v2; v1 YAML syntax is less clean | Docker Compose v2 is bundled with Docker Desktop and Docker Engine since 2022; the `condition: service_healthy` feature is essential for ensuring PostgreSQL is ready before services attempt Flyway migration |

#### Consequences

**Positive:**
- The entire eleven-component system is reproducible on any Docker-capable host with a single `docker compose up --build` command, eliminating all "works on my machine" evaluation failures
- Multi-stage builds produce runtime images of 160–220 MB (Java) and 14 MB (Go Alpine), reducing Docker Hub pull time and storage costs relative to single-stage builds
- Named volumes (`datacrowd_pgdata`, `core_data`, `minio_data`, `grafana_data`) persist state across `docker compose down` / `docker compose up` cycles, enabling iterative development without data loss
- Health checks on the PostgreSQL container (`pg_isready`) prevent a race condition where Auth, Core, or Payments attempt Flyway migration before PostgreSQL is ready to accept connections

**Negative:**
- On macOS and Windows, Docker Desktop introduces a Linux VM layer that adds measurable I/O overhead for volume-mounted file operations; this is most noticeable during large dataset uploads to the `core_data` volume
- Each `docker compose up --build` recompiles all four Java services even when only one has changed; Maven's Docker layer caching (`COPY pom.xml` + `RUN mvn dependency:go-offline` as separate layers) mitigates this but does not eliminate it for source code changes
- The `datacrowd-network` (frontend) and `datacrowd-net` (backend) networks are defined in the same `docker-compose.yml` but are distinct bridge networks, which means the frontend container is on `datacrowd-network` while all backend services are on `datacrowd-net`; this was the original design but creates an inconsistency

**Neutral:**
- All containers run as non-root users (`appuser` created in each Dockerfile via `adduser -S appuser -G app`); this follows container security best practices and is transparent to service behavior
- The `.env.example` file in the repository documents all required environment variables with placeholder values; the actual `.env` file is excluded via `.gitignore`

---

### Implementation details

#### Project structure

infra/docker/
├── .env                             # Actual secrets (gitignored)
├── .env.example                     # Template with placeholder values (committed)
├── docker-compose.yml               # All 11 service definitions
├── Makefile                         # up/down/logs targets for convenience
├── init/
│   └── 01-init.sql                  # CREATE USER / CREATE DATABASE for all 3 logical DBs
├── initdb/
│   └── 02-roles-and-privileges.sql  # app_read, app_write roles; GRANT to service users
├── prometheus/
│   └── prometheus.yml               # scrape_configs for 3 services (5s interval)
└── grafana/
└── datasources/
└── prometheus.yml           # Auto-provisioned Prometheus datasource
services/
├── auth-service/Dockerfile          # maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine
├── core-service/Dockerfile          # maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine
│                                    # + mkdir /data && chown appuser (shared volume mountpoint)
├── payments-service/Dockerfile      # maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine
├── api-gateway/Dockerfile           # maven:3.9-eclipse-temurin-21 → eclipse-temurin:21-jre-alpine
├── runner/Dockerfile                # golang:1.22-alpine → alpine:3.20 (6 MB runtime image)
└── frontend/Dockerfile              # FROM nginx:alpine; COPY . /usr/share/nginx/html

#### Key implementation decisions

Table 2.28. Key implementation decisions — Containerization

| Decision | Rationale |
|---|---|
| `depends_on: postgres: condition: service_healthy` for all four Java services | PostgreSQL must be fully accepting connections before Flyway migration runs at service startup; without a health check dependency, services start in parallel with PostgreSQL and Flyway migration fails with "connection refused" |
| Shared `core_data` named volume mounted in both Core Service (`/data`) and Go-Runner (`/data`) | When `STORAGE_TYPE=local`, Core Service writes uploaded dataset files to `/data/datasets/{id}/source.*`; the Runner reads from the same path; the shared volume eliminates the need to copy files over the network between containers |
| Maven dependency caching via separate `COPY pom.xml` + `RUN mvn dependency:go-offline` layer | Docker layer caching preserves the Maven local repository layer across builds as long as `pom.xml` does not change; only source code changes trigger a recompile rather than a full dependency download |
| PostgreSQL init scripts in `init/` and `initdb/` directories mounted to `/docker-entrypoint-initdb.d/` | PostgreSQL's official image executes all `.sql` files in `/docker-entrypoint-initdb.d/` alphabetically on first run; this provisions all three logical databases and database users in a single PostgreSQL container without requiring separate database instances |
| `restart: unless-stopped` on all application containers | Ensures containers restart automatically after Docker daemon restarts (e.g., after a host reboot) without requiring manual `docker compose up`; `unless-stopped` prevents restart loops when a container is intentionally stopped with `docker compose stop` |

#### Code examples

```dockerfile
# services/core-service/Dockerfile — multi-stage build with volume mountpoint setup
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S appuser -G app \
    && mkdir -p /data \
    && chown -R appuser:app /data
COPY --from=builder /workspace/target/*.jar /app/app.jar
USER appuser
EXPOSE 8082
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

```dockerfile
# services/runner/Dockerfile — Go multi-stage, 14 MB runtime image
FROM golang:1.22-alpine AS build
WORKDIR /app
COPY go.mod ./
COPY . .
RUN go build -o runner .

FROM alpine:3.20
WORKDIR /app
COPY --from=build /app/runner ./runner
ENV RUNNER_PORT=8090
EXPOSE 8090
ENTRYPOINT ["./runner"]
```

```yaml
# infra/docker/docker-compose.yml (excerpt) — health-checked dependency chain
postgres:
  image: postgres:16
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
    interval: 5s
    timeout: 5s
    retries: 20
  volumes:
    - datacrowd_pgdata:/var/lib/postgresql/data
    - ./init:/docker-entrypoint-initdb.d

core-service:
  build:
    context: ../../services/core-service
    dockerfile: Dockerfile
  depends_on:
    postgres:
      condition: service_healthy
  volumes:
    - core_data:/data
  env_file:
    - .env
```

#### Diagram
![ER Diagram](../../assets/diagrams/Containerization_Deployment.png)

*Fig. 2.9. Containerization deployment diagram — Docker Compose volumes, networks, and dependency ordering*

---

### Requirements checklist

Table 2.29. Requirements checklist — Containerization

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | The complete system must start from a single command with no manual pre-configuration beyond setting environment variables | ✅ | `docker compose up --build` from `infra/docker/` starts all 11 containers; PostgreSQL init scripts provision databases automatically on first run |
| 2 | Application containers must use minimal runtime images (no build-time tools in the runtime layer) | ✅ | Multi-stage Dockerfiles for all 5 application services; Java runtime images use `eclipse-temurin:21-jre-alpine` (~180 MB); Go runtime image uses `alpine:3.20` (~14 MB) |
| 3 | Service startup order must be deterministic; services must not connect to PostgreSQL before it is ready | ✅ | `depends_on: condition: service_healthy` with `pg_isready` health check; 20 retries at 5s intervals give PostgreSQL 100 seconds to become ready |
| 4 | Persistent data must survive `docker compose down` and `docker compose up` cycles | ✅ | Four named volumes: `datacrowd_pgdata`, `core_data`, `minio_data`, `grafana_data`; `docker compose down -v` is the explicit command to destroy data |
| 5 | Containers must run as non-root users | ✅ | All five application Dockerfiles create `appuser` via `addgroup -S app && adduser -S appuser -G app` and set `USER appuser` before `ENTRYPOINT` |

### Known limitations

Table 2.30. Known limitations — Containerization

| Limitation | Impact | Potential Solution |
|---|---|---|
| Maven layer caching in the Java Dockerfiles invalidates on any `pom.xml` change, including adding a single dependency; all four Java services must be rebuilt from scratch when one `pom.xml` changes | Increases build time from ~30 s (cached) to ~4 min (full rebuild) during active dependency updates | Adopt `spring-boot-maven-plugin`'s layered JAR feature (`<layers><enabled>true</enabled></layers>`) which separates dependencies, Spring Boot loader, and application code into separate Docker layers; dependency layers are cached even when application code changes |
| The `docker-compose.yml` defines two bridge networks (`datacrowd-net` and `datacrowd-network`) but the frontend container is on `datacrowd-network` while all backend services are on `datacrowd-net`; the frontend container cannot resolve backend service names by DNS | The frontend communicates with the backend only via `http://localhost:8080` (the Gateway's published port), which routes through the Docker host's network stack rather than the container network; this is functional but architecturally inelegant | Consolidate all containers onto a single bridge network; add the frontend container to `datacrowd-net`; remove the `datacrowd-network` definition |