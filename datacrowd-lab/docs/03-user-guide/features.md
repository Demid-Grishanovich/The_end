## 3.2 Feature walkthrough

This section describes the three primary workflows in DataCrowd Lab from the perspective of each user role. Each walkthrough covers the complete journey from login to the expected outcome.

---

### Feature 1: Client happy path — create project, upload dataset, fund, and export

#### Overview

The Client workflow covers the full annotation lifecycle from the perspective of a data owner. A Client creates a project with labeling parameters, uploads a raw dataset file, funds the project to enable worker access, monitors progress in real time, and exports the verified annotations as a structured file ready for ML pipeline ingestion.

#### How to use

![Screenshot](../assets/images/feature-1.png)

*Fig. 3.1. Client dashboard showing active projects with progress indicators*

**Step 1: Create a project**
Navigate to `http://localhost:80`, log in as a Client, and click **+ Create New Project** on the dashboard. Fill in the project name, select the data type (TEXT, IMAGE, AUDIO, CODE, or MATH), set the reward points per approved task, configure the number of reviewers per task using the slider, and enter the minimum answer time in seconds. Click **Launch Project**. The project is created with `billingStatus=UNPAID` and a task quota of zero.

**Step 2: Upload a dataset**
On the Create Project page, drag and drop a CSV or JSONL file onto the upload zone, or click the zone to open a file picker. The file name and size are displayed after selection. Click **Launch Project** — the file is uploaded to the server (MinIO or local volume) as part of the same action. A `datasetId` is stored in `localStorage` for the subsequent payment flow.

**Step 3: Fund the project**
After project creation, the user is redirected to the Payment page. The project summary and pricing are displayed. Click **Pay with Stripe** to initiate a Stripe Checkout session. In the local development environment with `STRIPE_ENABLED=false`, the button navigates to the mock payment URL (`/api/payments/mock/pay/{paymentId}`), which immediately marks the project as `PAID` and grants the configured task quota. After payment, the system automatically triggers task generation.

**Step 4: Monitor progress**
After payment, the Project Detail page displays a progress bar and task counters that refresh every 4 seconds. The Go-Runner parses the uploaded file asynchronously; the dataset status transitions `UPLOADED → GENERATING → READY`. Task counts update as Workers complete and Reviewers approve answers.

**Step 5: Export results**
Once sufficient tasks are approved, click **Export JSONL** or **Export CSV** on the Project Detail page. The system generates an export file containing all verified (APPROVED) answers and initiates a browser download. JSONL exports contain one JSON object per answer; CSV exports use dataType-specific column schemas (e.g., `taskId,workerId,text,label,confidence` for TEXT projects).

**Expected result:** A structured JSONL or CSV file is downloaded to the client's machine, containing all answers that have reached the consensus threshold, with each answer traceable to its task ID and worker ID.

**Tips:**
- Use the Data Type Format Guide at `GET /api/core/projects/data-type-guides` to preview the expected input CSV format and output JSON schema before uploading
- If the task count remains 0 after payment, check the Dataset Status card on the Project Detail page; `FAILED` status means the Go-Runner encountered parse errors — inspect `http://localhost:5050` (pgAdmin) → `core_db` → `failed_items` for row-level error messages
- Set `reviewersCount=0` to enable auto-approval (no reviewer required); this is useful for quick demonstrations
- The progress page auto-refreshes; there is no need to manually reload the browser tab

---

### Feature 2: Worker happy path — browse projects, claim task, and submit answer

#### Overview

The Worker workflow covers the annotation experience from the perspective of a crowd contributor. A Worker browses available funded projects, selects one, claims the next available task from the queue, reads the task content within the required time window, submits a labeled answer, and monitors their Trust Score and accumulated points on the performance dashboard.

#### How to use

![Screenshot](../assets/images/feature-2.png)

*Fig. 3.2. Worker task execution interface with content panel, timer bar, and answer input*

