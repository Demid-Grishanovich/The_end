-- Dead Letter Queue: битые строки из датасетов которые не удалось обработать
CREATE TABLE IF NOT EXISTS failed_items (
                                            id          uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    dataset_id  uuid        NOT NULL,
    line_number int         NOT NULL,
    raw_content text,
    error_msg   text        NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_failed_items_dataset ON failed_items(dataset_id);