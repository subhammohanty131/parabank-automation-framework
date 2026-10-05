#!/usr/bin/env bash
set -euo pipefail

if [[ "${GITHUB_ACTIONS:-}" != "true" ||
      "${RUNNER_ENVIRONMENT:-}" != "github-hosted" ]]; then
  echo 'This script is intended only for a fresh GitHub-hosted runner.' >&2
  exit 1
fi

export CATALINA_HOME="$RUNNER_TEMP/apache-tomcat-10.1.60"
export CATALINA_BASE="$CATALINA_HOME"

archive="$RUNNER_TEMP/apache-tomcat-10.1.60.tar.gz"

url='https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.60/bin/apache-tomcat-10.1.60.tar.gz'

curl --fail --silent --show-error --location \
  --retry 2 --max-time 180 \
  "$url" -o "$archive"

curl --fail --silent --show-error --location \
  --retry 2 --max-time 60 \
  "$url.sha512" -o "$archive.sha512"

python3 - "$archive" <<'PY'
import hashlib
import re
import sys
from pathlib import Path

archive = Path(sys.argv[1])

expected = re.search(
    r"\b[0-9a-fA-F]{128}\b",
    Path(str(archive) + ".sha512").read_text(),
)

actual = hashlib.sha512(
    archive.read_bytes()
).hexdigest()

if expected is None or actual != expected.group().lower():
    raise SystemExit("Tomcat SHA-512 verification failed.")

print("Tomcat archive SHA-512 verified.")
PY

tar -xzf "$archive" -C "$RUNNER_TEMP"

python3 - <<'PY'
import os
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

home = Path(os.environ["CATALINA_HOME"])
server = home / "conf/server.xml"

tree = ET.parse(server)
connectors = tree.findall(".//Connector")

http = [
    connector
    for connector in connectors
    if connector.get("port") == "8080"
]

if len(http) != 1:
    raise SystemExit(
        "Unexpected Tomcat HTTP connector configuration."
    )

http[0].set("port", "8081")

for connector in connectors:
    connector.set("address", "127.0.0.1")

tree.write(
    server,
    encoding="utf-8",
    xml_declaration=True,
)

# Remove sample applications only from disposable CI Tomcat.
for name in ("ROOT", "docs", "examples", "manager", "host-manager"):
    path = home / "webapps" / name

    if path.exists():
        shutil.rmtree(path)

war = (
    Path(os.environ["RUNNER_TEMP"])
    / "parabank-source/target/parabank-5.0.0-SNAPSHOT.war"
)

if not war.is_file():
    raise SystemExit("Built ParaBank WAR not found.")

shutil.copyfile(
    war,
    home / "webapps/parabank.war",
)
PY

(
  cd "$CATALINA_HOME"
  nohup bash bin/catalina.sh run \
    > "$RUNNER_TEMP/parabank-console.log" 2>&1 &
)

python3 - <<'PY'
import json
import time
import urllib.request

url = (
    "http://localhost:8081/parabank"
    "/services/bank/accounts/13566"
)

deadline = time.monotonic() + 180
last_error = "not started"

while time.monotonic() < deadline:
    try:
        request = urllib.request.Request(
            url,
            headers={"Accept": "application/json"},
        )

        with urllib.request.urlopen(
            request,
            timeout=5,
        ) as response:
            account = json.load(response)

        if (
            int(account["id"]) != 13566
            or int(account["customerId"]) != 12434
        ):
            raise RuntimeError("Unexpected seeded account.")

        if float(account["balance"]) != 5000.0:
            raise RuntimeError("Unexpected opening balance.")

        print(
            "ParaBank API and isolated HSQLDB seed are ready."
        )
        break

    except Exception as error:
        last_error = str(error)
        time.sleep(2)

else:
    raise SystemExit(
        "ParaBank readiness timed out: " + last_error
    )
PY