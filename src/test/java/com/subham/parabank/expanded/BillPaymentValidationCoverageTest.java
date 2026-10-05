package com.subham.parabank.expanded;

import com.subham.parabank.database.CoverageQueries;
import com.subham.parabank.listeners.*;

import java.util.*;

import org.openqa.selenium.By;
import org.testng.*;
import org.testng.annotations.*;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class BillPaymentValidationCoverageTest
        extends CoverageSupport {

    @DataProvider(name = "billErrors", parallel = false)
    public Object[][] billErrors() {

        return new Object[][] {
                {"payee.name", "", "validationModel-name"},
                {
                        "payee.address.street", "",
                        "validationModel-address"
                },
                {
                        "payee.address.city", "",
                        "validationModel-city"
                },
                {
                        "payee.address.state", "",
                        "validationModel-state"
                },
                {
                        "payee.address.zipCode", "",
                        "validationModel-zipCode"
                },
                {
                        "payee.phoneNumber", "",
                        "validationModel-phoneNumber"
                },
                {
                        "payee.accountNumber", "",
                        "validationModel-account-empty"
                },
                {
                        "payee.accountNumber", "abc",
                        "validationModel-account-invalid"
                },
                {
                        "verifyAccount", "",
                        "validationModel-verifyAccount-empty"
                },
                {
                        "verifyAccount", "abc",
                        "validationModel-verifyAccount-invalid"
                },
                {
                        "verifyAccount", "987655",
                        "validationModel-verifyAccount-mismatch"
                },
                {
                        "amount", "",
                        "validationModel-amount-empty"
                },
                {
                        "amount", "abc",
                        "validationModel-amount-invalid"
                }
        };
    }

    @Test(dataProvider = "billErrors")
    public void invalidBillShowsFieldErrorWithoutFinancialChanges(
            String field, String input, String errorId) {

        Fixture f = fixture();

        var before = CoverageQueries.state(f.customerId());

        open("billpay.htm");

        Map<String, String> values = new LinkedHashMap<>();

        values.put("payee.name", "Automation Utilities");
        values.put("payee.address.street", "10 Test Street");
        values.put("payee.address.city", "Test City");
        values.put("payee.address.state", "Test State");
        values.put("payee.address.zipCode", "12345");
        values.put("payee.phoneNumber", "5551234567");
        values.put("payee.accountNumber", "987654");
        values.put("verifyAccount", "987654");
        values.put("amount", "25.00");

        values.put(field, input);

        values.forEach((k, v) -> fill(By.name(k), v));

        select(By.name("fromAccountId"), f.accountId());

        click(By.cssSelector("input[value='Send Payment']"));

        Assert.assertFalse(
                visible(By.id(errorId)).getText().isBlank()
        );

        Assert.assertFalse(
                driver.findElement(
                        By.id("billpayResult")
                ).isDisplayed(),
                "Invalid bill reported success."
        );

        unchanged(f.customerId(), before);
        checkBalances(f.customerId());

        Reporter.log(
                "Validated bill error: " + errorId,
                true
        );
    }
}