package com.subham.parabank.api;

import com.subham.parabank.utils.ConfigReader;
import io.restassured.RestAssured;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

public class AccountApiClient {

    public Response getAccount(int accountId) {

        String baseUrl = ConfigReader.get("baseUrl");

        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException(
                    "baseUrl is missing from config.properties."
            );
        }

        String endpoint =
                baseUrl.replaceAll("/+$", "")
                        + "/services/bank/accounts/"
                        + accountId;

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
                                                10000
                                        )
                        )
                )
                .accept(ContentType.JSON)
                .redirects().follow(false)
                .when()
                .get(endpoint);
    }
}