## Criterion: Automated tests

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

The platform's correctness guarantees depend on three layers of behavior that cannot be verified by unit tests against mocked dependencies. First, the concurrent task assignment mechanism relies on PostgreSQL's `FOR UPDATE SKIP LOCKED` behavior — a database-level primitive whose semantics are erased by mocking. Second, the state machine transitions in `WorkerTaskService` and `ReviewWorkflowService` involve multiple interacting conditions (Trust Score, minimum answer time, honeypot detection, consensus counting) whose interactions only manifest correctly when tested against the full service context. Third, the cross-service contracts between Core and the Go-Runner (bulk task creation), and between Payments and Core (billing grant), must be verified against real service behavior rather than mock responses. A test strategy limited to mocked unit tests would provide coverage metrics while leaving the most failure-prone behaviors untested.

#### Decision

Adopt a four-layer testing pyramid: (1) unit tests with JUnit 5 and Mockito for pure domain logic; (2) integration tests with `@SpringBootTest` and Testcontainers for real PostgreSQL 16 containers; (3) controller-layer tests with `@WebMvcTest` and `MockMvc` for HTTP contract verification; and (4) a documented E2E scenario executable via Postman or REST-assured. Enforce a minimum 60% JaCoCo instruction coverage gate on all Java services in the CI pipeline, excluding configuration classes, application entry points, DTOs, and entity classes. Run all tests in per-service GitHub Actions jobs with dedicated PostgreSQL service containers.

#### Alternatives considered

Table 2.35. Alternatives considered — Automated tests

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Mocked PostgreSQL (H2 in-memory database) for integration tests | Zero Docker dependency for tests; faster test execution (~5× vs Testcontainers); no Docker Desktop required on developer machines | H2 SQL dialect differs from PostgreSQL; `FOR UPDATE SKIP LOCKED` is not supported in H2; PostgreSQL-specific types (`UUID`, `JSONB`, `TIMESTAMPTZ`) behave differently; Flyway migrations targeting PostgreSQL fail on H2 | The entire value of the integration test layer is verifying behavior against the actual target database; using H2 would produce a green test suite that fails on the real database |
| Test coverage target of 80% instruction coverage | Higher confidence in code correctness; forces testing of edge cases | Excluding DTOs, entities, and config classes (which are correct by construction) inflates the denominator; achieving 80% on remaining classes would require testing boilerplate getters/setters | 60% on business logic packages with meaningful test scenarios is more valuable than 80% achieved by testing constructors and getter chains; the gate is a floor, not a ceiling |
| WireMock for all external service mocks (including PostgreSQL) | Consistent mocking approach across all external dependencies | WireMock cannot mock PostgreSQL's wire protocol; WireMock for HTTP dependencies (HuggingFace, Stripe) is appropriate but not sufficient for database testing | WireMock is the correct tool for HTTP service mocks; Testcontainers is the correct tool for database integration; the two are complementary, not competing |
| Postman + Newman as the primary test framework | Non-developer-friendly; rich request/response examples; CI integration via `newman run` | Postman collections drift from implementation without a code-generation step; coverage metrics are not available; mocking complex concurrent scenarios in Postman is infeasible | Postman Collections are documented as the E2E evidence artifact (layer 4) but are supplementary to JUnit; programmatic tests in JUnit provide coverage metrics, parametric test generation, and concurrent load simulation |

#### Consequences

**Positive:**
- Testcontainers integration tests catch a class of bug that unit tests with mocks structurally cannot: Flyway migration correctness, JSONB query operator behavior, `FOR UPDATE SKIP LOCKED` semantics under concurrent transactions, and `@Transactional` boundary correctness
- The CI pipeline's JaCoCo gate prevents regression in test coverage as new features are added; a PR that adds a new service method without tests will fail the coverage gate
- `@WebMvcTest` slices provide fast, focused HTTP contract tests (no DB required) that verify request deserialization, validation error responses, and HTTP status code mapping without starting the full application context
- Per-service CI jobs run in parallel, reducing total CI wall-clock time compared to a sequential test execution model

**Negative:**
- Testcontainers requires Docker to be running on the test host; this is satisfied by GitHub Actions runners (Linux-based) but may cause issues on developer machines with Docker Desktop license restrictions or resource constraints
- The CI matrix (five parallel jobs) consumes more GitHub Actions concurrent runner minutes than a single sequential job; on the free tier (2000 minutes/month), frequent pushes to active branches may approach the monthly limit
- Go-Runner has no unit or integration tests in the current scope; correctness of the parsing logic is validated only through manual dataset upload testing

