# CHECKLIST ĐỊNH NGHĨA HOÀN THÀNH (DEFINITION_OF_DONE)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0  
**Áp dụng:** Bắt buộc cho mọi Task kỹ thuật, User Story và Pull Request.

---

## 1. MỤC TIÊU VÀ NGUYÊN TẮC ÁP DỤNG

Một task **chỉ được coi là hoàn thành (DONE)** khi và chỉ khi thỏa mãn 100% các tiêu chí trong checklist dưới đây. Bất kỳ sự thiếu sót nào về kiểm thử, tài liệu hoặc an toàn dữ liệu đều bị coi là **chưa hoàn thành (Incomplete)** và không được phép bàn giao sang giai đoạn tiếp theo.

---

## 2. CHECKLIST BẮT BUỘC ĐÁNH GIÁ TỪNG NHÓM TIÊU CHÍ

### Nhóm 1: Chất lượng Mã nguồn và Thiết kế (Code Quality & Architecture)
- [ ] **Modular Monolith:** Mã nguồn tuân thủ đúng cấu trúc module nghiệp vụ; không xuất hiện sự phụ thuộc vòng (Circular Dependency).
- [ ] **Phân tầng Trách nhiệm:** Controller không chứa logic cơ sở dữ liệu; Service xử lý nghiệp vụ; Repository thuần túy truy xuất dữ liệu.
- [ ] **Java 21 Standards:** DTOs sử dụng Java Record; không sử dụng Java serialization; dùng Virtual Threads cho I/O nặng khi cần.
- [ ] **Frontend Standards (khi có code FE):** TypeScript không dùng `any`; sử dụng TanStack Query cho server state; chỉ dùng **một UI Library duy nhất**.
- [ ] **Clean Code:** Đã xóa toàn bộ câu lệnh debug thừa (`System.out.println`, `console.log`), không có dead code hoặc comment rác.

### Nhóm 2: An toàn Thông tin và Bảo mật (Security & Access Control)
- [ ] **Bảo vệ Secret:** Tuyệt đối không commit password, private key, token, credential vào git hoặc tài liệu.
- [ ] **Chống SQL Injection:** Toàn bộ truy vấn SQL/JPQL sử dụng Parameterized Query hoặc Criteria API. Tuyệt đối không nối chuỗi thô.
- [ ] **Validation Dữ liệu:** Tất cả Request DTO đều có Jakarta Validation annotations (`@NotNull`, `@Size`, `@Pattern`, `@Positive`).
- [ ] **Phân quyền Truy cập:** Các endpoint đều được bảo vệ bởi Spring Security hoặc kiểm tra Role rõ ràng (`ROLE_ADMIN`, `ROLE_OPERATOR`, `ROLE_VIEWER`).
- [ ] **An toàn Tải tệp (File Upload):** Kiểm tra MIME Type thực tế qua Magic Bytes (Apache Tika); chặn hoàn toàn các tệp thực thi; tên file trên MinIO là UUID.
- [ ] **Ẩn thông tin nhạy cảm:** Log không chứa token, cookie hoặc mật khẩu người dùng.

### Nhóm 3: Toàn vẹn Dữ liệu và Quản lý Schema (Data Integrity & Flyway)
- [ ] **Flyway Migration:** Mọi thay đổi cấu trúc bảng đều có file migration `db/migration/V{N}__{name}.sql`. Không bật `hibernate.ddl-auto: update`.
- [ ] **Không làm Mất Dữ liệu (Zero Data Loss):** Migration bảo đảm tính lũy tiến, không xóa cột/bảng đang lưu trữ dữ liệu thực tế mà chưa có bước chuyển dịch an toàn.
- [ ] **Kịch bản Hoàn tác (Rollback Plan):** Có phương án hoặc script hoàn tác đi kèm trong trường hợp migration gặp sự cố.
- [ ] **Kiểm tra Checksum & Trùng lặp:** Dữ liệu nạp từ JSON được kiểm tra mã băm SHA-256; đảm bảo `0 duplicate records` theo khóa tự nhiên `(dataset_code, record_key)`.
- [ ] **Đối soát Dữ liệu (Reconciliation):** Đã kiểm tra đối soát số dòng: $\text{Read} = \text{Inserted} + \text{Updated} + \text{Skipped} + \text{Failed}$.

