# Data Dictionary

## auth_db
### users
- id UUID PK
- email VARCHAR(255) UNIQUE NOT NULL (PII)
- username VARCHAR(64) UNIQUE NOT NULL
- password_hash VARCHAR(255) NOT NULL (bcrypt)
- role VARCHAR(50) NOT NULL
- status VARCHAR(16) NOT NULL
- created_at TIMESTAMP/TIMESTAMPTZ NOT NULL
- updated_at TIMESTAMPTZ NOT NULL

Качество данных:
- email валиден (проверяется на уровне приложения)
- username уникален

## core_db
Основные сущности: projects, datasets, tasks, answers, reviews, exports (см. миграции core-service).

## payments_db
### payments
- id UUID PK
- user_id UUID NULL (ссылка на auth, без FK)
- project_id UUID NOT NULL (ссылка на core, без FK)
- amount_cents INT NOT NULL (CHECK >0)
- currency VARCHAR(3) NOT NULL
- status VARCHAR(32) NOT NULL
- stripe_session_id VARCHAR(255) UNIQUE NULL
- stripe_payment_intent_id VARCHAR(255) UNIQUE NULL
- created_at TIMESTAMPTZ NOT NULL

### ledger
- id UUID PK
- project_id UUID NOT NULL
- type VARCHAR(32) NOT NULL
- amount_cents INT NOT NULL (может быть +/-)
- ref_payment_id UUID NULL FK -> payments(id)
- created_at TIMESTAMPTZ NOT NULL
