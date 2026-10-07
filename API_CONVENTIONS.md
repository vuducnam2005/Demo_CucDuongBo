# QUY CHUẨN THIẾT KẾ VÀ BẢO MẬT REST API (API_CONVENTIONS)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0  

---

## 1. NGUYÊN TẮC THIẾT KẾ URL VÀ TÀI NGUYÊN RESTFUL

1. **Định dạng URL:**
   - Sử dụng chữ thường và phân tách bằng dấu gạch nối (`kebab-case`).
   - Danh từ số nhiều đại diện cho tập tài nguyên: `/api/datasets`, `/api/documents`, `/api/roads`.
   - Quan hệ phân cấp (Nested Resources): `/api/datasets/{datasetKey}/records`, `/api/documents/{folderId}/files`.
2. **Phiên bản hóa API:**
   - Tiền tố chung: `/api/v1/...` (Các endpoint tương thích ngược tạm thời có thể dùng `/api/...`).
3. **Các Phương thức HTTP Chuẩn:**
   - `GET`: Truy vấn thông tin (Safe & Idempotent, không làm thay đổi trạng thái hệ thống).
   - `POST`: Tạo mới tài nguyên hoặc kích hoạt tác vụ nạp dữ liệu.
   - `PUT`: Cập nhật toàn bộ tài nguyên (Idempotent).
   - `PATCH`: Cập nhật một phần thuộc tính tài nguyên.
   - `DELETE`: Xóa tài nguyên (thực hiện xóa mềm `is_deleted = true`).

---

## 2. QUY CHUẨN MÃ TRẠNG THÁI HTTP (HTTP STATUS CODES)

| Mã trạng thái (Status Code) | Định nghĩa chuẩn | Áp dụng cụ thể trong hệ thống KCHT ĐB |
| :--- | :--- | :--- |
| **`200 OK`** | Thành công | Trả về dữ liệu truy vấn `GET`, cập nhật thành công `PUT`/`PATCH`. |
| **`201 Created`** | Đã tạo thành công | Tạo mới tài nguyên thành công `POST`, kèm header `Location`. |
| **`204 No Content`** | Thành công không có nội dung | Xóa thành công `DELETE` hoặc xử lý không cần trả về body. |
| **`400 Bad Request`** | Yêu cầu không hợp lệ | Vi phạm validation dữ liệu đầu vào, sai tham số bộ lọc, trường không nằm trong Allowlist. |
| **`401 Unauthorized`** | Chưa xác thực | Không truyền Bearer Token, token không hợp lệ hoặc token đã hết hạn. |
| **`403 Forbidden`** | Không có quyền truy cập | Người dùng đã đăng nhập nhưng không có vai trò hoặc quyền trên dataset yêu cầu. |
| **`404 Not Found`** | Không tìm thấy tài nguyên | Mã dataset không tồn tại, ID công trình không tìm thấy trong CSDL. |
| **`409 Conflict`** | Xung đột dữ liệu | Trùng khóa tự nhiên `record_key`, xung đột phiên bản Optimistic Lock (`version` mismatch). |
| **`422 Unprocessable Entity`** | Sai logic nghiệp vụ | Tọa độ không gian nằm ngoài lãnh thổ Việt Nam, lý trình điểm kết thúc nhỏ hơn lý trình điểm bắt đầu. |
| **`429 Too Many Requests`** | Quá giới hạn tần suất gọi | Vượt quá ngưỡng Rate Limiting được cấu hình. |
| **`500 Internal Server Error`** | Lỗi hệ thống ngoài ý muốn | Lỗi không bắt được phía server. Hệ thống ghi log chi tiết và trả về mã lỗi truy vết `errorId`. |
| **`503 Service Unavailable`** | Dịch vụ tạm thời gián đoạn | MinIO hoặc Database tạm ngưng hoạt động; bảo trì hệ thống. |

---

