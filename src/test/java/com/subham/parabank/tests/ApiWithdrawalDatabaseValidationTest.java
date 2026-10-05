package com.subham.parabank.tests;

import com.subham.parabank.api.BankActionsApi;
import com.subham.parabank.database.TransactionQueries;
import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import com.subham.parabank.support.AdditionalCoverageBase;

import java.math.BigDecimal;
import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class ApiWithdrawalDatabaseValidationTest
        extends AdditionalCoverageBase {

    @Test
    public void verifyApiWithdrawalAcrossDatabaseAndUI() {

        BigDecimal amount = new BigDecimal("25.00");

        BigDecimal before = startingBalance(PRIMARY);
        BigDecimal otherBefore = startingBalance(SECONDARY);

        Assert.assertTrue(
                before.compareTo(amount) >= 0,
                "Insufficient withdrawal funds: " + before
        );

        int previousId =
                TransactionQueries.getLatestTransactionId(PRIMARY);

        int otherPreviousId =
                TransactionQueries.getLatestTransactionId(SECONDARY);

        new BankActionsApi().withdraw(PRIMARY, amount);

        balances(
                Map.of(
                        PRIMARY, before.subtract(amount),
                        SECONDARY, otherBefore
                )
        );

        transaction(PRIMARY, previousId, "1", amount);

        Assert.assertTrue(
                TransactionQueries.getTransactionsAfter(
                        SECONDARY, otherPreviousId
                ).isEmpty(),
                "Withdrawal created a transaction in the other account."
        );
    }
}