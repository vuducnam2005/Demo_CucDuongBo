# Hồ sơ và lịch sử phiên bản trên bản demo

Luồng này chỉ thao tác trên PostgreSQL và kho lưu trữ cục bộ; không ghi lên website nguồn. Mô hình tham khảo các nguyên tắc phổ biến của [OpenKM](https://github.com/openkm/document-management-system) và [Firefly document management](https://github.com/firefly-oss/core-common-document-mgmt): phân quyền, lịch sử tệp và audit. Không sao chép mã nguồn hay khẳng định tương thích với các hệ thống đó.

## Luồng sử dụng

1. Người được cấp quyền tạo thư mục theo đơn vị, nạp tệp vào đúng thư mục hoặc kho chung. Admin xem toàn bộ, cán bộ vùng chỉ thao tác trong chi nhánh của mình. Danh sách hỗ trợ tìm theo tên/mã tài sản, lọc định dạng và phân trang.
2. Chọn **Lịch sử phiên bản** của một hồ sơ để xem người tải, thời điểm, ghi chú và tải lại một bản cũ. Phiên bản gốc của tệp cục bộ hiện có được tạo bằng migration V19; không tạo phiên bản giả cho bản ghi không có tệp gốc hợp lệ.
3. Admin, manager hoặc editor trong phạm vi được thay tệp bằng bản mới **cùng phần mở rộng** (tối đa 25 MiB). Bản mới trở thành bản tải mặc định; bản cũ và mã băm SHA-256 được giữ nguyên. Cập nhật metadata, thêm phiên bản và ghi audit trong một giao dịch DB; tệp tải lên mới bị xóa nếu giao dịch lỗi. Xóa hồ sơ là xóa mềm, không xóa lịch sử.

## API cục bộ

| Phương thức | Tuyến | Quyền | Kết quả |
| --- | --- | --- | --- |
| GET | `/api/local-documents/branches` | Người đăng nhập có quyền đọc | Danh sách đơn vị khả dụng (vùng chỉ nhận đúng vùng mình) |
| GET | `/api/local-documents/{id}/versions` | Đọc hồ sơ trong phạm vi | Danh sách mới nhất trước, không trả đường dẫn kho lưu trữ |
| GET | `/api/local-documents/{id}/versions/{version}/file` | Đọc hồ sơ trong phạm vi | Tệp đính kèm, kiểm SHA-256, bắt buộc tải xuống |
| POST | `/api/local-documents/{id}/versions` | Admin/manager/editor đúng vùng | Multipart `file`, tùy chọn `note`; trả phiên bản mới |

Các tuyến cũ `/api/local-documents/{id}/file` và `/upload` vẫn giữ nguyên. Swagger của backend có tại `/swagger-ui/index.html`; yêu cầu JWT demo. Chỉ đưa bản demo lên môi trường công khai sau khi thay tài khoản/mật khẩu demo, xác nhận hợp đồng dữ liệu và rà an ninh.

Các hyperlink Excel hiện là trang chia sẻ HTML của nhà cung cấp, **không phải file ảnh đã lưu** trong kho hồ sơ. Tính năng bản đồ mở trang nguồn để xem ảnh khi nhà cung cấp cho phép nhúng; chưa xác nhận trang nguồn luôn hiển thị được trong iframe. Chưa có API ảnh/mật độ phương tiện chính thức của VroadAI, không suy đoán schema hoặc tạo tọa độ ngoài dữ liệu.
