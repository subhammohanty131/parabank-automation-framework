package com.subham.parabank.tests;

import com.subham.parabank.api.AccountApiClient;
import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.database.AccountQueries;
import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import com.subham.parabank.pages.AccountsOverviewPage;
import com.subham.parabank.pages.LoginPage;
import com.subham.parabank.utils.ConfigReader;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;

import org.openqa.selenium.WebDriver;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.math.BigDecimal;

import org.testng.annotations.DataProvider;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class UIApiDatabaseBalanceValidationTest {

    private WebDriver driver;

    @BeforeMethod
    public void setup() {

        driver = DriverFactory.initializeDriver();

        driver.get(
                ConfigReader.get("baseUrl")
        );
    }

    @DataProvider(name = "balanceAccounts", parallel = false)
    public Object[][] balanceAccounts() {

        return new Object[][] {
                {13566},
                {13677}
        };
    }

    @Test(dataProvider = "balanceAccounts")
    public void verifyBalanceAcrossUIApiAndDatabase(int accountId) {

        AccountsOverviewPage accountsPage =
                new LoginPage(driver).login(
                        ConfigReader.get("username"),
                        ConfigReader.get("password")
                );

        Assert.assertTrue(
                accountsPage.isLoaded(),
                "Accounts Overview page did not load."
        );

        BigDecimal databaseBefore =
                AccountQueries.getAccountBalance(accountId);

        Response response =
                new AccountApiClient().getAccount(accountId);

        Reporter.log(
                "Account API HTTP status: "
                        + response.statusCode(),
                true
        );

        Assert.assertEquals(
                response.statusCode(),
                200,
                "Account API failed. Response: "
                        + response.asString()
        );

        response.then()
                .contentType(ContentType.JSON);

        JsonPath json =
                response.jsonPath().using(
                        new JsonPathConfig().numberReturnType(
                                JsonPathConfig.NumberReturnType.BIG_DECIMAL
                        )
                );

        Integer apiAccountId =
                json.getObject("id", Integer.class);

        Assert.assertNotNull(
                apiAccountId,
                "API response is missing account id."
        );

        Assert.assertEquals(
                apiAccountId.intValue(),
                accountId,
                "API returned the wrong account."
        );

        Object rawBalance = json.get("balance");

        Assert.assertTrue(
                rawBalance instanceof Number,
                "API balance is missing or is not numeric: "
                        + rawBalance
        );

        BigDecimal apiBalance =
                new BigDecimal(rawBalance.toString());

        BigDecimal uiBalance =
                accountsPage.getBalance(accountId);

        BigDecimal databaseAfter =
                AccountQueries.getAccountBalance(accountId);

        Reporter.log(
                "Account " + accountId
                        + " | UI Balance: " + uiBalance
                        + " | API Balance: " + apiBalance
                        + " | Database Balance: " + databaseAfter,
                true
        );

        assertMoneyEquals(
                databaseAfter,
                databaseBefore,
                "Database balance changed during the read-only test."
        );

        assertMoneyEquals(
                apiBalance,
                databaseAfter,
                "API and database balances do not match."
        );

        assertMoneyEquals(
                uiBalance,
                databaseAfter,
                "UI and database balances do not match."
        );
    }

    private void assertMoneyEquals(
            BigDecimal actual,
            BigDecimal expected,
            String message) {

        Assert.assertEquals(
                actual.compareTo(expected),
                0,
                message
                        + " Expected: " + expected
                        + "; actual: " + actual
        );
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown() {

        DriverFactory.quitDriver();
    }
}