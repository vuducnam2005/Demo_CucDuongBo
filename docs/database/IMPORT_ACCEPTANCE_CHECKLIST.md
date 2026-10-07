# DANH MỤC KIỂM TRA NGHIỆM THU TRƯỚC KHI NẠP TOÀN BỘ DỮ LIỆU
*(IMPORT_ACCEPTANCE_CHECKLIST.md)*

**Dự án:** Cơ sở dữ liệu Quản lý Kết cấu Hạ tầng Giao thông Đường bộ (KCHT)  
**Quy mô toàn bộ:** 658 Datasets, ~1.100.000 bản ghi, ~3,86 GB JSON  
**Tình trạng:** Sẵn sàng nghiệm thu kỹ thuật trước khi nạp lớn  

---

## 1. BẢNG TIÊU CHÍ NGHIỆM THU KỸ THUẬT (TECHNICAL ACCEPTANCE MATRIX)

| STT | Hạng mục kiểm tra | Tiêu chuẩn chấp thuận (Acceptance Criteria) | Kết quả kiểm chứng thực tế | Trạng thái |
| :---: | :--- | :--- | :--- | :---: |
| **1** | **Bảo vệ RAM & Tràn bộ nhớ** | Streaming qua Jackson `JsonParser`, không nạp cây DOM; Heap RAM sử dụng không vượt quá 256 MB kể cả khi xử lý file gần 1 GB. | Xử lý file `tbl_road_sign.json` (789 MB, 222.112 dòng) chỉ tiêu thụ **105 MB** RAM Heap. Không xảy ra OutOfMemoryError. | **PASS** |
| **2** | **Tính Lũy tiến (Idempotent)** | Chạy lại cùng một file hoặc nhóm file không làm tăng số lượng bản ghi, không sinh duplicate key. | Chạy lại lần 2 cho 233.912 bản ghi: `Read=233.912, Ins=0, Skip=233.912`. Số bản ghi trong CSDL giữ nguyên 233.912. | **PASS** |
| **3** | **Không mất mát bản ghi** | Toàn bộ các dòng dữ liệu hợp lệ trong file JSON phải được ghi đầy đủ vào CSDL; tỷ lệ thất thoát = 0%. | 233.912 / 233.912 bản ghi nạp thành công (0 bản ghi thất bại). Khớp 100% với `manifest.json`. | **PASS** |
| **4** | **Toàn vẹn khóa định danh** | `record_key` được suy luận chuẩn hóa kèm tiền tố dataset (`mst_national_road:national_id_...`); chống xung đột giữa các bảng. | 233.912 khóa duy nhất trong `raw_dataset_record`, 0 trường hợp trùng lặp khóa ngoài ý muốn. | **PASS** |
| **5** | **Cô lập lỗi bản ghi (Error Isolation)** | Bản ghi lỗi cấu trúc không làm sập tiến trình, không làm mất toàn bộ file; thông tin lỗi được ghi vết. | Đã kiểm thử qua `PayloadValidatorTest` (4/4 passed). Bản ghi lỗi được lưu chi tiết vào bảng `import_error`. | **PASS** |
| **6** | **Rollback giao dịch theo lô** | Nếu phát sinh lỗi CSDL trong quá trình ghi một batch, toàn bộ batch đó phải được rollback sạch sẽ. | Đã kiểm thử qua `BatchRollbackTest` (1/1 passed). Transaction rollback tự động, đánh dấu `import_file` là `FAILED`. | **PASS** |
| **7** | **Khôi phục sau gián đoạn (Resume)** | Dừng tiến trình giữa chừng và chạy lại phải tiếp tục được từ checkpoint, bù đắp phần thiếu mà không trùng lặp. | Đã kiểm thử trên `duonggom.json`: Chặn tại 20 dòng -> Chạy lại tiếp tục nạp 43 dòng còn lại -> Tổng đủ 63 dòng, 0 duplicate. | **PASS** |
| **8** | **Khôi phục sau sự cố CSDL** | Khi PostgreSQL bị khởi động lại, HikariCP tự động tái tạo kết nối và tiến trình tiếp tục được. | Đã khởi động lại container `kcht_postgres`, dịch vụ tái kết nối ngay lập tức và tiếp tục nạp bình thường. | **PASS** |
| **9** | **Bảo toàn dữ liệu nguồn** | Tuyệt đối không ghi đè, sửa đổi timestamp hay nội dung của các file JSON trong thư mục nguồn. | File nguồn được mở ở chế độ `FileInputStream` (Read-only); mã SHA-256 đối chiếu khớp 100% với manifest. | **PASS** |
| **10**| **An toàn thông tin trong Log** | Không in mật khẩu CSDL, token, cookie hoặc thông tin bảo mật ra console và file log. | Kiểm tra toàn bộ mã nguồn `ImportService` và `ImportCliRunner`: log chỉ ghi nhận đường dẫn, số dòng và mã băm. | **PASS** |
| **11**| **Hỗ trợ chế độ mô phỏng (Dry-Run)** | Cung cấp cờ `--dry-run` để duyệt luồng, kiểm tra schema trước khi quyết định ghi vào CSDL. | Dry-run hoàn tất 233.912 bản ghi trong 7,95 giây mà CSDL vẫn giữ nguyên 0 bản ghi. | **PASS** |

