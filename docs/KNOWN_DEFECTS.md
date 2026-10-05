# ParaBank Validation Findings

Evidence: rejection-contract execution on 5 October 2026.

- Tests: 19
- Passed: 4
- Failed: 15
- Configuration failures: 0

IDs below group related findings. Fifteen failed test invocations do
not necessarily represent fifteen independent defects.

Severity ratings are proposed triage ratings.

| ID | Scenario | Observed Result | Expected Contract / Status | Severity |
|---|---|---|---|---|
| PB-001 | Blank or nonnumeric transfer | Internal error; financial state unchanged | Field validation; existing known issue | Medium |
| PB-002 | Zero transfer | Two zero-value ledger rows created | Reject without ledger changes | Medium |
| PB-003 | Negative transfer | Source increased by 1; destination decreased by 1 | Reject negative amounts | High |
| PB-004 | Transfer exceeding funds | Source balance became -1 | Test expects rejection; confirm overdraft policy | High |
| PB-005 | Transfer to the same account | Debit and credit rows created; net balance unchanged | Test expects rejection; business rule needs clarification | Pending |
| PB-006 | Open account with zero funding | New account received 100; funding became -100 | Test expects rejection; confirm funding policy | High |
| PB-007 | Account type 999 | HTTP 500; financial-state assertion passed | Client validation error with unchanged state | Medium |
| PB-008 | Invalid loan inputs | Blank/nonnumeric loan amount, zero amount, and blank/nonnumeric down payment displayed internal errors | Input validation; financial-state assertions passed | Medium |
| PB-009 | Negative loan down payment | Loan approved; funding increased by 20; debit amount -20 | Reject negative down payment without mutation | High |
| PB-010 | Zero bill payment | Zero-value debit created | Reject nonpositive payment | Medium |
| PB-011 | Negative bill payment | Funding increased by 1; debit amount -1 | Reject negative payment | High |
| API-001 | Nonexistent account lookup | HTTP 400 instead of expected 404 | Confirm required status before classifying as a defect | Pending |

## Passing Rejection Cases

- Malformed account ID returned HTTP 404; the test accepts 400 or 404.
- Nonexistent API endpoint returned HTTP 404.
- GET against the deposit endpoint returned HTTP 405.
- Loan amount -100 with down payment 20 passed the existing rejection checks.

The negative-loan result does not establish that dedicated field
validation exists.

## Reproduction

Run:

    mvn test "-DsuiteFile=testng-rejection-contracts.xml"

Each data provider identifies the scenario.

Review these together:

- Test parameters
- Assertion failure
- Database state before and after
- Screenshot
- HTML report

Preserve the original report and its linked images.

Do not mark these findings fixed because the regression suite passes.