## 3. CẤU TRÚC PHẢN HỒI LỖI CHUẨN (STANDARD ERROR RESPONSE - RFC 7807)

Mọi phản hồi lỗi từ API (mã $4xx$ và $5xx$) bắt buộc phải tuân theo cấu trúc JSON duy nhất:

```json
{
  "timestamp": "2026-10-05T23:30:00.000Z",
  "status": 400,
  "error": "BAD_REQUEST",
  "errorCode": "KCHT-VAL-001",
  "message": "Dữ liệu yêu cầu không hợp lệ",
  "path": "/api/datasets/tbl_bridge/records",
  "errorId": "err-9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "details": [
    {
      "field": "kmFrom",
      "rejectedValue": -5.2,
      "message": "Lý trình điểm bắt đầu không được là số âm"
    },
    {
      "field": "sort",
      "rejectedValue": "secret_column",
      "message": "Trường sắp xếp không nằm trong danh mục cho phép (Allowlist)"
    }
  ]
}
```

### Quy tắc Bắt buộc:
- **Tuyệt đối không rò rỉ vết lỗi (No Stack Trace Leak):** Không trả về `org.postgresql.util.PSQLException`, class name, file path hoặc truy vấn SQL cho client.
- **Truy vết qua `errorId`:** Mã `errorId` (UUID) được in trong log server kèm toàn bộ stack trace để kỹ sư đối soát khi cần thiết.

---

## 4. QUY CHUẨN VALIDATION DỮ LIỆU ĐẦU VÀO

1. **Sử dụng Jakarta Validation (JSR-380):**
   - `@NotNull`, `@NotBlank`: Bắt buộc nhập cho các trường định danh và khóa.
   - `@Size(min = ..., max = ...)`: Giới hạn độ dài chuỗi để ngăn chặn tràn bộ nhớ hoặc DoS.
   - `@Pattern(regexp = ...)`: Kiểm soát định dạng mã tuyến, mã đơn vị hành chính.
   - `@Positive`, `@PositiveOrZero`: Áp dụng cho lý trình, chiều dài, diện tích công trình.
2. **Kiểm tra nghiệp vụ không gian (Custom Spatial Validators):**
   - Kiểm tra tính hợp lệ của tọa độ EPSG:4326 trước khi gọi hàm PostGIS.

---

## 5. CƠ CHẾ XÁC THỰC VÀ PHÂN QUYỀN (AUTHENTICATION & AUTHORIZATION)

### 5.1 Giao thức Xác thực
- **JWT (JSON Web Token) Stateless:**
  - Client gửi token qua HTTP Header:
    ```http
    Authorization: Bearer <jwt_access_token>
    ```
  - Thuật toán ký: **HMAC-SHA256 (HS256)** hoặc **RSA-SHA256 (RS256)** với khóa bí mật tối thiểu 256 bits.
  - Payload JWT chứa các claim chuẩn: `sub` (userId/username), `roles` (danh sách quyền), `orgId` (đơn vị công tác), `iat`, `exp`.

### 5.2 Mô hình Phân quyền Dựa trên Vai trò (RBAC)
Hệ thống thiết lập 3 vai trò chuẩn:
1. **`ROLE_ADMIN`:** Toàn quyền quản trị hệ thống, nạp/xóa dataset, xem audit log, kích hoạt Import Worker, quản lý tài khoản.
2. **`ROLE_OPERATOR`:** Quản lý hạ tầng đường bộ, cập nhật trạng thái bảo trì, chỉnh sửa lý trình, gắn tài liệu hoàn công.
3. **`ROLE_VIEWER`:** Chỉ đọc (Read-only), tra cứu danh mục, hiển thị bản đồ WebGIS, xem hồ sơ kỹ thuật công khai.

---

## 6. QUY TẮC BẢO VỆ AN TOÀN HỆ THỐNG VÀ THÔNG TIN NHẠY CẢM

