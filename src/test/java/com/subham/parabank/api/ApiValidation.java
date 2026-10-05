package com.subham.parabank.api;

import com.subham.parabank.database.AccountCreationQueries.Account;
import com.subham.parabank.database.TransactionQueries.Transaction;
import com.subham.parabank.utils.ConfigReader;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.path.json.config.JsonPathConfig;
import io.restassured.response.Response;

import org.testng.Assert;
import org.testng.Reporter;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class ApiValidation {

    private ApiValidation() {
    }

    private static JsonPath readJson(
            Response response, String label) {

        Reporter.log(
                label + " | HTTP " + response.statusCode(),
                true
        );

        Assert.assertEquals(
                response.statusCode(),
                200,
                label + " failed: " + response.asString()
        );

        response.then().contentType(ContentType.JSON);

        return response.jsonPath().using(
                new JsonPathConfig().numberReturnType(
                        JsonPathConfig.NumberReturnType.BIG_DECIMAL
                )
        );
    }

    private static JsonPath get(String path) {

        String baseUrl = ConfigReader.get("baseUrl");

        Assert.assertNotNull(
                baseUrl, "Missing baseUrl configuration."
        );

        Assert.assertFalse(
                baseUrl.isBlank(), "Empty baseUrl configuration."
        );

        Response response = RestAssured.given()
                .config(
                        RestAssuredConfig.config().httpClient(
                                HttpClientConfig.httpClientConfig()
                                        .setParam(
                                                "http.connection.timeout",
                                                5000
                                        )
                                        .setParam(
                                                "http.socket.timeout",
                                                10000
                                        )
                        )
                )
                .accept(ContentType.JSON)
                .redirects().follow(false)
                .when()
                .get(
                        baseUrl.replaceAll("/+$", "")
                                + "/services/bank" + path
                );

        return readJson(response, "GET " + path);
    }

    private static JsonPath account(int accountId) {

        JsonPath json = readJson(
                new AccountApiClient().getAccount(accountId),
                "GET account " + accountId
        );

        Integer actualId =
                json.getObject("id", Integer.class);

        Assert.assertNotNull(
                actualId, "Missing API account id."
        );

        Assert.assertEquals(
                actualId.intValue(),
                accountId,
                "API account id mismatch."
        );

        return json;
    }

    private static BigDecimal money(
            JsonPath json, String field) {

        Object value = json.get(field);

        Assert.assertTrue(
                value instanceof Number,
                "Missing or nonnumeric API "
                        + field + ": " + value
        );

        return new BigDecimal(value.toString());
    }

    private static void assertMoney(
            BigDecimal actual,
            BigDecimal expected,
            String label) {

        Assert.assertNotNull(
                expected, label + ": missing expected amount."
        );

        Assert.assertEquals(
                actual.compareTo(expected),
                0,
                label + "; expected " + expected
                        + ", actual " + actual
        );
    }

    public static void assertBalance(
            int accountId, BigDecimal databaseBalance) {

        BigDecimal apiBalance =
                money(account(accountId), "balance");

        assertMoney(
                apiBalance,
                databaseBalance,
                "API versus DB balance for " + accountId
        );

        Reporter.log(
                "Validated API balance: account=" + accountId
                        + ", API=" + apiBalance
                        + ", DB=" + databaseBalance,
                true
        );
    }

    public static void assertAccount(Account databaseAccount) {

        JsonPath json = account(databaseAccount.id());

        Integer customerId =
                json.getObject("customerId", Integer.class);

        Assert.assertNotNull(
                customerId, "Missing API customer id."
        );

        Assert.assertEquals(
                customerId.intValue(),
                databaseAccount.customerId(),
                "API versus DB account owner."
        );

        String expectedType = switch (databaseAccount.type()) {
            case 0 -> "CHECKING";
            case 1 -> "SAVINGS";
            case 2 -> "LOAN";
            default -> throw new AssertionError(
                    "Unknown DB account type: "
                            + databaseAccount.type()
            );
        };

        String actualType = json.getString("type");

        Assert.assertNotNull(
                actualType, "Missing API account type."
        );

        Assert.assertEquals(
                actualType.toUpperCase(Locale.ROOT),
                expectedType,
                "API versus DB account type."
        );

        assertMoney(
                money(json, "balance"),
                databaseAccount.balance(),
                "API versus DB account balance"
        );

        Reporter.log(
                "Validated API account: " + databaseAccount.id()
                        + ", owner=" + customerId
                        + ", type=" + actualType
                        + ", balance=" + databaseAccount.balance(),
                true
        );
    }

    public static void assertTransaction(
            Transaction databaseTransaction) {

        JsonPath json = get(
                "/transactions/" + databaseTransaction.id()
        );

        Integer id = json.getObject("id", Integer.class);

        Integer accountId =
                json.getObject("accountId", Integer.class);

        Assert.assertNotNull(
                id, "Missing API transaction id."
        );

        Assert.assertNotNull(
                accountId, "Missing API transaction account id."
        );

        Assert.assertEquals(
                id.intValue(),
                databaseTransaction.id(),
                "API versus DB transaction id."
        );

        Assert.assertEquals(
                accountId.intValue(),
                databaseTransaction.accountId(),
                "API versus DB transaction account."
        );

        String expectedType =
                switch (databaseTransaction.type().trim()) {
                    case "1" -> "DEBIT";
                    case "0" -> "CREDIT";
                    default -> throw new AssertionError(
                            "Unknown DB transaction type: "
                                    + databaseTransaction.type()
                    );
                };

        String actualType = json.getString("type");

        Assert.assertNotNull(
                actualType, "Missing API transaction type."
        );

        Assert.assertEquals(
                actualType.toUpperCase(Locale.ROOT),
                expectedType,
                "API versus DB transaction type."
        );

        assertMoney(
                money(json, "amount"),
                databaseTransaction.amount(),
                "API versus DB transaction amount"
        );

        String description = json.getString("description");

        Assert.assertNotNull(
                description, "Missing API transaction description."
        );

        Assert.assertEquals(
                description.trim(),
                databaseTransaction.description().trim(),
                "API versus DB transaction description."
        );

        Reporter.log(
                "Validated API transaction: id=" + id
                        + ", account=" + accountId
                        + ", type=" + actualType
                        + ", amount=" + money(json, "amount")
                        + ", description=" + description,
                true
        );
    }

    public static Set<Integer> transactionIds(int accountId) {

        JsonPath json = get(
                "/accounts/" + accountId + "/transactions"
        );

        List<Integer> ids =
                json.getList("id", Integer.class);

        List<Integer> owners =
                json.getList("accountId", Integer.class);

        Assert.assertNotNull(
                ids, "Missing API transaction list."
        );

        Assert.assertNotNull(
                owners, "Missing API transaction account list."
        );

        Assert.assertEquals(
                owners.size(),
                ids.size(),
                "Incomplete API transaction list."
        );

        Assert.assertFalse(
                ids.contains(null),
                "API transaction is missing an id."
        );

        for (Integer owner : owners) {
            Assert.assertEquals(
                    owner,
                    Integer.valueOf(accountId),
                    "API list contains another account's transaction."
            );
        }

        Set<Integer> uniqueIds = new HashSet<>(ids);

        Assert.assertEquals(
                uniqueIds.size(),
                ids.size(),
                "Duplicate API transaction ids."
        );

        return uniqueIds;
    }

    public static void assertTransactionsUnchanged(
            int accountId, Set<Integer> before) {

        Assert.assertEquals(
                transactionIds(accountId),
                before,
                "API transaction ids changed after invalid transfer for "
                        + accountId
        );

        Reporter.log(
                "Validated API ledger unchanged: account=" + accountId,
                true
        );
    }
}