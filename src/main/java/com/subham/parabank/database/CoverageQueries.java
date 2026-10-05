package com.subham.parabank.database;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;

import com.subham.parabank.database.AccountCreationQueries.Account;

public final class CoverageQueries {

    private CoverageQueries() {
    }

    public record Tx(
            int id,
            int accountId,
            String type,
            LocalDate date,
            BigDecimal amount,
            String description) {
    }

    public record State(
            Map<Integer, Account> accounts,
            List<Tx> transactions) {
    }

    public static List<Map<String, Object>> query(
            String sql, Object... values) {

        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {

            for (int i = 0; i < values.length; i++) {
                p.setObject(i + 1, values[i]);
            }

            try (ResultSet r = p.executeQuery()) {

                List<Map<String, Object>> rows =
                        new ArrayList<>();

                while (r.next()) {

                    Map<String, Object> row =
                            new LinkedHashMap<>();

                    for (int i = 1;
                         i <= r.getMetaData().getColumnCount();
                         i++) {

                        row.put(
                                r.getMetaData()
                                        .getColumnLabel(i)
                                        .toUpperCase(Locale.ROOT),
                                r.getObject(i)
                        );
                    }

                    rows.add(row);
                }

                return rows;
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Coverage query failed", e
            );
        }
    }

    public static int countCustomers() {

        return ((Number) query(
                "SELECT COUNT(*) AS N FROM CUSTOMER"
        ).get(0).get("N")).intValue();
    }

    public static Integer customerId(String username) {

        var rows = query(
                "SELECT ID FROM CUSTOMER WHERE USERNAME = ?",
                username
        );

        return rows.isEmpty()
                ? null
                : ((Number) rows.get(0).get("ID")).intValue();
    }

    public static String parameter(String name) {

        var rows = query(
                "SELECT VALUE FROM PARAMETER WHERE NAME = ?",
                name
        );

        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "Missing/duplicate parameter: " + name
            );
        }

        return String.valueOf(rows.get(0).get("VALUE"));
    }

    public static Map<String, String> profile(int customerId) {

        var row = query(
                "SELECT FIRST_NAME, LAST_NAME, ADDRESS, CITY, "
                        + "STATE, ZIP_CODE, PHONE_NUMBER, SSN "
                        + "FROM CUSTOMER WHERE ID = ?",
                customerId
        ).get(0);

        String[] fields = {
                "firstName",
                "lastName",
                "address.street",
                "address.city",
                "address.state",
                "address.zipCode",
                "phoneNumber",
                "ssn"
        };

        String[] columns = {
                "FIRST_NAME",
                "LAST_NAME",
                "ADDRESS",
                "CITY",
                "STATE",
                "ZIP_CODE",
                "PHONE_NUMBER",
                "SSN"
        };

        Map<String, String> profile = new LinkedHashMap<>();

        for (int i = 0; i < fields.length; i++) {
            profile.put(
                    fields[i],
                    String.valueOf(row.get(columns[i]))
            );
        }

        return profile;
    }

    public static List<Tx> transactions(int customerId) {

        List<Tx> rows = new ArrayList<>();

        for (var r : query(
                "SELECT T.* FROM TRANSACTION T "
                        + "JOIN ACCOUNT A ON A.ID = T.ACCOUNT_ID "
                        + "WHERE A.CUSTOMER_ID = ? ORDER BY T.ID",
                customerId
        )) {

            Object date = r.get("DATE");

            rows.add(new Tx(
                    ((Number) r.get("ID")).intValue(),
                    ((Number) r.get("ACCOUNT_ID")).intValue(),
                    String.valueOf(r.get("TYPE")),
                    date == null
                            ? null
                            : ((java.sql.Date) date).toLocalDate(),
                    (BigDecimal) r.get("AMOUNT"),
                    String.valueOf(r.get("DESCRIPTION"))
            ));
        }

        return rows;
    }

    public static State state(int customerId) {

        Map<Integer, Account> accounts = new TreeMap<>();

        for (int id :
                AccountCreationQueries.getAccountIds(customerId)) {

            accounts.put(
                    id,
                    AccountCreationQueries.getAccount(id)
            );
        }

        return new State(
                accounts,
                transactions(customerId)
        );
    }

    public static BigDecimal availableFunds(int customerId) {

        return state(customerId).accounts().values().stream()
                .filter(a -> a.type() != 2)
                .map(Account::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}