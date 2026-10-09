import argparse
import hashlib
import json
import os
import secrets
import subprocess
from pathlib import Path


ACCOUNTS = ("admin", "editor_demo", "manager_demo", "viewer_demo")


def private_write(destination, contents):
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write(contents)


def initialize(directory, edge_network):
    if (directory / ".env").exists():
        raise ValueError("Preview already initialized; existing secrets will not be replaced.")
    values = {
        "POSTGRES_DB": "vroad_preview",
        "POSTGRES_USER": "vroad_preview",
        "POSTGRES_PASSWORD": secrets.token_urlsafe(32),
        "JWT_SECRET": secrets.token_urlsafe(64),
        "MINIO_ROOT_USER": "preview-local-storage",
        "MINIO_ROOT_PASSWORD": secrets.token_urlsafe(32),
        "REFRESH_COOKIE_NAME": "vroad_preview_refresh",
        "REFRESH_COOKIE_SECURE": "true",
        "REFRESH_COOKIE_SAME_SITE": "Lax",
        "REFRESH_COOKIE_DOMAIN": "",
        "REFRESH_COOKIE_PATH": "/api/auth",
        "EDGE_NETWORK": edge_network,
    }
    accounts = {username: secrets.token_urlsafe(18) for username in ACCOUNTS}
    private_directory = directory / ".private"
    private_directory.mkdir(mode=0o700, exist_ok=True)
    private_write(private_directory / "accounts.json", json.dumps(accounts, indent=2) + "\n")
    private_write(directory / ".env", "".join(f"{key}={value}\n" for key, value in values.items()))
    access = "VROAD PREVIEW - PRIVATE ACCESS\nhttps://vroad.160-30-136-28.sslip.io\n\n"
    access += "".join(f"{username}: {password}\n" for username, password in accounts.items())
    access += "\nDemo only. Do not publish these credentials.\n"
    private_write(private_directory / "ACCESS_PRIVATE.txt", access)
    print("Generated independent preview secrets and four unique account passwords (not printed).")


def rotate_access(directory):
    accounts = json.loads((directory / ".private" / "accounts.json").read_text(encoding="utf-8"))
    if set(accounts) != set(ACCOUNTS):
        raise ValueError("Unexpected preview account list.")
    sql = "BEGIN;\n"
    sql += "DO $$ BEGIN IF (SELECT count(*) FROM app_user WHERE username IN ('admin','editor_demo','manager_demo','viewer_demo')) <> 4 THEN RAISE EXCEPTION 'Missing preview accounts'; END IF; END $$;\n"
    for username, password in accounts.items():
        if not all(character.isalnum() or character in "_-" for character in password):
            raise ValueError("Unexpected generated password format.")
        sql += f"UPDATE app_user SET password_hash=crypt('{password}',gen_salt('bf',12)), is_active=TRUE, updated_at=now() WHERE username='{username}';\n"
    sql += "DELETE FROM app_refresh_token;\n"
    sql += "UPDATE app_user SET branch_id='kqldb_1' WHERE username='viewer_demo';\n"
    sql += """
WITH chosen_routes AS (
    SELECT raw_payload->>'route_name' AS route_name
    FROM raw_dataset_record
    WHERE dataset_key='vroad_defects' AND length(trim(raw_payload->>'route_name')) > 0
    GROUP BY raw_payload->>'route_name'
    ORDER BY count(*) DESC, raw_payload->>'route_name' LIMIT 2
)
INSERT INTO vroad_route_assignment (user_id,route_name,purpose,assigned_by)
SELECT actor.id,chosen_routes.route_name,'DEMO',administrator.id
FROM app_user actor CROSS JOIN chosen_routes
CROSS JOIN app_user administrator
WHERE actor.username IN ('editor_demo','manager_demo','viewer_demo')
  AND administrator.username='admin'
ON CONFLICT (user_id,route_name) DO NOTHING;
COMMIT;
"""
    command = ["docker", "compose", "-p", "vroad-preview", "--env-file", str(directory / ".env"),
               "-f", str(directory / "compose.yaml"), "exec", "-T", "postgres", "psql",
               "-X", "-v", "ON_ERROR_STOP=1", "-U", "vroad_preview", "-d", "vroad_preview", "-q"]
    result = subprocess.run(command, input=sql, text=True, encoding="utf-8", capture_output=True, timeout=60)
    if result.returncode:
        raise RuntimeError("Preview credential rotation failed; transaction rolled back. No secrets printed.")
    print("Rotated four preview passwords, revoked copied refresh sessions, assigned two real routes for DEMO access.")


def verify(directory):
    manifest = json.loads((directory / "SHA256SUMS.json").read_text(encoding="utf-8"))
    for relative, expected in manifest.items():
        file = (directory / relative).resolve()
        if not file.is_relative_to(directory) or not file.is_file():
            raise ValueError("Unexpected release file path.")
        actual = hashlib.sha256(file.read_bytes()).hexdigest()
        if actual != expected:
            raise ValueError(f"Release checksum mismatch: {relative}")
    print(f"Verified {len(manifest)} release file checksums.")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("verify", "initialize", "rotate-access"))
    parser.add_argument("directory", type=Path)
    parser.add_argument("--edge-network", default="groupguard-vn_default")
    arguments = parser.parse_args()
    directory = arguments.directory.resolve(strict=True)
    if not (directory / "compose.yaml").is_file():
        raise ValueError("Not a preview release directory.")
    if arguments.action == "verify":
        verify(directory)
    elif arguments.action == "initialize":
        initialize(directory, arguments.edge_network)
    else:
        rotate_access(directory)


if __name__ == "__main__":
    main()
