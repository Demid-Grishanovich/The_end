CREATE TABLE IF NOT EXISTS audit_logs (
                                          id          uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
    actor_id    uuid,
    action      varchar(100) NOT NULL,
    entity_type varchar(50),
    entity_id   uuid,
    details     text,
    ip_address  varchar(45),
    created_at  timestamptz NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS idx_audit_logs_actor   ON audit_logs(actor_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity  ON audit_logs(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);