**Step 1: Browse available projects**
Log in as a Worker and navigate to `http://localhost:80/worker.html`. The **Do Tasks** tab displays all projects with `billingStatus=PAID` that have at least one available task. Each project card shows the data type, number of reviewers, reward points per task, and available task count. Click **Start Working** on the desired project.

**Step 2: Claim the next task**
On the Task Execution page (`worker-task.html`), click **Get Next Task**. The system calls `GET /api/core/tasks/next?projectId={id}` and then `POST /api/core/tasks/{id}/lock` to atomically assign the task to the Worker. The task content is displayed in the content panel. If no tasks are available, a message is shown; the Worker may check back later or choose a different project.

**Step 3: Read the task and observe the timer**
The timer bar begins filling from the moment the task is locked. The task content is displayed in the content panel — this may be a text sentence, an image, an audio clip, or a code snippet depending on the project's data type. If the project has ML pre-annotation enabled, an AI suggestion banner appears showing `aiSuggestedLabel` and a confidence percentage. Click **Use this** to populate the answer field with the suggested label, or type a custom answer.

**Step 4: Submit the answer**
After the timer has exceeded `minAnswerSeconds`, type or confirm the answer in the text area. The **Submit Answer** button becomes enabled as soon as text is entered. Click **Submit Answer**. The system validates the response time, checks for honeypot conditions, saves the answer, and transitions the task to `IN_REVIEW` (or `APPROVED` if `reviewersCount=0`). The points awarded are displayed in the confirmation screen.

**Step 5: View performance statistics**
Click **My Stats** in the navigation bar to open the statistics page (`stats.html`). The Trust Score is displayed as an animated circular gauge with color coding (green/yellow/orange/red). Completed tasks, rejected tasks, total points, and the accuracy rate are shown below.

**Expected result:** The submitted answer is persisted with `status=SUBMITTED`, the task transitions to `IN_REVIEW`, and the Worker's session displays a success screen with the points awarded (0 for `reviewersCount>0` cases, since points are awarded only after reviewer approval).

**Tips:**
- If the **Submit Answer** button is greyed out and the system returns a 409 error, the answer was submitted too quickly; wait for the timer bar to fill before submitting
- If `GET /api/core/tasks/next` returns 403, the Worker's Trust Score is below 30 (BLOCKED level); check the Stats page and contact the platform administrator
- Click **Release Task** at any time to return the task to the queue without penalty; this is useful when the task content is unclear or requires domain expertise the Worker does not have
- The AI suggestion banner is a hint, not a requirement; Workers are encouraged to override incorrect suggestions

---

### Feature 3: Reviewer happy path — load answer, evaluate, and record decision

#### Overview

The Reviewer workflow covers the quality assurance experience. A Reviewer pulls the next submitted answer from the shared review queue, evaluates it against the original task content, records an approve or reject decision with an optional comment, and proceeds to the next answer. The Reviewer's decisions drive the consensus mechanism that determines whether an answer is finalized and whether a Worker earns points.

#### How to use

![Screenshot](../assets/images/feature-3.png)

*Fig. 3.3. Reviewer interface showing original task content alongside the worker's submitted answer*

**Step 1: Access the review queue**
Log in as a Reviewer. Navigate to `http://localhost:80/worker.html` and click the **Review Answers** tab, then click **Find Answer to Review**. Alternatively, navigate directly to `http://localhost:80/review.html`. Click **Load Next Answer** to fetch the next pending submission from the queue.

**Step 2: Evaluate the answer**
The review interface displays two panels side by side: the original task content (left) and the Worker's submitted answer (right). For IMAGE and AUDIO tasks, the binary asset is loaded via an authenticated fetch call to `GET /api/core/tasks/{id}/asset`. Read the task content, evaluate the submitted answer for correctness and completeness, and optionally type a comment in the **Review Comment** field.

