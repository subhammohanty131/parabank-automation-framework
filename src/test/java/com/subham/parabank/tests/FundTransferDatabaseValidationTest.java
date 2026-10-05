package com.subham.parabank.tests;

import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import org.testng.annotations.Listeners;
import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.database.AccountQueries;
import com.subham.parabank.database.TransactionQueries;
import com.subham.parabank.database.TransactionQueries.Transaction;
import com.subham.parabank.pages.AccountActivityPage;
import com.subham.parabank.pages.AccountActivityPage.HistoryTransaction;
import com.subham.parabank.pages.AccountsOverviewPage;
import com.subham.parabank.pages.LoginPage;
import com.subham.parabank.pages.TransferFundsPage;
import com.subham.parabank.utils.ConfigReader;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import com.subham.parabank.api.ApiValidation;

@Listeners({
    FailureScreenshotListener.class,
    HtmlReportListener.class
})
public class FundTransferDatabaseValidationTest {

    private WebDriver driver;

    private static final int SOURCE = 13566;
    private static final int DESTINATION = 13677;

    private static final BigDecimal AMOUNT =
            new BigDecimal("10.00");

    @BeforeMethod
    public void setup() {

        driver = DriverFactory.initializeDriver();

        driver.get(
                ConfigReader.get("baseUrl")
        );
    }

    @Test
    public void verifyFundTransferBalancesAndTransactions() {

        AccountsOverviewPage overview =
                new LoginPage(driver).login(
                        ConfigReader.get("username"),
                        ConfigReader.get("password")
                );

        Assert.assertTrue(
                overview.isLoaded(),
                "Accounts Overview did not load."
        );

        // Read live starting balances.
        BigDecimal sourceBefore =
                AccountQueries.getAccountBalance(SOURCE);

        BigDecimal destinationBefore =
                AccountQueries.getAccountBalance(DESTINATION);

        Assert.assertTrue(
                sourceBefore.compareTo(AMOUNT) >= 0,
                "Source account needs at least " + AMOUNT
                        + "; current balance: " + sourceBefore
        );

        assertMoney(
                overview.getBalance(SOURCE),
                sourceBefore,
                "Source UI balance before transfer"
        );

        assertMoney(
                overview.getBalance(DESTINATION),
                destinationBefore,
                "Destination UI balance before transfer"
        );
        
        ApiValidation.assertBalance(SOURCE, sourceBefore);
        ApiValidation.assertBalance(DESTINATION, destinationBefore);
        
        // Capture transaction IDs before submitting the transfer.
        int sourcePreviousId =
                TransactionQueries.getLatestTransactionId(SOURCE);

        int destinationPreviousId =
                TransactionQueries.getLatestTransactionId(DESTINATION);

        TransferFundsPage transferPage =
                new TransferFundsPage(driver);

        transferPage.open();

        transferPage.transfer(
                SOURCE,
                DESTINATION,
                AMOUNT
        );

        String confirmation =
                transferPage.getConfirmationText();

        Assert.assertTrue(
                confirmation.contains(Integer.toString(SOURCE)),
                "Source account missing from confirmation."
        );

        Assert.assertTrue(
                confirmation.contains(Integer.toString(DESTINATION)),
                "Destination account missing from confirmation."
        );

        BigDecimal expectedSource =
                sourceBefore.subtract(AMOUNT);

        BigDecimal expectedDestination =
                destinationBefore.add(AMOUNT);

        // Wait until both new database records exist.
        new WebDriverWait(
                driver,
                Duration.ofSeconds(10)
        ).until(d ->
                !TransactionQueries.getTransactionsAfter(
                        SOURCE,
                        sourcePreviousId
                ).isEmpty()
                &&
                !TransactionQueries.getTransactionsAfter(
                        DESTINATION,
                        destinationPreviousId
                ).isEmpty()
        );

        BigDecimal sourceAfter =
                AccountQueries.getAccountBalance(SOURCE);

        BigDecimal destinationAfter =
                AccountQueries.getAccountBalance(DESTINATION);

        assertMoney(
                sourceAfter,
                expectedSource,
                "Source DB balance after transfer"
        );

        assertMoney(
                destinationAfter,
                expectedDestination,
                "Destination DB balance after transfer"
        );

        assertMoney(
                sourceAfter.add(destinationAfter),
                sourceBefore.add(destinationBefore),
                "Combined balance must remain unchanged"
        );

        // Validate and retain the new records for UI comparison.
        Transaction debitTransaction = validateTransaction(
                TransactionQueries.getTransactionsAfter(
                        SOURCE,
                        sourcePreviousId
                ),
                SOURCE,
                "1",
                sourcePreviousId
        );

        Transaction creditTransaction = validateTransaction(
                TransactionQueries.getTransactionsAfter(
                        DESTINATION,
                        destinationPreviousId
                ),
                DESTINATION,
                "0",
                destinationPreviousId
        );
        
        ApiValidation.assertBalance(SOURCE, sourceAfter);
        ApiValidation.assertBalance(DESTINATION, destinationAfter);

        ApiValidation.assertTransaction(debitTransaction);
        ApiValidation.assertTransaction(creditTransaction);
        
        AccountsOverviewPage updatedOverview =
                transferPage.openAccountsOverview();

        assertMoney(
                updatedOverview.getBalance(SOURCE),
                sourceAfter,
                "Updated source UI versus DB"
        );

        assertMoney(
                updatedOverview.getBalance(DESTINATION),
                destinationAfter,
                "Updated destination UI versus DB"
        );

        // Check both new transactions in the account history UI.
        validateHistoryTransaction(debitTransaction);
        validateHistoryTransaction(creditTransaction);

        System.out.printf(
                "Transfer %s: %d %s -> %s; %d %s -> %s%n",
                AMOUNT,
                SOURCE,
                sourceBefore,
                sourceAfter,
                DESTINATION,
                destinationBefore,
                destinationAfter
        );
    }

