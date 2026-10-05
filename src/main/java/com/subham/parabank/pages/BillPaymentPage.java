package com.subham.parabank.pages;

import java.math.BigDecimal;
import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class BillPaymentPage {

    public record Payee(
            String name,
            String street,
            String city,
            String state,
            String zip,
            String phone,
            String accountNumber) {
    }

    private final WebDriver driver;
    private final WebDriverWait wait;

    public BillPaymentPage(WebDriver driver) {
        this.driver = driver;
        wait = new WebDriverWait(
                driver, Duration.ofSeconds(10)
        );
    }

    public void open() {

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.linkText("Bill Pay")
                )
        ).click();

        wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.name("payee.name")
                )
        );
    }

    private void fill(String name, String value) {

        WebElement field = wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.name(name)
                )
        );

        field.clear();
        field.sendKeys(value);
    }

    public String pay(
            Payee payee, int sourceId, BigDecimal amount) {

        fill("payee.name", payee.name());
        fill("payee.address.street", payee.street());
        fill("payee.address.city", payee.city());
        fill("payee.address.state", payee.state());
        fill("payee.address.zipCode", payee.zip());
        fill("payee.phoneNumber", payee.phone());
        fill("payee.accountNumber", payee.accountNumber());
        fill("verifyAccount", payee.accountNumber());
        fill("amount", amount.toPlainString());

        String account = Integer.toString(sourceId);
        By select = By.name("fromAccountId");

        wait.until(d ->
                new Select(
                        d.findElement(select)
                ).getOptions().stream().anyMatch(option ->
                        account.equals(option.getAttribute("value"))
                )
        );

        new Select(
                driver.findElement(select)
        ).selectByValue(account);

        wait.until(d ->
                account.equals(
                        new Select(
                                d.findElement(select)
                        ).getFirstSelectedOption()
                                .getAttribute("value")
                )
        );

        wait.until(
                ExpectedConditions.elementToBeClickable(
                        By.cssSelector(
                                "input[value='Send Payment']"
                        )
                )
        ).click();

        wait.until(
                ExpectedConditions.visibilityOfElementLocated(
                        By.xpath(
                                "//h1[normalize-space()='Bill Payment Complete']"
                        )
                )
        );

        return wait.until(d -> {

            String text = d.findElement(
                    By.id("billpayResult")
            ).getText();

            return text.contains(payee.name())
                    && text.contains(account)
                    ? text
                    : null;
        });
    }
}