package com.subham.parabank.listeners;

import com.subham.parabank.base.DriverFactory;
import com.subham.parabank.utils.ScreenshotUtils;

import java.nio.file.Path;

import org.openqa.selenium.WebDriver;

import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ITestResult;
import org.testng.Reporter;

public class FailureScreenshotListener
        implements IInvokedMethodListener {

    @Override
    public void afterInvocation(
            IInvokedMethod method,
            ITestResult result) {

        if (result.getStatus() != ITestResult.FAILURE) {
            return;
        }

        WebDriver driver =
                DriverFactory.getDriver();

        if (driver == null) {

            Reporter.log(
                    "Screenshot unavailable: no active browser for "
                            + result.getMethod().getMethodName(),
                    true
            );

            return;
        }

        try {

            String name =
                    result.getTestClass()
                            .getRealClass()
                            .getSimpleName()
                            + "_"
                            + result.getMethod().getMethodName();

            Path screenshot =
                    ScreenshotUtils.saveScreenshot(
                            driver,
                            name
                    );

            result.setAttribute(
                    "screenshotPath",
                    screenshot.toString()
            );

            Reporter.log(
                    "Screenshot saved: " + screenshot,
                    true
            );

        } catch (Exception e) {

            // Preserve the original test failure.
            Reporter.log(
                    "Screenshot capture failed: "
                            + e.getClass().getSimpleName()
                            + ": " + e.getMessage(),
                    true
            );
        }
    }
}