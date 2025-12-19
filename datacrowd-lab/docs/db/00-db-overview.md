# DB Overview (OLTP)

В проекте используется PostgreSQL как транзакционная (OLTP) СУБД. :contentReference[oaicite:9]{index=9}

## Версионность структуры
Структура всех БД изменяется только через миграции Flyway (каталоги `db/migration` в сервисах) и хранится в Git. :contentReference[oaicite:10]{index=10}

## Изоляция БД
Используется один Postgres instance, но разные базы и разные пользователи:
- auth_db / auth_user
- core_db / core_user
- payments_db / payments_user

Это учебная оптимизация без shared-DB по смыслу: кросс-доступ запрещён. :contentReference[oaicite:11]{index=11}
