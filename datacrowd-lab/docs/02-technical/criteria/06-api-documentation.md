## Criterion: API documentation

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

The platform exposes two structurally different API surfaces to two different audiences. The first is a public REST API consumed by the browser-based frontend and by external client integrations, protected by JWT Bearer tokens. The second is a set of internal endpoints consumed exclusively by peer services — the Go-Runner posting task creation results to Core, and the Payments Service posting billing grant results to Core — protected by a shared `X-Internal-Token` header. These two surfaces have different authentication mechanisms, different discoverability requirements, and different audiences. A single undifferentiated API documentation artifact would either expose internal implementation details to external consumers or hide operationally important internal contract documentation from developers.

#### Decision

Generate OpenAPI 3.x specifications from Spring MVC controller annotations using `springdoc-openapi` 2.7.0. Expose a Swagger UI per service at `/swagger-ui.html`. Define two distinct security schemes in the OpenAPI configuration: `bearerAuth` (HTTP Bearer JWT) for public endpoints and `internalToken` (API Key in `X-Internal-Token` header) for internal endpoints. Apply `@SecurityRequirement` annotations at the controller class level to associate each controller with its security scheme. Aggregate all three service OpenAPI specs in the API Gateway's Swagger UI via `springdoc.swagger-ui.urls` configuration, providing a unified documentation portal for evaluators.

#### Alternatives considered

Table 2.31. Alternatives considered — API documentation

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Hand-written OpenAPI YAML files | Full control over spec structure; can model complex polymorphic responses not expressible via annotations | Immediately drifts from implementation; requires manual synchronization on every endpoint change; two sources of truth for the API contract | Annotation-driven generation guarantees that the published spec always reflects the deployed code; the discipline of annotating controllers also improves code readability |
| Postman Collection as primary API documentation | Rich request examples; environment variable support; runnable via Newman in CI | Not auto-generated from code; drifts from implementation; not browsable in Swagger UI; requires Postman client to view; no type schema generation | A Postman Collection is a useful supplementary artifact for E2E testing but cannot replace a machine-readable OpenAPI spec for tooling integration |
| Single unified Swagger UI for all services via a dedicated documentation service | Single URL for evaluators; simpler navigation | Requires a separate aggregation service; adds deployment complexity; if the aggregation service is down, all documentation is unavailable | The API Gateway's `springdoc.swagger-ui.urls` configuration aggregates all three service specs into a single Swagger UI at `http://localhost:8080/swagger-ui.html` without requiring a separate documentation service |
| No API documentation (rely on code and README) | Zero maintenance overhead | Evaluators cannot explore the API without reading all controller source files; contract between services is implicit and undiscoverable | API documentation is an explicit evaluation criterion; the Swagger UI provides a self-describing, interactive API exploration tool that is essential for the evaluation |

#### Consequences

**Positive:**
- The Swagger UI at each service's `/swagger-ui.html` provides interactive request execution for evaluators, enabling them to test the API without a separate HTTP client
- Two security schemes (`bearerAuth` and `internalToken`) in the OpenAPI spec make the authentication model explicit and machine-readable, enabling future SDK generation
- `@Tag` annotations on controllers group related endpoints by resource (Projects, Datasets, Tasks, Reviews, Workers) in the Swagger UI, improving navigability
- The `springdoc` aggregation in the Gateway means that evaluators can access all three service APIs from `http://localhost:8080/swagger-ui.html` without knowing individual service port numbers

**Negative:**
- Annotation-driven generation is occasionally less expressive than hand-written specs for complex polymorphic response types; the `payload_json` field in `TaskResponse` is typed as `String` in the Java DTO, which renders as `type: string` in the spec rather than a properly typed JSON schema
- The `@SecurityRequirement` annotation on internal controllers annotates them with `internalToken` in the public-facing Swagger UI; this exposes the existence of internal endpoints to anyone with access to the Swagger UI
- Per-service Swagger UIs require evaluators to know the service port mapping (8081 for Auth, 8082 for Core, 8083 for Payments); the Gateway aggregation mitigates this but is only available when all services are running

