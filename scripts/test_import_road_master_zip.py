"""Read-only checks of road master ZIP validation."""

import json
import pathlib
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
from import_road_master_zip import prepare_dataset


class RoadMasterArchiveTests(unittest.TestCase):
    def make_archive(self, directory, rows, expected):
        path = pathlib.Path(directory) / "roads.zip"
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("kcht_2026-10-05/mst_national_road_manifest.json", json.dumps({
                "dataset": "mst_national_road", "complete_website_crawl": False,
                "main": {"records": expected, "fields": ["vidagis_id", "name_vi"],
                         "source_workbook": "road-source.xlsx"}}))
            archive.writestr("kcht_2026-10-05/mst_national_road.json", json.dumps(rows))
        return path

    def test_preserves_identity_and_labels_partially_collected_records(self):
        with tempfile.TemporaryDirectory() as directory:
            path = self.make_archive(directory, [{"vidagis_id": "national_id_07", "name_vi": "Quốc lộ 7"}], 1)
            with zipfile.ZipFile(path) as archive:
                workbook, records = prepare_dataset(archive, "mst_national_road")
            self.assertEqual(workbook, "road-source.xlsx")
            payload = json.loads(records[0])
            self.assertEqual(payload["source_key"], "national_id_07")
            self.assertFalse(payload["is_demo"])
            self.assertIn("partial", payload["source_status"])

    def test_rejects_duplicate_ids_and_inconsistent_counts(self):
        with tempfile.TemporaryDirectory() as directory:
            row = {"vidagis_id": "national_id_07", "name_vi": "Quốc lộ 7"}
            path = self.make_archive(directory, [row, row], 2)
            with zipfile.ZipFile(path) as archive:
                with self.assertRaisesRegex(ValueError, "Duplicate"):
                    prepare_dataset(archive, "mst_national_road")
            path = self.make_archive(directory, [row], 2)
            with zipfile.ZipFile(path) as archive:
                with self.assertRaisesRegex(ValueError, "count mismatch"):
                    prepare_dataset(archive, "mst_national_road")


if __name__ == "__main__":
    unittest.main()
