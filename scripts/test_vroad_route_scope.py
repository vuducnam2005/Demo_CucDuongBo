import argparse
import subprocess


CHECKSUM_SQL = """
SELECT count(*)::text || ':' || md5(string_agg(to_jsonb(record_data)::text, chr(10) ORDER BY record_data.id))
FROM raw_dataset_record record_data;
"""

SCOPE_SQL = """
BEGIN;
DO $verification$
DECLARE
    admin_id BIGINT;
    editor_id BIGINT;
    manager_id BIGINT;
    viewer_id BIGINT;
    source_item raw_dataset_record%ROWTYPE;
    iri_item raw_dataset_record%ROWTYPE;
    source_route TEXT;
    source_chainage NUMERIC;
    iri_start NUMERIC;
    iri_end NUMERIC;
BEGIN
    SELECT id INTO STRICT admin_id FROM app_user WHERE username = 'admin' AND is_active;
    SELECT id INTO STRICT editor_id FROM app_user WHERE username = 'editor_demo' AND is_active;
    SELECT id INTO STRICT manager_id FROM app_user WHERE username = 'manager_demo' AND is_active;
    SELECT id INTO STRICT viewer_id FROM app_user WHERE username = 'viewer_demo' AND is_active;
    SELECT item.* INTO STRICT source_item FROM raw_dataset_record item
    WHERE item.dataset_key = 'vroad_defects' AND vroad_chainage_m(item.raw_payload) IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM region_demo_review review WHERE review.record_id = item.id)
    ORDER BY item.id LIMIT 1;
    source_route := source_item.raw_payload->>'route_name';
    source_chainage := vroad_chainage_m(source_item.raw_payload);
    DELETE FROM vroad_route_assignment WHERE user_id IN (editor_id, manager_id, viewer_id);
    UPDATE app_user SET branch_id = 'kqldb_1' WHERE id = viewer_id;
    IF NOT vroad_record_visible(source_item.id, admin_id)
       OR vroad_record_visible(source_item.id, editor_id)
       OR vroad_record_visible(source_item.id, 0) THEN
        RAISE EXCEPTION 'Default deny or administrator visibility failed';
    END IF;
    INSERT INTO vroad_route_assignment(user_id, route_name, assigned_by)
    VALUES (editor_id, source_route, admin_id), (manager_id, source_route, admin_id),
           (viewer_id, source_route, admin_id);
    IF NOT vroad_record_visible(source_item.id, editor_id)
       OR NOT vroad_record_visible(source_item.id, manager_id)
       OR vroad_record_visible(source_item.id, viewer_id) THEN
        RAISE EXCEPTION 'Assigned source visibility or unreviewed viewer isolation failed';
    END IF;
    IF EXISTS (SELECT 1 FROM raw_dataset_record item WHERE item.dataset_key = 'vroad_defects'
        AND item.raw_payload->>'route_name' <> source_route AND vroad_record_visible(item.id, editor_id)) THEN
        RAISE EXCEPTION 'Unassigned route leaked';
    END IF;
    INSERT INTO region_demo_review(record_id, branch_id, status, submitted_by)
    VALUES (source_item.id, 'kqldb_1', 'IN_REVIEW', editor_id);
    IF vroad_record_visible(source_item.id, viewer_id) THEN
        RAISE EXCEPTION 'Pending review leaked to viewer';
    END IF;
    UPDATE region_demo_review SET status = 'APPROVED', reviewed_by = manager_id,
        reviewed_at = CURRENT_TIMESTAMP WHERE record_id = source_item.id;
    IF NOT vroad_record_visible(source_item.id, viewer_id) THEN
        RAISE EXCEPTION 'Approved assigned record was not visible';
    END IF;
    UPDATE app_user SET is_active = FALSE WHERE id = editor_id;
    IF vroad_record_visible(source_item.id, editor_id) THEN
        RAISE EXCEPTION 'Inactive account retained source access';
    END IF;
    UPDATE app_user SET is_active = TRUE WHERE id = editor_id;
    UPDATE vroad_route_assignment SET chainage_from_m = source_chainage + 1 WHERE user_id = editor_id;
    IF vroad_record_visible(source_item.id, editor_id) THEN
        RAISE EXCEPTION 'Out-of-range source record leaked';
    END IF;
    UPDATE vroad_route_assignment SET chainage_from_m = source_chainage, chainage_to_m = source_chainage
    WHERE user_id = editor_id;
    IF NOT vroad_record_visible(source_item.id, editor_id) THEN
        RAISE EXCEPTION 'Inclusive chainage boundary failed';
    END IF;
    DELETE FROM vroad_route_assignment WHERE user_id = editor_id;
    IF vroad_record_visible(source_item.id, editor_id) THEN
        RAISE EXCEPTION 'Revoked assignment retained source access';
    END IF;
    SELECT item.* INTO STRICT iri_item FROM raw_dataset_record item
    WHERE item.dataset_key = 'vroad_iri' ORDER BY item.id LIMIT 1;
    iri_start := (iri_item.raw_payload->>'start_m')::NUMERIC;
    iri_end := (iri_item.raw_payload->>'end_m')::NUMERIC;
    INSERT INTO vroad_route_assignment(user_id, route_name, chainage_from_m, chainage_to_m, assigned_by)
    VALUES (editor_id, iri_item.raw_payload->>'road_name', iri_start, iri_end, admin_id);
    IF NOT vroad_record_visible(iri_item.id, editor_id) THEN
        RAISE EXCEPTION 'IRI route-name or segment-scope mapping failed';
    END IF;
    UPDATE vroad_route_assignment SET chainage_to_m = iri_end - 1 WHERE user_id = editor_id;
    IF vroad_record_visible(iri_item.id, editor_id) THEN
        RAISE EXCEPTION 'Partially out-of-scope IRI segment leaked';
    END IF;
END
$verification$;
ROLLBACK;
"""


def run_sql(arguments, sql):
    result = subprocess.run([
        'docker', 'exec', '-i', arguments.container, 'psql', '-X', '-v', 'ON_ERROR_STOP=1',
        '-U', arguments.user, '-d', arguments.database, '-qAt',
    ], input=sql, text=True, encoding='utf-8', capture_output=True, timeout=45)
    if result.returncode:
        raise RuntimeError('PostgreSQL scope verification failed: ' + result.stderr[-1500:])
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description='Transactional VROAD scope verification on existing demo data.')
    parser.add_argument('--container', default='kcht_postgres')
    parser.add_argument('--user', default='kcht_user')
    parser.add_argument('--database', default='kcht_db')
    arguments = parser.parse_args()
    before = run_sql(arguments, CHECKSUM_SQL)
    run_sql(arguments, SCOPE_SQL)
    after = run_sql(arguments, CHECKSUM_SQL)
    if before != after:
        raise RuntimeError('Raw-data checksum changed during verification.')
    print('PASS: default deny, exact routes, account isolation, QC visibility, disabled users, chainage bounds, revocation and IRI extent.')
    print('PASS: transactional fixtures rolled back; original raw-data count and checksum unchanged (' + after + ').')


if __name__ == '__main__':
    main()
