package com.subham.parabank.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.math.BigDecimal;
import java.time.Duration;

public class AccountsOverviewPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    private final By pageHeading =
            By.xpath("//h1[contains(text(),'Accounts Overview')]");

    public AccountsOverviewPage(WebDriver driver) {

        this.driver = driver;

        this.wait =
                new WebDriverWait(
                        driver,
                        Duration.ofSeconds(10)
                );
    }

    public boolean isLoaded() {

        return wait.until(
                ExpectedConditions
                        .visibilityOfElementLocated(pageHeading)
        ).isDisplayed();
    }

    public BigDecimal getBalance(int accountId) {

        By balanceLocator =
                By.xpath(
                        "//a[normalize-space()='"
                        + accountId
                        + "']/ancestor::tr/td[2]"
                );

        String balanceText =
                wait.until(
                        ExpectedConditions
                                .visibilityOfElementLocated(
                                        balanceLocator
                                )
                ).getText();

        balanceText =
                balanceText
                        .replace("$", "")
                        .replace(",", "")
                        .trim();

        return new BigDecimal(balanceText);
    }
}