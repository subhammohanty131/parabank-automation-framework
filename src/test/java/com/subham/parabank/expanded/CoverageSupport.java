package com.subham.parabank.expanded;

import com.subham.parabank.api.ApiValidation;
import com.subham.parabank.database.*;
import com.subham.parabank.database.CoverageQueries.*;
import com.subham.parabank.pages.*;
import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.utils.ConfigReader;

import io.restassured.RestAssured;
import io.restassured.config.*;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;
import org.testng.*;
import org.testng.annotations.*;

public abstract class CoverageSupport {

    protected WebDriver driver;
    protected WebDriverWait wait;

    protected record Fixture(
            String username,
            String password,
            int customerId,
            int accountId) {
    }

    @BeforeMethod
    public void startCoverageBrowser() {

        driver = DriverFactory.initializeDriver();

        wait = new WebDriverWait(
                driver, Duration.ofSeconds(12)
        );

        driver.get(ConfigReader.get("baseUrl"));
    }

    @AfterMethod(alwaysRun = true)
    public void stopCoverageBrowser() {
        DriverFactory.quitDriver();
    }

    protected String url(String path) {

        return ConfigReader.get("baseUrl")
                .replaceAll("/+$", "")
                + "/" + path;
    }

    protected void open(String path) {
        driver.get(url(path));
    }

    protected WebElement visible(By by) {

        return wait.until(
                ExpectedConditions.visibilityOfElementLocated(by)
        );
    }

    protected void fill(By by, String value) {

        WebElement e = visible(by);
        e.clear();

        if (!value.isEmpty()) {
            e.sendKeys(value);
        }
    }

    protected void click(By by) {

        wait.until(
                ExpectedConditions.elementToBeClickable(by)
        ).click();
    }

    protected void select(By by, int id) {

        String value = Integer.toString(id);

        wait.until(d ->
                new Select(d.findElement(by))
                        .getOptions()
                        .stream()
                        .anyMatch(o ->
                                value.equals(o.getAttribute("value"))
                        )
        );

        new Select(
                driver.findElement(by)
        ).selectByValue(value);

        wait.until(d ->
                value.equals(
                        new Select(d.findElement(by))
                                .getFirstSelectedOption()
                                .getAttribute("value")
                )
        );
    }

    protected Response request(
            String method,
            String path,
            Map<String, ?> query) {

        return RestAssured.given()
                .config(
                        RestAssuredConfig.config().httpClient(
                                HttpClientConfig.httpClientConfig()
                                        .setParam(
                                                "http.connection.timeout",
                                                5000
                                        )
                                        .setParam(
                                                "http.socket.timeout",
                                                15000
                                        )
                        )
                )
                .accept(ContentType.JSON)
                .redirects().follow(false)
                .queryParams(query)
                .when()
                .request(
                        method,
                        url("services/bank/" + path)
                );
    }

    protected JsonPath json(Response r) {

        Assert.assertEquals(
                r.statusCode(),
                200,
                "API request failed (HTTP "
                        + r.statusCode() + ")."
        );

        r.then().contentType(ContentType.JSON);

        return r.jsonPath().using(
                new JsonPathConfig().numberReturnType(
                        JsonPathConfig.NumberReturnType.BIG_DECIMAL
                )
        );
    }

    protected Map<String, String> registration(
            String username) {

        Map<String, String> fields = new LinkedHashMap<>();

        fields.put("customer.firstName", "Automation");
        fields.put("customer.lastName", "Tester");
        fields.put("customer.address.street", "10 Test Road");
        fields.put("customer.address.city", "Test City");
        fields.put("customer.address.state", "Test State");
        fields.put("customer.address.zipCode", "12345");
        fields.put("customer.phoneNumber", "5551234567");
        fields.put("customer.ssn", "111223333");
        fields.put("customer.username", username);
        fields.put("customer.password", "TestOnly17!");
        fields.put("repeatedPassword", "TestOnly17!");

        return fields;
    }

