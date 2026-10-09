# Phạm vi nghiệp vụ demo (07/10/2026)

Đã đọc 12 chương hướng dẫn `/user-guide` trong phiên khảo sát chỉ đọc. Chương 5 mô tả lưu nháp, gửi duyệt, phê duyệt/trả lại có lý do; chương 10 mô tả quyền khai thác, nhập và duyệt theo đơn vị. Đây là bằng chứng về hệ thống tham chiếu, không phải chính sách đã được phê duyệt cho demo.

- Đề xuất tạm: bản ghi chỉ thuộc đơn vị vùng nếu `branch_id` nguồn khớp chính xác `branch_id` của tài khoản. Thiếu mã đơn vị thì không hiển thị cho vùng.
- Ví dụ “vùng 1 Hà Nội–Nghệ An” cần xác nhận danh sách tuyến, khoảng lý trình, đơn vị phụ trách và cách xử lý tuyến giao nhiều vùng. Không tự phân tuyến dựa vào chuỗi địa danh.
- `raw_dataset_record` chỉ chứa bản ghi nguồn import, chưa có quy trình QC được kiểm chứng; số liệu dashboard vùng không phải số tài sản duy nhất hay đã được phê duyệt.
- Dashboard cũ có các chỉ số hardcode và chưa áp phạm vi quyền; các endpoint đó không được cấp cho giao diện vùng.
- Chưa có logo gốc kèm quyền dùng trong repo; giữ nhãn demo độc lập, không sao chép logo cổng chính thức.
