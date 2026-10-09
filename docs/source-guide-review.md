# Khảo sát hướng dẫn nguồn — 07/10/2026 (UTC+07)

**Nguồn:** `https://kcht.drvn.gov.vn/user-guide#`; đọc DOM của cả 12 chương trong phiên trình duyệt khảo sát chỉ đọc (không lưu cookie, token, mật khẩu, tên người dùng). Bảng trường chương 9 đã được trích riêng trong `C:\Users\linzi\Downloads\cuc duong bo\collection\normalized\guide-schema.json` (tệp cục bộ, không đưa vào Git). Nội dung dưới đây là tóm tắt, không phải đặc tả đã được Cục/anh Thuận phê duyệt.

| Chương | Đã quan sát trong hướng dẫn | Ứng dụng vào demo / giới hạn |
| --- | --- | --- |
| 1. Giới thiệu | Dữ liệu tập trung tại Cục, trao đổi với đơn vị; ba nhóm quản trị, nhập liệu, phê duyệt. | Chú ý hướng dẫn có vài đoạn lẫn thuật ngữ ngành khác; không sao chép nguyên văn thành quy tắc. |
| 2. Đăng nhập | Tài khoản được cấp; sau đăng nhập vào bảng tin, menu trái và tài khoản góc phải. | Demo dùng tài khoản và phiên độc lập, không dùng phiên website nguồn. |
| 3. Hồ sơ | Tab thông tin, sửa thông tin, mật khẩu, nhóm, quyền, nhật ký. | Cần đối chiếu UI hồ sơ và quyền trước khi nhận nghiệm thu. |
| 4. Dashboard | Số liệu tổng hợp, biểu đồ đồng bộ, trạng thái cầu, lối tắt tới nhóm tài sản. | Dashboard Cục hiện lấy từ DB; không dùng con số hardcode cho biển báo. |
| 5. Cập nhật và phê duyệt | Tìm kiếm nhiều điều kiện; xuất Excel tất cả hoặc theo lọc; tạo mới, lưu nháp, gửi duyệt; tệp, ghi chú và lịch sử; người duyệt phê duyệt/trả về có lý do, có nhắc ký số VGCASign. | Luồng QC/ký số **chưa triển khai**; dữ liệu Excel nhập vào staging RAW_STORED, không tự công bố. |
| 6. Thống kê tài sản | Cầu theo tình trạng, tỉnh, quốc lộ; điểm đen và biển báo lọc theo tuyến/đoạn, xuất nhiều định dạng. | Không tự áp phân loại/trạng thái cầu của hệ thống nguồn cho VroadAI. |
| 7. Bản đồ | Đo khoảng cách/diện tích, bookmark, tìm địa danh/tài sản, nền và lớp chuyên đề, xuất PDF/PNG. | Chỉ dùng tọa độ có nguồn; không vẽ IRI khi thiếu hình tuyến. |
| 8. Tài liệu | Tìm từ khóa/ngày, tải lên, sửa tên, xóa có xác nhận và tải xuống. | Demo bổ sung kho tệp cục bộ theo đơn vị: thư mục, lọc/tìm kiếm/phân trang, tải lên/xuống, đổi tên, xóa mềm và audit. Chỉ với hồ sơ lưu nội bộ; tài liệu nguồn chưa có nhị phân không được tạo file giả. |
| 9. Sổ tay nhập liệu | 6 nhóm Đường/Cầu/Hầm/ATGT/Giấy phép/Đường địa phương; snapshot cũ trích 54 bảng/1.607 nhãn trường. | Chỉ dùng field có bằng chứng theo từng dataset; không nhập dữ liệu cá nhân từ nhóm giấy phép. |
| 10. Phân quyền | Bảy nhóm quyền khai thác/nhập/phê duyệt/quản trị; Cục toàn cục, Sở/Khu/Phường/Xã theo đơn vị. | Vùng chỉ đọc API có bộ lọc đơn vị và chặn các API chưa hỗ trợ; ranh giới thực tế cần nghiệp vụ xác nhận. |
| 11. Đồng bộ | Quản trị Cục→Bộ, lọc thời gian, lỗi đồng bộ, trả lại đơn vị có lý do. | Chưa kết nối/cấu hình API đồng bộ bên ngoài. |
| 12. FAQ | Lưu ý về quyền thêm mới, thống kê sau duyệt, chia đoạn tuyến theo tỉnh, plugin ký số. | Không tự bật plugin/ký số, không coi lỗi dữ liệu trong FAQ là trạng thái hệ thống hiện tại. |

**Chưa được xác minh từ tài khoản nguồn:** danh sách tuyến/phân đoạn của “vùng 1 Hà Nội–Nghệ An”, schema tích hợp VroadAI, quyền ảnh thật và hiệu lực chữ ký số trên máy demo. Cần anh Thuận và đầu mối Cục duyệt trước khi phân quyền theo địa lý hoặc phát hành bản chính thức.
