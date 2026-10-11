\set ON_ERROR_STOP on
\set branch_id 'kqldb_1'
\set sample_record_id 'replace-with-existing-record-id'
\set sample_name_pattern '%cay%'

-- Run after ANALYZE green_tree_record on staging/production.
-- Review plans manually; do not treat execution success as an index-use guarantee.
EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT tree.id, tree.record_id, ST_X(tree.geom), ST_Y(tree.geom)
FROM green_tree_record tree
WHERE tree.is_deleted = FALSE
  AND tree.geom IS NOT NULL
  AND tree.geom && ST_MakeEnvelope(105.0, 8.0, 110.0, 24.0, 4326)
ORDER BY tree.id
LIMIT 501;

EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT *
FROM green_tree_record
WHERE record_id = :'sample_record_id';

EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT id, record_id, name
FROM green_tree_record
WHERE branch_id = :'branch_id' AND is_deleted = FALSE
ORDER BY id
LIMIT 100;

EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT id, record_id, name, updated_at
FROM green_tree_record
WHERE is_deleted = FALSE
ORDER BY id
LIMIT 100 OFFSET 0;

EXPLAIN (ANALYZE, BUFFERS, VERBOSE)
SELECT id, record_id, name, species_name
FROM green_tree_record
WHERE is_deleted = FALSE
  AND (name ILIKE :'sample_name_pattern' OR species_name ILIKE :'sample_name_pattern')
ORDER BY id
LIMIT 100;