    private Transaction validateTransaction(
            List<Transaction> rows,
            int accountId,
            String expectedTypeCode,
            int previousId) {

        Assert.assertEquals(
                rows.size(),
                1,
                "Expected exactly one new transaction for "
                        + accountId + ": " + rows
        );

        Transaction transaction = rows.get(0);

        Assert.assertTrue(
                transaction.id() > previousId,
                "Transaction must be new."
        );

        Assert.assertEquals(
                transaction.accountId(),
                accountId,
                "Incorrect transaction account."
        );

        Assert.assertNotNull(
                transaction.type(),
                "Missing transaction type."
        );

        Assert.assertEquals(
                transaction.type().trim(),
                expectedTypeCode,
                "Incorrect transaction type code."
        );

        assertMoney(
                transaction.amount(),
                AMOUNT,
                "Transaction amount for " + accountId
        );

        Assert.assertNotNull(
                transaction.description(),
                "Missing transaction description."
        );

        Assert.assertTrue(
                transaction.description()
                        .toLowerCase(Locale.ROOT)
                        .contains("transfer"),
                "Expected transfer description: "
                        + transaction.description()
        );

        System.out.println(
                "Validated transaction: " + transaction
        );

        return transaction;
    }

    private void validateHistoryTransaction(
            Transaction databaseTransaction) {

        AccountActivityPage activity =
                new AccountActivityPage(driver);

        activity.open(
                databaseTransaction.accountId()
        );

        HistoryTransaction uiTransaction =
                activity.getTransaction(
                        databaseTransaction.id()
                );

        Assert.assertEquals(
                uiTransaction.id(),
                databaseTransaction.id(),
                "UI versus DB transaction ID"
        );

        Assert.assertEquals(
                uiTransaction.description(),
                databaseTransaction.description().trim(),
                "UI versus DB transaction description"
        );

        String type =
                databaseTransaction.type().trim();

        if ("1".equals(type)) {

            assertMoney(
                    uiTransaction.debit(),
                    databaseTransaction.amount(),
                    "UI debit versus DB amount"
            );

            assertMoney(
                    uiTransaction.credit(),
                    BigDecimal.ZERO,
                    "Debit row must have no credit amount"
            );

        } else if ("0".equals(type)) {

            assertMoney(
                    uiTransaction.credit(),
                    databaseTransaction.amount(),
                    "UI credit versus DB amount"
            );

            assertMoney(
                    uiTransaction.debit(),
                    BigDecimal.ZERO,
                    "Credit row must have no debit amount"
            );

        } else {

            Assert.fail(
                    "Unexpected DB transaction type: " + type
            );
        }

        System.out.println(
                "Validated UI history for account "
                        + databaseTransaction.accountId()
                        + ": " + uiTransaction
        );
    }

    private void assertMoney(
            BigDecimal actual,
            BigDecimal expected,
            String message) {

        Assert.assertNotNull(
                actual,
                message + ": missing amount"
        );

        Assert.assertEquals(
                actual.compareTo(expected),
                0,
                message
                        + "; expected " + expected
                        + ", actual " + actual
        );
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {

        DriverFactory.quitDriver();
    }
}