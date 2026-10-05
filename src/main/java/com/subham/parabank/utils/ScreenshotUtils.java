package com.subham.parabank.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

public class ScreenshotUtils {

    private ScreenshotUtils() {
    }

    public static Path saveScreenshot(
            WebDriver driver,
            String name) throws IOException {

        if (!(driver instanceof TakesScreenshot)) {
            throw new IllegalArgumentException(
                    "Driver does not support screenshots."
            );
        }

        byte[] png = ((TakesScreenshot) driver)
                .getScreenshotAs(OutputType.BYTES);

        Path directory = Path.of(
                "test-output",
                "screenshots"
        ).toAbsolutePath();

        Files.createDirectories(directory);

        String safeName =
                name.replaceAll("[^a-zA-Z0-9_-]", "_");

        if (safeName.length() > 80) {
            safeName = safeName.substring(0, 80);
        }

        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern(
                        "yyyyMMdd_HHmmss_SSS"
                )
        );

        // Unique suffix prevents overwriting screenshots.
        Path file = Files.createTempFile(
                directory,
                safeName + "_" + timestamp + "_",
                ".png"
        );

        try {

            Files.write(file, png);

            return file;

        } catch (IOException e) {

            Files.deleteIfExists(file);

            throw e;
        }
    }
}