**Step 3: Record the decision**
Click **Approve** (green button) if the answer is correct and meets quality standards, or **Reject** (red button) if it does not. The system records a `ReviewEntity` and updates the approval count. If the total number of approvals for this answer reaches the project's `reviewersCount` threshold, the answer transitions to `APPROVED`, the task is finalized, and the Worker's points are credited automatically. If the answer is rejected, the task returns to `NEW` status for reassignment, and the Worker's Trust Score decreases by 10.

**Step 4: Continue or skip**
After each decision, the system returns to the review screen. Click **Next Answer** to load the next pending submission immediately. Click **Skip** to pass on the current answer without making a decision (no penalty; answer remains in the queue for other reviewers).

**Expected result:** The review decision is persisted in the `reviews` table, the approval count for the answer is incremented, and — if the consensus threshold is reached — the answer is finalized, the Worker is credited, and the project's progress percentage increases.

**Tips:**
- A Reviewer cannot review their own submitted answers; the queue automatically excludes answers whose `user_id` matches the Reviewer's authenticated `userId`
- A Reviewer can review each answer at most once; attempting to review the same answer twice returns a 409 Conflict error
- If the review queue is empty (204 No Content), all submitted answers have already been reviewed by the required number of reviewers; check back later as new Worker submissions arrive
- Leave a comment when rejecting to provide constructive feedback; Workers can see rejection comments in future versions of the platform (currently stored in the database for audit purposes)

---

### Keyboard shortcuts and navigation

Table 3.4. Keyboard shortcuts

| Shortcut | Action | Available On |
|---|---|---|
| `Tab` | Move focus to the next interactive element (input, button, link) | All pages |
| `Shift + Tab` | Move focus to the previous interactive element | All pages |
| `Enter` | Activate the focused button or submit the focused form | Login page, Registration page, Task submission |
| `Space` | Activate the focused button (alternative to Enter) | All pages |
| `Escape` | Close modal dialogs (browser default) | Payment page (Stripe modal) |
| `Ctrl + R` / `Cmd + R` | Manual page refresh (reloads current state from server) | All pages |
| Arrow keys | Navigate range sliders (Reviewers Count, Minimum Answer Time) | Create Project page |
| `Tab` to file input + `Enter` | Open file picker for dataset upload | Create Project page |

> Note: The DataCrowd Lab frontend is a mouse-first interface. Full keyboard-only navigation is functional via standard browser Tab/Enter/Space semantics but has not been optimized for keyboard-power users in the current version. Screen reader compatibility relies on semantic HTML5 elements and explicit `label` associations.

---

### Feature comparison by role

Table 3.5. Feature comparison by role

| Feature | Client | Worker | Reviewer |
|---|---|---|---|
| Create and configure annotation projects | ✅ | ❌ | ❌ |
| Upload dataset files (CSV, JSONL, ZIP) | ✅ | ❌ | ❌ |
| Fund project via Stripe / mock payment | ✅ | ❌ | ❌ |
| View own projects and progress | ✅ | ❌ | ❌ |
| Browse available funded projects | ❌ | ✅ | ❌ |
| Claim and lock annotation tasks | ❌ | ✅ | ❌ |
| Submit labeled answers | ❌ | ✅ | ❌ |
| Release task back to queue | ❌ | ✅ | ❌ |
| View personal Trust Score and statistics | ❌ | ✅ | ✅ |
| Load next answer from review queue | ❌ | ❌ | ✅ |
| Approve or reject submitted answers | ❌ | ❌ | ✅ |
| Leave review comments on answers | ❌ | ❌ | ✅ |
| Export verified dataset (JSONL / CSV) | ✅ | ❌ | ❌ |
| View real-time project progress (SSE) | ✅ | ❌ | ❌ |
| Earn annotation reward points | ❌ | ✅ | ❌ |
| Register new users (self-service) | ✅ (with admin key) | ✅ | ❌ (admin-promoted) |
| Access Swagger UI documentation | ✅ | ✅ | ✅ |
| Manage user roles (promote/demote) | ❌ | ❌ | ❌ (Admin only) |