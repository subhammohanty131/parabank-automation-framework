package com.subham.parabank.api;

import com.subham.parabank.utils.ConfigReader;

import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

import java.math.BigDecimal;

import org.testng.Assert;
import org.testng.Reporter;

public class BankActionsApi {

    public void deposit(int accountId, BigDecimal amount) {
        post("/deposit", accountId, amount);
    }

    public void withdraw(int accountId, BigDecimal amount) {
        post("/withdraw", accountId, amount);
    }

    private void post(
            String path, int accountId, BigDecimal amount) {

        if (amount == null
                || amount.signum() <= 0
                || amount.stripTrailingZeros().scale() > 2) {

            throw new IllegalArgumentException(
                    "Use a positive amount with at most two decimal places."
            );
        }

        String baseUrl = ConfigReader.get("baseUrl");

        Assert.assertNotNull(baseUrl, "Missing baseUrl.");
        Assert.assertFalse(baseUrl.isBlank(), "Empty baseUrl.");

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
                .queryParam("accountId", accountId)
                .queryParam("amount", amount.toPlainString())
                .when()
                .post(
                        baseUrl.replaceAll("/+$", "")
                                + "/services/bank" + path
                );

        Reporter.log(
                "POST " + path
                        + " | account=" + accountId
                        + " | amount=" + amount
                        + " | HTTP " + response.statusCode()
                        + " | response=" + response.asString(),
                true
        );

        Assert.assertEquals(
                response.statusCode(),
                200,
                "Bank operation failed: " + response.asString()
        );
    }
}