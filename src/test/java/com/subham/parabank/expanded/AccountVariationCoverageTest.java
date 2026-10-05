package com.subham.parabank.expanded;

import com.subham.parabank.database.*;
import com.subham.parabank.database.CoverageQueries.State;
import com.subham.parabank.listeners.*;
import com.subham.parabank.pages.*;

import java.math.BigDecimal;
import java.util.*;

import org.testng.*;
import org.testng.annotations.*;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class AccountVariationCoverageTest
        extends CoverageSupport {

    @DataProvider(name = "accountVariants", parallel = false)
    public Object[][] accountVariants() {

        return new Object[][] {
                {"UI", 0, false},
                {"UI", 1, true},
                {"API", 1, false}
        };
    }

    @Test(dataProvider = "accountVariants")
    public void accountCreationAndOpeningTransactions(
            String channel,
            int type,
            boolean secondFunding) {

        Fixture f = fixture();
        int funding = f.accountId();

        if (secondFunding) {
            funding = create(f, funding, 0, "API");
        }

        create(f, funding, type, channel);
    }

    private int create(
            Fixture f,
            int funding,
            int type,
            String channel) {

        State before = CoverageQueries.state(f.customerId());

        BigDecimal deposit = new BigDecimal(
                CoverageQueries.parameter("minimumBalance")
        );

        Assert.assertTrue(
                before.accounts().get(funding)
                        .balance().compareTo(deposit) >= 0,
                "Insufficient opening deposit."
        );

        int newId;

        if (channel.equals("API")) {

            newId = json(request(
                    "POST",
                    "createAccount",
                    Map.of(
                            "customerId", f.customerId(),
                            "newAccountType", type,
                            "fromAccountId", funding
                    )
            )).getInt("id");

        } else {

            OpenNewAccountPage page =
                    new OpenNewAccountPage(driver);

            page.open();

            money(
                    page.getMinimumDeposit(),
                    deposit,
                    "Displayed opening deposit"
            );

            newId = type == 0
                    ? page.openCheckingAccount(funding)
                    : new SavingsAccountPage(driver)
                            .create(funding);
        }

        var after = CoverageQueries.state(f.customerId());

        var added = new HashSet<>(
                after.accounts().keySet()
        );

        added.removeAll(before.accounts().keySet());

        Assert.assertEquals(added, Set.of(newId));

        Assert.assertTrue(
                after.accounts().keySet().containsAll(
                        before.accounts().keySet()
                )
        );

        var created = after.accounts().get(newId);

        Assert.assertEquals(created.type(), type);

        Assert.assertEquals(
                created.customerId(), f.customerId()
        );

        money(created.balance(), deposit, "Opening balance");

        before.accounts().forEach((id, a) ->
                money(
                        after.accounts().get(id).balance(),
                        id == funding
                                ? a.balance().subtract(deposit)
                                : a.balance(),
                        "Funding delta " + id
                )
        );

        var rows =
                addedTransactions(f.customerId(), before);

        Assert.assertEquals(rows.size(), 2);

        var debit = rows.stream()
                .filter(t -> t.accountId() == funding)
                .findFirst()
                .orElseThrow();

        var credit = rows.stream()
                .filter(t -> t.accountId() == newId)
                .findFirst()
                .orElseThrow();

        Assert.assertEquals(debit.type(), "1");
        Assert.assertEquals(credit.type(), "0");

        money(debit.amount(), deposit, "Opening debit");
        money(credit.amount(), deposit, "Opening credit");

        checkBalances(f.customerId());
        checkTransaction(debit);
        checkTransaction(credit);

        return newId;
    }
}