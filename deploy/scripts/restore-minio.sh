#!/usr/bin/env bash
# ==============================================================================
# SCRIPT PHỤC HỒI VÙNG LƯU TRỮ ĐỐI TƯỢNG MINIO / S3 (KCHT ĐB)
# ==============================================================================
set -euo pipefail

if [ "$#" -lt 1 ]; then
    echo "Sử dụng: $0 <đường_dẫn_tệp_tar_gz>"
    echo "Ví dụ:   $0 ./deploy/backups/minio/minio_buckets_20261006_030000.tar.gz"
    exit 1
fi

ARCHIVE_FILE="$1"
TEMP_EXTRACT_DIR="/tmp/kcht_minio_restore_temp"

if [ ! -f "${ARCHIVE_FILE}" ]; then
    echo "[ERROR] Tệp lưu trữ không tồn tại: ${ARCHIVE_FILE}" >&2
    exit 1
fi

echo "=============================================================================="
echo "[INFO] Bắt đầu phục hồi đối tượng MinIO từ: ${ARCHIVE_FILE}"
echo "=============================================================================="

# Giải nén
rm -rf "${TEMP_EXTRACT_DIR}"
mkdir -p "${TEMP_EXTRACT_DIR}"
tar -xzf "${ARCHIVE_FILE}" -C "${TEMP_EXTRACT_DIR}"

# Đưa vào container mc
docker cp "${TEMP_EXTRACT_DIR}" "kcht_prod_minio_init:/tmp/restore_minio"

# Đồng bộ ngược lại các bucket
docker exec -i kcht_prod_minio_init /bin/sh -c "
  /usr/bin/mc alias set myminio http://minio:9000 \${MINIO_ROOT_USER} \${MINIO_ROOT_PASSWORD};
  if [ -d /tmp/restore_minio/kcht-documents ]; then
    /usr/bin/mc mirror --overwrite /tmp/restore_minio/kcht-documents myminio/kcht-documents;
  fi
  if [ -d /tmp/restore_minio/kcht-media ]; then
    /usr/bin/mc mirror --overwrite /tmp/restore_minio/kcht-media myminio/kcht-media;
  fi
  rm -rf /tmp/restore_minio;
  echo '[INFO] Đã đồng bộ hoàn tất dữ liệu nhị phân vào MinIO.';
"

rm -rf "${TEMP_EXTRACT_DIR}"
echo "[SUCCESS] Phục hồi vùng lưu trữ MinIO thành công!"
exit 0
