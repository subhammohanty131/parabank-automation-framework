package com.subham.parabank.pages;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class OpenNewAccountPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public OpenNewAccountPage(WebDriver driver) {

        this.driver = driver;

        this.wait = new WebDriverWait(
                driver,
                Duration.ofSeconds(10)
        );
    }

    public void open() {

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.linkText("Open New Account")
                )
        ).click();

        wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.id("type")
                )
        );
    }

    public BigDecimal getMinimumDeposit() {

        Pattern pattern = Pattern.compile(
                "minimum of\\s*\\$([\\d,]+(?:\\.\\d+)?)",
                Pattern.CASE_INSENSITIVE
        );

        return wait.until(d -> {

            String pageText =
                    d.findElement(By.id("rightPanel")).getText();

            Matcher matcher = pattern.matcher(pageText);

            if (!matcher.find()) {
                return null;
            }

            return new BigDecimal(
                    matcher.group(1).replace(",", "")
            );
        });
    }

    public int openCheckingAccount(int fundingAccountId) {

        // Standard ParaBank option: 0 = CHECKING.
        new Select(
                driver.findElement(By.id("type"))
        ).selectByValue("0");

        String fundingId =
                Integer.toString(fundingAccountId);

        // Wait for the application to populate the account list.
        wait.until(d ->
                new Select(
                        d.findElement(By.id("fromAccountId"))
                ).getOptions()
                        .stream()
                        .anyMatch(option ->
                                fundingId.equals(
                                        option.getAttribute("value")
                                )
                        )
        );

        new Select(
                driver.findElement(By.id("fromAccountId"))
        ).selectByValue(fundingId);

        wait.until(d ->
                fundingId.equals(
                        new Select(
                                d.findElement(By.id("fromAccountId"))
                        ).getFirstSelectedOption()
                                .getAttribute("value")
                )
        );

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.cssSelector(
                                "input[value='Open New Account']"
                        )
                )
        ).click();

        wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.xpath(
                                "//h1[normalize-space()='Account Opened!']"
                        )
                )
        );

        // Capture the dynamically generated account ID.
        return wait.until(d -> {

            String id = d.findElement(
                    By.id("newAccountId")
            ).getText().trim();

            return id.matches("\\d+")
                    ? Integer.valueOf(id)
                    : null;
        });
    }

    public AccountsOverviewPage openAccountsOverview() {

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.linkText("Accounts Overview")
                )
        ).click();

        AccountsOverviewPage overview =
                new AccountsOverviewPage(driver);

        overview.isLoaded();

        return overview;
    }
}