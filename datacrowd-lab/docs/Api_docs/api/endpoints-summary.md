# Endpoints Summary

This page provides a high-level overview of available API endpoints.

## Auth
- POST /auth/register
- POST /auth/login

## Admin
- PATCH /admin/users/{id}/role

## Projects (Client)
- POST /projects
- GET /projects
- GET /projects/{id}

## Datasets (Client)
- POST /projects/{id}/datasets
- GET /datasets/{id}
- POST /datasets/{id}/generate-tasks

## Tasks (Worker)
- GET /tasks/next
- POST /tasks/{id}/lock
- POST /tasks/{id}/unlock
- POST /tasks/{id}/submit

## Payments
- POST /payments/checkout
- POST /webhooks/stripe

## Internal
- /internal/** (requires X-Internal-Token)
