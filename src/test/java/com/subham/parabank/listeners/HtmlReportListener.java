package com.subham.parabank.listeners;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.testng.IReporter;
import org.testng.ISuite;
import org.testng.ISuiteResult;
import org.testng.ITestContext;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.xml.XmlSuite;

public class HtmlReportListener implements IReporter {

    private record Entry(
            String kind,
            ITestResult result) {
    }

    @Override
    public void generateReport(
            List<XmlSuite> xmlSuites,
            List<ISuite> suites,
            String outputDirectory) {

        try {

            Path directory = Path.of(outputDirectory)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(directory);

            List<Entry> entries = new ArrayList<>();

            int passed = 0;
            int failed = 0;
            int skipped = 0;
            int configurationFailures = 0;

            for (ISuite suite : suites) {

                for (ISuiteResult suiteResult
                        : suite.getResults().values()) {

                    ITestContext context =
                            suiteResult.getTestContext();

                    passed += context.getPassedTests().size();
                    failed += context.getFailedTests().size();
                    skipped += context.getSkippedTests().size();

                    configurationFailures +=
                            context.getFailedConfigurations().size();

                    context.getPassedTests()
                            .getAllResults()
                            .forEach(r ->
                                    entries.add(new Entry("Test", r)));

                    context.getFailedTests()
                            .getAllResults()
                            .forEach(r ->
                                    entries.add(new Entry("Test", r)));

                    context.getSkippedTests()
                            .getAllResults()
                            .forEach(r ->
                                    entries.add(new Entry("Test", r)));

                    context.getFailedConfigurations()
                            .getAllResults()
                            .forEach(r ->
                                    entries.add(
                                            new Entry("Configuration", r)
                                    ));
                }
            }

            entries.sort(
                    Comparator.comparingLong(
                            e -> e.result().getStartMillis()
                    )
            );

            StringBuilder html = new StringBuilder("""
                    <!doctype html>
                    <html lang="en">
                    <head>
                    <meta charset="UTF-8">
                    <meta name="viewport"
                          content="width=device-width,initial-scale=1">
                    <title>ParaBank Automation Report</title>
                    <style>
                    body {
                        font-family: Arial, sans-serif;
                        margin: 30px;
                        background: #f3f5f7;
                        color: #17212b;
                    }
                    article {
                        background: white;
                        border: 1px solid #dce2e8;
                        padding: 18px;
                        margin: 16px 0;
                        border-radius: 8px;
                    }
                    .PASS { color: #126b36; }
                    .FAIL { color: #b42318; }
                    .SKIP { color: #725b00; }
                    .defect {
                        background: #fff4ce;
                        padding: 12px;
                        border-left: 4px solid #b98500;
                    }
                    pre {
                        white-space: pre-wrap;
                        overflow-wrap: anywhere;
                        background: #f3f5f7;
                        padding: 12px;
                    }
                    img {
                        max-width: 360px;
                        width: 100%;
                        border: 1px solid #ccc;
                    }
                    h2 {
                        font-size: 18px;
                        overflow-wrap: anywhere;
                    }
                    a { color: #0759ae; }
                    </style>
                    </head>
                    <body>
                    <h1>ParaBank Automation Report</h1>
                    """);

            html.append("<p>Generated: ")
                    .append(
                            escape(LocalDateTime.now().toString())
                    )
                    .append("</p>");

            html.append("<p><strong>Tests: ")
                    .append(passed + failed + skipped)
                    .append(" | Passed: ").append(passed)
                    .append(" | Failed: ").append(failed)
                    .append(" | Skipped: ").append(skipped)
                    .append(" | Configuration failures: ")
                    .append(configurationFailures)
                    .append("</strong></p>");

            html.append(
                    "<p>A passing data-integrity test does not "
                            + "resolve a known validation defect.</p>"
            );

            for (Entry entry : entries) {

                ITestResult result = entry.result();

                String status = switch (result.getStatus()) {
                    case ITestResult.SUCCESS -> "PASS";
                    case ITestResult.SKIP -> "SKIP";
                    default -> "FAIL";
                };

                String name =
                        result.getTestClass()
                                .getRealClass()
                                .getSimpleName()
                                + "."
                                + result.getMethod().getMethodName();

                html.append("<article><h2 class=\"")
                        .append(status)
                        .append("\">")
                        .append(status)
                        .append(" — ")
                        .append(escape(entry.kind()))
                        .append(": ")
                        .append(escape(name))
                        .append("</h2>");

                html.append("<p>Parameters: ")
                        .append(
                                escape(
                                        Arrays.deepToString(
                                                result.getParameters()
                                        )
                                )
                        )
                        .append(" | Duration: ")
                        .append(
                                Math.max(
                                        0,
                                        result.getEndMillis()
                                                - result.getStartMillis()
                                )
                        )
                        .append(" ms</p>");

                Object knownDefect =
                        result.getAttribute("knownDefect");

                if (knownDefect != null) {

                    html.append(
                            "<p class=\"defect\"><strong>"
                                    + "Unresolved known defect:</strong> "
                    )
                            .append(escape(knownDefect.toString()))
                            .append("</p>");
                }

                appendScreenshot(
                        html,
                        directory,
                        result.getAttribute("screenshotPath"),
                        "Failure screenshot"
                );

                appendScreenshot(
                        html,
                        directory,
                        result.getAttribute("evidenceScreenshotPath"),
                        "Known-defect evidence"
                );

                Object evidenceError =
                        result.getAttribute("evidenceError");

                if (evidenceError != null) {

                    html.append("<p>")
                            .append(escape(evidenceError.toString()))
                            .append("</p>");
                }

                if (result.getThrowable() != null) {

                    StringWriter trace = new StringWriter();

                    result.getThrowable().printStackTrace(
                            new PrintWriter(trace)
                    );

                    html.append(
                            "<details open><summary>"
                                    + "Failure or skip details"
                                    + "</summary><pre>"
                    )
                            .append(escape(trace.toString()))
                            .append("</pre></details>");
                }

                List<String> logs =
                        Reporter.getOutput(result);

                if (!logs.isEmpty()) {

                    html.append(
                            "<details><summary>Test log</summary><pre>"
                    )
                            .append(
                                    escape(
                                            String.join(
                                                    System.lineSeparator(),
                                                    logs
                                            )
                                    )
                            )
                            .append("</pre></details>");
                }

                html.append("</article>");
            }

            html.append("</body></html>");

            Path report =
                    directory.resolve("parabank-report.html");

            Files.writeString(
                    report,
                    html,
                    StandardCharsets.UTF_8
            );

            System.out.println(
                    "HTML report saved: " + report
            );

        } catch (Exception e) {

            System.err.println(
                    "HTML report generation failed: "
                            + e.getMessage()
            );
        }
    }

    private static void appendScreenshot(
            StringBuilder html,
            Path directory,
            Object path,
            String label) {

        if (path == null) {
            return;
        }

        try {

            Path source =
                    Path.of(path.toString());

            Path evidenceDirectory =
                    directory.resolve("evidence");

            Files.createDirectories(evidenceDirectory);

            Path destination =
                    evidenceDirectory.resolve(
                            source.getFileName()
                    );

            Files.copy(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );

            String link =
                    "evidence/" + destination.getFileName();

            html.append("<p>")
                    .append(escape(label))
                    .append("</p><a href=\"")
                    .append(escape(link))
                    .append(
                            "\" target=\"_blank\" rel=\"noopener\">"
                                    + "<img src=\""
                    )
                    .append(escape(link))
                    .append("\" alt=\"")
                    .append(escape(label))
                    .append("\"></a>");

        } catch (Exception e) {

            html.append("<p>")
                    .append(escape(label))
                    .append(" unavailable: ")
                    .append(escape(e.getMessage()))
                    .append("</p>");
        }
    }

    private static String escape(String value) {

        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}