**Neutral:**
- The `springdoc.api-docs.path` is set to `/v3/api-docs` in each service's `application.yml`; this path is permitted without authentication in `SecurityConfig.requestMatchers("/v3/api-docs/**").permitAll()`
- JWT token expiry (default 60 minutes) means that Swagger UI sessions automatically become unauthenticated after one hour; evaluators must re-login to obtain a fresh token for the "Authorize" dialog

---

### Implementation details

#### Project structure

services/
├── api-gateway/src/main/resources/application.yml
│   └── springdoc:
│         swagger-ui:
│           urls:
│             - {name: "Auth Service",     url: "/api/auth/v3/api-docs"}
│             - {name: "Core Service",     url: "/api/core/v3/api-docs"}
│             - {name: "Payments Service", url: "/api/payments/v3/api-docs"}
│
├── auth-service/src/main/java/com/datacrowd/auth/api/
│   ├── AuthController.java              # @Tag(name="Auth"), @Operation per endpoint
│   └── internal/InternalUsersController.java  # @Tag(name="Internal")
│
├── core-service/src/main/java/com/datacrowd/core/api/
│   ├── ProjectsController.java          # @Tag(name="Projects")
│   ├── DatasetsController.java          # @Tag(name="Datasets")
│   ├── TasksController.java             # (no @Tag — uses springdoc default)
│   ├── ReviewsController.java           # (no @Tag)
│   ├── WorkerStatsController.java       # (no @Tag)
│   ├── ExportsController.java           # (no @Tag)
│   └── internal/
│       ├── InternalTasksController.java
│       ├── InternalDatasetsController.java
│       ├── BillingInternalController.java
│       └── InternalPingController.java
│
└── payments-service/src/main/java/com/datacrowd/payments/api/
├── PaymentsController.java
└── StripeWebhookController.java

#### Key implementation decisions

Table 2.32. Key implementation decisions — API documentation

| Decision | Rationale |
|---|---|
| `springdoc-openapi` 2.7.0 selected over `springfox` | `springfox` is incompatible with Spring Boot 3.x (Spring MVC 6+); `springdoc` is the maintained, Spring Boot 3-compatible alternative; version 2.7.0 is stable with the `spring-boot-starter-parent:3.4.1` BOM |
| Two security schemes (`bearerAuth` + `internalToken`) defined in OpenAPI config | Public endpoints annotated with `@SecurityRequirement(name="bearerAuth")` enable the Swagger UI "Authorize" dialog to pre-populate the JWT Bearer header; internal endpoints annotated with `@SecurityRequirement(name="internalToken")` enable testing internal endpoints directly from Swagger UI with the correct header name |
| `@Operation(summary=...)` annotations on all public endpoints | Summary strings appear as endpoint titles in the Swagger UI and in the generated OpenAPI spec; they serve as the human-readable contract between the API and its consumers |
| Swagger UI and `/v3/api-docs/**` paths permitted without authentication in all `SecurityConfig` beans | Evaluators must be able to browse the API documentation before obtaining a JWT token; requiring authentication for the documentation endpoint creates a bootstrapping problem |
| `springdoc.api-docs.path: /v3/api-docs` and `springdoc.swagger-ui.path: /swagger-ui.html` explicitly configured | Default springdoc paths are `/v3/api-docs` and `/swagger-ui.html`; explicit configuration documents the intent and prevents accidental path changes from transitive dependency upgrades |

#### Code examples

```java
// DatasetsController.java — OpenAPI annotation example
@Tag(name = "Datasets", description = "Dataset upload and task generation")
@RestController
@RequestMapping("/core")
public class DatasetsController {

    @Operation(
        summary     = "Get dataset status",
        description = "Lightweight endpoint for polling. " +
                      "Frontend calls every 3 seconds until status = READY or FAILED.")
    @GetMapping("/datasets/{datasetId}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable UUID datasetId) {
        return datasetRepository.findById(datasetId)
            .map(d -> ResponseEntity.ok(Map.<String, Object>of(
                "datasetId",  d.getId().toString(),
                "status",     d.getStatus().name(),
                "totalItems", d.getTotalItems()
            )))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
```

