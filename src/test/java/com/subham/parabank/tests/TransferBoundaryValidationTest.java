package com.subham.parabank.tests;

import com.subham.parabank.database.TransactionQueries;
import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import com.subham.parabank.pages.TransferFundsPage;
import com.subham.parabank.support.AdditionalCoverageBase;

import java.math.BigDecimal;
import java.util.Map;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class TransferBoundaryValidationTest
        extends AdditionalCoverageBase {

    @DataProvider(
            name = "transferBoundaries",
            parallel = false
    )
    public Object[][] transferBoundaries() {

        return new Object[][] {
                {"One cent", "0.01"},
                {"Fractional amount", "12.34"},
                {"Entire available balance", "ALL"}
        };
    }

    @Test(dataProvider = "transferBoundaries")
    public void verifyTransferBoundaryAndReturnTransfer(
            String label, String value) {

        BigDecimal sourceBefore = startingBalance(PRIMARY);

        BigDecimal destinationBefore =
                startingBalance(SECONDARY);

        BigDecimal amount =
                "ALL".equals(value)
                        ? sourceBefore
                        : new BigDecimal(value);

        Assert.assertTrue(
                amount.signum() > 0,
                "Positive balance required for " + label
        );

        Assert.assertTrue(
                sourceBefore.compareTo(amount) >= 0,
                "Insufficient funds for " + label
        );

        Assert.assertTrue(
                amount.stripTrailingZeros().scale() <= 2,
                "Account has sub-cent balance: " + amount
        );

        int sourcePrevious =
                TransactionQueries.getLatestTransactionId(PRIMARY);

        int destinationPrevious =
                TransactionQueries.getLatestTransactionId(SECONDARY);

        TransferFundsPage page =
                new TransferFundsPage(driver);

        page.open();
        page.transfer(PRIMARY, SECONDARY, amount);

        BigDecimal sourceAfter =
                sourceBefore.subtract(amount);

        BigDecimal destinationAfter =
                destinationBefore.add(amount);

        balances(
                Map.of(
                        PRIMARY, sourceAfter,
                        SECONDARY, destinationAfter
                )
        );

        money(
                sourceAfter.add(destinationAfter),
                sourceBefore.add(destinationBefore),
                "Transfer funds conservation"
        );

        transaction(
                PRIMARY, sourcePrevious, "1", amount
        );

        transaction(
                SECONDARY, destinationPrevious, "0", amount
        );

        // Validate the return transfer as a second operation.
        int returnSourcePrevious =
                TransactionQueries.getLatestTransactionId(SECONDARY);

        int returnDestinationPrevious =
                TransactionQueries.getLatestTransactionId(PRIMARY);

        page.open();
        page.transfer(SECONDARY, PRIMARY, amount);

        balances(
                Map.of(
                        PRIMARY, sourceBefore,
                        SECONDARY, destinationBefore
                )
        );

        transaction(
                SECONDARY, returnSourcePrevious, "1", amount
        );

        transaction(
                PRIMARY, returnDestinationPrevious, "0", amount
        );

        Reporter.log(
                "Validated " + label
                        + ": amount=" + amount
                        + "; forward and return transactions match "
                        + "JDBC/API/UI; starting balances restored.",
                true
        );
    }
}