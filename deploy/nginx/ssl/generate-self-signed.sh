#!/usr/bin/env bash
# ==============================================================================
# Script sinh chứng chỉ TLS Self-Signed phục vụ môi trường Staging / Testing
# LƯU Ý: Với môi trường Production, sử dụng chứng chỉ số hợp lệ từ Let's Encrypt / CA
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SSL_DIR="${SCRIPT_DIR}"
DAYS_VALID=365

mkdir -p "${SSL_DIR}"

if [ -f "${SSL_DIR}/kcht_tls.crt" ] && [ -f "${SSL_DIR}/kcht_tls.key" ]; then
    echo "[INFO] Chứng chỉ TLS đã tồn tại tại ${SSL_DIR}. Bỏ qua bước sinh mới."
    exit 0
fi

echo "[INFO] Đang sinh cặp khóa RSA 2048-bit và chứng chỉ tự ký X.509..."

openssl req -x509 -nodes -days ${DAYS_VALID} -newkey rsa:2048 \
  -keyout "${SSL_DIR}/kcht_tls.key" \
  -out "${SSL_DIR}/kcht_tls.crt" \
  -subj "/C=VN/ST=HaNoi/L=HaNoi/O=CucDuongBoVietNam/OU=KCHT/CN=kcht.drvn.gov.vn" \
  -addext "subjectAltName=DNS:kcht.drvn.gov.vn,DNS:localhost,IP:127.0.0.1"

chmod 600 "${SSL_DIR}/kcht_tls.key"
chmod 644 "${SSL_DIR}/kcht_tls.crt"

echo "[SUCCESS] Đã sinh thành công chứng chỉ TLS tự ký tại:"
echo " - Private Key: ${SSL_DIR}/kcht_tls.key"
echo " - Certificate: ${SSL_DIR}/kcht_tls.crt"
