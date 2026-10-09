# VROAD — bản xem thử trên VPS

Ngày triển khai: **08/10/2026 (UTC+07:00)**.

- HTTPS: `https://vroad.160-30-136-28.sslip.io`
- Release: `/opt/vroad-preview/release-20261008`.
- Compose project: `vroad-preview`; dùng database/volume riêng để không ảnh hưởng các hệ thống đã có trên VPS.
- Nginx chỉ bind `127.0.0.1:18090`; PostgreSQL và backend không công bố cổng trên host.
- Caddy hiện hữu được bổ sung đúng hai host block cho domain mới, validate rồi reload; không restart container hoặc đổi route cũ.
- Bản demo độc lập; không phải website chính thức của cơ quan quản lý đường bộ.

## Tài khoản

`admin`, `editor_demo`, `manager_demo`, `viewer_demo` có bốn mật khẩu mới, khác nhau. Không dùng mật khẩu root VPS, mật khẩu demo cũ hay phiên đăng nhập website nguồn. JWT secret được tạo mới; refresh session sao chép từ local đã bị thu hồi trước khi bật backend.

Thông tin truy cập chỉ nằm trong file riêng tư `.private/ACCESS_PRIVATE.txt` tại release; bản tải về ở `scratch/vroad-preview/credentials/ACCESS_PRIVATE.txt`, bị Git bỏ qua. Không copy mật khẩu vào README, ảnh chụp hoặc commit. Đơn vị được phân hai tuyến khảo sát theo đúng tên có trong nguồn, gắn nhãn phạm vi `DEMO`. Viewer chỉ thấy dữ liệu được kiểm tra nội bộ; dữ liệu chưa duyệt vẫn bị ẩn.

## Dữ liệu và tính toàn vẹn

Dump PostgreSQL gồm 6.461 bản ghi raw: 2.614 tài sản khảo sát, 3.404 hư hỏng, 221 đoạn IRI, 169 quốc lộ, 51 cao tốc và 2 bản ghi vùng mô phỏng. Đây là phạm vi đã nhập, không phải toàn bộ dữ liệu KCHT quốc gia. Nguồn, dump và hồ sơ không đưa vào Git.

`database.dump` có SHA-256 trong `SHA256SUMS.json`; tất cả 34 file release đã kiểm tra tại VPS trước khi restore. Restore chỉ thực hiện sau khi xác minh database preview chưa có `raw_dataset_record`. Checksum 6.461 bản ghi raw trước/sau triển khai khớp: `5ab4190a71a1879593233fa7ad410084`. Không chạy importer hoặc ghi vào website nguồn.

## Vận hành

```sh
cd /opt/vroad-preview/release-20261008
docker compose -p vroad-preview --env-file .env -f compose.yaml ps
docker compose -p vroad-preview --env-file .env -f compose.yaml logs --tail=50 backend
curl --fail https://vroad.160-30-136-28.sslip.io/actuator/health
```

Không chạy `down -v`, không dùng Compose project của ứng dụng khác, không đưa `.env` vào output. Muốn tạm dừng chỉ demo: `docker compose -p vroad-preview --env-file .env -f compose.yaml stop`. Backup database preview bằng `pg_dump` trước thay đổi tiếp theo. Backup Caddy trước bổ sung domain nằm tại `/opt/vroad-preview/audit/Caddyfile.before-20261008`.

## Kiểm chứng ngày triển khai

- Vitest: **100/100**; TypeScript/Vite build và ESLint đạt. Hai fixture cũ được sửa đúng nhãn và contract phân trang; chạy lại **5/5** test liên quan đạt.
- Backend: **49/49** test controller/phân quyền mục tiêu đạt. Test SQL phân tuyến kiểm tra default-deny, ranh giới lý trình, thu hồi quyền và viewer chưa duyệt, rollback fixture và giữ nguyên checksum raw.
- Helper deploy: `python deploy/preview/test_prepare.py`; kiểm tra secret riêng, giữ cấu hình cũ, checksum, path traversal và giao dịch đổi tài khoản.
- Playwright Chrome trên **HTTPS VPS thật**: **5/5**, gồm đăng nhập bốn vai trò và bản đồ OpenLayers; không dựa vào mock API.
- HTTP: frontend/health 200, anonymous API 401, admin endpoint với ba vai trò vùng 403, `/actuator/env` 404; Secure/HttpOnly refresh cookie, refresh và logout/revocation đạt.
- Tất cả container có từ trước giữ nguyên `StartedAt` và `RestartCount`; IP gốc vẫn 200, dịch vụ cổng 8088 vẫn 302, domain confessions vẫn 200.

**Giới hạn:** không tuyên bố full Maven suite đạt. Hai cookie integration test cũ phụ thuộc cấu hình DB/mật khẩu fixture đã lỗi ở lần chạy đầu; chúng không được tính vào 49 test mục tiêu. Luồng auth/cookie được kiểm chứng bổ sung trên HTTPS thật. Logo chính thức, hợp đồng VroadAI trực tiếp, nghiệm thu nghiệp vụ và thu thập dữ liệu còn lại chưa hoàn tất; bản này phục vụ xem thử, không phải nghiệm thu production.
