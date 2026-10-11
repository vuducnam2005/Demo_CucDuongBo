#!/usr/bin/env bash
# ==============================================================================
# SCRIPT THỰC THI SCHEMA MIGRATION SẢN XUẤT (FLYWAY RELEASE)
# ==============================================================================
set -euo pipefail

FLYWAY_CONTAINER="${FLYWAY_CONTAINER:-kcht_prod_flyway}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.prod.yml}"
ENV_FILE="${ENV_FILE:-.env.production}"
TARGET_VERSION="${1:-${FLYWAY_TARGET:-}}"

echo "=============================================================================="
echo "[INFO] Khởi động tiến trình Release Migration CSDL trên môi trường sản xuất"
echo "[INFO] Thời gian: $(date -Iseconds)"
echo "=============================================================================="

if [ ! -f "${ENV_FILE}" ]; then
    echo "[ERROR] Tệp môi trường ${ENV_FILE} không tồn tại!" >&2
    exit 1
fi

if [[ ! "${TARGET_VERSION}" =~ ^[0-9]+$ ]]; then
    echo "[ERROR] Phải chỉ định Flyway target tường minh, ví dụ: $0 23" >&2
    exit 1
fi

# 1. Kiểm tra trạng thái các migration đã áp dụng (Flyway info)
echo "[INFO] 1. Kiểm tra trạng thái migration hiện thời..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" run --rm --no-deps flyway info

# 2. Xác thực tính toàn vẹn của các tệp migration trước khi áp dụng (Flyway validate)
echo "[INFO] 2. Xác thực mã băm và thứ tự migration (Flyway validate)..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" run --rm --no-deps flyway validate

# 3. Thực thi migration tiến về phía trước (Flyway migrate)
echo "[INFO] 3. Đang áp dụng migration tới target V${TARGET_VERSION}..."
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" run --rm --no-deps flyway "-target=${TARGET_VERSION}" migrate

# 4. Hiển thị bảng phiên bản CSDL sau khi migrate
echo "[INFO] 4. Phiên bản CSDL sau khi hoàn thành migration:"
docker compose -f "${COMPOSE_FILE}" --env-file "${ENV_FILE}" run --rm --no-deps flyway info

echo "[SUCCESS] Tiến trình Schema Migration sản xuất đã hoàn tất an toàn 100%!"
exit 0
