package com.subham.parabank.contracts;

import com.subham.parabank.expanded.CoverageSupport;
import com.subham.parabank.database.CoverageQueries;
import com.subham.parabank.listeners.*;
import com.subham.parabank.utils.ConfigReader;

import java.util.*;

import org.testng.*;
import org.testng.annotations.*;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class ApiErrorContractTest extends CoverageSupport {

    @DataProvider(name = "apiErrors", parallel = false)
    public Object[][] apiErrors() {

        return new Object[][] {
                {"GET", "accounts/not-a-number", 400},
                {"GET", "not-a-real-endpoint", 404},
                {"GET", "deposit", 405},
                {"GET", "missing-account", 404}
        };
    }

    @Test(dataProvider = "apiErrors")
    public void apiReturnsExpectedClientErrorWithoutMutation(
            String method, String path, int expectedStatus) {

        Integer id = CoverageQueries.customerId(
                ConfigReader.get("username")
        );

        Assert.assertNotNull(id);

        var before = CoverageQueries.state(id);

        if (path.equals("missing-account")) {

            int max = ((Number) CoverageQueries.query(
                    "SELECT MAX(ID) AS N FROM ACCOUNT"
            ).get(0).get("N")).intValue();

            path = "accounts/"
                    + Math.addExact(max, 1000000);
        }

        var response = request(
                method, path, Map.of()
        );

        Reporter.log(
                "API error contract: " + method + " " + path
                        + " | expected=" + expectedStatus
                        + " | actual=" + response.statusCode(),
                true
        );

        if (path.equals("accounts/not-a-number")) {

            Assert.assertTrue(
                    response.statusCode() == 400
                            || response.statusCode() == 404,
                    "Expected path conversion client error "
                            + "(400 or 404)."
            );

        } else {

            Assert.assertEquals(
                    response.statusCode(),
                    expectedStatus,
                    "Unexpected API error mapping."
            );
        }

        unchanged(id, before);
    }
}