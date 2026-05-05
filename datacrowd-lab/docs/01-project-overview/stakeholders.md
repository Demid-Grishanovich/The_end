## 1.2 Stakeholders and Users

Table 1.6. Target Audience

| Persona | Description | Key Needs |
|---|---|---|
| ML Engineer / Client | A technical professional at a startup or research institution who owns the annotation project, defines its parameters, uploads raw data, and consumes the verified output | Fast dataset turnaround, cost control, data format compatibility with training pipelines, full audit trail |
| Crowd Annotator / Worker | A semi-technical or non-technical individual who performs atomic labeling tasks in exchange for reward points or monetary compensation | Clear task instructions, responsive UI, transparent performance feedback, fair point accounting |
| QA Engineer / Reviewer | A domain expert or senior annotator who validates worker submissions and enforces label quality through approve/reject decisions | Efficient review queue, clear presentation of both source data and worker answer, audit documentation |
| Platform Administrator | An internal operator responsible for user management, system monitoring, and global configuration | Role management tooling, system health visibility, access to audit logs |

---

Table 1.7. User Persona — Client

| Attribute | Detail |
|---|---|
| Role | ML Engineer / Project Client |
| Name (invented) | Anastasia Veretnikova |
| Age | 31 |
| Occupation | ML Engineer at a Series A NLP startup (12 employees) |
| Tech Savviness | High — comfortable with REST APIs, Python, Docker, and cloud storage; not a backend engineer |
| Goals | Upload a 50,000-sentence sentiment dataset, receive a verified JSONL export within one week, integrate directly into a fine-tuning pipeline without reformatting |
| Frustrations | Previously spent 18 hours manually deduplicating annotations from a shared Google Sheet; had two annotators label the same 800 records; discovered the error only during model evaluation |
| Scenario | Anastasia creates a project with dataType=TEXT, reviewersCount=2, rewardPoints=15, minAnswerSeconds=4. She uploads a CSV file, funds the project via mock Stripe, and monitors progress through the SSE-driven project detail page. She exports the verified dataset as JSONL and pipes it directly into a Hugging Face Trainer script. |

---

Table 1.8. User Persona — Worker

| Attribute | Detail |
|---|---|
| Role | Crowd Annotator / Worker |
| Name (invented) | Tobias Schreiber |
| Age | 24 |
| Occupation | Graduate student in linguistics; part-time crowd annotator |
| Tech Savviness | Medium — comfortable with web applications; no programming background; uses the platform via browser only |
| Goals | Complete as many tasks as possible per session to accumulate points; maintain a high Trust Score to ensure continued task access; understand clearly what each task requires |
| Frustrations | On a previous platform, his account was suspended without explanation after he answered a trick question; he received no feedback on why his answers were rejected |
| Scenario | Tobias logs in, navigates to the worker dashboard, selects the "Sentiment Analysis" project, and clicks "Get Next Task". The task presents a product review and asks him to select a sentiment label. He reads the task for 8 seconds (above minAnswerSeconds=4), selects "negative", and submits. His Trust Score is displayed in real time. When a reviewer later approves his answer, 15 points are credited and his accuracy metric updates. |

---

Table 1.9. User Persona — Reviewer

| Attribute | Detail |
|---|---|
| Role | QA Engineer / Reviewer |
| Name (invented) | Mariam Dzebniauri |
| Age | 34 |
| Occupation | Senior data scientist at a research institute; part-time reviewer on the platform |
| Tech Savviness | High — experienced with data quality methodologies, familiar with REST APIs, uses both the UI and direct API calls via Postman |
| Goals | Efficiently process the review queue during dedicated 1-hour sessions; ensure that only linguistically accurate sentiment labels reach the verified dataset; provide brief comments on rejected answers to help workers improve |
| Frustrations | On previous annotation platforms, the review interface presented answers without the original source text, forcing reviewers to navigate to a separate screen; the queue had no priority ordering |
| Scenario | Mariam navigates to the reviewer tab and clicks "Load Next Answer". The interface displays the original product review alongside Tobias's submitted label. She evaluates the answer as correct and clicks "Approve". The system records the review, increments the approval count for that answer, and — since reviewersCount=2 and this is the second approval — transitions the answer to APPROVED and the task to APPROVED, crediting Tobias's points automatically. Mariam moves to the next answer without any additional navigation. |

---

### Stakeholder Map

**High Influence / High Interest (Primary Stakeholders):**
- Client (ML Engineer) — directly funds the project and consumes the output
- Worker (Crowd Annotator) — directly produces the annotations; system quality depends on their effort
- Reviewer (QA Engineer) — directly determines which annotations enter the verified dataset

**High Influence / Low Interest (Secondary Stakeholders):**
- Platform Administrator — configures user roles and monitors system health; not involved in day-to-day annotation
- University Evaluation Committee — determines the academic grade; interested in architectural correctness and evaluation criteria coverage

**Low Influence / High Interest (Peripheral Stakeholders):**
- End beneficiaries of downstream ML models trained on exported datasets (e.g., users of NLP applications)
- Open-source community potentially reusing the platform architecture as a reference implementation

**Low Influence / Low Interest (Monitoring Stakeholders):**
- Payment processor (Stripe) — provides billing infrastructure but has no interest in annotation outcomes
- Cloud infrastructure providers (MinIO, Docker Hub) — supply commodity services