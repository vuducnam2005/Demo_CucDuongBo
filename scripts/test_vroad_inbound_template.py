import pathlib
import sys
import tempfile
import unittest
from zipfile import ZipFile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from build_vroad_inbound_template import HEADERS, SAMPLE, build, worksheet
from convert_vroad_proposal_excel import convert, read_rows


class VroadInboundTemplateTest(unittest.TestCase):
    def test_round_trip_preserves_vietnamese_and_source_key(self):
        with tempfile.TemporaryDirectory() as directory:
            workbook = pathlib.Path(directory) / "proposal.xlsx"
            build(workbook)
            request = convert(workbook, "demo-batch-001")
        self.assertEqual(request["schemaVersion"], "demo-proposal-v1")
        self.assertEqual(len(request["records"]), 2)
        self.assertEqual(request["records"][0]["sourceId"], "DEMO-0001")
        self.assertEqual(request["records"][0]["routeName"], "Tuyến minh họa")
        self.assertEqual(request["records"][0]["confidence"], 0.8)

    def test_rejects_another_schema_instead_of_guessing_fields(self):
        with tempfile.TemporaryDirectory() as directory:
            workbook = pathlib.Path(directory) / "not-proposal.xlsx"
            with ZipFile(workbook, "w") as archive:
                archive.writestr("xl/worksheets/sheet1.xml", worksheet((("wrong",),)))
            with self.assertRaisesRegex(ValueError, "Not the VroadAI proposed template"):
                read_rows(workbook)

    def test_rejects_more_than_one_hundred_proposed_rows(self):
        with tempfile.TemporaryDirectory() as directory:
            workbook = pathlib.Path(directory) / "oversized.xlsx"
            with ZipFile(workbook, "w") as archive:
                archive.writestr("xl/worksheets/sheet1.xml", worksheet((HEADERS, *([SAMPLE[0]] * 101))))
            with self.assertRaisesRegex(ValueError, "No more than 100"):
                read_rows(workbook)


if __name__ == "__main__":
    unittest.main()
