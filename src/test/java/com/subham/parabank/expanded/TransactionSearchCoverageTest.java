package com.subham.parabank.expanded;

import com.subham.parabank.api.BankActionsApi;
import com.subham.parabank.database.CoverageQueries;
import com.subham.parabank.database.CoverageQueries.Tx;
import com.subham.parabank.listeners.*;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import org.openqa.selenium.*;
import org.testng.*;
import org.testng.annotations.*;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class TransactionSearchCoverageTest
        extends CoverageSupport {

    @DataProvider(name = "searches", parallel = false)
    public Object[][] searches() {

        return new Object[][] {
                {"ID"},
                {"AMOUNT"},
                {"DATE"},
                {"DATE_RANGE"},
                {"EMPTY"},
                {"ACTIVITY"}
        };
    }

    @Test(dataProvider = "searches")
    public void searchResultsMatchDatabaseAndApi(String mode) {

        Fixture f = fixture();

        new BankActionsApi().deposit(
                f.accountId(), new BigDecimal("11.23")
        );

        new BankActionsApi().withdraw(
                f.accountId(), new BigDecimal("1.00")
        );

        var before = CoverageQueries.state(f.customerId());

        Tx seed = before.transactions().stream()
                .filter(t ->
                        t.amount().compareTo(
                                new BigDecimal("11.23")
                        ) == 0
                )
                .findFirst()
                .orElseThrow();

        String date = seed.date().format(
                DateTimeFormatter.ofPattern("MM-dd-yyyy")
        );

        List<Tx> expected = before.transactions();

        String path = "accounts/" + f.accountId()
                + "/transactions/";

        switch (mode) {

            case "ID":
                expected = List.of(seed);
                path = "transactions/" + seed.id();
                break;

            case "AMOUNT":
                expected = expected.stream()
                        .filter(t ->
                                t.amount().compareTo(
                                        seed.amount()
                                ) == 0
                        ).toList();
                path += "amount/11.23";
                break;

            case "EMPTY":
                expected = List.of();
                path += "amount/99999.99";
                break;

            case "DATE":
                expected = expected.stream()
                        .filter(t ->
                                seed.date().equals(t.date())
                        ).toList();
                path += "onDate/" + date;
                break;

            case "DATE_RANGE":
                expected = expected.stream()
                        .filter(t ->
                                seed.date().equals(t.date())
                        ).toList();
                path += "fromDate/" + date + "/toDate/" + date;
                break;

            case "ACTIVITY":
                expected = expected.stream()
                        .filter(t -> t.type().equals("1"))
                        .toList();
                path += "month/All/type/Debit";
                break;

            default:
                throw new AssertionError(mode);
        }

        var apiResponse = request("GET", path, Map.of());

        List<Integer> apiIds;

        if (mode.equals("EMPTY")
                && apiResponse.statusCode() == 404) {

            apiIds = List.of();

        } else if (mode.equals("ID")) {

            apiIds = List.of(
                    json(apiResponse).getInt("id")
            );

        } else {

            apiIds = json(apiResponse)
                    .getList("id", Integer.class);
        }

        Set<Integer> expectedIds = expected.stream()
                .map(Tx::id)
                .collect(Collectors.toSet());

        Assert.assertEquals(
                new HashSet<>(apiIds),
                expectedIds,
                "API filter results versus DB."
        );

        Assert.assertEquals(
                apiIds.size(),
                expectedIds.size(),
                "Duplicate API rows."
        );

        if (!mode.equals("ACTIVITY")) {

            open("findtrans.htm");
            select(By.id("accountId"), f.accountId());

            switch (mode) {

                case "ID":
                    fill(
                            By.id("transactionId"),
                            Integer.toString(seed.id())
                    );
                    click(By.id("findById"));
                    break;

                case "AMOUNT":
                case "EMPTY":
                    fill(
                            By.id("amount"),
                            mode.equals("EMPTY")
                                    ? "99999.99" : "11.23"
                    );
                    click(By.id("findByAmount"));
                    break;

                case "DATE":
                    fill(By.id("transactionDate"), date);
                    click(By.id("findByDate"));
                    break;

                case "DATE_RANGE":
                    fill(By.id("fromDate"), date);
                    fill(By.id("toDate"), date);
                    click(By.id("findByDateRange"));
                    break;

                default:
                    throw new AssertionError(mode);
            }

            visible(By.id("resultContainer"));

            wait.until(d ->
                    d.findElements(
                            By.cssSelector("#transactionBody tr")
                    ).size() == expectedIds.size()
            );

            Map<Integer, List<WebElement>> uiRows =
                    new HashMap<>();

            for (WebElement row :
                    driver.findElements(
                            By.cssSelector("#transactionBody tr")
                    )) {

                var cells =
                        row.findElements(By.tagName("td"));

                String href = cells.get(1)
                        .findElement(By.tagName("a"))
                        .getAttribute("href");

                int id = Integer.parseInt(
                        href.substring(href.indexOf("id=") + 3)
                );

                Assert.assertNull(
                        uiRows.put(id, cells),
                        "Duplicate UI transaction row."
                );
            }

            Assert.assertEquals(
                    uiRows.keySet(),
                    expectedIds,
                    "UI search IDs versus DB."
            );

            for (Tx tx : expected) {

                var cells = uiRows.get(tx.id());

                Assert.assertEquals(
                        cells.get(1).getText().trim(),
                        tx.description()
                );

                Assert.assertEquals(
                        cells.get(0).getText().trim(),
                        tx.date().format(
                                DateTimeFormatter.ofPattern(
                                        "MM-dd-yyyy"
                                )
                        )
                );

                money(
                        parseMoney(cells.get(2).getText()),
                        tx.type().equals("1")
                                ? tx.amount() : BigDecimal.ZERO,
                        "Search debit"
                );

                money(
                        parseMoney(cells.get(3).getText()),
                        tx.type().equals("0")
                                ? tx.amount() : BigDecimal.ZERO,
                        "Search credit"
                );
            }
        }

        unchanged(f.customerId(), before);

        Reporter.log(
                "Validated search/filter: " + mode
                        + ", rows=" + expectedIds.size(),
                true
        );
    }

    private BigDecimal parseMoney(String value) {

        return value.isBlank()
                ? BigDecimal.ZERO
                : new BigDecimal(
                        value.replace("$", "")
                                .replace(",", "").trim()
                );
    }

    @DataProvider(name = "invalidSearches", parallel = false)
    public Object[][] invalidSearches() {

        return new Object[][] {
                {
                        "transactionId", "abc",
                        "findById", "transactionIdError"
                },
                {
                        "amount", "abc",
                        "findByAmount", "amountError"
                },
                {
                        "transactionDate", "bad-date",
                        "findByDate", "transactionDateError"
                }
        };
    }

    @Test(dataProvider = "invalidSearches")
    public void invalidSearchShowsFieldError(
            String field,
            String input,
            String button,
            String error) {

        Fixture f = fixture();
        var before = CoverageQueries.state(f.customerId());

        open("findtrans.htm");
        select(By.id("accountId"), f.accountId());
        fill(By.id(field), input);
        click(By.id(button));

        Assert.assertFalse(
                visible(By.id(error)).getText().isBlank()
        );

        unchanged(f.customerId(), before);
    }
}