**Neutral:**
- The JaCoCo exclusion list (`**/*Config*`, `**/*Application*`, `**/dto/**`, `**/entity/**`, `**/model/**`) ensures that coverage is measured on code that can fail in non-obvious ways; configuration classes and DTOs are implicitly tested through the service methods that use them
- `@MockitoSettings(strictness = Strictness.LENIENT)` is used in `ReviewWorkflowServiceTest` and `WorkerTaskServiceTest` to avoid unnecessary stub verification failures in tests that mock more dependencies than they exercise in a given scenario

---

### Implementation details

#### Project structure

services/
├── auth-service/src/test/java/com/datacrowd/auth/
│   ├── api/
│   │   ├── AuthControllerTest.java          # @WebMvcTest + @MockitoBean AuthService
│   │   └── AuthControllerWebTest.java       # @WebMvcTest + @AutoConfigureMockMvc(addFilters=false)
│   ├── integration/
│   │   └── AuthRepositoryIT.java            # @Testcontainers + @SpringBootTest (Flyway validation)
│   └── service/
│       └── AuthServiceTest.java             # Pure unit test (Mockito mocks only)
│
├── core-service/src/test/java/com/datacrowd/core/
│   ├── api/
│   │   └── AdminControllerTest.java         # Unit test for RoleChangeRequest validation
│   └── service/
│       ├── BillingServiceTest.java          # @ExtendWith(MockitoExtension.class)
│       ├── ExportServiceTest.java           # Forbidden/NotFound/Conflict scenarios
│       ├── PointsServiceTest.java           # Idempotent award logic
│       ├── ReviewWorkflowServiceTest.java   # approve/reject/trust score scenarios
│       ├── WorkerStatsServiceTest.java      # Trust level computation
│       └── WorkerTaskServiceTest.java       # Bot detection, trust gate, auto-approve
│
└── payments-service/src/test/java/com/datacrowd/payments/
├── api/
│   └── PaymentsControllerWebTest.java   # @WebMvcTest ping/success/cancel endpoints
└── service/
└── PaymentServiceTest.java          # Mock Stripe, markPaidMock idempotency


#### Key implementation decisions

Table 2.36. Key implementation decisions — Automated tests

| Decision | Rationale |
|---|---|
| `@Testcontainers` + `@DynamicPropertySource` in `AuthRepositoryIT` | `@DynamicPropertySource` injects the Testcontainers-assigned random port into the Spring datasource URL before the application context starts; this eliminates hardcoded port configurations in test properties and works correctly when multiple test classes run in parallel |
| `@WebMvcTest` with `@AutoConfigureMockMvc(addFilters = false)` for controller-layer tests | Disabling security filters in `@WebMvcTest` isolates the test to HTTP contract verification (status codes, JSON paths, request validation) without requiring JWT token generation; security filter behavior is tested separately at the integration test level |
| `@MockitoSettings(strictness = Strictness.LENIENT)` in workflow service tests | `ReviewWorkflowServiceTest` and `WorkerTaskServiceTest` mock 7–9 dependencies; not all stubs are exercised in every test scenario; `LENIENT` mode prevents spurious `UnnecessaryStubbingException` failures without reducing test coverage quality |
| Trust score boundary tests at exactly 30 (the block threshold) | The threshold value is the most failure-prone boundary in the anti-fraud subsystem; `nextTask_allowsAccess_whenTrustScoreExactlyAtThreshold()` verifies that 30 is inclusive (allowed), while `nextTask_throwsForbidden_whenTrustScoreTooLow()` verifies that 29 is exclusive (blocked) |
| `ReflectionTestUtils.setField(entity, "id", UUID.randomUUID())` in `PaymentServiceTest` | `PaymentEntity.id` is auto-generated by JPA and has no setter; `ReflectionTestUtils` injects the field value directly to simulate the state of a persisted entity without requiring a real database |

#### Code examples

```java
// AuthRepositoryIT.java — Testcontainers integration test
@Testcontainers
@SpringBootTest
class AuthRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auth_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.flyway.enabled",      () -> "true");
        r.add("app.jwt.secret", () -> "test-secret-key-minimum-32-characters-long!");
    }

    @Autowired JdbcTemplate jdbc;

    @Test
    void flywayApplied_usersTableExists() {
        Integer cnt = jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'users'",
            Integer.class);
        assertNotNull(cnt);
        assertTrue(cnt > 0, "users table should exist after Flyway migrations");
    }
}
```

