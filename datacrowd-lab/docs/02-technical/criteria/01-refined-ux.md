## Criterion: Refined UX

### Architecture Decision Record

**Status:** Accepted
**Date:** 2024-12-01

#### Context

Crowdsourced annotation platforms face a structural retention problem: workers performing repetitive labeling tasks disengage rapidly when feedback on their performance is delayed, invisible, or delivered in aggregate rather than in real time. Disengaged workers produce lower-quality annotations, which corrupts the downstream dataset and degrades model performance in ways that are only detectable weeks after the labeling event. The platform must therefore provide a user experience that makes worker performance feedback continuous, transparent, and immediately actionable after every interaction. At the same time, the three user roles — Client, Worker, and Reviewer — have fundamentally different information needs and access rights. A unified interface for all three would either expose privileged data to unprivileged actors or clutter the experience of each role with irrelevant controls.

#### Decision

Implement three completely isolated frontend surfaces — a Client dashboard, a Worker dashboard, and a Reviewer queue — served as static HTML/CSS/JavaScript pages by an `nginx:alpine` container. Role-based routing is performed client-side by reading the `role` field from the JWT stored in `localStorage` after login. The Worker dashboard exposes a real-time Trust Score circular gauge, an accuracy ratio, a points balance, and completed/rejected task counts — all sourced from a single cached API endpoint (`GET /api/core/workers/me/stats`) and updated after every reviewer verdict. Project progress is streamed to the Client dashboard via Server-Sent Events (SSE). The task labeling interface enforces a visible countdown timer tied to `minAnswerSeconds` to provide workers with real-time feedback on whether their response time is acceptable.

#### Alternatives considered

Table 2.11. Alternatives considered — Refined UX

| Alternative | Pros | Cons | Why Not Chosen |
|---|---|---|---|
| Single unified React SPA with role-based conditional rendering | Single codebase; shared component library; standard approach in modern frontends | Requires a Node.js build toolchain (Webpack/Vite), increasing CI complexity; a rendering bug could expose a privileged component to the wrong role; bundle size grows with all three role surfaces | The diploma project prioritises zero-build-step simplicity; role isolation via conditional rendering is weaker than physical page separation; adding a bundler would have shifted CI effort away from backend quality |
| Server-side rendered pages (Spring MVC Thymeleaf templates) | No separate frontend service required; templates are rendered server-side, eliminating client-side routing risks; simpler session management | Tight coupling between backend and frontend codebases; any UI change requires a backend redeployment; harder to demonstrate REST API independently via Swagger UI | The project's architecture calls for a clean separation between the REST API (demonstrated via Swagger) and the consuming frontend; server-side rendering would obscure the API layer from evaluators |
| WebSocket-based real-time dashboard updates | Lower latency than polling or SSE for high-frequency events; bidirectional communication enables server-initiated push without client polling | Requires a stateful connection manager on the server; conflicts with the stateless design principle; WebSocket infrastructure adds complexity not justified by annotation feedback latency requirements | SSE is sufficient for the 2-second progress update interval required by the use case; SSE is unidirectional and stateless from the server perspective, consistent with the project's architectural constraints |
| Third-party UI component library (e.g., Material UI, Tailwind CSS CDN) | Faster development of visually polished components; accessibility baked in | CDN dependency introduces availability risk; external CSS frameworks can conflict with custom styles; adds external HTTP requests on page load | Custom CSS variables and utility classes were written once and reused across all pages, providing full control over the visual language without external dependencies |

#### Consequences

**Positive:**
- Complete information isolation between roles is enforced at the physical page level, not merely through conditional rendering, eliminating the risk of privilege leakage through UI bugs
- The Trust Score gauge provides workers with an immediate, unambiguous signal of their standing, converting an internal anti-fraud metric into a motivational feedback loop
- The SSE-based progress stream eliminates the need for client-side polling intervals on the project detail page, reducing unnecessary API load while providing smoother visual progress updates
- Zero build toolchain dependency means the frontend is deployable by simply copying HTML files into an `nginx` container with no npm, Webpack, or Vite step required