    protected String newUsername() {

        return "qa" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);
    }

    protected void register(Map<String, String> fields) {

        open("register.htm");

        fields.forEach((name, value) ->
                fill(By.name(name), value)
        );

        click(By.cssSelector("input[value='Register']"));
    }

    protected Fixture fixture() {

        String username = newUsername();

        Map<String, String> fields =
                registration(username);

        register(fields);

        visible(By.xpath(
                "//*[@id='rightPanel']//h1[contains(.,'"
                        + username + "')]"
        ));

        Integer customerId =
                CoverageQueries.customerId(username);

        Assert.assertNotNull(
                customerId,
                "Registered customer not found in DB."
        );

        Set<Integer> ids =
                AccountCreationQueries.getAccountIds(customerId);

        Assert.assertEquals(
                ids.size(),
                1,
                "Registration should create one account."
        );

        int account = ids.iterator().next();

        var db = AccountCreationQueries.getAccount(account);

        Assert.assertEquals(
                db.type(),
                0,
                "Registration account should be CHECKING."
        );

        money(
                db.balance(),
                new BigDecimal(
                        CoverageQueries.parameter("initialBalance")
                ),
                "Registration balance"
        );

        Map<String, String> expected = new LinkedHashMap<>();

        fields.forEach((k, v) -> {

            if (!k.contains("username")
                    && !k.contains("password")
                    && !k.equals("repeatedPassword")) {

                expected.put(
                        k.substring("customer.".length()),
                        v
                );
            }
        });

        Assert.assertEquals(
                CoverageQueries.profile(customerId),
                expected,
                "Registration profile versus DB."
        );

        checkProfile(customerId, expected);
        overview();
        checkBalances(customerId);

        Reporter.log(
                "Created isolated test customer "
                        + username + ", id=" + customerId,
                true
        );

        return new Fixture(
                username,
                fields.get("customer.password"),
                customerId,
                account
        );
    }

    protected void overview() {

        open("overview.htm");

        Assert.assertTrue(
                new AccountsOverviewPage(driver).isLoaded(),
                "Overview did not load."
        );
    }

    protected void money(
            BigDecimal actual,
            BigDecimal expected,
            String label) {

        Assert.assertNotNull(
                actual, label + ": missing value"
        );

        Assert.assertEquals(
                actual.compareTo(expected),
                0,
                label + "; actual=" + actual
                        + ", expected=" + expected
        );
    }

    protected void checkProfile(
            int id, Map<String, String> expected) {

        JsonPath j = json(
                request(
                        "GET",
                        "customers/" + id,
                        Map.of()
                )
        );

        Assert.assertEquals(j.getInt("id"), id);

        expected.forEach((field, value) ->
                Assert.assertEquals(
                        j.getString(field),
                        value,
                        "API profile " + field
                )
        );
    }

    protected void checkBalances(int customerId) {

        overview();

        AccountsOverviewPage page =
                new AccountsOverviewPage(driver);

        for (var a :
                CoverageQueries.state(customerId)
                        .accounts().values()) {

            money(
                    page.getBalance(a.id()),
                    a.balance(),
                    "UI/DB account " + a.id()
            );

            ApiValidation.assertAccount(a);
        }
    }

    protected void unchanged(int id, State before) {

        long start = System.nanoTime();

        wait.pollingEvery(
                Duration.ofMillis(250)
        ).until(d -> {

            Assert.assertEquals(
                    CoverageQueries.state(id),
                    before,
                    "Unexpected account or transaction mutation."
            );

            return System.nanoTime() - start
                    >= Duration.ofSeconds(2).toNanos();
        });

        for (var a : before.accounts().values()) {

            ApiValidation.assertAccount(a);

            Set<Integer> expected = new HashSet<>();

            before.transactions().stream()
                    .filter(t -> t.accountId() == a.id())
                    .forEach(t -> expected.add(t.id()));

            Assert.assertEquals(
                    ApiValidation.transactionIds(a.id()),
                    expected,
                    "API ledger changed."
            );
        }
    }

    protected void checkTransaction(Tx tx) {

        var db = new TransactionQueries.Transaction(
                tx.id(),
                tx.accountId(),
                tx.type(),
                tx.amount(),
                tx.description()
        );

        ApiValidation.assertTransaction(db);

        AccountActivityPage activity =
                new AccountActivityPage(driver);

        activity.open(tx.accountId());

        var row = activity.getTransaction(tx.id());

        Assert.assertEquals(row.id(), tx.id());

        Assert.assertEquals(
                row.description(),
                tx.description()
        );

        money(
                row.debit(),
                "1".equals(tx.type())
                        ? tx.amount() : BigDecimal.ZERO,
                "History debit"
        );

        money(
                row.credit(),
                "0".equals(tx.type())
                        ? tx.amount() : BigDecimal.ZERO,
                "History credit"
        );
    }

    protected List<Tx> addedTransactions(
            int customerId, State before) {

        Set<Integer> old = new HashSet<>();
        before.transactions().forEach(t -> old.add(t.id()));

        return CoverageQueries.transactions(customerId)
                .stream()
                .filter(t -> !old.contains(t.id()))
                .toList();
    }

    protected void logout() {

        click(By.linkText("Log Out"));
        visible(By.name("username"));
    }

    protected void openProfile(Fixture f) {

        open("updateprofile.htm");

        wait.until(d ->
                CoverageQueries.profile(f.customerId())
                        .get("firstName")
                        .equals(
                                d.findElement(
                                        By.id("customer.firstName")
                                ).getAttribute("value")
                        )
        );
    }

    protected void visibleErrors() {

        wait.until(d ->
                d.findElements(
                        By.cssSelector("#rightPanel .error")
                ).stream().anyMatch(e ->
                        e.isDisplayed()
                                && !e.getText().isBlank()
                )
        );
    }
}