package com.subham.parabank.pages;

import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;

public class SavingsAccountPage {

    private final WebDriver driver;
    private final WebDriverWait wait;

    public SavingsAccountPage(WebDriver driver) {
        this.driver = driver;
        wait = new WebDriverWait(
                driver, Duration.ofSeconds(10)
        );
    }

    public int create(int fundingAccountId) {

        new Select(
                driver.findElement(By.id("type"))
        ).selectByValue("1");

        wait.until(d ->
                "1".equals(
                        new Select(
                                d.findElement(By.id("type"))
                        ).getFirstSelectedOption()
                                .getAttribute("value")
                )
        );

        String value = Integer.toString(fundingAccountId);

        wait.until(d ->
                new Select(
                        d.findElement(By.id("fromAccountId"))
                ).getOptions().stream().anyMatch(option ->
                        value.equals(option.getAttribute("value"))
                )
        );

        new Select(
                driver.findElement(By.id("fromAccountId"))
        ).selectByValue(value);

        wait.until(d ->
                value.equals(
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

        return wait.until(d -> {

            String text = d.findElement(
                    By.id("newAccountId")
            ).getText().trim();

            return text.matches("\\d+")
                    ? Integer.valueOf(text)
                    : null;
        });
    }
}