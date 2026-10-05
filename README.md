# ParaBank End-to-End Banking Test Automation Framework

Java 17 automation project by Subham Mohanty using Selenium WebDriver,
TestNG, Maven, REST Assured and JDBC against a local ParaBank application
and HSQLDB database.

## Verified Execution

On 5 October 2026, the combined TestNG regression suite executed:

- Tests: 70
- Passed: 70
- Failed: 0
- Skipped: 0

All six loan scenarios passed within the combined run.

A separate rejection-contract suite executed:

- Tests: 19
- Passed: 4
- Failed: 15
- Skipped: 0

These rejection findings are documented in
[KNOWN_DEFECTS.md](docs/KNOWN_DEFECTS.md).

Execution results are snapshots. They do not establish that every
application scenario is covered or that known defects are resolved.

An earlier UI loan approval timeout passed on both a standalone rerun
and the combined regression rerun. Its cause remains unconfirmed.

## Technology Stack

- Java 17
- Selenium WebDriver
- TestNG
- Maven
- REST Assured
- JDBC
- HSQLDB
- Apache Tomcat
- Screenshot evidence and custom HTML reporting

## Functional Coverage

- Registration and duplicate-username validation
- Successful and unsuccessful login
- Logout and session validation
- Profile updates and required-field validation
- Checking and savings account creation
- Opening deposits and funding-account balance changes
- Fund transfers and boundary amounts
- Deposits and withdrawals
- Bill payments and field-validation errors
- Transaction history
- Transaction searches by ID, amount, date and date range
- UI and API loan approvals and denials
- API error and financial rejection contracts

Balances, account ownership, account types and transactions are checked
through UI, API and JDBC where applicable to each scenario.
Not every test exercises all three layers.

## Prerequisites

- Java 17
- Maven
- Google Chrome
- Tomcat 10.1.60
- ParaBank deployed at http://localhost:8081/parabank/
- HSQLDB listening on port 9001

The current JDBC implementation uses:

- URL: jdbc:hsqldb:hsql://localhost/parabank
- Username: sa
- Password: empty

These settings are intended for the local demo environment.

## Configuration

Copy:

src/test/resources/config.example.properties

to:

src/test/resources/config.properties

Enter the local test password.

The existing tests require customer Selenium_User_01 and accounts
13566 and 13677.

The loan suite currently requires these application parameters:

- loanProcessor=funds
- loanProcessorThreshold=20

Start Tomcat and HSQLDB before execution.

Tests read current balances dynamically. Previous-run balances should
not be used as fixed expected values.

## Execution

Run commands from the project root.

### Complete Regression: 70 Tests

    mvn clean test

### Expanded Coverage: 55 Tests

    mvn test "-DsuiteFile=testng-expanded.xml"

### Rejection Contracts: 19 Tests

    mvn test "-DsuiteFile=testng-rejection-contracts.xml"

Rejection failures retain a failed build status.
No automatic retry or failure-ignore setting is configured.

Execution is sequential because the current driver factory is static.

In Eclipse, right-click the suite XML and select:

Run As > TestNG Suite

## Reports

Custom HTML report:

test-output/parabank-report.html

Maven reports:

target/surefire-reports/

Failure screenshots and evidence are stored under test-output.

Preserve the accompanying evidence and screenshot folders when copying
the HTML report. The next execution overwrites the report, so archive
important results.

## Test Data

Tests create customers and accounts and perform financial transactions.

Regular runs consume funding from the configured demo customer.
Replenish funds when required.

Rejection tests use generated customers and may leave negative balances
or invalid transactions when the application accepts invalid requests.

Use a disposable local demo database.

## Continuous Integration

The workflow at:

.github/workflows/parabank-regression.yml

is manually triggered and requires a Windows self-hosted runner on the
computer hosting ParaBank and HSQLDB.

Required custom runner label:

parabank-local

Run the runner interactively for the existing headed Chrome setup.

Set PARABANK_CONFIG_FILE to the absolute path of a local config.properties
file outside the repository checkout.

The workflow checks prerequisites, runs the regression suite and uploads
reports. It does not install or start ParaBank or HSQLDB.

CI execution has not yet been verified.

Do not configure this runner to execute untrusted pull-request code.