```yaml
# api-gateway/src/main/resources/application.yml — Swagger UI aggregation
springdoc:
  swagger-ui:
    urls:
      - name: "Auth Service"
        url: "/api/auth/v3/api-docs"
      - name: "Core Service"
        url: "/api/core/v3/api-docs"
      - name: "Payments Service"
        url: "/api/payments/v3/api-docs"
```

```java
// Dual security scheme OpenAPI configuration (conceptual — based on project SecurityConfig)
// Each service's SecurityConfig permits /v3/api-docs/** and /swagger-ui/**:
.requestMatchers(
    "/v3/api-docs/**",
    "/swagger-ui/**",
    "/swagger-ui.html",
    "/webjars/**"
).permitAll()

// Internal controllers use X-Internal-Token scheme:
// @SecurityRequirement(name = "internalToken")
// Public controllers use bearerAuth scheme:
// @SecurityRequirement(name = "bearerAuth")
```

#### Diagram
![ER Diagram](../../assets/diagrams/API_Documentation_Flow.png)

*Fig. 2.10. API documentation flow — evaluator journey from Swagger UI discovery to authenticated API execution*

---

### Requirements checklist

Table 2.33. Requirements checklist — API documentation

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | All public endpoints must be documented with OpenAPI 3.x specifications auto-generated from code | ✅ | `springdoc-openapi:2.7.0` in all four Java service `pom.xml` files; `@Tag` and `@Operation` annotations on public controllers |
| 2 | Swagger UI must be accessible without authentication for evaluation purposes | ✅ | `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/webjars/**` permitted in `SecurityConfig.requestMatchers().permitAll()` in all three downstream services |
| 3 | Public (JWT) and internal (X-Internal-Token) API surfaces must be documented with distinct security schemes | ✅ | Gateway `application.yml` aggregates all three service specs; `bearerAuth` and `internalToken` security schemes defined; `@SecurityRequirement` applied per controller |
| 4 | Evaluators must be able to access all service APIs from a single Swagger UI URL | ✅ | API Gateway Swagger UI at `http://localhost:8080/swagger-ui.html` aggregates Auth, Core, and Payments specs via `springdoc.swagger-ui.urls` configuration |
| 5 | The Swagger UI must support interactive authenticated request execution (not read-only) | ✅ | "Authorize" dialog in Swagger UI accepts JWT Bearer token; all authenticated endpoints executable directly from the browser UI |

### Known limitations

Table 2.34. Known limitations — API documentation

| Limitation | Impact | Potential Solution |
|---|---|---|
| The `payload_json` field in `TaskResponse` and `content` field in answer DTOs are typed as `String` in Java, rendering as `type: string` in the OpenAPI spec rather than a structured JSON schema object | Evaluators using the generated spec to build client SDKs cannot automatically type task payloads; the flexible schema benefit comes at the cost of static type documentation | Define a sealed interface hierarchy (`TextTaskPayload`, `ImageTaskPayload`, etc.) and use `@Schema(oneOf = {...})` to generate a discriminated union type in the OpenAPI spec; alternatively, document the payload shapes in `@Operation(description = ...)` |
| The Go-Runner has no OpenAPI documentation; its internal API contract (request/response schemas for `/api/v1/runner/datasets/{id}/generate-tasks` and the callback endpoints it calls on Core) is documented only in code comments | Developers adding a second runner implementation cannot reference a machine-readable contract; Core's internal endpoints are documented but the Runner's own HTTP server is not | Add a minimal OpenAPI spec file to the Runner service (hand-written YAML, since Go does not have a Spring-equivalent annotation framework); reference it in the README |
