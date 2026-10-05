package com.subham.parabank.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class AccountQueries {

    public static BigDecimal getAccountBalance(int accountId) {

        String sql = "SELECT BALANCE FROM ACCOUNT WHERE ID = ?";

        try (
                Connection connection = DBConnection.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {

            statement.setInt(1, accountId);

            try (ResultSet resultSet = statement.executeQuery()) {

                if (resultSet.next()) {
                    return resultSet.getBigDecimal("BALANCE");
                }

                throw new RuntimeException(
                        "Account not found: " + accountId
                );
            }

        } catch (SQLException e) {
            throw new RuntimeException(
                    "Database query failed",
                    e
            );
        }
    }
}