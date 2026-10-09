ALTER TABLE traffic_station DROP CONSTRAINT ck_traffic_demo_station;
ALTER TABLE traffic_station ADD COLUMN source_system VARCHAR(80) NOT NULL DEFAULT 'DEMO';
ALTER TABLE traffic_station ADD COLUMN segment_length_km NUMERIC(10, 3)
    CHECK (segment_length_km > 0 AND segment_length_km <= 1000);

CREATE TABLE traffic_import_batch (
    id BIGSERIAL PRIMARY KEY,
    request_key VARCHAR(120) NOT NULL UNIQUE,
    payload_sha256 CHAR(64) NOT NULL,
    source_system VARCHAR(80) NOT NULL,
    station_id BIGINT NOT NULL REFERENCES traffic_station(id) ON DELETE RESTRICT,
    submitted_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    interval_count INTEGER NOT NULL,
    snapshot_count INTEGER NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE traffic_import_event (
    source_event_key VARCHAR(80) PRIMARY KEY,
    source_record_key VARCHAR(150) NOT NULL,
    event_kind VARCHAR(20) NOT NULL CHECK (event_kind IN ('COUNT', 'DENSITY')),
    batch_id BIGINT NOT NULL REFERENCES traffic_import_batch(id) ON DELETE RESTRICT
);

CREATE TABLE traffic_density_snapshot (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES traffic_station(id) ON DELETE RESTRICT,
    direction VARCHAR(50) NOT NULL,
    measured_at TIMESTAMPTZ NOT NULL,
    vehicle_class VARCHAR(40) NOT NULL,
    present_vehicles INTEGER NOT NULL CHECK (present_vehicles >= 0),
    source_event_key VARCHAR(80) NOT NULL UNIQUE REFERENCES traffic_import_event(source_event_key),
    CONSTRAINT uq_traffic_density_measure UNIQUE (station_id, direction, measured_at, vehicle_class)
);

CREATE INDEX idx_traffic_density_time ON traffic_density_snapshot(station_id, measured_at DESC);
