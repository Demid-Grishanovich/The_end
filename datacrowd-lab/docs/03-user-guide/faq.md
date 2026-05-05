## 3.3 FAQ and troubleshooting

### Frequently asked questions

#### General

**Q: What types of datasets does DataCrowd Lab support?**
The platform supports five data types: TEXT (CSV or JSONL files with text fields), IMAGE (ZIP archives containing `manifest.jsonl` + image files), AUDIO (ZIP archives containing `manifest.jsonl` + audio clips), CODE (JSONL files with code snippets and language identifiers), and MATH (JSONL files with mathematical problems and solutions). The data type is set at the project level and determines the task payload schema and the column structure of CSV exports. The format guide for each type is available at `GET /api/core/projects/data-type-guides`.

**Q: How does the consensus mechanism work?**
Each project has a `reviewersCount` parameter (set at creation time). An answer reaches the `APPROVED` state only after this many independent reviewers have each approved it. For example, with `reviewersCount=2`, an answer requires two separate approve decisions from two different reviewers. If any reviewer rejects the answer at any point, it immediately returns to `NEW` status and is reassigned to a different worker, regardless of how many prior approvals it had accumulated.

**Q: What happens to dataset rows that cannot be parsed?**
Unparseable rows are recorded in the `failed_items` table in `core_db` with the line number, raw content, and error message. The Go-Runner continues processing the rest of the file without interruption. Clients can inspect failed items via pgAdmin (`http://localhost:5050`) or by querying `GET /api/core/datasets/{id}/summary`, which returns a `failedItems` count alongside the `totalItems` count. Rows in `failed_items` do not become tasks and are not included in exports.

**Q: Can a project be deleted after creation?**
Project deletion is not implemented in the current version. Projects can be archived by transitioning to `ARCHIVED` status via the internal admin API. The `ARCHIVED` status prevents new tasks from being assigned to workers but does not delete existing annotations or export data.

**Q: Is the platform multi-tenant?**
Yes. Each Client can only see and manage their own projects. The `getOwnedOrThrow()` check in `ProjectService` verifies project ownership on every project-level mutation. Workers see only the available task queue (projects with `billingStatus=PAID` and available tasks) without visibility into project ownership or other Clients' configurations.

---

#### Account and access

**Q: I registered as a Worker but I need to be a Client. Can I change my role?**
Role changes are performed by an administrator via the internal API endpoint `PATCH /internal/users/{userId}/role` with the `X-Internal-Token` header. If you have administrator access, you can execute this from the Auth Service Swagger UI at `http://localhost:8081/swagger-ui.html` under the **Internal** section. Alternatively, register a new account with the Client role using `POST /api/auth/register-client` with the `X-Admin-Key` header.

**Q: My login is failing with "wrong email or password" even though my credentials are correct.**
Verify that the email address is entered in exactly the same format used during registration (case-sensitive). If the password was entered incorrectly three or more times in the same browser session, check that the browser has not auto-filled an outdated password. Passwords are stored as BCrypt hashes; there is no plaintext recovery. If access is irrecoverably lost, an administrator can inspect the `auth_db.users` table via pgAdmin and reset the account by promoting the user to a known role.

**Q: My JWT token keeps expiring during a long annotation session.**
The default token TTL is 60 minutes, configurable via `JWT_TTL_MINUTES` in the `.env` file. Use `POST /api/auth/refresh` with the current `Authorization: Bearer <token>` header to issue a new token without re-entering credentials. The frontend does not currently implement automatic token refresh; this is a known limitation documented in Section 4.3.

**Q: The Admin Key for Client registration is not accepted.**
The Admin Key is set via the `ADMIN_KEY` environment variable in `infra/docker/.env`. The default value is `demo-admin-key`. Verify that the key is entered exactly as configured (case-sensitive, no leading/trailing spaces). If the `.env` file was edited after `docker compose up`, restart the Auth Service with `docker compose restart auth-service` for the new value to take effect.

**Q: My Worker account shows "BLOCKED" on the stats page and I cannot receive tasks.**
A BLOCKED status means your Trust Score has fallen below 30. Trust Score decreases when answers are submitted too quickly (bot detection, −15), honeypot tasks are answered incorrectly (−20), or reviewer decisions are rejections (−10). Contact the platform administrator to review your account's audit log. In a real deployment, a reputation recovery mechanism would allow workers to regain access through a probationary period; this is planned for Phase 2.

---

#### Features

**Q: The task count shows 0 even though I uploaded a dataset and paid for the project.**
Dataset ingestion is asynchronous. After payment, the system triggers the Go-Runner, which parses the file and creates tasks. The dataset status transitions through `UPLOADED → GENERATING → READY`. This process typically takes 10–90 seconds depending on file size. Refresh the Project Detail page; the task count updates automatically every 4 seconds. If the status remains `GENERATING` for more than 5 minutes, check the Runner logs with `docker compose logs runner`.

