package com.subham.parabank.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginPage {

    private final WebDriver driver;

    private final By username =
            By.name("username");

    private final By password =
            By.name("password");

    private final By loginButton =
            By.cssSelector("input[value='Log In']");

    public LoginPage(WebDriver driver) {
        this.driver = driver;
    }

    public void enterUsername(String user) {
        driver.findElement(username).sendKeys(user);
    }

    public void enterPassword(String pass) {
        driver.findElement(password).sendKeys(pass);
    }

    public void clickLogin() {
        driver.findElement(loginButton).click();
    }

    public AccountsOverviewPage login(
            String user,
            String pass) {

        enterUsername(user);
        enterPassword(pass);
        clickLogin();

        return new AccountsOverviewPage(driver);
    }
}