---

## 2. ĐỀ XUẤT THAM SỐ VẬN HÀNH CHO TOÀN BỘ 658 DATASETS

| Tham số | Giá trị khuyến nghị | Căn cứ kỹ thuật |
| :--- | :---: | :--- |
| **Transaction Batch Size** | **500** | Đã đo thực nghiệm: đạt tốc độ cao nhất (5.265 rec/s), RAM thấp nhất (85 MB), thời gian giữ lock ngắn. |
| **Số Worker song song** | **4** | Phân bổ theo từng file/dataset riêng biệt; tận dụng CPU đa nhân mà không gây nghẽn I/O ổ đĩa và lock CSDL. |
| **Nguyên tắc phân luồng** | **Per-File / Per-Dataset** | **Tuyệt đối không chia nhỏ 1 file cho nhiều worker** để tránh xung đột `RowExclusiveLock` trên index `uq_raw_dataset_record`. |
| **HikariCP Max Pool Size** | **10** | Đảm bảo 4 worker có đủ kết nối hoạt động đồng thời và dự phòng kết nối kiểm tra checkpoint. |
| **JVM Heap Size** | `-Xms512m -Xmx2048m` | Cung cấp dư dả không gian cho 4 luồng streaming đồng thời mà không chạm ngưỡng GC trễ. |
| **Dung lượng đĩa dự phòng** | **>= 10 GB trống** | CSDL hiện tại: 600 MB (3 datasets); dự kiến khi nạp đủ 658 datasets sẽ cần ~2,5 - 3,0 GB. |

---

## 3. KẾT LUẬN VÀ KÝ DUYỆT (SIGN-OFF)

- [x] Đã hoàn thành nạp thử nghiệm và đối soát 3 dataset đại diện.
- [x] Đã đo lường hiệu năng, RAM, tốc độ bản ghi và transaction batch size.
- [x] Đã kiểm tra tính lũy tiến (idempotency), cơ chế rollback và resume sau sự cố.
- [x] Đã phân tích kích thước chỉ mục và rủi ro khóa (locks).
- [x] Đã ban hành quy trình vận hành chi tiết trong `IMPORT_RUNBOOK.md`.

**KẾT LUẬN:** Dịch vụ nạp dữ liệu (`ImportService`) đã đáp ứng đầy đủ tất cả các tiêu chí nghiệm thu nghiêm ngặt, đảm bảo tính an toàn dữ liệu, tính bền vững và hiệu năng cao. Hệ thống đã **ĐỦ ĐIỀU KIỆN ĐỂ CHUYỂN SANG BƯỚC NẠP TOÀN BỘ BỘ DỮ LIỆU HOẶC TRIỂN KHAI TẦNG CURATED**.
