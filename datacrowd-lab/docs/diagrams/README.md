# Диаграммы (Этап 1)

Формат: **PlantUML** (`.puml`).

## Как открыть
- IntelliJ IDEA → Plugins → **PlantUML Integration**
- (Опционально) Graphviz, если PlantUML попросит для рендера

## Список файлов
1. `01-architecture.puml` — общая микросервисная архитектура
2. `02-auth-sequence.puml` — регистрация/логин + JWT
3. `03-payments-sequence.puml` — платеж (checkout → webhook)
4. `04-erd-auth-db.puml` — ERD для Auth DB (текущая реализация)
5. `05-deployment-docker.puml` — деплой в Docker (логические контейнеры)

> Примечание: в архитектурной схеме показаны также будущие сервисы (core/payments/runner),
> даже если они пока не реализованы в репозитории.
