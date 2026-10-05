package com.subham.parabank.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class AccountCreationQueries {

    public record Account(
            int id,
            int customerId,
            int type,
            BigDecimal balance) {
    }

    public static Account getAccount(int accountId) {

        String sql =
                "SELECT ID, CUSTOMER_ID, TYPE, BALANCE "
                        + "FROM ACCOUNT WHERE ID = ?";

        try (
                Connection connection = DBConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, accountId);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    throw new IllegalStateException(
                            "Account not found: " + accountId
                    );
                }

                int customerId = result.getInt("CUSTOMER_ID");

                if (result.wasNull()) {
                    throw new IllegalStateException(
                            "Missing customer ID for " + accountId
                    );
                }

                int type = result.getInt("TYPE");

                if (result.wasNull()) {
                    throw new IllegalStateException(
                            "Missing account type for " + accountId
                    );
                }

                return new Account(
                        result.getInt("ID"),
                        customerId,
                        type,
                        result.getBigDecimal("BALANCE")
                );
            }

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Cannot read account " + accountId,
                    e
            );
        }
    }

    public static Set<Integer> getAccountIds(int customerId) {

        String sql =
                "SELECT ID FROM ACCOUNT WHERE CUSTOMER_ID = ?";

        try (
                Connection connection = DBConnection.getConnection();
                PreparedStatement statement =
                        connection.prepareStatement(sql)
        ) {

            statement.setInt(1, customerId);

            Set<Integer> ids = new HashSet<>();

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    ids.add(result.getInt("ID"));
                }
            }

            return ids;

        } catch (SQLException e) {

            throw new IllegalStateException(
                    "Cannot read accounts for customer " + customerId,
                    e
            );
        }
    }
}