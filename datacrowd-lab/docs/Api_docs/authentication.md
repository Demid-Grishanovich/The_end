# Authentication & Authorization

## JWT authentication

All public API endpoints (except /auth/** and health endpoints)
require a JWT token.

Header format:
Authorization: Bearer <token>

JWT is issued by:
POST /auth/login

## Roles and permissions

### WORKER
- GET /tasks/next
- POST /tasks/{id}/lock
- POST /tasks/{id}/submit
- Review endpoints (if enabled)

### CLIENT
- POST /projects
- POST /projects/{id}/datasets
- POST /datasets/{id}/generate-tasks
- Export endpoints

### ADMIN
- PATCH /admin/users/{id}/role

## Internal API

Internal endpoints are protected by:
X-Internal-Token: <INTERNAL_TOKEN>

They are used by:
- Runner → Core
- Payments Service → Core
