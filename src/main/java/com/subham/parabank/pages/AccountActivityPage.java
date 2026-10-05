package com.subham.parabank.pages;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class AccountActivityPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public record HistoryTransaction(
            int id,
            String description,
            BigDecimal debit,
            BigDecimal credit) {
    }

    public AccountActivityPage(WebDriver driver) {

        this.driver = driver;

        this.wait = new WebDriverWait(
                driver,
                Duration.ofSeconds(10)
        );
    }

    public void open(int accountId) {

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.linkText("Accounts Overview")
                )
        ).click();

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.linkText(Integer.toString(accountId))
                )
        ).click();

        wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.xpath(
                                "//h1[normalize-space()='Account Details']"
                        )
                )
        );

        wait.until(
                ExpectedConditions.textToBe(
                        By.id("accountId"),
                        Integer.toString(accountId)
                )
        );
    }

    public HistoryTransaction getTransaction(int transactionId) {

        By rowLocator = By.xpath(
                "//table[@id='transactionTable']//a["
                        + "contains(@href,'transaction.htm?id=') and "
                        + "substring-after(@href,'id=')='"
                        + transactionId
                        + "']/ancestor::tr"
        );

        return wait.until(d -> {

            try {

                List<WebElement> rows =
                        d.findElements(rowLocator);

                if (rows.isEmpty()) {
                    return null;
                }

                if (rows.size() != 1) {

                    throw new IllegalStateException(
                            "Duplicate UI rows for transaction "
                                    + transactionId
                    );
                }

                WebElement row = rows.get(0);

                List<WebElement> cells =
                        row.findElements(By.tagName("td"));

                if (cells.size() < 4 || !row.isDisplayed()) {
                    return null;
                }

                // Columns: Date, Transaction, Debit, Credit.
                WebElement link =
                        cells.get(1).findElement(By.tagName("a"));

                String description =
                        link.getText().trim();

                String debit =
                        cells.get(2).getText().trim();

                String credit =
                        cells.get(3).getText().trim();

                if (description.isEmpty()
                        || (debit.isEmpty() && credit.isEmpty())) {

                    return null;
                }

                String href =
                        link.getAttribute("href");

                int actualId = Integer.parseInt(
                        href.substring(href.indexOf("id=") + 3)
                );

                return new HistoryTransaction(
                        actualId,
                        description,
                        parseAmount(debit),
                        parseAmount(credit)
                );

            } catch (StaleElementReferenceException e) {

                // Retry when the application refreshes the table.
                return null;
            }
        });
    }

    private BigDecimal parseAmount(String text) {

        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }

        String cleaned = text
                .replace("$", "")
                .replace(",", "")
                .replace("\u00a0", "")
                .trim();

        return new BigDecimal(cleaned);
    }
}