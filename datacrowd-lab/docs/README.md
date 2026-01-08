# Datacrowd-lab — документация

- [План по автотестам и покрытию](Tests/auto-tests-plan.md)

## Быстрый старт (локально)

### Запуск инфраструктуры (Docker)

```bash
cd infra/docker
cp .env.example .env  # если есть; иначе создай .env по образцу
docker compose up -d --build
```

Порты по умолчанию:

- API Gateway: `http://localhost:8080`
- Auth-service: `http://localhost:8081`
- Core-service: `http://localhost:8082`
- Payments-service: `http://localhost:8083`
- PgAdmin: `http://localhost:5050`

### Smoke-check

```bash
curl http://localhost:8080/actuator/health
```

## Тестирование

- Юнит/интеграционные тесты запускаются из папки сервиса: `mvn test`
- Отчёт покрытия JaCoCo после тестов: `services/<service>/target/site/jacoco/index.html`

