-- ==============================================================================
-- KIỂM TRA POSTGIS, PHIÊN BẢN VÀ CÁC TÍNH NĂNG KHÔNG GIAN ĐỊA LÝ
-- ==============================================================================

\echo '--- 1. KIỂM TRA PHIÊN BẢN POSTGRESQL & POSTGIS ---'
SELECT 
    version() AS postgresql_version,
    PostGIS_Full_Version() AS postgis_version;

\echo '--- 2. KIỂM TRA TIỆN ÍCH MỞ RỘNG ĐÃ CÀI ĐẶT ---'
SELECT extname, extversion 
FROM pg_extension 
WHERE extname IN ('postgis', 'pg_trgm');

\echo '--- 3. KIỂM TRA THỜI GIAN VÀ TIMEZONE HIỆN TẠI (YÊU CẦU UTC) ---'
SHOW timezone;
SELECT CURRENT_TIMESTAMP AS current_time_utc;

\echo '--- 4. KIỂM TRA TẠO ĐIỂM (POINT) WGS 84 (EPSG:4326) VÀ GEOJSON ---'
SELECT 
    ST_AsText(ST_SetSRID(ST_MakePoint(106.709518, 21.967163), 4326)) AS wkt_point,
    ST_AsGeoJSON(ST_SetSRID(ST_MakePoint(106.709518, 21.967163), 4326)) AS geojson_point;

\echo '--- 5. KIỂM TRA TẠO ĐƯỜNG (LINESTRING) VÀ TÍNH CHIỀU DÀI TRẮC ĐỊA (MÉT) ---'
SELECT 
    ST_AsText(ST_SetSRID(ST_MakeLine(ST_MakePoint(106.71, 21.97), ST_MakePoint(106.71, 21.96)), 4326)) AS wkt_line,
    ROUND(ST_Length(ST_SetSRID(ST_MakeLine(ST_MakePoint(106.71, 21.97), ST_MakePoint(106.71, 21.96)), 4326)::geography)::numeric, 2) AS length_meters;

\echo '--- 6. KIỂM TRA TRUY VẤN HỘP BAO KHÔNG GIAN (SPATIAL ENVELOPE OVERLAP) ---'
SELECT 
    ST_Intersects(
        ST_SetSRID(ST_MakePoint(106.709518, 21.967163), 4326),
        ST_MakeEnvelope(106.0, 21.0, 107.0, 22.0, 4326)
    ) AS point_inside_northern_box;

\echo '--- 7. KIỂM TRA TÌM KIẾM MỜ TIẾNG VIỆT (TRIGRAM SIMILARITY) ---'
SELECT 
    similarity('Cầu Dần Xây', 'Cau Dan Xay') AS similarity_score,
    show_trgm('Cầu Dần Xây') AS trigrams;