**Negative:**
- Three separate HTML files sharing similar navigation and utility JavaScript functions introduce code duplication that would be eliminated by a component-based framework
- `localStorage`-based JWT storage is vulnerable to XSS attacks; a production-grade frontend should use `HttpOnly` cookies for token storage
- The absence of a frontend test framework (e.g., Playwright, Cypress) means UI correctness is validated only through manual testing and cannot be gated in CI

**Neutral:**
- Client-side role routing via `localStorage.role` means a user can manually navigate to a page for a different role; this is acceptable because all data-returning API calls are authenticated and role-verified server-side
- The visual design uses a custom CSS variable system (`--p`, `--ok`, `--err`, `--ts`) that is functional but would benefit from a formal design system document in a team environment

---

### Implementation details

#### Project structure
services/frontend/
├── Dockerfile                  # FROM nginx:alpine; COPY . /usr/share/nginx/html
├── nginx.conf                  # client_max_body_size 100M; try_files for SPA-style routing
├── style.css                   # CSS variables, component classes (card, btn, badge, pbar, spin)
├── index.html                  # Login page → routes to dashboard.html or worker.html by role
├── register.html               # Registration with role selector (WORKER / CLIENT + admin key)
├── dashboard.html              # Client dashboard: project grid, active/completed sections
├── create-project.html         # Project creation form with data type, quality control sliders
├── project-detail.html         # SSE progress stream, dataset status, export buttons
├── payment.html                # Stripe / mock checkout page with project summary
├── worker.html                 # Worker hub: "Do Tasks" tab + "Review Answers" tab
├── worker-task.html            # Task execution: timer, content display, answer input, AI hint
├── review.html                 # Reviewer queue: side-by-side source + answer, approve/reject
└── stats.html                  # Worker performance: Trust Score gauge, accuracy bar, guide

#### Key implementation decisions

Table 2.12. Key implementation decisions — Refined UX

| Decision | Rationale |
|---|---|
| Trust Score rendered as an animated SVG circular gauge with stroke-dashoffset animation | A numeric value alone does not communicate urgency; a partially filled ring provides immediate spatial intuition of standing; the animation (1 s ease transition on `stroke-dashoffset`) draws the worker's attention to changes without being disruptive |
| Trust Score color mapped to four semantic levels (HIGH=green, MEDIUM=yellow, LOW=orange, BLOCKED=red) | Color coding must be supplemented by text labels to satisfy accessibility requirements; the four-level scheme maps directly to the `trustLevel` field returned by the API, requiring no client-side computation |
| Task timer bar tied to `minAnswerSeconds` with visual fill animation | Workers need to know when their answer will be accepted; a visible progress bar tied to the server-enforced minimum time communicates the rule before submission is attempted, reducing 409 rejections and trust score penalties |
| SSE stream (`EventSource`) for project progress, polling fallback with `setInterval` for dashboard | SSE provides push-based updates without repeated HTTP overhead; `setInterval` at 6-second intervals on the main dashboard provides a reasonable fallback for contexts where SSE connections are dropped by proxies |
| AI suggestion banner with one-click "Use this" affordance | Pre-annotation reduces worker cognitive load; displaying `aiSuggestedLabel` and `aiConfidence` prominently with a low-friction adoption path increases the efficiency gain from ML pre-annotation without forcing the worker to accept the suggestion |

#### Code examples

