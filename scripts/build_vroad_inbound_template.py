"""Build a small, explicitly proposed XLSX template without third-party dependencies."""

from pathlib import Path
from xml.sax.saxutils import escape
from zipfile import ZIP_DEFLATED, ZipFile


HEADERS = (
    "sourceId", "kind", "routeName", "chainage", "assetType", "defectType",
    "longitude", "latitude", "confidence", "observedAt",
)
SAMPLE = (
    ("DEMO-0001", "DEFECT", "Tuyến minh họa", "Km1+000", "", "Minh họa", "", "", "0.8", ""),
    ("DEMO-0002", "ASSET", "Tuyến minh họa", "Km1+100", "Minh họa", "", "", "", "", ""),
)


def worksheet(rows):
    lines = []
    for row_number, values in enumerate(rows, start=1):
        cells = "".join(
            f'<c r="{chr(65 + column)}{row_number}" t="inlineStr"><is><t>{escape(str(value))}</t></is></c>'
            for column, value in enumerate(values)
        )
        lines.append(f'<row r="{row_number}">{cells}</row>')
    return ('<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
            '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
            '<sheetData>' + "".join(lines) + '</sheetData></worksheet>')


def build(output):
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    content_types = ('<?xml version="1.0" encoding="UTF-8"?>'
                     '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
                     '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
                     '<Default Extension="xml" ContentType="application/xml"/>'
                     '<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
                     '<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
                     '<Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
                     '</Types>')
    workbook = ('<?xml version="1.0" encoding="UTF-8"?>'
                '<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" '
                'xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">'
                '<sheets><sheet name="Du lieu minh hoa" sheetId="1" r:id="rId1"/>'
                '<sheet name="De xuat cho xac nhan" sheetId="2" r:id="rId2"/></sheets></workbook>')
    notes = (
        ("ĐỀ XUẤT CHỜ XÁC NHẬN — không phải mẫu chính thức của VroadAI",),
        ("Không gửi dữ liệu cá nhân, mật khẩu hoặc ảnh nguồn qua mẫu.",),
        ("sourceId giữ dạng chuỗi để bảo toàn số 0 đầu; tọa độ chỉ nhập khi được xác nhận hệ quy chiếu.",),
        ("Nạp JSON chuyển đổi vào API staging, chưa nhập trực tiếp tài sản chính thức.",),
    )
    with ZipFile(output, "w", ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", content_types)
        archive.writestr("_rels/.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                         '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>')
        archive.writestr("xl/workbook.xml", workbook)
        archive.writestr("xl/_rels/workbook.xml.rels", '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
                         '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>'
                         '<Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/></Relationships>')
        archive.writestr("xl/worksheets/sheet1.xml", worksheet((HEADERS, *SAMPLE)))
        archive.writestr("xl/worksheets/sheet2.xml", worksheet(notes))


if __name__ == "__main__":
    build(Path(__file__).resolve().parents[1] / "data" / "templates" / "vroad_inbound_proposal.xlsx")
