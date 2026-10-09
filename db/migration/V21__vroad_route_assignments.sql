CREATE TABLE vroad_route_assignment (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    route_name TEXT NOT NULL,
    chainage_from_m NUMERIC(15,3),
    chainage_to_m NUMERIC(15,3),
    purpose VARCHAR(20) NOT NULL DEFAULT 'DEMO' CHECK (purpose = 'DEMO'),
    assigned_by BIGINT NOT NULL REFERENCES app_user(id) ON DELETE RESTRICT,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, route_name),
    CHECK (length(trim(route_name)) BETWEEN 1 AND 500),
    CHECK (chainage_from_m IS NULL OR chainage_from_m >= 0),
    CHECK (chainage_to_m IS NULL OR chainage_to_m >= 0),
    CHECK (chainage_from_m IS NULL OR chainage_to_m IS NULL OR chainage_from_m <= chainage_to_m)
);

CREATE INDEX idx_vroad_route_assignment_route ON vroad_route_assignment(route_name, user_id);
CREATE INDEX idx_vroad_record_route ON raw_dataset_record
    ((COALESCE(raw_payload->>'route_name', raw_payload->>'road_name')))
    WHERE dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri');

CREATE FUNCTION vroad_chainage_m(payload JSONB) RETURNS NUMERIC
LANGUAGE sql IMMUTABLE PARALLEL SAFE AS $$
    SELECT CASE
        WHEN payload->>'start_m' ~ '^[0-9]+([.][0-9]+)?$'
            THEN (payload->>'start_m')::NUMERIC
        WHEN trim(payload->>'chainage') ~ '^[0-9]+[+/][0-9]{1,3}$'
            THEN split_part(replace(trim(payload->>'chainage'), '/', '+'), '+', 1)::NUMERIC * 1000
                 + split_part(replace(trim(payload->>'chainage'), '/', '+'), '+', 2)::NUMERIC
        ELSE NULL
    END
$$;

CREATE FUNCTION vroad_record_visible(record_id BIGINT, actor_id BIGINT) RETURNS BOOLEAN
LANGUAGE sql STABLE AS $$
    SELECT COALESCE((
        SELECT actor.is_active AND (
            role.role_code = 'ROLE_ADMIN'
            OR (
                role.role_code IN ('ROLE_EDITOR', 'ROLE_MANAGER', 'ROLE_VIEWER')
                AND NULLIF(trim(actor.branch_id), '') IS NOT NULL
                AND (
                    (item.raw_payload->>'branch_id' = actor.branch_id
                     AND item.dataset_key = 'demo_region_assets')
                    OR (
                        item.dataset_key IN ('vroad_assets', 'vroad_defects', 'vroad_iri')
                        AND EXISTS (
                            SELECT 1 FROM vroad_route_assignment assignment
                            WHERE assignment.user_id = actor.id
                              AND assignment.route_name = COALESCE(item.raw_payload->>'route_name', item.raw_payload->>'road_name')
                              AND (assignment.chainage_from_m IS NULL OR vroad_chainage_m(item.raw_payload) >= assignment.chainage_from_m)
                              AND (assignment.chainage_to_m IS NULL OR
                                  CASE WHEN item.raw_payload->>'end_m' ~ '^[0-9]+([.][0-9]+)?$'
                                       THEN (item.raw_payload->>'end_m')::NUMERIC
                                       ELSE vroad_chainage_m(item.raw_payload) END <= assignment.chainage_to_m)
                        )
                    )
                )
                AND (role.role_code <> 'ROLE_VIEWER' OR EXISTS (
                    SELECT 1 FROM region_demo_review review
                    WHERE review.record_id = item.id AND review.status = 'APPROVED'
                ))
            )
        )
        FROM app_user actor JOIN app_role role ON role.id = actor.role_id
        JOIN raw_dataset_record item ON item.id = record_id
        WHERE actor.id = actor_id
    ), FALSE)
$$;
