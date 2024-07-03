package ru.familybudget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BudgetDB {
    Logger logger = Logger.getLogger(BudgetDB.class.getName());
    private static final String URL = "jdbc:sqlite:budget.db";
    private Connection conn = null;

    public boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            conn = DriverManager.getConnection(URL);
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return false;
    }

    public void close() {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    public Map<String, String> insertExpense(Map<String, String> expense, int user_id, String rawText) {
        try {
            int amount = Integer.parseInt(expense.get("amount"));
            Map<String, String> category = getCategory(expense.get("category"));
            String sql = "INSERT INTO expense (amount, user_id, created, category_codename, raw_text) " +
                    "VALUES (?, ?, ?, ?, ?);";
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, amount);
            preparedStatement.setInt(2, user_id);
            preparedStatement.setString(3,getDataString(LocalDateTime.now()));

            preparedStatement.setString(4, category.get("codename"));
            preparedStatement.setString(5, rawText);
            preparedStatement.executeUpdate();
            expense.put("amount", String.valueOf(amount));
            expense.put("category", category.get("name"));
            return expense;
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return null;
    }

    public void deleteExpense(int row_id) {
        try {
            var stmt = conn.prepareStatement("DELETE FROM expense where id=?");
            stmt.setInt(1, row_id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
    }

    private String getDataString(LocalDateTime date) {
        String pattern = "yyyy-MM-dd HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return date.format(formatter);
    }

    private Map<String, String> getCategory(String categoryFromText) {
        Map<String, String> result = new HashMap<>();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM category;");
            while (rs.next()) {
                List<String> rowAliases = new ArrayList<>();
                rowAliases.add(rs.getString("name"));
                rowAliases.addAll(List.of(rs.getString("aliases").split(", ")));
                if (rowAliases.stream().anyMatch(categoryFromText::equals)) {
                    result.put("codename", rs.getString("codename"));
                    result.put("name", rs.getString("name"));
                    return result;
                }
            }
            rs.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        result.put("codename", "other");
        result.put("name", "прочее");
        return result;
    }

    public String getAllCategories() {
        StringBuilder result = new StringBuilder();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT * FROM category;");
            while (rs.next()) {
                result
                        .append("‣ ")
                        .append(rs.getString("name").toUpperCase())
                        .append(" (")
                        .append(rs.getString("aliases"))
                        .append((rs.getString("aliases").isEmpty()) ? "" : ", ")
                        .append(rs.getString("codename"))
                        .append(")")
                        .append("\n");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return result.toString();
    }

    public String getLastExpenses() {
        StringBuilder result = new StringBuilder();
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(
                    "SELECT e.id, e.amount, c.name " +
                    "FROM expense e LEFT JOIN category c " +
                    "ON c.codename=e.category_codename " +
                    "ORDER BY created DESC LIMIT 10;"
            );
            while (rs.next()) {
                result
                        .append("- ")
                        .append(rs.getString("amount"))
                        .append(" руб. на ")
                        .append(rs.getString("name"))
                        .append(" /del").append(rs.getString("id"))
                        .append("\n");
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return result.toString();
    }

    public String getTodaySum() {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT SUM(amount) FROM expense WHERE date(created)=date('now', 'localtime');");
            if (rs.next()) {
                return String.valueOf(rs.getInt(1));
            }
            rs.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return "";
    }

    public String getMonthSum() {
        try {
            var rs = conn.createStatement().executeQuery("SELECT sum(amount) FROM expense WHERE date(created)>=date('now', 'start of month')");
            if (rs.next()) {
                return String.valueOf(rs.getInt(1));
            }
            rs.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return "";
    }


    public JSONArray getMonthStatistic() {
        JSONArray result = new JSONArray();
        String query =
                "SELECT e.category_codename, " +
                        "c.name, " +
                        "SUM(e.amount) AS sum, " +
                        "100.0 * SUM(e.amount) / (SELECT SUM(amount) FROM expense WHERE date(created)>=date('now', 'start of month')) AS percentage " +
                "FROM expense e LEFT JOIN category c ON c.codename=e.category_codename " +
                "WHERE date(created)>=date('now', 'start of month')" +
                "GROUP BY category_codename " +
                "ORDER BY sum DESC;";
        try {
            var rs = conn.createStatement().executeQuery(query);
            while (rs.next()) {
                JSONObject row = new JSONObject();
                row.put("name", rs.getString("name"));
                row.put("percentage", rs.getFloat("percentage"));
                row.put("sum", rs.getInt("sum"));
                result.put(row);
            }
            rs.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return result;
    }

}