```java
// WorkerTaskServiceTest.java — Trust Score boundary and bot detection tests
@Test
void nextTask_throwsForbidden_whenTrustScoreTooLow() {
    WorkerProfileEntity profile = new WorkerProfileEntity(workerId);
    profile.setTrustScore(20);   // below threshold of 30
    when(workerProfileRepository.findById(workerId)).thenReturn(Optional.of(profile));

    assertThatThrownBy(() -> workerTaskService.nextTask(workerId))
        .isInstanceOf(ApiForbiddenException.class)
        .hasMessageContaining("trust score");
}

@Test
void submit_throwsConflict_andDecreasesTrustScore_whenAnswerTooFast() {
    task.setLockedAt(Instant.now());      // 0 seconds elapsed
    project.setMinAnswerSeconds(5);       // minimum is 5 seconds
    when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
    when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    when(workerProfileRepository.findById(workerId)).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
        workerTaskService.submit(taskId, workerId, "{\"label\":\"positive\"}"))
        .isInstanceOf(ApiConflictException.class)
        .hasMessageContaining("too fast");

    verify(workerProfileRepository).save(profileCaptor.capture());
    assertThat(profileCaptor.getValue().getTrustScore()).isEqualTo(85); // 100 - 15
    verify(metricsService).incrementBotDetected();
}
```

```java
// ReviewWorkflowServiceTest.java — consensus mechanism and trust reward
@Test
void approve_finalizes_whenApprovalsReachRequired() {
    project.setReviewersCount(1);
    project.setRewardPoints(10);
    when(reviewRepository.countByAnswerIdAndDecision(answerId, ReviewDecision.APPROVED))
        .thenReturn(1L);  // this approval reaches the required count
    when(pointsService.awardTaskApprovedOnce(workerId, taskId, 10)).thenReturn(10);

    ReviewWorkflowService.DecisionResult result =
        reviewWorkflowService.approve(answerId, reviewerId, "good answer");

    assertThat(result.pointsAwarded()).isEqualTo(10);
    assertThat(result.task().getStatus()).isEqualTo(TaskStatus.APPROVED);
    verify(metricsService).incrementTasksApproved();
}
```

#### Diagram
![ER Diagram](../../assets/diagrams/AutomatedTests_Pyramid.png)

*Fig. 2.11. Automated tests — four-layer testing pyramid with execution characteristics*

---

### Requirements checklist

Table 2.37. Requirements checklist — Automated tests

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | Business logic must be covered by unit tests that verify both happy paths and negative/edge cases | ✅ | 7 unit test classes across `auth-service`, `core-service`, `payments-service`; negative cases: wrong password, duplicate email, too-fast submission, insufficient trust score, self-review forbidden, duplicate payment idempotency |
| 2 | Integration tests must run against a real PostgreSQL 16 instance, not an in-memory substitute | ✅ | `AuthRepositoryIT` uses `PostgreSQLContainer("postgres:16-alpine")` via Testcontainers; `@DynamicPropertySource` injects container URL |
| 3 | Test coverage must be enforced as a CI quality gate with a defined minimum threshold | ✅ | JaCoCo Maven Plugin 0.8.12 configured in all four Java `pom.xml` files; minimum 60% instruction coverage; build fails on gate violation; `check` goal runs in `verify` phase |
| 4 | Controller-layer HTTP contracts must be tested independently of the database layer | ✅ | `AuthControllerTest`, `AuthControllerWebTest`, `PaymentsControllerWebTest` use `@WebMvcTest` with `@AutoConfigureMockMvc(addFilters=false)` and `@MockitoBean` service classes |
| 5 | The CI pipeline must execute all tests automatically on every push to protected branches | ✅ | `.github/workflows/ci.yml` runs `mvn -B verify` for each Java service with a dedicated PostgreSQL service container; Go `go build` and `go vet` for the Runner |

### Known limitations

Table 2.38. Known limitations — Automated tests

| Limitation | Impact | Potential Solution |
|---|---|---|
| The Go-Runner has no automated tests; parsing correctness is validated only through manual integration testing during the diploma demonstration | Any regression in CSV header parsing, JSONL line handling, ZIP manifest extraction, or batch flush logic will not be caught by CI | Add Go unit tests for `parseCSV`, `parseJSONL`, and `extractZip` functions using `testing` package table-driven tests; add an integration test using an HTTP test server (`httptest.NewServer`) to mock Core's `/internal/tasks/bulk` endpoint and verify that the correct number of tasks is created for a given input file |
| The `WorkerTaskServiceTest` and `ReviewWorkflowServiceTest` mock the `WorkerStatsService.evictStats()` call but do not verify that cache eviction actually invalidates the cached worker stats in a real Caffeine cache instance | If the cache eviction logic is changed (e.g., the cache name is renamed), the unit tests continue to pass while the real system serves stale worker stats after review verdicts | Add a `@SpringBootTest` integration test class that populates the Caffeine cache via `getStats()`, triggers a review verdict (approve or reject), and then calls `getStats()` again to verify the returned value reflects the updated Trust Score |