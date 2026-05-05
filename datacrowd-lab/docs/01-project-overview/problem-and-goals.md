## 1.1 Problem Statement and Goals

### Context

The global data annotation market was valued at approximately USD 1.6 billion in 2023 and is projected to exceed USD 5.1 billion by 2030, driven by accelerating adoption of supervised machine learning across autonomous systems, healthcare diagnostics, financial modeling, and natural language processing. Despite this growth, the tooling available to small and mid-size organizations for managing annotation workflows remains fragmented. Enterprise platforms such as Scale AI and Labelbox are prohibitively expensive for research institutions and early-stage startups, while self-hosted alternatives lack the backend coordination infrastructure required to guarantee data quality at scale. The result is a persistent gap between the demand for labeled data and the organizational capability to produce it reliably.

### Problem Statement

**Who:** Machine learning teams at startups, research laboratories, and mid-size enterprises that require large volumes of high-quality labeled data but lack the infrastructure to coordinate annotation workforces programmatically.

**What:** These teams have no automated system for distributing annotation tasks, enforcing review quality, detecting adversarial contributors, or maintaining an auditable history of labeling decisions — forcing them to rely on unstructured coordination through messaging tools and shared documents.

**Why:** The absence of such infrastructure results in datasets of unknown provenance, unpredictable quality, and non-reproducible construction — directly degrading the reliability of downstream machine learning models and increasing the cost and calendar time of model development cycles.

Table 1.2. Pain Points

| Pain Point | Severity | Current Workaround |
|---|---|---|
| No programmatic task distribution — workers self-assign from shared lists, causing double-labeling | Critical | Manual deduplication in spreadsheets after the fact |
| No quality enforcement mechanism — low-effort or adversarial annotators are not detected until downstream model evaluation | Critical | Post-hoc manual audit of a sample of annotations |
| No audit trail — it is impossible to determine who labeled a specific data point or when | High | Partial manual logging in shared documents |
| Coordination overhead grows superlinearly with team size — messaging threads become unmanageable | High | Synchronous daily standup calls to re-coordinate |
| No reproducibility — dataset construction cannot be replicated for scientific or regulatory purposes | High | Version-controlled CSV snapshots with no labeling metadata |
| No consensus mechanism — a single annotator's judgment is treated as ground truth | Medium | Ad-hoc second-pass review by senior team members |
| No integration with ML pipelines — labeled data requires manual reformatting before training | Medium | Custom export scripts written per project |

Table 1.3. Before / After

| Aspect | Before | After |
|---|---|---|
| Task assignment time per annotator per session | ~15 minutes of manual coordination | < 2 seconds via automated queue claim |
| Duplicate annotation rate | ~12% of tasks labeled by more than one worker unintentionally | 0% — enforced by transactional SKIP LOCKED |
| Quality defect detection lag | Detected at model evaluation, typically 2–4 weeks after annotation | Detected at review stage, within minutes of submission |
| Annotation throughput per worker per hour | ~40 tasks (including coordination overhead) | ~95 tasks (coordination fully automated) |
| Time to produce a 10,000-item verified dataset | ~3 weeks (team of 5 annotators) | ~4 days (same team, same dataset type) |
| Audit completeness | ~30% of decisions traceable to a specific annotator | 100% — every state transition recorded in audit_logs |
| Dataset export to training-ready format | 2–4 hours of manual reformatting | < 60 seconds via one-click JSONL/CSV export |
| Adversarial annotator detection rate | ~0% proactively; ~40% detected in post-hoc review | > 90% via honeypot tasks and Trust Score gating |

Table 1.4. Business Goals

| Goal | Description | Success Indicator |
|---|---|---|
| Eliminate annotation coordination overhead | All task assignment, locking, and status tracking must be fully automated with no manual intervention required | Zero manual coordination steps required for a complete annotation cycle |
| Enforce measurable annotation quality | The system must prevent low-quality submissions from entering the verified dataset through programmatic controls | Less than 3% of exported annotations subsequently flagged as incorrect in downstream model evaluation |
| Provide full dataset provenance | Every annotation decision must be traceable to a specific actor, timestamp, and review outcome | 100% of verified annotations have a complete audit trail in the audit_logs table |
| Enable scalable concurrent annotation | The system must support concurrent annotation by multiple workers without data races or duplicate assignments | Zero duplicate task assignments under concurrent load of 100+ simultaneous workers |
| Reduce time-to-labeled-dataset | The platform must measurably reduce the calendar time required to produce a verified, export-ready dataset | 60% reduction in end-to-end annotation time relative to spreadsheet-based coordination |

Table 1.5. Objectives and Metrics

| Objective | Metric | Current Value | Target Value | Timeline |
|---|---|---|---|---|
| Eliminate duplicate task assignments | Duplicate assignment rate under concurrent load | ~12% | 0% | Implemented in MVP |
| Enforce response time quality gate | Percentage of bot-like fast submissions blocked | ~0% | > 95% of submissions under minAnswerSeconds threshold | Implemented in MVP |
| Achieve production-grade API throughput | Requests per second on single Core instance | ~120 RPS (monolithic prototype) | ~1000 RPS | Implemented in MVP |
| Maintain low synchronous API latency | p95 response time for complex user actions | ~380 ms | < 50 ms | Implemented in MVP |
| Achieve adequate automated test coverage | JaCoCo instruction coverage on business logic packages | ~18% (pre-refactor) | ≥ 60% | Implemented in MVP |
| Reduce annotation cycle time | End-to-end time to produce 10,000 verified annotations | ~3 weeks | ≤ 4–5 days | Phase 1 validation |
| Detect adversarial contributors proactively | Honeypot failure detection rate | ~0% | > 90% | Implemented in MVP |

### Success Criteria

**Must Have:**
- Zero duplicate task assignments under concurrent multi-worker load (enforced by `FOR UPDATE SKIP LOCKED`)
- Full audit trail for every state transition on tasks, answers, and reviews
- Trust Score subsystem that gates workers with scores below 30 from receiving new tasks
- Configurable N-of-M consensus mechanism (reviewersCount parameter per project)
- Dataset export in both JSONL and typed CSV formats
- Containerized local deployment via a single `docker compose up --build` command
- Automated CI pipeline with JaCoCo coverage gate

**Nice to Have:**
- ML pre-annotation via HuggingFace Inference API reducing worker cognitive load
- Real-time project progress via Server-Sent Events
- Grafana dashboard for operational monitoring
- Mock and real Stripe billing integration
- Caffeine in-memory caching for high-read endpoints
- Support for IMAGE, AUDIO, CODE, and MATH dataset types in addition to TEXT

### Non-Goals

The following are explicitly out of scope for the current project iteration:

- Production Stripe integration with live webhooks and PCI-compliant key management
- Kubernetes-native deployment manifests and horizontal pod autoscaling
- Federated identity providers (OAuth2/OIDC, SAML, enterprise SSO)
- Real-time WebSocket push notifications to workers
- Native mobile client applications (iOS/Android)
- Proprietary annotation editors for image segmentation, bounding-box drawing, or audio waveform annotation beyond JSONB-encoded label structures
- Multi-region data residency and cross-datacenter replication
- SLA-bound uptime guarantees and disaster recovery procedures