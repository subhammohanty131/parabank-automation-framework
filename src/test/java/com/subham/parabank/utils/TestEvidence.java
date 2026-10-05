package com.subham.parabank.utils;

import java.nio.file.Path;

import org.openqa.selenium.WebDriver;
import org.testng.ITestResult;
import org.testng.Reporter;

public class TestEvidence {

    private TestEvidence() {
    }

    public static void captureKnownDefect(
            WebDriver driver,
            String defectId,
            String caseName) {

        ITestResult result =
                Reporter.getCurrentTestResult();

        if (result == null) {
            throw new IllegalStateException(
                    "Evidence capture must run inside a TestNG test."
            );
        }

        result.setAttribute(
                "knownDefect",
                defectId + ": " + caseName
                        + " shows an internal error "
                        + "instead of field validation."
        );

        try {

            Path screenshot =
                    ScreenshotUtils.saveScreenshot(
                            driver,
                            defectId + "_" + caseName
                    );

            result.setAttribute(
                    "evidenceScreenshotPath",
                    screenshot.toString()
            );

            Reporter.log(
                    "Known-defect screenshot saved: " + screenshot,
                    true
            );

        } catch (Exception e) {

            String message =
                    "Known-defect screenshot unavailable: "
                            + e.getMessage();

            result.setAttribute(
                    "evidenceError",
                    message
            );

            Reporter.log(message, true);
        }
    }
}