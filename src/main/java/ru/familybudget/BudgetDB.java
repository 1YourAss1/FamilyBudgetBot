package ru.familybudget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
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

    public Map<String, String> insertExpense(Expense expense, int userId, String rawText) {
        Map<String, String> result = new HashMap<>();
        try {
            int amount = expense.getAmount();
            Map<String, String> category = getCategory(expense.getCategory());
            String sql = "INSERT INTO expense (amount, user_id, created, category_codename, raw_text) " +
                    "VALUES (?, ?, ?, ?, ?);";
            PreparedStatement preparedStatement = conn.prepareStatement(sql);
            preparedStatement.setInt(1, amount);
            preparedStatement.setInt(2, userId);
            preparedStatement.setString(3, getDataString(expense.getDate()));

            preparedStatement.setString(4, category.get("codename"));
            preparedStatement.setString(5, rawText);
            preparedStatement.executeUpdate();
            result.put("amount", String.valueOf(amount));
            result.put("category", category.get("name"));
            result.put("date", getDataString(expense.getDate()));
        } catch (SQLException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
        }
        return result;
    }

    public void deleteExpense(int rowId) {
        try (var stmt = conn.prepareStatement("DELETE FROM expense where id=?")) {
            stmt.setInt(1, rowId);
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

    private List<String> getMounths() {
        ArrayList<String> result = new ArrayList<>();
        String query = "SELECT DISTINCT strftime('%m.%Y', created) as month_year FROM expense;";
        try (var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(query);
            while (rs.next()) {
                result.add(rs.getString("month_year"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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

    public Map<String, JSONArray> getAllExpenses() {
        Map<String, JSONArray> result = new HashMap<>();

        List<String> mounths = getMounths();
        mounths.forEach(mounth -> {
            String query = "SELECT c.name, e.amount FROM expense e LEFT JOIN category c ON c.codename=e.category_codename " +
                    "WHERE strftime('%m.%Y', created) = '" + mounth + "';";
            try (var stmt = conn.createStatement()) {
                var rs = stmt.executeQuery(query);
                JSONArray rows = new JSONArray();
                while (rs.next()) {
                    JSONObject row = new JSONObject();
                    row.put("category", rs.getString("name"));
                    row.put("amount", rs.getFloat("amount"));
                    rows.put(row);
                }
                result.put(mounth, rows);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });

        return result;
    }

    public String getLastExpenses() {
        StringBuilder result = new StringBuilder();
        String query = "SELECT e.id, e.amount, e.created ,c.name " +
                "FROM expense e LEFT JOIN category c " +
                "ON c.codename=e.category_codename " +
                "ORDER BY id DESC LIMIT 10;";
        try (var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(query);
            while (rs.next()) {
                result
                        .append("- ")
                        .append(rs.getString("amount"))
                        .append(" руб. на ")
                        .append(rs.getString("name"))
                        .append(" от ")
                        .append(rs.getString("created"))
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
        try (var stmt = conn.createStatement()) {
            var rs = stmt.executeQuery(query);
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


