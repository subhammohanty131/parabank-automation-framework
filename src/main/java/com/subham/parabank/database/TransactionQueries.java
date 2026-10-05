package com.subham.parabank.database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TransactionQueries {
    public record Transaction(int id, int accountId, String type,
                              BigDecimal amount, String description) {}

    public static int getLatestTransactionId(int accountId) {
        String sql = "SELECT COALESCE(MAX(ID), 0) FROM TRANSACTION WHERE ACCOUNT_ID = ?";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read latest transaction for account " + accountId, e);
        }
    }

    public static List<Transaction> getTransactionsAfter(int accountId, int previousId) {
        String sql = "SELECT ID, ACCOUNT_ID, TYPE, AMOUNT, DESCRIPTION FROM TRANSACTION "
                + "WHERE ACCOUNT_ID = ? AND ID > ? ORDER BY ID";
        try (Connection connection = DBConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, accountId);
            statement.setInt(2, previousId);
            List<Transaction> transactions = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    transactions.add(new Transaction(result.getInt("ID"),
                            result.getInt("ACCOUNT_ID"), result.getString("TYPE"),
                            result.getBigDecimal("AMOUNT"), result.getString("DESCRIPTION")));
                }
            }
            return transactions;
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot read new transactions for account " + accountId, e);
        }
    }
}
