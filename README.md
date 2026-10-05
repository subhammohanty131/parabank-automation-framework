# ParaBank End-to-End Banking Test Automation Framework

[![Hosted Regression](https://github.com/subhammohanty131/parabank-automation-framework/actions/workflows/parabank-regression.yml/badge.svg?branch=main)](https://github.com/subhammohanty131/parabank-automation-framework/actions/workflows/parabank-regression.yml)

A banking automation portfolio project by Subham Mohanty using Java,
Selenium WebDriver, TestNG, Maven, REST Assured and JDBC.

The framework checks banking behavior and financial data across the
ParaBank UI, REST API and HSQLDB database where applicable.

## Verified Results

On 6 October 2026, GitHub-hosted regression run #4 completed successfully:

| Metric | Result |
|---|---:|
| Tests executed | 70 |
| Passed | 70 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| Configuration failures | 0 |
| Workflow duration | 7 minutes 47 seconds |

Verified framework commit: `83c88ed`.

[View the verified CI run](https://github.com/subhammohanty131/parabank-automation-framework/actions/runs/37379770919)

The uploaded artifact contains the HTML report, Maven test results,
known-defect screenshot evidence and server logs.

The same 70-test regression suite also passed locally.

These results describe specific executions. They do not establish
complete application coverage or resolution of known defects.

## Technology Stack

- Java 17
- Selenium WebDriver 4.35.0
- TestNG 7.11.0
- Maven and Maven Surefire
- REST Assured 6.0.1
- JDBC and HSQLDB 2.7.4
- Apache Tomcat 10.1.60
- GitHub Actions
- Failure screenshots and custom HTML reporting

## Coverage

- Customer registration and duplicate-username validation
- Successful login, failed login, logout and session checks
- Profile updates and required-field validation
- Checking and savings account creation variations
- Opening deposits and funding-account balance changes
- Fund transfers and boundary amounts
- API deposits and withdrawals
- Bill payments and field-validation errors
- Transaction history and transaction details
- Transaction searches by ID, amount, date and date range
- UI and API loan approvals, threshold approval and denials
- UI, API and database balance comparisons
- Invalid transfer data-integrity checks

A separate suite checks stricter API error and financial rejection
contracts. Not every test uses all three validation layers.

## Project Structure

| Location | Purpose |
|---|---|
| `src/main/java/.../base` | Browser lifecycle |
| `src/main/java/.../pages` | Selenium page objects |
| `src/main/java/.../database` | JDBC queries |
| `src/main/java/.../utils` | Configuration and screenshots |
| `src/test/java/.../api` | API clients and validation |
| `src/test/java/.../tests` | Core banking tests |
| `src/test/java/.../expanded` | Expanded functional coverage |
| `src/test/java/.../contracts` | Strict rejection contracts |
| `src/test/java/.../listeners` | Screenshots and HTML reporting |
| `ci/` | Disposable CI environment preparation |
| `.github/workflows/` | GitHub Actions workflows |
| `docs/KNOWN_DEFECTS.md` | Documented findings |

## Local Setup

Required:

- Java 17 selected for Maven and Tomcat
- Maven
- Google Chrome
- ParaBank deployed on Tomcat 10.1.60
- Application URL: `http://localhost:8081/parabank/`
- HSQLDB available on port 9001

The current JDBC connection is:

```text
URL: jdbc:hsqldb:hsql://localhost/parabank
Username: sa
Password: empty
```

These connection settings are for the disposable demo environment.

Copy the example configuration:

```powershell
Copy-Item src/test/resources/config.example.properties src/test/resources/config.properties
```

Enter the local demo customer's password in `config.properties`.

Core tests require:

- Customer username: `Selenium_User_01`
- Account `13566`: checking
- Account `13677`: savings
- Both accounts owned by the configured customer
- Sufficient funds for transfers and account-opening deposits

Expanded tests create isolated customers for their scenarios.

Loan tests require these application parameters:

```properties
loanProcessor=funds
loanProcessorThreshold=20
```

Start the application and its HSQLDB server before running local tests.
Opening ParaBank's home page initializes a fresh database.

Tests read balances dynamically rather than assuming previous-run values.

## Run Tests

Run commands from the automation project's root.

Complete regression, currently 70 tests:

```powershell
mvn clean test
```

Expanded coverage, currently 55 tests:

```powershell
mvn test "-DsuiteFile=testng-expanded.xml"
```

Core banking coverage, currently 15 tests:

```powershell
mvn test "-DsuiteFile=testng.xml"
```

Strict rejection contracts, currently 19 tests:

```powershell
mvn test "-DsuiteFile=testng-rejection-contracts.xml"
```

In Eclipse, right-click the relevant suite XML and select
**Run As → TestNG Suite**.

Execution is sequential. The current static driver factory does not
support parallel test execution.

## Reports and Screenshots

The custom report is written to TestNG's output directory:

| Execution | Custom report location |
|---|---|
| Eclipse TestNG, default output | `test-output/parabank-report.html` |
| Maven Surefire | `target/surefire-reports/parabank-report.html` |

Maven XML and text results are under `target/surefire-reports/`.

Screenshots are saved under `test-output/screenshots/`.
The custom report copies attached screenshots into its accompanying
`evidence/` directory.

Preserve the report and its evidence directory together.
Archive important results before another execution overwrites them.

## GitHub-Hosted CI

Two workflows are configured:

| Workflow | Trigger | Purpose |
|---|---|---|
| ParaBank Build Check (Tests Not Run) | Push to main or manual | Compile application and test sources |
| ParaBank Hosted Regression (70 Tests) | Manual | Prepare ParaBank and execute full regression |

To run regression:

1. Open the repository's Actions tab.
2. Select **ParaBank Hosted Regression (70 Tests)**.
3. Click **Run workflow**.
4. Select `main` and start the run.
5. Download the evidence artifact from the completed run.

The regression workflow:

- Uses a GitHub-hosted Ubuntu runner and Java 17.
- Fetches ParaBank source at commit
  `cea469acea34a05e97b4c81a82cf8d736b489545`.
- Generates a temporary demo password and seeds two funded accounts.
- Builds ParaBank and verifies the downloaded Tomcat archive checksum.
- Starts Tomcat and embedded HSQLDB on the runner.
- Opens the home page to initialize the fresh database.
- Runs Selenium through an Xvfb virtual display.
- Requires exactly 70 tests with no failures, errors or skips.
- Uploads reports, screenshot evidence and server logs.
- Stops the disposable Tomcat instance.

Artifacts are retained for seven days.

This workflow runs independently of the developer's computer.
No local runner, tunnel or personal application password is required.
Application and database services bind to the runner's loopback interface.

Workflow permissions are limited to repository read access.
Third-party GitHub Actions are pinned to full commit SHAs.
Generated `config.properties` is not included in the artifact.

## Known Defects and Rejection Contracts

See [Known Defects](docs/KNOWN_DEFECTS.md).

A separate local rejection-contract execution recorded:

- 19 tests
- 4 passed
- 15 failed
- 0 skipped

These are test results, not a count of independent defects.
Some rejection expectations require clarification of application policy.

The strict rejection suite is excluded from the default 70-test
regression suite. Running it preserves its failed build status when
the application violates an asserted contract.

PB-001 data-integrity tests pass because invalid input leaves balances
and transactions unchanged. The application's internal-error response
remains a documented validation defect.

## Test Data and Limitations

Tests create customers, accounts and transactions.
Core tests consume funding from the configured demo customer.
Replenish local demo funds when needed.

Use a disposable test database. Tests do not roll back all changes.
Each hosted regression run starts with a fresh environment.

Current coverage does not include every possible input combination,
all browsers, load testing or a comprehensive security assessment.