```javascript
// stats.html — Trust Score SVG gauge animation
const CIRC = 2 * Math.PI * 66; // circumference of r=66 circle

async function load() {
  const r = await fetch(`${API}/core/workers/me/stats`, {
    headers: { 'Authorization': 'Bearer ' + tok }
  });
  const s = await r.json();
  const score = s.trustScore ?? 100;
  const level = s.trustLevel || 'HIGH';

  const offset = CIRC - (score / 100) * CIRC;
  const ring = document.getElementById('ring');
  ring.style.strokeDashoffset = offset;  // animates via CSS transition: stroke-dashoffset 1s ease
  ring.style.stroke = COLS[level];       // COLS = {HIGH:'#16a34a', MEDIUM:'#ca8a04', ...}

  document.getElementById('scoreNum').textContent = score;
  document.getElementById('scoreLvl').textContent = level;
}
```

```javascript
// project-detail.html — SSE-based real-time progress stream
function startPolling() {
  document.getElementById('pollStatus').textContent = 'Auto-refresh ●';
  pollIv = setInterval(async () => {
    const r = await fetch(`${API}/core/projects/${id}`, { headers: H() });
    const p = await r.json();
    updateProgress(p.progress || 0, p.completedTasks || 0, p.totalTasks || 0);
  }, 4000);
}
```

```javascript
// worker-task.html — minimum answer time enforcement with timer bar
function startTimer() {
  timerIv = setInterval(() => {
    const s = Math.floor((Date.now() - lockTime) / 1000);
    const m = Math.floor(s / 60);
    document.getElementById('timerDisp').textContent =
      `${m}:${String(s % 60).padStart(2, '0')}`;
    document.getElementById('timerBar').style.width =
      Math.min(s / 30 * 100, 100) + '%';
  }, 1000);
}
```

#### Diagram


![ER Diagram](../../assets/diagrams/UX_StateNavigation.png)


*Fig. 2.5. UX navigation state machine across all three role surfaces*

---

### Requirements checklist

Table 2.13. Requirements checklist — Refined UX

| # | Requirement | Status | Evidence / Notes |
|---|---|---|---|
| 1 | Each user role must have a dedicated, isolated interface with no cross-role data leakage | ✅ | Three separate HTML pages; role routing enforced by `localStorage.role`; all API calls server-side role-verified |
| 2 | Workers must receive real-time feedback on their Trust Score and accuracy after every review verdict | ✅ | `GET /api/core/workers/me/stats` returns `trustScore`, `trustLevel`, `completedTasks`, `rejectedTasks`, `totalPoints`; Caffeine cache evicted by `ReviewWorkflowService` on every verdict |
| 3 | The task interface must enforce and visually communicate the minimum answer time requirement | ✅ | Timer bar fills over 30 seconds; `submitBtn` is disabled until the worker types an answer; server-side enforcement returns 409 with trust penalty if `minAnswerSeconds` not elapsed |
| 4 | Project progress must be communicated to the Client without requiring manual page refresh | ✅ | `setInterval`-based dashboard refresh at 6 s; SSE stream at `/api/core/projects/{id}/progress/stream` for the project detail page |
| 5 | The interface must degrade gracefully when optional features (ML pre-annotation, HuggingFace) are unavailable | ✅ | `aiSuggestion` div is hidden by default; shown only when `payload.aiSuggestedLabel` is present; no error is thrown when the field is absent |

### Known limitations

Table 2.14. Known limitations — Refined UX

| Limitation | Impact | Potential Solution |
|---|---|---|
| JWT stored in `localStorage` is vulnerable to XSS attacks; any injected script on the page can read the token and impersonate the user | High severity in a production environment with user-generated content displayed in task payloads | Migrate to `HttpOnly` SameSite=Strict cookies for JWT storage; implement Content Security Policy headers in `nginx.conf` to restrict script execution sources |
| No frontend automated tests; UI correctness is validated only through manual walkthroughs during development and demonstration | Regressions in JavaScript logic (timer, SSE, form validation) are not caught by the CI pipeline | Integrate Playwright or Cypress E2E tests for the three critical flows (Client project creation, Worker task submission, Reviewer approval); add a `frontend-e2e` job to the GitHub Actions pipeline |