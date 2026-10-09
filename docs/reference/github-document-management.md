# Tham khảo GitHub cho quản lý hồ sơ đường bộ

Chỉ đối chiếu thiết kế; không đưa mã nguồn, tài nguyên giao diện hay dữ liệu của các dự án khác vào bản demo.

| Nguồn gốc | Bài học có thể áp dụng | Đối chiếu với bản demo |
| --- | --- | --- |
| [OpenFilz Core — README](https://github.com/openfilz/openfilz-core#readme) | Thư mục là metadata trong DB; lưu tệp tách khỏi metadata; truy cập theo vai trò; checksum và audit gắn tài liệu. | `document_folder`, `document_metadata`, `document_version` và file cục bộ đã tách; cần nghiệm thu thời hạn lưu trữ/khóa hồ sơ trước khi thêm cơ chế bất biến. |
| [Paperless-ngx — hướng dẫn sử dụng trong repo](https://github.com/paperless-ngx/paperless-ngx/blob/dev/docs/usage.md#document-file-versions) | Phiên bản tệp có lịch sử riêng, metadata và quyền vẫn thuộc hồ sơ gốc; có tìm kiếm/lọc, trang chi tiết, lịch sử thao tác. | Bản demo đã giữ các phiên bản và SHA-256, tìm kiếm/phân trang, cây thư mục theo đơn vị và kiểm tra quyền server-side; chưa có quy trình duyệt hồ sơ hoặc chính sách lưu trữ được phê duyệt. |

Không sao chép cách tổ chức bằng thẻ của Paperless-ngx thay thế cây hồ sơ theo đơn vị: nghiệp vụ và phạm vi quyền phải được xác nhận với đầu mối. Không gắn tài liệu ở vùng này cho vùng khác dựa trên tên tệp hay lý trình suy đoán.

OpenFilz Core công bố [AGPL-3.0 hoặc giấy phép thương mại](https://github.com/openfilz/openfilz-core#license); [Paperless-ngx công bố GPL-3.0](https://github.com/paperless-ngx/paperless-ngx). Chỉ tham khảo hành vi và thiết kế; muốn dùng lại mã nguồn phải đánh giá nghĩa vụ giấy phép và quyền triển khai riêng.

Ưu tiên tiếp theo khi có nghiệp vụ xác nhận: quy trình nộp–duyệt–đóng hồ sơ, thời hạn lưu và quyền xóa; việc duyệt phải có vai trò, thời gian, lý do và audit, không tự nhận bản demo đã đáp ứng lưu trữ hồ sơ chính thức.
