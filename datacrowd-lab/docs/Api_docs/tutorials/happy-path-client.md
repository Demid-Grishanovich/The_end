# Client Happy Path

Project → Dataset → Payment → Task Generation

## Steps

1. Register user and promote to CLIENT (admin action)
2. Login and obtain JWT
3. Create project (POST /projects)
4. Upload dataset (POST /projects/{projectId}/datasets)
5. Create checkout (POST /payments/checkout)
6. Pay via Stripe test mode
7. Stripe webhook marks payment as PAID
8. Generate tasks (POST /datasets/{datasetId}/generate-tasks)
