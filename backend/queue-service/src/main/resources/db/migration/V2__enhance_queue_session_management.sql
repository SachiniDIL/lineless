ALTER TABLE queue_sessions
    ADD COLUMN capacity INTEGER,
    ADD COLUMN scheduled_start_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason TEXT;

UPDATE queue_sessions qs
SET capacity = q.max_capacity
FROM queues q
WHERE qs.queue_id = q.id;

UPDATE queue_sessions qs
SET scheduled_start_at = COALESCE(
    qs.opened_at,
    qs.session_date::TIMESTAMP
        AT TIME ZONE b.timezone
)
FROM queues q
JOIN businesses b ON b.id = q.business_id
WHERE qs.queue_id = q.id;

ALTER TABLE queue_sessions
    ALTER COLUMN capacity SET NOT NULL,
    ALTER COLUMN scheduled_start_at SET NOT NULL;

ALTER TABLE queue_sessions
    ADD CONSTRAINT chk_queue_sessions_capacity_positive
        CHECK (capacity > 0);

ALTER TABLE queue_sessions
    DROP CONSTRAINT chk_queue_sessions_status,
    ADD CONSTRAINT chk_queue_sessions_status
        CHECK (status IN ('OPEN', 'PAUSED', 'CLOSED', 'CANCELLED'));

ALTER TABLE queue_entries
    DROP CONSTRAINT chk_queue_entries_status,
    ADD CONSTRAINT chk_queue_entries_status
        CHECK (status IN (
            'WAITING',
            'CALLED',
            'COMPLETED',
            'SKIPPED',
            'LEFT',
            'CANCELLED'
        ));

ALTER TABLE queue_operations
    DROP CONSTRAINT chk_queue_operations_operation_type,
    ADD CONSTRAINT chk_queue_operations_operation_type
        CHECK (operation_type IN (
            'JOINED',
            'CALLED',
            'RECALLED',
            'SKIPPED',
            'COMPLETED',
            'LEFT',
            'CANCELLED'
        ));

CREATE TABLE session_operations (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    performed_by_user_id UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_session_operations_session
        FOREIGN KEY (session_id) REFERENCES queue_sessions(id),
    CONSTRAINT chk_session_operations_operation_type
        CHECK (operation_type IN (
            'OPENED',
            'PAUSED',
            'RESUMED',
            'CLOSED',
            'CANCELLED',
            'CAPACITY_CHANGED'
        ))
);

CREATE INDEX idx_session_operations_session_id
    ON session_operations(session_id);
