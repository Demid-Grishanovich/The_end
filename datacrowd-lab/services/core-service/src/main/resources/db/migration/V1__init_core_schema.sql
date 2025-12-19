-- V1__init_core_schema.sql
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- projects
CREATE TABLE IF NOT EXISTS projects (
                                        id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    title varchar(255) NOT NULL,
    description text,
    reward_points int NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- datasets
CREATE TABLE IF NOT EXISTS datasets (
                                        id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    name varchar(255) NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT now()
    );

-- tasks
CREATE TABLE IF NOT EXISTS tasks (
                                     id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    project_id uuid NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    dataset_id uuid REFERENCES datasets(id) ON DELETE SET NULL,
    title varchar(255) NOT NULL,
    description text,
    status varchar(50) NOT NULL DEFAULT 'OPEN',
    assigned_user_id uuid NULL, -- id из auth-service (внешней FK не делаем)
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- answers
CREATE TABLE IF NOT EXISTS answers (
                                       id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    task_id uuid NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    content text NOT NULL,
    status varchar(50) NOT NULL DEFAULT 'SUBMITTED',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- reviews
CREATE TABLE IF NOT EXISTS reviews (
                                       id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    answer_id uuid NOT NULL REFERENCES answers(id) ON DELETE CASCADE,
    reviewer_id uuid NOT NULL,
    decision varchar(50) NOT NULL, -- APPROVED/REJECTED
    comment text,
    created_at timestamptz NOT NULL DEFAULT now()
    );

-- (опционально, чтобы "очки" были простыми и явными)
-- points_ledger - журнал начислений
CREATE TABLE IF NOT EXISTS points_ledger (
                                             id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id uuid NOT NULL,
    task_id uuid,
    points int NOT NULL,
    reason varchar(255),
    created_at timestamptz NOT NULL DEFAULT now()
    );
