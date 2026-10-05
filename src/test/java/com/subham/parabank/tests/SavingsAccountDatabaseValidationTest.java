package com.subham.parabank.tests;

import com.subham.parabank.api.ApiValidation;
import com.subham.parabank.database.AccountCreationQueries;
import com.subham.parabank.database.AccountCreationQueries.Account;
import com.subham.parabank.listeners.FailureScreenshotListener;
import com.subham.parabank.listeners.HtmlReportListener;
import com.subham.parabank.pages.OpenNewAccountPage;
import com.subham.parabank.pages.SavingsAccountPage;
import com.subham.parabank.support.AdditionalCoverageBase;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.testng.Assert;
import org.testng.Reporter;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class SavingsAccountDatabaseValidationTest
        extends AdditionalCoverageBase {

    @Test
    public void verifyNewSavingsAccountAcrossUIApiAndDatabase() {

        Account funding =
                AccountCreationQueries.getAccount(PRIMARY);

        BigDecimal before = startingBalance(PRIMARY);
        BigDecimal otherBefore = startingBalance(SECONDARY);

        ApiValidation.assertAccount(funding);

        Set<Integer> idsBefore =
                AccountCreationQueries.getAccountIds(
                        funding.customerId()
                );

        OpenNewAccountPage page =
                new OpenNewAccountPage(driver);

        page.open();

        BigDecimal deposit = page.getMinimumDeposit();

        Assert.assertTrue(
                deposit.signum() > 0,
                "Opening deposit must be positive."
        );

        Assert.assertTrue(
                before.compareTo(deposit) >= 0,
                "Insufficient savings opening funds: " + before
        );

        int newId =
                new SavingsAccountPage(driver).create(PRIMARY);

        Assert.assertFalse(
                idsBefore.contains(newId),
                "UI returned an existing account."
        );

        wait.until(d ->
                AccountCreationQueries.getAccountIds(
                        funding.customerId()
                ).contains(newId)
        );

        Set<Integer> idsAfter =
                AccountCreationQueries.getAccountIds(
                        funding.customerId()
                );

        Set<Integer> added = new HashSet<>(idsAfter);
        added.removeAll(idsBefore);

        Assert.assertEquals(
                added,
                Set.of(newId),
                "Expected exactly one new savings account."
        );

        Assert.assertTrue(
                idsAfter.containsAll(idsBefore),
                "Existing account disappeared."
        );

        Account created =
                AccountCreationQueries.getAccount(newId);

        Assert.assertEquals(
                created.id(),
                newId,
                "UI versus DB new account id."
        );

        Assert.assertEquals(
                created.customerId(),
                funding.customerId(),
                "Incorrect savings owner."
        );

        Assert.assertEquals(
                created.type(),
                1,
                "Expected SAVINGS database type 1."
        );

        money(
                created.balance(),
                deposit,
                "Savings opening balance"
        );

        balances(
                Map.of(
                        PRIMARY, before.subtract(deposit),
                        SECONDARY, otherBefore,
                        newId, deposit
                )
        );

        Account updatedFunding =
                AccountCreationQueries.getAccount(PRIMARY);

        money(
                updatedFunding.balance().add(created.balance()),
                before,
                "Funds conservation"
        );

        ApiValidation.assertAccount(created);
        ApiValidation.assertAccount(updatedFunding);

        Reporter.log(
                "Validated new savings account: " + created,
                true
        );
    }
}