CREATE TABLE businesses (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(100),
    address TEXT,
    phone VARCHAR(50),
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Colombo',
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_businesses_name_not_blank
        CHECK (BTRIM(name) <> '')
);

CREATE TABLE queues (
    id UUID PRIMARY KEY,
    business_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    ticket_prefix VARCHAR(10),
    max_capacity INTEGER NOT NULL,
    average_service_time_minutes INTEGER NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_queues_business
        FOREIGN KEY (business_id) REFERENCES businesses(id),
    CONSTRAINT uq_queues_business_name
        UNIQUE (business_id, name),
    CONSTRAINT chk_queues_name_not_blank
        CHECK (BTRIM(name) <> ''),
    CONSTRAINT chk_queues_ticket_prefix_not_blank
        CHECK (ticket_prefix IS NULL OR BTRIM(ticket_prefix) <> ''),
    CONSTRAINT chk_queues_max_capacity_positive
        CHECK (max_capacity > 0),
    CONSTRAINT chk_queues_average_service_time_positive
        CHECK (average_service_time_minutes > 0)
);

CREATE INDEX idx_queues_business_id ON queues(business_id);

CREATE TABLE queue_sessions (
    id UUID PRIMARY KEY,
    queue_id UUID NOT NULL,
    session_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL,
    next_ticket_number INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    opened_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,

    CONSTRAINT fk_queue_sessions_queue
        FOREIGN KEY (queue_id) REFERENCES queues(id),
    CONSTRAINT uq_queue_sessions_queue_date
        UNIQUE (queue_id, session_date),
    CONSTRAINT chk_queue_sessions_status
        CHECK (status IN ('OPEN', 'PAUSED', 'CLOSED')),
    CONSTRAINT chk_queue_sessions_next_ticket_number_positive
        CHECK (next_ticket_number > 0)
);

CREATE INDEX idx_queue_sessions_queue_id ON queue_sessions(queue_id);

CREATE TABLE queue_entries (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    customer_user_id UUID NOT NULL,
    ticket_number INTEGER NOT NULL,
    ticket_code VARCHAR(30) NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,

    CONSTRAINT fk_queue_entries_session
        FOREIGN KEY (session_id) REFERENCES queue_sessions(id),
    CONSTRAINT uq_queue_entries_session_ticket_number
        UNIQUE (session_id, ticket_number),
    CONSTRAINT uq_queue_entries_session_ticket_code
        UNIQUE (session_id, ticket_code),
    CONSTRAINT chk_queue_entries_ticket_number_positive
        CHECK (ticket_number > 0),
    CONSTRAINT chk_queue_entries_ticket_code_not_blank
        CHECK (BTRIM(ticket_code) <> ''),
    CONSTRAINT chk_queue_entries_status
        CHECK (status IN ('WAITING', 'CALLED', 'COMPLETED', 'SKIPPED', 'LEFT'))
);

CREATE INDEX idx_queue_entries_session_id ON queue_entries(session_id);

CREATE UNIQUE INDEX uq_queue_entries_called_session
    ON queue_entries(session_id)
    WHERE status = 'CALLED';

CREATE UNIQUE INDEX uq_queue_entries_active_customer_session
    ON queue_entries(session_id, customer_user_id)
    WHERE status IN ('WAITING', 'CALLED');

CREATE INDEX idx_queue_entries_active_ticket_order
    ON queue_entries(session_id, ticket_number)
    WHERE status IN ('WAITING', 'CALLED');

CREATE INDEX idx_queue_entries_active_customer
    ON queue_entries(customer_user_id, session_id)
    WHERE status IN ('WAITING', 'CALLED');

CREATE TABLE queue_operations (
    id UUID PRIMARY KEY,
    queue_entry_id UUID NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    performed_by_user_id UUID NOT NULL,
    details JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_queue_operations_entry
        FOREIGN KEY (queue_entry_id) REFERENCES queue_entries(id),
    CONSTRAINT chk_queue_operations_operation_type
        CHECK (operation_type IN (
            'JOINED',
            'CALLED',
            'RECALLED',
            'SKIPPED',
            'COMPLETED',
            'LEFT'
        ))
);

CREATE INDEX idx_queue_operations_queue_entry_id
    ON queue_operations(queue_entry_id);

CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,

    CONSTRAINT chk_outbox_events_aggregate_type_not_blank
        CHECK (BTRIM(aggregate_type) <> ''),
    CONSTRAINT chk_outbox_events_event_type_not_blank
        CHECK (BTRIM(event_type) <> '')
);

CREATE INDEX idx_outbox_events_unpublished
    ON outbox_events(created_at)
    WHERE published_at IS NULL;
