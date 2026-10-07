#!/usr/bin/env bash
# ==============================================================================
# SCRIPT CHẠY TIẾN TRÌNH IMPORT / REFRESH DỮ LIỆU ĐỘC LẬP (IMPORT WORKER)
# ==============================================================================
set -euo pipefail

DATA_DIR="${1:-/data/kcht_json}"
BATCH_SIZE="${2:-500}"
DRY_RUN="${3:-false}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.production}"

echo "=============================================================================="
echo "[INFO] Bắt đầu tác vụ nạp/làm mới dữ liệu hạ tầng (KCHT Import Worker)"
echo "[INFO] Thư mục nguồn dữ liệu: ${DATA_DIR}"
echo "[INFO] Kích thước lô (Batch):  ${BATCH_SIZE}"
echo "[INFO] Chế độ kiểm tra thử:   ${DRY_RUN}"
echo "=============================================================================="

# Chạy backend ở chế độ một tác vụ CLI độc lập, không mở cổng dịch vụ API
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" run --rm \
  -e KCHT_DATA_DIR="${DATA_DIR}" \
  -e KCHT_IMPORT_BATCH_SIZE="${BATCH_SIZE}" \
  -e KCHT_IMPORT_DRY_RUN="${DRY_RUN}" \
  -v "${DATA_DIR}:${DATA_DIR}:ro" \
  backend \
  java -XX:MaxRAMPercentage=75.0 -XX:+UseZGC -XX:+ZGenerational \
       -Dspring.profiles.active=prod \
       -Dkcht.data.dir="${DATA_DIR}" \
       -Dkcht.import.batch-size="${BATCH_SIZE}" \
       -Dkcht.import.dry-run="${DRY_RUN}" \
       -jar app.jar --kcht.worker.mode=true

echo "[SUCCESS] Tác vụ nạp/làm mới dữ liệu hạ tầng đã hoàn thành thành công!"
exit 0
