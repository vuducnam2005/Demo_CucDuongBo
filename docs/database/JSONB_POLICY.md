# CHÍNH SÁCH QUẢN LÝ VÀ SỬ DỤNG JSONB (JSONB_POLICY)
**Hệ thống Quản lý Kết cấu Hạ tầng Giao thông Đường bộ - Cục Đường bộ Việt Nam**

---

## 1. Bối cảnh và Vai trò của JSONB trong Hệ thống

Hệ thống hạ tầng đường bộ Việt Nam quản lý 57 loại công trình tài sản với hơn **531 trường thuộc tính kỹ thuật động** khác nhau (được ghi nhận trong `field_dictionary.json`).
- Nếu sử dụng **Relational thuần túy (Mô hình EAV - Entity-Attribute-Value)**: Hệ thống sẽ phải thực hiện hàng chục phép JOIN phức tạp, gây sập hiệu năng khi số lượng bản ghi lên tới hơn 833,000 dòng.
- Nếu tạo **bảng phẳng chứa tất cả 531 cột**: Bảng sẽ chứa hơn 90% giá trị `NULL` (Sparse Table), gây lãng phí bộ nhớ đệm và liên tục phải sửa đổi cấu trúc bảng (`ALTER TABLE`) mỗi khi phát sinh chỉ tiêu kiểm tra mới từ Cục Đường bộ.
- **Giải pháp tối ưu:** Áp dụng mô hình **Hybrid Relational + JSONB** của PostgreSQL 16. Sử dụng cột `attributes JSONB` trong `asset_record` để lưu các thuộc tính nghiệp vụ đặc thù, kết hợp với các cột Typed tường minh cho các trường dùng chung.

---

## 2. Tiêu chí Phân định: Cột Typed vs Thuộc tính trong JSONB

```
                      +------------------------------------------+
                      |      Thuộc tính từ nguồn dữ liệu         |
                      +------------------------------------------+
                                           |
                    +----------------------+----------------------+
                    |                                             |
         [Thỏa mãn ít nhất 1 điều kiện]                [Không thỏa mãn]
                    |                                             |
                    v                                             v
     +------------------------------+             +------------------------------+
     |   CỘT TYPED (TƯỜNG MINH)     |             |      CỘT JSONB (ATTRIBUTES)  |
     | - Định danh & Phân cấp       |             | - Thuộc tính kỹ thuật riêng  |
     | - Lọc phạm vi số/ngày tháng  |             | - Thông số cấu kiện cục bộ   |
     | - Khóa ngoại & Phân quyền    |             | - Trường thưa (< 5% dataset) |
     | - Tọa độ không gian GIS      |             | - Không dùng làm điều kiện   |
     | - Tìm kiếm mờ tiếng Việt     |             |   lọc toàn cục               |
     +------------------------------+             +------------------------------+
```

### 2.1 Năm (05) Tiêu chí Thăng cấp Thuộc tính thành Cột Typed
Một trường thông tin bắt buộc phải được đưa ra làm **Cột có kiểu dữ liệu tường minh (Typed Column)** khi thỏa mãn ít nhất một trong các tiêu chí sau:

1. **Tiêu chí Định danh và Quan hệ Phân cấp:**
   - Làm khóa chính, khóa tự nhiên hoặc liên kết cha - con trong cây hạ tầng đường bộ.
   - *Ví dụ:* `id` (`record_id`), `gid`, `parent_id`, `raw_record_id`.
2. **Tiêu chí Phân quyền và Bảo mật Vùng dữ liệu (Data Scoping):**
   - Dùng để giới hạn quyền truy cập theo đơn vị tổ chức và địa bàn hành chính.
   - *Ví dụ:* `organization_id`, `branch_id` (Khu QLĐB), `province_id` (Tỉnh/Thành phố).
3. **Tiêu chí Tìm kiếm Số học và Lọc theo Khoảng (Range Filtering):**
   - Cần thực hiện các phép so sánh số học: lớn hơn, nhỏ hơn, nằm trong khoảng (`BETWEEN`), tính tổng (`SUM`), trung bình (`AVG`).
   - *Ví dụ:* `km_from`, `km_to` (Lý trình số km), `maintain_value` (Kinh phí bảo trì), `construction_year` (Năm xây dựng).
4. **Tiêu chí Điều hướng và Nhóm Toàn cục (Global Routing & Grouping):**
   - Thường xuyên xuất hiện trên các bộ lọc Header của giao diện người dùng trên mọi loại công trình.
   - *Ví dụ:* `dataset_code`, `asset_type`, `route_code` (Mã quốc lộ), `state` (Trạng thái duyệt).