### Nhóm 4: Dữ liệu Không gian Địa lý PostGIS (Spatial Standards)
- [ ] **Chuẩn Tọa độ:** Toàn bộ dữ liệu tọa độ lưu trong `asset_geometry` tuân thủ hệ quy chiếu chuẩn **EPSG:4326 (WGS 84)**.
- [ ] **Lọc Tọa độ Hợp lệ:** Đã loại trừ các tọa độ $(0, 0)$, `null` hoặc tọa độ nằm ngoài lãnh thổ Việt Nam ($102 \le X \le 112, 8 \le Y \le 24$).
- [ ] **Chỉ mục Không gian:** Cột hình học `geom` được đánh chỉ mục `GiST` phục vụ tối ưu hóa truy vấn không gian.
- [ ] **Chuẩn GeoJSON:** Endpoint trả về dữ liệu bản đồ tuân thủ RFC 7946 FeatureCollection.

### Nhóm 5: Kiểm thử Tự động (Automated Testing & Verification)
- [ ] **Unit Tests:** Đã viết unit test cho toàn bộ logic mới trong Service và Validator.
- [ ] **Integration Tests:** Đã viết integration test cho các endpoint API chính và truy vấn PostGIS phức tạp.
- [ ] **Kiểm thử Hồi quy (Regression Test):** Chạy toàn bộ test suite và đạt kết quả:
  ```text
  [INFO] BUILD SUCCESS
  [INFO] Tests run: XX, Failures: 0, Errors: 0, Skipped: 0
  ```
- [ ] **Kiểm thử Biên (Edge Cases):** Đã kiểm thử các trường hợp dữ liệu rỗng, dữ liệu cực lớn, chuỗi ký tự đặc biệt, tọa độ lỗi.

### Nhóm 6: Hiệu năng và Giám sát (Performance & Observability)
- [ ] **Phân trang Bắt buộc:** Tất cả API danh sách đều có phân trang `page` và `size`, khống chế cứng `size <= 100`.
- [ ] **Tối ưu Truy vấn:** Các trường lọc thường xuyên đều được đánh chỉ mục (B-Tree, GIN, Trigram). Không có Full Table Scan trên bảng lớn.
- [ ] **Streaming khi nạp lớn:** Tác vụ đọc tệp JSON lớn sử dụng Jackson Streaming API; bộ nhớ heap tiêu thụ không vượt ngưỡng an toàn ($< 256$ MB).
- [ ] **Xử lý Ngoại lệ Chuẩn:** Trả về mã lỗi RFC 7807, không rò rỉ stack trace ra client, có mã `errorId` trong log server.

### Nhóm 7: Tài liệu hóa và Hợp đồng Giao tiếp (Documentation & OpenAPI)
- [ ] **OpenAPI Spec:** Cập nhật đầy đủ chú thích Swagger UI (`@Operation`, `@ApiResponse`, `@Schema`) cho các Controller và DTO.
- [ ] **Ghi nhận Giả định & Câu hỏi Mở:** Mọi giả định kỹ thuật hoặc điểm chưa rõ ràng đã được ghi chép vào `OPEN_QUESTIONS.md`. Không tự bịa logic.
- [ ] **Cập nhật Tài liệu Kỹ thuật:** Các thay đổi về hợp đồng dữ liệu, biến môi trường `.env` được phản ánh trong `DATA_CONTRACT.md` hoặc `README_DATABASE.md`.

### Nhóm 8: Báo cáo Nghiệm thu và Kết thúc Task (Task Sign-off)
- [ ] **Báo cáo Minh bạch:** Xuất báo cáo tổng kết gồm: Danh sách tệp đã tạo/sửa, Giả định, Câu hỏi mở, Lệnh verify đã thực thi.
- [ ] **Dừng Đúng Phạm vi:** Dừng lại tại đúng yêu cầu của prompt hiện tại; tuyệt đối không tự ý chuyển sang prompt hoặc giai đoạn tiếp theo.
