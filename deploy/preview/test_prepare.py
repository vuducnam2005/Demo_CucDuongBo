import hashlib
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("preview_prepare", Path(__file__).with_name("prepare.py"))
PREPARE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PREPARE)


class PreviewPreparationTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.directory = Path(self.temporary.name).resolve()
        (self.directory / "compose.yaml").write_text("services: {}\n", encoding="utf-8")

    def tearDown(self):
        self.temporary.cleanup()

    def initialize(self):
        PREPARE.initialize(self.directory, "test-edge")
        return json.loads((self.directory / ".private" / "accounts.json").read_text(encoding="utf-8"))

    def test_unique_passwords_and_independent_environment(self):
        accounts = self.initialize()
        self.assertEqual(set(accounts), set(PREPARE.ACCOUNTS))
        self.assertEqual(len(set(accounts.values())), 4)
        self.assertTrue(all(len(password) >= 24 for password in accounts.values()))
        configuration = (self.directory / ".env").read_text(encoding="utf-8")
        self.assertIn("REFRESH_COOKIE_SECURE=true\n", configuration)
        self.assertIn("EDGE_NETWORK=test-edge\n", configuration)
        for password in accounts.values():
            self.assertNotIn(password, configuration)

    def test_reinitialization_preserves_existing_secrets(self):
        self.initialize()
        original = (self.directory / ".env").read_bytes()
        with self.assertRaises(ValueError):
            self.initialize()
        self.assertEqual((self.directory / ".env").read_bytes(), original)

    def test_manifest_accepts_only_unchanged_release_files(self):
        configuration = self.directory / "compose.yaml"
        expected = hashlib.sha256(configuration.read_bytes()).hexdigest()
        (self.directory / "SHA256SUMS.json").write_text(json.dumps({"compose.yaml": expected}))
        PREPARE.verify(self.directory)
        configuration.write_text("changed")
        with self.assertRaises(ValueError):
            PREPARE.verify(self.directory)

    def test_manifest_rejects_paths_outside_release(self):
        (self.directory / "SHA256SUMS.json").write_text(json.dumps({"../outside": "0" * 64}))
        with self.assertRaises(ValueError):
            PREPARE.verify(self.directory)

    def test_credential_rotation_is_preview_only_and_transactional(self):
        self.initialize()
        with patch.object(PREPARE.subprocess, "run", return_value=subprocess.CompletedProcess([], 0)) as execute:
            PREPARE.rotate_access(self.directory)
        invocation = execute.call_args
        self.assertIn("vroad-preview", invocation.args[0])
        self.assertIn("vroad_preview", invocation.args[0])
        statements = invocation.kwargs["input"]
        self.assertTrue(statements.startswith("BEGIN;\n"))
        self.assertTrue(statements.endswith("COMMIT;\n"))
        self.assertIn("DELETE FROM app_refresh_token", statements)
        self.assertIn("ON CONFLICT (user_id,route_name) DO NOTHING", statements)
        self.assertNotIn("UPDATE raw_dataset_record", statements)
        self.assertNotIn("DELETE FROM raw_dataset_record", statements)

    def test_failed_rotation_returns_no_credentials(self):
        accounts = self.initialize()
        with patch.object(PREPARE.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
            with self.assertRaises(RuntimeError) as failure:
                PREPARE.rotate_access(self.directory)
        for password in accounts.values():
            self.assertNotIn(password, str(failure.exception))


if __name__ == "__main__":
    unittest.main()