5. **Tiêu chí Tọa độ Không gian Địa lý (Spatial Coordinates):**
   - Dùng để dựng hình học PostGIS phục vụ hiển thị và tính toán GIS.
   - *Ví dụ:* `from_coordinatex`, `from_coordinatey` được chuyển hóa thành cột `geom` trong `asset_geometry`.

---

### 2.2 Ba (03) Tiêu chí Giữ Thuộc tính trong JSONB (`attributes`)
Các trường được giữ lại trong đối tượng `attributes JSONB` khi:

1. **Thuộc tính Thưa (Sparse Attributes):**
   - Chỉ xuất hiện ở một hoặc vài tập dữ liệu cụ thể, không phổ quát cho 57 loại công trình.
   - *Ví dụ:* `longestpier` (Chiều dài nhịp chính - chỉ có ở Cầu), `type_guardrail` (Loại hộ lan - chỉ có ở Hộ lan), `ventilation_method_id` (Biện pháp thông gió - chỉ có ở Hầm).
2. **Thuộc tính Cấu hình và Diễn giải Chi tiết (Descriptive & Specs):**
   - Thông số kỹ thuật chuyên ngành phụ trợ: Tiết diện mố cầu, màu sắc sơn phản quang, đường kính bu-lông, độ dốc taluy.
3. **Dữ liệu Động Chưa Ổn định Chuẩn hóa:**
   - Các trường mới được bổ sung theo từng đợt khảo sát kiểm định hoặc theo văn bản điều hành thay đổi đột xuất của ngành đường bộ.

---

## 3. Cấu trúc và Định dạng Dữ liệu trong `attributes` JSONB

Tại tầng Raw Ingestion, mảng `data_` có dạng phức tạp gồm các wrapper `column_code`, `column_name`, `column_value`, `column_identify`. 

Khi chuyển dịch sang tầng Curated, hệ thống **chuẩn hóa và làm sạch triệt để** mảng này thành một JSON Object phẳng gọn nhẹ:

```json
{
  "ten_en": "QL.2 - Tuyen tranh TP. Tuyen Quang",
  "roadtype": "Quốc lộ",
  "capduong": "Cấp III",
  "chieudaithucte": 6.701,
  "duanbot": "Không",
  "active_status": "Đang khai thác",
  "ghichu": "Đoạn qua địa phận phường An Tường",
  "type_guardrail": "Hộ lan tôn sóng",
  "protection_type": "Gia cố đá xây"
}
```

### Quy tắc chuẩn hóa giá trị trong JSONB:
1. **Khóa của JSON Object:** Luôn sử dụng mã định danh sạch `column_identify` (chữ thường, nối bằng dấu gạch dưới).
2. **Loại bỏ thẻ HTML:** Loại bỏ hoàn toàn các thẻ wrapper `<span>...</span>` và chuỗi tiền tố rác trước khi đưa vào JSON.
3. **Ép kiểu dữ liệu nguyên thủy (Primitive Type Casting):**
   - Giá trị số (chiều dài, diện tích, tải trọng) phải được lưu dưới dạng Number (`6.701` thay vì `"6.701"`).
   - Giá trị đúng/sai được lưu dưới dạng Boolean (`true`/`false`).
   - Giá trị văn bản được lưu dưới dạng String sạch (`"Quốc lộ"`).

---

## 4. Các Mẫu Truy vấn Khai thác JSONB Chuẩn mực

### 4.1 Trích xuất Giá trị Văn bản (`->>`)
Sử dụng toán tử `->>` để lấy giá trị dạng chuỗi (Text) phục vụ hiển thị trên giao diện hoặc ghép chuỗi:
```sql
SELECT 
    ar.id,
    ar.name,
    ar.attributes->>'roadtype' AS road_type,
    ar.attributes->>'capduong' AS road_class
FROM asset_record ar
WHERE ar.dataset_code = 'duonggom'
LIMIT 50;
```

### 4.2 Ép kiểu Dữ liệu Số từ JSONB để Tính toán
Khi cần tính toán số học trên thuộc tính động nằm trong `attributes`:
```sql
SELECT 
    ar.route_code,
    AVG((ar.attributes->>'chieudaithucte')::numeric) AS avg_length_km,
    SUM((ar.attributes->>'chieudaithucte')::numeric) AS total_length_km
FROM asset_record ar
WHERE ar.attributes ? 'chieudaithucte'
GROUP BY ar.route_code;
```

