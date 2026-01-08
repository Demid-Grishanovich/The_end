# Auto Tests

## Goal

Цель — обеспечить проверяемое качество проекта с помощью автотестов и подтвердить покрытие кода отчётом JaCoCo (**целевой порог: ≥ 70%**).

Проект — микросервисный (Spring Boot). Поэтому используем тестовую пирамиду:

- **Unit tests** — логика сервисов, быстрые, изолированные
- **Integration tests** — `@SpringBootTest` + Postgres через **Testcontainers**
- **Component tests** — моки внешних сервисов (runner/payments)
- **E2E tests** — минимум 1 сквозной сценарий

## Scope

Основной функционал, который покрываем тестами:

### core-service

- создание и чтение проектов
- создание датасетов
- генерация задач / bulk internal endpoint
- базовые проверки прав доступа

### auth-service

- login / register / refresh (если есть)

### payments-service

- обработка callback / webhook (валидный / невалидный)

## Tools

- Unit: **JUnit 5**, **Mockito**
- Integration: **Spring Boot Test**, **Testcontainers (PostgreSQL)**, **Flyway**
- Coverage: **JaCoCo**
- Component/E2E (для «10»): **WireMock / MockWebServer** или **Postman + Newman**

## Test structure

Для каждого сервиса:

- Unit tests: `src/test/java/.../service/*Test.java`
- Integration tests: `src/test/java/.../it/*IT.java` (или `.../integration/*IT.java`)

Нейминг:

- `SomethingServiceTest` — unit
- `SomethingControllerIT` — integration

## How to run locally

### Requirements

- Java 21
- Docker Desktop / Docker Engine (для Testcontainers)
- Maven

### Run tests (по сервисам)

Перейти в папку сервиса и выполнить:

```bash
mvn test
```

Примеры:

```bash
cd services/core-service && mvn test
cd services/auth-service && mvn test
cd services/payments-service && mvn test
```

### Run all tests (если есть корневой aggregator)

Если в корне есть общий `pom.xml` — можно запускать из корня:

```bash
mvn test
```

## Coverage (JaCoCo)

Целевое покрытие: **≥ 70%**.

После добавления JaCoCo в `pom.xml` каждого сервиса отчёт будет доступен по пути:

- `services/<service>/target/site/jacoco/index.html`

Порог покрытия будет настроен как quality gate:

- если покрытие ниже порога — build падает.

> Примечание: на этапе внедрения JaCoCo секция обновится цифрами покрытия и (при необходимости) скриншотами.

## What is considered “good tests”

Мы проверяем не DTO/геттеры/конфиги, а бизнес-логику:

- happy path
- edge cases
- negative cases (ошибки, запреты, валидация)

Примеры edge cases:

- создание сущности с обязательными полями (NOT NULL)
- попытка доступа без JWT → 401
- неверная роль → 403
- некорректные входные данные → 400

## Integration tests requirements

Интеграционные тесты должны:

- поднимать Postgres через Testcontainers
- применять Flyway миграции
- проверять реальные запросы к репозиториям и/или REST endpoint’ам

Минимальный пример покрытия:

- создать проект → проверить, что он в БД
- создать датасет → проверить, что он в БД
- выполнить internal bulk tasks → проверить вставку tasks

## Component tests (для «10»)

Для внешних зависимостей используем моки (WireMock/MockWebServer), чтобы:

- не зависеть от доступности runner/payments
- гарантировать воспроизводимость тестов

## E2E tests (для «10»)

Минимальный E2E сценарий должен быть описан в `docs/autotests-e2e.md`.

## CI

В CI должны выполняться:

- `mvn test`
- JaCoCo report + check (quality gate)

CI должен работать в чистом окружении без ручных действий.

## Known gaps

На старте допускаются исключения (но они должны быть описаны и обоснованы):

- интеграции, требующие внешних ключей/платежных провайдеров (мокаем)
- сложные UI сценарии (если нет фронта)

## Submission checklist (что показать преподавателю)

- ✅ Unit tests + Integration tests (есть в репозитории)
- ✅ CI запускает тесты
- ✅ JaCoCo отчёт в `target/site/jacoco/index.html`
- ✅ Покрытие ≥ 70%
- ✅ 1 сквозной сценарий (E2E) + описание
