# CONCLUSION

DataCrowd Lab demonstrates that the structural problems of crowdsourced dataset annotation — cost asymmetry, absent provenance, and silent quality degradation — are fundamentally engineering problems, amenable to architectural solutions. The platform's central thesis, that **data should be coordinated, not people**, is validated by every principal design decision: the finite-state machine over task and answer lifecycles prevents invalid transitions without human supervision; the database-per-service isolation model eliminates cross-service state corruption without coordination protocols; and the FOR UPDATE SKIP LOCKED concurrency primitive resolves worker contention at the PostgreSQL layer rather than at the application layer, reducing task-claim latency from over 200 ms under optimistic locking to under 10 ms. The result is a system in which correctness is an emergent property of the architecture, not of workforce compliance.

All seven evaluation criteria established at the outset have been met. User experience and gamification are addressed through role-isolated dashboards, a real-time Trust Score gauge, and SSE-driven progress feeds. The backend stack (Java 17/Spring Boot 3.4 for transactional services, Go 1.22 for streaming file processing) demonstrates deliberate, workload-driven technology selection. The PostgreSQL data model enforces domain invariants at the schema level through UNIQUE constraints, status enumerations, and Flyway-versioned migrations. The microservices decomposition produces independently deployable units with clear failure domains. Docker Compose delivers binary-reproducible deployment. OpenAPI/Swagger documentation is annotation-driven, eliminating specification drift. The four-layer test pyramid, enforced by a JaCoCo ≥60 % coverage gate in GitHub Actions CI, ensures that the most critical concurrent behaviors — SKIP LOCKED, honeypot detection, Trust Score gating — are verified against real PostgreSQL instances via Testcontainers.

The platform is production-ready in its current form for medium-scale annotation workloads. The documented technical debt items — Kubernetes migration, live Stripe webhook integration, Go-Runner horizontal autoscaling, and message-broker decoupling — represent a clear, sequenced roadmap toward enterprise deployment. Each item is a refinement, not a correction: the existing contracts, data models, and service boundaries already accommodate these evolutions without breaking changes. The project therefore delivers not only a functioning annotation platform but a reference architecture for database-centric, state-machine-governed microservices at the intersection of MLOps and crowdsourcing.

---

# REFERENCES

Kleppmann, Martin. *Designing Data-Intensive Applications: The Big Ideas Behind Reliable, Scalable, and Maintainable Systems*. Sebastopol, CA: O'Reilly Media, 2017.

Monarch, Robert (Munro). *Human-in-the-Loop Machine Learning: Active Learning and Annotation for Human-Centered AI*. Shelter Island, NY: Manning Publications, 2021.

PostgreSQL Global Development Group. "Explicit Locking — FOR UPDATE / FOR SHARE / SKIP LOCKED." In *PostgreSQL 16 Documentation*, §13.3.2. Accessed April 2026. https://www.postgresql.org/docs/16/explicit-locking.html.

VMware, Inc. *Spring Boot Reference Documentation, Version 3.4.1*. Accessed April 2026. https://docs.spring.io/spring-boot/docs/3.4.1/reference/html/.

Stripe, Inc. *Stripe API Reference: Checkout Sessions*. Accessed April 2026. https://stripe.com/docs/api/checkout/sessions.

Flyway Team. *Flyway Documentation: Versioned Migrations*. Redgate Software. Accessed April 2026. https://documentation.red-gate.com/fd/versioned-migrations-184127470.html.

Sigelman, Ben, et al. "Dapper, a Large-Scale Distributed Systems Tracing Infrastructure." Google Technical Report, 2010. https://research.google/pubs/pub36356/.