# Worker Happy Path

Task execution flow for WORKER role.

## Steps

1. Login and obtain JWT
2. Get next task (GET /tasks/next)
3. Lock task (POST /tasks/{id}/lock)
4. Submit answer (POST /tasks/{id}/submit)

Example submit request:
{
"answer": {
"label": "cat",
"confidence": 0.95
}
}

5. If review is enabled:
- reviewer approves or rejects
- on approve: task becomes APPROVED and points are credited
