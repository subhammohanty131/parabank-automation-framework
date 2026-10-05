package com.subham.parabank.expanded;

import com.subham.parabank.database.*;
import com.subham.parabank.pages.*;
import com.subham.parabank.listeners.*;
import com.subham.parabank.utils.ConfigReader;

import java.util.*;

import org.openqa.selenium.By;
import org.testng.*;
import org.testng.annotations.*;

@Listeners({
        FailureScreenshotListener.class,
        HtmlReportListener.class
})
public class CustomerCoverageTest extends CoverageSupport {

    @Test
    public void registrationCreatesCustomerAccountAndAllowsRelogin() {

        Fixture f = fixture();
        logout();

        Assert.assertTrue(
                new LoginPage(driver).login(
                        f.username(), f.password()
                ).isLoaded(),
                "New customer login failed."
        );

        checkBalances(f.customerId());
    }

    @DataProvider(name = "registrationErrors", parallel = false)
    public Object[][] registrationErrors() {

        return new Object[][] {
                {"customer.firstName"},
                {"customer.lastName"},
                {"customer.address.street"},
                {"customer.address.city"},
                {"customer.address.state"},
                {"customer.address.zipCode"},
                {"customer.ssn"},
                {"customer.username"},
                {"customer.password"},
                {"repeatedPassword"},
                {"mismatch"}
        };
    }

    @Test(dataProvider = "registrationErrors")
    public void invalidRegistrationDoesNotCreateCustomer(
            String field) {

        String username = newUsername();
        var fields = registration(username);

        if (field.equals("mismatch")) {
            fields.put("repeatedPassword", "Different17!");
        } else {
            fields.put(field, "");
        }

        int before = CoverageQueries.countCustomers();

        register(fields);
        visibleErrors();

        Assert.assertEquals(
                CoverageQueries.countCustomers(),
                before,
                "Invalid registration created a customer."
        );

        Assert.assertNull(
                CoverageQueries.customerId(username)
        );

        Assert.assertTrue(
                driver.findElements(
                        By.linkText("Log Out")
                ).isEmpty(),
                "Invalid registration logged in."
        );

        Reporter.log(
                "Validated registration error: " + field,
                true
        );
    }

    @Test
    public void duplicateUsernameIsRejectedWithoutDataChanges() {

        Fixture f = fixture();

        var state = CoverageQueries.state(f.customerId());
        var profile = CoverageQueries.profile(f.customerId());
        int count = CoverageQueries.countCustomers();

        logout();
        register(registration(f.username()));
        visibleErrors();

        Assert.assertEquals(
                CoverageQueries.countCustomers(), count
        );

        Assert.assertEquals(
                CoverageQueries.profile(f.customerId()),
                profile
        );

        unchanged(f.customerId(), state);
    }

    @DataProvider(name = "failedLogins", parallel = false)
    public Object[][] failedLogins() {

        return new Object[][] {
                {"wrong password"},
                {"unknown username"},
                {"blank username"},
                {"blank password"}
        };
    }

    @Test(dataProvider = "failedLogins")
    public void failedLoginDoesNotAuthenticateOrChangeFunds(
            String scenario) {

        Integer id = CoverageQueries.customerId(
                ConfigReader.get("username")
        );

        Assert.assertNotNull(id);

        var before = CoverageQueries.state(id);

        String username =
                scenario.equals("unknown username")
                        ? newUsername()
                        : ConfigReader.get("username");

        String password =
                scenario.equals("wrong password")
                        ? "Wrong" + UUID.randomUUID()
                        : ConfigReader.get("password");

        if (scenario.equals("blank username")) {
            username = "";
        }

        if (scenario.equals("blank password")) {
            password = "";
        }

        fill(By.name("username"), username);
        fill(By.name("password"), password);
        click(By.cssSelector("input[value='Log In']"));
        visibleErrors();

        Assert.assertTrue(
                driver.findElements(
                        By.linkText("Log Out")
                ).isEmpty(),
                "Failed login authenticated."
        );

        Assert.assertTrue(
                driver.findElements(
                        By.id("accountTable")
                ).isEmpty(),
                "Failed login exposed accounts."
        );

        unchanged(id, before);

        Reporter.log(
                "Validated failed login: " + scenario,
                true
        );
    }

    @Test
    public void profileUpdatePersistsAcrossUIApiDatabaseAndRelogin() {

        Fixture f = fixture();

        var state = CoverageQueries.state(f.customerId());

        Map<String, String> updated = new LinkedHashMap<>(
                CoverageQueries.profile(f.customerId())
        );

        updated.put("firstName", "Updated");
        updated.put("lastName", "Customer");
        updated.put("address.street", "20 Updated Road");
        updated.put("address.city", "New City");
        updated.put("address.state", "New State");
        updated.put("address.zipCode", "54321");
        updated.put("phoneNumber", "5557654321");

        openProfile(f);

        updated.forEach((k, v) -> {
            if (!k.equals("ssn")) {
                fill(By.id("customer." + k), v);
            }
        });

        click(By.cssSelector("input[value='Update Profile']"));
        visible(By.id("updateProfileResult"));

        Assert.assertEquals(
                CoverageQueries.profile(f.customerId()),
                updated
        );

        checkProfile(f.customerId(), updated);

        logout();

        Assert.assertTrue(
                new LoginPage(driver).login(
                        f.username(), f.password()
                ).isLoaded()
        );

        openProfile(f);

        updated.forEach((k, v) -> {
            if (!k.equals("ssn")) {
                Assert.assertEquals(
                        visible(
                                By.id("customer." + k)
                        ).getAttribute("value"),
                        v,
                        "Persisted UI profile " + k
                );
            }
        });

        unchanged(f.customerId(), state);
    }

    @DataProvider(name = "profileErrors", parallel = false)
    public Object[][] profileErrors() {

        return new Object[][] {
                {"firstName", "firstName-error"},
                {"lastName", "lastName-error"},
                {"address.street", "street-error"},
                {"address.city", "city-error"},
                {"address.state", "state-error"},
                {"address.zipCode", "zipCode-error"}
        };
    }

    @Test(dataProvider = "profileErrors")
    public void blankProfileFieldIsRejected(
            String field, String errorId) {

        Fixture f = fixture();

        var profile = CoverageQueries.profile(f.customerId());
        var state = CoverageQueries.state(f.customerId());

        openProfile(f);
        fill(By.id("customer." + field), "");
        click(By.cssSelector("input[value='Update Profile']"));

        Assert.assertFalse(
                visible(By.id(errorId)).getText().isBlank()
        );

        Assert.assertEquals(
                CoverageQueries.profile(f.customerId()),
                profile
        );

        checkProfile(f.customerId(), profile);
        unchanged(f.customerId(), state);
    }
}