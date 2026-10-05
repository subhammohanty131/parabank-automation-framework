package com.subham.parabank.tests;

import com.subham.parabank.database.TransactionQueries;
import com.subham.parabank.database.TransactionQueries.Transaction;
import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import com.subham.parabank.pages.BillPaymentPage;
import com.subham.parabank.pages.BillPaymentPage.Payee;
import com.subham.parabank.support.AdditionalCoverageBase;

import java.math.BigDecimal;
import java.util.Map;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class BillPaymentDatabaseValidationTest
        extends AdditionalCoverageBase {

    @Test
    public void verifyBillPaymentAcrossUIApiAndDatabase() {

        BigDecimal amount = new BigDecimal("25.00");

        BigDecimal before = startingBalance(PRIMARY);
        BigDecimal otherBefore = startingBalance(SECONDARY);

        Assert.assertTrue(
                before.compareTo(amount) >= 0,
                "Insufficient bill payment funds: " + before
        );

        int previousId =
                TransactionQueries.getLatestTransactionId(PRIMARY);

        int otherPreviousId =
                TransactionQueries.getLatestTransactionId(SECONDARY);

        Payee payee = new Payee(
                "Automation Utilities",
                "10 Test Street",
                "Test City",
                "Test State",
                "12345",
                "5551234567",
                "987654"
        );

        BillPaymentPage page = new BillPaymentPage(driver);
        page.open();

        String confirmation =
                page.pay(payee, PRIMARY, amount);

        Assert.assertTrue(
                confirmation.contains(payee.name()),
                "Payee missing from UI confirmation."
        );

        Assert.assertTrue(
                confirmation.contains(Integer.toString(PRIMARY)),
                "Source missing from UI confirmation."
        );

        Assert.assertTrue(
                confirmation.contains(amount.toPlainString()),
                "Amount missing from UI confirmation."
        );

        Reporter.log(
                "Bill payment confirmation: " + confirmation,
                true
        );

        balances(
                Map.of(
                        PRIMARY, before.subtract(amount),
                        SECONDARY, otherBefore
                )
        );

        Transaction row =
                transaction(PRIMARY, previousId, "1", amount);

        Assert.assertTrue(
                row.description().contains(payee.name()),
                "Payee missing from transaction description."
        );

        Assert.assertTrue(
                TransactionQueries.getTransactionsAfter(
                        SECONDARY, otherPreviousId
                ).isEmpty(),
                "Bill payment created a transaction in the other account."
        );
    }
}