### 4.3 Kiểm tra Chứa Cặp Key-Value bằng Toán tử `@>` (Containment Operator)
Đây là toán tử có hiệu năng cao nhất vì tận dụng trực tiếp chỉ mục `GIN (attributes jsonb_path_ops)`:
```sql
SELECT 
    ar.id,
    ar.name,
    ar.route_code,
    ar.lytrinh
FROM asset_record ar
WHERE ar.attributes @> '{"type_guardrail": "Hộ lan tôn sóng"}'
  AND ar.is_deleted = FALSE;
```
*Hiệu năng:* PostgreSQL duyệt qua chỉ mục GIN, xác định tức thì danh sách `id` thỏa mãn mà không cần đọc khối dữ liệu đĩa lớn.

### 4.4 Kiểm tra Sự Tồn tại của Khóa (Key Existence)
- Toán tử `?`: Kiểm tra xem bản ghi có chứa khóa này hay không.
- Toán tử `?|`: Kiểm tra xem có chứa **ít nhất một** trong các khóa.
- Toán tử `?&`: Kiểm tra xem có chứa **đồng thời tất cả** các khóa.
```sql
-- Tìm tất cả các tài sản có khai báo chiều dài thực tế:
SELECT id, name FROM asset_record WHERE attributes ? 'chieudaithucte';

-- Tìm tài sản có khai báo ít nhất một trong hai thông số kỹ thuật:
SELECT id, name FROM asset_record WHERE attributes ?| ARRAY['longestpier', 'type_guardrail'];
```

---

## 5. Chiến lược Đánh Chỉ mục trên JSONB (Index Strategy)

### 5.1 Chỉ mục Toàn cục: GIN với `jsonb_path_ops`
Được tạo mặc định trên bảng `asset_record`:
```sql
CREATE INDEX idx_asset_record_attrs_gin 
ON asset_record USING GIN (attributes jsonb_path_ops);
```
- **So sánh `jsonb_path_ops` vs `jsonb_ops`:**
  - `jsonb_ops` (Mặc định): Đánh chỉ mục riêng lẻ cho từng khóa và từng giá trị. Kích thước index rất lớn (~1.5 GB).
  - `jsonb_path_ops` (Lựa chọn chuẩn): Băm kết hợp đường dẫn khóa và giá trị (`hash(key + value)`). Kích thước index nhỏ hơn **60%** (chỉ ~400 MB) và tăng tốc độ xử lý toán tử `@>` lên 2.5 lần.

### 5.2 Chỉ mục Cục bộ trên Thuộc tính Thường xuyên Lọc (Expression B-Tree Index)
Nếu nghiệp vụ người dùng trên website thường xuyên lọc một thuộc tính động với tần suất cao (ví dụ: lọc hộ lan theo loại hộ lan), tạo chỉ mục B-Tree trên biểu thức JSON kết hợp điều kiện Partial Index:
```sql
CREATE INDEX idx_asset_guardrail_type 
ON asset_record ((attributes->>'type_guardrail')) 
WHERE dataset_code = 'tbl_guardrail' AND is_deleted = FALSE;
```
*Tác dụng:* Truy vấn lọc theo loại hộ lan sẽ chạy với tốc độ của chỉ mục B-Tree thuần túy ($< 5\text{ms}$), không tốn chi phí quét GIN index.

---

## 6. Chính sách Ràng buộc Toàn vẹn và Thăng cấp Cột (Evolution Policy)

1. **Ràng buộc Kiểm tra Kiểu dữ liệu (CHECK Constraint):**
   - Cột `attributes` luôn được áp dụng ràng buộc đảm bảo là một JSON Object hợp lệ:
     ```sql
     ALTER TABLE asset_record 
     ADD CONSTRAINT chk_asset_attributes_is_object 
     CHECK (jsonb_typeof(attributes) = 'object');
     ```
2. **Quy trình Thăng cấp Thuộc tính từ JSONB thành Cột Typed:**
   - Trong quá trình vận hành hệ thống, nếu nhật ký giám sát cơ sở dữ liệu (`pg_stat_statements`) ghi nhận một trường trong `attributes` được người dùng lọc hoặc sắp xếp với tần suất vượt quá **10,000 lượt/ngày**, đội ngũ kỹ thuật sẽ thực thi quy trình thăng cấp cột thông qua Flyway Migration:
     1. Bước 1: Thêm cột Typed mới với giá trị `NULL`.
     2. Bước 2: Chạy tiến trình nền cập nhật dữ liệu từ `attributes->>'field_name'` sang cột mới theo từng đợt (Batching 5,000 dòng).
     3. Bước 3: Tạo chỉ mục B-Tree trên cột mới.
     4. Bước 4: Xóa khóa tương ứng khỏi đối tượng `attributes` để tiết kiệm dung lượng (`attributes = attributes - 'field_name'`).
