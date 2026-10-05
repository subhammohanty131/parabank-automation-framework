package com.subham.parabank.expanded;

import com.subham.parabank.database.CoverageQueries;
import com.subham.parabank.database.CoverageQueries.State;
import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.openqa.selenium.By;
import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class LoanCoverageTest extends CoverageSupport {

    @DataProvider(name = "loans", parallel = false)
    public Object[][] loans() {

        return new Object[][] {
                {"UI", "approved"},
                {"UI", "threshold"},
                {"UI", "ratio denied"},
                {"UI", "down payment denied"},
                {"API", "approved"},
                {"API", "ratio denied"}
        };
    }

    @Test(dataProvider = "loans")
    public void loanDecisionAndFinancialEffects(
            String channel,
            String scenario) {

        Fixture f = fixture();

        State before =
                CoverageQueries.state(f.customerId());

        Assert.assertEquals(
                CoverageQueries.parameter("loanProcessor"),
                "funds",
                "These cases require Available Funds processor."
        );

        Assert.assertEquals(
                CoverageQueries.parameter("loanProcessorThreshold"),
                "20",
                "These cases require threshold 20%."
        );

        BigDecimal available =
                CoverageQueries.availableFunds(f.customerId());

        BigDecimal down =
                new BigDecimal("20.00");

        Assert.assertTrue(
                before.accounts()
                        .get(f.accountId())
                        .balance()
                        .compareTo(down) >= 0,
                "Insufficient down-payment funds."
        );

        BigDecimal amount =
                new BigDecimal("100.00");

        if (scenario.equals("threshold")) {
            amount = available.multiply(
                    new BigDecimal("5")
            );
        }

        if (scenario.equals("ratio denied")) {
            amount = available.multiply(
                    new BigDecimal("10000")
            );
        }

        if (scenario.equals("down payment denied")) {
            down = available.add(BigDecimal.ONE);
        }

        boolean expectedApproval =
                scenario.equals("approved")
                        || scenario.equals("threshold");

        int newId = 0;

        if (channel.equals("API")) {

            var j = json(request(
                    "POST",
                    "requestLoan",
                    Map.of(
                            "customerId", f.customerId(),
                            "amount", amount.toPlainString(),
                            "downPayment", down.toPlainString(),
                            "fromAccountId", f.accountId()
                    )
            ));

            Boolean approved = j.get("approved");

            Assert.assertNotNull(
                    approved,
                    "Loan response must contain an approval decision."
            );

            Assert.assertEquals(
                    approved.booleanValue(),
                    expectedApproval,
                    "Unexpected API loan decision."
            );

            String provider =
                    j.getString("loanProviderName");

            Assert.assertNotNull(
                    provider,
                    "Missing loan provider."
            );

            Assert.assertFalse(
                    provider.isBlank(),
                    "Missing loan provider."
            );

            if (expectedApproval) {

                Number accountId =
                        j.get("accountId");

                Assert.assertNotNull(
                        accountId,
                        "Approved loan must return an accountId."
                );

                newId = accountId.intValue();

                Assert.assertTrue(
                        newId > 0,
                        "Approved loan accountId must be positive."
                );

            } else {

                Number accountId =
                        j.get("accountId");

                Assert.assertTrue(
                        accountId == null
                                || accountId.intValue() == 0,
                        "Denied loan must not return a created account ID."
                );

                Assert.assertEquals(
                        j.getString("message"),
                        "error.insufficient.funds",
                        "Unexpected loan denial reason."
                );
            }

        } else {

            open("requestloan.htm");

            select(
                    By.id("fromAccountId"),
                    f.accountId()
            );

            fill(
                    By.id("amount"),
                    amount.toPlainString()
            );

            fill(
                    By.id("downPayment"),
                    down.toPlainString()
            );

            click(
                    By.cssSelector("input[value='Apply Now']")
            );

            String expected =
                    expectedApproval ? "Approved" : "Denied";

            wait.until(d ->
                    expected.equalsIgnoreCase(
                            d.findElement(
                                    By.id("loanStatus")
                            ).getText().trim()
                    )
            );

            Assert.assertFalse(
                    visible(
                            By.id("loanProviderName")
                    ).getText().isBlank(),
                    "Missing UI loan provider."
            );

            if (expectedApproval) {

                newId = Integer.parseInt(
                        visible(
                                By.id("newAccountId")
                        ).getText().trim()
                );

            } else {

                Assert.assertFalse(
                        visible(
                                By.cssSelector(
                                        "#loanRequestDenied p.error"
                                )
                        ).getText().isBlank(),
                        "Missing UI loan denial reason."
                );
            }
        }

        if (!expectedApproval) {

            Assert.assertEquals(
                    newId,
                    0,
                    "Denied loan must not create an account."
            );

            unchanged(
                    f.customerId(),
                    before
            );

            checkBalances(f.customerId());

            Reporter.log(
                    "Validated denied " + channel
                            + " loan: " + scenario
                            + "; accounts, balances and transactions unchanged.",
                    true
            );

            return;
        }

        validateApprovedLoan(
                f,
                before,
                newId,
                amount,
                down
        );

        Reporter.log(
                "Validated " + channel
                        + " loan: " + scenario
                        + ", amount=" + amount
                        + ", down=" + down,
                true
        );
    }

    private void validateApprovedLoan(
            Fixture f,
            State before,
            int newId,
            BigDecimal amount,
            BigDecimal down) {

        Assert.assertTrue(
                newId > 0,
                "New loan account ID must be positive."
        );

        Assert.assertFalse(
                before.accounts().containsKey(newId),
                "Loan account already existed before the request."
        );

        State after =
                CoverageQueries.state(f.customerId());

        Set<Integer> added =
                new HashSet<>(after.accounts().keySet());

        added.removeAll(
                before.accounts().keySet()
        );

        Assert.assertEquals(
                added,
                Set.of(newId),
                "Expected exactly one new loan account."
        );

        Assert.assertTrue(
                after.accounts().keySet().containsAll(
                        before.accounts().keySet()
                ),
                "An existing account disappeared after loan approval."
        );

        var loan =
                after.accounts().get(newId);

        Assert.assertEquals(
                loan.type(),
                2,
                "New account must be a loan account."
        );

        Assert.assertEquals(
                loan.customerId(),
                f.customerId(),
                "Loan account belongs to the wrong customer."
        );

        money(
                loan.balance(),
                amount,
                "Loan account balance"
        );

        before.accounts().forEach((id, account) ->
                money(
                        after.accounts().get(id).balance(),
                        id == f.accountId()
                                ? account.balance().subtract(down)
                                : account.balance(),
                        "Post-loan balance " + id
                )
        );

        var rows =
                addedTransactions(f.customerId(), before);

        Assert.assertEquals(
                rows.size(),
                1,
                "Expected exactly one loan down-payment transaction."
        );

        var debit =
                rows.get(0);

        Assert.assertEquals(
                debit.accountId(),
                f.accountId(),
                "Down payment used the wrong funding account."
        );

        Assert.assertEquals(
                debit.type(),
                "1",
                "Down-payment transaction must be a debit."
        );

        money(
                debit.amount(),
                down,
                "Loan down-payment debit"
        );

        Assert.assertEquals(
                debit.description(),
                "Down Payment for Loan # " + newId,
                "Unexpected down-payment description."
        );

        checkBalances(f.customerId());

        checkTransaction(debit);
    }
}