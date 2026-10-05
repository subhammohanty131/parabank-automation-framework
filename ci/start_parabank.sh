#!/usr/bin/env bash
set -euo pipefail

# Run only on a fresh GitHub-hosted runner.
if [[ "${GITHUB_ACTIONS:-}" != "true" || "${RUNNER_ENVIRONMENT:-}" != "github-hosted" ]]; then
    echo "This script is intended only for a fresh GitHub-hosted runner." >&2
    exit 1
fi

export CATALINA_HOME="$RUNNER_TEMP/apache-tomcat-10.1.60"
export CATALINA_BASE="$CATALINA_HOME"

archive="$RUNNER_TEMP/apache-tomcat-10.1.60.tar.gz"
url="https://archive.apache.org/dist/tomcat/tomcat-10/v10.1.60/bin/apache-tomcat-10.1.60.tar.gz"

# Download Tomcat and its published checksum.
curl --fail --silent --show-error --location \
    --retry 2 --max-time 180 \
    "$url" -o "$archive"

curl --fail --silent --show-error --location \
    --retry 2 --max-time 60 \
    "$url.sha512" -o "$archive.sha512"

# Verify the download before extracting it.
python3 - "$archive" <<'PY'
import hashlib
import re
import sys
from pathlib import Path

archive = Path(sys.argv[1])
checksum_file = Path(str(archive) + '.sha512')

expected = re.search(
    r'\b[0-9a-fA-F]{128}\b',
    checksum_file.read_text()
)

actual = hashlib.sha512(archive.read_bytes()).hexdigest()

if expected is None or actual != expected.group().lower():
    raise SystemExit('Tomcat SHA-512 verification failed.')

print('Tomcat archive SHA-512 verified.')
PY

tar -xzf "$archive" -C "$RUNNER_TEMP"

# Configure the disposable Tomcat instance and deploy ParaBank.
python3 - <<'PY'
import os
import shutil
import xml.etree.ElementTree as ET
from pathlib import Path

home = Path(os.environ['CATALINA_HOME'])
server_xml = home / 'conf/server.xml'

tree = ET.parse(server_xml)
connectors = tree.findall('.//Connector')

http_connectors = [
    connector
    for connector in connectors
    if connector.get('port') == '8080'
]

if len(http_connectors) != 1:
    raise SystemExit(
        'Unexpected Tomcat HTTP connector configuration.'
    )

http_connectors[0].set('port', '8081')

# Bind connectors to this runner's loopback interface.
for connector in connectors:
    connector.set('address', '127.0.0.1')

tree.write(
    server_xml,
    encoding='utf-8',
    xml_declaration=True
)

# Remove sample applications only from disposable CI Tomcat.
for name in (
    'ROOT',
    'docs',
    'examples',
    'manager',
    'host-manager'
):
    application = home / 'webapps' / name

    if application.exists():
        shutil.rmtree(application)

war = (
    Path(os.environ['RUNNER_TEMP'])
    / 'parabank-source'
    / 'target'
    / 'parabank-5.0.0-SNAPSHOT.war'
)

if not war.is_file():
    raise SystemExit('Built ParaBank WAR not found.')

shutil.copyfile(
    war,
    home / 'webapps' / 'parabank.war'
)

print('Configured disposable Tomcat and deployed ParaBank WAR.')
PY

# Start Tomcat in the background and retain its console log.
(
    cd "$CATALINA_HOME"

    nohup bash bin/catalina.sh run \
        > "$RUNNER_TEMP/parabank-console.log" 2>&1 &
)

# Open the home page to initialize the fresh database,
# then verify that the seeded account is available through the API.
python3 - <<'PY'
import json
import time
import urllib.request
from decimal import Decimal

home_url = 'http://localhost:8081/parabank/index.htm'
account_url = (
    'http://localhost:8081/parabank/'
    'services/bank/accounts/13566'
)

home_opened = False
deadline = time.monotonic() + 180
last_error = 'not started'

while time.monotonic() < deadline:
    try:
        # ParaBank's home page redirects to initializeDB.htm
        # when the database has no tables.
        # urllib follows that redirect automatically.
        if not home_opened:
            with urllib.request.urlopen(
                home_url,
                timeout=15
            ) as response:
                response.read()

            home_opened = True

            print(
                'Opened ParaBank home page to initialize '
                'the fresh database.'
            )

        request = urllib.request.Request(
            account_url,
            headers={'Accept': 'application/json'}
        )

        with urllib.request.urlopen(
            request,
            timeout=5
        ) as response:
            account = json.load(
                response,
                parse_float=Decimal
            )

        if (
            int(account['id']) != 13566
            or int(account['customerId']) != 12434
        ):
            raise RuntimeError('Unexpected seeded account.')

        balance = Decimal(str(account['balance']))

        if balance != Decimal('5000.00'):
            raise RuntimeError('Unexpected opening balance.')

        print('ParaBank API and isolated HSQLDB seed are ready.')
        break

    except Exception as error:
        last_error = str(error)
        time.sleep(2)

else:
    raise SystemExit(
        'ParaBank readiness timed out: ' + last_error
    )
PY