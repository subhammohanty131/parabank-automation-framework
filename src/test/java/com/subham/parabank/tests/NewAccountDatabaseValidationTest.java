package com.subham.parabank.tests;

import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.database.AccountCreationQueries;
import com.subham.parabank.database.AccountCreationQueries.Account;
import com.subham.parabank.pages.AccountsOverviewPage;
import com.subham.parabank.pages.LoginPage;
import com.subham.parabank.pages.OpenNewAccountPage;
import com.subham.parabank.utils.ConfigReader;

import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import org.testng.annotations.Listeners;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

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
public class NewAccountDatabaseValidationTest {

    private WebDriver driver;

    private static final int FUNDING_ACCOUNT = 13566;

    // Standard ParaBank database mapping.
    private static final int CHECKING_TYPE = 0;

    @BeforeMethod
    public void setup() {

        driver = DriverFactory.initializeDriver();

        driver.get(
                ConfigReader.get("baseUrl")
        );
    }

    @Test
    public void verifyNewCheckingAccountAgainstDatabase() {

        AccountsOverviewPage overview =
                new LoginPage(driver).login(
                        ConfigReader.get("username"),
                        ConfigReader.get("password")
                );

        Assert.assertTrue(
                overview.isLoaded(),
                "Accounts Overview did not load."
        );

        Account fundingBefore =
                AccountCreationQueries.getAccount(
                        FUNDING_ACCOUNT
                );

        assertMoney(
                overview.getBalance(FUNDING_ACCOUNT),
                fundingBefore.balance(),
                "Funding UI versus DB before account creation"
        );
        
        ApiValidation.assertAccount(fundingBefore);
        
        // Record all existing accounts for this customer.
        Set<Integer> idsBefore =
                AccountCreationQueries.getAccountIds(
                        fundingBefore.customerId()
                );

        OpenNewAccountPage newAccountPage =
                new OpenNewAccountPage(driver);

        newAccountPage.open();

        // Read the required opening deposit from the UI.
        BigDecimal deposit =
                newAccountPage.getMinimumDeposit();

        Assert.assertTrue(
                deposit.signum() > 0,
                "Expected a positive minimum deposit."
        );

        Assert.assertTrue(
                fundingBefore.balance().compareTo(deposit) >= 0,
                "Insufficient funding balance: "
                        + fundingBefore.balance()
                        + "; required: " + deposit
        );

        int newAccountId =
                newAccountPage.openCheckingAccount(
                        FUNDING_ACCOUNT
                );

        Assert.assertFalse(
                idsBefore.contains(newAccountId),
                "UI returned an existing account ID."
        );

        // Wait for the new account to appear in the database.
        new WebDriverWait(
                driver,
                Duration.ofSeconds(10)
        ).until(d ->
                AccountCreationQueries.getAccountIds(
                        fundingBefore.customerId()
                ).contains(newAccountId)
        );

        Set<Integer> idsAfter =
                AccountCreationQueries.getAccountIds(
                        fundingBefore.customerId()
                );

        Set<Integer> addedIds =
                new HashSet<>(idsAfter);

        addedIds.removeAll(idsBefore);

        Assert.assertEquals(
                addedIds,
                Set.of(newAccountId),
                "Expected exactly one new account."
        );

        Assert.assertTrue(
                idsAfter.containsAll(idsBefore),
                "An existing account disappeared."
        );

        Account newAccount =
                AccountCreationQueries.getAccount(
                        newAccountId
                );

        Account fundingAfter =
                AccountCreationQueries.getAccount(
                        FUNDING_ACCOUNT
                );

        Assert.assertEquals(
                newAccount.id(),
                newAccountId,
                "UI versus DB account ID"
        );

        Assert.assertEquals(
                newAccount.customerId(),
                fundingBefore.customerId(),
                "Incorrect account owner"
        );

        Assert.assertEquals(
                newAccount.type(),
                CHECKING_TYPE,
                "Expected a CHECKING account"
        );

        assertMoney(
                newAccount.balance(),
                deposit,
                "New account opening balance"
        );

        assertMoney(
                fundingAfter.balance(),
                fundingBefore.balance().subtract(deposit),
                "Funding account deduction"
        );

        assertMoney(
                fundingAfter.balance().add(newAccount.balance()),
                fundingBefore.balance(),
                "Combined funds must remain unchanged"
        );
        
        ApiValidation.assertAccount(newAccount);
        ApiValidation.assertAccount(fundingAfter);
        
        AccountsOverviewPage updated =
                newAccountPage.openAccountsOverview();

        assertMoney(
                updated.getBalance(newAccountId),
                newAccount.balance(),
                "New account UI versus DB"
        );

        assertMoney(
                updated.getBalance(FUNDING_ACCOUNT),
                fundingAfter.balance(),
                "Funding UI versus DB"
        );

        System.out.println(
                "Validated new account: " + newAccount
        );

        System.out.println(
                "Opening deposit: " + deposit
                        + "; funding balance: "
                        + fundingBefore.balance()
                        + " -> "
                        + fundingAfter.balance()
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