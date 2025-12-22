# E2E scenario (minimal)

Цель — один воспроизводимый сквозной сценарий, подтверждающий работоспособность ключевого пути.

## Scenario: Project → Dataset → (Generate tasks / Bulk tasks)

### Preconditions
- подняты сервисы через docker compose
- есть пользователь (или регистрируемся)
- получен JWT token

### Steps (manual via Postman)
1) Login
- POST `auth-service` login
- сохранить `JWT`

2) Create Project
- POST `api-gateway` `/api/core/projects`
- сохранить `projectId`

3) Create Dataset
- POST `/api/core/projects/{projectId}/datasets` (multipart)
- сохранить `datasetId`

4) Generate tasks (если есть endpoint)
- POST `/api/core/tasks/generate` with `datasetId`

5) Verify tasks created
- GET `/api/core/tasks?datasetId={datasetId}`
- ожидаем непустой список

## Automation option (for 10)
Вариант автоматизации:
- Postman Collection + Newman в CI
  или
- REST-assured test (отдельный e2e модуль)

## Evidence
Что показать на защите:
- запросы и ответы (Postman скрины/экспорт коллекции)
- логи сервисов (по необходимости)