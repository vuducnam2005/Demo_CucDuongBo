#!/usr/bin/env bash
# ==============================================================================
# SCRIPT SAO LƯU VÙNG LƯU TRỮ ĐỐI TƯỢNG MINIO / S3 (KCHT ĐB)
# ==============================================================================
set -euo pipefail

CONTAINER_MC="kcht_prod_minio_init"
HOST_BACKUP_DIR="${HOST_S3_BACKUP_DIR:-./deploy/backups/minio}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
TAR_ARCHIVE="${HOST_BACKUP_DIR}/minio_buckets_${TIMESTAMP}.tar.gz"

mkdir -p "${HOST_BACKUP_DIR}/temp"

echo "=============================================================================="
echo "[INFO] Bắt đầu sao lưu toàn bộ bucket MinIO S3..."
echo "[INFO] Thời gian: $(date -Iseconds)"
echo "=============================================================================="

# Sử dụng MinIO Client (mc) mirror toàn bộ đối tượng từ cluster sang thư mục tạm
docker exec -i kcht_prod_minio_init /bin/sh -c "
  /usr/bin/mc alias set myminio http://minio:9000 \${MINIO_ROOT_USER} \${MINIO_ROOT_PASSWORD};
  /usr/bin/mc mirror --overwrite myminio/kcht-documents /tmp/backup_minio/kcht-documents;
  /usr/bin/mc mirror --overwrite myminio/kcht-media /tmp/backup_minio/kcht-media;
  echo '[INFO] Đã kết xuất xong tệp nhị phân từ các bucket sang /tmp/backup_minio.';
"

echo "[INFO] Đang sao chép từ container ra máy chủ lưu trữ..."
docker cp "kcht_prod_minio_init:/tmp/backup_minio" "${HOST_BACKUP_DIR}/temp/"

echo "[INFO] Đang nén kho lưu trữ thành định dạng tar.gz..."
tar -czf "${TAR_ARCHIVE}" -C "${HOST_BACKUP_DIR}/temp/backup_minio" .

# Dọn dẹp thư mục tạm
rm -rf "${HOST_BACKUP_DIR}/temp"
docker exec -i kcht_prod_minio_init rm -rf /tmp/backup_minio

# Tạo checksum
if command -v sha256sum > /dev/null 2>&1; then
    (cd "${HOST_BACKUP_DIR}" && sha256sum "$(basename "${TAR_ARCHIVE}")" > "$(basename "${TAR_ARCHIVE}").sha256")
fi

BACKUP_SIZE=$(ls -lh "${TAR_ARCHIVE}" | awk '{print $5}')
echo "[SUCCESS] Đã sao lưu MinIO thành công vào: ${TAR_ARCHIVE} (${BACKUP_SIZE})"
echo "[COMPLETED] Tiến trình sao lưu MinIO kết thúc lúc $(date -Iseconds)."
exit 0
