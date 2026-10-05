package com.subham.parabank.pages;

import java.math.BigDecimal;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class TransferFundsPage {
    private final WebDriver driver;
    private final WebDriverWait wait;
    private final By amount = By.id("amount");
    private final By fromAccount = By.id("fromAccountId");
    private final By toAccount = By.id("toAccountId");
    private final By complete = By.xpath("//h1[normalize-space()='Transfer Complete!']");

    public TransferFundsPage(WebDriver driver) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    public void open() {
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Transfer Funds"))).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(amount));
    }

    private void selectAccount(By locator, int accountId) {
        String value = Integer.toString(accountId);
        wait.until(d -> new Select(d.findElement(locator)).getOptions().stream()
                .anyMatch(option -> value.equals(option.getAttribute("value"))));
        new Select(driver.findElement(locator)).selectByValue(value);
        wait.until(d -> value.equals(new Select(d.findElement(locator))
                .getFirstSelectedOption().getAttribute("value")));
    }

    public void transfer(int sourceId, int destinationId, BigDecimal transferAmount) {
        if (sourceId == destinationId || transferAmount.signum() <= 0) {
            throw new IllegalArgumentException("Use different accounts and a positive amount.");
        }
        selectAccount(fromAccount, sourceId);
        selectAccount(toAccount, destinationId);
        driver.findElement(amount).clear();
        driver.findElement(amount).sendKeys(transferAmount.toPlainString());
        wait.until(ExpectedConditions.elementToBeClickable(
                By.cssSelector("input[value='Transfer']"))).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(complete));
    }

    public String getConfirmationText() {
        wait.until(ExpectedConditions.visibilityOfElementLocated(complete));
        return driver.findElement(By.id("rightPanel")).getText();
    }

    public AccountsOverviewPage openAccountsOverview() {
        wait.until(ExpectedConditions.elementToBeClickable(By.linkText("Accounts Overview"))).click();
        AccountsOverviewPage page = new AccountsOverviewPage(driver);
        page.isLoaded();
        return page;
    }
    public void submitInvalidAmount(
            int sourceId,
            int destinationId,
            String rawAmount) {

        selectAccount(fromAccount, sourceId);
        selectAccount(toAccount, destinationId);

        driver.findElement(amount).clear();

        if (!rawAmount.isEmpty()) {
            driver.findElement(amount).sendKeys(rawAmount);
        }

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.cssSelector("input[value='Transfer']")
                )
        ).click();
    }

    public String getErrorText() {

        wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.xpath(
                                "//*[@id='rightPanel']//h1"
                                        + "[normalize-space()='Error!']"
                        )
                )
        );

        return driver.findElement(
                By.id("rightPanel")
        ).getText().trim();
    }
}
