-- 1. App User Table
CREATE TABLE IF NOT EXISTS app_user (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    device_id VARCHAR(255) UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- 2. Change Event Table (Immutable Event Log)
CREATE TABLE IF NOT EXISTS change_event (
    event_id UUID PRIMARY KEY,
    entity_type VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    change_type VARCHAR(50) NOT NULL,
    payload JSONB,
    device_id VARCHAR(255) NOT NULL,
    hlc_timestamp VARCHAR(255) NOT NULL,
    server_received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    server_seq BIGSERIAL NOT NULL,
    based_on_seq BIGINT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_change_event_server_seq ON change_event(server_seq);
CREATE INDEX IF NOT EXISTS idx_change_event_entity ON change_event(entity_type, entity_id);

-- 3. Field State Table (Resolved Per-Field Attributes)
CREATE TABLE IF NOT EXISTS field_state (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_value TEXT,
    last_write_hlc VARCHAR(255) NOT NULL,
    last_write_device_id VARCHAR(255) NOT NULL,
    last_write_seq BIGINT,
    CONSTRAINT uq_field_state_entity_field UNIQUE (entity_type, entity_id, field_name)
);

CREATE INDEX IF NOT EXISTS idx_field_state_lookup ON field_state(entity_type, entity_id);

-- 4. Entity Deletion State Table (Delete Race Tracking)
CREATE TABLE IF NOT EXISTS entity_deletion_state (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    deletion_hlc VARCHAR(255) NOT NULL,
    deleted_by_device_id VARCHAR(255) NOT NULL,
    deletion_seq BIGINT,
    CONSTRAINT uq_entity_deletion_entity UNIQUE (entity_type, entity_id)
);

-- 5. Patient Record Table (Read Model Materialized View)
CREATE TABLE IF NOT EXISTS patient_record (
    id UUID PRIMARY KEY,
    patient_name VARCHAR(255) NOT NULL,
    weight DOUBLE PRECISION,
    blood_pressure VARCHAR(50),
    dosage VARCHAR(100),
    doses_administered BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_patient_record_deleted ON patient_record(deleted_at);

-- 6. Conflict Record Table (Supervisory Review Queue)
CREATE TABLE IF NOT EXISTS conflict_record (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    conflict_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(255) NOT NULL,
    entity_id UUID NOT NULL,
    event_id UUID NOT NULL,
    field_name VARCHAR(255),
    current_value TEXT,
    current_device_id VARCHAR(255),
    incoming_value TEXT,
    incoming_device_id VARCHAR(255),
    message TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    resolution VARCHAR(50),
    resolved_value TEXT,
    resolution_event_id UUID,
    resolved_by VARCHAR(255),
    resolved_at TIMESTAMPTZ,
    detected_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conflict_status_detected ON conflict_record(status, detected_at);