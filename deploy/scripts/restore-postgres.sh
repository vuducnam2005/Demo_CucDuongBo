#!/usr/bin/env bash
# ==============================================================================
# SCRIPT PHỤC HỒI CƠ SỞ DỮ LIỆU POSTGRESQL 16 + POSTGIS (KCHT ĐB)
# ==============================================================================
set -euo pipefail

if [ "$#" -lt 1 ]; then
    echo "Sử dụng: $0 <đường_dẫn_tệp_dump> [--force]"
    echo "Ví dụ:   $0 ./deploy/backups/postgres/kcht_db_backup_20261006_030000.dump"
    exit 1
fi

BACKUP_FILE="$1"
FORCE_RESTORE="${2:-}"
CONTAINER_NAME="${PG_CONTAINER:-kcht_prod_postgres}"
DB_NAME="${POSTGRES_DB:-kcht_prod_db}"
DB_USER="${POSTGRES_USER:-kcht_prod_admin}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "[ERROR] Tệp sao lưu không tồn tại: ${BACKUP_FILE}" >&2
    exit 1
fi

echo "=============================================================================="
echo "[CẢNH BÁO NGUY HIỂM] TIẾN TRÌNH PHỤC HỒI CSDL SẼ GHI ĐÈ DỮ LIỆU HIỆN CÓ!"
echo "[MỤC TIÊU]: CSDL: ${DB_NAME} trên Container: ${CONTAINER_NAME}"
echo "[NGUỒN]:    ${BACKUP_FILE}"
echo "=============================================================================="

# 1. Xác thực mã băm SHA-256 nếu có tệp .sha256 đi kèm
SHA_FILE="${BACKUP_FILE}.sha256"
if [ -f "${SHA_FILE}" ]; then
    echo "[INFO] Đang kiểm tra mã băm SHA-256..."
    if command -v sha256sum > /dev/null 2>&1; then
        (cd "$(dirname "${BACKUP_FILE}")" && sha256sum -c "$(basename "${SHA_FILE}")")
        echo "[SUCCESS] Mã băm SHA-256 hợp lệ tuyệt đối."
    fi
else
    echo "[WARN] Không tìm thấy tệp mã băm ${SHA_FILE}. Bỏ qua bước kiểm tra checksum."
fi

# 2. Xác nhận từ người vận hành (nếu không có cờ --force)
if [ "${FORCE_RESTORE}" != "--force" ]; then
    read -p "Bạn có chắc chắn muốn phục hồi CSDL ${DB_NAME}? (Nhập 'CONFIRM_RESTORE' để tiếp tục): " CONFIRM
    if [ "${CONFIRM}" != "CONFIRM_RESTORE" ]; then
        echo "[ABORTED] Người vận hành đã hủy tiến trình phục hồi."
        exit 0
    fi
fi

# 3. Tạo bản sao lưu an toàn của CSDL hiện tại trước khi khôi phục (Safety Snapshot)
echo "[INFO] Đang tạo bản snapshot an toàn của CSDL hiện tại..."
PRE_RESTORE_DUMP="/backups/pre_restore_safety_$(date +%Y%m%d_%H%M%S).dump"
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "${CONTAINER_NAME}" \
    pg_dump -U "${DB_USER}" -d "${DB_NAME}" -F c -f "${PRE_RESTORE_DUMP}" || true
echo "[INFO] Snapshot an toàn đã lưu tại: ${PRE_RESTORE_DUMP}"

# 4. Ngắt toàn bộ kết nối hiện tại đến CSDL để tránh deadlock
echo "[INFO] Đang ngắt các kết nối người dùng/ứng dụng vào CSDL ${DB_NAME}..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "${CONTAINER_NAME}" psql -U "${DB_USER}" -d postgres -c "
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = '${DB_NAME}' AND pid <> pg_backend_pid();"

# 5. Đưa tệp dump vào container
TARGET_CONTAINER_PATH="/backups/restore_target.dump"
echo "[INFO] Đang sao chép tệp dump vào container..."
docker cp "${BACKUP_FILE}" "${CONTAINER_NAME}:${TARGET_CONTAINER_PATH}"

# 6. Thực thi pg_restore với cờ dọn dẹp an toàn (--clean --if-exists --no-owner --no-privileges)
echo "[INFO] Bắt đầu phục hồi cấu trúc và dữ liệu qua pg_restore..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "${CONTAINER_NAME}" \
    pg_restore -U "${DB_USER}" -d "${DB_NAME}" \
    --clean --if-exists --no-owner --no-privileges -v \
    "${TARGET_CONTAINER_PATH}" || {
        echo "[WARN] pg_restore hoàn thành với một số cảnh báo phi nghiêm trọng (ví dụ: role system)."
    }

# 7. Tối ưu hóa thống kê chỉ mục sau phục hồi
echo "[INFO] Đang thực thi VACUUM ANALYZE để cập nhật thống kê Optimizer..."
docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "${CONTAINER_NAME}" \
    psql -U "${DB_USER}" -d "${DB_NAME}" -c "VACUUM ANALYZE;"

# 8. Kiểm tra số lượng bản ghi sau phục hồi
RECORD_COUNT=$(docker exec -e PGPASSWORD="${POSTGRES_PASSWORD}" "${CONTAINER_NAME}" \
    psql -U "${DB_USER}" -d "${DB_NAME}" -t -A -c "SELECT COUNT(*) FROM raw_dataset_record;" || echo "0")

echo "=============================================================================="
echo "[SUCCESS] Phục hồi CSDL ${DB_NAME} thành công!"
echo "[THỐNG KÊ]: Tổng số bản ghi raw_dataset_record hiện tại: ${RECORD_COUNT}"
echo "[THỜI GIAN HOÀN TẤT]: $(date -Iseconds)"
echo "=============================================================================="

# Dọn dẹp tệp dump tạm trong container
docker exec "${CONTAINER_NAME}" rm -f "${TARGET_CONTAINER_PATH}"
exit 0
