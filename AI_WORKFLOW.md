# QUY TRÌNH LÀM VIỆC THEO TỪNG TASK VÀ LỘ TRÌNH DỰ ÁN DÀNH CHO AI (AI_WORKFLOW)
**Dự án: Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT ĐB)**  
**Cơ quan chủ quản:** Cục Đường bộ Việt Nam  
**Phiên bản:** 1.0.0  

---

## 1. NGUYÊN TẮC BẮT BUỘC TRONG MỖI PROMPT THỰC THI

Trong mọi giai đoạn, AI Coding Agent phải tuân thủ nghiêm ngặt chỉ dẫn:
> **Chỉ dẫn cốt lõi:**  
> - Trước khi sửa mã nguồn, bắt buộc đọc kỹ [`AI_RULES.md`](file:///c:/Demo_CucDuongBo/AI_RULES.md), [`AI_WORKFLOW.md`](file:///c:/Demo_CucDuongBo/AI_WORKFLOW.md) và các tài liệu liên quan.  
> - Kiểm tra hiện trạng repository, lập kế hoạch ngắn gọn rồi mới bắt đầu chỉnh sửa code.  
> - **Chỉ làm việc trong đúng phạm vi của giai đoạn/prompt hiện tại; tuyệt đối không tự ý nhảy cóc sang giai đoạn sau.**  
> - **Không xóa, reset hoặc ghi đè thay đổi của người dùng.**  
> - Sau khi hoàn thành, bắt buộc chạy test phù hợp và báo cáo minh bạch: danh sách file đã sửa, lệnh verify đã chạy, kết quả đạt được, rủi ro còn lại và công việc dự kiến tiếp theo.

---

## 2. QUY TRÌNH 5 BƯỚC KHÉP KÍN CHO TỪNG TASK

```
+-----------------------------------------------------------------------------------+
|  BƯỚC 1: KIỂM TRA HIỆN TRẠNG REPOSITORY & CHẠY BASELINE TEST                       |
|  - Kiểm tra các tệp hiện có, cấu trúc thư mục, container Docker đang chạy.         |
|  - Chạy toàn bộ test hiện hữu (.\mvnw.bat test) để bảo đảm codebase đang xanh.    |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  BƯỚC 2: PHÂN TÍCH YÊU CẦU & ĐỐI SOÁT TÀI LIỆU DATABASE ĐÃ CHỐT                   |
|  - Đối chiếu tài liệu docs/database/ và bộ JSON nguồn C:\Data\kcht_json_2026-10-05.|
|  - Không tạo bảng/migration/importer thứ hai nếu đã tồn tại.                      |
|  - Nếu có mâu thuẫn: Dừng lại, ghi vào OPEN_QUESTIONS.md, không tự bịa logic.    |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  BƯỚC 3: THỰC THI NGUYÊN TỬ THEO RANH GIỚI MODULE (ATOMIC EXECUTION)               |
|  - Tuân thủ ranh giới 7 module của Modular Monolith.                              |
|  - Sửa đổi nhỏ, chắc chắn, không sửa lan man ngoài phạm vi prompt.                |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  BƯỚC 4: CHẠY KIỂM THỬ TỰ ĐỘNG & XÁC MINH TOÀN DIỆN (AUTOMATED VERIFICATION)      |
|  - Chạy Unit Test, Testcontainers, Playwright E2E tùy theo phân hệ sửa đổi.       |
|  - Chạy Regression Test: Kiểm tra 100% test suite vượt qua (0 failure, 0 error).  |
+-----------------------------------------------------------------------------------+
                                          |
                                          v
+-----------------------------------------------------------------------------------+
|  BƯỚC 5: BÁO CÁO MINH BẠCH & DỪNG ĐÚNG PHẠM VI GIAI ĐOẠN                          |
|  - Báo cáo: File đã tạo/sửa, Giả định, Câu hỏi mở, Lệnh verify và kết quả.       |
|  - Dừng lại tại đúng prompt hiện tại, chờ chỉ đạo tiếp theo từ người dùng.        |
+-----------------------------------------------------------------------------------+
```

---

## 3. LỘ TRÌNH 15 GIAI ĐOẠN XÂY DỰNG WEBSITE (PROJECT ROADMAP)

Hệ thống được chia thành các giai đoạn độc lập theo tài liệu kế hoạch `# Kế hoạch prompt xây dựng website.txt`:

| Giai đoạn | Tên giai đoạn | Mục tiêu & Kết quả bàn giao chính | Ranh giới & Ràng buộc bắt buộc |
| :---: | :--- | :--- | :--- |
| **Khởi động** | **Quy tắc & Workflow AI** *(Hiện tại)* | Tạo lập `AI_RULES.md`, `AI_WORKFLOW.md`, `ARCHITECTURE.md`, `DATA_CONTRACT.md`, `API_CONVENTIONS.md`, `DEFINITION_OF_DONE.md`, `ADR 0001`, `OPEN_QUESTIONS.md`. | **Không viết tính năng lớn. Dừng lại sau khi hoàn thành tài liệu.** |
| **Giai đoạn 0** | **Khảo sát & Chốt phạm vi** | Lập inventory chức năng, route map, dataset map, ma trận quyền và ma trận trạng thái UI (`docs/reference/*`). | Không bypass auth/CAPTCHA; chỉ phân tích dữ liệu hợp lệ; chưa code tính năng. |
| **Giai đoạn 1** | **Khởi tạo Monorepo & Local Env** | Dựng skeleton monorepo (Spring Boot 3, React TypeScript Vite, Docker Compose, MinIO, Flyway, OpenAPI). | Tái sử dụng PostgreSQL/PostGIS và Flyway hiện có; không tạo database thứ hai. |
| **Giai đoạn 2** | **Bàn giao Database cho Code** | Lập `DATABASE_CODE_HANDOFF.md`, tạo entity/projection, repository, query adapter theo schema thực tế. | Không tạo lại database/importer; không cho frontend gọi thẳng bảng raw. |
| **Giai đoạn 3** | **Tích hợp & Kiểm tra Dữ liệu** | Smoke test read-only trên 3 dataset đại diện: `mst_national_road`, `tbl_bridge`, `tbl_road_sign`. | Không import lại toàn bộ; không sửa JSON nguồn hoặc raw data. |
| **Giai đoạn 4** | **Backend API Nền tảng** | Đối chiếu và hoàn thiện API phân trang, tìm kiếm, lọc theo allowlist, BBOX spatial query từ Prompt 8. | Không cho client truyền tên bảng SQL tùy ý; dùng dataset registry; test với Testcontainers. |
| **Giai đoạn 5** | **Authentication, Authz & Audit** *(Hoàn thành)* | Triển khai Spring Security, JWT stateless, Argon2/BCrypt, RBAC 4 vai trò (viewer, editor, manager, admin), audit log. | Không log credential/token; rate limit login; test truy cập đúng/từ chối. |
| **Giai đoạn 6** | **Frontend Shell & Layout** *(Hoàn thành)* | Xây dựng shell giao diện: sidebar, topbar, breadcrumb, route guard, notification, loading skeleton, empty state, pagination, table toolbar, tokens. | Dùng 1 UI library duy nhất; không nhúng dữ liệu lớn vào bundle frontend. |
| **Giai đoạn 7** | **Dashboard & Thống kê Tổng quan** *(Hoàn thành)* | Dashboard với các thẻ thống kê tổng quan, biểu đồ theo tỉnh/trạng thái/tuyến qua API aggregate server-side. | Không tính tổng hàng trăm nghìn dòng trong browser; mỗi card có nguồn dữ liệu. |
| **Giai đoạn 8** | **Cây Tài sản & Danh sách Chi tiết** *(Hoàn thành)* | Tree navigation lazy-load, bảng dữ liệu động theo metadata, phân trang server-side, export, link bản đồ. | Bắt đầu bằng vertical slice: `tbl_road_sign`, `mst_national_road`, `tbl_bridge`. |
| **Giai đoạn 9** | **Bản đồ GIS Chuyên đề** *(Hoàn thành)* | Bản đồ OpenLayers/Leaflet, PostGIS BBOX query, clustering, vector tiles, popup thông tin, zoom đến đối tượng. | Không tải 222 nghìn điểm cùng lúc; benchmark tối ưu hiển thị. |
| **Giai đoạn 10** | **Báo cáo, Danh mục & Tài liệu** *(Hoàn thành)* | Báo cáo chiều dài đường, bảo trì động; tra cứu 152 danh mục; cây thư mục tài liệu và tải file từ MinIO/S3. | Báo cáo query server-side có filter/export; file nhị phân lưu ở MinIO. |
| **Giai đoạn 11** | **Quản trị và CRUD Dữ liệu** | Form nhập liệu sinh từ schema, validation client/server, optimistic locking, audit log, soft delete. | Không sửa/xóa trực tiếp `raw_dataset_record`; CRUD tác động vào bảng curated. |
| **Giai đoạn 12** | **Kiểm thử Toàn diện & Đối soát** | `TEST_MATRIX.md`, Playwright E2E các luồng chính, Testcontainers, reconciliation test, pagination stability test. | Phải có bằng chứng thực tế từ lệnh test; không đánh dấu pass giả. |
| **Giai đoạn 13** | **Hiệu năng và Vận hành** | Benchmark p50/p95, tối ưu query, memory profiling, chỉ mục database, cursor pagination nếu cần. | Không giảm dữ liệu hiển thị mà không có sự đồng ý của người dùng. |
| **Giai đoạn 14** | **Deploy Production & Bàn giao** | Docker images, PostgreSQL backup/restore script, MinIO backup, Nginx TLS, release checklist. | Không ghi secret thật vào repository; quy trình backup/restore kiểm thử thực tế. |

---

## 4. THỨ TỰ ƯU TIÊN THỰC TẾ (10 NGUYÊN TẮC THỰC THI)

1. Tạo quy tắc và workflow (đã hoàn thành).
2. Kiểm tra repository hiện có và tạo phần code shell còn thiếu; không tạo lại database/import.
3. Bàn giao database, tạo adapter/repository/query và kiểm tra dữ liệu mẫu.
4. Đối chiếu và hoàn thiện Backend API pagination/filter/search theo API đã có từ Prompt 8 database; không tạo API hoặc schema thứ hai.
5. Xây dựng Frontend shell và dashboard tổng quan.
6. Xây dựng Cây tài sản và danh sách tài sản chi tiết.
7. Triển khai Bản đồ GIS không gian địa lý.
8. Triển khai Báo cáo bảo trì, danh mục tham chiếu và quản lý hồ sơ tài liệu.
9. Triển khai CRUD quản trị, phân quyền RBAC và audit log.
10. Kiểm thử E2E (Playwright), benchmark hiệu năng và đóng gói triển khai production.

> [!TIP]
> **Chiến lược Vertical Slice:** Luôn hoàn thành một luồng dọc hoàn chỉnh trên 3 dataset đại diện (`tbl_road_sign`, `mst_national_road`, `tbl_bridge`) trước khi mở rộng ra toàn bộ 658 dataset để phát hiện sớm các vấn đề về schema, geometry, phân trang và hiệu năng.
