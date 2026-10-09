# Đề xuất kết nối VroadAI — chờ anh Thuận xác nhận (07/10/2026)

**Đã xác minh:** 5 Excel do nhóm cung cấp có dữ liệu hư hỏng, tài sản, độ gồ ghề; JSONL chuẩn hóa đã nhập vào `raw_dataset_record`. Link ảnh trỏ tới trang chia sẻ, không phải hợp đồng API. [Trang giới thiệu công khai của VroadAI](https://www.vroad.vn/) mô tả khảo sát GPS, AI nhận diện hư hỏng, IRI, bản đồ và lịch sử nhưng **không công bố hợp đồng endpoint/schema cho tích hợp** trong phần đã kiểm tra ngày 07/10/2026. Hướng dẫn tham chiếu `kcht.drvn.gov.vn/user-guide#` cũng không xác nhận schema đồng bộ VroadAI. Chưa gọi hoặc đồng bộ ra hệ thống thật.

**Chỉ là mẫu demo:** `POST /api/vroad/inbound/batches` dùng JWT của **admin cục bộ**, nhận tối đa 100 bản ghi/lô, dưới 1 MB và phiên bản chính xác `demo-proposal-v1`. `GET /api/vroad/inbound/batches?page=0&size=20` xem các lô; `GET /api/vroad/inbound/batches/{id}` trả lỗi kiểm tra theo dòng. OpenAPI có tại `/v3/api-docs` trên backend cục bộ. Đây **không phải endpoint của vroad.vn**, không tạo tài sản/hư hỏng chính thức và chưa áp dụng quyền gửi dữ liệu cho bên thứ ba.

Ví dụ giả lập (không phải dữ liệu khảo sát):

```json
{
  "requestKey": "demo-review-20261007-01",
  "schemaVersion": "demo-proposal-v1",
  "records": [
    {"sourceId": "DEMO-0001", "kind": "DEFECT", "routeName": "Tuyến minh họa", "chainage": "Km1+000", "confidence": 0.8},
    {"sourceId": "DEMO-0002", "kind": "ASSET", "routeName": "Tuyến minh họa"}
  ]
}
```

| Trường đề xuất | Ý nghĩa trong bản demo | Quy tắc hiện tại |
| --- | --- | --- |
| `requestKey` | Mã lô có thể gửi lại an toàn | Cùng mã + cùng nội dung: trả lô cũ; nội dung khác: HTTP 409 |
| `sourceId`, `kind` | Mã nguồn giữ số 0 đầu; phân loại `ASSET`, `DEFECT`, `TRAFFIC` | Thiếu/sai hoặc trùng cặp trong lô → dòng `INVALID` |
| `routeName`, `chainage` | Chuỗi mô tả nguồn, chưa quy đổi thành phân đoạn địa bàn | Chưa gán `branch_id`; không suy diễn mã tuyến |
| `longitude`, `latitude` | Cặp số tọa độ **nếu bên cung cấp xác nhận** | Thiếu một số hoặc vượt biên độ → dòng `INVALID`; chưa vẽ lên bản đồ |
| `confidence` | Độ tin cậy AI giả định trong [0,1] | Ngoài khoảng → dòng `INVALID`, không tự phê duyệt |
| Các trường khác | Payload nguyên dạng để đối chiếu tại staging | Không ánh xạ sang bảng chính thức, GET không trả raw JSON |

Chỉ admin mới được gửi và xem lô. Lưu raw payload trong `vroad_ingest_record`, kết quả kiểm tra theo dòng, hash SHA-256 của lô và audit người gửi; xử lý lặp không tăng bản ghi. Trạng thái `PENDING` nghĩa **đợi thống nhất hợp đồng và xác nhận nghiệp vụ**, không phải đã duyệt. Lô có dòng lỗi vẫn ở vùng chờ để hiệu chỉnh; chưa có nút duyệt/publish. Mẫu Excel `data/templates/vroad_inbound_proposal.xlsx` được gắn nhãn giả lập; không đưa thông tin cá nhân hay mật khẩu vào payload.

**Cần hỏi anh Thuận:** mã định danh và cơ chế thay đổi bản ghi; taxonomy tài sản/hư hỏng; định danh tuyến, lý trình và ranh giới đơn vị; đơn vị đo; hệ tọa độ thực; ngày khảo sát; ảnh/tệp và quyền truy cập; độ tin cậy AI, vòng QC/duyệt; giới hạn batch, retry, ký/ủy quyền API; xử lý xóa/sửa và lịch sử phiên bản. Chỉ sau khi có hợp đồng chính thức và quyền tích hợp mới triển khai mapping/duyệt và cơ chế nhận từ VroadAI thật.

Tham khảo thiết kế thư mục/tài liệu theo hướng tách metadata, quyền và lịch sử thay đổi từ dự án [OpenFilz](https://github.com/openfilz/openfilz-core) và quy trình hồ sơ công việc từ [Atlas CMMS](https://github.com/Grashjs/cmms). Cả hai đều công bố giấy phép AGPL-3.0; **chỉ tham khảo ý tưởng**, không sao chép mã nguồn hay coi dự án đó là phần mềm nghiệp vụ của Cục.
