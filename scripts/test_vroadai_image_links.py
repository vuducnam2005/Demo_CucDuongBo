"""Unit checks for XLSX hyperlink mapping; no remote downloads or real workbook required."""

import pathlib
import tempfile
import unittest
import zipfile

from import_vroadai_image_links import image_links


class ImageLinkTests(unittest.TestCase):
    def workbook(self, destination, url):
        path = destination / 'images.xlsx'
        with zipfile.ZipFile(path, 'w') as archive:
            archive.writestr('xl/worksheets/sheet1.xml', '''<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                <sheetData><row r="5"><c r="A5" t="inlineStr"><is><t>DEF-00001</t></is></c></row></sheetData>
                <hyperlinks><hyperlink ref="K5" r:id="rId1"/></hyperlinks></worksheet>''')
            archive.writestr('xl/worksheets/_rels/sheet1.xml.rels', '''<Relationships
                xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship
                Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
                Target="'''+url+'''" TargetMode="External"/></Relationships>''')
        return path

    def test_matches_row_identity_to_hyperlink_target(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.workbook(pathlib.Path(directory), 'https://platform.vroad.vn/s/example')
            self.assertEqual(image_links(path, 'K'), [('DEF-00001', 'https://platform.vroad.vn/s/example')])

    def test_rejects_untrusted_image_host(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.workbook(pathlib.Path(directory), 'https://platform.vroad.vn.attacker.example/s/x')
            with self.assertRaisesRegex(ValueError, 'Unexpected image host'):
                image_links(path, 'K')

    def test_rejects_wrong_column(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.workbook(pathlib.Path(directory), 'https://platform.vroad.vn/s/example')
            with self.assertRaisesRegex(ValueError, 'Unexpected image column'):
                image_links(path, 'L')


if __name__ == '__main__':
    unittest.main()
