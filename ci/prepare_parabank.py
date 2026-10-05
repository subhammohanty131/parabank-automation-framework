import os
import secrets
import string
from pathlib import Path

if (
    os.environ.get("GITHUB_ACTIONS") != "true"
    or os.environ.get("RUNNER_ENVIRONMENT") != "github-hosted"
):
    raise SystemExit(
        "This script is intended only for a fresh GitHub-hosted runner."
    )

source = Path(os.environ["RUNNER_TEMP"]) / "parabank-source"

seed = (
    source
    / "src/main/resources/com/parasoft/parabank/dao/jdbc/sql/insert.sql"
)

sql = seed.read_text()

required = [
    "('Customer', 12434)",
    "('Account', 13566)",
    "('initialBalance', '515.50')",
    "('minimumBalance', '100.00')",
    "('loanProcessor', 'funds')",
    "('loanProcessorThreshold', '20')",
    "('accessmode', 'jdbc')",
]

for expected in required:
    if expected not in sql:
        raise SystemExit(
            "Unexpected seed configuration: " + expected
        )

password = ''.join(
    secrets.choice(string.ascii_letters + string.digits)
    for _ in range(20)
)

# Generated demo-only credential; never use a personal password.
print("::add-mask::" + password)

sql += f"""
INSERT INTO Customer
(id, first_name, last_name, address, city, state, zip_code,
phone_number, ssn, username, password)
VALUES
(12434, 'Automation', 'Customer', '10 Test Street',
'Test City', 'Test State', '12345', '5551234567',
'111-22-3333', 'Selenium_User_01', '{password}');

INSERT INTO Account (id, customer_id, type, balance)
VALUES (13566, 12434, 0, 5000.00);

INSERT INTO Account (id, customer_id, type, balance)
VALUES (13677, 12434, 1, 5000.00);

UPDATE Sequence SET next_id = 12545 WHERE name = 'Customer';
UPDATE Sequence SET next_id = 13788 WHERE name = 'Account';
"""

seed.write_text(sql)

hsqldb = (
    source
    / "src/main/resources/applicationContext-hsqldb.xml"
)

xml = hsqldb.read_text()

if '<prop key="server.silent">true</prop>' not in xml:
    raise SystemExit("Unexpected HSQLDB configuration.")

xml = xml.replace(
    '<prop key="server.silent">true</prop>',
    '<prop key="server.address">127.0.0.1</prop>\n'
    '<prop key="server.port">9001</prop>\n'
    '<prop key="server.silent">true</prop>',
)

hsqldb.write_text(xml)

jms = (
    source
    / "src/main/resources/applicationContext-jms.xml"
)

jms.write_text(
    jms.read_text().replace("0.0.0.0", "127.0.0.1")
)

config = Path("src/test/resources/config.properties")
config.parent.mkdir(parents=True, exist_ok=True)

config.write_text(
    "baseUrl=http://localhost:8081/parabank/\n"
    "username=Selenium_User_01\n"
    "password=" + password + "\n"
)

print(
    "Prepared isolated demo customer, accounts and test configuration."
)