**Q: The "Generate Tasks" button does not appear on my project page.**
The task generation trigger requires three conditions to be met: (1) the project must have `billingStatus=PAID`, (2) a dataset must have been uploaded (the Dataset card must show a source file), and (3) the dataset status must be `UPLOADED` or `FAILED` (not `READY` — tasks cannot be regenerated from an already-processed dataset). If all three conditions are met but the button is still absent, hard-refresh the page (`Ctrl+Shift+R` / `Cmd+Shift+R`).

**Q: The export file is empty.**
The export contains only answers with `status=APPROVED`. If all answers are in `SUBMITTED` or `IN_REVIEW` status (awaiting reviewer consensus), the export will be empty. Check the project's `completedTasks` count on the dashboard. If `completedTasks=0` and `totalTasks>0`, either no reviewers have processed any answers, or `reviewersCount` is high and consensus has not been reached. Set `reviewersCount=1` for faster turnaround in demonstration environments.

**Q: Image or audio content is not displaying in the task execution interface.**
Binary assets (images, audio clips) are served via `GET /api/core/tasks/{id}/asset`, which requires authentication. The frontend loads assets via an authenticated `fetch()` call and renders them via a Blob URL. If the asset area remains blank, verify that: (1) the ZIP archive was correctly structured (images in an `images/` or `assets/` subfolder, `manifest.jsonl` in the root), (2) the `file` or `assetRelPath` field in the task `payloadJson` matches the actual path inside the extracted ZIP, and (3) the browser supports the media type of the file (e.g., WebM audio requires a Chromium-based browser).

---

### Common issues

Table 3.6. Common issues

| Problem | Possible Cause | Solution |
|---|---|---|
| `docker compose up --build` fails with "port is already allocated" | Another process (PostgreSQL, MinIO, or a previous Docker run) is using one of the required ports (80, 3000, 5050, 5432, 8080-8083, 8090, 9000-9001, 9090) | Run `docker compose down` to stop any existing containers; use `lsof -i :<port>` (macOS/Linux) or Task Manager (Windows) to identify and stop the conflicting process; alternatively, change the host port mapping in `docker-compose.yml` |
| Services start but return 503 from the API Gateway | One or more downstream services (Auth, Core, Payments) failed to start; the Gateway routes to an unavailable service | Run `docker compose logs auth-service core-service payments-service` to identify the failing service; common causes: missing `JWT_SECRET` in `.env`, Flyway migration failure due to incorrect DB credentials |
| Task count stays at 0 after payment, Runner log shows "connection refused" | `CORE_INTERNAL_BASE_URL` or `INTERNAL_TOKEN` is misconfigured; the Runner cannot reach Core's internal API | Verify `CORE_INTERNAL_BASE_URL=http://core-service:8082` in `.env`; verify `INTERNAL_TOKEN` matches the value expected by Core's `InternalTokenFilter`; restart affected containers |
| Flyway migration fails at startup with "relation already exists" | A previous partial migration left the database in an inconsistent state; Flyway's checksum does not match the stored migration | Connect via pgAdmin, navigate to the affected database, and truncate the `flyway_schema_history` table; alternatively, run `docker compose down -v` to reset all volumes and start fresh |
| Browser shows a blank white page on `localhost:80` | The nginx frontend container failed to start, or the HTML files were not copied into the image correctly | Run `docker compose logs frontend`; if the container exited, check that `services/frontend/Dockerfile` is present and that `docker compose up --build` completed without errors |
| Login succeeds but dashboard shows no projects for a Client | The JWT `role` claim is not `CLIENT`; the user may have registered via the Worker endpoint | Log out, re-register using `POST /api/auth/register-client` with the `X-Admin-Key` header, or ask an administrator to change the role via the internal API |
| Export download starts but the file is 0 bytes | No answers have reached `APPROVED` status; all answers are still in `SUBMITTED` or `IN_REVIEW` | Ensure reviewers have processed answers; check `core_db.answers` via pgAdmin to confirm `status` values; use `reviewersCount=0` on the project for auto-approval in demonstration environments |

---

### Error messages

Table 3.7. Error messages

