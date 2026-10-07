#!/usr/bin/env bash
# ==============================================================================
# SCRIPT SAO LƯU CƠ SỞ DỮ LIỆU POSTGRESQL 16 + POSTGIS (KCHT ĐB)
# ==============================================================================
set -euo pipefail

# 1. Cấu hình tham số
CONTAINER_NAME="${PG_CONTAINER:-kcht_prod_postgres}"
DB_NAME="${POSTGRES_DB:-kcht_prod_db}"
DB_USER="${POSTGRES_USER:-kcht_prod_admin}"
BACKUP_DIR="${BACKUP_DEST_DIR:-/backups}"
HOST_BACKUP_DIR="${HOST_BACKUP_DIR:-./deploy/backups/postgres}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILENAME="kcht_db_backup_${TIMESTAMP}.dump"
CONTAINER_BACKUP_PATH="${BACKUP_DIR}/${BACKUP_FILENAME}"
HOST_BACKUP_PATH="${HOST_BACKUP_DIR}/${BACKUP_FILENAME}"

mkdir -p "${HOST_BACKUP_DIR}"

echo "=============================================================================="
echo "[INFO] Bắt đầu tiến trình sao lưu CSDL PostgreSQL: ${DB_NAME}"
echo "[INFO] Thời gian: $(date -Iseconds)"
echo "[INFO] Container: ${CONTAINER_NAME}"
echo "=============================================================================="

# 2. Kiểm tra container đang chạy
if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
    echo "[ERROR] Container ${CONTAINER_NAME} không hoạt động! Dừng tiến trình." >&2
    exit 1
fi

# 3. Tạo thư mục sao lưu trong container nếu chưa có
docker exec "${CONTAINER_NAME}" mkdir -p "${BACKUP_DIR}"

# 4. Thực thi pg_dump định dạng nén nhị phân tối ưu (-Fc: Custom format với nén zlib level 9)
echo "[INFO] Đang kết xuất dữ liệu CSDL qua pg_dump (-Fc, compression 9)..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD:-}" "${CONTAINER_NAME}" \
    pg_dump -U "${DB_USER}" -d "${DB_NAME}" \
    -F c -b -v -Z 9 \
    --exclude-table-data='*.audit_log_temp_*' \
    -f "${CONTAINER_BACKUP_PATH}"

# 4. Sao chép bản sao lưu ra thư mục lưu trữ của máy chủ (Host)
echo "[INFO] Đang đồng bộ tệp dump từ container ra host..."
docker cp "${CONTAINER_NAME}:${CONTAINER_BACKUP_PATH}" "${HOST_BACKUP_PATH}"

# 5. Kiểm tra tính toàn vẹn của tệp dump bằng pg_restore --list
echo "[INFO] Đang xác thực tính toàn vẹn của tệp sao lưu..."
if docker exec "${CONTAINER_NAME}" pg_restore --list "${CONTAINER_BACKUP_PATH}" > /dev/null 2>&1; then
    echo "[SUCCESS] Tệp sao lưu hợp lệ (TOC header và checksum khối nén toàn vẹn)."
else
    echo "[ERROR] Tệp sao lưu bị hỏng hoặc lỗi TOC header!" >&2
    exit 2
fi

# 6. Tạo mã băm SHA-256 để chống giả mạo / phát hiện sai lệch bit
echo "[INFO] Đang tạo mã băm SHA-256 checksum..."
if command -v sha256sum > /dev/null 2>&1; then
    (cd "${HOST_BACKUP_DIR}" && sha256sum "${BACKUP_FILENAME}" > "${BACKUP_FILENAME}.sha256")
else
    docker exec "${CONTAINER_NAME}" sha256sum "${CONTAINER_BACKUP_PATH}" > "${HOST_BACKUP_PATH}.sha256"
fi

BACKUP_SIZE=$(ls -lh "${HOST_BACKUP_PATH}" | awk '{print $5}')
echo "[SUCCESS] Sao lưu thành công: ${HOST_BACKUP_PATH} (Dung lượng: ${BACKUP_SIZE})"
echo "[SUCCESS] Mã băm lưu tại: ${HOST_BACKUP_PATH}.sha256"

# 7. Xoay vòng bản sao lưu cũ (Prune backups older than RETENTION_DAYS)
echo "[INFO] Đang dọn dẹp các bản sao lưu cũ quá ${RETENTION_DAYS} ngày..."
find "${HOST_BACKUP_DIR}" -type f -name "kcht_db_backup_*.dump" -mtime +"${RETENTION_DAYS}" -exec rm -f {} \;
find "${HOST_BACKUP_DIR}" -type f -name "kcht_db_backup_*.dump.sha256" -mtime +"${RETENTION_DAYS}" -exec rm -f {} \;

echo "[COMPLETED] Tiến trình sao lưu CSDL hoàn tất thành công lúc $(date -Iseconds)."
exit 0
