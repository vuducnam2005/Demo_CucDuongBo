\set ON_ERROR_STOP on

-- Run inside the FINAL maintenance window after tree writes are paused and reconciliation is clean.
BEGIN;

SELECT pg_advisory_xact_lock(hashtext('green_tree_sync_sequence_before_final'));

SELECT setval(
    pg_get_serial_sequence('green_tree_record', 'id'),
    COALESCE((SELECT MAX(id) FROM green_tree_record), 1),
    EXISTS (SELECT 1 FROM green_tree_record)
);

SELECT MAX(id) AS current_max_id,
       pg_get_serial_sequence('green_tree_record', 'id') AS sequence_name
FROM green_tree_record;

COMMIT;
