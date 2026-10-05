package com.subham.parabank.contracts;

import com.subham.parabank.expanded.CoverageSupport;
import com.subham.parabank.database.CoverageQueries;
import com.subham.parabank.api.BankActionsApi;
import com.subham.parabank.listeners.*;
import com.subham.parabank.pages.TransferFundsPage;

import java.math.BigDecimal;
import java.util.*;

import org.openqa.selenium.By;
import org.testng.*;
import org.testng.annotations.*;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class BankingRejectionContractTest
        extends CoverageSupport {

    @DataProvider(name = "transferRejections", parallel = false)
    public Object[][] transferRejections() {

        return new Object[][] {
                {"zero"},
                {"negative"},
                {"overdraft"},
                {"same account"}
        };
    }

    @Test(dataProvider = "transferRejections")
    public void invalidTransferMustBeRejectedWithoutMutation(
            String scenario) {

        Fixture f = fixture();

        int destination = json(request(
                "POST",
                "createAccount",
                Map.of(
                        "customerId", f.customerId(),
                        "newAccountType", 0,
                        "fromAccountId", f.accountId()
                )
        )).getInt("id");

        var before = CoverageQueries.state(f.customerId());

        BigDecimal balance =
                before.accounts().get(f.accountId()).balance();

        String amount = switch (scenario) {
            case "zero" -> "0";
            case "negative" -> "-1.00";
            case "overdraft" ->
                    balance.add(BigDecimal.ONE).toPlainString();
            default -> "1.00";
        };

        TransferFundsPage page = new TransferFundsPage(driver);
        page.open();

        page.submitInvalidAmount(
                f.accountId(),
                scenario.equals("same account")
                        ? f.accountId() : destination,
                amount
        );

        wait.until(d ->
                d.findElements(
                        By.cssSelector("#rightPanel h1")
                ).stream().anyMatch(e ->
                        e.isDisplayed()
                                && (
                                e.getText().contains("Complete")
                                        || e.getText().contains("Error")
                        )
                )
        );

        var after = CoverageQueries.state(f.customerId());

        Assert.assertEquals(
                after,
                before,
                "REJECTION DEFECT: " + scenario
                        + " changed accounts or transactions."
        );

        Assert.assertTrue(
                driver.findElements(
                        By.cssSelector("#rightPanel h1")
                ).stream().noneMatch(e ->
                        e.isDisplayed()
                                && e.getText().contains(
                                "Transfer Complete"
                        )
                ),
                "REJECTION DEFECT: invalid transfer reported success."
        );

        unchanged(f.customerId(), before);
    }

    @DataProvider(name = "invalidLoans", parallel = false)
    public Object[][] invalidLoans() {

        return new Object[][] {
                {"", "20"},
                {"abc", "20"},
                {"0", "20"},
                {"-100.00", "20"},
                {"100", ""},
                {"100", "abc"},
                {"100", "-20"}
        };
    }

    @Test(dataProvider = "invalidLoans")
    public void invalidLoanMustNotApproveOrMutate(
            String amount, String down) {

        Fixture f = fixture();
        var before = CoverageQueries.state(f.customerId());

        open("requestloan.htm");

        select(By.id("fromAccountId"), f.accountId());
        fill(By.id("amount"), amount);
        fill(By.id("downPayment"), down);
        click(By.cssSelector("input[value='Apply Now']"));

        wait.until(d ->
                d.findElement(
                        By.id("requestLoanResult")
                ).isDisplayed()
                        || d.findElement(
                        By.id("requestLoanError")
                ).isDisplayed()
                        || d.findElements(
                        By.cssSelector("#requestLoanForm .error")
                ).stream().anyMatch(e ->
                        e.isDisplayed()
                                && !e.getText().isBlank()
                )
        );

        Assert.assertEquals(
                CoverageQueries.state(f.customerId()),
                before,
                "LOAN VALIDATION DEFECT: invalid amount changed data."
        );

        Assert.assertFalse(
                driver.findElement(
                        By.id("loanRequestApproved")
                ).isDisplayed(),
                "LOAN VALIDATION DEFECT: invalid amount approved."
        );

        Assert.assertFalse(
                driver.findElement(
                        By.id("requestLoanError")
                ).isDisplayed(),
                "LOAN VALIDATION DEFECT: internal error "
                        + "instead of input validation."
        );

        unchanged(f.customerId(), before);
    }

    @Test
    public void insufficientOpeningDepositMustNotCreateAccount() {

        Fixture f = fixture();

        BigDecimal initial =
                CoverageQueries.state(f.customerId())
                        .accounts().get(f.accountId()).balance();

        Assert.assertTrue(initial.signum() > 0);

        new BankActionsApi().withdraw(
                f.accountId(), initial
        );

        var before = CoverageQueries.state(f.customerId());

        var r = request(
                "POST",
                "createAccount",
                Map.of(
                        "customerId", f.customerId(),
                        "newAccountType", 1,
                        "fromAccountId", f.accountId()
                )
        );

        Assert.assertEquals(
                CoverageQueries.state(f.customerId()),
                before,
                "ACCOUNT VALIDATION DEFECT: insufficient "
                        + "funds created/mutated accounts."
        );

        Assert.assertTrue(
                r.statusCode() >= 400 && r.statusCode() < 500,
                "Expected client rejection, HTTP "
                        + r.statusCode()
        );

        unchanged(f.customerId(), before);
    }

    @Test
    public void invalidAccountTypeMustBeRejected() {

        Fixture f = fixture();

        var before = CoverageQueries.state(f.customerId());

        var r = request(
                "POST",
                "createAccount",
                Map.of(
                        "customerId", f.customerId(),
                        "newAccountType", 999,
                        "fromAccountId", f.accountId()
                )
        );

        Assert.assertEquals(
                CoverageQueries.state(f.customerId()),
                before,
                "Invalid account type changed data."
        );

        Assert.assertTrue(
                r.statusCode() >= 400 && r.statusCode() < 500,
                "Expected client error, HTTP "
                        + r.statusCode()
        );

        unchanged(f.customerId(), before);
    }

    @DataProvider(name = "billAmountRejections", parallel = false)
    public Object[][] billAmountRejections() {

        return new Object[][] {
                {"0"},
                {"-1.00"}
        };
    }

    @Test(dataProvider = "billAmountRejections")
    public void nonpositiveBillMustBeRejected(String amount) {

        Fixture f = fixture();

        var before = CoverageQueries.state(f.customerId());

        open("billpay.htm");

        Map<String, String> fields = new LinkedHashMap<>();

        fields.put("payee.name", "Test Utilities");
        fields.put("payee.address.street", "10 Test Street");
        fields.put("payee.address.city", "Test City");
        fields.put("payee.address.state", "Test State");
        fields.put("payee.address.zipCode", "12345");
        fields.put("payee.phoneNumber", "5551234567");
        fields.put("payee.accountNumber", "987654");
        fields.put("verifyAccount", "987654");
        fields.put("amount", amount);

        fields.forEach((k, v) -> fill(By.name(k), v));

        select(By.name("fromAccountId"), f.accountId());

        click(By.cssSelector("input[value='Send Payment']"));

        wait.until(d ->
                d.findElement(
                        By.id("billpayResult")
                ).isDisplayed()
                        || d.findElement(
                        By.id("billpayError")
                ).isDisplayed()
                        || d.findElements(
                        By.cssSelector("#billpayForm .error")
                ).stream().anyMatch(WebElement ->
                        WebElement.isDisplayed()
                )
        );

        Assert.assertEquals(
                CoverageQueries.state(f.customerId()),
                before,
                "BILL VALIDATION DEFECT: nonpositive "
                        + "amount changed data."
        );

        Assert.assertFalse(
                driver.findElement(
                        By.id("billpayResult")
                ).isDisplayed(),
                "Invalid bill reported success."
        );

        Assert.assertFalse(
                driver.findElement(
                        By.id("billpayError")
                ).isDisplayed(),
                "Internal error instead of field validation."
        );

        unchanged(f.customerId(), before);
    }
}