| HTTP Status / Message | Meaning | How to Fix |
|---|---|---|
| `401 Unauthorized` — `{"error":"Invalid JWT"}` | The JWT token in the `Authorization` header is absent, malformed, or has expired | Log in again via `POST /api/auth/login` to obtain a fresh token; paste the new token into the Swagger UI "Authorize" dialog or re-log in on the frontend |
| `401 Unauthorized` — `{"error":"Unauthorized: missing or invalid X-Internal-Token"}` | A request to an `/internal/**` endpoint was made without the correct `X-Internal-Token` header | Add the header `X-Internal-Token: <INTERNAL_TOKEN>` where `<INTERNAL_TOKEN>` is the value set in `.env`; this endpoint is not intended for end-user access |
| `403 Forbidden` — `{"detail":"Your trust score (N/100) is too low"}` | The Worker's Trust Score has fallen below the block threshold of 30 | Check the Worker's stats page for Trust Score history; contact the platform administrator to review the audit log; Trust Score recovery is a Phase 2 feature |
| `403 Forbidden` — `{"detail":"Not your project"}` | A Client attempted to access or modify a project they do not own | Verify the project ID in the request URL; a Client can only access projects where `owner_user_id` matches their authenticated `userId` |
| `403 Forbidden` — `{"detail":"Reviewer cannot review own answer"}` | A Reviewer attempted to approve or reject an answer they submitted as a Worker | This is intentional behavior enforced by the platform; use a separate account with the Reviewer role |
| `409 Conflict` — `{"detail":"Answer submitted too fast (Ns). Minimum required: Ms"}` | The Worker submitted an answer in fewer seconds than the project's `minAnswerSeconds` setting | Wait for the timer bar on the task page to fill before submitting; if this occurs repeatedly, verify that the system clock on the test machine is accurate |
| `409 Conflict` — `{"detail":"Worker already has a locked task: {taskId}"}` | A Worker attempted to lock a second task while already holding a lock on another | Complete or release the currently locked task before claiming a new one; navigate to the task page and click **Release Task** |
| `409 Conflict` — `{"detail":"Reviewer already reviewed this answer"}` | A Reviewer attempted to submit a second decision on the same answer | Each Reviewer may review each answer exactly once; if a correction is needed, contact the platform administrator to inspect the `reviews` table via pgAdmin |
| `400 Bad Request` — `{"detail":"username taken"}` | Registration failed because the username is already in use | Choose a different username; usernames are globally unique across the platform |
| `400 Bad Request` — `{"detail":"email taken"}` | Registration failed because the email address is already registered | Log in with the existing account or use a different email address |
| `404 Not Found` | The requested resource (project, task, dataset, export) does not exist or has been deleted | Verify the UUID in the request URL; if the resource was recently created, check that the creation request completed with a 200 or 201 status |
| `500 Internal Server Error` | An unhandled exception occurred on the server | Check the service logs with `docker compose logs <service-name>`; the most common causes are database connectivity failures and misconfigured environment variables |

---

### Browser-specific issues

Table 3.8. Browser-specific issues

| Browser | Known Issue | Workaround |
|---|---|---|
| Safari 17 (macOS) | `EventSource` (SSE) connections to the project progress stream may be silently dropped after the macOS display sleeps; the progress bar freezes without an error | Prevent display sleep during long annotation sessions via System Preferences → Battery → Never turn off the display when plugged in; alternatively, reload the Project Detail page to restart the SSE connection |
| Firefox 121+ | The Blob URL generated for audio assets (`createObjectURL`) may not autoplay due to Firefox's stricter autoplay policy; audio clips remain silent until the user clicks the audio element | Ensure the audio player element has `controls` attribute set; instruct workers to click the play button rather than expecting autoplay |
| Chrome (all versions) on Windows + WSL2 Docker | `localhost:80` occasionally resolves to the Windows host rather than the WSL2 Docker interface; the frontend loads but API calls to `localhost:8080` fail with CORS errors | Use `127.0.0.1:80` and `127.0.0.1:8080` explicitly in the browser address bar; alternatively, configure WSL2 network forwarding per Microsoft's documentation |
| Internet Explorer 11 | The frontend uses CSS custom properties (`var(--p)`), `EventSource`, `fetch`, and `localStorage` — none of which are supported in IE 11 | Internet Explorer is not supported; use Chrome 120+, Firefox 121+, or Safari 17+ |
| Chrome with strict Content Security Policy extensions (e.g., uBlock Origin) | CSP extensions may block inline `<script>` tags in the HTML pages, breaking client-side authentication logic | Disable the extension for `localhost` or add `localhost` to the extension's allowlist |

---

### Getting help

Table 3.9. Contact and support

| Channel | Purpose | Response Time |
|---|---|---|
| GitHub Issues (`github.com/<owner>/datacrowd-lab/issues`) | Bug reports, feature requests, documentation errors | Best effort (open-source project) |
| GitHub Discussions | Architecture questions, integration guidance, general questions | Best effort |
| pgAdmin (`http://localhost:5050`) | Direct database inspection for data issues, audit log queries, DLQ review | Immediate (self-service) |
| Swagger UI (`http://localhost:8080/swagger-ui.html`) | Interactive API testing, endpoint discovery, authentication debugging | Immediate (self-service) |
| `docker compose logs <service>` | Service-level log inspection for startup failures and runtime errors | Immediate (self-service) |

**Bug reporting procedure:**

1. Reproduce the bug consistently and note the exact steps required to trigger it
2. Collect the relevant service log output: `docker compose logs <service-name> --tail=100 > bug-report.log`
3. Note the values of all relevant environment variables (excluding secrets); record which `.env` values differ from `.env.example`
4. Capture the full HTTP request and response using the browser developer tools Network tab (or Postman) — include the request URL, headers (omit the JWT token value), request body, response status code, and response body
5. Open a GitHub Issue with the title format: `[BUG] <Brief description>` and attach the log file, environment summary, and HTTP capture
6. Do not include actual secret values (`JWT_SECRET`, `INTERNAL_TOKEN`, `STRIPE_SECRET_KEY`) in the issue or any public channel