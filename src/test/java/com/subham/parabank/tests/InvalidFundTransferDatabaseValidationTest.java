package com.subham.parabank.tests;

import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.database.AccountQueries;
import com.subham.parabank.database.TransactionQueries;
import com.subham.parabank.pages.AccountsOverviewPage;
import com.subham.parabank.pages.LoginPage;
import com.subham.parabank.pages.TransferFundsPage;
import com.subham.parabank.utils.ConfigReader;

import java.math.BigDecimal;
import java.time.Duration;

import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import org.testng.annotations.Listeners;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.subham.parabank.api.ApiValidation;
import java.util.Set;

import com.subham.parabank.utils.TestEvidence;

@Listeners({
    FailureScreenshotListener.class,
    HtmlReportListener.class
})
public class InvalidFundTransferDatabaseValidationTest {

    private WebDriver driver;

    private static final int SOURCE = 13566;
    private static final int DESTINATION = 13677;

    private static final String OBSERVED_ERROR =
            "An internal error has occurred and has been logged.";

    @BeforeMethod
    public void setup() {

        driver = DriverFactory.initializeDriver();

        driver.get(
                ConfigReader.get("baseUrl")
        );
    }

    @DataProvider(
            name = "invalidAmounts",
            parallel = false
    )
    public Object[][] invalidAmounts() {

        return new Object[][] {
                {"Blank amount", ""},
                {"Nonnumeric amount", "abc"}
        };
    }

    @Test(dataProvider = "invalidAmounts")
    public void verifyInvalidAmountDoesNotChangeFunds(
            String caseName,
            String input) {

        AccountsOverviewPage overview =
                new LoginPage(driver).login(
                        ConfigReader.get("username"),
                        ConfigReader.get("password")
                );

        Assert.assertTrue(
                overview.isLoaded(),
                "Accounts Overview did not load."
        );

        BigDecimal sourceBefore =
                AccountQueries.getAccountBalance(SOURCE);

        BigDecimal destinationBefore =
                AccountQueries.getAccountBalance(DESTINATION);

        assertMoney(
                overview.getBalance(SOURCE),
                sourceBefore,
                "Initial source UI versus DB"
        );

        assertMoney(
                overview.getBalance(DESTINATION),
                destinationBefore,
                "Initial destination UI versus DB"
        );

        int sourcePreviousId =
                TransactionQueries.getLatestTransactionId(SOURCE);

        int destinationPreviousId =
                TransactionQueries.getLatestTransactionId(DESTINATION);
        
        ApiValidation.assertBalance(SOURCE, sourceBefore);
        ApiValidation.assertBalance(DESTINATION, destinationBefore);

        Set<Integer> sourceApiIdsBefore =
                ApiValidation.transactionIds(SOURCE);

        Set<Integer> destinationApiIdsBefore =
                ApiValidation.transactionIds(DESTINATION);
        
        TransferFundsPage transferPage =
                new TransferFundsPage(driver);

        transferPage.open();

        transferPage.submitInvalidAmount(
                SOURCE,
                DESTINATION,
                input
        );

        String errorText =
                transferPage.getErrorText();

        System.out.println(
                caseName + " response: " + errorText
        );

        // Characterize the currently observed response.
        // This does not establish correct field validation.
        Assert.assertTrue(
                errorText.contains(OBSERVED_ERROR),
                "Unexpected error response: " + errorText
        );

        Assert.assertFalse(
                errorText.contains("Transfer Complete!"),
                "Invalid transfer reported success."
        );

        // Sample database state for two seconds after the error.
        long startedAt = System.nanoTime();

        new WebDriverWait(
                driver,
                Duration.ofSeconds(5)
        ).pollingEvery(
                Duration.ofMillis(250)
        ).until(d -> {

            assertUnchanged(
                    sourceBefore,
                    destinationBefore,
                    sourcePreviousId,
                    destinationPreviousId
            );

            return System.nanoTime() - startedAt
                    >= Duration.ofSeconds(2).toNanos();
        });
        
        TestEvidence.captureKnownDefect(
                driver,
                "PB-001",
                caseName
        );

        AccountsOverviewPage updated =
                transferPage.openAccountsOverview();

        assertMoney(
                updated.getBalance(SOURCE),
                sourceBefore,
                "Source UI after invalid transfer"
        );

        assertMoney(
                updated.getBalance(DESTINATION),
                destinationBefore,
                "Destination UI after invalid transfer"
        );

        // Check once more after revisiting the UI.
        assertUnchanged(
                sourceBefore,
                destinationBefore,
                sourcePreviousId,
                destinationPreviousId
        );
        
        ApiValidation.assertBalance(SOURCE, sourceBefore);
        ApiValidation.assertBalance(DESTINATION, destinationBefore);

        ApiValidation.assertTransactionsUnchanged(
                SOURCE, sourceApiIdsBefore
        );

        ApiValidation.assertTransactionsUnchanged(
                DESTINATION, destinationApiIdsBefore
        );

        assertUnchanged(
                sourceBefore,
                destinationBefore,
                sourcePreviousId,
                destinationPreviousId
        );
        Reporter.log(
                "Known defect PB-001: " + caseName
                        + " displays an internal error instead of "
                        + "field validation. This test verifies the "
                        + "observed error and data integrity only.",
                true
        );

        System.out.println(
                "Validated " + caseName
                        + ": balances unchanged; no new transactions."
        );
    }

    private void assertUnchanged(
            BigDecimal sourceBefore,
            BigDecimal destinationBefore,
            int sourcePreviousId,
            int destinationPreviousId) {

        assertMoney(
                AccountQueries.getAccountBalance(SOURCE),
                sourceBefore,
                "Source DB balance after invalid transfer"
        );

        assertMoney(
                AccountQueries.getAccountBalance(DESTINATION),
                destinationBefore,
                "Destination DB balance after invalid transfer"
        );

        Assert.assertTrue(
                TransactionQueries.getTransactionsAfter(
                        SOURCE,
                        sourcePreviousId
                ).isEmpty(),
                "Invalid transfer created a source transaction."
        );

        Assert.assertTrue(
                TransactionQueries.getTransactionsAfter(
                        DESTINATION,
                        destinationPreviousId
                ).isEmpty(),
                "Invalid transfer created a destination transaction."
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