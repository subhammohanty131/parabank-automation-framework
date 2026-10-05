package com.subham.parabank.support;

import com.subham.parabank.api.ApiValidation;
import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.database.AccountQueries;
import com.subham.parabank.database.TransactionQueries;
import com.subham.parabank.database.TransactionQueries.Transaction;
import com.subham.parabank.pages.AccountActivityPage;
import com.subham.parabank.pages.AccountActivityPage.HistoryTransaction;
import com.subham.parabank.pages.AccountsOverviewPage;
import com.subham.parabank.pages.LoginPage;
import com.subham.parabank.utils.ConfigReader;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

public abstract class AdditionalCoverageBase {

    protected WebDriver driver;
    protected WebDriverWait wait;

    protected static final int PRIMARY = 13566;
    protected static final int SECONDARY = 13677;

    @BeforeMethod
    public void setupAdditionalCoverage() {

        driver = DriverFactory.initializeDriver();

        wait = new WebDriverWait(
                driver, Duration.ofSeconds(10)
        );

        driver.get(ConfigReader.get("baseUrl"));

        AccountsOverviewPage overview =
                new LoginPage(driver).login(
                        ConfigReader.get("username"),
                        ConfigReader.get("password")
                );

        Assert.assertTrue(
                overview.isLoaded(),
                "Accounts Overview did not load."
        );
    }

    protected void money(
            BigDecimal actual,
            BigDecimal expected,
            String label) {

        Assert.assertNotNull(
                actual, label + ": missing amount."
        );

        Assert.assertEquals(
                actual.compareTo(expected),
                0,
                label + "; expected=" + expected
                        + ", actual=" + actual
        );
    }

    protected BigDecimal startingBalance(int accountId) {

        BigDecimal db =
                AccountQueries.getAccountBalance(accountId);

        money(
                new AccountsOverviewPage(driver)
                        .getBalance(accountId),
                db,
                "Starting UI versus DB"
        );

        ApiValidation.assertBalance(accountId, db);

        return db;
    }

    protected void balances(
            Map<Integer, BigDecimal> expected) {

        wait.until(d ->
                expected.entrySet().stream().allMatch(e ->
                        AccountQueries.getAccountBalance(
                                e.getKey()
                        ).compareTo(e.getValue()) == 0
                )
        );

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.linkText("Accounts Overview")
                )
        ).click();

        AccountsOverviewPage overview =
                new AccountsOverviewPage(driver);

        Assert.assertTrue(
                overview.isLoaded(),
                "Updated Accounts Overview did not load."
        );

        for (Map.Entry<Integer, BigDecimal> entry
                : expected.entrySet()) {

            int id = entry.getKey();

            BigDecimal db =
                    AccountQueries.getAccountBalance(id);

            money(
                    db,
                    entry.getValue(),
                    "Expected DB balance for " + id
            );

            money(
                    overview.getBalance(id),
                    db,
                    "UI versus DB for " + id
            );

            ApiValidation.assertBalance(id, db);

            Reporter.log(
                    "Validated UI/API/DB balance: account="
                            + id + ", balance=" + db,
                    true
            );
        }
    }

    protected Transaction transaction(
            int accountId,
            int previousId,
            String type,
            BigDecimal amount) {

        wait.until(d ->
                !TransactionQueries.getTransactionsAfter(
                        accountId, previousId
                ).isEmpty()
        );

        List<Transaction> rows =
                TransactionQueries.getTransactionsAfter(
                        accountId, previousId
                );

        Assert.assertEquals(
                rows.size(),
                1,
                "Expected exactly one new transaction: " + rows
        );

        Transaction row = rows.get(0);

        Assert.assertTrue(
                row.id() > previousId,
                "Transaction must be new."
        );

        Assert.assertEquals(
                row.accountId(),
                accountId,
                "Wrong transaction account."
        );

        Assert.assertNotNull(
                row.type(), "Missing DB transaction type."
        );

        Assert.assertEquals(
                row.type().trim(),
                type,
                "Wrong DB transaction type."
        );

        money(row.amount(), amount, "Transaction amount");

        Assert.assertNotNull(
                row.description(),
                "Missing transaction description."
        );

        Assert.assertFalse(
                row.description().isBlank(),
                "Empty transaction description."
        );

        ApiValidation.assertTransaction(row);

        AccountActivityPage activity =
                new AccountActivityPage(driver);

        activity.open(accountId);

        HistoryTransaction ui =
                activity.getTransaction(row.id());

        Assert.assertEquals(
                ui.id(), row.id(), "UI transaction id."
        );

        Assert.assertEquals(
                ui.description(),
                row.description().trim(),
                "UI transaction description."
        );

        money(
                ui.debit(),
                "1".equals(type) ? amount : BigDecimal.ZERO,
                "UI debit"
        );

        money(
                ui.credit(),
                "0".equals(type) ? amount : BigDecimal.ZERO,
                "UI credit"
        );

        Reporter.log(
                "Validated JDBC/API/UI history: " + row,
                true
        );

        return row;
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownAdditionalCoverage() {
        DriverFactory.quitDriver();
    }
}