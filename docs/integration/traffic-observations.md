# Nhập quan sát giao thông — hợp đồng đề xuất

Đây là hợp đồng **đề xuất cho demo cục bộ**, không phải API đã thống nhất với VroadAI. Chưa có dữ liệu số xe/mật độ phương tiện trong các XLSX tài sản, hư hỏng và IRI được giao; trạm `DEMO-TRAFFIC-01` vẫn là dữ liệu mô phỏng. Trang `/traffic` gắn nhãn riêng cho bản ghi nhập, chờ đối soát.

## Điểm nhận và quyền

- `POST /api/vroad/traffic/imports`: cần Bearer JWT vai trò `ROLE_ADMIN`; không kết nối sang hệ thống nguồn.
- `GET /api/vroad/traffic/stations`: admin thấy toàn bộ, cán bộ vùng xem trạm trùng `branchId`, viewer bị từ chối.
- `GET /api/vroad/traffic/summary?stationCode=...&from=...&to=...`: thời gian ISO-8601 có múi giờ, khoảng tối đa 31 ngày; trạm ngoài phạm vi trả 404.
- OpenAPI tại `/v3/api-docs`; không dùng mật khẩu hoặc token nguồn để gọi API demo.

Một `requestKey` chỉ đại diện đúng một payload: gửi lại y nguyên trả lô cũ; cùng mã nhưng dữ liệu khác trả 409. `sourceSystem` là khai báo của bên gửi, **chưa được xác thực** là nguồn chính thức. `eventKey` định danh phép đo trong phạm vi hệ thống khai báo; một sự kiện không được ghi hai lần qua các lô. Mỗi lô ghi người nhập, thời gian, tổng số phép đo vào nhật ký. Dữ liệu nhập vào PostgreSQL riêng, không hòa lẫn nhãn mô phỏng.

## Mẫu JSON giả lập

```json
{
  "requestKey": "example-traffic-001",
  "sourceSystem": "LOCAL_UPLOAD",
  "station": {
    "code": "EXAMPLE-TRAFFIC-01",
    "name": "Điểm khảo sát minh họa",
    "routeName": "Tuyến minh họa",
    "branchId": "kqldb_1",
    "segmentLengthKm": 2.5
  },
  "counts": [{
    "eventKey": "example-interval-1",
    "direction": "Bắc",
    "lane": 1,
    "windowStart": "2026-10-07T08:00:00+07:00",
    "windowEnd": "2026-10-07T08:15:00+07:00",
    "vehicleClass": "Ô tô con",
    "vehicleCount": 16
  }],
  "snapshots": [{
    "eventKey": "example-snapshot-1",
    "direction": "Bắc",
    "measuredAt": "2026-10-07T08:10:00+07:00",
    "vehicleClass": "Ô tô con",
    "presentVehicles": 10
  }]
}
```

Trước khi gửi, thay toàn bộ mã `EXAMPLE-*` và `replace-with-*` cùng `branchId`, tuyến và phép đo bằng dữ liệu có nguồn; mã trạm `EXAMPLE-*` bị API từ chối. `counts` là số **lượt đi qua** trong cửa sổ thời gian; không chia số này cho chiều dài để tạo mật độ. `snapshots` là **số xe hiện diện đồng thời** trên đoạn đo; chỉ khi có `segmentLengthKm > 0` hợp lệ mới tính `xe/km = presentVehicles / segmentLengthKm`. Ở ví dụ trên: 16 lượt trong 15 phút, nhưng mật độ tại 08:10 là 10 / 2,5 = 4 xe/km. Cả hai trường đều có phân loại xe riêng. Không suy diễn số liệu từ tốc độ hoặc IRI.

## Cần xác nhận trước tích hợp

Nguồn định danh trạm/bản ghi, mã đơn vị và đoạn đo, hướng/làn, phân loại xe, mẫu thời gian và timezone, nghĩa của số xe hiện diện, chứng cứ chiều dài đoạn đo, quy trình đối soát/duyệt, xác thực và cơ chế kết nối API/Excel VroadAI. Không tự gọi `www.vroad.vn` hoặc sao chép tài khoản nguồn vào dịch vụ demo khi chưa có hợp đồng và quyền phù hợp.
