CREATE TABLE traffic_station (
    id BIGSERIAL PRIMARY KEY,
    station_code VARCHAR(80) NOT NULL UNIQUE,
    station_name VARCHAR(180) NOT NULL,
    route_name VARCHAR(180) NOT NULL,
    branch_id VARCHAR(100),
    is_demo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_traffic_demo_station CHECK (is_demo)
);

CREATE TABLE traffic_interval (
    id BIGSERIAL PRIMARY KEY,
    station_id BIGINT NOT NULL REFERENCES traffic_station(id) ON DELETE RESTRICT,
    direction VARCHAR(50) NOT NULL,
    lane SMALLINT NOT NULL CHECK (lane > 0),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    vehicle_class VARCHAR(40) NOT NULL,
    vehicle_count INTEGER NOT NULL CHECK (vehicle_count >= 0),
    source_event_key VARCHAR(150) NOT NULL UNIQUE,
    CONSTRAINT ck_traffic_half_open_window CHECK (window_start < window_end),
    CONSTRAINT uq_traffic_measure UNIQUE (station_id, direction, lane, window_start, window_end, vehicle_class)
);

CREATE INDEX idx_traffic_window ON traffic_interval(station_id, window_start, window_end);

INSERT INTO traffic_station(station_code, station_name, route_name, is_demo)
VALUES ('DEMO-TRAFFIC-01', 'Trạm mô phỏng số 01', 'Tuyến mô phỏng, không có vị trí thật', TRUE);

INSERT INTO traffic_interval(station_id, direction, lane, window_start, window_end,
                             vehicle_class, vehicle_count, source_event_key)
SELECT station.id, 'Chiều A', 1, date_time, date_time + INTERVAL '15 minutes', vehicle_class,
       CASE vehicle_class WHEN 'Xe máy' THEN 110 + slot * 13
           WHEN 'Ô tô con' THEN 32 + slot * 5
           WHEN 'Xe tải' THEN 15 + slot * 2 ELSE 6 + slot END,
       'DEMO-TRAFFIC-01-' || slot || '-' || vehicle_class
FROM traffic_station station
CROSS JOIN generate_series(0, 7) AS slot
CROSS JOIN (VALUES ('Xe máy'), ('Ô tô con'), ('Xe tải'), ('Xe khách')) AS classes(vehicle_class)
CROSS JOIN LATERAL (SELECT TIMESTAMPTZ '2026-10-07 08:00:00+07' + slot * INTERVAL '15 minutes' AS date_time) AS periods
WHERE station.station_code = 'DEMO-TRAFFIC-01';