### 6.1 Bảo vệ Token và Thông tin Bí mật
- Không bao giờ truyền Token, Secret, API Key hoặc Password qua Query Parameters (URL) vì URL sẽ bị lưu vết trong browser history, proxy log và web server access log.
- Cấu hình Logback Masking để tự động che dấu các header `Authorization`, `Cookie`, `X-API-KEY`.

### 6.2 Bảo vệ Mật khẩu
- Mật khẩu lưu trữ trong bảng `app_user` bắt buộc phải được băm bằng thuật toán **BCrypt** với độ phức tạp $12$. Tuyệt đối không dùng MD5, SHA-1 hoặc lưu plain text.
- Chính sách mật khẩu: Tối thiểu 8 ký tự, bao gồm chữ hoa, chữ thường, chữ số và ký tự đặc biệt.

### 6.3 Quy chuẩn Bảo vệ Tải lên Tệp tin (File Upload Security)
1. **Xác thực Định dạng Tệp (Magic Bytes Validation):** Sử dụng thư viện Apache Tika để kiểm tra nội dung nhị phân thực tế của tệp, ngăn ngừa việc giả mạo phần mở rộng tệp.
2. **Allowlist Định dạng Cho Phép:**
   - Tài liệu kỹ thuật: `application/pdf`, `application/vnd.ms-excel`, `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.
   - Hình ảnh hiện trường: `image/jpeg`, `image/png`, `image/webp`.
   - Bản vẽ kỹ thuật / CAD: `application/dwg`, `application/dxf` (hoặc nén zip có kiểm tra nội dung).
   - **Cấm hoàn toàn:** `.exe`, `.bat`, `.sh`, `.jsp`, `.php`, `.py`, `.js`, `.html`.
3. **Lưu trữ Tên tệp Băm An toàn trên MinIO/S3:**
   - Tệp sau khi upload được đặt tên theo quy tắc: `{bucket}/{dataset_code}/{year}/{uuid}.{ext}`.
   - Tên gốc `original_name` chỉ được lưu trong database metadata.
4. **Truy cập Tệp qua Pre-signed URL:**
   - Không public bucket MinIO ra ngoài Internet.
   - Người dùng tải tệp phải gọi API `/api/documents/{fileId}/download-url` để nhận URL có chữ ký điện tử với thời hạn sống tối đa 15 phút.

### 6.4 Bảo vệ Các Endpoint Quản trị (Admin Endpoints)
- Các endpoint nhạy cảm được cấu hình chặn tầng mạng và tầng ứng dụng:
  - `/api/admin/**`: Yêu cầu quyền `ROLE_ADMIN`.
  - `/api/import/**`: Chỉ cho phép gọi từ nội bộ hoặc người dùng có quyền `ROLE_ADMIN`.
  - `/actuator/**`: Ngoại trừ `/actuator/health` và `/actuator/info`, toàn bộ actuator endpoints bị vô hiệu hóa hoặc chỉ truy cập được qua IP nội bộ (Localhost/Docker internal).

---

## 7. QUY CHUẨN TÀI LIỆU HÓA OPENAPI 3.0 (SWAGGER)

- Toàn bộ Controller và DTO phải được chú thích đầy đủ bằng các annotation của **SpringDoc OpenAPI 3**:
  - `@Tag(name = "...", description = "...")`: Nhóm chức năng theo nghiệp vụ.
  - `@Operation(summary = "...", description = "...")`: Diễn giải mục đích endpoint bằng tiếng Việt có dấu.
  - `@ApiResponse`: Khai báo đầy đủ các trường hợp trả về: `200`, `400`, `401`, `403`, `404`, `500`.
  - `@Schema`: Khai báo ví dụ (`example`), mô tả (`description`) và tính bắt buộc (`requiredMode`) trên từng trường DTO.
- Đường dẫn truy cập công khai:
  - Swagger UI: `http://localhost:8089/swagger-ui/index.html`
  - OpenAPI JSON Spec: `http://localhost:8089/v3/api-docs`
